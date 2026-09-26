package io.blockdesigner.core.blocks;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Blocks made of the same material in different shapes: {@code oak_planks}, {@code oak_stairs}, {@code oak_slab},
 * {@code oak_fence}, {@code oak_door}… or {@code stone_bricks}, {@code stone_brick_stairs}, {@code stone_brick_wall}.
 * Built by {@link BlockFamilies} from block ids.
 *
 * @param id      the shared stem with its namespace, e.g. {@code minecraft:oak} or {@code minecraft:stone_brick}
 * @param members block id (with namespace) for each shape the family has
 */
public record BlockFamily(String id, Map<Shape, String> members) {

    /** The shapes a material comes in. {@link #BLOCK} is the full cube (planks, bricks, the plain block). */
    public enum Shape {
        BLOCK, STAIRS, SLAB, WALL, FENCE, FENCE_GATE, DOOR, TRAPDOOR, BUTTON, PRESSURE_PLATE,
        SIGN, WALL_SIGN, HANGING_SIGN, WALL_HANGING_SIGN, LOG, WOOD, STRIPPED_LOG, STRIPPED_WOOD
    }

    public BlockFamily {
        Objects.requireNonNull(id, "id");
        EnumMap<Shape, String> copy = new EnumMap<>(Shape.class);
        copy.putAll(members);
        members = Collections.unmodifiableMap(copy);
    }

    /** The member of this shape, e.g. {@code get(STAIRS)} of oak is {@code minecraft:oak_stairs}. */
    public Optional<String> get(Shape shape) {
        return Optional.ofNullable(members.get(shape));
    }

    /** Which shape {@code blockId} (with namespace) is in this family, if it belongs to it. */
    public Optional<Shape> shapeOf(String blockId) {
        for (var e : members.entrySet()) if (e.getValue().equals(blockId)) return Optional.of(e.getKey());
        return Optional.empty();
    }

    public boolean contains(String blockId) {
        return members.containsValue(blockId);
    }

    /** The family's name without namespace, e.g. {@code stone_brick}. */
    public String name() {
        return id.substring(id.indexOf(':') + 1);
    }
}
