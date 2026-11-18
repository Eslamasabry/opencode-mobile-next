package io.github.eslamasabry.oc.tools.rules

import io.gitlab.arturbosch.detekt.api.Config

private val KONSIST_LAYERS = setOf("core", "protocol", "engine", "ui", "feature", "androidApp", "desktopApp")

/** Replaces KonsistRulesTest `production`: production source sets of the layers K1-K18 read. */
private fun Source.konsistProduction() = layer in KONSIST_LAYERS && isKonsistProduction

/**
 * K1 (replaces KonsistRulesTest `K1 features arrange kit parts only`, KitOnlyPlantedTest): a feature file imports only
 * kit parts, the engines' and domain's public types, and its own package. Files listed in
 * `quality/config/rule-baseline/K1-kit-only.txt` are the old debt; a file not on it may not add to it.
 */
internal class OcKitOnlyFeatures(config: Config) :
    OcRule(config, "OcKitOnlyFeatures", "A feature arranges kit parts and draws nothing of its own.") {
    override fun check(src: Source) {
        if (!(src.konsistProduction() && src.layer == "feature")) return
        if (src.rel in ruleBaseline(src.root, "K1-kit-only")) return
        val bad = violations(src.packageName(), src.name.removeSuffix(".kt"), src.imports())
        if (bad.isNotEmpty()) {
            flagFile(
                "This feature file imports ${bad.take(3).joinToString(", ")}${if (bad.size > 3) " and ${bad.size - 3} more" else ""}, " +
                    "which is not a kit part. Features only arrange kit parts: use the kit part that does this " +
                    "(ui/kit*), or ask the kit owner to add the missing part. Never draw it in the feature.",
            )
        }
    }

    companion object {
        private val allowed =
            listOf(
                "androidx.compose.runtime.",
                "androidx.compose.foundation.layout.",
                "androidx.compose.foundation.lazy.",
                "androidx.compose.foundation.rememberScrollState", "androidx.compose.foundation.ScrollState",
                "androidx.compose.foundation.verticalScroll", "androidx.compose.foundation.horizontalScroll",
                "androidx.compose.foundation.text.input.TextFieldState",
                "androidx.compose.foundation.text.input.rememberTextFieldState",
                "androidx.compose.ui.Modifier", "androidx.compose.ui.Alignment",
                "androidx.compose.ui.unit.", "androidx.compose.ui.semantics.", "androidx.compose.ui.focus.",
                "androidx.compose.ui.input.key.",
                "androidx.compose.ui.platform.LocalLayoutDirection", "androidx.compose.ui.platform.LocalDensity",
                "androidx.compose.ui.platform.LocalFocusManager", "androidx.compose.ui.platform.testTag",
                "androidx.lifecycle.", "dev.zacsweers.metro.", "dev.zacsweers.metrox.viewmodel.",
                "org.jetbrains.compose.resources.stringResource", "org.jetbrains.compose.resources.pluralStringResource",
                "io.github.eslamasabry.oc.ui.kit.", "io.github.eslamasabry.oc.ui.kitchat.",
                "io.github.eslamasabry.oc.ui.kitcontent.", "io.github.eslamasabry.oc.ui.design.",
                "io.github.eslamasabry.oc.ui.words.", "io.github.eslamasabry.oc.ui.routes.",
                "io.github.eslamasabry.oc.engine.", "io.github.eslamasabry.oc.core.domain.",
                "io.github.eslamasabry.oc.core.model.", "io.github.eslamasabry.oc.core.common.",
                "kotlinx.coroutines.flow.", "kotlinx.coroutines.launch", "kotlinx.coroutines.CoroutineScope",
                "kotlinx.collections.immutable.", "kotlinx.serialization.", "kotlin.",
            )
        private val onlyInEntries = listOf("androidx.navigation3.runtime.")
        private val onlyInPreviews = listOf("androidx.compose.ui.tooling.preview.")
        private val bannedInside =
            listOf(
                "io.github.eslamasabry.oc.ui.kit.platform.", "io.github.eslamasabry.oc.core.common.AppDispatchers",
                "io.github.eslamasabry.oc.ui.kit.effects.kitLiquidFace", "io.github.eslamasabry.oc.ui.kit.foundation.kitGlassSheet",
                "io.github.eslamasabry.oc.ui.kit.foundation.LocalKitGlassBackdrop",
            )

        /** Same decision as `KitOnly.violations` in :quality; the file name is without `.kt`. */
        fun violations(packageName: String?, fileName: String, imports: List<String>): List<String> {
            val own = packageName?.takeIf { it.isNotEmpty() }?.split('.')?.take(6)?.joinToString(".")
            return imports.filterNot { n ->
                when {
                    bannedInside.any { n.startsWith(it) } -> false
                    onlyInEntries.any { n.startsWith(it) } -> fileName.endsWith("Entries")
                    onlyInPreviews.any { n.startsWith(it) } -> fileName.endsWith("Previews")
                    own != null && n.startsWith(own) -> true
                    else -> allowed.any { n.startsWith(it) }
                }
            }
        }
    }
}

