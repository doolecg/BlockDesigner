package io.blockdesigner.app.ui;

import io.blockdesigner.app.plugins.SceneObjectStore;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The right-click menu of a plugin scene object, the same in the view and in the Layers panel: the plugin's own
 * entries ({@link io.blockdesigner.plugin.SceneObject#menu}), then rename, focus, hide, lock and delete.
 */
final class ObjectMenus {
    private ObjectMenus() {
    }

    /** @param focus frames the object in the view */
    static List<MenuItem> items(SceneObjectStore store, SceneObjectStore.Entry e, Consumer<SceneObjectStore.Entry> focus) {
        List<MenuItem> out = new ArrayList<>();
        try {
            List<MenuItem> own = e.object() == null ? List.of() : e.object().menu(e);
            if (own != null) out.addAll(own);
        } catch (Throwable t) {
            store.report(e, t);
        }
        if (!out.isEmpty()) out.add(new SeparatorMenuItem());
        MenuItem rename = new MenuItem("Rename…", new FontIcon(Feather.EDIT_2));
        rename.setOnAction(a -> rename(e));
        MenuItem frame = new MenuItem("Focus camera", new FontIcon(Feather.CROSSHAIR));
        frame.setOnAction(a -> focus.accept(e));
        MenuItem hide = new MenuItem(e.visible() ? "Hide" : "Show", new FontIcon(e.visible() ? Feather.EYE_OFF : Feather.EYE));
        hide.setOnAction(a -> e.setVisible(!e.visible()));
        MenuItem lock = new MenuItem(e.locked() ? "Unlock" : "Lock", new FontIcon(e.locked() ? Feather.UNLOCK : Feather.LOCK));
        lock.setOnAction(a -> e.setLocked(!e.locked()));
        MenuItem delete = new MenuItem("Delete", new FontIcon(Feather.TRASH_2));
        delete.setOnAction(a -> e.remove());
        out.addAll(List.of(rename, frame, hide, lock, new SeparatorMenuItem(), delete));
        return out;
    }

    static void rename(SceneObjectStore.Entry e) {
        TextInputDialog d = new TextInputDialog(e.name());
        d.setTitle("Rename");
        d.setHeaderText("Rename " + e.name());
        d.setContentText("Name");
        d.showAndWait().map(String::strip).filter(s -> !s.isEmpty()).ifPresent(e::setName);
    }
}
