val lwjglNatives = "natives-windows"

dependencies {
    api(project(":assets"))
    api(platform(libs.lwjgl.bom))
    api(libs.lwjgl)
    api(libs.lwjgl.glfw)
    api(libs.lwjgl.opengl)
    api(libs.joml)
    runtimeOnly(variantOf(libs.lwjgl) { classifier(lwjglNatives) })
    runtimeOnly(variantOf(libs.lwjgl.glfw) { classifier(lwjglNatives) })
    runtimeOnly(variantOf(libs.lwjgl.opengl) { classifier(lwjglNatives) })

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.launcher)
}
