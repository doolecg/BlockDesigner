package io.blockdesigner.assets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.blockdesigner.core.model.BlockState;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Colours for tinted faces (grass, leaves, water, redstone...), using fixed plains-biome values. */
public final class BlockTints {
    private record Rule(int color, List<Pattern> patterns) {
    }

    private final List<Rule> rules = new ArrayList<>();
    private final int fallback;

    private BlockTints(JsonNode root) {
        for (JsonNode t : root.path("tints")) {
            List<Pattern> ps = new ArrayList<>();
            t.path("blocks").forEach(b -> ps.add(Pattern.compile(("\\Q" + b.asText() + "\\E").replace("*", "\\E.*\\Q"))));
            rules.add(new Rule(parse(t.path("color").asText()), ps));
        }
        fallback = parse(root.path("default").asText("#FFFFFF"));
    }

    public static BlockTints defaults() {
        try (InputStream in = BlockTints.class.getResourceAsStream("/io/blockdesigner/assets/block_tints.json")) {
            return new BlockTints(new ObjectMapper().readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int parse(String hex) {
        return Integer.parseInt(hex.replace("#", ""), 16);
    }

    /** RGB tint for a face with the given tint index. */
    public int tint(BlockState state, int tintIndex) {
        if (tintIndex < 0) return 0xFFFFFF;
        String name = state.name();
        if (name.equals("minecraft:redstone_wire")) return redstone(parseInt(state.get("power")));
        for (Rule r : rules) for (Pattern p : r.patterns) if (p.matcher(name).matches()) return r.color;
        return fallback;
    }

    private static int parseInt(String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Vanilla's redstone dust colour ramp. */
    static int redstone(int power) {
        float f = power / 15f;
        float r = f * 0.6f + (f > 0 ? 0.4f : 0.3f);
        float g = Math.clamp(f * f * 0.7f - 0.5f, 0f, 1f);
        float b = Math.clamp(f * f * 0.6f - 0.7f, 0f, 1f);
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }
}
