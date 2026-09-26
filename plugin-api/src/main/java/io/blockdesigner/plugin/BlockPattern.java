package io.blockdesigner.plugin;

import io.blockdesigner.core.model.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * A weighted mix of blocks, like WorldEdit's {@code 70%stone,30%andesite}. The value of a
 * {@link Options.Builder#blockList block list} option.
 *
 * @param entries at least one block with a positive weight
 */
public record BlockPattern(List<Entry> entries) {

    /** One block of the mix and its weight (weights don't have to add up to 100). */
    public record Entry(BlockState block, double weight) {
        public Entry {
            Objects.requireNonNull(block, "block");
            if (!(weight > 0)) throw new IllegalArgumentException("Weights must be positive: " + weight);
        }
    }

    public BlockPattern {
        entries = List.copyOf(entries);
        if (entries.isEmpty()) throw new IllegalArgumentException("A pattern needs at least one block");
    }

    /** Equal parts of each block. */
    public static BlockPattern of(BlockState... blocks) {
        return of(List.of(blocks));
    }

    /** Equal parts of each block. */
    public static BlockPattern of(List<BlockState> blocks) {
        return new BlockPattern(blocks.stream().map(b -> new Entry(b, 1)).toList());
    }

    /**
     * Parses {@code 70%stone,30%andesite} (a missing weight counts as 1, so {@code stone,andesite} is half and half).
     *
     * @param resolve turns each block's text into a state, e.g. {@link BlockCatalog#resolve}
     * @throws IllegalArgumentException for bad weights or blocks {@code resolve} rejects
     */
    public static BlockPattern parse(String text, PluginCommand.BlockResolver resolve) {
        List<Entry> out = new ArrayList<>();
        for (String part : splitTopLevel(text)) {
            String p = part.strip();
            if (p.isEmpty()) continue;
            int pct = p.indexOf('%');
            // A '%' inside the block's properties isn't a weight.
            int bracket = p.indexOf('[');
            double weight = 1;
            if (pct > 0 && (bracket < 0 || pct < bracket)) {
                try {
                    weight = Double.parseDouble(p.substring(0, pct).strip());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Bad weight in '" + p + "'");
                }
                p = p.substring(pct + 1).strip();
                if (p.isEmpty()) throw new IllegalArgumentException("No block after '" + part.strip() + "'");
            }
            out.add(new Entry(resolve.resolve(p), weight));
        }
        return new BlockPattern(out);
    }

    /** Commas inside a block's {@code [properties]} don't separate entries. */
    private static List<String> splitTopLevel(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') depth--;
            else if (c == ',' && depth == 0) {
                parts.add(text.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(text.substring(start));
        return parts;
    }

    /** A block drawn by weight. */
    public BlockState pick(RandomGenerator random) {
        if (entries.size() == 1) return entries.getFirst().block();
        double total = 0;
        for (Entry e : entries) total += e.weight();
        double r = random.nextDouble() * total;
        for (Entry e : entries) {
            r -= e.weight();
            if (r < 0) return e.block();
        }
        return entries.getLast().block();
    }

    /** The blocks in the mix, in order. */
    public List<BlockState> blocks() {
        return entries.stream().map(Entry::block).toList();
    }

    /** Back to the text form, e.g. {@code 70%minecraft:stone,30%minecraft:andesite}; round-trips through {@link #parse}. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            if (!sb.isEmpty()) sb.append(',');
            double w = e.weight();
            // Weight 1 is what a bare block means, so "stone,andesite" stays that way.
            if (w != 1) sb.append(w == Math.rint(w) ? String.valueOf((long) w) : String.format(Locale.ROOT, "%s", w)).append('%');
            sb.append(e.block());
        }
        return sb.toString();
    }
}
