@file:OptIn(ExperimentalTestApi::class)
@file:Suppress("DEPRECATION")

package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.kit.action.KitIconButtonCopy
import io.github.eslamasabry.oc.ui.kit.foundation.kitContentPress
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.MenuRules
import io.github.eslamasabry.oc.ui.kit.motion.KitEntrance
import io.github.eslamasabry.oc.ui.kit.motion.KitSwap
import io.github.eslamasabry.oc.ui.kit.motion.KitSwapGrow
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S10 motion kit (audit of native 5ff3ac113 against the Flutter app): menus fade out (M-004), the copied check
 * cross-fades and grows from 0.9 with the copy haptic (M-015), a held message shows its press (M-018), a theme change
 * fades the old screen over the new one (M-019) and a late part rises 6 dp (M-021). Each test drives the test clock
 * by hand and checks a frame in the middle of the move and the reduced-motion path.
 */
class S10MotionKitTest {
    private val items = listOf(KitMenuItem("Rename", {}), KitMenuItem("Delete", {}, destructive = true))

    // ---- M-004 ----

    @Test
    fun `M-004 the menu's popup stays while it is open or fading, and leaves once the fade is done`() {
        assertTrue(MenuRules.composed(current = false, target = true), "opening")
        assertTrue(MenuRules.composed(current = true, target = true), "open")
        assertTrue(MenuRules.composed(current = true, target = false), "closing: the fade still runs")
        assertTrue(!MenuRules.composed(current = false, target = false), "closed")
    }

