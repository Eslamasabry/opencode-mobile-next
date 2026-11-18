package io.github.eslamasabry.oc.tools.rules

import io.gitlab.arturbosch.detekt.api.Config

/** Layers the BuildRestGates-based guard tests read, as `BuildRestGates.production(root, ...)` did. */
private fun Source.productionIn(vararg layers: String) = layer in layers && isProductionSet && !has("/build/")

private val ALL_PRODUCT_LAYERS = arrayOf("core", "protocol", "engine", "ui", "feature", "android", "androidApp", "desktopApp")

/**
 * Replaces NoEarlyReturnGuardTest (C20). A labelled return out of a composable content block crashed a list row in the
 * field (IntStack.peek2 in GapComposer.endToMarker).
 */
internal class OcNoLabelledReturnInContent(config: Config) :
    OcRule(config, "OcNoLabelledReturnInContent", "No labelled return out of a composable content block.") {
    private val label =
        Regex(
            "return@(Column|Row|Box|BoxWithConstraints|CompositionLocalProvider|item|items|itemsIndexed|stickyHeader|" +
                "AnimatedVisibility|AnimatedContent|Crossfade|key|FlowRow|Surface|Kit[A-Z][A-Za-z0-9]*)\\b",
        )

    /** File and label pairs that are not composable content (copied from the guard test, with its reasons). */
    private val allowed =
        setOf(
            "KitLiquidBody.kt" to "KitLiquidBackdrop",
            "KitLiquidGlass.kt" to "KitLiquidBackdrop",
            "KitGlassLookPicker.kt" to "KitLiquidBackdrop",
            "ChatInboxSlot.kt" to "KitConfirm",
        )

    override fun check(src: Source) {
        if (src.layer !in setOf("ui", "feature", "androidApp", "desktopApp")) return
        if ("/src/" !in "/${src.rel}" || "Test/" in src.rel) return
        src.text.lines().forEachIndexed { index, line ->
            val code = line.substringBefore("//")
            if (code.trimStart().startsWith("*") || code.trimStart().startsWith("/*")) return@forEachIndexed
            val found = label.find(code)?.groupValues?.get(1) ?: return@forEachIndexed
            if ((src.name to found) in allowed) return@forEachIndexed
            flag(
                src, index + 1,
                "A labelled return (return@$found) out of a composable content block crashed a list row in the field. " +
                    "Write if / else around the rest of the block: `if (x == null) { ... } else { rest }`, or lift the " +
                    "condition above the block.",
            )
        }
    }
}

/** Replaces KeyboardInsetGuardTest. The kit reads the raw keyboard measurement in one place. */
internal class OcRawKeyboardInset(config: Config) :
    OcRule(config, "OcRawKeyboardInset", "Only the kit's keyboard files read the raw keyboard inset.") {
    private val allowed = setOf("KitKeyboard.kt", "KitKeyboard.android.kt", "KitKeyboard.desktop.kt")
    private val raw =
        Regex("""WindowInsets\.(Companion\.)?ime\b|\bimeAnimationTarget\b|\bimePadding\(|\bisImeVisible\b|layout\.ime\b|layout\.imePadding\b""")

    override fun check(src: Source) {
        if (!src.productionIn(*ALL_PRODUCT_LAYERS) || src.name in allowed) return
        src.text.lines().forEachIndexed { i, raw0 ->
            val line = codeLine(raw0) ?: return@forEachIndexed
            if (raw.containsMatchIn(line)) {
                flag(
                    src, i + 1,
                    "This reads the raw keyboard measurement, which keeps the keyboard's last height if its closing " +
                        "animation never ends. Ask the kit: kitKeyboardVisible(), kitKeyboardHeight() or kitImePadding().",
                )
            }
        }
    }
}

/** Replaces FailureLabelGuardTest. R8 renames classes, so a class name on screen is noise. */
internal class OcFailureLabel(config: Config) :
    OcRule(config, "OcFailureLabel", "A screen or report names a failure with Failure.label(), never a class name.") {
    private val allowed = setOf("FlutterGoldenGate.kt", "CrashStore.kt")
    private val raw =
        Regex("""::class\.(simpleName|qualifiedName)\b|\bjavaClass\.(simpleName|name)\b|\bfailure\??\.toString\(\)""")

    private fun scanned(path: String): Boolean =
        path.startsWith("feature/") || path.startsWith("ui/") || path.startsWith("engine/diagnostics/") ||
            (path.startsWith("engine/analytics/") && "/health/" in path)

    override fun check(src: Source) {
        if (!src.productionIn("feature", "ui", "engine") || !scanned(src.rel) || src.name in allowed) return
        src.text.lines().forEachIndexed { i, raw0 ->
            val line = codeLine(raw0) ?: return@forEachIndexed
            if (raw.containsMatchIn(line)) {
                flag(
                    src, i + 1,
                    "A class name or a raw failure text is read here; a release build renames classes, so it prints noise. " +
                        "Name the failure with Failure.label() (core/common, FailureLabel.kt).",
                )
            }
        }
    }
}

