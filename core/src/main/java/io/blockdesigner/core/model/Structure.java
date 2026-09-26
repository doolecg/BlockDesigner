package io.blockdesigner.core.model;

import io.blockdesigner.core.nbt.CompoundTag;
import io.blockdesigner.core.util.LongObjectMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
    private final LongObjectMap<Section> sections = new LongObjectMap<>();
    private final Map<BlockPos, CompoundTag> blockEntities = new LinkedHashMap<>();
    private final List<StructureEntity> entities = new ArrayList<>();
    private final Metadata metadata = new Metadata();
    private long blockCount;
    private long modCount;
    /**
     * Tight bounds of the blocks, kept up to date incrementally: placing grows it, and only removing a block on its
     * surface invalidates it (the next {@link #bounds()} rescans).
     */
    private int bMinX, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ;
    private boolean boundsValid = true;
    /**
     * Identifies the block content: {@link #copy()} gives the copy the same id, and the first edit after a copy gives
     * the edited structure a fresh one. Equal ids therefore mean identical blocks and block entities, which lets the
     * renderer draw duplicated layers from one shared mesh (instancing) instead of meshing each copy.
     */
    private static final java.util.concurrent.atomic.AtomicLong CONTENT_IDS = new java.util.concurrent.atomic.AtomicLong();
    private long contentId = CONTENT_IDS.incrementAndGet();
    private boolean contentShared;

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

    /** True if there is a non-air block at the position (no palette lookup). */
    public boolean has(int x, int y, int z) {
        Section s = sections.get(sectionKey(x >> SECTION_BITS, y >> SECTION_BITS, z >> SECTION_BITS));
        return s != null && s.blocks[localIndex(x, y, z)] != 0;
    }

    /**
     * Copies the states of the box {@code [x0, x0+sx) × [y0, y0+sy) × [z0, z0+sz)} into {@code out}, indexed
     * {@code (y * sz + z) * sx + x}. Reads each overlapped section once instead of hashing per block, which is what
     * meshing snapshots need.
     */
    public void copyRegion(int x0, int y0, int z0, int sx, int sy, int sz, BlockState[] out) {
        Arrays.fill(out, 0, sx * sy * sz, BlockState.AIR);
        if (blockCount == 0) return;
        int x1 = x0 + sx - 1, y1 = y0 + sy - 1, z1 = z0 + sz - 1;
        for (int cy = y0 >> SECTION_BITS; cy <= y1 >> SECTION_BITS; cy++) {
            for (int cz = z0 >> SECTION_BITS; cz <= z1 >> SECTION_BITS; cz++) {
                for (int cx = x0 >> SECTION_BITS; cx <= x1 >> SECTION_BITS; cx++) {
                    Section sec = sections.get(sectionKey(cx, cy, cz));
                    if (sec == null) continue;
                    short[] blocks = sec.blocks;
                    int ax = Math.max(x0, cx << SECTION_BITS), bx = Math.min(x1, (cx << SECTION_BITS) + SECTION_MASK);
                    int ay = Math.max(y0, cy << SECTION_BITS), by = Math.min(y1, (cy << SECTION_BITS) + SECTION_MASK);
                    int az = Math.max(z0, cz << SECTION_BITS), bz = Math.min(z1, (cz << SECTION_BITS) + SECTION_MASK);
                    for (int y = ay; y <= by; y++) {
                        for (int z = az; z <= bz; z++) {
                            int src = localIndex(0, y, z), dst = ((y - y0) * sz + (z - z0)) * sx - x0;
                            for (int x = ax; x <= bx; x++) {
                                short b = blocks[src + (x & SECTION_MASK)];
                                if (b != 0) out[dst + x] = palette.get(b);
                            }
                        }
                    }
                }
            }
        }
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
            growBounds(x, y, z);
        } else if (!prevAir && idx == 0) {
            s.nonAir--;
            blockCount--;
            if (s.nonAir == 0) sections.remove(key);
            shrinkBounds(x, y, z);
        }
        if (!blockEntities.isEmpty() && (air || !prev.name().equals(state.name()))) blockEntities.remove(new BlockPos(x, y, z));
        changed();
        return prev;
    }

    public BlockState set(BlockPos p, BlockState state) {
        return set(p.x(), p.y(), p.z(), state);
    }

    private void changed() {
        modCount++;
        if (contentShared) {
            contentShared = false;
            contentId = CONTENT_IDS.incrementAndGet();
        }
    }

    /** See {@link #contentId}: structures with equal ids hold the same blocks. */
    public long contentId() {
        return contentId;
    }

    private void growBounds(int x, int y, int z) {
        if (!boundsValid) return;
        if (blockCount == 1) {
            bMinX = bMaxX = x;
            bMinY = bMaxY = y;
            bMinZ = bMaxZ = z;
            return;
        }
        if (x < bMinX) bMinX = x;
        if (y < bMinY) bMinY = y;
        if (z < bMinZ) bMinZ = z;
        if (x > bMaxX) bMaxX = x;
        if (y > bMaxY) bMaxY = y;
        if (z > bMaxZ) bMaxZ = z;
    }

    private void shrinkBounds(int x, int y, int z) {
        // Removing a block strictly inside the box cannot change it; one on its surface might.
        if (boundsValid && blockCount > 0 && (x == bMinX || x == bMaxX || y == bMinY || y == bMaxY || z == bMinZ || z == bMaxZ)) {
            boundsValid = false;
        }
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
        sections.forEachValue(s -> {
            for (short b : s.blocks) used[b]++;
        });
        List<BlockState> newPalette = new ArrayList<>(List.of(BlockState.AIR));
        short[] remap = new short[palette.size()];
        for (int i = 1; i < palette.size(); i++) {
            if (used[i] > 0) {
                remap[i] = (short) newPalette.size();
                newPalette.add(palette.get(i));
            }
        }
        sections.forEachValue(s -> {
            for (int i = 0; i < SECTION_VOLUME; i++) s.blocks[i] = remap[s.blocks[i]];
        });
        palette.clear();
        palette.addAll(newPalette);
        paletteIndex.clear();
        for (int i = 0; i < palette.size(); i++) paletteIndex.put(palette.get(i), i);
    }

    /** Distinct non-air states currently present. */
    public Set<BlockState> usedStates() {
        // Mark palette ids in use rather than visiting every block through a callback.
        boolean[] used = new boolean[palette.size()];
        sections.forEachValue(s -> {
            for (short b : s.blocks) used[b] = true;
        });
        Set<BlockState> out = new java.util.LinkedHashSet<>();
        for (int i = 1; i < used.length; i++) if (used[i]) out.add(palette.get(i));
        return out;
    }

    /** Number of blocks of each distinct non-air state. */
    public Map<BlockState, Long> stateCounts() {
        long[] counts = new long[palette.size()];
        sections.forEachValue(s -> {
            for (short b : s.blocks) counts[b]++;
        });
        Map<BlockState, Long> out = new LinkedHashMap<>();
        for (int i = 1; i < counts.length; i++) if (counts[i] > 0) out.put(palette.get(i), counts[i]);
        return out;
    }

    /**
     * Visits every non-air block. The section list is captured up front, so the visitor may edit this structure
     * (blocks it adds in brand-new sections are not visited).
     */
    public void forEachBlock(BlockVisitor visitor) {
        for (long key : sections.keys()) {
            Section sec = sections.get(key);
            if (sec == null) continue;
            BlockPos sp = BlockPos.unpack(key);
            int bx = sp.x() << SECTION_BITS, by = sp.y() << SECTION_BITS, bz = sp.z() << SECTION_BITS;
            short[] blocks = sec.blocks;
            for (int i = 0; i < SECTION_VOLUME; i++) {
                short b = blocks[i];
                if (b != 0) visitor.visit(bx + (i & 15), by + (i >> 8), bz + ((i >> 4) & 15), palette.get(b));
            }
        }
    }

    /** Tight bounds of non-air blocks, or empty if the structure has no blocks. Cached, so cheap enough per frame. */
    public Optional<Box> bounds() {
        if (blockCount == 0) return Optional.empty();
        if (!boundsValid) recomputeBounds();
        return Optional.of(new Box(bMinX, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ));
    }

    /** Full rescan; sections lying wholly inside the box found so far are skipped. */
    private void recomputeBounds() {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long key : sections.keys()) {
            BlockPos sp = BlockPos.unpack(key);
            int bx = sp.x() << SECTION_BITS, by = sp.y() << SECTION_BITS, bz = sp.z() << SECTION_BITS;
            if (bx >= minX && bx + SECTION_MASK <= maxX && by >= minY && by + SECTION_MASK <= maxY && bz >= minZ && bz + SECTION_MASK <= maxZ) continue;
            short[] blocks = sections.get(key).blocks;
            for (int i = 0; i < SECTION_VOLUME; i++) {
                if (blocks[i] == 0) continue;
                int x = bx + (i & 15), y = by + (i >> 8), z = bz + ((i >> 4) & 15);
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (z < minZ) minZ = z;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
                if (z > maxZ) maxZ = z;
            }
        }
        bMinX = minX;
        bMinY = minY;
        bMinZ = minZ;
        bMaxX = maxX;
        bMaxY = maxY;
        bMaxZ = maxZ;
        boundsValid = true;
    }

    /** Section coordinates (packed) that contain at least one block: a snapshot, later edits don't show in it. */
    public Set<Long> sectionKeys() {
        long[] keys = sections.keys();
        Set<Long> out = new HashSet<>(keys.length * 2);
        for (long k : keys) out.add(k);
        return Collections.unmodifiableSet(out);
    }

    /** As {@link #sectionKeys()}, without boxing. */
    public long[] sectionKeyArray() {
        return sections.keys();
    }

    /** Number of 16³ sections holding blocks. */
    public int sectionCount() {
        return sections.size();
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
        changed();
    }

    /** The entities (mobs, armour stands, item frames…), read-only: change them with the methods below. */
    public List<StructureEntity> entities() {
        return Collections.unmodifiableList(entities);
    }

    public void addEntity(StructureEntity e) {
        entities.add(java.util.Objects.requireNonNull(e));
        changed();
    }

    /** Removes the entity at {@code index}; returns it. */
    public StructureEntity removeEntity(int index) {
        StructureEntity e = entities.remove(index);
        changed();
        return e;
    }

    public void setEntity(int index, StructureEntity e) {
        entities.set(index, java.util.Objects.requireNonNull(e));
        changed();
    }

    /** Replaces every entity (undo and redo restore whole lists). */
    public void setEntities(List<StructureEntity> list) {
        entities.clear();
        entities.addAll(list);
        changed();
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
        boundsValid = true;
        compactPalette();
        changed();
    }

    public Structure copy() {
        Structure c = new Structure();
        c.palette.clear();
        c.palette.addAll(palette);
        c.paletteIndex.clear();
        c.paletteIndex.putAll(paletteIndex);
        sections.forEach((k, v) -> c.sections.put(k, v.copy()));
        c.bMinX = bMinX;
        c.bMinY = bMinY;
        c.bMinZ = bMinZ;
        c.bMaxX = bMaxX;
        c.bMaxY = bMaxY;
        c.bMaxZ = bMaxZ;
        c.boundsValid = boundsValid;
        c.contentId = contentId;
        c.contentShared = contentShared = true;
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
        if (!src.entities.isEmpty()) changed();
    }

    /** Moves all content so the minimum corner of its bounds sits at the origin; returns the applied shift. */
    public BlockPos normalizeToOrigin() {
        Optional<Box> b = bounds();
        if (b.isEmpty() || b.get().min().equals(BlockPos.ORIGIN)) return BlockPos.ORIGIN;
        BlockPos min = b.get().min();
        Structure moved = new Structure();
        moved.paste(this, -min.x(), -min.y(), -min.z());
        sections.clear();
        moved.sections.forEach(sections::put);
        boundsValid = false;
        palette.clear();
        palette.addAll(moved.palette);
        paletteIndex.clear();
        paletteIndex.putAll(moved.paletteIndex);
        blockEntities.clear();
        blockEntities.putAll(moved.blockEntities);
        entities.clear();
        entities.addAll(moved.entities);
        changed();
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
