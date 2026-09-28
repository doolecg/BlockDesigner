package io.blockdesigner.app.refplanes;

import io.blockdesigner.plugin.ObjectHandle;
import io.blockdesigner.plugin.Pose;
import io.blockdesigner.plugin.ToolEvent.Vec3;
import io.blockdesigner.plugin.ui.Controls;
import io.blockdesigner.plugin.ui.Form;
import io.blockdesigner.plugin.ui.Section;
import io.blockdesigner.plugin.ui.Theme;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every number of a reference image in one window: position, rotation, scale (with resets for the rotation and the
 * picture's aspect ratio), UV offset and scale (with a reset), and opacity. Apply changes the pose and the settings (an
 * undo step each, when they changed); a field that isn't a number says so and keeps Apply off.
 */
final class PropertiesDialog {
    private PropertiesDialog() {
    }

    static void show(ObjectHandle self, ReferenceImage ref, ReferencePlanes.Ui ui) {
        Pose p = self.pose();
        ReferenceSettings s = ref.settings();
        List<BooleanProperty> bad = new ArrayList<>();

        TextField[] pos = fields(bad, "Position", new String[]{"X", "Y", "Z"}, p.position().x(), p.position().y(), p.position().z());
        TextField[] rot = fields(bad, "Rotation", new String[]{"X", "Y", "Z"}, p.rotation().x(), p.rotation().y(), p.rotation().z());
        TextField[] scale = fields(bad, "Scale", new String[]{"X", "Y", "Z"}, p.scale().x(), p.scale().y(), p.scale().z());
        TextField[] uvOffset = fields(bad, "UV offset", new String[]{"U", "V"}, s.uOffset(), s.vOffset());
        TextField[] uvScale = fields(bad, "UV scale", new String[]{"U", "V"}, s.uScale(), s.vScale());
        TextField[] opacity = fields(bad, "Opacity", new String[]{""}, Math.round(s.opacity() * 1000) / 10.0);

        Form transform = new Form();
        row(transform, "Position", pos, bad).unit("blocks");
        row(transform, "Rotation", rot, bad).unit("°");
        row(transform, "Scale", scale, bad);
        Section transformSection = new Section("Transform", transform).actions(
                Controls.link("Reset rotation", () -> set(rot, 0, 0, 0)),
                Controls.link("Reset aspect ratio", () -> {
                    double[] now = parse(scale);
                    if (now == null) return;
                    Vec3 v = ReferenceSettings.resetAspect(new Vec3(now[0], now[1], now[2]));
                    set(scale, v.x(), v.y(), v.z());
                }));

        Form picture = new Form();
        row(picture, "UV offset", uvOffset, bad);
        row(picture, "UV scale", uvScale, bad);
        row(picture, "Opacity", opacity, bad).unit("%");
        Section pictureSection = new Section("Picture", picture,
                Controls.caption(s.fileName() + " · " + s.width() + "×" + s.height() + " px · 1 block tall at scale 1"))
                .actions(Controls.link("Reset UV", () -> {
                    set(uvOffset, 0, 0);
                    set(uvScale, 1, 1);
                }));

        VBox body = new VBox(Theme.LG, transformSection, pictureSection);
        body.setPrefWidth(440);

        Dialog<ButtonType> d = new Dialog<>();
        ui.style(d);
        d.setTitle("Properties: " + self.name());
        d.setHeaderText(null);
        d.setGraphic(null);
        d.getDialogPane().getStyleClass().add("bd-dialog");
        d.getDialogPane().setContent(body);
        ButtonType apply = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, apply);
        var applyButton = d.getDialogPane().lookupButton(apply);
        applyButton.getStyleClass().add("accent");
        applyButton.disableProperty().bind(Bindings.createBooleanBinding(() -> bad.stream().anyMatch(BooleanProperty::get),
                bad.toArray(BooleanProperty[]::new)));
        if (d.showAndWait().orElse(ButtonType.CANCEL) != apply || !self.exists()) return;

        double[] a = parse(pos), r = parse(rot), c = parse(scale), uo = parse(uvOffset), us = parse(uvScale), o = parse(opacity);
        Pose want = new Pose(new Vec3(a[0], a[1], a[2]), new Vec3(r[0], r[1], r[2]), new Vec3(c[0], c[1], c[2]));
        if (!want.equals(self.pose())) self.setPose(want, "Edit transform of " + self.name());
        ReferenceSettings next = ref.settings().withUv(uo[0], uo[1], us[0], us[1]).withOpacity(Math.clamp(o[0] / 100, 0, 1));
        if (!next.equals(ref.settings())) ref.change(self, "Edit " + self.name(), x -> next);
    }

    /** Number fields for one row; each marks {@code bad} while it isn't a number. */
    private static TextField[] fields(List<BooleanProperty> bad, String row, String[] axes, double... values) {
        TextField[] f = new TextField[values.length];
        for (int i = 0; i < values.length; i++) {
            TextField t = new TextField(num(values[i]));
            t.setPrefColumnCount(5);
            t.setMinWidth(0);
            t.setPromptText(axes[i]);
            t.setAccessibleText((row + " " + axes[i]).strip());
            if (!axes[i].isEmpty()) t.setTooltip(new javafx.scene.control.Tooltip(axes[i]));
            HBox.setHgrow(t, Priority.ALWAYS);
            BooleanProperty wrong = new SimpleBooleanProperty();
            t.textProperty().addListener((o, was, now) -> wrong.set(number(now) == null));
            bad.add(wrong);
            f[i] = t;
        }
        return f;
    }

    /** A form row of fields side by side; it shows an error while one of them isn't a number. */
    private static Form.Row row(Form form, String label, TextField[] fields, List<BooleanProperty> bad) {
        Node control = fields.length == 1 ? fields[0] : new HBox(Theme.XS, fields);
        Form.Row row = form.row(label, control);
        if (fields.length == 1) fields[0].setMaxWidth(96); // a single number needs no full-width field
        for (TextField t : fields) {
            t.textProperty().addListener((o, was, now) -> {
                boolean any = false;
                for (TextField x : fields) any |= number(x.getText()) == null;
                row.error(any ? "Enter a number." : null);
            });
        }
        return row;
    }

    private static void set(TextField[] fields, double... values) {
        for (int i = 0; i < fields.length; i++) fields[i].setText(num(values[i]));
    }

    /** The fields as numbers; null if one isn't. */
    static double[] parse(TextField[] fields) {
        double[] out = new double[fields.length];
        for (int i = 0; i < fields.length; i++) {
            Double v = number(fields[i].getText());
            if (v == null) return null;
            out[i] = v;
        }
        return out;
    }

    /** A number typed by the user ("," as a decimal point is fine); null if it isn't one. */
    static Double number(String text) {
        try {
            double v = Double.parseDouble(text.strip().replace(',', '.'));
            return Double.isFinite(v) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A number for a field: up to three decimals, no trailing zeros. */
    static String num(double v) {
        String s = String.format(Locale.ROOT, "%.3f", v);
        s = s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
        return s.equals("-0") ? "0" : s;
    }
}
