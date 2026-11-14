package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.chat.E20Goldens.compareGolden
import io.github.eslamasabry.oc.feature.chat.rb.o42.ChatScene
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidBackdrop
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidPaneSpec
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.tool.STEP_STAGGER_MAX
import io.github.eslamasabry.oc.ui.kitchat.tool.STEP_STAGGER_MS
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.fail

/**
 * Pictures for the owner (2026-11-14, the steps of a long turn): the owner's turn of thirteen steps, 11 commands and 2
 * other steps with the agent's words between them, at phone size (412 x 915 dp at 2.625 px/dp), in the owner's look
 * (Glass, dark, Contour lines) and in Solid dark and Solid light: folded, opened (the earlier steps and one run of
 * commands open), and while its last command runs (the step light mid-lap). Two frame strips in the owner's look: the
 * fold opening and a new step arriving, five frames each at 0, 25, 50, 75 and 100 % of the motion.
 */
@OptIn(ExperimentalTestApi::class)
class StepsCraftPicturesTest {
    /** Desktop with the lens: a liquid pane draws what it reads blurred by its recipe's blur, under its tint. */
    private class LensPlatform : KitPlatform by DesktopKitPlatform() {
        override val liquidGlass: Boolean = true

        override fun liquidPane(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, transform: () -> Float): Modifier {
            val at = arrayOfNulls<LayoutCoordinates>(1)
            val draw = backdropDraw(backdrop)
            return Modifier.onGloballyPositioned { at[0] = it }
                .clip(shape)
                .drawWithCache {
                    val layer = obtainGraphicsLayer()
                    val r = spec.recipe.blurDp.dp.toPx()
                    onDrawBehind {
                        val c = at[0] ?: return@onDrawBehind
                        layer.renderEffect = BlurEffect(r, r)
                        layer.record { draw(this, c) }
                        drawLayer(layer)
                        drawRect(spec.fill)
                    }
                }
        }

        @Suppress("UNCHECKED_CAST")
        private fun backdropDraw(backdrop: KitLiquidBackdrop): (DrawScope, LayoutCoordinates) -> Unit {
            val getter = KitLiquidBackdrop::class.java.methods.first { it.name.startsWith("getDraw") && it.parameterCount == 0 }
            return getter.invoke(backdrop) as (DrawScope, LayoutCoordinates) -> Unit
        }
    }

    private enum class Look(val id: String, val style: Style, val dark: Boolean, val live: String?) {
        GlassDarkContour("glass-dark-contour", Style.Glass, true, "topo"),
        SolidDark("solid-dark", Style.Solid, true, null),
        SolidLight("solid-light", Style.Solid, false, null),
    }

    private enum class Moment { Folded, Opened, Running }

