package io.github.eslamasabry.oc.ui.kit.lamp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.test.onAllNodesWithText
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
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.geometry.Offset
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
    fun `B-LAMP-K1-020 face_quiet_isControlWithLabel - a 12 x 40 tab in a gutter-wide (16) x 48 touch box, every style, mode and direction`() {
        for (style in styles) for (dark in listOf(true, false)) for (rtl in listOf(false, true)) {
            runDesktopComposeUiTest(W, H) {
                val scene = Scene(KitLampFace())
                host(scene, style, dark, if (rtl) "ar" else "en")
                waitForIdle()
                val what = "$style ${if (dark) "dark" else "light"} ${if (rtl) "rtl" else "ltr"}"
                val t = touch()
                val f = face()
                near(16f, t.w(), "$what touch width: the page gutter")
                near(48f, t.h(), "$what touch height")
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
                near(16f, t.w(), "$what touch width: the page gutter")
                near(68f, t.h(), "$what touch height: the mark's own")
                near(t.top.value, f.top.value, "$what: the box is the mark's height")
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
    fun `B-LAMP-K1-023 hidden it fades out, takes no tap and says nothing, and shown again it is back`() =
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
    fun `B-LAMP-K1-025 needs you arriving pulses once with one Attention haptic, a burst shares it, open or hidden never pulses`() =
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

    /** A button flush at the content's edge (the gutter, 16 dp, in from the window's edge) beside the light. */
    private class Behind {
        var button = 0
        var light = 0
    }

    private fun ComposeUiTest.hostBehind(
        behind: Behind,
        locale: String = "en",
        side: KitLampSide = KitLampSide.End,
        inset: Dp = 0.dp,
        fontScale: Float = 1f,
        face: KitLampFace = KitLampFace(),
    ) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                KitTheme(KitAppearance(mode = AppearanceMode.Dark, style = Style.Solid, motion = MotionLevel.Full), FakeKitPlatform(), locale) {
                    val left = KitLampRules.onLeft(side, locale == "ar")
                    Box(Modifier.size(W.dp, H.dp)) {
                        // The page's content begins at the gutter: its edge button ends 16 dp (plus the inset) in from the window's edge.
                        Box(
                            Modifier.size(48.dp).align(if (left) Alignment.CenterStart else Alignment.CenterEnd)
                                .padding(if (left) PaddingValues(start = 16.dp + inset) else PaddingValues(end = 16.dp + inset))
                                .clickable { behind.button++ },
                        )
                        KitLamp(face, LABEL, { behind.light++ }, Modifier.align(if (left) Alignment.CenterStart else Alignment.CenterEnd), side = side, sideInset = inset)
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.tapAt(xDp: Float, yDp: Float) {
        onRoot().performTouchInput { click(Offset(xDp * density, yDp * density)) }
    }

    @Test
    fun `B-LAMP-009 a button flush at the content edge, beside the light, gets its taps and the light takes none of them`() =
        runDesktopComposeUiTest(W, H) {
            val behind = Behind()
            hostBehind(behind)
            waitForIdle()
            // The button's ink spans 40..? left of the gutter: its right end is at W - 16; tap just inside it, and at its middle.
            tapAt(W - 17f, H / 2f)
            tapAt(W - 40f, H / 2f)
            tapAt(W - 17f, H / 2f - 20f)
            assertEquals(3, behind.button, "the button at the content's edge is tapped")
            assertEquals(0, behind.light, "the light took no tap that is not in its gutter box")
        }

    @Test
    fun `B-LAMP-010 a tap anywhere in the gutter's 48 dp box opens the light, and none outside it`() =
        runDesktopComposeUiTest(W, H) {
            val behind = Behind()
            hostBehind(behind)
            waitForIdle()
            tapAt(W - 8f, H / 2f)
            tapAt(W - 2f, H / 2f - 22f)
            tapAt(W - 14f, H / 2f + 22f)
            assertEquals(3, behind.light, "inside the 16 x 48 box (the mark is 12 x 40): every tap opens it")
            tapAt(W - 8f, H / 2f - 27f)
            assertEquals(3, behind.light, "above the box nothing opens")
            assertEquals(0, behind.button)
        }

    @Test
    fun `B-LAMP-010 right to left the box mirrors to the left edge`() =
        runDesktopComposeUiTest(W, H) {
            val behind = Behind()
            hostBehind(behind, locale = "ar")
            waitForIdle()
            tapAt(8f, H / 2f)
            tapAt(14f, H / 2f - 22f)
            assertEquals(2, behind.light)
            tapAt(W - 8f, H / 2f)
            assertEquals(2, behind.light, "the right edge is not the light's in Arabic")
            tapAt(17f, H / 2f)
            assertEquals(1, behind.button, "the button flush at the content edge keeps its taps")
            assertEquals(2, behind.light)
        }

    @Test
    fun `B-LAMP-010 the box follows the side the light is moved to`() =
        runDesktopComposeUiTest(W, H) {
            val behind = Behind()
            hostBehind(behind, side = KitLampSide.Start)
            waitForIdle()
            tapAt(8f, H / 2f)
            assertEquals(1, behind.light, "moved to the other side, the box is on that side")
            tapAt(W - 8f, H / 2f)
            assertEquals(1, behind.light)
        }

    @Test
    fun `B-LAMP-010 a side inset widens the box to the content edge and no further`() =
        runDesktopComposeUiTest(W, H) {
            val behind = Behind()
            hostBehind(behind, inset = 10.dp)
            waitForIdle()
            tapAt(W - 24f, H / 2f)
            assertEquals(1, behind.light, "inside gutter plus inset (26 dp) opens it")
            tapAt(W - 27f, H / 2f)
            assertEquals(1, behind.button, "the content's edge button (inset 10 + gutter 16 in) takes its tap")
            assertEquals(1, behind.light)
        }

    @Test
    fun `B-LAMP-010 the widest mark, 9+ at text 1_3, stays inside the gutter`() =
        runDesktopComposeUiTest(W, H) {
            val behind = Behind()
            val many = KitLampFace(12, listOf(KitLampMark("n", KitLampMarkKind.NeedsYou), KitLampMark("f", KitLampMarkKind.Failed), KitLampMark("w", KitLampMarkKind.Working, 0.4f)))
            hostBehind(behind, fontScale = 1.3f, face = many)
            waitForIdle()
            val t = touch()
            val f = face()
            near(16f, t.w(), "the box is the gutter")
            assertTrue(f.w() <= 16.5f, "the mark (${f.w()} dp) is not wider than the gutter")
            assertTrue(f.left.value >= t.left.value - 0.5f && f.right.value <= t.right.value + 0.5f, "the mark is inside the box")
            val nine = onAllNodesWithText("9", substring = true, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue(nine.isNotEmpty(), "the number is drawn")
            for (n in nine) {
                val b = n.boundsInRoot
                assertTrue(b.left >= f.left.value + 2f && b.right <= f.right.value - 2f, "the number $b keeps 2 dp clear of both sides of the mark")
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
