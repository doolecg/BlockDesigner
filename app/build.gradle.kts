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
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED", "-Dprism.forceGPU=true")
}
