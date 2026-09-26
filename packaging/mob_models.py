"""Writes every entity model layer of a Minecraft client jar to assets/.../mob_models.json.

Minecraft builds its entity models in code: LayerDefinitions.createRoots() calls each model's createBodyLayer(), which
stacks cubes with CubeListBuilder and poses them with PartPose. Since 26.1 the client jar is unobfuscated, so this
script runs that code itself: a small interpreter for the JVM bytecode javap prints, running the game's own model and
builder classes, with stand-ins for the Java, Guava and JOML library calls they make. The result is exactly the
cubes, texture offsets, poses and scales the game draws with, for every model layer, keyed by its layer name
("minecraft:pig#main").

    python packaging/mob_models.py <minecraft-26.x-client.jar> [javap]
"""
import json
import math
import os
import re
import struct
import subprocess
import sys
import tempfile
import zipfile

OUT = os.path.join(os.path.dirname(__file__), "..", "assets", "src", "main", "resources", "io", "blockdesigner", "assets",
                   "mob_models.json")
PACKAGES = ("net/minecraft/client/model/", "net/minecraft/client/renderer/entity/")


def f32(x):
    """Rounds to a Java float."""
    if isinstance(x, float) and (math.isnan(x) or math.isinf(x)):
        return x
    try:
        return struct.unpack("f", struct.pack("f", x))[0]
    except OverflowError:
        return math.copysign(math.inf, x)


def i32(x):
    x &= 0xFFFFFFFF
    return x - (1 << 32) if x & 0x80000000 else x


def java_hash(s):
    h = 0
    for ch in s:
        h = (31 * h + ord(ch)) & 0xFFFFFFFF
    return i32(h)


# ---- values ------------------------------------------------------------------------------------------------------

class Obj:
    """An instance of an interpreted class (or a JOML vector): its class and fields."""

    def __init__(self, cls):
        self.cls = cls
        self.fields = {}

    def __repr__(self):
        return "Obj(%s)" % self.cls


class Enum:
    def __init__(self, owner, name, ordinal):
        self.owner, self.name, self.ordinal = owner, name, ordinal

    def __repr__(self):
        return self.name


class ClassRef:
    def __init__(self, name):
        self.name = name


class Opaque:
    """A value from a library call this script doesn't model; fine as long as the geometry doesn't depend on it."""

    def __init__(self, what):
        self.what = what

    def __repr__(self):
        return "Opaque(%s)" % self.what


class Closure:
    def __init__(self, kind, owner, name, desc, captured):
        self.kind, self.owner, self.name, self.desc, self.captured = kind, owner, name, desc, captured


class JList(list):
    pass


class JMap(dict):
    pass


class JSet(dict):
    """An insertion-ordered set (dict keys)."""

    def add(self, v):
        present = v in self
        self[v] = True
        return not present


class Entry:
    def __init__(self, k, v):
        self.k, self.v = k, v


class JavaRandom:
    """java.util.Random's generator, which Minecraft's seeded random sources share (the ghast's tentacle lengths)."""

    def __init__(self, seed):
        self.seed = (seed ^ 0x5DEECE66D) & ((1 << 48) - 1)

    def next(self, bits):
        self.seed = (self.seed * 0x5DEECE66D + 0xB) & ((1 << 48) - 1)
        return i32(self.seed >> (48 - bits))

    def next_int(self, bound):
        if bound & -bound == bound:
            return i32((bound * self.next(31)) >> 31)
        while True:
            bits = self.next(31)
            val = bits % bound
            if bits - val + (bound - 1) < (1 << 31):
                return val


class JIter:
    def __init__(self, items):
        self.items = list(items)
        self.i = 0


DIRECTIONS = ["DOWN", "UP", "NORTH", "SOUTH", "WEST", "EAST"]


# ---- descriptors -------------------------------------------------------------------------------------------------

def parse_desc(desc):
    """(arg type codes, return code) of a method descriptor; codes are J/D (wide) or anything else."""
    args = []
    i = desc.index("(") + 1
    while desc[i] != ")":
        c = desc[i]
        start = i
        while desc[i] == "[":
            i += 1
        if desc[i] == "L":
            i = desc.index(";", i)
        i += 1
        args.append(desc[start:i])
    return args, desc[i + 1:]


def default_for(desc):
    if desc in ("F", "D"):
        return 0.0
    if desc in ("I", "Z", "B", "S", "C", "J"):
        return 0
    return None


# ---- class files via javap ----------------------------------------------------------------------------------------

INSN = re.compile(r"^\s+(\d+): (\w+)\s*(.*?)\s*(?://\s*(.*))?$")
CP = re.compile(r"^\s+#(\d+) = (\w+)\s+(.*?)(?:\s*//\s*(.*))?$")


class Method:
    def __init__(self, cls, name, desc, static):
        self.cls, self.name, self.desc, self.static = cls, name, desc, static
        self.code = []  # (offset, op, operands, comment)
        self.index = {}
        self.switches = {}


