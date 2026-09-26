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
    implementation(project(":worldgen"))
    implementation(project(":plugin-api"))
    implementation(libs.jna.platform)
    implementation(libs.atlantafx)
    implementation(libs.ikonli.javafx)
    implementation(libs.ikonli.feather)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.launcher)
}

// The version shown to users and checked against GitHub releases by the updater (Updater.currentVersion). Set it with
// "version" in the root build.gradle.kts.
val packageVersion = project.version.toString().removeSuffix("-SNAPSHOT")

val versionResource = tasks.register("versionResource") {
    val out = layout.buildDirectory.dir("generated/version")
    inputs.property("version", packageVersion)
    outputs.dir(out)
    doLast {
        val f = out.get().file("io/blockdesigner/app/version.properties").asFile
        f.parentFile.mkdirs()
        f.writeText("version=$packageVersion\n")
    }
}
sourceSets.main { resources.srcDir(versionResource) }

application {
    mainClass = "io.blockdesigner.app.Main"
    // JavaFX sits on the module path and LWJGL on the class path; both load native code. JOML reads memory via Unsafe.
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=javafx.graphics,ALL-UNNAMED",
        "--sun-misc-unsafe-memory-access=allow",
        "-Dprism.forceGPU=true",
    )
}

// ---- Windows packaging (jpackage) ------------------------------------------------------------------------------
// ./gradlew :app:portable   -> dist/BlockDesigner/BlockDesigner.exe + dist/BlockDesigner-<v>-portable.zip (no install;
//                              settings are kept in a "data" folder beside the exe)
// ./gradlew :app:installer  -> dist/BlockDesigner-<v>.exe setup (Start menu + desktop shortcut, .bdproj association).
//                              Needs the WiX Toolset: unzip WiX 3.14 binaries into tools/wix3, or have WiX on PATH.

val jdkBin = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(26) }
    .map { it.metadata.installationPath.dir("bin") }
val jpackageExe = jdkBin.map { it.file("jpackage.exe").asFile.absolutePath }
val distDir = rootProject.layout.projectDirectory.dir("dist")
val imagesDir = layout.buildDirectory.dir("jpackage")
val runtimeModules = listOf(
    "java.base", "java.desktop", "java.logging", "java.management", "java.naming", "java.net.http", "java.prefs",
    "java.scripting", "java.sql", "java.xml", "java.xml.crypto", "jdk.unsupported", "jdk.unsupported.desktop",
    "jdk.zipfs", "jdk.crypto.ec", "jdk.crypto.mscapi", "jdk.charsets", "jdk.localedata", "jdk.accessibility",
).joinToString(",")
val iconFile = rootProject.file("packaging/windows/blockdesigner.ico")

fun appImageArgs(dest: String, extraJavaOptions: List<String>): List<String> {
    val lib = layout.buildDirectory.dir("install/app/lib").get().asFile.absolutePath
    val opts = listOf("--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow", "-Dprism.forceGPU=true") + extraJavaOptions
    return listOf(
        jpackageExe.get(), "--type", "app-image", "--dest", dest,
        "--name", "BlockDesigner", "--app-version", packageVersion, "--vendor", "BlockDesigner",
        "--description", "Minecraft structure designer",
        "--input", lib, "--main-jar", "app-${project.version}.jar", "--main-class", "io.blockdesigner.app.Main",
        "--icon", iconFile.absolutePath, "--add-modules", runtimeModules,
        "--jlink-options", "--strip-debug --no-header-files --no-man-pages",
    ) + opts.flatMap { listOf("--java-options", it) }
}

val portableImage = tasks.register<Exec>("portableImage") {
    group = "distribution"
    description = "Builds the portable app folder dist/BlockDesigner."
    dependsOn(tasks.named("installDist"))
    doFirst { delete(distDir.dir("BlockDesigner")) }
    // $APPDIR is expanded by the launcher: settings go to <portable folder>/data.
    commandLine(appImageArgs(distDir.asFile.absolutePath, listOf("-Dblockdesigner.dataDir=\$APPDIR/../data")))
}

tasks.register<Zip>("portable") {
    group = "distribution"
    description = "Zips the portable build."
    dependsOn(portableImage)
    from(distDir.dir("BlockDesigner")) { into("BlockDesigner") }
    archiveFileName = "BlockDesigner-$packageVersion-portable.zip"
    destinationDirectory = distDir
}

val installerImage = tasks.register<Exec>("installerImage") {
    group = "distribution"
    dependsOn(tasks.named("installDist"))
    doFirst { delete(imagesDir) }
    commandLine(appImageArgs(imagesDir.get().asFile.absolutePath, emptyList()))
}

tasks.register<Exec>("installer") {
    group = "distribution"
    description = "Builds the Windows setup .exe in dist/ (needs WiX)."
    dependsOn(installerImage)
    workingDir = rootProject.projectDir
    // Use a local WiX 3 (tools/wix3, git-ignored) when present; otherwise WiX must be on PATH.
    val localWix = rootProject.file("tools/wix3")
    if (localWix.isDirectory) environment("PATH", localWix.absolutePath + File.pathSeparator + System.getenv("PATH"))
    commandLine(
        jpackageExe.get(), "--type", "exe", "--dest", distDir.asFile.absolutePath,
        "--app-image", imagesDir.get().dir("BlockDesigner").asFile.absolutePath,
        "--name", "BlockDesigner", "--app-version", packageVersion, "--vendor", "BlockDesigner",
        "--icon", iconFile.absolutePath,
        "--file-associations", rootProject.file("packaging/windows/bdproj.properties").absolutePath,
        "--win-menu", "--win-menu-group", "BlockDesigner", "--win-shortcut", "--win-shortcut-prompt",
        "--win-dir-chooser", "--win-per-user-install",
        // Fixed so newer installers upgrade older ones in place.
        "--win-upgrade-uuid", "3f0f6a4e-5b1c-4f3e-9d7a-2b8e6c1d4a90",
    )
}

// PluginManagerTest loads the example plugin's jar.
tasks.named<Test>("test") {
    dependsOn(":examples:hello-plugin:jar")
    systemProperty("blockdesigner.examplePluginDir", rootProject.file("examples/hello-plugin/build/libs").absolutePath)
}
