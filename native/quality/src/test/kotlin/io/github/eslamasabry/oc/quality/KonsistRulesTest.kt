package io.github.eslamasabry.oc.quality

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue as kAssertTrue

/**
 * The rewrite's architecture tests (ADR §6.3, plan 03 §19.4). Infra tests: exempt from K12 catalog
 * naming.
 */
class KonsistRulesTest {
    private val root = File(System.getProperty("oc.root"))

    private fun scope(vararg layers: String): KoScope =
        layers
            .map { File(root, it) }
            .filter { it.isDirectory }
            .map { Konsist.scopeFromDirectory(it.relativeTo(projectRootForKonsist()).path) }
            .reduce { a, b -> a + b }

    /**
     * Konsist resolves directories against the Gradle root it detects (the folder holding gradlew).
     */
    private fun projectRootForKonsist(): File = root

    /**
     * A rule whose debt is written down: `quality/config/rule-baseline/<rule>.txt` lists the files that broke it when
     * the suite ran again on 2026-11-10 after weeks of not starting. A file not on the list fails (no new debt), and
     * a listed file that no longer breaks the rule fails too (take it off: the list only shrinks).
     */
    private fun ratchet(rule: String, breaking: Collection<File>) {
        val now = breaking.map { it.relativeTo(root).invariantSeparatorsPath }.toSortedSet()
        val file = File(root, "quality/config/rule-baseline/$rule.txt")
        if (System.getProperty("oc.ruleBaseline") == "write") {
            file.parentFile.mkdirs()
            file.writeText(
                "# $rule: files that broke this rule on the day the list was written. Fix one, then delete its line.\n" +
                    "# Never add a line by hand; a new file must follow the rule.\n" + now.joinToString("") { "$it\n" }
            )
            return
        }
        val listed = file.takeIf { it.isFile }?.readLines().orEmpty().filter { it.isNotBlank() && !it.startsWith("#") }.toSet()
        kotlin.test.assertEquals(emptyList(), (now - listed).toList(), "$rule: new files break the rule")
        kotlin.test.assertEquals(emptyList(), (listed - now).sorted(), "$rule: fixed; delete these lines from ${file.name}")
    }

    // protocol (S1-OC2 request) and src/jvmShared (S1-NET request) added by INTEGRATE-W1; android/
    // (copied Flutter
    // Kotlin, TWINS.md) is not scanned until its slice rewrites it.
    private val production
        get() =
            scope("core", "protocol", "engine", "ui", "feature", "androidApp", "desktopApp").slice {
                "/src/commonMain/" in it.path ||
                    "/src/main/" in it.path ||
                    "/src/androidMain/" in it.path ||
                    "/src/desktopMain/" in it.path ||
                    "/src/iosMain/" in it.path ||
                    "/src/jvmShared/" in it.path
            }

    @Test
    fun `K1 features arrange kit parts only`() {
        // Test harnesses may construct framework nodes; every production feature stays kit-only.
        val features = production.slice { "/feature/" in it.path }
        kAssertTrue(
            features.files.isNotEmpty(),
            "Konsist saw no feature files; the scope is wrong (P-03-6)",
        )
        ratchet(
            "K1-kit-only",
            features.files
                .filter { file -> KitOnly.violations(file.packagee?.name, file.name, file.imports.map { it.name }).isNotEmpty() }
                .map { File(it.path) },
        )
    }

    @Test
    fun `K2 presentation imports no Compose and no copy`() {
        scope("feature")
            .files
            .filter { it.packagee?.name?.endsWith(".presentation") == true }
            .assertFalse { file ->
                file.imports.any {
                    it.name.startsWith("androidx.compose.") || it.name.contains(".ui.words.res")
                }
            }
    }

    @Test
    fun `K3 no runCatching in files with suspend functions`() {
        ratchet(
            "K3-runCatching",
            production.files
                .filter { Regex("""\brunCatching\s*[({]""").containsMatchIn(it.text) && "suspend " in it.text }
                .map { File(it.path) },
        )
    }

