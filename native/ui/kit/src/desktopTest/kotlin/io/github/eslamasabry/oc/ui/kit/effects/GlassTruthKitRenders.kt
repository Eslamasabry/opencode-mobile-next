@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.GlassLookId
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.choice.KitAgentChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitAgentGridItem
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBackground
import io.github.eslamasabry.oc.ui.kit.modal.KitSideSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoBar
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoOffer
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.kitSliverRowGroup
import io.github.eslamasabry.oc.ui.kit.state.KitSummaryCard
import io.github.eslamasabry.oc.ui.kit.teaching.KitTipBubble
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Desktop pictures for glass-truth (2026-11-16): the kit's parts that painted their own outer face (the side sheet, the
 * undo bar, the summary card, the tip, the agent choices, the lazy row group) and the plain bar and message box over
 * bright content, each with its words, in Glass (Glossy and Frosted, dark and light) and in Solid. Not a test: it runs
 * only when `GLASS_TRUTH_OUT` names a folder. Desktop draws no lens: a liquid pane is the plain stand-in (blur, hold,
 * tint, rim light; no bend).
 */
class GlassTruthKitRenders {
    private class Lens : KitPlatform by FakeKitPlatform(frost = true) {
        override val liquidGlass: Boolean = true
        private val desktop = io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform()
        override val liveShaders = desktop.liveShaders

        // The stand-in draws the hold (the knee) through desktop's shader pass, as the menu's words test does.
        override fun liquidJoinEffect(program: io.github.eslamasabry.oc.ui.kit.live.LiveShaderProgram, blurPx: Float): androidx.compose.ui.graphics.RenderEffect? =
            desktop.liquidJoinEffect(program, blurPx)

        override fun liquidPane(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, transform: () -> Float): Modifier =
            kitLiquidPlainStandIn(spec, backdrop, shape, this)
    }

    private data class Look(val name: String, val style: Style, val glass: GlassLookId = GlassLookId.Glossy)

    private val looks = listOf(Look("glossy", Style.Glass), Look("frosted", Style.Glass, GlassLookId.Frosted), Look("solid", Style.Solid))

    @Test
    fun `glass truth kit renders`() {
        val out = System.getenv("GLASS_TRUTH_OUT")?.let(::File) ?: return
        out.mkdirs()
        for (look in looks) for (dark in listOf(true, false)) {
            val mode = if (dark) "dark" else "light"
            shot(File(out, "kit-parts-${look.name}-$mode.png"), look, dark, 1000) { Parts() }
            shot(File(out, "kit-sidesheet-${look.name}-$mode.png"), look, dark, 760) { SideSheetScene() }
            if (look.style == Style.Glass) shot(File(out, "words-bar-box-${look.name}-$mode.png"), look, dark, 520) { BarAndBox(dark) }
        }
        // Item 3: the system's reduced motion (motion Off) must leave the material as chosen.
        for (dark in listOf(true, false)) {
            val mode = if (dark) "dark" else "light"
            shot(File(out, "reduced-motion-kit-parts-glossy-$mode.png"), looks[0], dark, 1000, MotionLevel.Off) { Parts() }
            shot(File(out, "reduced-motion-bar-box-glossy-$mode.png"), looks[0], dark, 520, MotionLevel.Off) { BarAndBox(dark) }
        }
    }