/** K2 (replaces `K2 presentation imports no Compose and no copy`): a feature's presentation package is plain Kotlin. */
internal class OcPresentationPlainKotlin(config: Config) :
    OcRule(config, "OcPresentationPlainKotlin", "Presentation code has no Compose and no copy.") {
    override fun check(src: Source) {
        if (src.layer != "feature" || !src.packageName().endsWith(".presentation")) return
        val bad = src.imports().firstOrNull { it.startsWith("androidx.compose.") || ".ui.words.res" in it } ?: return
        flagFile(
            "A presentation file imports $bad. Presentation holds state and decisions only: no Compose and no copy. " +
                "Put the words and the drawing in the screen (XScreen) and hand it plain values.",
        )
    }
}

/** K3 (replaces `K3 no runCatching in files with suspend functions`). Old debt: K3-runCatching.txt. */
internal class OcNoRunCatchingInSuspend(config: Config) :
    OcRule(config, "OcNoRunCatchingInSuspend", "runCatching swallows cancellation in suspend code.") {
    private val call = Regex("""\brunCatching\s*[({]""")

    override fun check(src: Source) {
        if (!src.konsistProduction() || src.rel in ruleBaseline(src.root, "K3-runCatching")) return
        val m = call.find(src.text) ?: return
        if ("suspend " !in src.text) return
        flag(
            src, src.lineOf(m.range.first),
            "runCatching in a file with suspend functions catches the coroutine's cancellation too. " +
                "Use attempt { } from core/common, which returns a Failure value and lets cancellation through.",
        )
    }
}

/** K4 (replaces `K4 no Dispatchers or Clock System outside core common`). Old debt: K4-dispatchers-clock.txt. */
internal class OcNoDispatchersOrClock(config: Config) :
    OcRule(config, "OcNoDispatchersOrClock", "Dispatchers and the system clock are injected, never read.") {
    private val dispatchers = Regex("""\bDispatchers\.""")

    override fun check(src: Source) {
        if (!src.konsistProduction() || src.has("/core/common/")) return
        if (src.rel in ruleBaseline(src.root, "K4-dispatchers-clock")) return
        val m = dispatchers.find(src.text)
        val at = m?.range?.first ?: src.text.indexOf("Clock.System").takeIf { it >= 0 } ?: return
        flag(
            src, src.lineOf(at),
            "Dispatchers.* or Clock.System outside core/common. Inject AppDispatchers and Clock instead, so tests can " +
                "control time and threads.",
        )
    }
}

/** K5 (replaces `K5 no Log, println, printStackTrace or logging interceptors`). The reviewed files are the old list. */
internal class OcLogOnlyThroughAppLog(config: Config) :
    OcRule(config, "OcLogOnlyThroughAppLog", "Log only through AppLog.") {
    private val raw = Regex("""\bprintln\(|printStackTrace\(|HttpLoggingInterceptor""")
    private val reviewed =
        listOf(
            "/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/DiagnosticsAttentionWiring.kt",
            "/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/AppLogSinks.kt",
            "/ui/gate/src/desktopMain/kotlin/io/github/eslamasabry/oc/ui/gate/AlignGuard.kt",
            "/ui/gate/src/desktopMain/kotlin/io/github/eslamasabry/oc/ui/gate/LayoutGuard.kt",
        )

    override fun check(src: Source) {
        if (!src.konsistProduction() || reviewed.any { ("/" + src.rel).endsWith(it) }) return
        val logs = "android.util.Log" in src.imports() || raw.containsMatchIn(src.text)
        if (!logs) return
        flag(
            src, raw.find(src.text)?.let { src.lineOf(it.range.first) } ?: 1,
            "Raw logging (android.util.Log, println, printStackTrace or an HTTP logging interceptor). Log only through " +
                "AppLog with a lambda: it redacts secrets, and a raw line can leak a credential or a prompt.",
        )
    }
}