    @Test
    fun `main lazy lists give every item a contentType`() {
        val lists =
            listOf(
                "feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/ChatsScreen.kt",
                "feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/ChatsAllScreen.kt",
                "feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/ChatsInboxScreen.kt",
                "feature/files/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/files/ui/FolderScreen.kt",
                "feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/ui/ChatTranscript.kt",
            )
        val call = Regex("""(?<![\w.])(?:items|itemsIndexed)\(\w|(?<![\w.])item\((?:key = |")""")
        val missing =
            lists.flatMap { path ->
                File(root, path).readLines().withIndex().filter { (_, line) ->
                    call.containsMatchIn(line) && "contentType" !in line
                }.map { "$path:${it.index + 1}" }
            }
        kAssertTrue(missing.isEmpty(), "lazy list items without contentType: $missing")
    }

    @Test
    fun `K4 no Dispatchers or Clock System outside core common`() {
        ratchet(
            "K4-dispatchers-clock",
            production.files
                .filter { "/core/common/" !in it.path }
                .filter { f -> Regex("""\bDispatchers\.""").containsMatchIn(f.text) || "Clock.System" in f.text }
                .map { File(it.path) },
        )
    }

    @Test
    fun `K5 no Log, println, printStackTrace or logging interceptors`() {
        // Reviewed exceptions (path → reason); the list only shrinks.
        val reviewed =
            mapOf(
                "/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/DiagnosticsAttentionWiring.kt" to
                    "the uncaught-crash line: this device's logcat is the one place the crash message is kept (834d840fbd)",
                "/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/AppLogSinks.kt" to
                    "the logcat sink of the app's own log port (4358d7e9fe, 06ebde6313): only ever a delegate of the " +
                    "redacting writer, so every line is already redacted; debug builds, or a tag a tester switched on",
                "/ui/gate/src/desktopMain/kotlin/io/github/eslamasabry/oc/ui/gate/AlignGuard.kt" to
                    "test tooling, never in the app: tells the developer a baselined rule can shrink",
                "/ui/gate/src/desktopMain/kotlin/io/github/eslamasabry/oc/ui/gate/LayoutGuard.kt" to
                    "test tooling, never in the app: tells the developer a baselined rule can shrink",
            )
        val raw = Regex("""\bprintln\(|printStackTrace\(|HttpLoggingInterceptor""")
        fun logsRaw(f: com.lemonappdev.konsist.api.declaration.KoFileDeclaration) =
            f.imports.any { it.name == "android.util.Log" } || raw.containsMatchIn(f.text)
        production.files.assertFalse { f -> logsRaw(f) && reviewed.keys.none { f.path.endsWith(it) } }
        val stale = reviewed.keys.filter { key -> production.files.none { f -> f.path.endsWith(key) && logsRaw(f) } }
        kotlin.test.assertEquals(emptyList(), stale, "a reviewed file no longer logs raw: remove it from the list")
    }

    @Test
    fun `K10 platform source sets only in the ADR 7_2 modules`() {
        val allowed =
            setOf(
                "core/common",
                "core/data",
                "core/net",
                "core/db",
                "ui/kit",
                "ui/kit-chat",
                "ui/kit-content",
                "ui/shell",
                "ui/testing",
                "ui/gate",
            )
        val offenders =
            listOf("core", "protocol", "engine", "ui", "feature").flatMap { layer ->
                File(root, layer)
                    .listFiles()
                    .orEmpty()
                    .filter { it.isDirectory }
                    .filter { module ->
                        val rel = module.relativeTo(root).path
                        rel !in allowed &&
                            listOf("androidMain", "desktopMain", "iosMain").any {
                                File(module, "src/$it").exists()
                            }
                    }
                    .map { it.relativeTo(root).path }
            }
        kAssertTrue(offenders.isEmpty(), "platform source sets outside ADR §7.2: $offenders")
    }

    @Test
    fun `K11 no String format, System, Thread, java or javax in commonMain`() {
        ratchet(
            "K11-jvm-in-common",
            production.files
                .filter { "/src/commonMain/" in it.path }
                .filter { f ->
                    f.imports.any { it.name.startsWith("java.") || it.name.startsWith("javax.") } ||
                        Regex("""String\.format|\bSystem\.|\bThread\.""").containsMatchIn(f.text)
                }
                .map { File(it.path) },
        )
    }

