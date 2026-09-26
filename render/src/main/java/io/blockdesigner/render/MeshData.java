package io.blockdesigner.render;

import io.blockdesigner.assets.model.RenderLayer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * CPU-side vertex data for one section, split by render layer. Each vertex is {@value #STRIDE} bytes:
 * position (3 × float), atlas UV (2 × float), colour RGBA (4 × ubyte, tint × AO), normal (3 × byte) and glow (1 byte: 127 for
 * unlit full-brightness quads, such as glowing eyes).
 */
public final class MeshData {
    public static final int STRIDE = 28;

    final Builder[] layers = new Builder[RenderLayer.values().length];
    /** Some quad shows an animated texture, so the view keeps redrawing while it's on screen. */
    boolean animated;

    MeshData() {
        for (int i = 0; i < layers.length; i++) layers[i] = new Builder();
    }

    public int quadCount(RenderLayer layer) {
        return layers[layer.ordinal()].quads;
    }

    /** Whether any quad uses an animated texture (fire, lava, campfire flames). */
    public boolean animated() {
        return animated;
    }

    public boolean isEmpty() {
        for (Builder b : layers) if (b.quads > 0) return false;
        return true;
    }

    /** Vertex bytes for a layer, positioned at 0 with limit = size. */
    public ByteBuffer vertices(RenderLayer layer) {
        return layers[layer.ordinal()].buffer();
    }

    static final class Builder {
        private ByteBuffer buf = ByteBuffer.allocate(STRIDE * 4 * 64).order(ByteOrder.nativeOrder());
        int quads;

        void vertex(float x, float y, float z, float u, float v, int rgba, float nx, float ny, float nz) {
            vertex(x, y, z, u, v, rgba, nx, ny, nz, false);
        }

        void vertex(float x, float y, float z, float u, float v, int rgba, float nx, float ny, float nz, boolean glow) {
            if (buf.remaining() < STRIDE) {
                ByteBuffer bigger = ByteBuffer.allocate(buf.capacity() * 2).order(ByteOrder.nativeOrder());
                buf.flip();
                bigger.put(buf);
                buf = bigger;
            }
            buf.putFloat(x).putFloat(y).putFloat(z).putFloat(u).putFloat(v);
            buf.put((byte) (rgba >>> 24)).put((byte) (rgba >>> 16)).put((byte) (rgba >>> 8)).put((byte) rgba);
            buf.put((byte) (nx * 127)).put((byte) (ny * 127)).put((byte) (nz * 127)).put((byte) (glow ? 127 : 0));
        }

        ByteBuffer buffer() {
            ByteBuffer d = buf.duplicate().order(ByteOrder.nativeOrder());
            d.flip();
            return d;
        }
    }
}