    @Test
    fun `M-004 a closing menu fades out over QUICK instead of vanishing on the frame it closes`() =
        runDesktopComposeUiTest(400, 800) {
            var expanded by mutableStateOf(true)
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Full), FakeKitPlatform(), "en") {
                    KitMenu(expanded, items, { expanded = false }) { Box(Modifier.size(48.dp)) }
                }
            }
            waitForIdle()
            onNodeWithText("Rename", useUnmergedTree = true).assertExists()
            mainClock.autoAdvance = false
            expanded = false
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeBy(MotionTokens.QUICK / 2L)
            onNodeWithText("Rename", useUnmergedTree = true).assertExists()
            mainClock.advanceTimeBy(MotionTokens.QUICK * 2L)
            onNodeWithText("Rename", useUnmergedTree = true).assertDoesNotExist()
        }

    @Test
    fun `M-004 with Animations Off the menu leaves at once`() = runDesktopComposeUiTest(400, 800) {
        var expanded by mutableStateOf(true)
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en") {
                KitMenu(expanded, items, { expanded = false }) { Box(Modifier.size(48.dp)) }
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        expanded = false
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        onNodeWithText("Rename", useUnmergedTree = true).assertDoesNotExist()
    }

    // ---- M-015 ----

    /** The width, in px, of the drawn (non-ground) run across the middle row of [tag]'s root capture. */
    private fun androidx.compose.ui.test.ComposeUiTest.inkWidth(ground: Color): Int {
        val map = onRoot().captureToImage().toPixelMap()
        val y = map.height / 2
        return (0 until map.width).count { x -> map[x, y] != ground }
    }

    @Test
    fun `M-015 the copied check grows from 0_9 as it fades in at Full, fades only under Calm, at once under Off`() {
        for (level in MotionLevel.entries) runComposeUiTest {
            var copied by mutableStateOf(false)
            var ground = Color.Unspecified
            setContent {
                KitTheme(KitAppearance(motion = level), FakeKitPlatform(), "en") {
                    CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides Density(1f)) {
                        ground = LocalKitColors.current.ground
                        Box(Modifier.size(240.dp).background(ground)) {
                            KitSwapGrow(copied, grows = { it }) { now ->
                                if (now) Box(Modifier.size(200.dp).background(Color.Red))
                                else Box(Modifier.size(0.dp))
                            }
                        }
                    }
                }
            }
            waitForIdle()
            mainClock.autoAdvance = false
            copied = true
            // Frame 1 sees the change, frame 2 starts the swap's clock, frame 3 is its first moving frame.
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            val early = inkWidth(ground)
            mainClock.advanceTimeBy(MotionTokens.QUICK * 3L)
            val done = inkWidth(ground)
            assertEquals(200, done, "$level: the check ends at full size")
            when (level) {
                MotionLevel.Full -> assertTrue(early in 180..199, "Full: it starts near 0.9 (was $early px)")
                MotionLevel.Calm -> assertEquals(200, early, "Calm: fade only, no growth")
                MotionLevel.Off -> assertEquals(200, early, "Off: at once")
            }
        }
    }

    @Test
    fun `M-015 the swapped-in check is part-faded half way, never popped in`() = runComposeUiTest {
        var copied by mutableStateOf(false)
        var ground = Color.Unspecified
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Calm), FakeKitPlatform(), "en") {
                ground = LocalKitColors.current.ground
                Box(Modifier.size(100.dp).background(ground)) {
                    KitSwap(copied) { now ->
                        if (now) Box(Modifier.size(100.dp).background(Color.Red)) else Box(Modifier)
                    }
                }
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        copied = true
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(MotionTokens.QUICK / 2L)
        val mid = onRoot().captureToImage().toPixelMap().let { it[it.width / 2, it.height / 2] }
        assertTrue(mid != ground && abs(mid.red - 1f) > 0.02f, "half way the check is part-faded, not popped in ($mid)")
    }

    @Test
    fun `M-015 copying plays the light copy haptic once and shows the check for the copied hold`() = runComposeUiTest {
        val platform = FakeKitPlatform()
        val clipboard =
            object : ClipboardManager {
                var text: String? = null

                override fun getText(): AnnotatedString? = text?.let(::AnnotatedString)

                override fun setText(annotatedString: AnnotatedString) {
                    text = annotatedString.text
                }
            }
        setContent {
            KitTheme(KitAppearance(), platform, "en") {
                CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                    KitIconButtonCopy(text = { "git status" }, label = "Copy command")
                }
            }
        }
        onNodeWithContentDescription("Copy command").performClick()
        waitForIdle()
        assertEquals(listOf(KitHaptic.Send), platform.haptics)
        assertEquals("git status", clipboard.text)
    }

    // ---- M-018 ----

    @Test
    fun `M-018 holding reading content shows the press from the start of the hold, inside its own shape`() {
        for (level in listOf(MotionLevel.Full, MotionLevel.Off)) runComposeUiTest {
            val platform = FakeKitPlatform()
            var opened = 0
            var ground = Color.Unspecified
            setContent {
                KitTheme(KitAppearance(motion = level), platform, "en") {
                    CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides Density(1f)) {
                        ground = LocalKitColors.current.ground
                        Box(Modifier.size(120.dp).background(ground)) {
                            Box(
                                Modifier.size(120.dp)
                                    .testTag("bubble")
                                    .kitContentPress("Message actions", { opened++ }, { opened++ }, CircleShape)
                            )
                        }
                    }
                }
            }
            waitForIdle()
            mainClock.autoAdvance = false
            onNodeWithTag("bubble").performTouchInput { down(center) }
            mainClock.advanceTimeBy(250)
            val held = onRoot().captureToImage().toPixelMap()
            assertTrue(held[60, 60] != ground, "$level: the press shows during the hold, before the menu opens")
            assertEquals(ground, held[1, 1], "$level: the press keeps to the bubble's outline")
            assertEquals(0, opened, "$level: nothing opens before the hold completes")
            mainClock.advanceTimeBy(600)
            onNodeWithTag("bubble").performTouchInput { up() }
            mainClock.advanceTimeBy(200)
            assertEquals(1, opened, "$level: the hold opens the actions once")
            assertEquals(listOf(KitHaptic.LongPress), platform.haptics, "$level: one long-press haptic")
        }
    }

    // ---- M-019 ----

    @Test
    fun `M-019 a theme change fades the old screen over the new colours, words never pass through the midpoint`() =
        runComposeUiTest {
            var mode by mutableStateOf(AppearanceMode.Dark)
            val seen = mutableListOf<Color>()
            setContent {
                KitTheme(KitAppearance(mode = mode, motion = MotionLevel.Full), FakeKitPlatform(), "en") {
                    val ground = LocalKitColors.current.ground
                    seen += ground
                    Box(Modifier.size(64.dp).background(ground))
                }
            }
            waitForIdle()
            val dark = seen.last()
            mainClock.autoAdvance = false
            mode = AppearanceMode.Light
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeBy(ThemeFadeMs / 2L)
            val light = seen.last()
            val mid = onRoot().captureToImage().toPixelMap().let { it[it.width / 2, it.height / 2] }
            assertTrue(mid != dark && mid != light, "half way the old screen is part-faded over the new ($mid)")
            mainClock.advanceTimeBy(ThemeFadeMs * 2L)
            val end = onRoot().captureToImage().toPixelMap().let { it[it.width / 2, it.height / 2] }
            assertEquals(light, end, "after the fade only the new theme shows")
            assertTrue(seen.all { it == dark || it == light }, "the live colours jump; only the picture fades")
        }

    @Test
    fun `M-019 the fade's 200 ms start after its first frame, so a slow first frame does not eat them`() =
        runComposeUiTest {
            var mode by mutableStateOf(AppearanceMode.Dark)
            var ground = Color.Unspecified
            setContent {
                KitTheme(KitAppearance(mode = mode, motion = MotionLevel.Full), FakeKitPlatform(), "en") {
                    ground = LocalKitColors.current.ground
                    Box(Modifier.size(64.dp).background(ground))
                }
            }
            waitForIdle()
            val dark = ground
            mainClock.autoAdvance = false
            mode = AppearanceMode.Light
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            val first = onRoot().captureToImage().toPixelMap().let { it[it.width / 2, it.height / 2] }
            assertEquals(dark, first, "the old screen still shows in full until the clock starts")
        }

    @Test
    fun `M-019 with motion off a theme change shows on the next frame`() = runComposeUiTest {
        var mode by mutableStateOf(AppearanceMode.Dark)
        var ground = Color.Unspecified
        setContent {
            KitTheme(KitAppearance(mode = mode, motion = MotionLevel.Off), FakeKitPlatform(), "en") {
                ground = LocalKitColors.current.ground
                Box(Modifier.size(64.dp).background(ground))
            }
        }
        waitForIdle()
        val dark = ground
        mainClock.autoAdvance = false
        mode = AppearanceMode.Light
        mainClock.advanceTimeByFrame()
        val now = onRoot().captureToImage().toPixelMap().let { it[it.width / 2, it.height / 2] }
        assertTrue(now != dark, "Off: no fade")
        assertEquals(ground, now)
    }

    // ---- M-021 ----

    @Test
    fun `M-021 a late part rises 6 dp into place, as Flutter's KitEntrance`() {
        assertEquals(6, MotionTokens.ENTRANCE_RISE_DP)
        runComposeUiTest {
            var ground = Color.Unspecified
            var shown by mutableStateOf(false)
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Full), FakeKitPlatform(), "en") {
                    CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides Density(1f)) {
                        ground = LocalKitColors.current.ground
                        Box(Modifier.size(80.dp).background(ground)) {
                            if (shown) KitEntrance { Box(Modifier.size(80.dp).background(Color.Red)) }
                        }
                    }
                }
            }
            waitForIdle()
            mainClock.autoAdvance = false
            shown = true
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            val map = onRoot().captureToImage().toPixelMap()
            val top = (0 until map.height).first { y -> map[40, y] != ground }
            assertTrue(top in 1..6, "it starts at most 6 px below its rest (was $top)")
        }
    }

    private companion object {
        const val ThemeFadeMs = 200
    }
}
