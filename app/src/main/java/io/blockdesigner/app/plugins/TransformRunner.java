package io.blockdesigner.app.plugins;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.nbt.CompoundTag;
import io.blockdesigner.core.worldedit.WorldEdit;
import io.blockdesigner.plugin.BlockCatalog;
import io.blockdesigner.plugin.OptionValues;
import io.blockdesigner.plugin.PluginTransform;
import io.blockdesigner.plugin.TransformContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Runs a {@link PluginTransform} without touching the scene: its writes go into an overlay on top of the real blocks,
 * and what differs from the scene comes back as {@link Changes}. The preview shows those changes as ghosts; Apply runs
 * the transform once more (same seed, same options) and writes the changes as one undo step, so what was previewed is
 * what gets applied.
 */
public final class TransformRunner {
    private TransformRunner() {
    }

    /**
     * What a transform works on.
     *
     * @param bounds world box around {@code blocks}
     * @param blocks the non-air blocks to transform, world positions
     * @param label  for the dialog, e.g. "1,204 selected blocks" or "layer House"
     * @param layer  the layer when transforming a whole layer (edits then go into it), null for a selection
     */
    public record Target(Box bounds, List<BlockPos> blocks, String label, Layer layer) {
        public Target {
            blocks = List.copyOf(blocks);
        }

        /** A target around {@code blocks}; null when there are none. */
        public static Target of(List<BlockPos> blocks, String label, Layer layer) {
            if (blocks.isEmpty()) return null;
            int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
            int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
            for (BlockPos p : blocks) {
                x0 = Math.min(x0, p.x());
                y0 = Math.min(y0, p.y());
                z0 = Math.min(z0, p.z());
                x1 = Math.max(x1, p.x());
                y1 = Math.max(y1, p.y());
                z1 = Math.max(z1, p.z());
            }
            return new Target(new Box(x0, y0, z0, x1, y1, z1), blocks, label, layer);
        }
    }

    /**
     * The cells a run changes: the new state of each, and block entity data where the transform set some.
     * Only real differences from the scene are kept.
     */
    public record Changes(Map<BlockPos, BlockState> blocks, Map<BlockPos, CompoundTag> blockEntities) {
        public Changes {
            blocks = Collections.unmodifiableMap(blocks);
            blockEntities = Collections.unmodifiableMap(blockEntities);
        }

        public int size() {
            return blocks.size();
        }

        public boolean isEmpty() {
            return blocks.isEmpty();
        }

        /** Writes the changes into {@code world} (inside an undo step). */
        public void writeTo(WorldEdit.World world) {
            blocks.forEach((p, st) -> {
                CompoundTag nbt = blockEntities.get(p);
                if (nbt != null) world.set(p, st, nbt);
                else world.set(p, st);
            });
        }
    }

    /**
     * Runs {@code transform} against {@code scene} (the blocks as they are) and returns what it would change.
     *
     * @param preview what {@link TransformContext#preview()} says
     * @throws Exception whatever the transform throws (IllegalArgumentException for bad options)
     */
    public static Changes run(PluginTransform transform, WorldEdit.World scene, Target target, OptionValues options, long seed,
                              BlockCatalog blocks, boolean preview) throws Exception {
        Overlay world = new Overlay(scene);
        Random random = new Random(seed);
        transform.apply(new TransformContext() {
            @Override
            public WorldEdit.World world() {
                return world;
            }

            @Override
            public Box bounds() {
                return target.bounds();
            }

            @Override
            public Iterable<BlockPos> solidBlocks() {
                return target.blocks();
            }

            @Override
            public int blockCount() {
                return target.blocks().size();
            }

            @Override
            public OptionValues options() {
                return options;
            }

            @Override
            public Random random() {
                return random;
            }

            @Override
            public BlockCatalog blocks() {
                return blocks;
            }

            @Override
            public boolean preview() {
                return preview;
            }
        });
        return world.changes();
    }

    /** Reads fall through to the scene; writes stay here. */
    private static final class Overlay implements WorldEdit.World {
        private final WorldEdit.World scene;
        private final Map<BlockPos, BlockState> written = new LinkedHashMap<>();
        private final Map<BlockPos, CompoundTag> nbt = new HashMap<>();

        Overlay(WorldEdit.World scene) {
            this.scene = scene;
        }

        @Override
        public BlockState get(BlockPos p) {
            BlockState st = written.get(p);
            return st != null ? st : scene.get(p);
        }

        @Override
        public void set(BlockPos p, BlockState s) {
            written.put(p, s);
            nbt.remove(p);
        }

        @Override
        public void set(BlockPos p, BlockState s, CompoundTag blockEntity) {
            written.put(p, s);
            if (blockEntity != null) nbt.put(p, blockEntity.copy());
            else nbt.remove(p);
        }

        @Override
        public CompoundTag blockEntity(BlockPos p) {
            if (nbt.containsKey(p)) return nbt.get(p);
            return written.containsKey(p) ? null : scene.blockEntity(p);
        }

        @Override
        public List<io.blockdesigner.core.model.StructureEntity> entities(Box box) {
            return scene.entities(box);
        }

        Changes changes() {
            Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
            Map<BlockPos, CompoundTag> entities = new HashMap<>();
            written.forEach((p, st) -> {
                CompoundTag be = nbt.get(p);
                // Block states are interned, so == is equality.
                if (st != scene.get(p) || be != null) {
                    blocks.put(p, st);
                    if (be != null) entities.put(p, be);
                }
            });
            return new Changes(blocks, entities);
        }
    }
}
