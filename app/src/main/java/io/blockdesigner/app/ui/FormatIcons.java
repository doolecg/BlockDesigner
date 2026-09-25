package io.blockdesigner.app.ui;

import io.blockdesigner.core.formats.SchematicFormat;
import io.blockdesigner.core.formats.Schematics;
import io.blockdesigner.core.project.ProjectFile;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Small vector icons for the kinds of file BlockDesigner reads and writes, so a schematic's origin is recognisable at a
 * glance: a brass cog for Create's .nbt, a holographic cube for Litematica, a wand axe for WorldEdit, a grass block for
 * worldgen data packs and a puzzle piece for plugin formats. Drawn on a 24-unit grid and scaled, so they are crisp at
 * any size in menus, lists and export cards.
 */
public final class FormatIcons {
    private FormatIcons() {
    }

    public enum Kind {
        CREATE("Create / Structure", 0xD9A441),
        LITEMATICA("Litematica", 0x4FC3F7),
        WORLDEDIT("WorldEdit", 0xC98A4B),
        DATAPACK("Worldgen data pack", 0x5DBB63),
        PLUGIN("Plugin", 0xB18CFF),
        PROJECT("BlockDesigner project", 0x7C9CFF);

        public final String label;
        public final int rgb;

        Kind(String label, int rgb) {
            this.label = label;
            this.rgb = rgb;
        }

        public Color color() {
            return Color.rgb((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
        }
    }

    public static Kind kindOf(SchematicFormat f) {
        if (f == Schematics.VANILLA) return Kind.CREATE;
        if (f == Schematics.LITEMATICA) return Kind.LITEMATICA;
        if (f == Schematics.SPONGE) return Kind.WORLDEDIT;
        return Kind.PLUGIN;
    }

    /** By format id (as stored on imported layers); null when unknown. */
    public static Kind kindOf(String formatId) {
        if (formatId == null || formatId.isBlank()) return null;
        return Schematics.byId(formatId).map(FormatIcons::kindOf).orElse(Kind.PLUGIN);
    }

    /** By file name: .bdproj, the built-in extensions, a plugin extension, or null. */
    public static Kind kindOf(Path file) {
        String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith("." + ProjectFile.EXTENSION)) return Kind.PROJECT;
        if (n.endsWith(".zip")) return Kind.DATAPACK;
        return Schematics.byExtension(file).map(FormatIcons::kindOf).orElse(null);
    }

    /** The bare icon, {@code size} px square. */
    public static Node icon(Kind kind, double size) {
        if (kind == Kind.PROJECT) return MainWindow.appIconView(size);
        Group g = switch (kind) {
            case CREATE -> cog();
            case LITEMATICA -> hologram();
            case WORLDEDIT -> axe();
            case DATAPACK -> grassBlock();
            default -> puzzle();
        };
        double s = size / 24.0;
        g.setScaleX(s);
        g.setScaleY(s);
        // Scale about the 24×24 art box, then lay out at the requested size.
        Group holder = new Group(g);
        StackPane box = new StackPane(holder);
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        box.setMouseTransparent(true);
        return box;
    }

    /** The icon on a rounded tile tinted with the kind's colour (export cards, headers). */
    public static Node tile(Kind kind, double size) {
        Rectangle bg = new Rectangle(size, size);
        bg.setArcWidth(size * 0.42);
        bg.setArcHeight(size * 0.42);
        Color c = kind.color();
        bg.setFill(Color.color(c.getRed(), c.getGreen(), c.getBlue(), 0.16));
        bg.setStroke(Color.color(c.getRed(), c.getGreen(), c.getBlue(), 0.38));
        bg.setStrokeWidth(1);
        StackPane t = new StackPane(bg, icon(kind, size * 0.7));
        t.setMinSize(size, size);
        t.setMaxSize(size, size);
        t.setMouseTransparent(true);
        return t;
    }

    // ---- art (24 × 24) -----------------------------------------------------------------------------------------

    /** Create: a brass cogwheel with an andesite hub. */
    private static Group cog() {
        SVGPath gear = new SVGPath();
        gear.setContent(gearPath(12, 12, 11, 8.2, 8, 3.2));
        gear.setFillRule(FillRule.EVEN_ODD);
        gear.setFill(Color.web("#D9A441"));
        gear.setStroke(Color.web("#7A5217"));
        gear.setStrokeWidth(1.1);
        gear.setStrokeLineJoin(StrokeLineJoin.ROUND);
        Circle hub = new Circle(12, 12, 5.3);
        hub.setFill(Color.TRANSPARENT);
        hub.setStroke(Color.web("#9C9A8C"));
        hub.setStrokeWidth(1.6);
        return new Group(frame(), gear, hub);
    }

