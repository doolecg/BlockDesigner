package io.blockdesigner.core.formats;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.nbt.CompoundTag;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Helpers shared by format implementations. */
final class FormatUtil {
    private FormatUtil() {
    }

    /** Returns a copy of {@code s} whose bounds start at the origin, plus the shift that was applied. */
    static Structure normalized(Structure s) {
        Structure copy = s.copy();
        copy.normalizeToOrigin();
        return copy;
    }

    static Box boundsOrUnit(Structure s) {
        return s.bounds().orElse(new Box(0, 0, 0, 0, 0, 0));
    }

    /** Strips position/id keys from block-entity data, keeping the vanilla {@code id} if present. */
    static CompoundTag cleanBlockEntity(CompoundTag raw, String idKey) {
        CompoundTag c = raw.copy();
        c.remove("x");
        c.remove("y");
        c.remove("z");
        c.remove("Pos");
        if (!idKey.equals("id") && c.contains(idKey)) {
            String id = c.getString(idKey);
            c.remove(idKey);
            if (!id.isEmpty()) c.putString("id", id);
        }
        c.remove("keepPacked");
        return c;
    }

    static BlockPos pos(int[] a) {
        return a.length >= 3 ? new BlockPos(a[0], a[1], a[2]) : BlockPos.ORIGIN;
    }

    // ---- varints (Sponge schematics) --------------------------------------------------------------------------

    static byte[] encodeVarInts(int[] values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(values.length);
        for (int v : values) {
            while ((v & ~0x7F) != 0) {
                out.write((v & 0x7F) | 0x80);
                v >>>= 7;
            }
            out.write(v);
        }
        return out.toByteArray();
    }

    static int[] decodeVarInts(byte[] data, int count) throws IOException {
        int[] out = new int[count];
        int i = 0, idx = 0;
        while (idx < count) {
            int value = 0, shift = 0;
            while (true) {
                if (i >= data.length) throw new IOException("Truncated varint block data");
                byte b = data[i++];
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
                if (shift > 35) throw new IOException("Varint too long");
            }
            out[idx++] = value;
        }
        return out;
    }

    // ---- tightly packed long arrays (Litematica) ---------------------------------------------------------------

    static int bitsFor(int paletteSize) {
        return Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, paletteSize - 1)));
    }

    static long[] packTight(int[] values, int bits) {
        long totalBits = (long) values.length * bits;
        long[] out = new long[(int) ((totalBits + 63) / 64)];
        long mask = (1L << bits) - 1;
        for (int i = 0; i < values.length; i++) {
            long bitIndex = (long) i * bits;
            int start = (int) (bitIndex >> 6);
            int offset = (int) (bitIndex & 63);
            long v = values[i] & mask;
            out[start] |= v << offset;
            if (offset + bits > 64) out[start + 1] |= v >>> (64 - offset);
        }
        return out;
    }

    static int[] unpackTight(long[] data, int bits, int count) throws IOException {
        if ((long) count * bits > (long) data.length * 64) throw new IOException("Packed block array too short");
        int[] out = new int[count];
        long mask = (1L << bits) - 1;
        for (int i = 0; i < count; i++) {
            long bitIndex = (long) i * bits;
            int start = (int) (bitIndex >> 6);
            int offset = (int) (bitIndex & 63);
            long v = data[start] >>> offset;
            if (offset + bits > 64) v |= data[start + 1] << (64 - offset);
            out[i] = (int) (v & mask);
        }
        return out;
    }
}
