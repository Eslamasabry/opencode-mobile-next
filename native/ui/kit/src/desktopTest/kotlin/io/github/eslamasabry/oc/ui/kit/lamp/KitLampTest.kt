package io.github.eslamasabry.oc.ui.kit.lamp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The edge light's face measured (LAMP-CRAFT §1-2, LAMP-PLAN §2 K1 tests): sizes in every style and mode, both reading
 * directions, the touch box, the label, hiding, the one halo and its haptic, and nothing running at rest.
 */
@OptIn(ExperimentalTestApi::class)
class KitLampTest {
    private val styles = listOf(Style.Solid, Style.Glass, Style.Soft, Style.Brutal, Style.Paper)

    private class Scene(face: KitLampFace, visible: Boolean = true) {
        var face by mutableStateOf(face)
        var visible by mutableStateOf(visible)
        var taps = 0
    }

    private fun ComposeUiTest.host(
        scene: Scene,
        style: Style = Style.Solid,
        dark: Boolean = true,
        locale: String = "en",
        motion: MotionLevel = MotionLevel.Full,
        platform: FakeKitPlatform = FakeKitPlatform(),
        extra: @Composable () -> Unit = {},
    ) {
        setContent {
            KitTheme(KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, style = style, motion = motion), platform, locale) {
                Box(Modifier.size(W.dp, H.dp)) {
                    KitLamp(scene.face, LABEL, { scene.taps++ }, Modifier.align(Alignment.CenterEnd), visible = scene.visible)
                    extra()
                }
            }
        }
    }

    private fun ComposeUiTest.touch(): DpRect = onNodeWithTag(LAMP_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun ComposeUiTest.face(): DpRect = onNodeWithTag(LAMP_FACE_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun near(expected: Float, actual: Float, what: String) =
        assertTrue(abs(expected - actual) < 0.6f, "$what: expected $expected dp, was $actual dp")

    private fun DpRect.w() = (right - left).value

    private fun DpRect.h() = (bottom - top).value

    private fun assertHugsEdge(touch: DpRect, face: DpRect, rtl: Boolean, what: String) {
        if (rtl) {
            near(0f, touch.left.value, "$what: the touch box starts at the left edge")
            near(touch.left.value, face.left.value, "$what: the face touches the left edge")
        } else {
            near(W.toFloat(), touch.right.value, "$what: the touch box ends at the right edge")
            near(touch.right.value, face.right.value, "$what: the face touches the right edge")
        }
    }

    @Test
    fun `B-LAMP-K1-020 face_quiet_isControlWithLabel - a 12 x 40 tab in a 24 x 56 touch box, every style, mode and direction`() {
        for (style in styles) for (dark in listOf(true, false)) for (rtl in listOf(false, true)) {
            runDesktopComposeUiTest(W, H) {
                val scene = Scene(KitLampFace())
                host(scene, style, dark, if (rtl) "ar" else "en")
                waitForIdle()
                val what = "$style ${if (dark) "dark" else "light"} ${if (rtl) "rtl" else "ltr"}"
                val t = touch()
                val f = face()
                near(24f, t.w(), "$what touch width")
                near(56f, t.h(), "$what touch height")
                near(12f, f.w(), "$what quiet face width")
                near(40f, f.h(), "$what quiet face height")
                assertHugsEdge(t, f, rtl, what)
                onNodeWithContentDescription(LABEL).performClick()
                assertEquals(1, scene.taps, "$what: the quiet tab is a control named by its label")
            }
        }
    }

    @Test
    fun `B-LAMP-K1-021 face_f2 a counted face is 16 wide and 8 + 15 per row tall in every style and direction`() {
        for (style in styles) for (dark in listOf(true, false)) for (rtl in listOf(false, true)) {
            runDesktopComposeUiTest(W, H) {
                val scene = Scene(BUSY)
                host(scene, style, dark, if (rtl) "ar" else "en")
                waitForIdle()
                val what = "$style ${if (dark) "dark" else "light"} ${if (rtl) "rtl" else "ltr"}"
                val t = touch()
                val f = face()
                near(16f, f.w(), "$what face width")
                near(68f, f.h(), "$what face height: the number and three marks")
                near(24f, t.w(), "$what touch width")
                near(84f, t.h(), "$what touch height")
                near(t.top.value + 8f, f.top.value, "$what: 8 dp of touch above the face")
                assertHugsEdge(t, f, rtl, what)
                assertTrue(f.right <= W.dp + 0.5.dp && f.left >= (-0.5).dp, "$what: the face stays inside the window")
            }
        }
    }

    @Test
    fun `B-LAMP-K1-022 the open face is the list's 16 x 48 close tab, named by its label`() =
        runDesktopComposeUiTest(W, H) {
            val scene = Scene(BUSY.copy(open = true))
            host(scene)
            waitForIdle()
            near(16f, face().w(), "open width")
            near(48f, face().h(), "open height")
            onNodeWithContentDescription(LABEL).performClick()
            assertEquals(1, scene.taps)
        }

    @Test
    fun `B-LAMP-K1-023 hidden it fades out, takes no tap and says nothing; shown again it is back`() =
        runDesktopComposeUiTest(W, H) {
            val scene = Scene(BUSY)
            host(scene)
            waitForIdle()
            onNodeWithContentDescription(LABEL).assertExists()
            scene.visible = false
            mainClock.advanceTimeBy(400)
            onNodeWithContentDescription(LABEL).assertDoesNotExist()
            scene.visible = true
            mainClock.advanceTimeBy(400)
            onNodeWithContentDescription(LABEL).performClick()
            assertEquals(1, scene.taps)
        }

    @Test
    fun `B-LAMP-K1-024 a mark arriving opens its slot on a spring at Full and at once under Off`() {
        for (motion in listOf(MotionLevel.Full, MotionLevel.Off)) {
            runDesktopComposeUiTest(W, H) {
                mainClock.autoAdvance = false
                val scene = Scene(KitLampFace(0, listOf(KitLampMark("w", KitLampMarkKind.Working, 0.4f))))
                host(scene, motion = motion)
                mainClock.advanceTimeBy(500)
                near(40f, face().h(), "$motion before")
                scene.face = KitLampFace(1, listOf(KitLampMark("w", KitLampMarkKind.Working, 0.4f), KitLampMark("n", KitLampMarkKind.NeedsYou)))
                repeat(4) { mainClock.advanceTimeByFrame() }
                val mid = face().h()
                if (motion == MotionLevel.Off) near(53f, mid, "Off: final in the next frame")
                else assertTrue(mid > 40f && mid < 53f, "Full: the slot opens on a spring (was $mid)")
                mainClock.advanceTimeBy(1_000)
                near(53f, face().h(), "$motion after")
            }
        }
    }

    @Test
    fun `B-LAMP-K1-025 needs you arriving pulses once with one Attention haptic; a burst shares it; open or hidden never pulses`() =
        runDesktopComposeUiTest(W, H) {
            mainClock.autoAdvance = false
            val platform = FakeKitPlatform()
            val scene = Scene(KitLampFace(1, listOf(KitLampMark("a", KitLampMarkKind.NeedsYou))))
            host(scene, platform = platform)
            mainClock.advanceTimeBy(500)
            assertEquals(emptyList(), platform.haptics, "what was there on the first frame does not arrive")
            scene.face = KitLampFace(2, scene.face.marks + KitLampMark("b", KitLampMarkKind.NeedsYou))
            mainClock.advanceTimeBy(500)
            assertEquals(listOf(KitHaptic.Attention), platform.haptics)
            scene.face = KitLampFace(3, scene.face.marks + KitLampMark("c", KitLampMarkKind.NeedsYou))
            mainClock.advanceTimeBy(500)
            assertEquals(listOf(KitHaptic.Attention), platform.haptics, "a burst inside 2 s gets one pulse")
            mainClock.advanceTimeBy(3_000)
            scene.face = KitLampFace(2, scene.face.marks.filter { it.key != "c" })
            mainClock.advanceTimeBy(300)
            scene.face = KitLampFace(3, scene.face.marks + KitLampMark("c", KitLampMarkKind.NeedsYou))
            mainClock.advanceTimeBy(300)
            assertEquals(1, platform.haptics.size, "never a second pulse for the same request")
            scene.face = KitLampFace(4, scene.face.marks + KitLampMark("d", KitLampMarkKind.NeedsYou), open = true)
            mainClock.advanceTimeBy(300)
            assertEquals(1, platform.haptics.size, "the open list is its own news")
            scene.face = KitLampFace(5, scene.face.marks + KitLampMark("e", KitLampMarkKind.NeedsYou))
            mainClock.advanceTimeBy(300)
            assertEquals(listOf(KitHaptic.Attention, KitHaptic.Attention), platform.haptics, "a new request on the closed light pulses again")
        }

    @Test
    fun `B-LAMP-K1-026 under Off a request keeps its haptic and draws no halo`() =
        runDesktopComposeUiTest(W, H) {
            mainClock.autoAdvance = false
            val platform = FakeKitPlatform()
            val scene = Scene(KitLampFace())
            host(scene, motion = MotionLevel.Off, platform = platform)
            mainClock.advanceTimeBy(300)
            val before = onRoot().captureToImage().toPixelMap().buffer.copyOf()
            scene.face = KitLampFace(0, emptyList())
            mainClock.advanceTimeBy(100)
            scene.face = KitLampFace(1, listOf(KitLampMark("n", KitLampMarkKind.NeedsYou)))
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            assertEquals(listOf(KitHaptic.Attention), platform.haptics)
            assertFalse(before.contentEquals(onRoot().captureToImage().toPixelMap().buffer), "the face changed to its counted state")
        }

    @Test
    fun `B-LAMP-K1-027 at rest the light requests no frames and draws the same picture`() {
        // The same check on a scene without the light: if the theme itself keeps a frame waiting, the light is not blamed.
        fun ComposeUiTest.pending(): Boolean = Recomposer.runningRecomposers.value.any { it.hasPendingWork }
        var baseline = false
        runDesktopComposeUiTest(W, H) {
            mainClock.autoAdvance = false
            setContent { KitTheme(KitAppearance(motion = MotionLevel.Full), FakeKitPlatform(), "en") { Box(Modifier.size(W.dp, H.dp)) } }
            mainClock.advanceTimeBy(3_000)
            baseline = pending()
        }
        for (style in styles) {
            runDesktopComposeUiTest(W, H) {
                mainClock.autoAdvance = false
                val scene = Scene(BUSY_UNKNOWN)
                host(scene, style)
                mainClock.advanceTimeBy(3_000)
                assertFalse(pending() && !baseline, "$style: a frame is still requested at rest")
                val a = onRoot().captureToImage().toPixelMap().buffer.copyOf()
                mainClock.advanceTimeBy(2_000)
                val b = onRoot().captureToImage().toPixelMap().buffer
                assertContentEquals(a, b, "$style: the light at rest (with a still dashed ring) drew a different frame")
            }
        }
    }

    private companion object {
        const val W = 200
        const val H = 300
        const val LABEL = "Other conversations"
        val BUSY =
            KitLampFace(
                2,
                listOf(
                    KitLampMark("w", KitLampMarkKind.Working, 0.4f),
                    KitLampMark("f", KitLampMarkKind.Failed),
                    KitLampMark("n", KitLampMarkKind.NeedsYou),
                ),
            )
        val BUSY_UNKNOWN =
            KitLampFace(
                1,
                listOf(
                    KitLampMark("f", KitLampMarkKind.Failed),
                    KitLampMark("w", KitLampMarkKind.Working, 0.6f),
                    KitLampMark("u", KitLampMarkKind.Working, null),
                ),
            )
    }
}