class ClassFile:
    def __init__(self, name, text):
        self.name = name
        self.super = None
        self.interfaces = []
        self.methods = {}
        self.bootstrap = []
        self.cp = {}
        self.fields = {}
        self.parse(text)

    def parse(self, text):
        lines = text.splitlines()
        i = 0
        simple = self.name.replace("/", ".")
        method = None
        in_code = False
        switch = None
        while i < len(lines):
            line = lines[i]
            m = CP.match(line)
            if m and not in_code:
                self.cp[int(m.group(1))] = (m.group(2), m.group(3), m.group(4))
                i += 1
                continue
            if line.startswith("  super_class:"):
                self.super = line.split("//")[1].strip()
            elif line.startswith("  interfaces:"):
                pass
            elif line.startswith("BootstrapMethods:"):
                i = self.parse_bootstrap(lines, i + 1)
                continue
            elif line.startswith("  ") and not line.startswith("   ") and line.rstrip().endswith(";") and "(" in line:
                decl = line.strip()[:-1]
                head = decl[:decl.index("(")]
                name = head.split()[-1]
                if name == simple:
                    name = "<init>"
                desc_line = lines[i + 1].strip()
                desc = desc_line[len("descriptor: "):] if desc_line.startswith("descriptor:") else "()V"
                method = Method(self, name, desc, " static " in " " + decl)
                self.methods[(name, desc)] = method
                in_code = False
            elif line.strip() == "static {};":
                method = Method(self, "<clinit>", "()V", True)
                self.methods[("<clinit>", "()V")] = method
                in_code = False
            elif line.startswith("  ") and not line.startswith("   ") and line.rstrip().endswith(";") and method is None or \
                    (line.startswith("  ") and not line.startswith("   ") and line.rstrip().endswith(";") and "(" not in line):
                # a field
                decl = line.strip()[:-1]
                parts = decl.split()
                if parts:
                    self.fields[parts[-1]] = " static " in " " + decl
                method = None
                in_code = False
            elif line.strip() == "Code:" and method is not None:
                in_code = True
            elif in_code:
                s = line.strip()
                if s.startswith(("LineNumberTable", "LocalVariableTable", "StackMapTable", "Exceptions", "RuntimeVisible",
                                 "MethodParameters", "Signature", "LocalVariableTypeTable")):
                    in_code = False
                elif switch is not None:
                    if s == "}":
                        switch = None
                    elif ":" not in s:
                        pass
                    else:
                        k, v = s.split(":")
                        switch[k.strip()] = int(v.strip())
                else:
                    m = INSN.match(line)
                    if m:
                        off, op, operands, comment = int(m.group(1)), m.group(2), m.group(3), m.group(4)
                        method.index[off] = len(method.code)
                        method.code.append((off, op, operands, comment))
                        if op in ("tableswitch", "lookupswitch"):
                            switch = {}
                            method.switches[off] = switch
            i += 1

    def parse_bootstrap(self, lines, i):
        while i < len(lines) and lines[i].startswith("  "):
            m = re.match(r"^\s+(\d+): #\d+ (\w+) (\S+)", lines[i])
            if not m:
                i += 1
                continue
            entry = {"kind": m.group(2), "target": m.group(3), "args": []}
            i += 1
            if i < len(lines) and "Method arguments:" in lines[i]:
                i += 1
                while i < len(lines) and re.match(r"^\s+#\d+ ", lines[i]):
                    entry["args"].append(lines[i].strip().split(" ", 1)[1] if " " in lines[i].strip() else "")
                    i += 1
            self.bootstrap.append(entry)
        return i

    def find(self, name, desc):
        return self.methods.get((name, desc))


