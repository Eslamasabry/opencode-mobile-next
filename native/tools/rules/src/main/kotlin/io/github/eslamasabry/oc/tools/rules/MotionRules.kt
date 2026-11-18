package io.github.eslamasabry.oc.tools.rules

import io.gitlab.arturbosch.detekt.api.Config

/**
 * Replaces MotionGatesTest MOT-GATE-1..4 (MotionGates.kt). The reviewed-exception lists are the guard's own, paths only
 * (the reasons stay in MotionGates.kt next to the test, which stays).
 */
internal object MotionLists {
    val core =
        setOf(
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitMotion.kt",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitMotionBuilders.kt",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitGrow.kt",
            "ui/shell/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/shell/nav/ShellMotion.kt",
        )
    val rawSpecFiles =
        setOf(
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitLoadingBar.kt",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitChecklistStep.kt",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/action/KitWorkingMark.kt",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/choice/KitChoiceCard.kt",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitFeedItem.kt",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/theme/KitThemeFade.kt",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitSurface.kt",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/KitFrost.kt",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitTopEdge.kt",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/dock/DockFace.kt",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/effects/KitLiquidGlass.kt",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/choice/KitModeSegmented.kt",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitArrival.kt",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/KitIllustration.kt",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/PortalScene.kt",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/StatesParts.kt",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/setup/KitServersLink.kt",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/setup/KitSetupScenes.kt",
            "ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/KitComposerBox.kt",
            "ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/markdown/KitMarkdown.kt",
            "ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/stream/FreshFade.kt",
            "ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/stream/KitPromptLift.kt",
            "ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/tool/KitToolNode.kt",
        )
    val loopsGatedByCaller =
        setOf(
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitFeedItem.kt",
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitRefresh.kt",
        )
    val physicalSlides =
        setOf(
            "ui/shell/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/shell/nav/ShellMotion.kt",
        )
}

private fun Source.isKitOrShell() = rel.startsWith("ui/kit") || rel.startsWith("ui/shell")

/** M1: a tween, spring, keyframes, loop spec or default size animation built outside the motion core skips reduced motion. */
internal class OcMotionSpecFromKit(config: Config) :
    OcRule(config, "OcMotionSpecFromKit", "Motion specs come from KitMotion.") {
    private val rawSpec = Regex("""\btween\(|\bspring\(|\bkeyframes\s*\{|\binfiniteRepeatable\(|animateContentSize\(\)""")

    override fun check(src: Source) {
        if (!(src.layer == "ui" || src.layer == "feature") || !src.isProductionSet || !src.isKitOrShell()) return
        if (src.rel in MotionLists.core || src.rel in MotionLists.rawSpecFiles) return
        src.text.lines().forEachIndexed { i, raw ->
            val line = codeLine(raw) ?: return@forEachIndexed
            if (rawSpec.containsMatchIn(line)) {
                flag(src, i + 1, "A motion spec is built from numbers here, which skips reduced motion and the kit's curves. Build it with KitMotion.specs.")
            }
        }
    }
}

/** M2: every rememberInfiniteTransition is gated on ambientLoops or stillFrame. */
internal class OcMotionLoopGated(config: Config) :
    OcRule(config, "OcMotionLoopGated", "Every kit loop is gated on ambientLoops.") {
    private val loop = Regex("""\brememberInfiniteTransition\(""")
    private val gate = Regex("""ambientLoops|stillFrame""")

    override fun check(src: Source) {
        if (!(src.layer == "ui" || src.layer == "feature") || !src.isProductionSet || !src.isKitOrShell()) return
        if (src.rel in MotionLists.loopsGatedByCaller) return
        val lines = src.text.lines()
        lines.forEachIndexed { i, raw ->
            val line = codeLine(raw) ?: return@forEachIndexed
            if (!loop.containsMatchIn(line)) return@forEachIndexed
            val above = lines.subList(maxOf(0, i - 12), i)
            if (above.none { gate.containsMatchIn(it) }) {
                flag(src, i + 1, "A loop that runs under Calm or still frames. Check ambientLoops (or stillFrame) in the 12 lines above it, or make the caller check it.")
            }
        }
    }
}

/** M3: a horizontal slide follows the reading direction. */
internal class OcMotionSlideMirrors(config: Config) :
    OcRule(config, "OcMotionSlideMirrors", "Horizontal slides follow the reading direction.") {
    private val slide = Regex("""\bslide(In|Out)Horizontally\(""")
    private val reading = Regex("""LayoutDirection|\brtl\b|\bRtl\b""")

    override fun check(src: Source) {
        if (!(src.layer == "ui" || src.layer == "feature") || !src.isProductionSet) return
        if (src.rel in MotionLists.physicalSlides || reading.containsMatchIn(src.text)) return
        src.text.lines().forEachIndexed { i, raw ->
            val line = codeLine(raw) ?: return@forEachIndexed
            if (slide.containsMatchIn(line)) {
                flag(src, i + 1, "This slide comes from the wrong edge in Arabic. Read LayoutDirection and mirror it (see ShellMotion).")
            }
        }
    }
}

/** M4: a dialog or sheet never scales in (MOT-2). */
internal class OcMotionModalNoScale(config: Config) :
    OcRule(config, "OcMotionModalNoScale", "A dialog or sheet fades and rises; it never scales in.") {
    private val modals = listOf("/ui/kit/modal/KitDialog.kt", "/ui/kit/modal/KitSheet.kt", "/ui/kit/modal/KitSideSheet.kt", "/ui/kit/modal/KitFloatingPanel.kt")
    private val scaleIn = Regex("""\bscaleIn\(""")

    override fun check(src: Source) {
        if (!(src.layer == "ui") || !src.isProductionSet || modals.none { "/${src.rel}".endsWith(it) }) return
        src.text.lines().forEachIndexed { i, raw ->
            val line = codeLine(raw) ?: return@forEachIndexed
            if (scaleIn.containsMatchIn(line)) flag(src, i + 1, "A modal grows from a smaller size. Fade and rise with KitModalMotion instead.")
        }
    }
}
