package com.example.palette;

import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.plugin.BlockCatalog;
import io.blockdesigner.plugin.OptionValues;
import io.blockdesigner.plugin.Options;
import io.blockdesigner.plugin.PluginImporter;
import io.blockdesigner.plugin.Progress;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Turns an image into pixel art: each pixel becomes the block whose colour is closest, from a palette of wool,
 * concrete or terracotta. Fully transparent pixels stay empty. Stands upright (x, y) or lies flat (x, z).
 */
final class PixelArtImporter implements PluginImporter {
    private static final List<String> COLOURS = List.of("white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow",
            "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink");

    @Override
    public String id() {
        return "pixel_art";
    }

    @Override
    public String displayName() {
        return "Pixel art from an image";
    }

    @Override
    public List<String> extensions() {
        return List.of("png", "gif", "bmp");
    }

    @Override
    public Options options() {
        return Options.builder()
                .choice("material", "Blocks", List.of("concrete", "wool", "terracotta"), "concrete")
                .choice("facing", "Lay out", List.of("upright", "flat"), "upright")
                .integer("maxSize", "Largest side (pixels)", 128, 8, 512)
                .build();
    }

    @Override
    public List<ImportedLayer> importFile(Path file, OptionValues options, Progress progress, BlockCatalog blocks) throws IOException {
        BufferedImage img = ImageIO.read(file.toFile());
        if (img == null) throw new IOException("Not an image BlockDesigner can read");
        int max = options.integer("maxSize");
        double scale = Math.min(1, max / (double) Math.max(img.getWidth(), img.getHeight()));
        int w = Math.max(1, (int) Math.round(img.getWidth() * scale)), h = Math.max(1, (int) Math.round(img.getHeight() * scale));
        String material = options.choice("material");
        List<BlockState> palette = COLOURS.stream().map(c -> BlockState.of(c + "_" + material)).toList();
        int[] colours = palette.stream().mapToInt(blocks::averageColor).toArray();
        boolean flat = options.choice("facing").equals("flat");
        Structure s = new Structure();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB((int) (x / scale), (int) (y / scale));
                if ((argb >>> 24) < 128) continue;
                BlockState b = palette.get(nearest(argb, colours));
                // Images run top-down; upright art has its top row highest.
                if (flat) s.set(x, 0, y, b);
                else s.set(x, h - 1 - y, 0, b);
            }
            progress.update((y + 1) / (double) h, "Pixel art: row " + (y + 1) + " of " + h);
        }
        String name = file.getFileName().toString().replaceFirst("\\.[^.]*$", "");
        return List.of(new ImportedLayer(name, s, null));
    }

    private static int nearest(int argb, int[] colours) {
        int best = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < colours.length; i++) {
            int dr = ((argb >> 16) & 255) - ((colours[i] >> 16) & 255);
            int dg = ((argb >> 8) & 255) - ((colours[i] >> 8) & 255);
            int db = (argb & 255) - (colours[i] & 255);
            // Weighted like the eye: green counts most, blue least.
            long d = 3L * dr * dr + 4L * dg * dg + 2L * db * db;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }
}
