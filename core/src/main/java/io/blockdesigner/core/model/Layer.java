package io.blockdesigner.core.model;

import io.blockdesigner.core.transform.Transform;

import java.util.Objects;
import java.util.UUID;

/**
 * One schematic in a {@link Scene}. Blocks are stored in layer-local coordinates; world position is
 * {@code offset + transform(local)}, so moving or rotating a layer never rewrites its blocks.
 */
public final class Layer {
    private final String id;
    private String name;
    private final Structure structure;
    private BlockPos offset = BlockPos.ORIGIN;
    private Transform transform = Transform.IDENTITY;
    private boolean visible = true;
    private boolean locked;
    private boolean ghost;
    private int color = 0x7C9CFF;
    /** Id of the schematic format this layer was imported from (e.g. {@code litematica}), or null if built here. */
    private String source;

    public Layer(String name, Structure structure) {
        this(UUID.randomUUID().toString(), name, structure);
    }

    public Layer(String id, String name, Structure structure) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.structure = Objects.requireNonNull(structure);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public Structure structure() {
        return structure;
    }

    public BlockPos offset() {
        return offset;
    }

    public void setOffset(BlockPos offset) {
        this.offset = Objects.requireNonNull(offset);
    }

    public Transform transform() {
        return transform;
    }

    public void setTransform(Transform transform) {
        this.transform = Objects.requireNonNull(transform);
    }

    public boolean visible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean locked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean ghost() {
        return ghost;
    }

    public void setGhost(boolean ghost) {
        this.ghost = ghost;
    }

    public String source() {
        return source;
    }

    public void setSource(String formatId) {
        this.source = formatId == null || formatId.isBlank() ? null : formatId;
    }

    /** Display tint (0xRRGGBB) for the layer's list row and outline. */
    public int color() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public BlockPos toWorld(int x, int y, int z) {
        return transform.apply(x, y, z).add(offset);
    }

    public BlockPos toWorld(BlockPos local) {
        return toWorld(local.x(), local.y(), local.z());
    }

    public BlockPos toLocal(BlockPos world) {
        return transform.inverse().apply(world.subtract(offset));
    }

    /** World-space bounds of this layer's blocks, if it has any. */
    public java.util.Optional<Box> worldBounds() {
        return structure.bounds().map(b -> Box.of(toWorld(b.min()), toWorld(b.max())));
    }

    /** Deep copy with a fresh id. */
    public Layer duplicate(String newName) {
        Layer l = new Layer(newName, structure.copy());
        l.offset = offset;
        l.transform = transform;
        l.visible = visible;
        l.ghost = ghost;
        l.color = color;
        l.source = source;
        return l;
    }

    @Override
    public String toString() {
        return "Layer[" + name + ", " + structure.blockCount() + " blocks @ " + offset + "]";
    }
}
