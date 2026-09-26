import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

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

// ---- Code signing ----------------------------------------------------------------------------------------------
// Windows Smart App Control and SmartScreen block unsigned programs they don't already know, and every release is a
// new file. When a certificate is configured, the packaging tasks sign the launcher (BlockDesigner.exe), the native
// DLLs that JavaFX, LWJGL and JNA unpack from their jars at runtime (they ship unsigned; the Java runtime's own DLLs
// are already signed by Eclipse / Microsoft), and the setup .exe. Without a certificate nothing is signed.
//
// Configure it outside the repo, in ~/.gradle/gradle.properties or as environment variables:
//   blockdesigner.sign.args      (env BLOCKDESIGNER_SIGN_ARGS)  signtool options, everything between "sign" and the files:
//     certificate in the Windows store (e.g. a Certum / SimplySign card):
//         /sha1 <certificate thumbprint> /fd SHA256 /tr http://time.certum.pl /td SHA256
//     a .pfx file:           /f C:\path\cert.pfx /p <password> /fd SHA256 /tr http://timestamp.digicert.com /td SHA256
//     Azure Trusted Signing: /fd SHA256 /tr http://timestamp.acs.microsoft.com /td SHA256
//                            /dlib C:\path\Azure.CodeSigning.Dlib.dll /dmdf C:\path\metadata.json
//   blockdesigner.signtool       (env BLOCKDESIGNER_SIGNTOOL)   signtool.exe to use; otherwise tools/signtool (fetched
//                                by ./gradlew :app:fetchSigntool), then the Windows SDK, then PATH.
//   blockdesigner.sign.required  true: fail the build instead of skipping when signing isn't set up (for releases).

fun signSetting(name: String, env: String): String? =
    (findProperty(name) as String?)?.takeIf { it.isNotBlank() } ?: System.getenv(env)?.takeIf { it.isNotBlank() }

/** Splits signtool options like a command line: spaces separate, double quotes keep paths with spaces together. */
fun splitArgs(s: String): List<String> {
    val out = mutableListOf<String>()
    val cur = StringBuilder()
    var quoted = false
    for (c in s) {
        when {
            c == '"' -> quoted = !quoted
            c.isWhitespace() && !quoted -> if (cur.isNotEmpty()) { out += cur.toString(); cur.clear() }
            else -> cur.append(c)
        }
    }
    if (cur.isNotEmpty()) out += cur.toString()
    return out
}