class Interpreter:
    def __init__(self, classdir, javap):
        self.classdir, self.javap = classdir, javap
        self.classes = {}
        self.statics = {}
        self.initialised = set()
        self.unknown = {}
        self.steps = 0
        # (owner, method) -> function(args): calls answered here instead of running the game's code.
        self.overrides = {}

    # -- loading --

    def exists(self, name):
        return os.path.exists(os.path.join(self.classdir, name + ".class"))

    def load(self, name):
        if name in self.classes:
            return self.classes[name]
        if not self.exists(name):
            self.classes[name] = None
            return None
        text = subprocess.run([self.javap, "-c", "-p", "-v", "-cp", self.classdir, name.replace("/", ".")],
                              capture_output=True, text=True, encoding="utf-8", errors="replace").stdout
        cf = ClassFile(name, text)
        self.classes[name] = cf
        return cf

    def clinit(self, name):
        if name in self.initialised:
            return
        self.initialised.add(name)
        cf = self.load(name)
        if cf is None:
            return
        if cf.super:
            self.clinit(cf.super)
        m = cf.find("<clinit>", "()V")
        if m is not None:
            try:
                self.run(m, [])
            except Exception as e:  # a class whose static setup we can't run keeps what it managed
                self.note("clinit " + name, repr(e))

    def note(self, what, why=""):
        self.unknown[what] = self.unknown.get(what, 0) + 1

    def resolve(self, cls, name, desc):
        while cls:
            cf = self.load(cls)
            if cf is None:
                return None
            m = cf.find(name, desc)
            if m is not None and m.code:
                return m
            # default methods on interfaces
            for itf in self.interfaces_of(cf):
                mm = self.resolve(itf, name, desc)
                if mm is not None:
                    return mm
            cls = cf.super
        return None

    def interfaces_of(self, cf):
        out = []
        for idx, (kind, value, comment) in cf.cp.items():
            pass
        return out

    def is_subclass(self, cls, target):
        while cls:
            if cls == target:
                return True
            cf = self.load(cls)
            if cf is None:
                return False
            cls = cf.super
        return False

    # -- calls --

    def call(self, owner, name, desc, args, virtual, special=False):
        hook = self.overrides.get((owner, name))
        if hook is not None:
            return hook(args)
        recv = args[0] if virtual and args else None
        if isinstance(recv, Closure) and name not in ("equals", "hashCode", "toString", "andThen", "compose"):
            return self.call_closure(recv, args[1:])
        target = owner
        if isinstance(recv, Obj) and not special:
            target = recv.cls
        if name == "<init>":
            m = self.resolve(owner, name, desc) if self.exists(owner) else None
        else:
            m = self.resolve(target, name, desc)
            if m is None and target != owner:
                m = self.resolve(owner, name, desc)
        if m is not None:
            if m.static:
                self.clinit(m.cls.name)
            return self.run(m, args)
        return self.native(owner, name, desc, args, virtual)

    def call_closure(self, c, args):
        full = list(c.captured) + list(args)
        if c.kind == "REF_invokeStatic":
            return self.call(c.owner, c.name, c.desc, full, False)
        if c.kind in ("REF_invokeVirtual", "REF_invokeInterface", "REF_invokeSpecial"):
            return self.call(c.owner, c.name, c.desc, full, True)
        if c.kind == "REF_newInvokeSpecial":
            o = self.new(c.owner)
            self.call(c.owner, "<init>", c.desc[:c.desc.index(")") + 1] + "V", [o] + full, True)
            return o
        raise RuntimeError("closure kind " + c.kind)

    def new(self, cls):
        if cls in ("java/util/ArrayList", "java/util/LinkedList"):
            return JList()
        if cls in ("java/util/HashMap", "java/util/LinkedHashMap", "java/util/TreeMap", "java/util/EnumMap", "java/util/IdentityHashMap"):
            return JMap()
        if cls in ("java/util/HashSet", "java/util/LinkedHashSet", "java/util/TreeSet"):
            return JSet()
        if self.exists(cls) or cls.startswith("org/joml/"):
            return Obj(cls)
        return Opaque("new " + cls)

    # -- library stand-ins --

    def native(self, owner, name, desc, args, virtual):
        key = owner + "." + name
        recv = args[0] if virtual and args else None
        a = args[1:] if virtual else args
        # Object / Record
        if name == "<init>":
            if isinstance(recv, Obj) and recv.cls == "org/joml/Vector3f":
                if len(a) == 3:
                    recv.fields.update(x=a[0], y=a[1], z=a[2])
                elif len(a) == 1 and isinstance(a[0], Obj):
                    recv.fields.update(a[0].fields)
                else:
                    recv.fields.update(x=0.0, y=0.0, z=0.0)
            elif isinstance(recv, JList) and a and isinstance(a[0], (list, dict)):
                recv.extend(list(a[0]))
            elif isinstance(recv, JMap) and a and isinstance(a[0], dict):
                recv.update(a[0])
            elif isinstance(recv, JSet) and a and isinstance(a[0], (list, dict)):
                for v in a[0]:
                    recv.add(v)
            return None
        # A record of a class outside the extracted packages (a stand-in built here): its accessors read its fields.
        if isinstance(recv, Obj) and not self.exists(recv.cls) and not a and name in recv.fields:
            return recv.fields[name]
        if owner.startswith("org/joml/Vector3f") and isinstance(recv, Obj):
            if name in ("x", "y", "z"):
                return recv.fields.get(name, 0.0)
        # maths
        if owner in ("net/minecraft/util/Mth", "java/lang/Math", "java/lang/StrictMath"):
            return self.math(name, a, desc)
        if owner in ("java/lang/Float", "java/lang/Integer", "java/lang/Double", "java/lang/Boolean", "java/lang/Long"):
            if name in ("valueOf", "floatValue", "intValue", "doubleValue", "booleanValue", "longValue"):
                v = recv if virtual else a[0]
                return f32(v) if name == "floatValue" else v
            if name == "compare":
                return (a[0] > a[1]) - (a[0] < a[1])
            if name == "max":
                return max(a)
            if name == "min":
                return min(a)
        if owner == "java/lang/String" or isinstance(recv, str):
            s = recv
            if name == "valueOf":
                return str(a[0])
            if name == "hashCode":
                return java_hash(s)
            if name == "equals":
                return int(s == a[0])
            if name == "length":
                return len(s)
            if name == "startsWith":
                return int(s.startswith(a[0]))
            if name == "endsWith":
                return int(s.endsWith(a[0]))
            if name == "formatted" or name == "format":
                return s
            if name == "concat":
                return s + a[0]
        if owner == "java/util/stream/Collectors":
            return ("collector", name) + tuple(a)
        if owner == "java/util/Objects":
            if name == "requireNonNull":
                return a[0]
            if name == "equals":
                return int(a[0] == a[1])
        # identifiers and layer locations
        if owner == "net/minecraft/resources/Identifier" or owner == "net/minecraft/resources/ResourceLocation":
            if name in ("withDefaultNamespace",):
                return "minecraft:" + a[0]
            if name in ("fromNamespaceAndPath",):
                return a[0] + ":" + a[1]
            if name == "parse":
                return a[0] if ":" in a[0] else "minecraft:" + a[0]
            if name == "toString":
                return recv
            if name == "getPath":
                return recv.split(":", 1)[1]
            if name == "withPrefix":
                ns, p = recv.split(":", 1)
                return ns + ":" + a[0] + p
            if name == "withSuffix":
                return recv + a[0]
        if owner == "net/minecraft/util/RandomSource" and name in ("create", "createThreadLocalInstance", "createNewThreadLocalInstance"):
            return JavaRandom(a[0] if a else 0)
        if isinstance(recv, JavaRandom):
            if name == "nextInt":
                return recv.next_int(a[0]) if len(a) == 1 else a[0] + recv.next_int(a[1] - a[0])
            if name == "nextFloat":
                return f32(recv.next(24) / float(1 << 24))
        if owner == "net/minecraft/util/Util" and name == "allOfEnumExcept":
            out = JSet()
            for d in DIRECTIONS:
                if d != a[0].name:
                    out.add(self.direction(d))
            return out
        # directions
        if owner == "net/minecraft/core/Direction":
            if name == "values":
                return JList(self.direction(d) for d in DIRECTIONS)
            if name == "ordinal":
                return recv.ordinal
            if name == "name":
                return recv.name
        if owner == "java/lang/Enum" and isinstance(recv, Enum):
            if name == "ordinal":
                return recv.ordinal
            if name == "name":
                return recv.name
        # collections
        if owner in ("java/util/Set", "java/util/List", "com/google/common/collect/ImmutableSet", "com/google/common/collect/ImmutableList",
                     "java/util/EnumSet", "com/google/common/collect/Sets", "com/google/common/collect/Lists", "java/util/Collections",
                     "com/google/common/collect/Maps", "java/util/Map", "com/google/common/collect/ImmutableMap", "java/util/Arrays",
                     "java/util/stream/Stream") and not virtual:
            return self.statics_collection(owner, name, desc, a)
        if isinstance(recv, (JList, JMap, JSet, JIter, Entry, list)) or owner in (
                "com/google/common/collect/ImmutableMap$Builder", "com/google/common/collect/ImmutableList$Builder",
                "com/google/common/collect/ImmutableSet$Builder"):
            return self.collection_method(recv, name, a)
        if owner == "java/lang/Object":
            if name == "getClass":
                return ClassRef(recv.cls if isinstance(recv, Obj) else "?")
            if name == "equals":
                return int(recv is a[0])
            if name == "hashCode":
                return id(recv) & 0x7FFFFFFF
        self.note(key + desc)
        ret = parse_desc(desc)[1]
        return default_for(ret) if ret != "V" and default_for(ret) is not None else (Opaque(key) if ret != "V" else None)

    def direction(self, name):
        if not hasattr(self, "_dirs"):
            self._dirs = {d: Enum("net/minecraft/core/Direction", d, i) for i, d in enumerate(DIRECTIONS)}
        return self._dirs[name]

    def math(self, name, a, desc):
        x = a[0] if a else 0.0
        if name in ("cos", "sin"):
            v = math.cos(x) if name == "cos" else math.sin(x)
            return f32(v) if desc.endswith("F") else v
        if name == "sqrt":
            v = math.sqrt(x) if x >= 0 else math.nan
            return f32(v) if desc.endswith("F") else v
        if name == "abs":
            return abs(x)
        if name == "max":
            return max(a[0], a[1])
        if name == "min":
            return min(a[0], a[1])
        if name == "clamp":
            return min(max(a[0], a[1]), a[2])
        if name in ("floor",):
            return math.floor(x) if desc.endswith("I") else float(math.floor(x))
        if name == "ceil":
            return math.ceil(x) if desc.endswith("I") else float(math.ceil(x))
        if name == "lerp":
            return f32(a[1] + a[0] * (a[2] - a[1]))
        if name == "toRadians":
            return math.radians(x)
        if name == "toDegrees":
            return math.degrees(x)
        if name == "atan2":
            return math.atan2(a[0], a[1])
        if name == "wrapDegrees":
            v = math.fmod(x, 360.0)
            if v >= 180:
                v -= 360
            if v < -180:
                v += 360
            return f32(v) if desc.endswith("F") else v
        if name == "square":
            return x * x
        self.note("math " + name)
        return 0.0

    def statics_collection(self, owner, name, desc, a):
        if name in ("of", "copyOf", "newArrayList", "newHashSet", "newLinkedHashSet", "asList", "newHashMap", "newLinkedHashMap",
                    "newEnumMap", "noneOf", "allOf", "emptyList", "emptySet", "emptyMap", "newIdentityHashMap", "newTreeMap",
                    "unmodifiableList", "unmodifiableSet", "unmodifiableMap", "singletonList", "singleton", "ofEntries", "builder",
                    "range", "copyOfRange"):
            if owner.endswith("Map") or owner == "com/google/common/collect/Maps" or name in ("newHashMap", "newLinkedHashMap", "emptyMap", "newEnumMap",
                                                                                               "newIdentityHashMap", "newTreeMap", "unmodifiableMap", "ofEntries"):
                if name == "builder":
                    return JMap()
                if name in ("of",):
                    m = JMap()
                    for i in range(0, len(a), 2):
                        m[a[i]] = a[i + 1]
                    return m
                if name in ("copyOf", "unmodifiableMap") and a:
                    return JMap(a[0])
                return JMap()
            if name == "builder":
                return JList()
            items = []
            if name == "allOf":
                c = a[0]
                if isinstance(c, ClassRef) and c.name.endswith("Direction"):
                    items = [self.direction(d) for d in DIRECTIONS]
            elif name in ("noneOf", "emptyList", "emptySet"):
                items = []
            else:
                for v in a:
                    if isinstance(v, (list, dict)) and (desc.startswith("([") or name in ("copyOf", "newArrayList", "newHashSet", "unmodifiableList",
                                                                                           "unmodifiableSet", "asList")):
                        items.extend(list(v))
                    else:
                        items.append(v)
            if "Set" in owner or name in ("newHashSet", "newLinkedHashSet", "noneOf", "allOf", "emptySet", "singleton", "unmodifiableSet"):
                s = JSet()
                for v in items:
                    s.add(v)
                return s
            return JList(items)
        if owner == "java/util/Arrays" and name == "setAll":
            arr, fn = a
            for i in range(len(arr)):
                arr[i] = self.call_closure(fn, [i])
            return None
        if owner == "java/util/Arrays" and name == "fill":
            arr = a[0]
            for i in range(len(arr)):
                arr[i] = a[-1]
            return None
        self.note(owner + "." + name + desc)
        return Opaque(owner + "." + name)

    def collection_method(self, recv, name, a):
        if isinstance(recv, Entry):
            return recv.k if name == "getKey" else recv.v
        if isinstance(recv, JIter):
            if name == "hasNext":
                return int(recv.i < len(recv.items))
            if name == "next":
                recv.i += 1
                return recv.items[recv.i - 1]
        if isinstance(recv, JMap):
            if name == "put" or name == "putIfAbsent" and a[0] not in recv:
                old = recv.get(a[0])
                recv[a[0]] = a[1]
                return recv if len(a) == 2 and name == "put" and False else old
            if name == "putAll":
                recv.update(a[0])
                return recv
            if name in ("build", "buildOrThrow", "buildKeepingLast"):
                return recv
            if name in ("get", "getOrDefault"):
                return recv.get(a[0], a[1] if len(a) > 1 else None)
            if name == "containsKey":
                return int(a[0] in recv)
            if name == "remove":
                return recv.pop(a[0], None)
            if name == "entrySet":
                s = JSet()
                for k, v in recv.items():
                    s.add(Entry(k, v))
                return s
            if name == "keySet":
                s = JSet()
                for k in recv:
                    s.add(k)
                return s
            if name == "values":
                return JList(recv.values())
            if name == "size":
                return len(recv)
            if name == "isEmpty":
                return int(not recv)
            if name == "forEach":
                for k, v in list(recv.items()):
                    self.call_closure(a[0], [k, v])
                return None
            if name == "computeIfAbsent":
                if a[0] not in recv:
                    recv[a[0]] = self.call_closure(a[1], [a[0]])
                return recv[a[0]]
            if name == "clear":
                recv.clear()
                return None
        if isinstance(recv, JSet):
            if name == "add":
                return int(recv.add(a[0]))
            if name == "contains":
                return int(a[0] in recv)
            if name == "remove":
                return int(recv.pop(a[0], None) is not None)
            if name == "iterator":
                return JIter(list(recv.keys()))
            if name == "size":
                return len(recv)
            if name == "isEmpty":
                return int(not recv)
            if name in ("addAll",):
                for v in a[0]:
                    recv.add(v)
                return 1
            if name in ("build",):
                return recv
            if name == "forEach":
                for v in list(recv):
                    self.call_closure(a[0], [v])
                return None
            if name == "stream":
                return JList(recv.keys())
        if isinstance(recv, list):
            if name == "add":
                if len(a) == 2:
                    recv.insert(a[0], a[1])
                    return None
                recv.append(a[0])
                return 1 if not isinstance(recv, JMap) else recv
            if name == "get":
                return recv[a[0]]
            if name == "set":
                old = recv[a[0]]
                recv[a[0]] = a[1]
                return old
            if name == "size":
                return len(recv)
            if name == "isEmpty":
                return int(not recv)
            if name == "iterator":
                return JIter(recv)
            if name == "contains":
                return int(a[0] in recv)
            if name in ("addAll",):
                recv.extend(list(a[0]))
                return 1
            if name in ("build",):
                return recv
            if name == "forEach":
                for v in list(recv):
                    self.call_closure(a[0], [v])
                return None
            if name == "stream":
                return JList(recv)
            if name == "toArray":
                return list(recv)
            if name == "getFirst":
                return recv[0]
            # streams are plain lists here
            if name == "filter":
                return JList(v for v in recv if self.call_closure(a[0], [v]))
            if name == "map":
                return JList(self.call_closure(a[0], [v]) for v in recv)
            if name == "collect" and a and isinstance(a[0], tuple) and a[0][0] == "collector":
                kind = a[0][1]
                if kind == "toMap":
                    out = JMap()
                    for v in recv:
                        out[self.call_closure(a[0][2], [v])] = self.call_closure(a[0][3], [v])
                    return out
                if kind == "toSet":
                    out = JSet()
                    for v in recv:
                        out.add(v)
                    return out
                return JList(recv)
            if name in ("toList", "collect"):
                return JList(recv)
            if name == "anyMatch":
                return int(any(self.call_closure(a[0], [v]) for v in recv))
        self.note("collection " + type(recv).__name__ + "." + name)
        return Opaque("collection." + name)

    # -- the bytecode loop --

    def ldc(self, cf, comment):
        kind, _, value = comment.partition(" ")
        if kind == "float":
            v = value.rstrip("f")
            return f32(float(v.replace("Infinity", "inf").replace("NaN", "nan")))
        if kind == "double":
            return float(value.rstrip("d").replace("Infinity", "inf").replace("NaN", "nan"))
        if kind == "int":
            return int(value)
        if kind == "long":
            return int(value.rstrip("l"))
        if kind == "String":
            return value
        if kind == "class":
            return ClassRef(value.strip('"'))
        return Opaque("ldc " + comment)

    def run(self, m, args):
        cf = m.cls
        locals_ = {}
        slot = 0
        for v, t in zip(args, ([None] if not m.static else []) + parse_desc(m.desc)[0]):
            locals_[slot] = v
            slot += 2 if t in ("J", "D") else 1
        stack = []
        pc = 0
        code = m.code
        while True:
            self.steps += 1
            if self.steps > 50_000_000:
                raise RuntimeError("too many steps")
            off, op, operands, comment = code[pc]
            pc += 1
            if op.startswith(("aload", "iload", "fload", "dload", "lload")) and op[1:5] == "load":
                n = int(op.split("_")[1]) if "_" in op else int(operands)
                stack.append(locals_.get(n))
            elif op.startswith(("astore", "istore", "fstore", "dstore", "lstore")) and op[1:6] == "store":
                n = int(op.split("_")[1]) if "_" in op else int(operands)
                locals_[n] = stack.pop()
            elif op == "aconst_null":
                stack.append(None)
            elif op.startswith("iconst_"):
                stack.append(-1 if op == "iconst_m1" else int(op[7:]))
            elif op.startswith("fconst_"):
                stack.append(float(op[7:]))
            elif op.startswith("dconst_"):
                stack.append(float(op[7:]))
            elif op.startswith("lconst_"):
                stack.append(int(op[7:]))
            elif op in ("bipush", "sipush"):
                stack.append(int(operands))
            elif op in ("ldc", "ldc_w", "ldc2_w"):
                stack.append(self.ldc(cf, comment))
            elif op == "dup":
                stack.append(stack[-1])
            elif op == "dup_x1":
                v1, v2 = stack.pop(), stack.pop()
                stack.extend([v1, v2, v1])
            elif op == "dup_x2":
                v1, v2, v3 = stack.pop(), stack.pop(), stack.pop()
                stack.extend([v1, v3, v2, v1])
            elif op == "dup2":
                stack.extend(stack[-2:])
            elif op == "pop":
                stack.pop()
            elif op == "pop2":
                stack.pop()
                stack.pop()
            elif op == "swap":
                stack[-1], stack[-2] = stack[-2], stack[-1]
            elif op in ("fadd", "fsub", "fmul", "fdiv", "frem", "dadd", "dsub", "dmul", "ddiv", "drem"):
                b, a = stack.pop(), stack.pop()
                k = op[1:]
                try:
                    r = a + b if k == "add" else a - b if k == "sub" else a * b if k == "mul" else (
                        a / b if k == "div" else math.fmod(a, b))
                except ZeroDivisionError:
                    r = math.nan if a == 0 else math.copysign(math.inf, a) * (1 if str(b)[0] != "-" else -1)
                stack.append(f32(r) if op[0] == "f" else r)
            elif op in ("iadd", "isub", "imul", "idiv", "irem", "iand", "ior", "ixor", "ishl", "ishr", "iushr",
                        "ladd", "lsub", "lmul", "ldiv", "lrem", "land", "lor", "lxor", "lshl", "lshr", "lushr"):
                b, a = stack.pop(), stack.pop()
                k = op[1:]
                if k == "add":
                    r = a + b
                elif k == "sub":
                    r = a - b
                elif k == "mul":
                    r = a * b
                elif k == "div":
                    r = int(a / b)
                elif k == "rem":
                    r = a - int(a / b) * b
                elif k == "and":
                    r = a & b
                elif k == "or":
                    r = a | b
                elif k == "xor":
                    r = a ^ b
                elif k == "shl":
                    r = a << (b & 31)
                elif k == "shr":
                    r = a >> (b & 31)
                else:
                    r = (a & 0xFFFFFFFF) >> (b & 31)
                stack.append(i32(r) if op[0] == "i" else r)
            elif op in ("fneg", "dneg", "ineg", "lneg"):
                stack.append(-stack.pop())
            elif op in ("i2f", "l2f", "d2f"):
                stack.append(f32(float(stack.pop())))
            elif op in ("i2d", "l2d", "f2d"):
                stack.append(float(stack.pop()))
            elif op in ("f2i", "d2i", "f2l", "d2l"):
                v = stack.pop()
                stack.append(0 if math.isnan(v) else int(v))
            elif op in ("i2l", "l2i"):
                stack.append(i32(stack.pop()) if op == "l2i" else stack.pop())
            elif op in ("i2b", "i2c", "i2s"):
                v = stack.pop()
                stack.append(((v + 128) & 255) - 128 if op == "i2b" else v & 0xFFFF if op == "i2c" else ((v + 32768) & 0xFFFF) - 32768)
            elif op in ("fcmpl", "fcmpg", "dcmpl", "dcmpg", "lcmp"):
                b, a = stack.pop(), stack.pop()
                if isinstance(a, float) and (math.isnan(a) or math.isnan(b)):
                    stack.append(1 if op.endswith("g") else -1)
                else:
                    stack.append((a > b) - (a < b))
            elif op == "iinc":
                n, d = operands.split(",")
                locals_[int(n)] = i32(locals_[int(n)] + int(d))
            elif op.startswith("if") or op == "goto" or op == "goto_w":
                target = int(operands)
                jump = False
                if op in ("goto", "goto_w"):
                    jump = True
                elif op.startswith("if_icmp"):
                    b, a = stack.pop(), stack.pop()
                    jump = self.cmp(op[7:], a, b)
                elif op.startswith("if_acmp"):
                    b, a = stack.pop(), stack.pop()
                    jump = (a is b or (isinstance(a, (str, int)) and a == b)) == (op == "if_acmpeq")
                elif op == "ifnull":
                    jump = stack.pop() is None
                elif op == "ifnonnull":
                    jump = stack.pop() is not None
                else:
                    v = stack.pop()
                    if isinstance(v, Opaque):
                        v = 0
                    jump = self.cmp(op[2:], v, 0)
                if jump:
                    pc = m.index[target]
            elif op in ("tableswitch", "lookupswitch"):
                v = stack.pop()
                table = m.switches[off]
                t = table.get(str(v), table.get("default"))
                pc = m.index[t]
            elif op in ("ireturn", "freturn", "areturn", "dreturn", "lreturn"):
                return stack.pop()
            elif op == "return":
                return None
            elif op == "getstatic" or op == "putstatic":
                owner, field = self.member(comment)
                owner = owner or cf.name
                if op == "getstatic":
                    stack.append(self.get_static(owner, field, comment))
                else:
                    self.clinit(owner)
                    self.statics.setdefault(owner, {})[field] = stack.pop()
            elif op == "getfield":
                o = stack.pop()
                owner, field = self.member(comment)
                if isinstance(o, Obj):
                    stack.append(o.fields.get(field, default_for(comment.rsplit(":", 1)[1])))
                else:
                    self.note("getfield on " + type(o).__name__ + " " + field)
                    stack.append(Opaque("field " + field))
            elif op == "putfield":
                v, o = stack.pop(), stack.pop()
                owner, field = self.member(comment)
                if isinstance(o, Obj):
                    o.fields[field] = v
            elif op == "new":
                cls = comment.split("class ", 1)[1].strip()
                stack.append(self.new(cls))
            elif op in ("invokestatic", "invokevirtual", "invokespecial", "invokeinterface"):
                owner, name, desc = self.method_ref(comment, cf.name)
                argc = len(parse_desc(desc)[0]) + (0 if op == "invokestatic" else 1)
                args_ = stack[len(stack) - argc:] if argc else []
                del stack[len(stack) - argc:]
                if op == "invokestatic":
                    self.clinit(owner)
                ret = self.call(owner, name, desc, args_, op != "invokestatic", op == "invokespecial")
                if parse_desc(desc)[1] != "V":
                    stack.append(ret)
            elif op == "invokedynamic":
                stack.append(self.indy(cf, operands, comment, stack))
            elif op in ("checkcast",):
                pass
            elif op == "instanceof":
                o = stack.pop()
                cls = comment.split("class ", 1)[1].strip()
                stack.append(int(isinstance(o, Obj) and self.is_subclass(o.cls, cls)))
            elif op in ("newarray", "anewarray"):
                n = stack.pop()
                d = 0.0 if "float" in operands or "double" in operands else 0 if op == "newarray" else None
                stack.append([d] * n)
            elif op == "multianewarray":
                raise RuntimeError("multianewarray")
            elif op == "arraylength":
                stack.append(len(stack.pop()))
            elif op.endswith("aload") and len(op) == 6:
                i, arr = stack.pop(), stack.pop()
                stack.append(arr[i])
            elif op.endswith("astore") and len(op) == 7:
                v, i, arr = stack.pop(), stack.pop(), stack.pop()
                arr[i] = v
            elif op == "athrow":
                raise RuntimeError("athrow in %s.%s" % (cf.name, m.name))
            elif op in ("monitorenter", "monitorexit"):
                stack.pop()
            elif op == "nop":
                pass
            else:
                raise RuntimeError("opcode " + op)

    @staticmethod
    def cmp(kind, a, b):
        return {"eq": a == b, "ne": a != b, "lt": a < b, "ge": a >= b, "gt": a > b, "le": a <= b}[kind]

    @staticmethod
    def member(comment):
        ref = comment.split(" ", 1)[1]
        name = ref.split(":")[0]
        if "." in name:
            owner, field = name.rsplit(".", 1)
        else:
            owner, field = None, name
        return owner, field

    def method_ref(self, comment, current):
        ref = comment.split(" ", 1)[1]
        name, desc = ref.split(":", 1)
        if "." in name:
            owner, name = name.rsplit(".", 1)
        else:
            owner = current
        return owner, name.strip('"'), desc

    def get_static(self, owner, field, comment):
        if owner == "net/minecraft/core/Direction" and field in DIRECTIONS:
            return self.direction(field)
        if owner == "net/minecraft/util/Mth":
            return {"PI": f32(math.pi), "HALF_PI": f32(math.pi / 2), "TWO_PI": f32(math.pi * 2), "DEG_TO_RAD": f32(math.pi / 180),
                    "RAD_TO_DEG": f32(180 / math.pi), "EPSILON": 1e-5, "SQRT_OF_TWO": f32(math.sqrt(2))}.get(field, 0.0)
        if self.exists(owner):
            self.clinit(owner)
            cls = owner
            while cls:
                d = self.statics.get(cls, {})
                if field in d:
                    return d[field]
                cf = self.load(cls)
                cls = cf.super if cf else None
            return default_for(comment.rsplit(":", 1)[1])
        self.note("static " + owner + "." + field)
        return Opaque(owner + "." + field)

    def indy(self, cf, operands, comment, stack):
        # comment: "InvokeDynamic #3:apply:(F)Ljava/util/function/Function;"
        m = re.match(r"InvokeDynamic #(\d+):([\w$<>]+):(.*)", comment)
        bsm = cf.bootstrap[int(m.group(1))]
        desc = m.group(3)
        argc = len(parse_desc(desc)[0])
        captured = stack[len(stack) - argc:] if argc else []
        del stack[len(stack) - argc:]
        if "LambdaMetafactory" in bsm["target"]:
            impl = bsm["args"][1]
            km = re.match(r"(REF_\w+) (\S+)", impl)
            kind, ref = km.group(1), km.group(2)
            name, mdesc = ref.split(":", 1)
            owner, name = name.rsplit(".", 1)
            return Closure(kind, owner, name.strip('"'), mdesc, captured)
        if "StringConcatFactory" in bsm["target"]:
            recipe = bsm["args"][0] if bsm["args"] else ""
            recipe = recipe.replace("\\u0001", "\x01")
            out, i = [], 0
            for ch in recipe:
                if ch == "\x01":
                    v = captured[i]
                    i += 1
                    out.append(self.to_string(v))
                else:
                    out.append(ch)
            return "".join(out)
        if "ObjectMethods" in bsm["target"]:
            return 0
        self.note("indy " + bsm["target"])
        return Opaque("indy")

    @staticmethod
    def to_string(v):
        if isinstance(v, float):
            return repr(v)
        if isinstance(v, Enum):
            return v.name
        return str(v)


