package io.github.eslamasabry.oc.ui.kit.rb.o31

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBackground
import io.github.eslamasabry.oc.ui.kit.live.KitLiveClock
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * KitLiveBackground draws the chosen look (lib/ui/kit/kit_live_background.dart): before order 31 the native page kept
 * its flat ground for every look but the painted aurora. Off by default; Saver hides it; Calm and Off show the still
 * frame and leave the shared clock alone; Full motion moves it on the clock.
 */
@OptIn(ExperimentalTestApi::class)
class KitLiveBackgroundWiringTest {
    private class Shot(val pixels: IntArray, val ground: Int, val usersWhileShown: Int)

    private fun shot(appearance: KitAppearance): Shot {
        var out: Shot? = null
        runDesktopComposeUiTest(200, 400) {
            mainClock.autoAdvance = false
            setContent {
                KitTheme(appearance = appearance, platform = DesktopKitPlatform(), localeTag = "en", stillFrame = false) {
                    KitLiveBackground(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) }
                }
            }
            mainClock.advanceTimeBy(500)
            val map = onRoot().captureToImage().toPixelMap()
            val px = IntArray(map.width * map.height) { argbOf(map[it % map.width, it / map.width]) }
            out = Shot(px, groundOf(appearance), KitLiveClock.instance.users)
        }
        return out!!
    }

    private fun argbOf(c: androidx.compose.ui.graphics.Color): Int =
        ((c.alpha * 255 + .5f).toInt() shl 24) or ((c.red * 255 + .5f).toInt() shl 16) or ((c.green * 255 + .5f).toInt() shl 8) or (c.blue * 255 + .5f).toInt()

    private fun groundOf(a: KitAppearance): Int {
        val dark = a.mode != AppearanceMode.Light
        val g = livePalette(dark).ground
        return argbOf(g)
    }

    private fun painted(s: Shot) = s.pixels.count { it != s.ground }

    private val caustics = KitAppearance(mode = AppearanceMode.Dark, liveBackground = true, liveStyle = "caustics")

    @Test fun aChosenDrawnLookPaintsThePage() {
        val s = shot(caustics.copy(motion = MotionLevel.Off))
        assertTrue(painted(s) > s.pixels.size / 2, "caustics covers the page (${painted(s)} of ${s.pixels.size} pixels)")
    }

    @Test fun offByDefaultAndWhenSwitchedOff() {
        assertEquals(0, painted(shot(KitAppearance(mode = AppearanceMode.Dark))), "default appearance: the plain ground")
        assertEquals(0, painted(shot(caustics.copy(liveBackground = false, motion = MotionLevel.Off))))
    }

    @Test fun saverHidesIt() {
        val s = shot(caustics.copy(mode = AppearanceMode.Saver, motion = MotionLevel.Off))
        assertEquals(1, s.pixels.toSet().size, "Saver: one flat colour (its true-black ground), no look")
    }

    @Test fun calmAndOffShowTheStillFrameWithoutTheClock() {
        for (motion in listOf(MotionLevel.Off, MotionLevel.Calm)) {
            val a = shot(caustics.copy(motion = motion))
            val b = shot(caustics.copy(motion = motion))
            assertTrue(a.pixels.contentEquals(b.pixels), "$motion: one still frame")
            assertEquals(0, a.usersWhileShown, "$motion: the shared clock is not attached")
            assertTrue(painted(a) > 0)
        }
    }

    @Test fun fullMotionMovesOnTheSharedClock() {
        val s = shot(caustics.copy(motion = MotionLevel.Full))
        assertEquals(1, s.usersWhileShown, "the shown look attaches the shared clock")
        assertTrue(painted(s) > 0)
        assertEquals(0, KitLiveClock.instance.users, "leaving the page detaches it")
    }

    @Test fun aSchoolsCapStillShowsTheLook() {
        val s = shot(caustics.copy(style = Style.Brutal, motion = MotionLevel.Off))
        assertTrue(painted(s) > 0, "Brutal caps the strength at .7 but keeps the look")
    }
}
