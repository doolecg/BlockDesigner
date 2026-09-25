package io.blockdesigner.core.formats;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.model.StructureEntity;
import io.blockdesigner.core.nbt.CompoundTag;
import io.blockdesigner.core.nbt.IntTag;
import io.blockdesigner.core.nbt.ListTag;
import io.blockdesigner.core.nbt.Tag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sponge schematic ({@code .schem}) as used by WorldEdit and FAWE. Reads versions 2 and 3; writes either.
 * Blocks are varint palette indices ordered {@code x + z * Width + y * Width * Length}.
 */
public final class SpongeSchematicFormat implements SchematicFormat {

    @Override
    public String id() {
        return "sponge";
    }

    @Override
    public String displayName() {
        return "WorldEdit (.schem)";
    }

    @Override
    public List<String> extensions() {
        return List.of("schem");
    }

    @Override
    public boolean detect(CompoundTag root) {
        CompoundTag s = body(root);
        return s.contains("Width") && s.contains("Height") && s.contains("Length") && (s.contains("BlockData") || s.contains("Blocks", Tag.COMPOUND));
    }

    /** v3 nests everything in a "Schematic" compound; v2 has it at the root. */
    private static CompoundTag body(CompoundTag root) {
        return root.findCompound("Schematic").orElse(root);
    }