/** K11 (replaces `K11 no String format, System, Thread, java or javax in commonMain`). Old debt: K11-jvm-in-common.txt. */
internal class OcNoJvmInCommon(config: Config) :
    OcRule(config, "OcNoJvmInCommon", "Shared code uses no JVM-only API.") {
    private val jvmUse = Regex("""String\.format|\bSystem\.|\bThread\.""")

    override fun check(src: Source) {
        if (!src.konsistProduction() || !src.has("/src/commonMain/")) return
        if (src.rel in ruleBaseline(src.root, "K11-jvm-in-common")) return
        val badImport = src.imports().firstOrNull { it.startsWith("java.") || it.startsWith("javax.") }
        if (badImport == null && !jvmUse.containsMatchIn(src.text)) return
        flag(
            src, jvmUse.find(src.text)?.let { src.lineOf(it.range.first) } ?: 1,
            "commonMain code uses a JVM-only API (${badImport ?: "String.format, System or Thread"}); iOS and desktop " +
                "builds share this file. Use the kotlin.* or core/common equivalent, or move this to an androidMain or " +
                "desktopMain file behind an interface.",
        )
    }
}

/** K13 (replaces `K13 RedactorApi opt-in only in core security and Redacted`). */
internal class OcRedactorOptInPlace(config: Config) :
    OcRule(config, "OcRedactorOptInPlace", "The redactor's internal API is opted into only by core/security and Redacted.") {
    override fun check(src: Source) {
        if (!src.konsistProduction() || src.has("/core/security/") || src.name.removeSuffix(".kt") == "Redacted") return
        val at = src.text.indexOf("OptIn(RedactorApi::class)").takeIf { it >= 0 } ?: return
        flag(
            src, src.lineOf(at),
            "OptIn(RedactorApi::class) outside core/security. Redaction has one owner: call the public redaction " +
                "function there instead of reaching into the redactor.",
        )
    }
}

/** K16 (replaces `K16 no GlobalScope or runBlocking`). */
internal class OcNoGlobalScopeOrRunBlocking(config: Config) :
    OcRule(config, "OcNoGlobalScopeOrRunBlocking", "No GlobalScope, runBlocking or detached jobs.") {
    private val bad = Regex("""\bGlobalScope\b|\brunBlocking\b|launch\(Job\(\)\)""")

    override fun check(src: Source) {
        if (!src.konsistProduction()) return
        val m = bad.find(src.text) ?: return
        flag(
            src, src.lineOf(m.range.first),
            "${m.value} escapes structured concurrency. Launch in the scope the engine or view model owns " +
                "(injected CoroutineScope), so work stops when its owner does.",
        )
    }
}

/** K18 (replaces `K18 material3 only in kits (adaptive also in shell)`). */
internal class OcMaterial3OnlyInKits(config: Config) :
    OcRule(config, "OcMaterial3OnlyInKits", "Material 3 is used only inside the kit modules.") {
    override fun check(src: Source) {
        if (!src.konsistProduction() || src.has("/ui/kit")) return
        val bad =
            src.imports().firstOrNull {
                it.startsWith("androidx.compose.material3.") &&
                    !(src.has("/ui/shell/") && it.startsWith("androidx.compose.material3.adaptive."))
            } ?: return
        flagFile("This file imports $bad. Material components live in the kit (ui/kit*); use the kit part, or ask the kit owner to add it.")
    }
}

/** K19 (replaces `K19 a feature never imports another feature`). */
internal class OcNoFeatureToFeature(config: Config) :
    OcRule(config, "OcNoFeatureToFeature", "A feature never imports another feature.") {
    override fun check(src: Source) {
        if (src.layer != "feature") return
        val own = src.packageName().split('.').take(6).joinToString(".")
        if (src.packageName().isEmpty()) return
        val bad = src.imports().firstOrNull { it.startsWith("io.github.eslamasabry.oc.feature.") && !it.startsWith(own) } ?: return
        flagFile(
            "This feature imports $bad from another feature. Features share nothing but the kit, the engines and " +
                "routes: move what both need into an engine or the kit, or open the other screen through ui/routes.",
        )
    }
}