/** Replaces LayerRecordGuardTest (first test). Layers are recorded and drawn through the kit's tracked helpers. */
internal class OcLayerRecord(config: Config) :
    OcRule(config, "OcLayerRecord", "GraphicsLayers are recorded and drawn through the kit's tracked helpers.") {
    private val notLayers = setOf("KitThemeMetrics", "store", "recentModels")
    private val draw = Regex("""(?<![\w.])drawLayer\(""")
    private val record = Regex("""(?<![\w.])([A-Za-z_][\w.]*)\.record\s*[({]""")

    override fun check(src: Source) {
        if (!src.productionIn("ui") || src.name == "KitLayerRecord.kt") return
        if ("GraphicsLayer" !in src.text && "drawLayer" !in src.text) return
        src.text.lines().forEachIndexed { i, raw0 ->
            val line = raw0.substringBefore("//").trim()
            val hit =
                when {
                    line.isEmpty() || line.startsWith("*") || line.startsWith("/*") || line.startsWith("import ") -> false
                    line.startsWith("fun ") || " fun " in line -> false
                    draw.containsMatchIn(line) -> true
                    else -> record.findAll(line).any { it.groupValues[1].substringBefore('.') !in notLayers }
                }
            if (hit) {
                flag(
                    src, i + 1,
                    "A GraphicsLayer is recorded or drawn directly; a layer drawn inside its own recording overflowed the " +
                        "renderer's stack on a device. Record with kitRecord and draw with kitDrawLayer (KitLayerRecord.kt).",
                )
            }
        }
    }
}

/**
 * Replaces PixelTickerGateTest, its delay-loop half (BG-CPU-GATE). A `while` loop that waits with a bare delay in UI code
 * kept waking the app behind the launcher (40 percent of a core). The loops that existed on 2026-11-14 are the old debt,
 * per file; a file may not gain one. The collectAsState count (a sum over files) stays in the test.
 */
internal class OcRawDelayLoop(config: Config) :
    OcRule(config, "OcRawDelayLoop", "A UI loop that repeats work for pixels waits through KitVisibility.tick.") {
    private val loop = Regex("""(?<![\w.])while\s*\(""")
    private val delay = Regex("""(?<!\w)delay\(""")
    private val body = 8

    private val baseline =
        mapOf(
            "feature/agents/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/agents/hub/AgentsPresenter.kt" to 2,
            "feature/agents/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/agents/remote_terminal/presentation/RemoteTerminalViewModel.kt" to 1,
            "feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/work/ChatWorkModel.kt" to 1,
            "feature/preview/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/preview/ui/DevPreviewScreen.kt" to 1,
            "feature/team/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/team/fleet/FleetGateSheet.kt" to 1,
            "feature/team/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/team/presentation/FleetAgentsViewModels.kt" to 1,
            "feature/team/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/team/presentation/TeamPresent.kt" to 1,
            "feature/team/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/team/presentation/TeamSetup.kt" to 1,
            "feature/terminal/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/terminal/presentation/LocalTerminalViewModel.kt" to 1,
            "feature/terminal/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/terminal/presentation/TerminalViewModel.kt" to 1,
            "ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/log/KitLogPanel.kt" to 1,
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/input/KitField.kt" to 1,
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/status/KitStatusLine.kt" to 1,
        )

    override fun check(src: Source) {
        if (!src.productionIn("ui", "feature")) return
        val lines = src.text.lines().mapIndexedNotNull { i, raw -> codeLine(raw)?.let { i + 1 to it } }
        val loops =
            lines.indices.filter { i ->
                loop.containsMatchIn(lines[i].second) &&
                    lines.subList(i, minOf(lines.size, i + body)).any { delay.containsMatchIn(it.second) }
            }
        val allowed = baseline[src.rel] ?: 0
        if (loops.size > allowed) {
            flag(
                src, lines[loops[allowed]].first,
                "A while loop that waits with a bare delay() keeps waking after the window stops drawing (it cost 40 " +
                    "percent of a core behind the launcher). Wait through KitVisibility.tick, which parks while the " +
                    "app is hidden; or, if the loop is not about pixels, run it in an engine on the Pacer (core/common).",
            )
        }
    }
}

