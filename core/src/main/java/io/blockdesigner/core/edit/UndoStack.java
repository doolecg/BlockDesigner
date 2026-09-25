package io.blockdesigner.core.edit;

import io.blockdesigner.core.model.Scene;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** Linear undo/redo history of {@link Transaction}s. Changes are already applied when pushed. */
public final class UndoStack {
    public static final long DEFAULT_MERGE_WINDOW_MS = 800;

    private final Scene scene;
    private final Deque<Transaction> undo = new ArrayDeque<>();
    private final Deque<Transaction> redo = new ArrayDeque<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private int limit = 500;
    private long mergeWindowMs = DEFAULT_MERGE_WINDOW_MS;
    private boolean topSealed;
    private Transaction group;
    private int groupDepth;

    public UndoStack(Scene scene) {
        this.scene = scene;
    }

    public void addListener(Runnable r) {
        listeners.add(r);
    }

    public void setLimit(int limit) {
        this.limit = Math.max(1, limit);
        trim();
    }

    public void setMergeWindowMs(long ms) {
        this.mergeWindowMs = ms;
    }

    public void push(Transaction t) {
        if (t.isEmpty()) return;
        if (group != null) {
            for (Change c : t.changes()) group.add(c);
            return;
        }
        Transaction top = undo.peek();
        if (!topSealed && top != null && t.mergeKey() != null && Objects.equals(top.mergeKey(), t.mergeKey())
                && System.currentTimeMillis() - top.lastTouched() <= mergeWindowMs) {
            top.mergeFrom(t);
        } else {
            undo.push(t);
            topSealed = false;
            trim();
        }
        redo.clear();
        fire();
    }

    /**
     * Collects every transaction pushed until the matching {@link #endGroup()} into one undo step (e.g. a whole
     * WorldEdit command). Groups nest; only the outermost one is recorded. Undo/redo are disabled while a group is open.
     */
    public void beginGroup(String label) {
        if (groupDepth++ == 0) group = new Transaction(label);
    }

    public void endGroup() {
        if (groupDepth == 0) return;
        if (--groupDepth > 0) return;
        Transaction g = group;
        group = null;
        if (g.isEmpty()) return;
        undo.push(g);
        topSealed = true;
        trim();
        redo.clear();
        fire();
    }

    public boolean inGroup() {
        return group != null;
    }

    /** Stops the top transaction from absorbing further merges (e.g. when a scroll burst ends). */
    public void sealTop() {
        topSealed = true;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public Optional<String> undoLabel() {
        return Optional.ofNullable(undo.peek()).map(Transaction::label);
    }

    public Optional<String> redoLabel() {
        return Optional.ofNullable(redo.peek()).map(Transaction::label);
    }

    public int size() {
        return undo.size();
    }

    public boolean undo() {
        if (group != null) return false;
        Transaction t = undo.poll();
        if (t == null) return false;
        t.undo(scene);
        redo.push(t);
        topSealed = true;
        fire();
        return true;
    }

    public boolean redo() {
        if (group != null) return false;
        Transaction t = redo.poll();
        if (t == null) return false;
        t.redo(scene);
        undo.push(t);
        topSealed = true;
        fire();
        return true;
    }

    public void clear() {
        undo.clear();
        redo.clear();
        fire();
    }

    private void trim() {
        while (undo.size() > limit) undo.removeLast();
    }

    private void fire() {
        listeners.forEach(Runnable::run);
    }
}