# ---- output ------------------------------------------------------------------------------------------------------

def r(v):
    v = float(v)
    return int(v) if v == int(v) else round(v, 6)


def pose(p):
    f = p.fields
    out = [r(f.get(k, 0.0)) for k in ("x", "y", "z", "xRot", "yRot", "zRot")]
    scale = [r(f.get(k, 1.0)) for k in ("xScale", "yScale", "zScale")]
    return out + scale if scale != [1, 1, 1] else out


def cube(c):
    f = c.fields
    o, d = f["origin"].fields, f["dimensions"].fields
    g = f["grow"].fields
    tc, ts = f["texCoord"].fields, f["texScale"].fields
    out = {"uv": [r(tc["u"]), r(tc["v"])], "at": [r(o["x"]), r(o["y"]), r(o["z"])], "size": [r(d["x"]), r(d["y"]), r(d["z"])]}
    grow = [r(g.get("growX", 0.0)), r(g.get("growY", 0.0)), r(g.get("growZ", 0.0))]
    if grow != [0, 0, 0]:
        out["grow"] = grow[0] if grow[0] == grow[1] == grow[2] else grow
    if f.get("mirror"):
        out["mirror"] = True
    if [r(ts["u"]), r(ts["v"])] != [1, 1]:
        out["texScale"] = [r(ts["u"]), r(ts["v"])]
    faces = f.get("visibleFaces")
    if isinstance(faces, dict) and len(faces) < 6:
        out["faces"] = [d.name.lower() for d in faces]
    return out


