package io.blockdesigner.plugin.ui;

import javafx.beans.value.ObservableBooleanValue;
import javafx.css.PseudoClass;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Slider;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Labelled controls in two columns: the label on the left (one width for the whole form, about 40% of it, 96–140 px)
 * and the control filling the right, with an optional unit after it and a line of help under it. Narrow forms put the
 * labels above the controls. Every label is {@link Label#setLabelFor linked} to its control, so screen readers name
 * the control. Since API 6.
 *
 * <pre>{@code
 * Form f = new Form();
 * f.row("Seed", seedField).help("Same seed, same terrain.").error(valid ? null : "A seed should be a whole number.");
 * f.row("Height", heightSpinner).unit("blocks");
 * f.row(new CheckBox("Show on the ground"));
 * }</pre>
 */
@SuppressWarnings("this-escape") // components set themselves up in their constructors, like JavaFX controls
public class Form extends GridPane {
    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");
    private static final PseudoClass NARROW = PseudoClass.getPseudoClass("narrow");
    /** Below this width the labels go above their controls. */
    private static final double STACK_BELOW = 280;

    private final List<Row> rows = new ArrayList<>();
    private final ColumnConstraints labels = new ColumnConstraints();
    private final ColumnConstraints controls = new ColumnConstraints();
    private boolean stacked;

    public Form() {
        Theme.attach(this);
        getStyleClass().add("bd-form");
        setMinWidth(0);
        labels.setMinWidth(Theme.LABEL_MIN);
        labels.setPrefWidth(Theme.LABEL_PREF);
        labels.setMaxWidth(Theme.LABEL_MAX);
        labels.setHalignment(HPos.LEFT);
        controls.setHgrow(Priority.ALWAYS);
        controls.setFillWidth(true);
        controls.setMinWidth(0);
        getColumnConstraints().addAll(labels, controls);
    }

    /** Fits the label column to the width (about 40%) and stacks the labels when narrow, in the same layout pass. */
    @Override
    protected void layoutChildren() {
        double width = getWidth() - snappedLeftInset() - snappedRightInset();
        if (width > 0) {
            double label = Math.clamp(width * 0.4, Theme.LABEL_MIN, Theme.LABEL_MAX);
            // Min as well as pref: long help text under a control must not squeeze the label column.
            if (Math.abs(labels.getPrefWidth() - label) > 0.5) {
                labels.setMinWidth(label);
                labels.setPrefWidth(label);
            }
            boolean s = width < STACK_BELOW;
            if (s != stacked) {
                stacked = s;
                pseudoClassStateChanged(NARROW, s);
                relayout();
            }
        }
        fitUnits();
        super.layoutChildren();
    }

    /**
     * Units of controls that fill the width (sliders, fields, lists) form one column: when one of them has a unit, every
     * labelled row with such a control keeps a slot as wide as the widest unit, so they all end at the same place. A
     * control of its own size (a spinner) just has its unit after it.
     */
    private void fitUnits() {
        double w = 0;
        for (Row r : rows) if (r.grows && r.unit != null && !r.unit.getText().isEmpty()) w = Math.max(w, r.unit.prefWidth(-1));
        for (Row r : rows) {
            if (r.label == null || !r.grows) continue;
            if (w > 0 && r.unit == null) r.unit("");
            if (r.unit == null) continue;
            Controls.show(r.unit, w > 0);
            if (Math.abs(r.unit.getMinWidth() - w) > 0.5) {
                r.unit.setMinWidth(w);
                r.unit.setPrefWidth(w);
            }
        }
    }

    /** A labelled row: the label left (or above, when narrow), the control right. */
    public Row row(String label, Node control) {
        Row r = new Row(label, control);
        rows.add(r);
        relayout();
        return r;
    }

    /** A row across the whole width: a check box, a row of buttons. */
    public Row row(Node fullWidth) {
        Row r = new Row(null, fullWidth);
        rows.add(r);
        relayout();
        return r;
    }

    /** The rows, in order. */
    public List<Row> rows() {
        return List.copyOf(rows);
    }

    private void relayout() {
        List<Node> order = new ArrayList<>();
        int r = 0;
        for (Row row : rows) {
            boolean shown = row.shown == null || row.shown.get();
            for (Node n : row.nodes()) Controls.show(n, shown);
            if (!shown) {
                order.addAll(row.nodes());
                continue;
            }
            if (row.label == null) {
                GridPane.setConstraints(row.cell, 0, r++, 2, 1);
            } else if (stacked) {
                GridPane.setConstraints(row.label, 0, r++, 2, 1, HPos.LEFT, VPos.TOP);
                GridPane.setConstraints(row.cell, 0, r++, 2, 1);
            } else {
                GridPane.setConstraints(row.label, 0, r, 1, 1, HPos.LEFT, VPos.TOP);
                GridPane.setConstraints(row.cell, 1, r++, 1, 1);
            }
            order.addAll(row.nodes());
        }
        // Children in row order, so Tab walks the form top to bottom; rows that stay keep their nodes (and focus).
        if (!getChildren().equals(order)) {
            getChildren().removeIf(n -> !order.contains(n));
            for (int i = 0; i < order.size(); i++) {
                Node n = order.get(i);
                int at = getChildren().indexOf(n);
                if (at == i) continue;
                if (at >= 0) getChildren().remove(at);
                getChildren().add(i, n);
            }
        }
    }

    /** One row of a {@link Form}; its methods return it, for chaining. */
    public final class Row {
        private final Label label;
        private final Node control;
        private final VBox cell = new VBox();
        private final HBox line = new HBox();
        private Label unit;
        /** Whether the control fills the width (and so shares the form's unit column). */
        private boolean grows;
        private Label help;
        private Label error;
        private ObservableBooleanValue shown;

        private Row(String text, Node control) {
            this.control = control;
            cell.getStyleClass().add("bd-form-cell");
            cell.setMinWidth(0);
            line.getStyleClass().add("bd-form-line");
            line.setAlignment(Pos.CENTER_LEFT);
            line.setMinWidth(0);
            if (text != null) {
                label = new Label(text);
                label.getStyleClass().add("bd-form-label");
                label.setMinWidth(0);
                label.setMaxWidth(Double.MAX_VALUE);
                label.setTextOverrun(OverrunStyle.ELLIPSIS);
                label.setTooltip(new Tooltip(text));
                label.setLabelFor(control);
                if (control.getAccessibleText() == null) control.setAccessibleText(text);
            } else {
                label = null;
            }
            if (control instanceof ComboBoxBase<?> || control instanceof TextInputControl || control instanceof Slider
                    || control instanceof ChoiceBox<?>) {
                ((Region) control).setMaxWidth(Double.MAX_VALUE);
                ((Region) control).setMinWidth(0);
                HBox.setHgrow(control, Priority.ALWAYS);
                grows = true;
            } else if (control instanceof Region reg && (reg instanceof HBox || reg instanceof VBox || reg instanceof GridPane)) {
                HBox.setHgrow(control, Priority.ALWAYS);
                reg.setMinWidth(0);
                grows = true;
            }
            line.getChildren().add(control);
            cell.getChildren().add(line);
        }

        private List<Node> nodes() {
            return label == null ? List.of(cell) : List.of(label, cell);
        }

        /** A line of muted help under the control. */
        public Row help(String text) {
            if (help == null) {
                help = Controls.hint(text);
                cell.getChildren().add(1, help);
            } else help.setText(text);
            return this;
        }

        /** A unit after the control: "blocks", "px", "°". */
        public Row unit(String text) {
            if (unit == null) {
                unit = new Label(text);
                unit.getStyleClass().add("bd-unit");
                unit.setMinWidth(Region.USE_PREF_SIZE);
                line.getChildren().add(unit);
            } else unit.setText(text);
            return this;
        }

        /** Marks the control as wrong ({@code :error}) with the message under it; null clears it. */
        public Row error(String messageOrNull) {
            boolean bad = messageOrNull != null && !messageOrNull.isBlank();
            control.pseudoClassStateChanged(ERROR, bad);
            if (error == null && bad) {
                error = new Label();
                error.getStyleClass().add("bd-form-error");
                error.setWrapText(true);
                error.setMinHeight(Region.USE_PREF_SIZE);
                cell.getChildren().add(error);
            }
            if (error != null) {
                error.setText(bad ? messageOrNull : "");
                Controls.show(error, bad);
            }
            return this;
        }

        /** Greys the row out (label and control) while {@code on} is false, and indents it under what switches it. */
        public Row enabledWhen(ObservableBooleanValue on) {
            javafx.beans.binding.BooleanBinding off = javafx.beans.binding.Bindings.not(on);
            cell.disableProperty().bind(off);
            if (label != null) {
                label.disableProperty().bind(off);
                label.getStyleClass().add("bd-indent");
            } else cell.getStyleClass().add("bd-indent");
            return this;
        }

        /** Shows the row only while {@code on} is true; hidden rows leave no gap. */
        public Row shownWhen(ObservableBooleanValue on) {
            shown = on;
            on.addListener((o, a, b) -> relayout());
            relayout();
            return this;
        }

        /** The label, or null for a full-width row. */
        public Label label() {
            return label;
        }

        public Node control() {
            return control;
        }
    }
}
