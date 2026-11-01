package io.github.eslamasabry.oc.quality

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue as kAssertTrue

/** The rewrite's architecture tests (ADR §6.3, plan 03 §19.4). Infra tests: exempt from K12 catalog naming. */
class KonsistRulesTest {
    private val root = File(System.getProperty("oc.root"))

    private fun scope(vararg layers: String): KoScope =
        layers
            .map { File(root, it) }
            .filter { it.isDirectory }
            .map { Konsist.scopeFromDirectory(it.relativeTo(projectRootForKonsist()).path) }
            .reduce { a, b -> a + b }

    /** Konsist resolves directories against the Gradle root it detects (the folder holding gradlew). */
    private fun projectRootForKonsist(): File = root

    // protocol (S1-OC2 request) and src/jvmShared (S1-NET request) added by INTEGRATE-W1; android/ (copied Flutter
    // Kotlin, TWINS.md) is not scanned until its slice rewrites it.
    private val production get() = scope("core", "protocol", "engine", "ui", "feature", "androidApp", "desktopApp")
        .slice { "/src/commonMain/" in it.path || "/src/main/" in it.path || "/src/androidMain/" in it.path ||
            "/src/desktopMain/" in it.path || "/src/iosMain/" in it.path || "/src/jvmShared/" in it.path }

    @Test
    fun `K1 features arrange kit parts only`() {
        val features = scope("feature")
        kAssertTrue(features.files.isNotEmpty(), "Konsist saw no feature files; the scope is wrong (P-03-6)")
        features.files.assertTrue { file -> KitOnly.violations(file.packagee?.name, file.name, file.imports.map { it.name }).isEmpty() }
    }

    @Test
    fun `K2 presentation imports no Compose and no copy`() {
        scope("feature").files
            .filter { it.packagee?.name?.endsWith(".presentation") == true }
            .assertFalse { file ->
                file.imports.any { it.name.startsWith("androidx.compose.") || it.name.contains(".ui.words.res") }
            }
    }

    @Test
    fun `K3 no runCatching in files with suspend functions`() {
        production.files.assertFalse { Regex("""\brunCatching\s*[({]""").containsMatchIn(it.text) && "suspend " in it.text }
    }

    @Test
    fun `K4 no Dispatchers or Clock System outside core common`() {
        production.files
            .filter { "/core/common/" !in it.path }
            .assertFalse { f -> Regex("""\bDispatchers\.""").containsMatchIn(f.text) || "Clock.System" in f.text }
    }

    @Test
    fun `K5 no Log, println, printStackTrace or logging interceptors`() {
        production.files.assertFalse { f ->
            f.imports.any { it.name == "android.util.Log" } ||
                Regex("""\bprintln\(|printStackTrace\(|HttpLoggingInterceptor""").containsMatchIn(f.text)
        }
    }

    @Test
    fun `K10 platform source sets only in the ADR 7_2 modules`() {
        val allowed = setOf("core/common", "core/data", "core/net", "core/db", "ui/kit", "ui/kit-chat",
            "ui/kit-content", "ui/shell", "ui/testing")
        val offenders =
            listOf("core", "protocol", "engine", "ui", "feature").flatMap { layer ->
                File(root, layer).listFiles().orEmpty().filter { it.isDirectory }.filter { module ->
                    val rel = module.relativeTo(root).path
                    rel !in allowed && listOf("androidMain", "desktopMain", "iosMain").any { File(module, "src/$it").exists() }
                }.map { it.relativeTo(root).path }
            }
        kAssertTrue(offenders.isEmpty(), "platform source sets outside ADR §7.2: $offenders")
    }

    @Test
    fun `K11 no String format, System, Thread, java or javax in commonMain`() {
        production.files
            .filter { "/src/commonMain/" in it.path }
            .assertFalse { f ->
                f.imports.any { it.name.startsWith("java.") || it.name.startsWith("javax.") } ||
                    Regex("""String\.format|\bSystem\.|\bThread\.""").containsMatchIn(f.text)
            }
    }

    @Test
    fun `K13 RedactorApi opt-in only in core security and Redacted`() {
        production.files
            .filter { "/core/security/" !in it.path && it.name != "Redacted" }
            .assertFalse { "OptIn(RedactorApi::class)" in it.text }
    }

    @Test
    fun `K16 no GlobalScope or runBlocking`() {
        production.files.assertFalse { Regex("""\bGlobalScope\b|\brunBlocking\b|launch\(Job\(\)\)""").containsMatchIn(it.text) }
    }

    @Test
    fun `K18 material3 only in kits (adaptive also in shell)`() {
        production.files
            .filter { "/ui/kit" !in it.path }
            .assertFalse { f ->
                f.imports.any {
                    it.name.startsWith("androidx.compose.material3.") &&
                        !("/ui/shell/" in f.path && it.name.startsWith("androidx.compose.material3.adaptive."))
                }
            }
    }

    @Test
    fun `K19 a feature never imports another feature`() {
        scope("feature").files.assertFalse { f ->
            val own = f.packagee?.name?.split('.')?.take(6)?.joinToString(".") ?: return@assertFalse false
            f.imports.any { it.name.startsWith("io.github.eslamasabry.oc.feature.") && !it.name.startsWith(own) }
        }
    }
}
