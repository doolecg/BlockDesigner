package io.blockdesigner.core.nbt;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Stringified NBT (the {@code /data} command syntax): printing and parsing. */
public final class Snbt {
    private static final Pattern BARE_KEY = Pattern.compile("[A-Za-z0-9._+-]+");

    private Snbt() {
    }

    public static String write(Tag tag) {
        StringBuilder sb = new StringBuilder();
        write(tag, sb);
        return sb.toString();
    }

    private static void write(Tag tag, StringBuilder sb) {
        switch (tag) {
            case ByteTag t -> sb.append(t.value()).append('b');
            case ShortTag t -> sb.append(t.value()).append('s');
            case IntTag t -> sb.append(t.value());
            case LongTag t -> sb.append(t.value()).append('L');
            case FloatTag t -> sb.append(t.value()).append('f');
            case DoubleTag t -> sb.append(t.value()).append('d');
            case StringTag t -> quote(t.value(), sb);
            case ByteArrayTag t -> {
                sb.append("[B;");
                byte[] a = t.value();
                for (int i = 0; i < a.length; i++) sb.append(i == 0 ? "" : ",").append(a[i]).append('b');
                sb.append(']');
            }
            case IntArrayTag t -> {
                sb.append("[I;");
                int[] a = t.value();
                for (int i = 0; i < a.length; i++) sb.append(i == 0 ? "" : ",").append(a[i]);
                sb.append(']');
            }
            case LongArrayTag t -> {
                sb.append("[L;");
                long[] a = t.value();
                for (int i = 0; i < a.length; i++) sb.append(i == 0 ? "" : ",").append(a[i]).append('L');
                sb.append(']');
            }
            case ListTag t -> {
                sb.append('[');
                boolean first = true;
                for (Tag item : t) {
                    if (!first) sb.append(',');
                    write(item, sb);
                    first = false;
                }
                sb.append(']');
            }
            case CompoundTag t -> {
                sb.append('{');
                boolean first = true;
                for (var e : t.entries().entrySet()) {
                    if (!first) sb.append(',');
                    if (BARE_KEY.matcher(e.getKey()).matches()) sb.append(e.getKey());
                    else quote(e.getKey(), sb);
                    sb.append(':');
                    write(e.getValue(), sb);
                    first = false;
                }
                sb.append('}');
            }
        }
    }

    private static void quote(String s, StringBuilder sb) {
        char q = s.indexOf('"') >= 0 && s.indexOf('\'') < 0 ? '\'' : '"';
        sb.append(q);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == q || c == '\\') sb.append('\\');
            sb.append(c);
        }
        sb.append(q);
    }

    public static Tag parse(String snbt) {
        Parser p = new Parser(snbt);
        Tag t = p.value();
        p.skipWs();
        if (p.pos != snbt.length()) throw p.error("Trailing data");
        return t;
    }

    public static CompoundTag parseCompound(String snbt) {
        if (parse(snbt) instanceof CompoundTag c) return c;
        throw new IllegalArgumentException("SNBT is not a compound: " + snbt);
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException(msg + " at position " + pos + " in SNBT: " + s);
        }

        void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        char peek() {
            skipWs();
            if (pos >= s.length()) throw error("Unexpected end");
            return s.charAt(pos);
        }

        void expect(char c) {
            if (peek() != c) throw error("Expected '" + c + "'");
            pos++;
        }

        Tag value() {
            char c = peek();
            if (c == '{') return compound();
            if (c == '[') return list();
            if (c == '"' || c == '\'') return new StringTag(quoted());
            return scalar(bare());
        }

        CompoundTag compound() {
            expect('{');
            CompoundTag tag = new CompoundTag();
            if (peek() == '}') {
                pos++;
                return tag;
            }
            while (true) {
                char c = peek();
                String key = (c == '"' || c == '\'') ? quoted() : bare();
                if (key.isEmpty()) throw error("Expected key");
                expect(':');
                tag.put(key, value());
                char n = peek();
                pos++;
                if (n == '}') return tag;
                if (n != ',') throw error("Expected ',' or '}'");
            }
        }

        Tag list() {
            expect('[');
            if (pos + 1 < s.length() && s.charAt(pos + 1) == ';') {
                char kind = s.charAt(pos);
                pos += 2;
                return typedArray(kind);
            }
            ListTag list = new ListTag();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(value());
                char n = peek();
                pos++;
                if (n == ']') return list;
                if (n != ',') throw error("Expected ',' or ']'");
            }
        }

        Tag typedArray(char kind) {
            List<Number> values = new ArrayList<>();
            if (peek() != ']') {
                while (true) {
                    Tag v = scalar(bare());
                    Number n = v.asNumber();
                    if (n == null) throw error("Expected number in array");
                    values.add(n);
                    char c = peek();
                    pos++;
                    if (c == ']') break;
                    if (c != ',') throw error("Expected ',' or ']'");
                }
            } else {
                pos++;
            }
            return switch (Character.toUpperCase(kind)) {
                case 'B' -> {
                    byte[] a = new byte[values.size()];
                    for (int i = 0; i < a.length; i++) a[i] = values.get(i).byteValue();
                    yield new ByteArrayTag(a);
                }
                case 'I' -> new IntArrayTag(values.stream().mapToInt(Number::intValue).toArray());
                case 'L' -> new LongArrayTag(values.stream().mapToLong(Number::longValue).toArray());
                default -> throw error("Unknown array type " + kind);
            };
        }

        String quoted() {
            char q = s.charAt(pos++);
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '\\' && pos < s.length()) sb.append(s.charAt(pos++));
                else if (c == q) return sb.toString();
                else sb.append(c);
            }
            throw error("Unterminated string");
        }

        String bare() {
            skipWs();
            int start = pos;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == '+') pos++;
                else break;
            }
            return s.substring(start, pos);
        }

        Tag scalar(String raw) {
            if (raw.isEmpty()) throw error("Expected value");
            String lower = raw.toLowerCase();
            if (lower.equals("true")) return new ByteTag((byte) 1);
            if (lower.equals("false")) return new ByteTag((byte) 0);
            try {
                char suffix = lower.charAt(lower.length() - 1);
                String body = raw.substring(0, raw.length() - 1);
                switch (suffix) {
                    case 'b':
                        return new ByteTag(Byte.parseByte(body));
                    case 's':
                        return new ShortTag(Short.parseShort(body));
                    case 'l':
                        return new LongTag(Long.parseLong(body));
                    case 'f':
                        return new FloatTag(Float.parseFloat(body));
                    case 'd':
                        return new DoubleTag(Double.parseDouble(body));
                    default:
                        break;
                }
                if (raw.contains(".") || lower.contains("e")) return new DoubleTag(Double.parseDouble(raw));
                return new IntTag(Integer.parseInt(raw));
            } catch (NumberFormatException e) {
                return new StringTag(raw);
            }
        }
    }
}
