package io.blockdesigner.core.model;

import io.blockdesigner.core.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A sparse voxel structure in local coordinates. Blocks live in 16³ sections indexing into a structure-wide palette,
 * so memory scales with occupied space rather than bounding volume. Not thread-safe; mutate from one thread.
 */
public final class Structure {
    public static final int SECTION_BITS = 4;
    public static final int SECTION_SIZE = 1 << SECTION_BITS;
    private static final int SECTION_MASK = SECTION_SIZE - 1;
    private static final int SECTION_VOLUME = SECTION_SIZE * SECTION_SIZE * SECTION_SIZE;

    /** Palette index 0 is always air. */
    private final List<BlockState> palette = new ArrayList<>(List.of(BlockState.AIR));
    private final Map<BlockState, Integer> paletteIndex = new HashMap<>(Map.of(BlockState.AIR, 0));
    private final Map<Long, Section> sections = new HashMap<>();
    private final Map<BlockPos, CompoundTag> blockEntities = new LinkedHashMap<>();
    private final List<StructureEntity> entities = new ArrayList<>();
    private final Metadata metadata = new Metadata();
    private long blockCount;
    private long modCount;

    /** A 16³ block section. {@code nonAir} tracks occupancy so empty sections can be dropped. */
    private static final class Section {
        final short[] blocks = new short[SECTION_VOLUME];
        int nonAir;

        Section copy() {
            Section s = new Section();
            System.arraycopy(blocks, 0, s.blocks, 0, SECTION_VOLUME);
            s.nonAir = nonAir;
            return s;
        }
    }

    /** Descriptive metadata carried through imports and exports. */
    public static final class Metadata {
        public String name = "";
        public String author = "";
        public String description = "";
        /** Minecraft DataVersion the structure was saved with; 0 if unknown. */
        public int dataVersion;

        void copyFrom(Metadata o) {
            name = o.name;
            author = o.author;
            description = o.description;
            dataVersion = o.dataVersion;
        }
    }

    @FunctionalInterface
    public interface BlockVisitor {
        void visit(int x, int y, int z, BlockState state);
    }

    public static long sectionKey(int sx, int sy, int sz) {
        return BlockPos.pack(sx, sy, sz);
    }

    private static int localIndex(int x, int y, int z) {
        return ((y & SECTION_MASK) << 8) | ((z & SECTION_MASK) << 4) | (x & SECTION_MASK);
    }

    public BlockState get(int x, int y, int z) {
        Section s = sections.get(sectionKey(x >> SECTION_BITS, y >> SECTION_BITS, z >> SECTION_BITS));
        return s == null ? BlockState.AIR : palette.get(s.blocks[localIndex(x, y, z)]);
    }

    public BlockState get(BlockPos p) {
        return get(p.x(), p.y(), p.z());
    }

    /** Sets a block and returns the previous state. Setting air clears any block entity at that position. */
    public BlockState set(int x, int y, int z, BlockState state) {
        long key = sectionKey(x >> SECTION_BITS, y >> SECTION_BITS, z >> SECTION_BITS);
        Section s = sections.get(key);
        boolean air = state.isAir();
        if (s == null) {
            if (air) return BlockState.AIR;
            s = new Section();
            sections.put(key, s);
        }
        int li = localIndex(x, y, z);
        short prevIdx = s.blocks[li];
        BlockState prev = palette.get(prevIdx);
        if (prev == state) return prev;
        short idx = (short) (air ? 0 : paletteId(state));
        s.blocks[li] = idx;
        boolean prevAir = prevIdx == 0;
        if (prevAir && idx != 0) {
            s.nonAir++;
            blockCount++;
        } else if (!prevAir && idx == 0) {
            s.nonAir--;
            blockCount--;
            if (s.nonAir == 0) sections.remove(key);
        }
        if (air || !prev.name().equals(state.name())) blockEntities.remove(new BlockPos(x, y, z));
        modCount++;
        return prev;
    }

    public BlockState set(BlockPos p, BlockState state) {
        return set(p.x(), p.y(), p.z(), state);
    }

    private int paletteId(BlockState state) {
        Integer id = paletteIndex.get(state);
        if (id != null) return id;
        if (palette.size() >= Short.MAX_VALUE) compactPalette();
        if (palette.size() >= Short.MAX_VALUE) throw new IllegalStateException("Too many distinct block states");
        int next = palette.size();
        palette.add(state);
        paletteIndex.put(state, next);
        return next;
    }

    /** Drops palette entries no longer referenced by any block. */
    public void compactPalette() {
        int[] used = new int[palette.size()];
        for (Section s : sections.values()) for (short b : s.blocks) used[b]++;
        List<BlockState> newPalette = new ArrayList<>(List.of(BlockState.AIR));
        short[] remap = new short[palette.size()];
        for (int i = 1; i < palette.size(); i++) {
            if (used[i] > 0) {
                remap[i] = (short) newPalette.size();
                newPalette.add(palette.get(i));
            }
        }
        for (Section s : sections.values()) for (int i = 0; i < SECTION_VOLUME; i++) s.blocks[i] = remap[s.blocks[i]];
        palette.clear();
        palette.addAll(newPalette);
        paletteIndex.clear();
        for (int i = 0; i < palette.size(); i++) paletteIndex.put(palette.get(i), i);
    }

