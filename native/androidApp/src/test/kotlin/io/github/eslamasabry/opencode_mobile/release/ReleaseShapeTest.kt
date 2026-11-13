package io.github.eslamasabry.opencode_mobile.release

import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Release readiness of the switch build (epic E01; no catalog rows yet, so the story ids name the tests):
 * E01-S01 identity and signing, E01-S08 R8 + keep rules + ABI splits, E01-S03 the draft-only release workflow, and the
 * release tool self-tests (identity gate, scripts/native-release.sh, owner preflight). Read from the checked-in build
 * files, like ReleaseScriptContractTest; the built APK is checked by the Gradle tasks and the identity gate.
 */
class ReleaseShapeTest {
    private val build by lazy { RepoFiles.text("androidApp/build.gradle.kts") }
    private val rules by lazy { RepoFiles.text("androidApp/release-rules.pro") }
    private val workflow by lazy { RepoFiles.rootText(".github/workflows/native-release.yml") }

    /** The `-keep class <name>` lines of release-rules.pro, without comments. */
    private val keptClasses by lazy {
        rules.lines().map { it.substringBefore('#').trim() }
            .mapNotNull { Regex("""^-keep class ([\w.$]+)""").find(it)?.groupValues?.get(1) }.toSet()
    }

    private fun block(text: String, start: String): String {
        val at = text.indexOf(start)
        check(at >= 0) { "no '$start'" }
        var depth = 0
        for (i in text.indexOf('{', at) until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return text.substring(at, i + 1)
            }
        }
        error("unbalanced block at '$start'")
    }

    // ---- E01-S01 ----------------------------------------------------------------------------------------------------

    @Test
    fun `E01-S01 version properties hold the first native release number and a plain 3_x name`() {
        val props = Properties().apply { RepoFiles.native("version.properties").inputStream().use(::load) }
        assertTrue(props.getProperty("versionCode").toInt() >= 10_010, "the first delivered native build is 10010+")
        assertTrue(Regex("""[3-9]\.\d+\.\d+""").matches(props.getProperty("versionName")), props.getProperty("versionName"))
    }

    @Test
    fun `E01-S01 the switch flavour keeps the Flutter package and the build checks its identity`() {
        val switch = block(build, "create(\"switch\")")
        assertFalse(switch.contains("applicationIdSuffix"), "switch must install over io.github.eslamasabry.opencode_mobile")
        assertTrue(build.contains("const val SWITCH_PACKAGE = \"io.github.eslamasabry.opencode_mobile\""))
        assertTrue(build.contains("const val FIRST_RELEASE_CODE = 10010"))
        assertTrue(build.contains("tasks.register<VerifyReleaseIdentity>(\"verifySwitchReleaseIdentity\")"))
        assertTrue(build.contains("every split needs the same one"), "all ABI splits must share the versionCode")
    }

    @Test
    fun `E01-S01 release signing reads the lineage key file and keys, and is skipped with a message without it`() {
        // The lookup lives in build-logic (ReleaseKey; its own tests cover the three places with fake files).
        val key = RepoFiles.text("build-logic/convention/src/main/kotlin/oc/buildlogic/ReleaseKey.kt")
        for (name in listOf("storeFile", "storePassword", "keyAlias", "keyPassword")) {
            assertTrue(key.contains("props.getProperty(\"$name\")"), "the native build reads $name")
        }
        assertTrue(key.contains("\"native/key.properties\"") && key.contains("\"android/key.properties\""))
        assertTrue(build.contains("oc.buildlogic.ReleaseKey.locate(named, rootDir.parentFile)"))
        assertTrue(build.contains("OC_KEY_PROPERTIES"))
        assertTrue(build.contains("Release signing skipped:"), "a clear message when no key is configured")
        // The debug-key proof build is opt-in only and the lineage check stays for every real signer.
        assertTrue(build.contains("providers.gradleProperty(\"ocReleaseDebugSigned\").orNull == \"true\""))
        assertTrue(build.contains("1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C"))
        assertTrue(build.contains("842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C"))
        assertFalse(build.contains("2D010C2103CB2F78ABAACA690EAD4D45F8003A6C0A02082CD2A2AE62FD18D0EC"), "Legacy CI never signs native")
    }

    // ---- E01-S08 ----------------------------------------------------------------------------------------------------

    @Test
    fun `E01-S08 the release build type runs R8 with resource shrinking and the release rules`() {
        val release = block(build, "getByName(\"release\")")
        assertTrue(release.contains("isMinifyEnabled = true"))
        assertTrue(release.contains("isShrinkResources = true"))
        assertTrue(release.contains("getDefaultProguardFile(\"proguard-android-optimize.txt\"), \"release-rules.pro\""))
        assertFalse(rules.lines().any { it.trim().startsWith("-dontobfuscate") }, "obfuscation stays on in the release")
    }

    @Test
    fun `E01-S08 ABI splits ship arm64-v8a, x86_64 and a universal APK`() {
        val abi = block(block(build, "splits {"), "abi {")
        assertTrue(abi.contains("reset()"))
        assertTrue(abi.contains("include(\"arm64-v8a\", \"x86_64\")"))
        assertTrue(abi.contains("isUniversalApk = true"))
        // The universal APK is 64-bit only: dependencies' 32-bit copies are dropped from release packaging.
        assertTrue(build.contains("variant.packaging.jniLibs.excludes.addAll(\"lib/armeabi-v7a/**\", \"lib/x86/**\")"))
        // Each split APK is checked for its own ABI's native libraries; the universal one for both.
        assertTrue(build.contains("filter { \"-\$it-\" in apk.name }"))
    }

    @Test
    fun `E01-S08 every class the component freeze names keeps its name under R8`() {
        val freeze = RepoFiles.native("quality/config/component-freeze/flutter-2.1.4.tsv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
        val classes =
            freeze.mapNotNull { (kind, name) ->
                when (kind) {
                    "component" -> name.substringAfter(':')
                    "widget", "tile", "worker" -> name
                    else -> null
                }
            }
        assertTrue(classes.size >= 25, "the freeze list lost its classes: $classes")
        val missing = classes.filter { it !in keptClasses }
        assertEquals(emptyList(), missing, "release-rules.pro must -keep every frozen class")
        // Workers keep their names whatever their package: WorkManager stores them with queued work.
        assertTrue(rules.contains("-keep class * extends androidx.work.ListenableWorker"))
        assertTrue(rules.contains("-keep class * extends android.appwidget.AppWidgetProvider"))
    }

    @Test
    fun `E01-S08 reflected and JNI-bound code, serialization and the Metro graph have rules`() {
        assertTrue(rules.contains("-keep class com.k2fsa.sherpa.onnx.** { *; }"), "sherpa-onnx JNI reads its config classes")
        assertTrue(rules.contains("-keepclassmembers class io.github.eslamasabry.oc.android.voice.speech.KokoroSink"))
        assertTrue(rules.contains("java.lang.Integer invoke(float[]);"))
        assertTrue(rules.contains("-keep class com.termux.terminal.JNI"))
        assertTrue(rules.contains("-keepclassmembers @kotlinx.serialization.Serializable class **"))
        assertTrue(rules.contains("kotlinx.serialization.KSerializer serializer(...);"))
        assertTrue(rules.contains("-keepnames @dev.zacsweers.metro.DependencyGraph class *"))
        // KokoroSink's own comment names this rule; the class must still exist where the rule points.
        assertTrue(
            RepoFiles.native("android/voice/src/main/kotlin/io/github/eslamasabry/oc/android/voice/speech/KokoroSink.kt").isFile
        )
    }

    // ---- E01-S03 ----------------------------------------------------------------------------------------------------

    @Test
    fun `E01-S03 the native release workflow runs only by hand and only makes a draft that is not latest`() {
        val on = workflow.substringAfter("\non:\n").substringBefore("\npermissions:")
        assertTrue(on.contains("workflow_dispatch:"))
        for (trigger in listOf("push:", "pull_request", "schedule:", "release:", "workflow_run:")) {
            assertFalse(on.contains(trigger), "the native release must never start on $trigger")
        }
        assertTrue(workflow.contains("--draft --latest=false"))
        assertFalse(Regex("""--draft=false|--latest(\s|$)|make_latest:\s*true""").containsMatchIn(workflow))
        assertTrue(workflow.contains("Refusing to touch a published release"))
        assertTrue(workflow.contains("native/tool/release/identity_gate.sh --expect-signer \"\$PUBLIC_CI_CERT_SHA256\""))
        assertTrue(workflow.contains("PUBLIC_CI_CERT_SHA256: 842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C"))
        for (abi in listOf("arm64-v8a", "x86_64", "universal")) assertTrue(workflow.contains(abi))
        assertTrue(workflow.contains("SHA256SUMS"))
        assertTrue(workflow.contains("if: always()") && workflow.contains("rm -rf \"\${{ runner.temp }}/native-key.properties\""))
    }

    // ---- release tools ----------------------------------------------------------------------------------------------

    @Test
    fun `E01-S01 E01-S05 E00-S16 the release tool self-tests pass`() {
        val script = File(RepoFiles.native, "tool/release/test/run_tests.sh")
        val process = ProcessBuilder("bash", script.path).directory(RepoFiles.root).redirectErrorStream(true).start()
        val output = StringBuilder()
        val reader = Thread { process.inputStream.bufferedReader().forEachLine { output.appendLine(it) } }.apply { start() }
        val finished = process.waitFor(3, TimeUnit.MINUTES)
        if (!finished) process.destroyForcibly()
        reader.join(5_000)
        assertTrue(finished, "the self-tests did not finish")
        assertEquals(0, process.exitValue(), output.takeLast(4_000).toString())
        assertTrue(output.contains("PASS: release tools contract"), output.takeLast(2_000).toString())
    }

    @Test
    fun `E01-S05 the release script is dry-run by default and only --go tags or pushes`() {
        val release = RepoFiles.rootText("scripts/native-release.sh")
        assertTrue(release.contains("GO=false"), "the default never commits, tags or pushes")
        assertTrue(release.contains("readonly RELEASE_BRANCH=\"master\""))
        assertTrue(release.contains("git status --porcelain=v1 --untracked-files=all"))
        val beforeGo = release.substringBefore("if \$GO; then")
        assertFalse(Regex("""git (tag|push|commit)""").containsMatchIn(beforeGo), "nothing is written before the --go branch")
        assertTrue(release.contains("allocate --kind release"), "the number comes from the allocator (+10 steps)")
        assertTrue(release.contains("identity_gate.sh"))
    }
}
