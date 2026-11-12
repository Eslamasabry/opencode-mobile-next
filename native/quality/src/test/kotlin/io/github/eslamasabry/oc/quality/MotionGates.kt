package io.github.eslamasabry.oc.quality

/**
 * Motion review 2026-11-09 (docs/native/qa/motion-review-2026-11-09): source gates that keep each fixed motion class
 * fixed. Pure functions over (path, text), as [BuildRestGates], so the planted tests feed them the same way.
 */
internal object MotionGates {
    data class Hit(val path: String, val rule: String, val line: Int)

    private fun code(text: String): List<Pair<Int, String>> =
        text.lines().mapIndexedNotNull { i, raw ->
            val line = raw.substringBefore("//").trim()
            if (line.isEmpty() || line.startsWith("*") || line.startsWith("/*")) null else (i + 1) to line
        }

    // ---- M1: specs come from KitMotion ----

    /** The motion core: the only kit files that build tweens and springs from numbers. */
    val MOTION_CORE: Set<String> =
        setOf(
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitMotion.kt",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitMotionBuilders.kt",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitGrow.kt",
            "ui/shell/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/shell/nav/ShellMotion.kt",
        )

    private val RAW_SPEC = Regex("""\btween\(|\bspring\(|\bkeyframes\s*\{|\binfiniteRepeatable\(|animateContentSize\(\)""")

    /**
     * A tween, spring, keyframes, loop spec or default-spring size animation built outside the motion core: it skips
     * reduced motion, the debug time scale or the kit's curves unless the file handles each by hand.
     */
    fun rawSpec(path: String, text: String): List<Hit> =
        if (path in MOTION_CORE) emptyList()
        else code(text).filter { (_, l) -> RAW_SPEC.containsMatchIn(l) }.map { (n, _) -> Hit(path, "raw motion spec", n) }

    /**
     * Reviewed files that still build their own specs (path → why). Shrinks only: a new file uses `KitMotion.specs`; a
     * file that stops needing its entry loses it.
     */
    val RAW_SPEC_FILES: Map<String, String> =
        mapOf(
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitLoadingBar.kt" to
                "the indeterminate bar's lap, created only when ambientLoops allows it",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitChecklistStep.kt" to
                "the running step's spinner lap, gated on ambientLoops",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/action/KitWorkingMark.kt" to
                "the working mark's spinner lap, gated on ambientLoops",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/choice/KitChoiceCard.kt" to
                "the busy mark's spinner lap, gated on ambientLoops (motion review 2026-11-09)",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitFeedItem.kt" to
                "the live sweep's lap, gated on ambientLoops",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/theme/KitThemeFade.kt" to
                "the theme cover's linear fade, a Flutter parity number, skipped when the switch does not fade",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitSurface.kt" to
                "the Style surface cross-fade on Pulse, its length 0 under reduced motion",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitFrost.kt" to
                "the frost fade, snapped under reduced motion",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitTopEdge.kt" to
                "the top edge's glass coming in under scrolled content: KitFrost's own 180 ms fade, snapped under reduced motion",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/dock/DockFace.kt" to
                "the edge dock's liquid springs (DockSprings): moves into the motion core with the Dock role (glass owner)",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/effects/KitLiquidGlass.kt" to
                "the liquid ink flip (owned by the glass work), a still frame under reduced motion",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/choice/KitModeSegmented.kt" to
                "the light/dark switch's clock, linear by design, snapped when the switch does not play",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitArrival.kt" to
                "the arrival wash's fade after its hold (Flutter ENTRANCE_MS), snapped under reduced motion",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/KitIllustration.kt" to
                "an illustration's entrance and loop, a still frame under reduced motion",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/PortalScene.kt" to
                "the portal scene's entrance and loop, a still frame under reduced motion",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/StatesParts.kt" to
                "state illustrations' entrance and breath, a still frame under reduced motion",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/setup/KitServersLink.kt" to
                "the setup link's entrance and breath (Flutter numbers), a still frame under reduced motion",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/setup/KitSetupScenes.kt" to
                "the setup scenes' entrance and breath (Flutter numbers), a still frame under reduced motion",
            "ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/KitComposerBox.kt" to
                "the Stop ring's lap, gated on ambientLoops and the saver (motion review 2026-11-09)",
            "ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/markdown/KitMarkdown.kt" to
                "the streaming caret's breath, gated on ambientLoops",
            "ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/stream/FreshFade.kt" to
                "the live block's grow-only height spring, run only at Full by its caller",
            "ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/stream/KitPromptLift.kt" to
                "the prompt's lift from the field (skipped at Off, fade only at Calm); its curve is the lift's own",
            "ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/tool/KitToolNode.kt" to
                "the tool mark's finishing tick (Flutter STEP numbers), snapped under reduced motion",
        )

