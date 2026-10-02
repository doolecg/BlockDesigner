# Testing plugins

There are three levels, from cheapest to most complete.

## 1. Unit tests against the API jars

The API jars in `libs/` are on the test class path (`testImplementation(blockDesigner)` in the template), so
everything that doesn't need the running app can be tested with JUnit: your algorithms, your own data formats,
anything that works on `core` types such as `BlockState`, `Structure` and `BlockPos`.

```java
// Illustrative: Tally is the plugin's own class.
class TallyTest {
    @Test
    void countsDoubleSlabsAsTwo() {
        Structure s = new Structure();
        s.set(0, 0, 0, BlockState.parse("minecraft:oak_slab[type=double]"));
        assertThat(Tally.count(s)).containsEntry("minecraft:oak_slab", 2L);
    }
}
```

That is what the official plugins do (`ItemsTest`, `TallyTest` in the BlockCompanion Plugin; the generator tests in Terrain
Generator). Design for it: keep logic in plain classes that take `Structure`, `OptionValues` or your own types, and
keep the `PluginContext` and JavaFX parts thin.

Useful pieces that work without the app:

- `Options.builder()...build().defaults()` gives real `OptionValues`, and `values.with("key", value)` changes one, so
  a transform or tool can be driven with the options a user would pick.
- `BlockState.of(...)`, `BlockState.parse(...)`, `Structure`, `BlockPos` and the rest of `core`.
- A `WorldEdit.World` is a two-method interface (`get`, `set`); a `HashMap`-backed one is enough to run a
  transform's or command's writes against.
- For `TransformContext`, `ToolContext`, `PluginContext` or `BlockCatalog`, write small fakes in your tests, or use
  `java.lang.reflect.Proxy` for the methods you don't care about. They are interfaces, so this is easy, but
  BlockDesigner's real implementations (the block catalog's families and variants, for example) are not in the API
  jars, so a fake only tests your side. A `Proxy` fake of `PluginContext` should answer the API 6 methods your
  `enable` calls: `registerSettings`, `updateSettings` and `setPanelStatus` can do nothing, and `ui()` can return null
  when the test builds no JavaFX nodes (AI Builder's `PluginEnableTest` does this). Keep code that needs no toolkit
  (migrations, formatting) out of classes that import JavaFX, so plain tests can run it.

A jar-content test is worth having (from Terrain Generator's `PluginPartsTest`): after `./gradlew jar`, open the jar
and check `blockdesigner-plugin.json` is at the root, your main class is there, and nothing from
`io/blockdesigner/core/` or `io/blockdesigner/plugin/` was bundled. The template's `ManifestTest` checks the manifest
on the test class path: its `main` class loads and implements `BlockDesignerPlugin`, and its `api` isn't newer than
the jars' `PluginApi.VERSION`.

## 2. Loading through `PluginManager` (BlockDesigner's own tests)

BlockDesigner tests its loader by loading real plugin jars, in
[`app/src/test/java/io/blockdesigner/app/plugins/PluginManagerTest.java`](https://github.com/doolecg/BlockDesigner/blob/main/app/src/test/java/io/blockdesigner/app/plugins/PluginManagerTest.java):

1. it copies the built jars of `examples/hello-plugin` and `examples/palette-tools` into a temporary plugins folder
   (picking the newest jar in each `build/libs`, since older versions pile up there);
2. creates `new PluginManager(folder, host, disabledIds)` with a small `PluginHost` implementation backed by a real
   `Scene` and `SceneEditor` and a `HashMap` world;
3. calls `loadAll()`, then checks states, what each plugin registered (`contributions()`, `exporters()`,
   `transforms()`, `WorldEdit.commands()`, `Schematics.byId(...)`), runs commands through `new WorldEdit().run("/pillar 3", ctx)`,
   runs actions with `pm.run(action)`, and checks that `setEnabled(p, false)` removes everything and is remembered;
4. `shutdown()` after each test.

`PluginManager` and `PluginHost` live in BlockDesigner's `app` module, which is **not** part of the API jars. So this
kind of test runs inside the BlockDesigner repository, not in a plugin repository. If you want your plugin covered
the same way, the way to do it is a test in a BlockDesigner checkout pointed at your jar; the example plugins show
the pattern. (The official plugin repositories don't do this; they rely on level 1 and level 3.)

## 3. In the app

1. `./gradlew jar`.
2. Drop the jar on the drop box in **Plugins › Manage plugins…**, or copy it into the plugins folder and press **Reload**.
3. Watch the plugin's row (on / failed to start / needs a newer BlockDesigner), its log in the Plugins window, and
   the Console at the bottom left, where your `ctx.log` lines appear under your plugin's name.
4. After a rebuild, Install… again or copy and Reload; there is no need to restart BlockDesigner.

Test with the oldest BlockDesigner your "needs" line promises if you can: a method from a newer API level than you
declared only fails there (see [API levels](API-Levels)).

To test [automatic updates](Automatic-Updates), install the previous version, publish the new release, and use
**Check for updates** in the Plugins window.
