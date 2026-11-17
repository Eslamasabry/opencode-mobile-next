@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.looks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.FrostPolicy
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.GlassLookId
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidBackdrop
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidPaneSpec
import io.github.eslamasabry.oc.ui.kit.effects.LocalKitLiquidChrome
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidPlainStandIn
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBackground
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.status.KitTaskState
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.card.KitAgent
import io.github.eslamasabry.oc.ui.kitchat.card.KitAgentStrip
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerBox
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerLimitStrip
import io.github.eslamasabry.oc.ui.kitchat.composer.KitLimitNotice
import io.github.eslamasabry.oc.ui.kitchat.composer.KitLimitReset
import io.github.eslamasabry.oc.ui.kitchat.composer.KitVoicePill
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffModel
import io.github.eslamasabry.oc.ui.kitchat.diff.KitDiffPreview
import io.github.eslamasabry.oc.ui.kitchat.diff.KitDiffPreviewCard
import io.github.eslamasabry.oc.ui.kitchat.diff.KitDiffView
import io.github.eslamasabry.oc.ui.kitchat.form.KitFormCard
import io.github.eslamasabry.oc.ui.kitchat.tool.KitCommandBox
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolOutput
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Desktop pictures for glass-truth (2026-11-16): the chat kit's parts that painted their own outer face (the voice pill,
 * its attention state, the limit strip inside the message box, the form card, the command and output boxes, the agent
 * strip) over bright content with their words, and the diff view and preview (removed lines), in Glass (Glossy and
 * Frosted, dark and light) and in Solid. Not a test: it runs only when `GLASS_TRUTH_OUT` names a folder. Desktop draws no
 * lens: a liquid pane is the plain stand-in (blur, hold, tint, rim light; no bend).
 */
class GlassTruthChatRenders {
    private class Lens(private val base: KitPlatform = DesktopKitPlatform()) : KitPlatform by base {
        override val liquidGlass: Boolean = true

        override fun frostEffect(spec: FrostSpec, density: Float): RenderEffect? =
            FrostPolicy.blurRadiusPx(spec.sigmaDp, density).let { BlurEffect(it, it) }

        override fun liquidPane(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, transform: () -> Float): Modifier =
            kitLiquidPlainStandIn(spec, backdrop, shape, this)
    }

    private data class Look(val name: String, val style: Style, val glass: GlassLookId = GlassLookId.Glossy)

    @Test
    fun `glass truth chat renders`() {
        val out = System.getenv("GLASS_TRUTH_OUT")?.let(::File) ?: return
        out.mkdirs()
        val looks = listOf(Look("glossy", Style.Glass), Look("frosted", Style.Glass, GlassLookId.Frosted), Look("solid", Style.Solid))
        for (look in looks) for (dark in listOf(true, false)) {
            val mode = if (dark) "dark" else "light"
            shot(File(out, "chat-parts-${look.name}-$mode.png"), look, dark, 1180) { ChatParts(dark) }
            shot(File(out, "chat-bottom-${look.name}-$mode.png"), look, dark, 720) { Bottom(dark) }
            shot(File(out, "diff-${look.name}-$mode.png"), look, dark, 520) { Diffs() }
        }
    }

