package io.blockdesigner.app.ui;

import io.blockdesigner.worldgen.DatapackExporter.TerrainAdaptation;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;

/**
 * Side-view sketches of what each terrain blending mode does: a house on a slope, the ground before (dashed) and
 * after (filled), so the options can be compared at a glance.
 */
final class TerrainDiagrams {
    static final double W = 150, H = 82;
    // The natural slope, high on the left, low on the right, and the house sitting on it.
    private static final double X0 = 0, Y0 = 26, X1 = W, Y1 = 72;
    private static final double HX0 = 52, HX1 = 100, HBOTTOM = 50, HTOP = 32, ROOF = 21;

    private TerrainDiagrams() {
    }

    private static double natural(double x) {
        return Y0 + (Y1 - Y0) * (x - X0) / (X1 - X0);
    }

    /** Drawn at {@code scale} of the 150 × 82 design size. */
    static Canvas draw(TerrainAdaptation t, boolean dark, double scale) {
        Canvas c = new Canvas(W * scale, H * scale);
        GraphicsContext g = c.getGraphicsContext2D();
        g.scale(scale, scale);
        Color sky = dark ? Color.web("#1b2433") : Color.web("#dfeaf7");
        Color grass = Color.web("#5DAA3E"), dirt = Color.web("#8B5E3C"), added = Color.web("#A5754C"), stone = Color.web("#8E8F92");
        g.setFill(sky);
        g.fillRoundRect(0, 0, W, H, 10, 10);

        boolean houseBehindGround = t == TerrainAdaptation.BURY;
        if (t == TerrainAdaptation.ENCAPSULATE) {
            // Solid ground wraps the whole box.
            ground(g, t, dirt, grass);
            g.setFill(added);
            g.fillRoundRect(HX0 - 8, ROOF - 7, HX1 - HX0 + 16, HBOTTOM - ROOF + 14, 8, 8);
            house(g, stone, dark);
        } else if (houseBehindGround) {
            house(g, stone, dark);
            ground(g, t, dirt, grass);
        } else {
            ground(g, t, dirt, grass);
            if (t == TerrainAdaptation.BEARD_BOX) {
                // The added block under the footprint, straight-sided.
                g.setFill(added);
                g.fillRect(HX0, HBOTTOM, HX1 - HX0, H - HBOTTOM);
            }
            house(g, stone, dark);
        }

        // The ground as it was, dashed.
        g.setStroke(dark ? Color.rgb(255, 255, 255, 0.55) : Color.rgb(0, 0, 0, 0.45));
        g.setLineWidth(1.2);
        g.setLineDashes(3, 3);
        g.strokeLine(X0, natural(X0), X1, natural(X1));
        g.setLineDashes();
        return c;
    }

    private static void house(GraphicsContext g, Color stone, boolean dark) {
        g.setFill(stone);
        g.fillRect(HX0, HTOP, HX1 - HX0, HBOTTOM - HTOP);
        g.setFill(Color.web("#B5483B"));
        g.fillPolygon(new double[]{HX0 - 4, (HX0 + HX1) / 2, HX1 + 4}, new double[]{HTOP, ROOF, HTOP}, 3);
        g.setFill(dark ? Color.web("#3a2a1c") : Color.web("#5b3d25"));
        g.fillRect((HX0 + HX1) / 2 - 4, HBOTTOM - 11, 8, 11);
        g.setStroke(Color.rgb(0, 0, 0, 0.35));
        g.setLineWidth(1);
        g.strokeRect(HX0, HTOP, HX1 - HX0, HBOTTOM - HTOP);
    }

    /** Fills the ground after blending, with a grass edge on top. */
    private static void ground(GraphicsContext g, TerrainAdaptation t, Color dirt, Color grass) {
        g.beginPath();
        g.moveTo(X0, natural(X0));
        switch (t) {
            case NONE, ENCAPSULATE -> g.lineTo(X1, natural(X1));
            case BEARD_THIN -> {
                // The hill above the floor is cut back and the dip below is filled, both feathered.
                g.lineTo(28, natural(28));
                g.quadraticCurveTo(44, natural(44), HX0, HBOTTOM);
                g.lineTo(HX1, HBOTTOM);
                g.quadraticCurveTo(110, HBOTTOM, 126, natural(126));
                g.lineTo(X1, natural(X1));
            }
            case BEARD_BOX -> {
                g.lineTo(HX0, natural(HX0));
                g.lineTo(HX0, HBOTTOM);
                g.lineTo(HX1, HBOTTOM);
                g.lineTo(HX1, natural(HX1));
                g.lineTo(X1, natural(X1));
            }
            case BURY -> {
                // A mound pulled up over the lower half of the walls.
                g.lineTo(30, natural(30));
                g.quadraticCurveTo(42, 38, HX0, 40);
                g.lineTo(HX1, 42);
                g.quadraticCurveTo(114, 44, 128, natural(128));
                g.lineTo(X1, natural(X1));
            }
        }
        g.lineTo(X1, H);
        g.lineTo(X0, H);
        g.closePath();
        g.setFill(dirt);
        g.fill();
        g.setStroke(grass);
        g.setLineWidth(3);
        g.setLineCap(StrokeLineCap.ROUND);
        // Re-trace only the top edge in green.
        g.beginPath();
        g.moveTo(X0, natural(X0));
        switch (t) {
            case NONE, ENCAPSULATE -> g.lineTo(X1, natural(X1));
            case BEARD_THIN -> {
                g.lineTo(28, natural(28));
                g.quadraticCurveTo(44, natural(44), HX0, HBOTTOM);
                g.moveTo(HX1, HBOTTOM);
                g.quadraticCurveTo(110, HBOTTOM, 126, natural(126));
                g.lineTo(X1, natural(X1));
            }
            case BEARD_BOX -> {
                g.lineTo(HX0, natural(HX0));
                g.moveTo(HX1, natural(HX1));
                g.lineTo(X1, natural(X1));
            }
            case BURY -> {
                g.lineTo(30, natural(30));
                g.quadraticCurveTo(42, 38, HX0, 40);
                g.lineTo(HX1, 42);
                g.quadraticCurveTo(114, 44, 128, natural(128));
                g.lineTo(X1, natural(X1));
            }
        }
        g.stroke();
    }
}
