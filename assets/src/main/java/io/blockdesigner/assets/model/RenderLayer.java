package io.blockdesigner.assets.model;

/** Render pass for a quad, decided from its texture's alpha channel. */
public enum RenderLayer {
    /** Fully opaque texels. */
    SOLID,
    /** Texels are either opaque or fully transparent (alpha test). */
    CUTOUT,
    /** Partially transparent texels (blended, sorted back to front). */
    TRANSLUCENT
}
