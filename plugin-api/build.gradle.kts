// The API third-party plugins compile against. Keep it small and stable: bump PluginApi.VERSION on breaking changes.
plugins {
    alias(libs.plugins.javafx)
}

// Only PluginPanel and PanelContext touch JavaFX. The app provides it at runtime, so plugins (and this jar) only
// compile against it.
javafx {
    version = libs.versions.javafx.get()
    modules = listOf("javafx.controls")
    configuration = "compileOnly"
}

dependencies {
    api(project(":core"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.named<Jar>("jar") {
    archiveBaseName = "blockdesigner-plugin-api"
}
