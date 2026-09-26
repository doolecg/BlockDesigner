package io.blockdesigner.app.plugins;

import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.assets.TextureAtlas;
import io.blockdesigner.assets.model.BakedQuad;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.place.BlockPlacement;
import io.blockdesigner.plugin.AssetAccess;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/** The {@link AssetAccess} plugins get, over whatever assets are loaded at the time of each call. */
public final class AppAssetAccess implements AssetAccess {
    private final Supplier<BlockAssets> assets;
    /** The atlas as PNG, made once per atlas (it is large and doesn't change until other assets load). */
    private TextureAtlas pngFor;
    private Atlas atlas;

    public AppAssetAccess(Supplier<BlockAssets> assets) {
        this.assets = assets;
    }

    @Override
    public boolean available() {
        return assets.get() != null;
    }

    @Override
    public List<Quad> quads(BlockState block, Predicate<BlockPlacement.Dir> culled) {
        BlockAssets a = assets.get();
        if (a == null) return List.of();
        List<Quad> out = new ArrayList<>();
        for (BakedQuad q : a.model(block).quads()) {
            Optional<BlockPlacement.Dir> cull = q.cull() == null ? Optional.empty() : Optional.of(BlockPlacement.Dir.valueOf(q.cull().name()));
            if (cull.isPresent() && culled.test(cull.get())) continue;
            out.add(new Quad(q.pos().clone(), q.uv().clone(), BlockPlacement.Dir.valueOf(q.face().name()), cull, q.tint(), q.sprite().id()));
        }
        return out;
    }

    @Override
    public synchronized Optional<Atlas> atlas() {
        BlockAssets a = assets.get();
        if (a == null) return Optional.empty();
        TextureAtlas t = a.atlas();
        if (t != pngFor) {
            atlas = new Atlas(t.width(), t.height(), t.pixels().clone(), png(t.width(), t.height(), t.pixels()));
            pngFor = t;
        }
        return Optional.of(atlas);
    }

    @Override
    public Optional<byte[]> texture(String resourceId) {
        BlockAssets a = assets.get();
        if (a == null || resourceId == null || resourceId.isBlank()) return Optional.empty();
        String path = resourceId;
        if (!resourceId.startsWith("assets/")) {
            int c = resourceId.indexOf(':');
            String ns = c < 0 ? "minecraft" : resourceId.substring(0, c);
            path = "assets/" + ns + "/textures/" + resourceId.substring(c + 1) + (resourceId.endsWith(".png") ? "" : ".png");
        }
        return a.assetStack().read(path);
    }

    /** A minimal PNG encoder (8-bit RGBA, no filtering), so plugins get file bytes without AWT. */
    static byte[] png(int width, int height, int[] argb) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (DeflaterOutputStream z = new DeflaterOutputStream(raw)) {
                byte[] row = new byte[1 + width * 4];
                for (int y = 0; y < height; y++) {
                    row[0] = 0; // filter: none
                    for (int x = 0; x < width; x++) {
                        int p = argb[y * width + x], i = 1 + x * 4;
                        row[i] = (byte) (p >> 16);
                        row[i + 1] = (byte) (p >> 8);
                        row[i + 2] = (byte) p;
                        row[i + 3] = (byte) (p >>> 24);
                    }
                    z.write(row);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(out);
            d.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
            ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
            DataOutputStream h = new DataOutputStream(ihdr);
            h.writeInt(width);
            h.writeInt(height);
            h.write(new byte[]{8, 6, 0, 0, 0}); // 8 bits, RGBA, deflate, no filter method, no interlace
            chunk(d, "IHDR", ihdr.toByteArray());
            chunk(d, "IDAT", raw.toByteArray());
            chunk(d, "IEND", new byte[0]);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void chunk(DataOutputStream d, String type, byte[] data) throws IOException {
        byte[] t = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        d.writeInt(data.length);
        d.write(t);
        d.write(data);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        d.writeInt((int) crc.getValue());
    }
}
