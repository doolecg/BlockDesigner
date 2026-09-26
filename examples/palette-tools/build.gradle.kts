// A sample plugin for plugin API 2: transforms (weathering, palette swap, gradient) and a panel. Build it with:
//   ./gradlew :examples:palette-tools:jar
// then copy build/libs/palette-tools-*.jar into BlockDesigner's plugins folder (Plugins > Manage plugins > Open folder).
//
// Outside this repository, depend on the API jars instead:  compileOnly(files("libs/blockdesigner-plugin-api.jar", "libs/core.jar"))
// and JavaFX (for the panel) as below. The app provides all of them at runtime, so never bundle them into your plugin.
plugins {
    alias(libs.plugins.javafx)
}

javafx {
    version = libs.versions.javafx.get()
    modules = listOf("javafx.controls")
    configuration = "compileOnly"
}

dependencies {
    compileOnly(project(":plugin-api"))
}