fun findSigntool(): File? {
    signSetting("blockdesigner.signtool", "BLOCKDESIGNER_SIGNTOOL")?.let { return File(it) }
    // The newest x64 signtool under a folder (the NuGet build tools and the Windows SDK both use bin/<version>/x64).
    fun newestIn(root: File): File? = root.takeIf { it.isDirectory }?.walkTopDown()
        ?.filter { it.name.equals("signtool.exe", true) && it.parentFile.name == "x64" }
        ?.maxByOrNull { it.parentFile.parentFile.name }
    return newestIn(rootProject.file("tools/signtool"))
        ?: newestIn(File(System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)", "Windows Kits/10/bin"))
        ?: System.getenv("PATH").orEmpty().split(File.pathSeparator).map { File(it, "signtool.exe") }.firstOrNull { it.isFile }
}

/** Runs a command, streaming its output into the build log; fails the build on a non-zero exit. */
fun runChecked(cmd: List<String>, what: String) {
    val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
    p.inputStream.bufferedReader().forEachLine { logger.lifecycle("  $it") }
    if (p.waitFor() != 0) throw GradleException("$what failed (exit ${p.exitValue()})")
}

/** Signs files with the configured certificate; returns false (after a warning) when signing isn't set up. */
fun signFiles(files: List<File>): Boolean {
    if (files.isEmpty()) return true
    val args = signSetting("blockdesigner.sign.args", "BLOCKDESIGNER_SIGN_ARGS")
    val required = (findProperty("blockdesigner.sign.required") as String?).toBoolean()
    val tool = findSigntool()
    if (args == null || tool == null || !tool.isFile) {
        val why = if (args == null) "no certificate is configured (blockdesigner.sign.args)" else "signtool.exe wasn't found (run :app:fetchSigntool)"
        if (required) throw GradleException("Can't sign: $why.")
        logger.warn("Not signing ${files.size} file(s): $why. Windows Smart App Control may block this build.")
        return false
    }
    logger.lifecycle("Signing ${files.size} file(s) with ${tool.absolutePath}")
    // A batch per call keeps a hardware token or cloud signer to a handful of round trips.
    files.chunked(40).forEach { batch ->
        runChecked(listOf(tool.absolutePath, "sign") + splitArgs(args) + batch.map { it.absolutePath }, "signtool sign")
    }
    return true
}

/** Whether a PE file already carries a valid Authenticode signature (the runtime's DLLs, Microsoft's CRT). */
fun isSigned(tool: File, f: File): Boolean =
    ProcessBuilder(tool.absolutePath, "verify", "/pa", "/q", f.absolutePath).redirectErrorStream(true).start()
        .also { it.inputStream.readAllBytes() }.waitFor() == 0

/**
 * Signs an app image in place: the launcher, and every unsigned DLL inside the jars in app/ (swapped for its signed
 * copy, so the libraries unpack signed natives at runtime).
 */
fun signAppImage(image: File) {
    val launcher = File(image, "BlockDesigner.exe")
    val tool = findSigntool()
    val configured = signSetting("blockdesigner.sign.args", "BLOCKDESIGNER_SIGN_ARGS") != null && tool != null && tool.isFile
    if (!configured) {
        signFiles(listOf(launcher))
        return
    }
    val work = layout.buildDirectory.dir("signing/${image.name}").get().asFile.also { it.deleteRecursively(); it.mkdirs() }
    // jar -> (entry -> extracted file) for every unsigned DLL
    val natives = mutableMapOf<File, MutableMap<String, File>>()
    File(image, "app").listFiles { f -> f.name.endsWith(".jar") }.orEmpty().forEach { jar ->
        ZipFile(jar).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".dll", true) }.forEach { e ->
                val out = File(work, "${jar.nameWithoutExtension}/${e.name}").also { it.parentFile.mkdirs() }
                zip.getInputStream(e).use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                if (!isSigned(tool!!, out)) natives.getOrPut(jar) { mutableMapOf() }[e.name] = out
            }
        }
    }
    signFiles(listOf(launcher) + natives.values.flatMap { it.values })
    natives.forEach { (jar, entries) ->
        FileSystems.newFileSystem(jar.toPath()).use { fs ->
            entries.forEach { (name, file) ->
                Files.copy(file.toPath(), fs.getPath(name), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
    logger.lifecycle("Signed ${image.name}: the launcher and ${natives.values.sumOf { it.size }} native DLL(s) in ${natives.size} jar(s)")
}

tasks.register("fetchSigntool") {
    group = "distribution"
    description = "Downloads signtool.exe (Microsoft.Windows.SDK.BuildTools from nuget.org) into tools/signtool."
    doLast {
        val index = URI("https://api.nuget.org/v3-flatcontainer/microsoft.windows.sdk.buildtools/index.json").toURL().readText()
        val version = Regex("\"([0-9.]+)\"").findAll(index).map { it.groupValues[1] }.last()
        val dest = rootProject.file("tools/signtool").also { it.deleteRecursively(); it.mkdirs() }
        val pkg = URI("https://api.nuget.org/v3-flatcontainer/microsoft.windows.sdk.buildtools/$version/microsoft.windows.sdk.buildtools.$version.nupkg").toURL()
        // A .nupkg is a zip: keep only the x64 signing tools.
        ZipInputStream(pkg.openStream()).use { zin ->
            generateSequence { zin.nextEntry }.filter { !it.isDirectory && it.name.contains("/x64/") && it.name.startsWith("bin/") }.forEach { e ->
                val out = File(dest, e.name).also { it.parentFile.mkdirs() }
                out.outputStream().use { zin.copyTo(it) }
            }
        }
        logger.lifecycle("signtool $version: ${findSigntool()}")
    }
}

val portableImage = tasks.register<Exec>("portableImage") {
    group = "distribution"
    description = "Builds the portable app folder dist/BlockDesigner."
    dependsOn(tasks.named("installDist"))
    doFirst { delete(distDir.dir("BlockDesigner")) }
    // $APPDIR is expanded by the launcher: settings go to <portable folder>/data.
    commandLine(appImageArgs(distDir.asFile.absolutePath, listOf("-Dblockdesigner.dataDir=\$APPDIR/../data")))
    doLast { signAppImage(distDir.dir("BlockDesigner").asFile) }
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
    doLast { signAppImage(imagesDir.get().dir("BlockDesigner").asFile) }
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
    // The app inside is signed by installerImage; this signs the setup itself (what SmartScreen checks first).
    doLast { signFiles(listOf(distDir.file("BlockDesigner-$packageVersion.exe").asFile)) }
}

// PluginManagerTest loads the example plugin's jar.
tasks.named<Test>("test") {
    dependsOn(":examples:hello-plugin:jar")
    systemProperty("blockdesigner.examplePluginDir", rootProject.file("examples/hello-plugin/build/libs").absolutePath)
}
