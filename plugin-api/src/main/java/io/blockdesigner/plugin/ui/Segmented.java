package io.blockdesigner.plugin.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.List;
import java.util.function.Function;

/**
 * A few mutually exclusive choices shown side by side, one always picked: Picture / Regions / Blocks, a scale of
 * 1 / 2 / 4 / 8. For views of the same thing, not for pages (register more panels for those). Since API 6.
 */
@SuppressWarnings("this-escape") // components set themselves up in their constructors, like JavaFX controls
public class Segmented<T> extends HBox {
    private final ObjectProperty<T> value = new SimpleObjectProperty<>(this, "value");
    private final ToggleGroup group = new ToggleGroup();

    public Segmented(List<T> values, Function<T, String> label) {
        if (values.isEmpty()) throw new IllegalArgumentException("Segmented needs at least one value");
        Theme.attach(this);
        getStyleClass().add("bd-segmented");
        setMinWidth(0);
        for (T v : values) {
            ToggleButton b = new ToggleButton(label.apply(v));
            b.getStyleClass().add("bd-segment");
            b.setUserData(v);
            b.setToggleGroup(group);
            b.setMaxWidth(Double.MAX_VALUE);
            b.setMinWidth(0);
            HBox.setHgrow(b, Priority.ALWAYS);
            getChildren().add(b);
        }
        // One stays picked: clicking the picked one keeps it.
        group.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) {
                if (a != null) a.setSelected(true);
                return;
            }
            @SuppressWarnings("unchecked") T v = (T) b.getUserData();
            if (!java.util.Objects.equals(value.get(), v)) value.set(v);
        });
        value.addListener((o, a, v) -> {
            for (Toggle t : group.getToggles()) if (java.util.Objects.equals(t.getUserData(), v)) group.selectToggle(t);
        });
        value.set(values.getFirst());
    }

    /** The picked value. */
    public ObjectProperty<T> valueProperty() {
        return value;
    }

    public T getValue() {
        return value.get();
    }

    public void setValue(T v) {
        value.set(v);
    }
}
