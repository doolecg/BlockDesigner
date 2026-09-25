package io.blockdesigner.core.edit;

import io.blockdesigner.core.model.Scene;

import java.util.ArrayList;
import java.util.List;

/**
 * A labelled group of changes undone and redone as a unit. Transactions with the same non-null {@code mergeKey}
 * pushed within the merge window collapse into one undo step (e.g. a burst of scroll-wheel nudges).
 */
public final class Transaction {
    private final String label;
    private final String mergeKey;
    private final List<Change> changes = new ArrayList<>();
    private long lastTouched;

    public Transaction(String label) {
        this(label, null);
    }

    public Transaction(String label, String mergeKey) {
        this.label = label;
        this.mergeKey = mergeKey;
        this.lastTouched = System.currentTimeMillis();
    }

    public String label() {
        return label;
    }

    public String mergeKey() {
        return mergeKey;
    }

    long lastTouched() {
        return lastTouched;
    }

    public Transaction add(Change c) {
        if (!changes.isEmpty()) {
            Change last = changes.getLast();
            if (last.absorb(c)) return this;
            if (last instanceof LayerChange.Properties p) {
                LayerChange.Properties merged = p.merge(c);
                if (merged != null) {
                    changes.set(changes.size() - 1, merged);
                    return this;
                }
            }
        }
        changes.add(c);
        return this;
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }

    public List<Change> changes() {
        return List.copyOf(changes);
    }

    void mergeFrom(Transaction later) {
        for (Change c : later.changes) add(c);
        lastTouched = System.currentTimeMillis();
    }

    void undo(Scene scene) {
        for (int i = changes.size() - 1; i >= 0; i--) changes.get(i).undo(scene);
    }

    void redo(Scene scene) {
        for (Change c : changes) c.redo(scene);
    }
}
