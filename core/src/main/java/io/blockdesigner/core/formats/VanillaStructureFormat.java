package io.blockdesigner.core.formats;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.model.StructureEntity;
import io.blockdesigner.core.nbt.CompoundTag;
import io.blockdesigner.core.nbt.ListTag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vanilla structure template ({@code .nbt}), as written by structure blocks, used by Create's schematicannon and by
 * worldgen template pools. Positions not listed are "structure void" (left untouched when placed).
 */
public final class VanillaStructureFormat implements SchematicFormat {

    @Override
    public String id() {
        return "vanilla";
    }

    @Override
    public String displayName() {
        return "Structure / Create (.nbt)";
    }

    @Override
    public List<String> extensions() {
        return List.of("nbt");
    }

    @Override
    public boolean detect(CompoundTag root) {
        return root.contains("size") && root.contains("blocks") && (root.contains("palette") || root.contains("palettes"));
    }

    @Override
    public SchematicFile read(CompoundTag root) throws IOException {
        ListTag paletteTag = root.contains("palette") ? root.getList("palette") : root.getList("palettes").isEmpty()
                ? new ListTag() : (ListTag) root.getList("palettes").get(0);
        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        for (CompoundTag p : paletteTag.compounds()) palette.add(BlockState.fromNbt(p));

        Structure s = new Structure();
        s.metadata().dataVersion = root.getInt("DataVersion");
        for (CompoundTag b : root.getList("blocks").compounds()) {
            ListTag pos = b.getList("pos");
            if (pos.size() != 3) throw new IOException("Block entry without 3-element pos");
            int state = b.getInt("state");
            if (state < 0 || state >= palette.size()) throw new IOException("Palette index " + state + " out of range");
            BlockPos p = new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
            BlockState bs = palette.get(state);
            if (bs.isAir()) continue; // explicit air is implied by the bounding box on re-export when includeAir is set
            s.set(p, bs);
            if (b.contains("nbt")) s.setBlockEntity(p, FormatUtil.cleanBlockEntity(b.getCompound("nbt"), "id"));
        }
        for (CompoundTag e : root.getList("entities").compounds()) {
            ListTag pos = e.getList("pos");
            if (pos.size() != 3) continue;
            // The relative "pos" is authoritative; the inner nbt's Pos is the absolute world position at save time.
            StructureEntity parsed = StructureEntity.fromFullNbt(e.getCompound("nbt"), 0, 0, 0);
            s.addEntity(new StructureEntity(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2), parsed.nbt()));
        }
        String author = root.getString("author");
        s.metadata().author = author;
        return new SchematicFile("", author, "", s.metadata().dataVersion, List.of(new SchematicFile.Region("main", s, BlockPos.ORIGIN)));
    }

    @Override
    public Encoded write(SchematicFile file, WriteOptions options) {
        Structure s = FormatUtil.normalized(file.merged());
        Box box = FormatUtil.boundsOrUnit(s);

        List<CompoundTag> palette = new ArrayList<>();
        Map<BlockState, Integer> index = new HashMap<>();
        ListTag blocks = new ListTag();
        // Vanilla writes plain blocks first, then block entities, so dependent blocks place correctly.
        List<CompoundTag> withNbt = new ArrayList<>();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    BlockState st = s.get(x, y, z);
                    if (st.isAir() && !options.includeAir()) continue;
                    if (st == BlockState.STRUCTURE_VOID) continue;
                    int id = index.computeIfAbsent(st, k -> {
                        palette.add(k.toNbt());
                        return palette.size() - 1;
                    });
                    CompoundTag b = new CompoundTag().put("pos", ListTag.ofInts(x, y, z)).putInt("state", id);
                    CompoundTag be = s.blockEntity(new BlockPos(x, y, z));
                    if (be != null) {
                        b.put("nbt", be.copy());
                        withNbt.add(b);
                    } else {
                        blocks.add(b);
                    }
                }
            }
        }
        withNbt.forEach(blocks::add);

        ListTag paletteTag = new ListTag(io.blockdesigner.core.nbt.Tag.COMPOUND);
        palette.forEach(paletteTag::add);

        ListTag entities = new ListTag(io.blockdesigner.core.nbt.Tag.COMPOUND);
        for (StructureEntity e : s.entities()) {
            entities.add(new CompoundTag()
                    .put("pos", ListTag.ofDoubles(e.x(), e.y(), e.z()))
                    .put("blockPos", ListTag.ofInts((int) Math.floor(e.x()), (int) Math.floor(e.y()), (int) Math.floor(e.z())))
                    .put("nbt", e.toFullNbt(0, 0, 0)));
        }

        CompoundTag root = new CompoundTag()
                .putInt("DataVersion", options.version().dataVersion())
                .put("size", ListTag.ofInts(box.sizeX(), box.sizeY(), box.sizeZ()))
                .put("palette", paletteTag)
                .put("blocks", blocks)
                .put("entities", entities);
        if (!file.author().isEmpty()) root.putString("author", file.author());
        return new Encoded("", root);
    }
}
