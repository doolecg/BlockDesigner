dependencies {
    api(project(":assets"))
    implementation(libs.anthropic)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.register("printClasspath") {
    val cp = configurations.named("runtimeClasspath")
    doLast { println(cp.get().files.joinToString(";")) }
}