    @Test
    fun `K13 RedactorApi opt-in only in core security and Redacted`() {
        production.files
            .filter { "/core/security/" !in it.path && it.name != "Redacted" }
            .assertFalse { "OptIn(RedactorApi::class)" in it.text }
    }

    @Test
    fun `K16 no GlobalScope or runBlocking`() {
        production.files.assertFalse {
            Regex("""\bGlobalScope\b|\brunBlocking\b|launch\(Job\(\)\)""").containsMatchIn(it.text)
        }
    }

    @Test
    fun `K18 material3 only in kits (adaptive also in shell)`() {
        production.files
            .filter { "/ui/kit" !in it.path }
            .assertFalse { f ->
                f.imports.any {
                    it.name.startsWith("androidx.compose.material3.") &&
                        !("/ui/shell/" in f.path &&
                            it.name.startsWith("androidx.compose.material3.adaptive."))
                }
            }
    }

    /**
     * K20 (docs/designs/glass-in-the-kit/SURFACES-SPEC.md §5): kit parts get their face from `kitSurfaceLook`. The
     * hand-painted surface lines per kit file ([KitSurfaces]) may only fall: a file over its number, or a new file,
     * fails; a file under its number fails too until the baseline is lowered (the list stays honest). The allowlist
     * needs a reason per line and drops entries that no longer allow anything.
     */
    @Test
    fun `K20 kit parts paint no surface of their own, and the count only falls`() {
        val files =
            KitSurfaces.MODULES.flatMap { m -> File(root, m).walkTopDown().filter { it.isFile }.toList() }
                .map { it.relativeTo(root).invariantSeparatorsPath to it }
                .filter { (path, _) -> KitSurfaces.inScope(path) }
                .associate { (path, file) -> path to file.readText() }
        kAssertTrue(files.size > 300, "K20 scanned only ${files.size} kit files; the scope is wrong")
        val (allow, malformed) = KitSurfaces.parseAllow(File(root, "quality/config/kit-surfaces-allow.txt").readText())
        kotlin.test.assertEquals(emptyList(), malformed, "K20: malformed allowlist lines")
        val result = KitSurfaces.count(files, allow)
        kotlin.test.assertEquals(
            emptyList(),
            result.stale.map { it.source },
            "K20: these allowlist entries no longer match a painting line; delete them",
        )
        val now = result.perFile
        val baseline = File(root, "quality/config/rule-baseline/K20-kit-surfaces.txt")
        if (System.getProperty("oc.ruleBaseline") == "write") {
            baseline.writeText(KitSurfaces.writeBaseline(now))
            return
        }
        val listed = KitSurfaces.parseBaseline(baseline.readText())
        fun lines(path: String) = result.counted[path].orEmpty().joinToString("") { "\n    ${it.line}: ${it.code}" }
        val over = now.filter { (p, n) -> n > (listed[p] ?: 0) }.map { (p, n) -> "$p: $n, baseline ${listed[p] ?: 0}${lines(p)}" }
        kotlin.test.assertEquals(
            emptyList(),
            over,
            "K20: kit files paint more surfaces by hand than their baseline; ask kitSurfaceLook (or, for a mark, a " +
                "preview or a document ground, add an allowlist line with its reason)",
        )
        val under = listed.filter { (p, n) -> (now[p] ?: 0) < n }.map { (p, n) -> "$p: ${now[p] ?: 0}, baseline $n" }
        kotlin.test.assertEquals(emptyList(), under, "K20: fixed; lower these numbers in ${baseline.name}")
    }

    @Test
    fun `K19 a feature never imports another feature`() {
        scope("feature").files.assertFalse { f ->
            val own =
                f.packagee?.name?.split('.')?.take(6)?.joinToString(".") ?: return@assertFalse false
            f.imports.any {
                it.name.startsWith("io.github.eslamasabry.oc.feature.") && !it.name.startsWith(own)
            }
        }
    }
}
