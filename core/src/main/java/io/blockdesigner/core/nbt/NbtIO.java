package io.blockdesigner.core.nbt;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Binary NBT reading and writing: Java edition's big-endian format ({@link #read}, {@link #write}), and Bedrock
 * edition's little-endian one ({@link #readLE}, {@link #writeLE}), as used by {@code .mcstructure} files.
 */
public final class NbtIO {
    private static final int MAX_DEPTH = 512;

    private NbtIO() {
    }

    /** A root tag together with its (usually empty) name. */
    public record Named(String name, CompoundTag tag) {
    }

    /** Reads a root compound, transparently handling gzip compression. */
    public static Named read(InputStream in) throws IOException {
        BufferedInputStream buf = new BufferedInputStream(in);
        buf.mark(2);
        int b1 = buf.read(), b2 = buf.read();
        buf.reset();
        InputStream src = (b1 == 0x1f && b2 == 0x8b) ? new BufferedInputStream(new GZIPInputStream(buf)) : buf;
        DataInputStream data = new DataInputStream(src);
        byte type = data.readByte();
        if (type != Tag.COMPOUND) throw new IOException("Root tag is not a compound (type " + type + ")");
        String name = data.readUTF();
        return new Named(name, (CompoundTag) readPayload(data, type, 0));
    }