def part(p):
    f = p.fields
    out = {}
    pp = pose(f["partPose"])
    if any(pp[:6]) or len(pp) > 6:
        out["pose"] = pp
    cubes = [cube(c) for c in f.get("cubes") or []]
    if cubes:
        out["cubes"] = cubes
    kids = f.get("children") or {}
    if kids:
        out["parts"] = {k: part(v) for k, v in kids.items()}
    return out


MP = "net/minecraft/client/model/geom/ModelPart"
DRAGON_SAMPLE = "net/minecraft/world/entity/boss/enderdragon/DragonFlightHistory$Sample"

# Layers whose rest pose comes from setupAnim rather than the layer (the dragon's neck and tail follow its flight
# history): the layer is baked, the model built around it and setupAnim run once with the render state below, and the
# part poses it leaves are written as another layer ("as").
POSED = {
    "minecraft:ender_dragon#main": {
        "as": "minecraft:ender_dragon#posed",
        "model": "net/minecraft/client/model/monster/dragon/EnderDragonModel",
        "state": "net/minecraft/client/renderer/entity/state/EnderDragonRenderState",
        # Hovering in place: every flight sample level and facing the same way, wings spread wide mid-flap.
        "fields": {"flapTime": 0.5, "ageInTicks": 0.0, "partialTicks": 0.0},
        "overrides": {("net/minecraft/client/renderer/entity/state/EnderDragonRenderState", "getHistoricalPos"): "sample"},
    },
}