    private fun shot(file: File, look: Look, dark: Boolean, heightDp: Int, content: @Composable () -> Unit) {
        runDesktopComposeUiTest((W * D).toInt(), (heightDp * D).toInt()) {
            mainClock.autoAdvance = false
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    KitTheme(
                        KitAppearance(
                            mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                            motion = MotionLevel.Full,
                            style = look.style,
                            glassLook = look.glass,
                            liveBackground = true,
                            liveStyle = "auroraImage",
                        ),
                        Lens(),
                        "en",
                        stillFrame = true,
                    ) {
                        CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                            KitLiveBackground(Modifier.fillMaxSize()) { content() }
                        }
                    }
                }
            }
            // The clock is ours (auto-advance off): a few half-second jumps, one frame each, instead of a frame per 16 ms
            // of the live background. The still frame is what is drawn either way.
            waitForIdle()
            repeat(3) {
                mainClock.advanceTimeBy(500, ignoreFrameDuration = true)
                waitForIdle()
            }
            ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", file)
        }
    }

    /** Bright content: white cards, white and black lines of "text", an orange and a green disc. */
    private fun DrawScope.brightPage(dark: Boolean) {
        val d = density
        fun rect(c: Color, l: Float, t: Float, w: Float, h: Float, r: Float = 0f) = drawRoundRect(c, Offset(l * d, t * d), Size(w * d, h * d), CornerRadius(r * d))
        var y = 8f
        while (y < size.height / d) {
            rect(Color.White, 12f, y, 170f, 46f, 12f)
            drawCircle(Color(1f, .5f, 0f), 22f * d, Offset(230f * d, (y + 24f) * d))
            drawCircle(Color(0xFF34C759), 18f * d, Offset(300f * d, (y + 30f) * d))
            for (line in 0 until 3) {
                var x = 196f
                while (x < 400f) {
                    rect(if (dark) Color.White else Color.Black, x, y + 58f + line * 16f, 2.2f, 11f)
                    x += 5f
                }
            }
            y += 120f
        }
    }

    @Composable
    private fun ChatParts(dark: Boolean) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().drawBehind { brightPage(dark) })
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                KitFormCard("A few questions before I start", "3 questions", "Answer", {})
                KitCommandBox("./gradlew :app:test", "BUILD SUCCESSFUL in 41s\n12 tests completed", moreLabel = "Show all", onMore = {}, exitLabel = "Exit code 0")
                KitToolOutput("{\n  \"files\": 3,\n  \"changed\": true\n}", moreLabel = "Show all", onMore = {})
                KitAgentStrip(
                    listOf(
                        KitAgent("a", "furiosa", KitTaskState.Working, role = "Lead"),
                        KitAgent("b", "nux", KitTaskState.NeedsYou, role = "Worker", onOpen = {}),
                        KitAgent("c", "capable", KitTaskState.Done, role = "Reviewer", onOpen = {}),
                    )
                )
            }
        }
    }

    /**
     * The bottom block over the transcript's recording: the voice pill (and its attention state) and the message box with
     * the limit strip inside. As in a chat, the transcript is gone under the block (KitWordsFade clears what the block's
     * glass sees from the block's top down, fading over 16 dp above it): the bright content lies above the block, the
     * block's glass sees the page's picture and bends the content above at its top rim.
     */
    @Composable
    private fun Bottom(dark: Boolean) {
        val frost = rememberKitFrostState()
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().kitFrostSource(frost).drawBehind {
                    // Gone from 16 dp above the block's top (BLOCK_TOP_DP) down, as the chat's fade leaves it.
                    clipRect(bottom = (BLOCK_TOP_DP - 16f) * density) { brightPage(dark) }
                }
            )
            CompositionLocalProvider(LocalKitLiquidChrome provides frost) {
                Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = BLOCK_TOP_DP.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    KitVoicePill("Listening", words = "Fix the login and run the tests", elapsed = "0:07", secondary = KitLineAction("Cancel", {}), primary = KitLineAction("Done", {}))
                    KitVoicePill("Let go to cancel", elapsed = "0:09", attention = true)
                    KitComposerBox(
                        running = false,
                        field = { KitText("Ask OpenCode…", maxLines = 1) },
                        above = { KitComposerLimitStrip(KitLimitNotice(reached = false, reset = KitLimitReset.Within(2, 10)), onViewUsage = {}, onDismiss = {}) },
                    )
                }
            }
        }
    }

    @Composable
    private fun Diffs() {
        val before = "val a = 1\nval b = 2\nval c = 3\nprintln(a + b)\nreturn c"
        val after = "val a = 1\nval b = 20\nval c = 3\nprintln(a * b)\nreturn c"
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            KitDiffView(DiffModel.fromBeforeAfter("Main.kt", before, after), hunkLabel = { _, _, s, e -> "Lines $s–$e" }, gapLabel = { "$it unchanged" })
            KitDiffPreviewCard(KitDiffPreview.fromPatch("@@ -1,4 +1,4 @@\n val a = 1\n-val b = 2\n+val b = 20\n val c = 3"), "Edit Main.kt", {})
            Box(Modifier.fillMaxWidth())
        }
    }

    private companion object {
        const val BLOCK_TOP_DP = 200f
        const val W = 400
        const val D = 2.625f
    }
}
