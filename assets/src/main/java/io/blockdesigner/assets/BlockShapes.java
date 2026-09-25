package io.blockdesigner.assets;

import io.blockdesigner.assets.model.BakedModel;
import io.blockdesigner.assets.model.BakedQuad;
import io.blockdesigner.core.model.BlockState;

import java.util.List;
import java.util.Set;

/**
 * Minecraft's own hitboxes (outline shapes) for blocks smaller than a full block, where the model's element bounds
 * would be wrong: plants and flowers are drawn on planes across the whole cell, torches on tall planes, crops as full
 * crosses and so on. The shapes follow the game's code (they aren't in the game's data files), in pixels (0–16). Any
 * modded plant drawn as a cross gets the grass-like shape; everything else keeps its model's bounds.
 */
public final class BlockShapes {
    private BlockShapes() {
    }

    private static final Set<String> SMALL_FLOWERS = Set.of("dandelion", "poppy", "blue_orchid", "allium", "azure_bluet",
            "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley",
            "wither_rose", "torchflower", "closed_eyeblossom", "open_eyeblossom");
    private static final Set<String> DOUBLE_PLANTS = Set.of("tall_grass", "large_fern", "sunflower", "lilac", "rose_bush", "peony",
            "pitcher_plant", "tall_dry_grass");
    private static final Set<String> GRASSES = Set.of("short_grass", "grass", "fern", "dead_bush", "crimson_roots", "warped_roots",
            "small_dripleaf", "bush", "short_dry_grass");

    /** The hitbox boxes for {@code state} (0..1 space), or null to keep the model's own. */
    public static List<float[]> shape(BlockState state, BakedModel model) {
        String p = state.path();
        List<float[]> several = multi(state, p);
        if (several != null) return several.stream().map(BlockShapes::scale).toList();
        float[] b = pixels(state, p);
        if (b == null && crossPlant(model)) {
            // A plant drawn as an X of planes (modded ones too): grass's shape, no taller than the model.
            float top = Math.min(13, Math.max(2, maxY(model) * 16));
            b = new float[]{2, 0, 2, 14, top, 14};
        }
        if (b == null) return null;
        return List.of(scale(b));
    }

    /** Shapes made of more than one box (Minecraft joins them): big dripleaf, beds, wall hanging signs. */
    private static List<float[]> multi(BlockState s, String p) {
        if (p.equals("big_dripleaf")) {
            // The leaf (none when fully tilted) plus the stem below it on the side the leaf faces from.
            float[] stem = switch (facing(s)) {
                case "south" -> box(5, 0, 1, 11, 13, 7);
                case "east" -> box(1, 0, 5, 7, 13, 11);
                case "west" -> box(9, 0, 5, 15, 13, 11);
                default -> box(5, 0, 9, 11, 13, 15);
            };
            return switch (String.valueOf(s.get("tilt"))) {
                case "full" -> List.of(stem);
                case "partial" -> List.of(box(0, 11, 0, 16, 13, 16), stem);
                default -> List.of(box(0, 11, 0, 16, 15, 16), stem);
            };
        }
        if (p.endsWith("_bed")) {
            // The mattress, with the two legs at this half's end of the bed.
            float[] base = box(0, 3, 0, 16, 9, 16);
            String f = facing(s);
            boolean head = "head".equals(s.get("part"));
            String end = head ? f : opposite(f);
            return switch (end) {
                case "south" -> List.of(base, box(0, 0, 13, 3, 3, 16), box(13, 0, 13, 16, 3, 16));
                case "east" -> List.of(base, box(13, 0, 0, 16, 3, 3), box(13, 0, 13, 16, 3, 16));
                case "west" -> List.of(base, box(0, 0, 0, 3, 3, 3), box(0, 0, 13, 3, 3, 16));
                default -> List.of(base, box(0, 0, 0, 3, 3, 3), box(13, 0, 0, 16, 3, 3));
            };
        }
        if (p.endsWith("_wall_hanging_sign")) {
            // The board plus the bar it hangs from, across the facing axis.
            return facing(s).equals("east") || facing(s).equals("west")
                    ? List.of(box(6, 14, 0, 10, 16, 16), box(7, 0, 1, 9, 10, 15))
                    : List.of(box(0, 14, 6, 16, 16, 10), box(1, 0, 7, 15, 10, 9));
        }
        return null;
    }

