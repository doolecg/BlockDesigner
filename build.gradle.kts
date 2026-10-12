subprojects {
    apply(plugin = "java-library")

    group = "io.blockdesigner"
    version = "0.4.28"

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion = JavaLanguageVersion.of(26)
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release = 25
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all,-serial,-processing,-classfile", "-parameters"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // The UI tests load the full Minecraft assets; the default 512 MB is too tight for editing a real build.
        maxHeapSize = "2g"
        jvmArgs("--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow")
        testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    }
}

// Dev Run: builds and runs the working tree.
tasks.register("runDev") {
    group = "application"
    description = "Builds and runs the app from the working tree."
    dependsOn(":app:run")
}

// Main Run: exports the code committed on main to build/main-run and runs the app from that copy.
val exportMain = tasks.register<Exec>("exportMain") {
    group = "application"
    val out = layout.buildDirectory.dir("main-run").get().asFile
    doFirst {
        delete(out)
        out.mkdirs()
    }
    commandLine("cmd", "/c", "git archive main | tar -x -C \"${out.absolutePath}\"")
}

tasks.register<Exec>("runMain") {
    group = "application"
    description = "Builds and runs the code committed on main (exported to build/main-run)."
    dependsOn(exportMain)
    workingDir = layout.buildDirectory.dir("main-run").get().asFile
    commandLine("cmd", "/c", "gradlew.bat", ":app:run", "--console=plain")
    standardInput = System.`in`
}