    private static String gearPath(double cx, double cy, double rOut, double rIn, int teeth, double hole) {
        StringBuilder sb = new StringBuilder();
        double step = 2 * Math.PI / teeth;
        for (int i = 0; i < teeth; i++) {
            double a = i * step - Math.PI / 2;
            double[][] pts = {{a - step * 0.30, rIn}, {a - step * 0.17, rOut}, {a + step * 0.17, rOut}, {a + step * 0.30, rIn}};
            for (int j = 0; j < pts.length; j++) {
                double x = cx + Math.cos(pts[j][0]) * pts[j][1], y = cy + Math.sin(pts[j][0]) * pts[j][1];
                sb.append(i == 0 && j == 0 ? "M" : "L").append(fmt(x)).append(' ').append(fmt(y)).append(' ');
            }
            // Root arc to the next tooth.
            double b = a + step * 0.70, x = cx + Math.cos(b) * rIn, y = cy + Math.sin(b) * rIn;
            sb.append("A").append(fmt(rIn)).append(' ').append(fmt(rIn)).append(" 0 0 1 ").append(fmt(x)).append(' ').append(fmt(y)).append(' ');
        }
        sb.append("Z ");
        sb.append("M").append(fmt(cx + hole)).append(' ').append(fmt(cy)).append(' ')
                .append("A").append(fmt(hole)).append(' ').append(fmt(hole)).append(" 0 1 0 ").append(fmt(cx - hole)).append(' ').append(fmt(cy)).append(' ')
                .append("A").append(fmt(hole)).append(' ').append(fmt(hole)).append(" 0 1 0 ").append(fmt(cx + hole)).append(' ').append(fmt(cy)).append(" Z");
        return sb.toString();
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    /** Litematica: a see-through cyan block, like its schematic overlay in the world. */
    private static Group hologram() {
        Color edge = Color.web("#0B8FD1");
        Polygon top = poly(Color.web("#4FC3F7", 0.55), edge, 12, 2.5, 21, 7, 12, 11.5, 3, 7);
        Polygon left = poly(Color.web("#4FC3F7", 0.30), edge, 3, 7, 12, 11.5, 12, 21.5, 3, 17);
        Polygon right = poly(Color.web("#4FC3F7", 0.42), edge, 21, 7, 12, 11.5, 12, 21.5, 21, 17);
        // Hidden back edges, dashed, to read as a hologram.
        SVGPath back = new SVGPath();
        back.setContent("M3 17 L12 12.5 L21 17 M12 12.5 L12 2.5");
        back.setFill(null);
        back.setStroke(Color.web("#0B8FD1", 0.55));
        back.setStrokeWidth(0.8);
        back.getStrokeDashArray().setAll(1.2, 1.2);
        return new Group(frame(), back, left, right, top);
    }

    /** WorldEdit: the wooden axe it uses as its selection wand. */
    private static Group axe() {
        SVGPath handle = new SVGPath();
        handle.setContent("M4.5 20.5 L17 8");
        handle.setStroke(Color.web("#8B5A2B"));
        handle.setStrokeWidth(3);
        handle.setStrokeLineCap(StrokeLineCap.ROUND);
        SVGPath grain = new SVGPath();
        grain.setContent("M6.2 18.2 L15 9.4");
        grain.setStroke(Color.web("#B07A45"));
        grain.setStrokeWidth(0.9);
        grain.setStrokeLineCap(StrokeLineCap.ROUND);
        // A bearded blade on the upper-left of the handle's top, its cutting edge curving outwards.
        SVGPath head = new SVGPath();
        head.setContent("M13.2 10.4 L18.4 5.2 L13.4 0.9 Q7.4 1.2 6.2 7.4 Z");
        head.setFill(Color.web("#C98A4B"));
        head.setStroke(Color.web("#6B4424"));
        head.setStrokeWidth(1);
        head.setStrokeLineJoin(StrokeLineJoin.ROUND);
        SVGPath edge = new SVGPath();
        edge.setContent("M13.1 1.9 Q8.2 2.3 7.2 7.2");
        edge.setFill(null);
        edge.setStroke(Color.web("#EDC08A"));
        edge.setStrokeWidth(1.3);
        edge.setStrokeLineCap(StrokeLineCap.ROUND);
        return new Group(frame(), handle, grain, head, edge);
    }

    /** Worldgen: a grass block, for structures that generate in the world. */
    private static Group grassBlock() {
        Color edge = Color.web("#2F5E1F");
        Polygon top = poly(Color.web("#6CC04A"), edge, 12, 2.5, 21, 7, 12, 11.5, 3, 7);
        Polygon left = poly(Color.web("#8B5E3C"), edge, 3, 7, 12, 11.5, 12, 21.5, 3, 17);
        Polygon right = poly(Color.web("#6F4A2E"), edge, 21, 7, 12, 11.5, 12, 21.5, 21, 17);
        Polygon lipL = poly(Color.web("#5AA83C"), null, 3, 7, 12, 11.5, 12, 13.8, 3, 9.3);
        Polygon lipR = poly(Color.web("#4E9434"), null, 21, 7, 12, 11.5, 12, 13.8, 21, 9.3);
        return new Group(frame(), left, right, lipL, lipR, top);
    }

    /** Plugin formats and exporters: a puzzle piece. */
    private static Group puzzle() {
        SVGPath p = new SVGPath();
        p.setContent("M4 8 H9 A2.6 2.6 0 1 1 14 8 H19 V13 A2.6 2.6 0 1 0 19 18 V21 H4 V17 A2.6 2.6 0 1 0 4 12 Z");
        p.setFill(Color.web("#B18CFF"));
        p.setStroke(Color.web("#6E4BC4"));
        p.setStrokeWidth(1.1);
        p.setStrokeLineJoin(StrokeLineJoin.ROUND);
        return new Group(frame(), p);
    }

    /** An invisible 24×24 box so every icon keeps the same bounds (and centre) when scaled. */
    private static Rectangle frame() {
        Rectangle r = new Rectangle(0, 0, 24, 24);
        r.setFill(Color.TRANSPARENT);
        return r;
    }

    private static Polygon poly(Color fill, Color stroke, double... pts) {
        Polygon p = new Polygon(pts);
        p.setFill(fill);
        if (stroke != null) {
            p.setStroke(stroke);
            p.setStrokeWidth(0.9);
            p.setStrokeLineJoin(StrokeLineJoin.ROUND);
        }
        return p;
    }
}