    private static float[] pixels(BlockState s, String p) {
        // Torches: a small stick, leaning off the wall for wall torches.
        if (p.endsWith("wall_torch")) {
            return switch (facing(s)) {
                case "south" -> box(5.5f, 3, 0, 10.5f, 13, 5);
                case "west" -> box(11, 3, 5.5f, 16, 13, 10.5f);
                case "east" -> box(0, 3, 5.5f, 5, 13, 10.5f);
                default -> box(5.5f, 3, 11, 10.5f, 13, 16);
            };
        }
        if (p.endsWith("torch")) return box(6, 0, 6, 10, 10, 10);

        // Plants.
        if (SMALL_FLOWERS.contains(p)) return box(5, 0, 5, 11, 10, 11);
        if (GRASSES.contains(p)) return box(2, 0, 2, 14, 13, 14);
        if (p.endsWith("_sapling") || p.equals("mangrove_propagule")) return box(2, 0, 2, 14, 12, 14);
        if (p.equals("bamboo_sapling")) return box(4, 0, 4, 12, 12, 12);
        if (p.equals("brown_mushroom") || p.equals("red_mushroom")) return box(5, 0, 5, 11, 6, 11);
        if (p.equals("crimson_fungus") || p.equals("warped_fungus")) return box(4, 0, 4, 12, 9, 12);
        if (p.equals("nether_sprouts")) return box(0, 0, 0, 16, 3, 16);
        if (p.equals("sugar_cane")) return box(2, 0, 2, 14, 16, 14);
        if (p.equals("cactus")) return box(1, 0, 1, 15, 16, 15);
        if (p.equals("lily_pad")) return box(1, 0, 1, 15, 1.5f, 15);
        if (p.equals("seagrass")) return box(2, 0, 2, 14, 12, 14);
        if (p.equals("tall_seagrass")) return box(2, 0, 2, 14, 16, 14);
        // Two-block plants (tall grass, sunflower, lilac…) have a full-block outline in Minecraft.
        if (DOUBLE_PLANTS.contains(p)) return box(0, 0, 0, 16, 16, 16);
        if (p.equals("sweet_berry_bush")) return age(s) == 0 ? box(3, 0, 3, 13, 8, 13) : box(1, 0, 1, 15, 16, 15);
        if (p.equals("cave_vines") || p.equals("cave_vines_plant")) return box(1, 0, 1, 15, 16, 15);
        if (p.equals("weeping_vines") || p.equals("weeping_vines_plant")) return box(4, p.endsWith("_plant") ? 0 : 9, 4, 12, 16, 12);
        if (p.equals("twisting_vines") || p.equals("twisting_vines_plant")) return box(4, 0, 4, 12, p.endsWith("_plant") ? 16 : 15, 12);
        if (p.equals("hanging_roots")) return box(2, 10, 2, 14, 16, 14);
        if (p.equals("spore_blossom")) return box(2, 13, 2, 14, 16, 14);
        if (p.equals("pink_petals") || p.equals("wildflowers") || p.equals("leaf_litter")) return box(0, 0, 0, 16, 3, 16);
        if (p.equals("frogspawn")) return box(0, 0, 0, 16, 1.5f, 16);

        // Crops grow with age.
        if (p.equals("wheat")) return box(0, 0, 0, 16, 2 + age(s) * 2, 16);
        if (p.equals("carrots") || p.equals("potatoes")) return box(0, 0, 0, 16, 2 + age(s), 16);
        if (p.equals("beetroots")) return box(0, 0, 0, 16, 2 + age(s) * 2, 16);
        if (p.equals("nether_wart")) return box(0, 0, 0, 16, new int[]{5, 8, 11, 14}[Math.clamp(age(s), 0, 3)], 16);
        if (p.equals("melon_stem") || p.equals("pumpkin_stem")) return box(7, 0, 7, 9, 2 + age(s) * 2, 9);
        if (p.equals("torchflower_crop")) return box(5, 0, 5, 11, age(s) == 0 ? 6 : 10, 11);

        // Coral: plants, fans on the floor, fans on walls.
        if (p.endsWith("coral_wall_fan")) {
            return switch (facing(s)) {
                case "south" -> box(0, 4, 0, 16, 12, 11);
                case "west" -> box(5, 4, 0, 16, 12, 16);
                case "east" -> box(0, 4, 0, 11, 12, 16);
                default -> box(0, 4, 5, 16, 12, 16);
            };
        }
        if (p.endsWith("coral_fan")) return box(2, 0, 2, 14, 4, 14);
        if (p.endsWith("_coral") && !p.endsWith("_block")) return box(2, 0, 2, 14, 15, 14);

        // Amethyst grows out of the face it is on.
        if (p.equals("amethyst_cluster")) return turned(box(3, 0, 3, 13, 7, 13), facing(s));
        if (p.equals("large_amethyst_bud")) return turned(box(3, 0, 3, 13, 5, 13), facing(s));
        if (p.equals("medium_amethyst_bud")) return turned(box(3, 0, 3, 13, 4, 13), facing(s));
        if (p.equals("small_amethyst_bud")) return turned(box(4, 0, 4, 12, 3, 12), facing(s));

        // Flat things.
        if (p.endsWith("_carpet") || p.equals("moss_carpet") || p.equals("pale_moss_carpet")) return box(0, 0, 0, 16, 1, 16);
        if (p.endsWith("_pressure_plate")) {
            boolean down = "true".equals(s.get("powered")) || !"0".equals(String.valueOf(s.get("power") == null ? "0" : s.get("power")));
            return box(1, 0, 1, 15, down ? 0.5f : 1, 15);
        }
        if (p.endsWith("rail") && s.has("shape")) return String.valueOf(s.get("shape")).startsWith("ascending") ? box(0, 0, 0, 16, 8, 16) : box(0, 0, 0, 16, 2, 16);

        // Dripstone: thinner towards the tip, pointing up or down.
        if (p.equals("pointed_dripstone")) {
            boolean down = "down".equals(s.get("vertical_direction"));
            return switch (String.valueOf(s.get("thickness"))) {
                case "tip_merge" -> box(5, 0, 5, 11, 16, 11);
                case "tip" -> down ? box(5, 5, 5, 11, 16, 11) : box(5, 0, 5, 11, 11, 11);
                case "frustum" -> box(4, 0, 4, 12, 16, 12);
                case "middle" -> box(3, 0, 3, 13, 16, 13);
                default -> box(2, 0, 2, 14, 16, 14);
            };
        }
        if (p.equals("big_dripleaf_stem")) {
            return switch (facing(s)) {
                case "south" -> box(5, 0, 1, 11, 16, 7);
                case "east" -> box(1, 0, 5, 7, 16, 11);
                case "west" -> box(9, 0, 5, 15, 16, 11);
                default -> box(5, 0, 9, 11, 16, 15);
            };
        }
        if (p.equals("bamboo")) return "large".equals(s.get("leaves")) ? box(3, 0, 3, 13, 16, 13) : box(5, 0, 5, 11, 16, 11);
        if (p.equals("cocoa")) {
            // Three sizes, hugging the log on the facing side.
            int a = Math.clamp(age(s), 0, 2);
            float w = new float[]{2, 3, 4}[a], d = new float[]{4, 6, 8}[a], y0 = new float[]{7, 5, 3}[a];
            float lo = 8 - w, hi = 8 + w;
            return switch (facing(s)) {
                case "east" -> box(15 - d, y0, lo, 15, 12, hi);
                case "west" -> box(1, y0, lo, 1 + d, 12, hi);
                case "south" -> box(lo, y0, 15 - d, hi, 12, 15);
                default -> box(lo, y0, 1, hi, 12, 1 + d);
            };
        }

        // Signs: a post when standing, a board on walls, the board of a hanging sign.
        if (p.endsWith("_hanging_sign")) {
            int r = rotation(s);
            if (r == 0 || r == 8) return box(1, 0, 7, 15, 10, 9);
            if (r == 4 || r == 12) return box(7, 0, 1, 9, 10, 15);
            return box(3, 0, 3, 13, 16, 13);
        }
        if (p.endsWith("_wall_sign")) {
            return switch (facing(s)) {
                case "south" -> box(0, 4.5f, 0, 16, 12.5f, 2);
                case "east" -> box(0, 4.5f, 0, 2, 12.5f, 16);
                case "west" -> box(14, 4.5f, 0, 16, 12.5f, 16);
                default -> box(0, 4.5f, 14, 16, 12.5f, 16);
            };
        }
        if (p.endsWith("_sign")) return box(4, 0, 4, 12, 16, 12);

        // Heads and skulls: 8×8×8 on the floor or against a wall (piglin heads are wider).
        if (p.endsWith("_wall_skull") || p.endsWith("_wall_head")) {
            boolean piglin = p.startsWith("piglin");
            float a0 = piglin ? 3 : 4, a1 = piglin ? 13 : 12;
            return switch (facing(s)) {
                case "south" -> box(a0, 4, 0, a1, 12, 8);
                case "east" -> box(0, 4, a0, 8, 12, a1);
                case "west" -> box(8, 4, a0, 16, 12, a1);
                default -> box(a0, 4, 8, a1, 12, 16);
            };
        }
        if ((p.endsWith("_skull") || p.endsWith("_head")) && !p.equals("piston_head")) {
            return p.startsWith("piglin") ? box(3, 0, 3, 13, 8, 13) : box(4, 0, 4, 12, 8, 12);
        }

        // Chests: 14 tall, running into the other half of a double chest.
        if (p.equals("chest") || p.equals("trapped_chest") || p.equals("ender_chest")) {
            String type = String.valueOf(s.get("type"));
            if (type.equals("left") || type.equals("right")) {
                String f = facing(s);
                String toward = type.equals("left") ? clockwise(f) : clockwise(clockwise(clockwise(f)));
                return switch (toward) {
                    case "north" -> box(1, 0, 0, 15, 14, 15);
                    case "south" -> box(1, 0, 1, 15, 14, 16);
                    case "west" -> box(0, 0, 1, 15, 14, 15);
                    default -> box(1, 0, 1, 16, 14, 15);
                };
            }
            return box(1, 0, 1, 15, 14, 15);
        }

        // Banners: a post when standing, a flat cloth hanging on a wall.
        if (p.endsWith("_wall_banner")) {
            return switch (facing(s)) {
                case "south" -> box(0, 0, 0, 16, 12.5f, 2);
                case "east" -> box(0, 0, 0, 2, 12.5f, 16);
                case "west" -> box(14, 0, 0, 16, 12.5f, 16);
                default -> box(0, 0, 14, 16, 12.5f, 16);
            };
        }
        if (p.endsWith("_banner")) return box(4, 0, 4, 12, 16, 12);

        // A potted plant's hitbox is just the pot.
        if (p.startsWith("potted_") || p.equals("flower_pot")) return box(5, 0, 5, 11, 6, 11);
        return null;
    }