    public static Named read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return read(in);
        }
    }

    public static void write(CompoundTag root, String name, OutputStream out, boolean gzip) throws IOException {
        OutputStream target = gzip ? new GZIPOutputStream(out) : out;
        DataOutputStream data = new DataOutputStream(new BufferedOutputStream(target));
        data.writeByte(Tag.COMPOUND);
        data.writeUTF(name);
        writePayload(data, root);
        data.flush();
        if (target instanceof GZIPOutputStream g) g.finish();
    }

    public static void write(CompoundTag root, String name, Path file, boolean gzip) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try (OutputStream out = Files.newOutputStream(file)) {
            write(root, name, out, gzip);
        }
    }

    /**
     * Reads a root compound in Bedrock's little-endian format (uncompressed, strings as a little-endian length and
     * UTF-8 bytes), e.g. a {@code .mcstructure} file.
     */
    public static Named readLE(InputStream in) throws IOException {
        DataInput data = new LittleEndianInput(new DataInputStream(new BufferedInputStream(in)));
        byte type = data.readByte();
        if (type != Tag.COMPOUND) throw new IOException("Root tag is not a compound (type " + type + ")");
        String name = data.readUTF();
        return new Named(name, (CompoundTag) readPayload(data, type, 0));
    }

    /** Writes a root compound in Bedrock's little-endian format (uncompressed). */
    public static void writeLE(CompoundTag root, String name, OutputStream out) throws IOException {
        DataOutputStream buffered = new DataOutputStream(new BufferedOutputStream(out));
        DataOutput data = new LittleEndianOutput(buffered);
        data.writeByte(Tag.COMPOUND);
        data.writeUTF(name);
        writePayload(data, root);
        buffered.flush();
    }

    private static Tag readPayload(DataInput in, byte type, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT nested too deeply");
        return switch (type) {
            case Tag.BYTE -> new ByteTag(in.readByte());
            case Tag.SHORT -> new ShortTag(in.readShort());
            case Tag.INT -> new IntTag(in.readInt());
            case Tag.LONG -> new LongTag(in.readLong());
            case Tag.FLOAT -> new FloatTag(in.readFloat());
            case Tag.DOUBLE -> new DoubleTag(in.readDouble());
            case Tag.BYTE_ARRAY -> {
                byte[] a = new byte[checkedLength(in.readInt())];
                in.readFully(a);
                yield new ByteArrayTag(a);
            }
            case Tag.STRING -> new StringTag(in.readUTF());
            case Tag.LIST -> {
                byte elem = in.readByte();
                int len = checkedLength(in.readInt());
                ListTag list = new ListTag(len == 0 ? Tag.END : elem);
                for (int i = 0; i < len; i++) list.add(readPayload(in, elem, depth + 1));
                yield list;
            }
            case Tag.COMPOUND -> {
                CompoundTag c = new CompoundTag();
                byte t;
                while ((t = in.readByte()) != Tag.END) {
                    String key = in.readUTF();
                    c.put(key, readPayload(in, t, depth + 1));
                }
                yield c;
            }
            case Tag.INT_ARRAY -> {
                int[] a = new int[checkedLength(in.readInt())];
                for (int i = 0; i < a.length; i++) a[i] = in.readInt();
                yield new IntArrayTag(a);
            }
            case Tag.LONG_ARRAY -> {
                long[] a = new long[checkedLength(in.readInt())];
                for (int i = 0; i < a.length; i++) a[i] = in.readLong();
                yield new LongArrayTag(a);
            }
            default -> throw new IOException("Unknown NBT tag type " + type);
        };
    }

    private static int checkedLength(int len) throws IOException {
        if (len < 0 || len > 256 * 1024 * 1024) throw new IOException("Invalid NBT length " + len);
        return len;
    }

    private static void writePayload(DataOutput out, Tag tag) throws IOException {
        switch (tag) {
            case ByteTag t -> out.writeByte(t.value());
            case ShortTag t -> out.writeShort(t.value());
            case IntTag t -> out.writeInt(t.value());
            case LongTag t -> out.writeLong(t.value());
            case FloatTag t -> out.writeFloat(t.value());
            case DoubleTag t -> out.writeDouble(t.value());
            case ByteArrayTag t -> {
                out.writeInt(t.length());
                out.write(t.value());
            }
            case StringTag t -> out.writeUTF(t.value());
            case ListTag t -> {
                out.writeByte(t.elementType());
                out.writeInt(t.size());
                for (Tag item : t) writePayload(out, item);
            }
            case CompoundTag t -> {
                for (var e : t.entries().entrySet()) {
                    out.writeByte(e.getValue().id());
                    out.writeUTF(e.getKey());
                    writePayload(out, e.getValue());
                }
                out.writeByte(Tag.END);
            }
            case IntArrayTag t -> {
                out.writeInt(t.length());
                for (int v : t.value()) out.writeInt(v);
            }
            case LongArrayTag t -> {
                out.writeInt(t.length());
                for (long v : t.value()) out.writeLong(v);
            }
        }
    }

    /** Reads numbers little-endian and strings as a little-endian u16 length plus UTF-8, Bedrock's way. */
    private record LittleEndianInput(DataInputStream in) implements DataInput {
        @Override
        public void readFully(byte[] b) throws IOException {
            in.readFully(b);
        }

        @Override
        public void readFully(byte[] b, int off, int len) throws IOException {
            in.readFully(b, off, len);
        }

        @Override
        public int skipBytes(int n) throws IOException {
            return in.skipBytes(n);
        }

        @Override
        public boolean readBoolean() throws IOException {
            return in.readBoolean();
        }

        @Override
        public byte readByte() throws IOException {
            return in.readByte();
        }

        @Override
        public int readUnsignedByte() throws IOException {
            return in.readUnsignedByte();
        }

        @Override
        public short readShort() throws IOException {
            return Short.reverseBytes(in.readShort());
        }

        @Override
        public int readUnsignedShort() throws IOException {
            return readShort() & 0xFFFF;
        }

        @Override
        public char readChar() throws IOException {
            return (char) readUnsignedShort();
        }

        @Override
        public int readInt() throws IOException {
            return Integer.reverseBytes(in.readInt());
        }

        @Override
        public long readLong() throws IOException {
            return Long.reverseBytes(in.readLong());
        }

        @Override
        public float readFloat() throws IOException {
            return Float.intBitsToFloat(readInt());
        }

        @Override
        public double readDouble() throws IOException {
            return Double.longBitsToDouble(readLong());
        }

        @Override
        public String readLine() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String readUTF() throws IOException {
            byte[] b = new byte[readUnsignedShort()];
            in.readFully(b);
            return new String(b, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /** The writing side of {@link LittleEndianInput}. */
    private record LittleEndianOutput(DataOutputStream out) implements DataOutput {
        @Override
        public void write(int b) throws IOException {
            out.write(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            out.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void writeBoolean(boolean v) throws IOException {
            out.writeBoolean(v);
        }

        @Override
        public void writeByte(int v) throws IOException {
            out.writeByte(v);
        }

        @Override
        public void writeShort(int v) throws IOException {
            out.writeShort(Short.reverseBytes((short) v));
        }

        @Override
        public void writeChar(int v) throws IOException {
            writeShort(v);
        }

        @Override
        public void writeInt(int v) throws IOException {
            out.writeInt(Integer.reverseBytes(v));
        }

        @Override
        public void writeLong(long v) throws IOException {
            out.writeLong(Long.reverseBytes(v));
        }

        @Override
        public void writeFloat(float v) throws IOException {
            writeInt(Float.floatToIntBits(v));
        }

        @Override
        public void writeDouble(double v) throws IOException {
            writeLong(Double.doubleToLongBits(v));
        }

        @Override
        public void writeBytes(String s) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeChars(String s) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeUTF(String s) throws IOException {
            byte[] b = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (b.length > 0xFFFF) throw new IOException("String too long for NBT: " + b.length + " bytes");
            writeShort(b.length);
            out.write(b);
        }
    }
}
