package io.blockdesigner.app.refplanes;

import io.blockdesigner.plugin.Drawing;
import io.blockdesigner.plugin.ObjectHandle;
import io.blockdesigner.plugin.ViewInfo;
import io.blockdesigner.plugin.ui.Icon;
import io.blockdesigner.plugin.ui.Theme;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A reference image's own right-click entries: Properties (every number: position, rotation, scale, UV, opacity, with
 * their resets), opacity, the views it shows in, how blocks cover it, flips, aligning to the view and replacing the
 * picture. The Layers panel and the view add rename, focus, hide, lock and delete after them (ObjectMenus).
 */
final class ReferenceMenu {
    private ReferenceMenu() {
    }

    static List<MenuItem> items(ObjectHandle self, ReferenceImage ref, ReferenceType type) {
        ReferenceSettings s = ref.settings();
        List<MenuItem> out = new ArrayList<>();

        MenuItem props = new MenuItem("Properties…", Icon.EDIT.node());
        props.setOnAction(e -> PropertiesDialog.show(self, ref, type.ui()));

        Menu opacity = new Menu("Opacity · " + Math.round(s.opacity() * 100) + "%");
        opacity.getItems().add(new CustomMenuItem(opacitySlider(self, ref), false));
        opacity.getItems().add(new SeparatorMenuItem());
        ToggleGroup og = new ToggleGroup();
        for (int pct : new int[]{100, 75, 50, 25}) {
            RadioMenuItem r = new RadioMenuItem(pct + "%");
            r.setToggleGroup(og);
            r.setSelected(Math.round(s.opacity() * 100) == pct);
            r.setOnAction(e -> ref.change(self, "Opacity " + pct + "% for " + self.name(), x -> x.withOpacity(pct / 100.0)));
            opacity.getItems().add(r);
        }

        Menu showIn = new Menu("Show in · " + s.showIn().label);
        ToggleGroup sg = new ToggleGroup();
        for (ReferenceSettings.ShowIn v : ReferenceSettings.ShowIn.values()) {
            RadioMenuItem r = new RadioMenuItem(v.label + (v.side().isPresent() ? " (orthographic)" : ""));
            r.setToggleGroup(sg);
            r.setSelected(s.showIn() == v);
            r.setOnAction(e -> ref.change(self, "Show " + self.name() + " in " + v.label.toLowerCase(Locale.ROOT), x -> x.withShowIn(v)));
            showIn.getItems().add(r);
            if (v == ReferenceSettings.ShowIn.ORTHO) showIn.getItems().add(new SeparatorMenuItem());
        }

        Menu depth = new Menu("Draw · " + NewPictures.draw(s.depth()));
        ToggleGroup dg = new ToggleGroup();
        for (Drawing.Depth d : Drawing.Depth.values()) {
            String label = NewPictures.draw(d);
            RadioMenuItem r = new RadioMenuItem(label);
            r.setToggleGroup(dg);
            r.setSelected(s.depth() == d);
            if (d == Drawing.Depth.IN_SCENE) r.setText(label + " (blocks in front hide it)");
            r.setOnAction(e -> ref.change(self, "Draw " + self.name() + " " + label.toLowerCase(Locale.ROOT), x -> x.withDepth(d)));
            depth.getItems().add(r);
        }

        CheckMenuItem flipX = new CheckMenuItem("Flip horizontally");
        flipX.setSelected(s.flipX());
        flipX.setOnAction(e -> ref.change(self, "Flip " + self.name(), x -> x.withFlip(!x.flipX(), x.flipY())));
        CheckMenuItem flipY = new CheckMenuItem("Flip vertically");
        flipY.setSelected(s.flipY());
        flipY.setOnAction(e -> ref.change(self, "Flip " + self.name(), x -> x.withFlip(x.flipX(), !x.flipY())));

        ViewInfo view = type.view();
        MenuItem align = new MenuItem(view.side().map(v -> "Align to the " + v.name().toLowerCase(Locale.ROOT) + " view").orElse("Face the camera"));
        align.setOnAction(e -> self.setPose(self.pose().withRotation(view.side().map(ViewInfo.Side::facing)
                .orElseGet(() -> ReferenceSettings.facingYaw(view.forward()))), "Align " + self.name() + " to the view"));
        MenuItem onlyHere = new MenuItem(view.side().map(v -> "Show only in the " + v.name().toLowerCase(Locale.ROOT) + " view").orElse(""));
        onlyHere.setOnAction(e -> ref.change(self, "Show " + self.name() + " in one view",
                x -> x.withShowIn(ReferenceSettings.ShowIn.of(view.side().orElseThrow()))));

        MenuItem replace = new MenuItem("Replace picture…", Icon.IMAGE.node());
        replace.setOnAction(e -> replace(self, ref, type));

        out.addAll(List.of(props, new SeparatorMenuItem(), opacity, showIn, depth, flipX, flipY, new SeparatorMenuItem(), align));
        if (view.side().isPresent()) out.add(onlyHere);
        out.add(replace);
        return out;
    }

    private static void replace(ObjectHandle self, ReferenceImage ref, ReferenceType type) {
        File f = ReferenceType.chooser("Replace the picture of " + self.name()).showOpenDialog(type.ui().owner());
        if (f == null) return;
        try {
            ReferenceType.Loaded l = type.load(f.toPath());
            ref.change(self, "Replace picture of " + self.name(), x -> x.withImage(l.blob(), l.fileName(), l.decoded().width(), l.decoded().height()));
            ref.set(ref.settings(), l.decoded().image());
        } catch (IOException ex) {
            type.toast("✖ " + f.getName() + ": " + ex.getMessage());
        }
    }

    /** An opacity slider: the view follows while dragging; letting go makes one undo step. */
    private static HBox opacitySlider(ObjectHandle self, ReferenceImage ref) {
        Slider slider = new Slider(0, 100, ref.settings().opacity() * 100);
        slider.setPrefWidth(160);
        slider.setAccessibleText("Opacity");
        Label pct = new Label(Math.round(slider.getValue()) + "%");
        pct.setMinWidth(40);
        ReferenceSettings[] before = {null};
        Consumer<Double> commit = value -> {
            if (before[0] != null) {
                ref.set(before[0], null);
                before[0] = null;
            }
            if (Math.abs(ref.settings().opacity() * 100 - value) > 0.5) {
                ref.change(self, "Opacity " + Math.round(value) + "% for " + self.name(), x -> x.withOpacity(value / 100));
            }
        };
        slider.valueProperty().addListener((o, a, b) -> {
            pct.setText(Math.round(b.doubleValue()) + "%");
            if (slider.isValueChanging()) {
                if (before[0] == null) before[0] = ref.settings();
                ref.set(before[0].withOpacity(b.doubleValue() / 100), null);
                self.refresh();
            } else {
                commit.accept(b.doubleValue());
            }
        });
        slider.valueChangingProperty().addListener((o, was, now) -> {
            if (!now) commit.accept(slider.getValue());
        });
        HBox box = new HBox(Theme.SM, slider, pct);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(Theme.XS));
        return box;
    }
}
