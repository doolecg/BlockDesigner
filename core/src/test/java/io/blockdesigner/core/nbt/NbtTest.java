package io.blockdesigner.core.nbt;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NbtTest {

    static CompoundTag sample() {
        return new CompoundTag()
                .putByte("b", -3)
                .putShort("s", 1234)
                .putInt("i", 0xCAFEBABE)
                .putLong("l", Long.MIN_VALUE + 7)
                .putFloat("f", 1.5f)
                .putDouble("d", -2.25)
                .putString("str", "héllo \"quoted\" 'single'")
                .putByteArray("ba", (byte) 1, (byte) -2)
                .putIntArray("ia", 1, 2, 3)
                .putLongArray("la", 4L, -5L)
                .put("list", ListTag.ofInts(7, 8, 9))
                .put("empty", new ListTag())
                .put("nested", new CompoundTag().putString("Name", "minecraft:stone").put("inner", ListTag.of(new CompoundTag().putInt("x", 1))));
    }

    @Test
    void littleEndianRoundTripAndByteOrder() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIO.writeLE(sample(), "", out);
        NbtIO.Named back = NbtIO.readLE(new ByteArrayInputStream(out.toByteArray()));
        assertThat(back.name()).isEmpty();
        assertThat(back.tag()).isEqualTo(sample());

        // A Bedrock root {format_version: 1}: type, u16 LE name length, then the int's name and value little-endian.
        ByteArrayOutputStream one = new ByteArrayOutputStream();
        NbtIO.writeLE(new CompoundTag().putInt("format_version", 1), "", one);
        byte[] b = one.toByteArray();
        assertThat(b[0]).isEqualTo(Tag.COMPOUND);
        assertThat(new byte[]{b[1], b[2]}).containsExactly(0, 0);
        assertThat(b[3]).isEqualTo(Tag.INT);
        assertThat(new byte[]{b[4], b[5]}).containsExactly(14, 0);
        assertThat(new byte[]{b[20], b[21], b[22], b[23]}).containsExactly(1, 0, 0, 0);
    }

    @Test
    void binaryRoundTripCompressedAndRaw() throws IOException {
        for (boolean gzip : new boolean[]{true, false}) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIO.write(sample(), "root", out, gzip);
            NbtIO.Named back = NbtIO.read(new ByteArrayInputStream(out.toByteArray()));
            assertThat(back.name()).isEqualTo("root");
            assertThat(back.tag()).isEqualTo(sample());
        }
    }

    @Test
    void snbtRoundTrip() {
        CompoundTag t = sample();
        String snbt = t.toSnbt();
        assertThat(Snbt.parse(snbt)).isEqualTo(t);
    }

    @Test
    void snbtParsesCommandSyntax() {
        CompoundTag t = Snbt.parseCompound("{Items:[{Slot:0b,id:\"minecraft:diamond\",count:3}], CustomName:'{\"text\":\"Hi\"}', flag:true, big:3000000000L, f:.5f}");
        assertThat(t.getList("Items").getCompound(0).getString("id")).isEqualTo("minecraft:diamond");
        assertThat(t.getList("Items").getCompound(0).getByte("Slot")).isEqualTo((byte) 0);
        assertThat(t.getString("CustomName")).isEqualTo("{\"text\":\"Hi\"}");
        assertThat(t.getBoolean("flag")).isTrue();
        assertThat(t.getLong("big")).isEqualTo(3_000_000_000L);
        assertThat(t.getFloat("f")).isEqualTo(0.5f);
    }

    @Test
    void listsRejectMixedTypes() {
        ListTag l = ListTag.ofInts(1);
        assertThatThrownBy(() -> l.add(new StringTag("x"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonCompoundRoot() {
        byte[] data = {Tag.INT, 0, 0, 0, 0, 0, 1};
        assertThatThrownBy(() -> NbtIO.read(new ByteArrayInputStream(data))).isInstanceOf(IOException.class);
    }
}