def posed(it, layer_obj, layer_json, spec):
    """Runs a model's setupAnim once and returns its layer with the poses that leaves (see POSED)."""
    def sample(args):
        o = Obj(DRAGON_SAMPLE)
        o.fields.update(y=0.0, yRot=0.0)
        return o
    hooks = {"sample": sample}
    for k, v in spec["overrides"].items():
        it.overrides[k] = hooks[v]
    root = it.call("net/minecraft/client/model/geom/builders/LayerDefinition", "bakeRoot", "()L" + MP + ";", [layer_obj], True)
    model = it.new(spec["model"])
    it.call(spec["model"], "<init>", "(L" + MP + ";)V", [model, root], True, True)
    state = it.new(spec["state"])
    it.call(spec["state"], "<init>", "()V", [state], True, True)
    state.fields.update(spec["fields"])
    it.call(spec["model"], "setupAnim", "(L" + spec["state"] + ";)V", [model, state], True)
    it.overrides.clear()

    def walk(mp, js):
        f = mp.fields
        out = dict(js)
        pose = [r(f.get(k, 0.0)) for k in ("x", "y", "z", "xRot", "yRot", "zRot")]
        scale = [r(f.get(k, 1.0)) for k in ("xScale", "yScale", "zScale")]
        pose = pose + scale if scale != [1, 1, 1] else pose
        if any(pose[:6]) or len(pose) > 6:
            out["pose"] = pose
        else:
            out.pop("pose", None)
        kids = f.get("children") or {}
        parts = {}
        for name, child in (js.get("parts") or {}).items():
            baked = kids.get(name)
            if baked is None:
                parts[name] = child
            elif baked.fields.get("visible", 1):
                parts[name] = walk(baked, child)
        if parts:
            out["parts"] = parts
        else:
            out.pop("parts", None)
        return out
    return {"texture": layer_json["texture"], "root": walk(root, layer_json["root"])}


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    jar = sys.argv[1]
    javap = sys.argv[2] if len(sys.argv) > 2 else "javap"
    with tempfile.TemporaryDirectory() as tmp:
        with zipfile.ZipFile(jar) as z:
            for n in z.namelist():
                if n.startswith(PACKAGES) and n.endswith(".class"):
                    z.extract(n, tmp)
        it = Interpreter(tmp, javap)
        roots = it.call("net/minecraft/client/model/geom/LayerDefinitions", "createRoots", "()Ljava/util/Map;", [], False)
        layers = {}
        for key, layer in roots.items():
            if not isinstance(key, Obj) or not isinstance(layer, Obj):
                continue
            name = "%s#%s" % (key.fields.get("model"), key.fields.get("layer"))
            try:
                mat = layer.fields["material"].fields
                layers[name] = {"texture": [mat["xTexSize"], mat["yTexSize"]], "root": part(layer.fields["mesh"].fields["root"])}
            except Exception as e:
                print("skipped", name, repr(e), file=sys.stderr)
        for name, spec in POSED.items():
            key = next((k for k in roots if isinstance(k, Obj) and "%s#%s" % (k.fields.get("model"), k.fields.get("layer")) == name), None)
            if key is None or name not in layers:
                print("no layer", name, "to pose", file=sys.stderr)
                continue
            try:
                layers[spec["as"]] = posed(it, roots[key], layers[name], spec)
            except Exception as e:
                print("could not pose", name, repr(e), file=sys.stderr)
        version = os.path.basename(jar).replace("-client.jar", "")
        out = {"source": version, "layers": dict(sorted(layers.items()))}
        with open(OUT, "w", encoding="utf-8", newline="\n") as f:
            json.dump(out, f, separators=(",", ":"))
            f.write("\n")
        print("wrote %d layers to %s" % (len(layers), os.path.normpath(OUT)))
        if it.unknown:
            print("library calls stood in for (harmless unless a model looks wrong):", file=sys.stderr)
            for k, v in sorted(it.unknown.items(), key=lambda kv: -kv[1])[:40]:
                print("  %5d  %s" % (v, k), file=sys.stderr)


if __name__ == "__main__":
    main()
