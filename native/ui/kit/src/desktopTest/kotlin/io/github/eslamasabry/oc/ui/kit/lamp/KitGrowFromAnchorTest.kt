package io.github.eslamasabry.oc.ui.kit.lamp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The pane that grows out of the edge light (LAMP-CRAFT §3): its body moves from the anchor to the pane on a spring
 * while the content keeps its full size (no scaling), a change mid-flight turns it around from where it is, Off is final
 * at once, touches wait for 60 %, and a closed pane draws nothing. One px = one dp here (density 1).
 */
@OptIn(ExperimentalTestApi::class)
class KitGrowFromAnchorTest {
    private val styles = listOf(Style.Solid, Style.Glass, Style.Soft, Style.Brutal, Style.Paper)

    private class Scene(open: Boolean = false) {
        var open by mutableStateOf(open)
        var backs = 0
        var taps = 0
    }

    private fun ComposeUiTest.host(scene: Scene, motion: MotionLevel = MotionLevel.Full, style: Style = Style.Solid, dark: Boolean = true) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                KitTheme(KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, style = style, motion = motion), FakeKitPlatform(), "en") {
                    Box(Modifier.size(W.dp, H.dp)) {
                        KitGrowFromAnchor(scene.open, { ANCHOR }, { scene.backs++ }, Modifier.size(W.dp, H.dp)) {
                            Box(Modifier.fillMaxSize().clickable { scene.taps++ })
                        }
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.body(): DpRect = onNodeWithTag(GROW_BODY_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun ComposeUiTest.content(): DpRect = onNodeWithTag(GROW_CONTENT_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun DpRect.w() = (right - left).value

    private fun near(expected: Float, actual: Float, what: String, slack: Float = 1f) =
        assertTrue(abs(expected - actual) <= slack, "$what: expected $expected, was $actual")

    @Test
    fun `B-LAMP-K1-030 the body grows from the anchor to the pane while the content keeps its full size, in every style`() {
        for (style in styles) for (dark in listOf(true, false)) {
            runDesktopComposeUiTest(W, H) {
                mainClock.autoAdvance = false
                val scene = Scene()
                host(scene, style = style, dark = dark)
                mainClock.advanceTimeBy(100)
                onNodeWithTag(GROW_BODY_TAG, useUnmergedTree = true).assertDoesNotExist()
                scene.open = true
                mainClock.advanceTimeByFrame()
                mainClock.advanceTimeByFrame()
                val early = body()
                assertTrue(early.w() < W / 2f, "$style: the body starts near the anchor (width ${early.w()})")
                assertTrue(early.right.value >= ANCHOR.right - 1f, "$style: it grows out of the anchor's side")
                near(W.toFloat(), content().w(), "$style: the content is laid out at full width from the first frame")
                mainClock.advanceTimeBy(1_000)
                val open = body()
                near(0f, open.left.value, "$style: open left")
                near(W.toFloat(), open.right.value, "$style: open right")
                near(H.toFloat(), (open.bottom - open.top).value, "$style: open height")
            }
        }
    }

    @Test
    fun `B-LAMP-K1-031 a change during the open turns it around from where it is, then it folds away`() =
        runDesktopComposeUiTest(W, H) {
            mainClock.autoAdvance = false
            val scene = Scene()
            host(scene)
            mainClock.advanceTimeBy(100)
            scene.open = true
            repeat(4) { mainClock.advanceTimeByFrame() }
            val turning = body().w()
            assertTrue(turning > ANCHOR.width && turning < W - 1f, "mid-open (width $turning)")
            scene.open = false
            mainClock.advanceTimeByFrame()
            val next = body().w()
            assertTrue(abs(next - turning) < W / 4f, "no jump to either end: $turning then $next")
            mainClock.advanceTimeBy(1_000)
            onNodeWithTag(GROW_BODY_TAG, useUnmergedTree = true).assertDoesNotExist()
            assertEquals(0, scene.backs)
        }

    @Test
    fun `B-LAMP-K1-032 under Off the open and the close are final in the next frame`() =
        runDesktopComposeUiTest(W, H) {
            mainClock.autoAdvance = false
            val scene = Scene()
            host(scene, motion = MotionLevel.Off)
            mainClock.advanceTimeBy(100)
            scene.open = true
            repeat(3) { mainClock.advanceTimeByFrame() }
            near(W.toFloat(), body().w(), "Off: open at once")
            scene.open = false
            repeat(3) { mainClock.advanceTimeByFrame() }
            onNodeWithTag(GROW_BODY_TAG, useUnmergedTree = true).assertDoesNotExist()
        }

    @Test
    fun `B-LAMP-K1-033 the content takes touches only once the body is 60 percent grown`() =
        runDesktopComposeUiTest(W, H) {
            mainClock.autoAdvance = false
            val scene = Scene()
            host(scene)
            mainClock.advanceTimeBy(100)
            scene.open = true
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            onRoot().performTouchInput { click(Offset(ANCHOR.left + 4f, ANCHOR.center.y)) }
            mainClock.advanceTimeByFrame()
            assertEquals(0, scene.taps, "a tap while the body is still small reaches nothing")
            mainClock.advanceTimeBy(1_000)
            onRoot().performTouchInput { click(Offset(W / 2f, H / 2f)) }
            mainClock.advanceTimeBy(300)
            assertEquals(1, scene.taps, "open, the content answers")
        }

    @Test
    fun `B-LAMP-K1-034 under Calm the body does not travel, the pane only fades`() =
        runDesktopComposeUiTest(W, H) {
            mainClock.autoAdvance = false
            val scene = Scene()
            host(scene, motion = MotionLevel.Calm)
            mainClock.advanceTimeBy(100)
            scene.open = true
            repeat(3) { mainClock.advanceTimeByFrame() }
            near(W.toFloat(), body().w(), "Calm: the body is at the pane's size at once")
        }

    private companion object {
        const val W = 300
        const val H = 400
        /** A strip at the pane's end edge beside the light, the light's height. */
        val ANCHOR = Rect(284f, 300f, 300f, 348f)
    }
}
