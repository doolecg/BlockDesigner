plugins {
    application
    alias(libs.plugins.javafx)
}

javafx {
    version = libs.versions.javafx.get()
    modules = listOf("javafx.controls")
}

dependencies {
    implementation(project(":render"))
    implementation(project(":ai"))
    implementation(project(":worldgen"))
    implementation(libs.jna.platform)
    implementation(libs.atlantafx)
    implementation(libs.ikonli.javafx)
    implementation(libs.ikonli.feather)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.launcher)
}

application {
    mainClass = "io.blockdesigner.app.Main"
    // JavaFX sits on the module path and LWJGL on the class path; both load native code. JOML reads memory via Unsafe.
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=javafx.graphics,ALL-UNNAMED",
        "--sun-misc-unsafe-memory-access=allow",
        "-Dprism.forceGPU=true",
    )
}
