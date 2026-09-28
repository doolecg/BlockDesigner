package io.blockdesigner.app.refplanes;

import io.blockdesigner.app.ConsoleLog;
import io.blockdesigner.app.Settings;
import io.blockdesigner.app.plugins.SceneObjectStore;
import io.blockdesigner.plugin.OptionValues;
import io.blockdesigner.plugin.Options;
import javafx.scene.control.Dialog;
import javafx.stage.Window;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Reference images, built in: pictures placed in the scene to build from, like Blender's reference images. They are
 * scene objects ({@link SceneObjectStore}), so they are listed in the Layers panel as REFERENCE, moved, turned and
 * scaled with the Move, Rotate and Scale tools, saved in the project, and set up from their right-click menu
 * (Properties, opacity, the views they show in, whether blocks cover them, flips…). How new pictures start out is set
 * on the Reference images page of the Settings window.
 *
 * <p>This used to be the Reference Planes plugin (up to its 1.2.0). Its objects are still saved under the plugin's
 * id ({@code reference-planes/reference}), so projects made with the plugin open with their pictures, and
 * {@link #importPluginSettings} carries over the plugin's settings and key. The plugin's jar is no longer loaded
 * ({@code PluginManager.BUILT_IN}).
 */
public final class ReferencePlanes {
    /** The id its objects are saved under in projects, and the plugin's id: kept so older projects still open. */
    public static final String ID = "reference-planes";
    public static final String NAME = "Reference Planes";
    /** Its entry in Settings › Keybinds ({@code Keybinds.Action}); no key by default. */
    public static final String KEYBIND = "ADD_REFERENCE_IMAGE";
    /** Where the plugin kept its settings and its action's key in the settings file. */
    static final String PLUGIN_SETTINGS = ID + "/settings/tab";
    static final String PLUGIN_ACTION_KEY = ID + "/Add reference image…";

    /** What it needs from the main window. */
    public interface Ui {
        /** The main window, for file choosers and dialogs. */
        Window owner();

        /** A short message over the 3D view. */
        void toast(String message);

        /** Gives a dialog the app's look and the main window as its owner. */
        void style(Dialog<?> dialog);
    }

    private final SceneObjectStore store;
    private final Settings settings;
    private final Ui ui;
    private final ReferenceType type;

    /** Adds the reference image type to {@code store}; pictures in the open project come back straight away. */
    public ReferencePlanes(SceneObjectStore store, Settings settings, Ui ui) {
        this.store = store;
        this.settings = settings;
        this.ui = ui;
        this.type = new ReferenceType(this);
        store.register(this, ID, type);
    }

    /** Layers › Add reference image… (and its key): pick pictures and add each, facing the view. */
    public void chooseAndAdd() {
        type.chooseAndAdd();
    }

    // ---- settings ----------------------------------------------------------------------------------------------

    /** The settings for new pictures, for the Settings window's Reference images page. */
    public static Options settingsOptions() {
        return NewPictures.OPTIONS;
    }

    /** The settings for new pictures as saved (defaults for anything missing). */
    public static OptionValues settingsValues(Settings s) {
        return NewPictures.values(s.newReferenceImages);
    }

    /** Keeps new settings for new pictures; the next picture added uses them. */
    public static void saveSettings(Settings s, OptionValues v) {
        s.newReferenceImages = new LinkedHashMap<>(v.toStrings());
    }

    /** How the next picture starts out, from the settings as they are now. */
    NewPictures newPictures() {
        return NewPictures.of(settingsValues(settings));
    }

    /**
     * Carries over what the Reference Planes plugin kept in the settings file, then drops its entries: its settings for
     * new pictures (unless there are built-in ones already) and the key given to its "Add reference image…" action
     * (unless the built-in action has a key, or one of BlockDesigner's own actions uses that key: the plugin's key
     * never won over those, and the built-in one would). Safe to call at every start.
     *
     * @param appUsesKey whether one of BlockDesigner's actions is bound to a key (given as its saved text)
     * @return whether anything changed (the settings need saving)
     */
    public static boolean importPluginSettings(Settings s, Predicate<String> appUsesKey) {
        boolean changed = false;
        Map<String, String> old = s.pluginOptions.remove(PLUGIN_SETTINGS);
        if (old != null) {
            if (s.newReferenceImages == null || s.newReferenceImages.isEmpty()) s.newReferenceImages = new LinkedHashMap<>(old);
            changed = true;
        }
        String key = s.pluginActionKeys.remove(PLUGIN_ACTION_KEY);
        if (key != null) {
            changed = true;
            if (!key.isBlank() && !s.keybinds.containsKey(KEYBIND) && !appUsesKey.test(key)) s.keybinds.put(KEYBIND, key + "|");
        }
        changed |= s.disabledPlugins.remove(ID);
        changed |= s.pluginPages.remove(ID) != null;
        changed |= s.closedPluginPanels.removeIf(p -> p.startsWith(ID + "/"));
        return changed;
    }

    // ---- for the reference image classes -------------------------------------------------------------------------

    SceneObjectStore store() {
        return store;
    }

    Ui ui() {
        return ui;
    }

    void toast(String message) {
        ui.toast(message);
    }

    void log(String message) {
        ConsoleLog.warn(NAME, message);
    }

    /** How the scene object store names it when one of its pictures fails. */
    @Override
    public String toString() {
        return NAME;
    }
}