    /** True for a model made only of vertical diagonal planes (an X, like grass or flowers). */
    private static boolean crossPlant(BakedModel m) {
        if (m.quads().isEmpty()) return false;
        for (BakedQuad q : m.quads()) {
            float[] n = q.normal();
            if (Math.abs(n[1]) > 0.1f || Math.abs(n[0]) < 0.4f || Math.abs(n[2]) < 0.4f) return false;
        }
        return true;
    }

    private static float maxY(BakedModel m) {
        float y = 0;
        for (BakedQuad q : m.quads()) for (int v = 0; v < 4; v++) y = Math.max(y, q.y(v));
        return y;
    }

    /** A box given for an upward-facing block, turned to face {@code facing} (as amethyst does). */
    private static float[] turned(float[] b, String facing) {
        float x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
        return switch (facing) {
            case "down" -> box(x0, 16 - y1, z0, x1, 16 - y0, z1);
            case "north" -> box(x0, z0, 16 - y1, x1, z1, 16 - y0);
            case "south" -> box(x0, z0, y0, x1, z1, y1);
            case "east" -> box(y0, x0, z0, y1, x1, z1);
            case "west" -> box(16 - y1, x0, z0, 16 - y0, x1, z1);
            default -> b;
        };
    }

    private static String opposite(String d) {
        return switch (d) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            case "west" -> "east";
            default -> d;
        };
    }

    private static String clockwise(String d) {
        return switch (d) {
            case "north" -> "east";
            case "east" -> "south";
            case "south" -> "west";
            case "west" -> "north";
            default -> d;
        };
    }

    private static int rotation(BlockState s) {
        try {
            return Integer.parseInt(String.valueOf(s.get("rotation")));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String facing(BlockState s) {
        return String.valueOf(s.get("facing"));
    }

    private static int age(BlockState s) {
        try {
            return Integer.parseInt(String.valueOf(s.get("age")));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static float[] box(float x0, float y0, float z0, float x1, float y1, float z1) {
        return new float[]{x0, y0, z0, x1, y1, z1};
    }

    private static float[] scale(float[] px) {
        float[] out = new float[6];
        for (int i = 0; i < 6; i++) out[i] = px[i] / 16f;
        return out;
    }
}
