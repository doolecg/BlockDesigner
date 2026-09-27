// The API third-party plugins compile against. Keep it small and stable: bump PluginApi.VERSION on breaking changes.
plugins {
    alias(libs.plugins.javafx)
}

// Panels and the UI kit (io.blockdesigner.plugin.ui) use JavaFX. The app provides it at runtime, so plugins (and this
// jar) only compile against it; the tests load the kit's classes, so they get it too.
javafx {
    version = libs.versions.javafx.get()
    modules = listOf("javafx.controls")
    configurations = arrayOf("compileOnly", "testImplementation")
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
