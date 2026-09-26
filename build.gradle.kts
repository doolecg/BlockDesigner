subprojects {
    apply(plugin = "java-library")

    group = "io.blockdesigner"
    version = "0.4.15"

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