    private fun shot(file: File, look: Look, dark: Boolean, heightDp: Int, motion: MotionLevel = MotionLevel.Full, content: @Composable () -> Unit) {
        runDesktopComposeUiTest((W * D).toInt(), (heightDp * D).toInt()) {
            mainClock.autoAdvance = false
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    KitTheme(
                        KitAppearance(
                            mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                            motion = motion,
                            style = look.style,
                            glassLook = look.glass,
                            liveBackground = true,
                            liveStyle = "auroraImage",
                        ),
                        Lens(),
                        "en",
                        stillFrame = true,
                    ) {
                        KitLiveBackground(Modifier.fillMaxSize()) { content() }
                    }
                }
            }
            // The clock is ours (auto-advance off): a few half-second jumps, one frame each, instead of a frame
            // per 16 ms of the live background. The still frame is what is drawn either way.
            waitForIdle()
            mainClock.advanceTimeBy(500, ignoreFrameDuration = true)
            waitForIdle()
            ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", file)
        }
    }

    /** Bright content under the parts: white cards, white and black lines of "text", an orange and a green disc. */
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
    private fun Parts() {
        val page = rememberKitLiquidSource()
        val dark = io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles.current.dark
        CompositionLocalProvider(LocalKitLiquidPage provides page) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().kitLiquidSource(page).drawBehind { brightPage(dark) })
                Column(Modifier.fillMaxSize().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    KitSummaryCard(
                        kicker = "Needs you",
                        title = "Allow the agent to run the tests?",
                        attention = true,
                        supporting = "From the conversation \"Fix the login\"",
                        detail = "./gradlew :app:test",
                    )
                    Row(Modifier.padding(horizontal = 16.dp).height(110.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KitAgentChoice(KitAgentGridItem("OpenCode", KitIcons.terminal, {}, selected = true), Modifier.weight(1f), wide = true)
                        KitAgentChoice(KitAgentGridItem("Claude", KitIcons.terminal, {}), Modifier.weight(1f), wide = true)
                    }
                    LazyColumn(Modifier.fillMaxWidth().height(200.dp)) {
                        kitSliverRowGroup(
                            children = emptyList(),
                            itemCount = 3,
                            label = "Recent",
                            itemContent = { i -> KitRow(listOf("Fix the login", "Glass audit", "Release notes")[i], supporting = "2 min ago", onClick = {}) },
                        )
                    }
                    Box(Modifier.padding(horizontal = 16.dp)) { KitTipBubble("Hold a conversation to pin it, rename it or move it.") }
                    KitUndoBar(KitUndoOffer("u", "Conversation moved to the archive"), onUndo = {}, onTimeout = {})
                }
            }
        }
    }

    @Composable
    private fun SideSheetScene() {
        val dark = io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles.current.dark
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().drawBehind { brightPage(dark) })
            KitSideSheet(visible = true, onDismissRequest = {}, widthDp = 300) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KitText("Details", role = TypeRole.Title)
                    KitText("The side sheet holds reading content: words that must read over the page.", role = TypeRole.Body)
                    KitText("Second line in secondary ink", role = TypeRole.Secondary, tone = TextTone.Secondary)
                }
            }
        }
    }

    /** Item 2: the plain bar and the plain message box (not joined) over bright content, with their words. */
    @Composable
    private fun BarAndBox(dark: Boolean) {
        val frost = rememberKitFrostState()
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().kitFrostSource(frost).drawBehind { brightPage(dark) })
            Column(Modifier.fillMaxSize().padding(vertical = 40.dp), verticalArrangement = Arrangement.SpaceBetween) {
                val bar = kitLiquidFace(LiquidRole.Bar, frost, LiquidBarShape)
                Box(Modifier.fillMaxWidth().height(64.dp).then(kitLiquidWordsHost(LiquidRole.Bar, frost))) {
                    if (bar != null) KitLiquidBarPane(bar)
                    Column(Modifier.align(Alignment.CenterStart).padding(start = 24.dp)) {
                        KitText("Fix the login", role = TypeRole.Title, maxLines = 1)
                        KitText("oc_app · 3 min ago", role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = 1)
                    }
                }
                val shape = RoundedCornerShape(26.dp)
                val box = kitLiquidFace(LiquidRole.Composer, frost, shape)
                Box(
                    Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(96.dp)
                        .then(box ?: Modifier.background(Color.Magenta, shape))
                        .padding(18.dp)
                ) {
                    Column {
                        KitText("Ask OpenCode…", role = TypeRole.Body, tone = TextTone.Secondary, maxLines = 1)
                        KitText("Steer while it works", role = TypeRole.Body, maxLines = 1)
                    }
                }
            }
        }
    }

    private companion object {
        const val W = 400
        const val D = 2.625f
    }
}