    @Override
    public SchematicFile read(CompoundTag root) throws IOException {
        CompoundTag s = body(root);
        int version = s.getInt("Version", 2);
        int w = s.getShort("Width") & 0xFFFF, h = s.getShort("Height") & 0xFFFF, l = s.getShort("Length") & 0xFFFF;
        CompoundTag paletteTag;
        byte[] data;
        ListTag blockEntities;
        if (version >= 3) {
            CompoundTag blocks = s.getCompound("Blocks");
            paletteTag = blocks.getCompound("Palette");
            data = blocks.getByteArray("Data");
            blockEntities = blocks.getList("BlockEntities");
        } else {
            paletteTag = s.getCompound("Palette");
            data = s.getByteArray("BlockData");
            blockEntities = s.contains("BlockEntities") ? s.getList("BlockEntities") : s.getList("TileEntities");
        }
        int maxId = 0;
        for (Tag t : paletteTag.entries().values()) maxId = Math.max(maxId, t.asNumber() == null ? 0 : t.asNumber().intValue());
        BlockState[] palette = new BlockState[maxId + 1];
        java.util.Arrays.fill(palette, BlockState.AIR);
        for (var e : paletteTag.entries().entrySet()) {
            Number n = e.getValue().asNumber();
            if (n != null) palette[n.intValue()] = parseState(e.getKey());
        }

        Structure st = new Structure();
        st.metadata().dataVersion = s.getInt("DataVersion");
        int[] ids = FormatUtil.decodeVarInts(data, w * h * l);
        int i = 0;
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++, i++) {
                    int id = ids[i];
                    if (id < 0 || id >= palette.length) throw new IOException("Palette index " + id + " out of range");
                    if (!palette[id].isAir()) st.set(x, y, z, palette[id]);
                }
            }
        }
        for (CompoundTag be : blockEntities.compounds()) {
            BlockPos p = FormatUtil.pos(be.getIntArray("Pos"));
            CompoundTag nbt = version >= 3 ? be.getCompound("Data").copy() : FormatUtil.cleanBlockEntity(be, "Id");
            String id = be.getString("Id");
            if (!id.isEmpty()) nbt.putString("id", id);
            nbt.remove("x");
            nbt.remove("y");
            nbt.remove("z");
            st.setBlockEntity(p, nbt);
        }
        // Entity positions are relative to the schematic's paste origin, which sits at -Offset from the min corner.
        int[] offset = s.getIntArray("Offset");
        double ox = offset.length == 3 ? offset[0] : 0, oy = offset.length == 3 ? offset[1] : 0, oz = offset.length == 3 ? offset[2] : 0;
        for (CompoundTag e : s.getList("Entities").compounds()) {
            CompoundTag full = version >= 3 ? e.getCompound("Data").copy() : e.copy();
            full.put("Pos", e.getList("Pos"));
            String id = e.getString("Id");
            if (!id.isEmpty()) full.putString("id", id);
            full.remove("Id");
            st.entities().add(StructureEntity.fromFullNbt(full, -ox, -oy, -oz));
        }
        CompoundTag meta = s.getCompound("Metadata");
        return new SchematicFile(meta.getString("Name"), meta.getString("Author"), "", st.metadata().dataVersion,
                List.of(new SchematicFile.Region("main", st, BlockPos.ORIGIN)));
    }

    private static BlockState parseState(String s) {
        try {
            return BlockState.parse(s);
        } catch (IllegalArgumentException e) {
            return BlockState.of(s.replaceAll("\\[.*", ""));
        }
    }

    @Override
    public Encoded write(SchematicFile file, WriteOptions options) {
        Structure s = FormatUtil.normalized(file.merged());
        Box box = FormatUtil.boundsOrUnit(s);
        int w = box.sizeX(), h = box.sizeY(), l = box.sizeZ();

        Map<BlockState, Integer> index = new HashMap<>();
        List<BlockState> order = new ArrayList<>();
        index.put(BlockState.AIR, 0);
        order.add(BlockState.AIR);
        int[] ids = new int[w * h * l];
        int i = 0;
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++, i++) {
                    BlockState st = s.get(x, y, z);
                    ids[i] = index.computeIfAbsent(st, k -> {
                        order.add(k);
                        return order.size() - 1;
                    });
                }
            }
        }
        CompoundTag palette = new CompoundTag();
        for (int p = 0; p < order.size(); p++) palette.put(order.get(p).toString(), new IntTag(p));
        byte[] data = FormatUtil.encodeVarInts(ids);

        boolean v3 = options.spongeVersion() >= 3;
        ListTag bes = new ListTag(Tag.COMPOUND);
        s.blockEntities().forEach((p, nbt) -> {
            CompoundTag data2 = nbt.copy();
            String id = data2.getString("id");
            CompoundTag be = new CompoundTag().putIntArray("Pos", p.x(), p.y(), p.z()).putString("Id", id);
            if (v3) {
                data2.remove("id");
                be.put("Data", data2);
            } else {
                data2.remove("id");
                data2.entries().forEach(be::put);
            }
            bes.add(be);
        });
        ListTag ents = new ListTag(Tag.COMPOUND);
        for (StructureEntity e : s.entities()) {
            CompoundTag full = e.nbt().copy();
            String id = full.getString("id");
            full.remove("id");
            CompoundTag ent = new CompoundTag().put("Pos", ListTag.ofDoubles(e.x(), e.y(), e.z())).putString("Id", id);
            if (v3) ent.put("Data", full);
            else full.entries().forEach(ent::put);
            ents.add(ent);
        }

        CompoundTag meta = new CompoundTag();
        if (!file.name().isEmpty()) meta.putString("Name", file.name());
        if (!file.author().isEmpty()) meta.putString("Author", file.author());
        meta.putLong("Date", System.currentTimeMillis());

        CompoundTag body = new CompoundTag()
                .putInt("Version", v3 ? 3 : 2)
                .putInt("DataVersion", options.version().dataVersion())
                .put("Metadata", meta)
                .putShort("Width", w)
                .putShort("Height", h)
                .putShort("Length", l)
                .putIntArray("Offset", 0, 0, 0);
        if (v3) {
            body.put("Blocks", new CompoundTag().put("Palette", palette).putByteArray("Data", data).put("BlockEntities", bes));
            body.put("Entities", ents);
            return new Encoded("", new CompoundTag().put("Schematic", body));
        }
        body.putInt("PaletteMax", order.size())
                .put("Palette", palette)
                .putByteArray("BlockData", data)
                .put("BlockEntities", bes)
                .put("Entities", ents);
        return new Encoded("Schematic", body);
    }
}
