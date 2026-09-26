package io.blockdesigner.assets;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Minecraft's creative menu: which block tab each vanilla block is in and its position there, read from
 * {@code creative_order.txt} (made from the game's code by {@code packaging/creative_order.py}).
 */
public final class CreativeOrder {
    /** The creative menu's block tabs, in the menu's order. */
    public enum Tab {
        BUILDING_BLOCKS("Building Blocks"), COLORED_BLOCKS("Colored Blocks"), NATURAL_BLOCKS("Natural Blocks"),
        FUNCTIONAL_BLOCKS("Functional Blocks"), REDSTONE_BLOCKS("Redstone Blocks");

        public final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private record Entry(Tab tab, int index) {
    }

    private static final Map<String, Entry> ENTRIES = load();

    private CreativeOrder() {
    }

    private static Map<String, Entry> load() {
        Map<String, Entry> out = new HashMap<>();
        try (InputStream in = CreativeOrder.class.getResourceAsStream("creative_order.txt")) {
            if (in == null) return out;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            Tab tab = null;
            int i = 0;
            for (String line; (line = r.readLine()) != null; ) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("[")) {
                    tab = Tab.valueOf(line.substring(1, line.length() - 1).toUpperCase(java.util.Locale.ROOT));
                    continue;
                }
                // A block in two tabs keeps its first place, as the game's search order does.
                if (tab != null) out.putIfAbsent("minecraft:" + line, new Entry(tab, i++));
            }
        } catch (IOException | IllegalArgumentException e) {
            // no order: everything sorts by id
        }
        return out;
    }

    /** Position in the creative menu (all block tabs in order), or -1 for blocks it doesn't list. */
    public static int index(String id) {
        Entry e = ENTRIES.get(id);
        return e == null ? -1 : e.index();
    }

    /** The creative tab a vanilla block is in, or null (modded blocks, and blocks with no item). */
    public static Tab tab(String id) {
        Entry e = ENTRIES.get(id);
        return e == null ? null : e.tab();
    }

    /** Sorts creative-menu blocks first in menu order, then everything else by id. */
    public static int compare(String a, String b) {
        int ia = index(a), ib = index(b);
        if (ia >= 0 && ib >= 0) return Integer.compare(ia, ib);
        if (ia >= 0) return -1;
        if (ib >= 0) return 1;
        return a.compareTo(b);
    }
}
