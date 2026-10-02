# Setting up a plugin project

A plugin is an ordinary Gradle (or any other) Java project that produces one jar with a
[`blockdesigner-plugin.json`](The-Manifest) at its root. This page describes the layout every official plugin
uses; copying one of them (the BlockCompanion Plugin, formerly Resource Tracker, is the smallest) is the quickest start.

## What you need

- **Windows** (BlockDesigner is Windows only) and **JDK 26**. The official builds use Temurin 26; the API jars are
  Java 25 class files (`options.release = 25`), so the compiler must be JDK 25 or newer and your `release` 25 or
  lower. The Gradle toolchain in the template asks for 26.
- The two **BlockDesigner API jars** for the version you target:
  - `blockdesigner-plugin-api-<version>.jar`: the `io.blockdesigner.plugin` API;
  - `core-<version>.jar`: the `io.blockdesigner.core` classes the API uses (block states, structures, layers, NBT,
    schematic formats, `WorldEdit.World`). The official plugins keep it as `blockdesigner-core-<version>.jar`.

  Build them from a BlockDesigner checkout with `./gradlew :plugin-api:jar :core:jar`; they land in
  `plugin-api/build/libs/` and `core/build/libs/`. They are not published to a Maven repository, so plugins
  **vendor** them: commit them in the plugin's `libs/` folder.

## Layout

```
BlockDesigner-<Name>/
  build.gradle.kts            version, compileOnly API jars, the @VERSION@ filter
  settings.gradle.kts         rootProject.name = "<plugin id>"
  gradle.properties           org.gradle.java.home = your JDK 26
  gradle/libs.versions.toml   junit, assertj, jackson, javafx versions
  gradlew, gradlew.bat, gradle/wrapper/
  libs/                       blockdesigner-plugin-api-<v>.jar, blockdesigner-core-<v>.jar
  src/main/java/...           the plugin
  src/main/resources/blockdesigner-plugin.json
  src/test/java/...           tests
  README.md, RELEASE_NOTES.md, LICENSE
  docs/images/logo.png
```

## `build.gradle.kts`

This is the official plugins' build file (the BlockCompanion Plugin's, with the names made generic):

```kotlin
plugins {
    `java-library`
    alias(libs.plugins.javafx)          // org.openjfx.javafxplugin 0.1.0, only needed for JavaFX (panels, menus, icons)
}

group = "io.blockdesigner.plugins"
version = "1.0.0"                       // keep at column 0: release scripts read it

repositories { mavenCentral() }

java { toolchain.languageVersion = JavaLanguageVersion.of(26) }

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial,-processing,-classfile", "-parameters"))
}

javafx {
    version = libs.versions.javafx.get()   // 26.0.2, what BlockDesigner ships
    modules = listOf("javafx.controls")
    configuration = "compileOnly"
}

// The BlockDesigner API this plugin targets (from BlockDesigner 0.4.22).
val blockDesigner = files("libs/blockdesigner-plugin-api-0.4.22.jar", "libs/blockdesigner-core-0.4.22.jar")

dependencies {
    compileOnly(blockDesigner)
    compileOnly(libs.jackson.databind)     // only if you use Jackson; BlockDesigner provides 2.20.0

    testImplementation(blockDesigner)
    testImplementation(libs.jackson.databind)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test { useJUnitPlatform() }

// The manifest's version is this project's.
tasks.named<ProcessResources>("processResources") {
    val v = project.version.toString()
    inputs.property("version", v)
    filesMatching("blockdesigner-plugin.json") { filter { it.replace("@VERSION@", v) } }
}
```

Why it looks like this:

- **`compileOnly`, never `implementation`, for everything BlockDesigner already has**: the API jars, JavaFX and
  Jackson. The app puts them on the class path the plugin's class loader delegates to (see
  [How plugins are loaded](How-Plugins-Are-Loaded)). A bundled copy would never be used (the parent loader wins),
  but it makes the jar bigger and can confuse you while debugging. Tests need them at runtime, hence the matching
  `testImplementation`.
- **Your own libraries can be bundled** into the jar (for example with a fat-jar or shadow setup). Pick versions
  that don't clash with what BlockDesigner ships, because BlockDesigner's copy is found first when both exist.
- **`@VERSION@`** in the manifest is replaced by the Gradle version, so the tag, the jar name and the manifest version
  can't drift apart. That matters for [automatic updates](Automatic-Updates).
- **`rootProject.name` = the plugin id** in `settings.gradle.kts`, so the jar is `<id>-<version>.jar`, the name the
  updater looks for first.
- **`gradle.properties`** points `org.gradle.java.home` at a JDK 26. The official repos commit the maintainer's own
  path (`C:/Users/…/.jdks/temurin-26.0.2.1`); change it to yours or remove it if Gradle finds a JDK 26 by itself.

## The entry point

```java
package io.blockdesigner.example;

import io.blockdesigner.plugin.BlockDesignerPlugin;
import io.blockdesigner.plugin.PluginAction;
import io.blockdesigner.plugin.PluginContext;

public final class ExamplePlugin implements BlockDesignerPlugin {
    @Override
    public void enable(PluginContext ctx) {
        ctx.registerAction(new PluginAction("Say hello", "Shows that the plugin runs", () -> ctx.toast("Hello")));
    }
}
```

The class needs a public no-argument constructor. See [Plugin lifecycle and context](Plugin-Lifecycle-And-Context).

## Build and try it

```
./gradlew jar test          # build/libs/<id>-<version>.jar
```

Install it by dropping the jar on the drop box in **Plugins (puzzle icon) › Manage plugins…** (or click the box to pick it), or copy it into the plugins folder and press
**Reload** there. Check the jar: `blockdesigner-plugin.json` must be at its root, and it must not contain
`io/blockdesigner/core/` or `io/blockdesigner/plugin/` classes.

## Two modules

When part of the plugin is plain Java that shouldn't depend on BlockDesigner (a generator, a file format), split it
as Terrain Generator does: a `runtime` module with no BlockDesigner dependency, and a `plugin` module that depends on
it and builds its classes into the plugin jar. Keep the version in the root `build.gradle.kts`, share `libs/` with
`rootProject.files(...)`, set `base.archivesName = "<id>"` in the plugin module, and register a root `jar` task that
copies the plugin jar into the root `build/libs/`, where release tooling looks for it. See
[Terrain Generator's build files](https://github.com/doolecg/BlockDesigner-TerrainGenerator).

## Moving to newer API jars

Replace the two jars in `libs/` with the ones from the newer BlockDesigner, update their file names in
`build.gradle.kts`, rebuild and test. Only raise `"api"` in the manifest if you start using something from a newer
level ([API levels](API-Levels)).