/** Replaces TextGlyphGateTest (B-KIT-GLYPH-001). A mark is a kit glyph, never a text character standing in for an icon. */
internal class OcTextGlyph(config: Config) :
    OcRule(config, "OcTextGlyph", "A mark is a KitIcon, never a text character.") {
    private val mark = """(?:\p{L}|[✓✔✗✕✖×☑☐☒•●○◦▪▸▶►★☆⚠ⓘ!?+−])"""
    private val textCall = Regex("""\b(?:KitText|Text|BasicText)\(\s*"$mark"""")
    private val branch = Regex("""->\s*"$mark"\s*$""")
    private val initials = Regex("""\b(?:KitText|Text|BasicText)\(\s*[\w.]+\.(?:take\([12]\)|first\(\)|firstOrNull\(\))""")

    private val allowed =
        setOf(
            "ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/markdown/KitMarkdown.kt",
            "ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/diff/KitDiffView.kt",
            "ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/terminal/KitTerminalKeyBar.kt",
        )

    override fun check(src: Source) {
        if (!src.productionIn("feature", "ui", "androidApp", "desktopApp") || src.rel in allowed) return
        src.text.lines().forEachIndexed { i, raw ->
            val line = raw.substringBefore("// ").trim()
            if (line.startsWith("*") || line.startsWith("/*")) return@forEachIndexed
            if (textCall.containsMatchIn(line) || branch.containsMatchIn(line) || initials.containsMatchIn(line)) {
                flag(
                    src, i + 1,
                    "A text character stands in for an icon here. Draw the mark with KitIcon(KitIcons.X), or show nothing.",
                )
            }
        }
    }
}

/** Replaces KitIconSizeGuardTest (scan half, P-101). KitIcon is sized by `size = KitIconSize.X`, never by a modifier. */
internal class OcKitIconSize(config: Config) :
    OcRule(config, "OcKitIconSize", "A KitIcon is sized with size = KitIconSize, never a Modifier.") {
    private val iconCall = Regex("""\bKit(?:Status)?Icon\(""")
    private val sizing = Regex("""\.(?:size|requiredSize|width|height|requiredWidth|requiredHeight|sizeIn)\(""")

    override fun check(src: Source) {
        if (src.layer != "ui" && src.layer != "feature") return
        if ("/src/" !in "/${src.rel}" || "Test/" in src.rel) return
        val source = src.text
        iconCall.findAll(source).forEach { match ->
            val open = match.range.last
            var depth = 0
            var end = open
            while (end < source.length) {
                when (source[end]) {
                    '(' -> depth++
                    ')' -> if (--depth == 0) break
                }
                end++
            }
            val own = source.substring(open + 1, end.coerceAtMost(source.length)).substringBefore('{')
            if ("Modifier" in own && sizing.containsMatchIn(own)) {
                flag(
                    src, src.lineOf(match.range.first),
                    "A KitIcon is sized by a modifier, which crops the glyph on its end and bottom edges. Pass " +
                        "size = KitIconSize.X instead of Modifier.size.",
                )
            }
        }
    }
}

/**
 * Replaces RestoreGuardsTest (gap 2). What comes back after Android kills the app: a confirm for a destructive act and
 * anything holding a secret are never saved with rememberSaveable.
 */
internal class OcRestoreGuards(config: Config) :
    OcRule(config, "OcRestoreGuards", "A destructive confirm and a secret are never held with rememberSaveable.") {
    private val saved = Regex("""(?:var|val)\s+(\w+)\s+by\s+rememberSaveable""")
    private val destructive = Regex("(?i)(confirm|delete|remove|forget|disconnect|wipe|erase|reset|stop|asking|revoke)")
    private val secret = Regex("(?i)(password|passcode|secret|token|apikey|credential)")

    override fun check(src: Source) {
        if (src.layer !in setOf("ui", "feature", "engine") || !src.has("/commonMain/")) return
        saved.findAll(src.text).forEach { m ->
            val name = m.groupValues[1]
            val line = src.lineOf(m.range.first)
            if (destructive.containsMatchIn(name)) {
                flag(src, line, "$name is a destructive confirm saved across a process death. Use remember, so the person has to ask again.")
            }
            if (secret.containsMatchIn(name)) {
                flag(src, line, "$name looks like a secret held with rememberSaveable, which writes it to saved state. Use remember, or keep it in SecretStore.")
            }
        }
    }
}