    private fun ComposeUiTest.screen(look: Look, running: Boolean, arrived: () -> Boolean = { false }) {
        val scene = ChatScene()
        val appearance =
            KitAppearance(
                style = look.style,
                mode = if (look.dark) AppearanceMode.Dark else AppearanceMode.Light,
                motion = MotionLevel.Full,
                liveBackground = look.live != null,
                liveStyle = look.live ?: "aurora",
            )
        val platform = if (look.style == Style.Glass) LensPlatform() else DesktopKitPlatform()
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
                KitTheme(appearance, platform, "en", stillFrame = false) {
                    CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                        if (running) scene.Screen(StepsCraftScene.runningState(), facts = StepsCraftScene.runningFacts(arrived()), projectName = "demo_app")
                        else scene.Screen(StepsCraftScene.doneState(), facts = StepsCraftScene.doneFacts(), projectName = "demo_app")
                    }
                }
            }
        }
    }

    private fun shot(look: Look, moment: Moment) =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            mainClock.autoAdvance = false
            screen(look, running = moment == Moment.Running)
            mainClock.advanceTimeBy(SETTLE)
            if (moment == Moment.Opened) {
                onNodeWithText("Show 9 earlier steps").performClick()
                mainClock.advanceTimeBy(SETTLE)
                onAllNodesWithText("Ran 2 commands").onLast().performClick()
                mainClock.advanceTimeBy(SETTLE)
            }
            // The running step's light, part-way round its node.
            if (moment == Moment.Running) mainClock.advanceTimeBy(LIGHT_MID)
            compareGolden("steps-craft-${look.id}-${moment.name.lowercase()}")
        }

    @Test fun glassDarkContourFolded() = shot(Look.GlassDarkContour, Moment.Folded)

    @Test fun glassDarkContourOpened() = shot(Look.GlassDarkContour, Moment.Opened)

    @Test fun glassDarkContourRunning() = shot(Look.GlassDarkContour, Moment.Running)

    @Test fun solidDarkFolded() = shot(Look.SolidDark, Moment.Folded)

    @Test fun solidDarkOpened() = shot(Look.SolidDark, Moment.Opened)

    @Test fun solidDarkRunning() = shot(Look.SolidDark, Moment.Running)

    @Test fun solidLightFolded() = shot(Look.SolidLight, Moment.Folded)

    @Test fun solidLightOpened() = shot(Look.SolidLight, Moment.Opened)

    @Test fun solidLightRunning() = shot(Look.SolidLight, Moment.Running)

    /** The fold's whole motion: STANDARD, the last row starting [STEP_STAGGER_MAX] staggers late. */
    private val foldMs = MotionTokens.STANDARD + STEP_STAGGER_MS * STEP_STAGGER_MAX

    @Test
    fun glassDarkContourFoldOpeningStrip() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            mainClock.autoAdvance = false
            screen(Look.GlassDarkContour, running = false)
            mainClock.advanceTimeBy(SETTLE)
            onNodeWithText("Show 9 earlier steps").performClick()
            strip("steps-craft-glass-dark-contour-strip-fold-opening", foldMs)
        }

    @Test
    fun glassDarkContourStepArrivingStrip() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            var arrived by mutableStateOf(false)
            mainClock.autoAdvance = false
            screen(Look.GlassDarkContour, running = true, arrived = { arrived })
            mainClock.advanceTimeBy(SETTLE)
            arrived = true
            strip("steps-craft-glass-dark-contour-strip-step-arriving", MotionTokens.STANDARD)
        }

    /** Five frames of a motion [totalMs] long (0, 25, 50, 75, 100 %), side by side, each the middle of the screen. */
    private fun ComposeUiTest.strip(name: String, totalMs: Int) {
        val frames = ArrayList<BufferedImage>()
        var at = 0L
        mainClock.advanceTimeByFrame()
        for (share in listOf(0f, .25f, .5f, .75f, 1f)) {
            val target = (totalMs * share).toLong() + if (share >= 1f) 48 else 0
            if (target > at) mainClock.advanceTimeBy(target - at)
            at = maxOf(at, target)
            frames += onRoot().captureToImage().toAwtImage()
        }
        val top = (HEIGHT * .18f).toInt()
        val bottom = (HEIGHT * .80f).toInt()
        val w = WIDTH / 2
        val h = (bottom - top) / 2
        val gap = 12
        val out = BufferedImage(frames.size * w + (frames.size - 1) * gap, h, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        frames.forEachIndexed { i, f ->
            val x = i * (w + gap)
            g.drawImage(f, x, 0, x + w, h, 0, top, WIDTH, bottom, null)
        }
        g.dispose()
        val dir = File(System.getProperty("oc.goldens.dir") ?: "src/desktopTest/goldens")
        val file = File(dir, "$name.png")
        val record = System.getProperty("oc.goldens.record") == "true"
        if (record || !file.isFile) {
            file.parentFile.mkdirs()
            ImageIO.write(out, "png", file)
            if (!record) fail("$name: no picture yet; recorded ${file.name}. Look at it, then run again.")
        }
    }

    private companion object {
        const val DENSITY = 2.625f
        const val WIDTH = 1081
        const val HEIGHT = 2402
        const val SETTLE = 900L
        const val LIGHT_MID = 1100L
    }
}
