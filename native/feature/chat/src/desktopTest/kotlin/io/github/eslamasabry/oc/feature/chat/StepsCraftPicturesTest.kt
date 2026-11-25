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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import io.github.eslamasabry.oc.ui.gate.flicker.KitUiOptions
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import kotlin.time.Duration.Companion.minutes
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
 * (Glass, dark, Contour lines) and in Solid dark and Solid light: folded, opened (the turn's one row and one run of
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

    /**
     * The screen is composed while true. Glass over the Contour lines keeps a ticker (its words' ink sampler) alive, which
     * fails the test's end ("uncompleted coroutines") although the picture is written: each test takes the screen away
     * before it ends.
     */
    private var alive by mutableStateOf(true)

    private fun ComposeUiTest.end() {
        alive = false
        mainClock.advanceTimeByFrame()
    }

    private fun ComposeUiTest.screen(
        look: Look,
        running: Boolean,
        arrived: () -> Boolean = { false },
    ) {
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
            if (alive) CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
                KitTheme(appearance, platform, "en", stillFrame = false) {
                    CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                        if (running) {
                            scene.Screen(
                                StepsCraftScene.runningState(),
                                facts = StepsCraftScene.runningFacts(arrived()),
                                canGoBack = true,
                                projectName = "demo_app",
                            )
                        } else {
                            scene.Screen(
                                StepsCraftScene.doneState(),
                                facts = StepsCraftScene.doneFacts(),
                                canGoBack = true,
                                projectName = "demo_app",
                            )
                        }
                    }
                }
            }
        }
    }

    private fun shot(look: Look, moment: Moment) =
        kitUiTest(WIDTH, HEIGHT, options = KitUiOptions(timeout = SCENE_TIMEOUT)) {
            mainClock.autoAdvance = false
            // Glass draws a frame in seconds on a runner without a GPU, and with the drag to the build run and that run
            // opened the Glass scene walked through some 270 frames: twelve minutes were not enough. With motion off it
            // was quick but Glass then has no lens (a flat bar, the jump pill apart from the message box), which is not
            // the look this picture is for. So in Glass the picture is the turn's row opened in place, with motion on;
            // the run brought to the middle and opened is shown by the two Solid pictures and proven by the step tests.
            val glass = look.style == Style.Glass
            screen(look, running = moment == Moment.Running)
            mainClock.advanceTimeBy(SETTLE)
            if (moment == Moment.Opened) {
                // The turn's row stays under the finger (P-010) and its steps open below it, the reply going on below.
                tap("chat-work-summary")
                mainClock.advanceTimeBy(SETTLE)
                if (!glass) {
                    // The person reads on: the build run (a build, then two adb commands) is brought to the middle of
                    // the screen, then opened there.
                    dragToMiddle("chat-step-run-c3")
                    mainClock.advanceTimeBy(SETTLE)
                    tap("chat-step-run-c3")
                    mainClock.advanceTimeBy(SETTLE)
                }
            }
            // The running step's light, part-way round its node.
            if (moment == Moment.Running) mainClock.advanceTimeBy(LIGHT_MID)
            try {
                compareGolden("steps-craft-${look.id}-${moment.name.lowercase()}")
            } finally {
                end()
            }
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
        kitUiTest(WIDTH, HEIGHT, options = KitUiOptions(timeout = SCENE_TIMEOUT)) {
            mainClock.autoAdvance = false
            screen(Look.GlassDarkContour, running = false)
            mainClock.advanceTimeBy(SETTLE)
            try {
                // The first frame is the folded row before the finger lands (the tap's own press fill is not the fold's
                // motion); the row then stays where it was tapped while its steps open below it.
                strip("steps-craft-glass-dark-contour-strip-fold-opening", foldMs, top = .40f, bottom = .88f) {
                    tap("chat-work-summary")
                }
            } finally {
                end()
            }
        }

    @Test
    fun glassDarkContourStepArrivingStrip() =
        kitUiTest(WIDTH, HEIGHT, options = KitUiOptions(timeout = SCENE_TIMEOUT)) {
            var arrived by mutableStateOf(false)
            mainClock.autoAdvance = false
            screen(Look.GlassDarkContour, running = true, arrived = { arrived })
            mainClock.advanceTimeBy(SETTLE)
            arrived = true
            // Cropped low enough that the arriving row is whole in every frame.
            try {
                strip("steps-craft-glass-dark-contour-strip-step-arriving", MotionTokens.STANDARD, top = .55f, bottom = .90f)
            } finally {
                end()
            }
        }

    /**
     * A tap on the row tagged [tag], then the pointer moves off the rows: a desktop mouse left at the tap draws a hover
     * band (on whatever row moves under it) that a phone never shows.
     */
    private fun ComposeUiTest.tap(tag: String) {
        onNodeWithTag(tag).performMouseInput { click() }
        // Off every row: the screen's end margin, half way down.
        onRoot().performMouseInput { moveTo(percentOffset(.995f, .5f)) }
    }

    /**
     * A slow drag (no fling) that brings the row tagged [tag] to the middle of the screen, as a person reading on
     * would.
     */
    private fun ComposeUiTest.dragToMiddle(tag: String) {
        val row = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val by = (row.center.y - HEIGHT / 2f)
        if (by <= 0f) return
        onNodeWithTag("transcript-list").performTouchInput {
            val start = Offset(width * .5f, height * .7f)
            down(start)
            val steps = 24
            // The touch slop is crossed first, then every move scrolls.
            repeat(steps) { moveBy(Offset(0f, -(by + SLOP) / steps), delayMillis = 16) }
            // Still for a moment before lifting: no speed is left for a fling.
            moveBy(Offset.Zero, delayMillis = 200)
            up()
        }
    }

    /**
     * Five frames of a motion [totalMs] long (0, 25, 50, 75, 100 %), side by side, each the band [top]..[bottom] of the
     * screen. [start] (a tap), when given, runs after the first frame: that frame is the moment before the motion.
     */
    private fun ComposeUiTest.strip(
        name: String,
        totalMs: Int,
        top: Float = .18f,
        bottom: Float = .80f,
        start: (() -> Unit)? = null,
    ) {
        val frames = ArrayList<BufferedImage>()
        var at = 0L
        mainClock.advanceTimeByFrame()
        if (start != null) {
            frames += onRoot().captureToImage().toAwtImage()
            start()
            mainClock.advanceTimeByFrame()
        }
        for (share in if (start != null) listOf(.25f, .5f, .75f, 1f) else listOf(0f, .25f, .5f, .75f, 1f)) {
            val target = (totalMs * share).toLong() + if (share >= 1f) 48 else 0
            if (target > at) mainClock.advanceTimeBy(target - at)
            at = maxOf(at, target)
            frames += onRoot().captureToImage().toAwtImage()
        }
        val y0 = (HEIGHT * top).toInt()
        val y1 = (HEIGHT * bottom).toInt()
        val w = WIDTH / 2
        val h = (y1 - y0) / 2
        val gap = 12
        val out = BufferedImage(frames.size * w + (frames.size - 1) * gap, h, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        frames.forEachIndexed { i, f ->
            val x = i * (w + gap)
            g.drawImage(f, x, 0, x + w, h, 0, y0, WIDTH, y1, null)
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

        /** More than the touch slop (8 dp at 2.625 px/dp), crossed before a drag scrolls. */
        const val SLOP = 24f

        /**
         * Glass over the Contour lines renders slowly on a runner without a GPU; one minute was too short for a scene, and
         * six for the opened one once it also drags the list to the build run (it recorded its picture, then ran out).
         */
        val SCENE_TIMEOUT = 12.minutes
    }
}