    /** Distinct non-air states currently present. */
    public Set<BlockState> usedStates() {
        Set<BlockState> out = new java.util.LinkedHashSet<>();
        forEachBlock((x, y, z, s) -> out.add(s));
        return out;
    }

    /** Visits every non-air block. */
    public void forEachBlock(BlockVisitor visitor) {
        for (var e : sections.entrySet()) {
            BlockPos sp = BlockPos.unpack(e.getKey());
            int bx = sp.x() << SECTION_BITS, by = sp.y() << SECTION_BITS, bz = sp.z() << SECTION_BITS;
            short[] blocks = e.getValue().blocks;
            for (int i = 0; i < SECTION_VOLUME; i++) {
                short b = blocks[i];
                if (b != 0) visitor.visit(bx + (i & 15), by + (i >> 8), bz + ((i >> 4) & 15), palette.get(b));
            }
        }
    }

    /** Tight bounds of non-air blocks, or empty if the structure has no blocks. */
    public Optional<Box> bounds() {
        if (blockCount == 0) return Optional.empty();
        int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        forEachBlock((x, y, z, s) -> {
            if (x < b[0]) b[0] = x;
            if (y < b[1]) b[1] = y;
            if (z < b[2]) b[2] = z;
            if (x > b[3]) b[3] = x;
            if (y > b[4]) b[4] = y;
            if (z > b[5]) b[5] = z;
        });
        return Optional.of(new Box(b[0], b[1], b[2], b[3], b[4], b[5]));
    }

    /** Section coordinates (packed) that contain at least one block. */
    public Set<Long> sectionKeys() {
        return Collections.unmodifiableSet(sections.keySet());
    }

    public long blockCount() {
        return blockCount;
    }

    /** Increments on every effective change; useful for cheap dirty checks. */
    public long modCount() {
        return modCount;
    }

    public boolean isEmpty() {
        return blockCount == 0 && entities.isEmpty();
    }

    public Map<BlockPos, CompoundTag> blockEntities() {
        return Collections.unmodifiableMap(blockEntities);
    }

    public CompoundTag blockEntity(BlockPos pos) {
        return blockEntities.get(pos);
    }

    /** Stores block-entity data (without x/y/z/id position keys, which formats add on export). */
    public void setBlockEntity(BlockPos pos, CompoundTag nbt) {
        if (nbt == null || nbt.isEmpty()) blockEntities.remove(pos);
        else blockEntities.put(pos, nbt);
        modCount++;
    }

    public List<StructureEntity> entities() {
        return entities;
    }

    public Metadata metadata() {
        return metadata;
    }

    public Collection<BlockState> palette() {
        return Collections.unmodifiableList(palette);
    }

    public void clear() {
        sections.clear();
        blockEntities.clear();
        entities.clear();
        blockCount = 0;
        compactPalette();
        modCount++;
    }

    public Structure copy() {
        Structure c = new Structure();
        c.palette.clear();
        c.palette.addAll(palette);
        c.paletteIndex.clear();
        c.paletteIndex.putAll(paletteIndex);
        sections.forEach((k, v) -> c.sections.put(k, v.copy()));
        blockEntities.forEach((k, v) -> c.blockEntities.put(k, v.copy()));
        for (StructureEntity e : entities) c.entities.add(e.copy());
        c.metadata.copyFrom(metadata);
        c.blockCount = blockCount;
        return c;
    }

    /** Copies all blocks, block entities and entities from {@code src}, offset by {@code (dx,dy,dz)}; air in src is skipped. */
    public void paste(Structure src, int dx, int dy, int dz) {
        src.forEachBlock((x, y, z, s) -> set(x + dx, y + dy, z + dz, s));
        src.blockEntities.forEach((p, nbt) -> setBlockEntity(p.add(dx, dy, dz), nbt.copy()));
        for (StructureEntity e : src.entities) entities.add(e.translated(dx, dy, dz));
    }

    /** Moves all content so the minimum corner of its bounds sits at the origin; returns the applied shift. */
    public BlockPos normalizeToOrigin() {
        Optional<Box> b = bounds();
        if (b.isEmpty() || b.get().min().equals(BlockPos.ORIGIN)) return BlockPos.ORIGIN;
        BlockPos min = b.get().min();
        Structure moved = new Structure();
        moved.paste(this, -min.x(), -min.y(), -min.z());
        sections.clear();
        sections.putAll(moved.sections);
        palette.clear();
        palette.addAll(moved.palette);
        paletteIndex.clear();
        paletteIndex.putAll(moved.paletteIndex);
        blockEntities.clear();
        blockEntities.putAll(moved.blockEntities);
        entities.clear();
        entities.addAll(moved.entities);
        modCount++;
        return new BlockPos(-min.x(), -min.y(), -min.z());
    }

    /** True if both structures contain identical blocks, block entities and entities. */
    public boolean contentEquals(Structure o) {
        if (blockCount != o.blockCount) return false;
        boolean[] same = {true};
        forEachBlock((x, y, z, s) -> {
            if (same[0] && o.get(x, y, z) != s) same[0] = false;
        });
        return same[0] && blockEntities.equals(o.blockEntities) && entities.equals(o.entities);
    }
}
