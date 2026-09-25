package io.blockdesigner.core.formats;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.model.StructureEntity;
import io.blockdesigner.core.nbt.CompoundTag;
import io.blockdesigner.core.nbt.ListTag;
import io.blockdesigner.core.version.McVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class FormatRoundTripTest {
    static final McVersion V = McVersion.byId("1.21.1").orElseThrow();

    @TempDir
    Path tmp;

    /** A structure with {@code distinctStates} different block states, a block entity and an entity. */
    static Structure sample(int distinctStates) {
        Structure s = new Structure();
        Random rnd = new Random(42);
        for (int i = 0; i < distinctStates; i++) {
            BlockState st = BlockState.of("minecraft:test_block", java.util.Map.of("variant", Integer.toString(i)));
            s.set(i % 9, (i / 81) % 7, (i / 9) % 9, st);
        }
        for (int i = 0; i < 200; i++) s.set(rnd.nextInt(9), 7 + rnd.nextInt(3), rnd.nextInt(9), BlockState.of("stone"));
        s.set(4, 11, 4, BlockState.parse("chest[facing=north,type=single,waterlogged=false]"));
        s.setBlockEntity(new BlockPos(4, 11, 4), new CompoundTag().putString("id", "minecraft:chest")
                .put("Items", ListTag.of(new CompoundTag().putByte("Slot", 0).putString("id", "minecraft:diamond").putInt("count", 5))));
        s.entities().add(new StructureEntity(2.5, 12.0, 3.5, new CompoundTag().putString("id", "minecraft:armor_stand").putBoolean("Invisible", false)));
        return s;
    }

    static List<SchematicFormat> formats() {
        return Schematics.formats();
    }

    @ParameterizedTest
    @MethodSource("formats")
    void roundTripPreservesEverything(SchematicFormat format) throws IOException {
        Structure original = sample(40);
        Path file = tmp.resolve("out." + format.extensions().getFirst());
        Schematics.write(SchematicFile.single("Sample", original), format, WriteOptions.defaults(V), file);
        SchematicFile back = Schematics.read(file);
        Structure read = back.merged();
        assertThat(read.contentEquals(original)).as("content equal for " + format.id()).isTrue();
        assertThat(back.dataVersion()).isEqualTo(V.dataVersion());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 17, 300, 1100})
    void litematicaBitPackingAtPaletteSizes(int states) throws IOException {
        Structure original = sample(states);
        Path file = tmp.resolve("p" + states + ".litematic");
        Schematics.write(SchematicFile.single("p", original), Schematics.LITEMATICA, WriteOptions.defaults(V), file);
        assertThat(Schematics.read(file).merged().contentEquals(original)).isTrue();
    }

    @Test
    void packTightMatchesKnownLayout() throws IOException {
        // 3 bits per entry: entry 21 straddles longs 0 and 1 (bits 63..65).
        int[] values = new int[30];
        for (int i = 0; i < values.length; i++) values[i] = i % 8;
        long[] packed = FormatUtil.packTight(values, 3);
        assertThat(packed).hasSize(2);
        assertThat(FormatUtil.unpackTight(packed, 3, values.length)).containsExactly(values);
        assertThat(FormatUtil.bitsFor(2)).isEqualTo(2);
        assertThat(FormatUtil.bitsFor(5)).isEqualTo(3);
        assertThat(FormatUtil.bitsFor(17)).isEqualTo(5);
        assertThat(FormatUtil.bitsFor(300)).isEqualTo(9);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void spongeVersionsBothRoundTrip(int version) throws IOException {
        Structure original = sample(30);
        Path file = tmp.resolve("v" + version + ".schem");
        Schematics.write(SchematicFile.single("s", original), Schematics.SPONGE, WriteOptions.defaults(V).withSpongeVersion(version), file);
        assertThat(Schematics.read(file).merged().contentEquals(original)).isTrue();
    }

    @Test
    void crossFormatConversionChain() throws IOException {
        Structure original = sample(64);
        SchematicFile cur = SchematicFile.single("chain", original);
        for (SchematicFormat f : List.of(Schematics.LITEMATICA, Schematics.SPONGE, Schematics.VANILLA, Schematics.LITEMATICA)) {
            Path file = tmp.resolve("chain." + f.extensions().getFirst());
            Schematics.write(cur, f, WriteOptions.defaults(V), file);
            cur = Schematics.read(file);
        }
        assertThat(cur.merged().contentEquals(original)).isTrue();
    }

    @Test
    void litematicaNegativeSizeRegions() throws IOException {
        // Build a Litematica file by hand with a region whose size is negative on every axis.
        Structure s = sample(10);
        SchematicFormat.Encoded enc = Schematics.LITEMATICA.write(SchematicFile.single("neg", s), WriteOptions.defaults(V));
        CompoundTag region = enc.root().getCompound("Regions").getCompound("neg");
        CompoundTag size = region.getCompound("Size");
        int sx = size.getInt("x"), sy = size.getInt("y"), sz = size.getInt("z");
        // Same blocks, described from the opposite corner.
        region.put("Position", new CompoundTag().putInt("x", sx - 1).putInt("y", sy - 1).putInt("z", sz - 1));
        region.put("Size", new CompoundTag().putInt("x", -sx).putInt("y", -sy).putInt("z", -sz));
        SchematicFile back = Schematics.LITEMATICA.read(enc.root());
        assertThat(back.regions().getFirst().position()).isEqualTo(BlockPos.ORIGIN);
        assertThat(back.merged().contentEquals(s)).isTrue();
    }

    @Test
    void multiRegionLitematicKeepsRelativePositions() throws IOException {
        Structure a = new Structure();
        a.set(0, 0, 0, BlockState.of("stone"));
        Structure b = new Structure();
        b.set(0, 0, 0, BlockState.of("dirt"));
        SchematicFile file = new SchematicFile("multi", "me", "", V.dataVersion(), List.of(
                new SchematicFile.Region("A", a, new BlockPos(0, 0, 0)),
                new SchematicFile.Region("B", b, new BlockPos(5, 2, -3))));
        Path out = tmp.resolve("multi.litematic");
        Schematics.write(file, Schematics.LITEMATICA, WriteOptions.defaults(V), out);
        SchematicFile back = Schematics.read(out);
        assertThat(back.regions()).hasSize(2);
        Structure merged = back.merged();
        merged.normalizeToOrigin();
        assertThat(merged.get(0, 0, 3).path()).isEqualTo("stone");
        assertThat(merged.get(5, 2, 0).path()).isEqualTo("dirt");
        assertThat(back.author()).isEqualTo("me");
    }

    @Test
    void vanillaIncludeAirControlsExplicitAir() {
        Structure s = new Structure();
        s.set(0, 0, 0, BlockState.of("stone"));
        s.set(2, 0, 0, BlockState.of("stone"));
        CompoundTag with = Schematics.VANILLA.write(SchematicFile.single("a", s), WriteOptions.defaults(V)).root();
        CompoundTag without = Schematics.VANILLA.write(SchematicFile.single("a", s), WriteOptions.defaults(V).withIncludeAir(false)).root();
        assertThat(with.getList("blocks").size()).isEqualTo(3);
        assertThat(without.getList("blocks").size()).isEqualTo(2);
        assertThat(with.getList("size").getInt(0)).isEqualTo(3);
    }

    // ---- real files from launchers (skipped when testdata/ is absent) ------------------------------------------

    static Structure normalized(Structure s) {
        Structure c = s.copy();
        c.normalizeToOrigin();
        return c;
    }

    static Path testdata(String name) {
        Path p = Path.of("..", "testdata", name);
        return Files.exists(p) ? p : Path.of("testdata", name);
    }

    @Test
    void readsRealLitematic() throws IOException {
        Path p = testdata("Pinecrest Watchtower.litematic");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(p));
        SchematicFile f = Schematics.read(p);
        assertThat(f.totalBlocks()).isPositive();
        Structure merged = normalized(f.merged());
        Path out = tmp.resolve("rt.litematic");
        Schematics.write(f, Schematics.LITEMATICA, WriteOptions.defaults(McVersion.forDataVersion(f.dataVersion())), out);
        assertThat(normalized(Schematics.read(out).merged()).contentEquals(merged)).isTrue();
        Box b = merged.bounds().orElseThrow();
        System.out.printf("Pinecrest: %d blocks, %s, %d regions, DataVersion %d%n", f.totalBlocks(), b, f.regions().size(), f.dataVersion());
    }

    @Test
    void readsRealStructureNbt() throws IOException {
        Path p = testdata("draconic_upgrade.nbt");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(p));
        SchematicFile f = Schematics.read(p);
        assertThat(f.totalBlocks()).isPositive();
        Path out = tmp.resolve("rt.nbt");
        Schematics.write(f, Schematics.VANILLA, WriteOptions.defaults(McVersion.forDataVersion(f.dataVersion())), out);
        assertThat(normalized(Schematics.read(out).merged()).contentEquals(normalized(f.merged()))).isTrue();
        System.out.printf("draconic_upgrade: %d blocks, states %s%n", f.totalBlocks(), f.merged().usedStates());
    }
}
