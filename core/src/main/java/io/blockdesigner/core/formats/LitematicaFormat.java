package io.blockdesigner.core.formats;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.model.StructureEntity;
import io.blockdesigner.core.nbt.CompoundTag;
import io.blockdesigner.core.nbt.ListTag;
import io.blockdesigner.core.nbt.Tag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Litematica schematics ({@code .litematic}). Holds any number of regions; sizes may be negative (the region extends
 * from its position in the negative direction). Block states are tightly bit-packed into a long array with entries
 * allowed to span two longs, indexed {@code (y * sizeZ + z) * sizeX + x}.
 */
public final class LitematicaFormat implements SchematicFormat {
    static final int WRITE_VERSION = 6;

    @Override
    public String id() {
        return "litematica";
    }

    @Override
    public String displayName() {
        return "Litematica (.litematic)";
    }

    @Override
    public List<String> extensions() {
        return List.of("litematic");
    }

    @Override
    public boolean detect(CompoundTag root) {
        return root.contains("Regions", Tag.COMPOUND) && root.contains("Metadata", Tag.COMPOUND);
    }

    @Override
    public SchematicFile read(CompoundTag root) throws IOException {
        CompoundTag meta = root.getCompound("Metadata");
        int dataVersion = root.getInt("MinecraftDataVersion");
        List<SchematicFile.Region> regions = new ArrayList<>();
        CompoundTag regionsTag = root.getCompound("Regions");

        for (String regionName : regionsTag.keys()) {
            CompoundTag r = regionsTag.getCompound(regionName);
            BlockPos position = vec(r.getCompound("Position"));
            BlockPos size = vec(r.getCompound("Size"));
            int sx = Math.abs(size.x()), sy = Math.abs(size.y()), sz = Math.abs(size.z());
            // Minimum corner of the region relative to the schematic origin.
            BlockPos min = new BlockPos(
                    position.x() + (size.x() < 0 ? size.x() + 1 : 0),
                    position.y() + (size.y() < 0 ? size.y() + 1 : 0),
                    position.z() + (size.z() < 0 ? size.z() + 1 : 0));

            ListTag paletteTag = r.getList("BlockStatePalette");
            List<BlockState> palette = new ArrayList<>(paletteTag.size());
            for (CompoundTag p : paletteTag.compounds()) palette.add(BlockState.fromNbt(p));
            if (palette.isEmpty()) palette.add(BlockState.AIR);

            Structure s = new Structure();
            s.metadata().dataVersion = dataVersion;
            long volume = (long) sx * sy * sz;
            if (volume > Integer.MAX_VALUE) throw new IOException("Region " + regionName + " too large");
            int[] ids = FormatUtil.unpackTight(r.getLongArray("BlockStates"), FormatUtil.bitsFor(palette.size()), (int) volume);
            int i = 0;
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++, i++) {
                        int id = ids[i];
                        if (id >= palette.size()) throw new IOException("Palette index " + id + " out of range in " + regionName);
                        BlockState st = palette.get(id);
                        if (!st.isAir()) s.set(x, y, z, st);
                    }
                }
            }
            for (CompoundTag te : r.getList("TileEntities").compounds()) {
                BlockPos p = new BlockPos(te.getInt("x"), te.getInt("y"), te.getInt("z"));
                s.setBlockEntity(p, FormatUtil.cleanBlockEntity(te, "id"));
            }
            for (CompoundTag e : r.getList("Entities").compounds()) {
                s.addEntity(StructureEntity.fromFullNbt(e, 0, 0, 0));
            }
            regions.add(new SchematicFile.Region(regionName, s, min));
        }
        return new SchematicFile(meta.getString("Name"), meta.getString("Author"), meta.getString("Description"), dataVersion, regions);
    }

    @Override
    public Encoded write(SchematicFile file, WriteOptions options) {
        CompoundTag regionsTag = new CompoundTag();
        Box enclosing = null;
        long totalBlocks = 0, totalVolume = 0;
        // Litematica uses region positions relative to the schematic origin; normalise so the smallest corner is 0,0,0.
        List<SchematicFile.Region> placed = new ArrayList<>();
        for (SchematicFile.Region region : file.regions()) {
            Structure s = region.structure().copy();
            BlockPos shift = s.normalizeToOrigin();
            BlockPos pos = region.position().subtract(shift);
            placed.add(new SchematicFile.Region(region.name(), s, pos));
            Box b = FormatUtil.boundsOrUnit(s).offset(pos.x(), pos.y(), pos.z());
            enclosing = enclosing == null ? b : enclosing.union(b);
        }
        if (enclosing == null) enclosing = new Box(0, 0, 0, 0, 0, 0);
        java.util.Set<String> usedNames = new java.util.HashSet<>();

        for (SchematicFile.Region region : placed) {
            Structure s = region.structure();
            Box box = FormatUtil.boundsOrUnit(s);
            BlockPos pos = region.position().subtract(enclosing.min());
            int sx = box.sizeX(), sy = box.sizeY(), sz = box.sizeZ();

            List<BlockState> palette = new ArrayList<>(List.of(BlockState.AIR));
            Map<BlockState, Integer> index = new HashMap<>(Map.of(BlockState.AIR, 0));
            int[] ids = new int[sx * sy * sz];
            int i = 0;
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++, i++) {
                        BlockState st = s.get(x, y, z);
                        if (st.isAir()) continue;
                        ids[i] = index.computeIfAbsent(st, k -> {
                            palette.add(k);
                            return palette.size() - 1;
                        });
                    }
                }
            }
            ListTag paletteTag = new ListTag(Tag.COMPOUND);
            palette.forEach(p -> paletteTag.add(p.toNbt()));

            ListTag tiles = new ListTag(Tag.COMPOUND);
            s.blockEntities().forEach((p, nbt) -> tiles.add(nbt.copy().putInt("x", p.x()).putInt("y", p.y()).putInt("z", p.z())));
            ListTag entities = new ListTag(Tag.COMPOUND);
            for (StructureEntity e : s.entities()) entities.add(e.toFullNbt(0, 0, 0));

            CompoundTag r = new CompoundTag()
                    .put("Position", vecTag(pos.x(), pos.y(), pos.z()))
                    .put("Size", vecTag(sx, sy, sz))
                    .put("BlockStatePalette", paletteTag)
                    .putLongArray("BlockStates", FormatUtil.packTight(ids, FormatUtil.bitsFor(palette.size())))
                    .put("TileEntities", tiles)
                    .put("Entities", entities)
                    .put("PendingBlockTicks", new ListTag(Tag.COMPOUND))
                    .put("PendingFluidTicks", new ListTag(Tag.COMPOUND));
            String name = region.name().isBlank() ? "Region" : region.name();
            String unique = name;
            for (int n = 2; !usedNames.add(unique); n++) unique = name + " " + n;
            regionsTag.put(unique, r);
            totalBlocks += s.blockCount();
            totalVolume += (long) sx * sy * sz;
        }

        long now = System.currentTimeMillis();
        CompoundTag meta = new CompoundTag()
                .putString("Name", file.name().isBlank() ? "Unnamed" : file.name())
                .putString("Author", file.author())
                .putString("Description", file.description())
                .putInt("RegionCount", placed.size())
                .putInt("TotalVolume", (int) Math.min(Integer.MAX_VALUE, totalVolume))
                .putInt("TotalBlocks", (int) Math.min(Integer.MAX_VALUE, totalBlocks))
                .putLong("TimeCreated", now)
                .putLong("TimeModified", now)
                .put("EnclosingSize", vecTag(enclosing.sizeX(), enclosing.sizeY(), enclosing.sizeZ()));

        CompoundTag root = new CompoundTag()
                .putInt("MinecraftDataVersion", options.version().dataVersion())
                .putInt("Version", WRITE_VERSION)
                .put("Metadata", meta)
                .put("Regions", regionsTag);
        return new Encoded("", root);
    }

    private static BlockPos vec(CompoundTag t) {
        return new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z"));
    }

    private static CompoundTag vecTag(int x, int y, int z) {
        return new CompoundTag().putInt("x", x).putInt("y", y).putInt("z", z);
    }
}