    // ---- M2: loops run only where loops may ----

    private val LOOP = Regex("""\brememberInfiniteTransition\(""")
    private val LOOP_GATE = Regex("""ambientLoops|stillFrame""")

    /** A `rememberInfiniteTransition` with no `ambientLoops`/`stillFrame` check in the [LOOP_LOOKBACK] lines above it. */
    fun ungatedLoop(path: String, text: String): List<Hit> {
        val lines = text.lines()
        return code(text).filter { (_, l) -> LOOP.containsMatchIn(l) }.mapNotNull { (n, _) ->
            val above = lines.subList(maxOf(0, n - 1 - LOOP_LOOKBACK), n - 1)
            if (above.any { LOOP_GATE.containsMatchIn(it) }) null else Hit(path, "loop not gated on ambientLoops", n)
        }
    }

    private const val LOOP_LOOKBACK = 12

    /** Reviewed: the loop's composable is called only when its caller checked `ambientLoops` (path → where). */
    val LOOPS_GATED_BY_CALLER: Map<String, String> =
        mapOf(
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitFeedItem.kt" to
                "sweepBrush runs only when `sweep` (running && ambientLoops)",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitRefresh.kt" to
                "the breath runs only when RefreshRules.breathes (Refreshing && ambientLoops)",
        )

    // ---- M3: horizontal slides follow the reading direction ----

    private val SLIDE_X = Regex("""\bslide(In|Out)Horizontally\(""")
    private val READING = Regex("""LayoutDirection|\brtl\b|\bRtl\b""")

    /** A file that slides along x without reading the layout direction: in Arabic it comes from the wrong edge. */
    fun unmirroredSlide(path: String, text: String): List<Hit> =
        if (READING.containsMatchIn(text)) emptyList()
        else code(text).filter { (_, l) -> SLIDE_X.containsMatchIn(l) }.map { (n, _) -> Hit(path, "slide ignores RTL", n) }

    /** Reviewed: slides by a physical edge on purpose (path → why). */
    val PHYSICAL_SLIDES: Map<String, String> =
        mapOf(
            "ui/shell/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/shell/nav/ShellMotion.kt" to
                "the predictive preview shifts away from the edge the finger swiped, whichever the reading direction",
        )

    // ---- M4: a modal never scales in (MOT-2) ----

    private val MODALS = listOf("/ui/kit/modal/KitDialog.kt", "/ui/kit/modal/KitSheet.kt", "/ui/kit/modal/KitSideSheet.kt", "/ui/kit/modal/KitFloatingPanel.kt")
    private val SCALE_IN = Regex("""\bscaleIn\(""")

    /** A dialog or sheet that grows from a smaller size instead of fading and rising (KitModalMotion). */
    fun modalScale(path: String, text: String): List<Hit> =
        if (MODALS.none { path.endsWith(it) }) emptyList()
        else code(text).filter { (_, l) -> SCALE_IN.containsMatchIn(l) }.map { (n, _) -> Hit(path, "modal scales in", n) }
}
