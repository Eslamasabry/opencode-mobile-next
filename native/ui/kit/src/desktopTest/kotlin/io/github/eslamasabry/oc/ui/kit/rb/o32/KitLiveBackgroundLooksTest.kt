package io.github.eslamasabry.oc.ui.kit.rb.o32

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.CompositionLocalProvider
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBackground
import io.github.eslamasabry.oc.ui.kit.live.KitLiveClock
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The page ground draws the chosen order-32 look (before this order a drawn look kept the page flat), moves it on the
 * shared clock without recomposing the page, holds the still frame under Calm, and hides it under Saver or with the
 * switch off.
 */
@OptIn(ExperimentalTestApi::class)
class KitLiveBackgroundLooksTest {
    private class Shots(val first: BufferedImage, val later: BufferedImage, val compositions: Int)

    private fun shoot(appearance: KitAppearance): Shots {
        KitLiveClock.instance.reset()
        var compositions = 0
        lateinit var first: BufferedImage
        lateinit var later: BufferedImage
        runDesktopComposeUiTest(412, 915) {
            mainClock.autoAdvance = false
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    KitTheme(appearance, DesktopKitPlatform(), "en", stillFrame = false) {
                        KitLiveBackground(Modifier.fillMaxSize()) {
                            SideEffect { compositions++ }
                            Box(Modifier.fillMaxSize())
                        }
                    }
                }
            }
            mainClock.advanceTimeBy(500)
            first = onNode(isRoot()).captureToImage().toAwtImage()
            val before = compositions
            mainClock.advanceTimeBy(2_000)
            later = onNode(isRoot()).captureToImage().toAwtImage()
            compositions -= before
        }
        KitLiveClock.instance.reset()
        return Shots(first, later, compositions)
    }

    private fun BufferedImage.flat(): Boolean {
        val g = getRGB(2, 2)
        for (y in 0 until height step 3) for (x in 0 until width step 3) if (getRGB(x, y) != g) return false
        return true
    }

    private fun BufferedImage.same(o: BufferedImage): Boolean {
        for (y in 0 until height) for (x in 0 until width) if (getRGB(x, y) != o.getRGB(x, y)) return false
        return true
    }

    private val on =
        KitAppearance(
            mode = AppearanceMode.Dark,
            motion = MotionLevel.Full,
            liveBackground = true,
            liveStyle = "halftone",
        )

    @Test fun aDrawnLookShowsAndMovesWithoutRecomposingThePage() {
        val s = shoot(on)
        assertTrue(!s.first.flat(), "halftone is drawn on the page ground")
        assertTrue(!s.first.same(s.later), "it moves on the clock")
        assertEquals(0, s.compositions, "a tick never recomposes the page")
    }

    @Test fun calmHoldsTheStillFrame() {
        val s = shoot(on.copy(motion = MotionLevel.Calm))
        assertTrue(!s.first.flat(), "the still frame is drawn")
        assertTrue(s.first.same(s.later), "and holds")
    }

    @Test fun offByDefaultUnderSaverAndForALookNotDrawnYet() {
        assertTrue(shoot(on.copy(liveBackground = false)).first.flat())
        assertTrue(shoot(on.copy(mode = AppearanceMode.Saver)).first.flat())
        val aurora = shoot(KitAppearance(liveBackground = true))
        assertTrue(aurora.first.flat(), "aurora is order 31's; flat until it lands")
    }

    @Test fun everyOrder32LookDraws() {
        // Flow threads is left out: its still frame is threads one point long, all but empty, as in Flutter.
        for (id in listOf(
            "mesh", "dotRipple", "glowSpill", "lightRays", "constellation", "halftone", "liquidBlobs",
            "honeycomb",
        )) {
            assertNotEquals(true, shoot(on.copy(liveStyle = id, motion = MotionLevel.Off)).first.flat(), id)
        }
    }
}
