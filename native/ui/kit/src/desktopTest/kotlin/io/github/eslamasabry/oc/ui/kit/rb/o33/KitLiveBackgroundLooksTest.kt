package io.github.eslamasabry.oc.ui.kit.rb.o33

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBackground
import io.github.eslamasabry.oc.ui.kit.live.KitLiveClock
import io.github.eslamasabry.oc.ui.kit.live.kitLiveStyleById
import io.github.eslamasabry.oc.ui.kit.live.kitLiveStyleIds
import io.github.eslamasabry.oc.ui.kit.live.kitLiveStyleLabel
import io.github.eslamasabry.oc.ui.kit.live.kitLiveStyles
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The live background draws the ported looks (kit_live_background.dart): a chosen look on the page ground when
 * Background is on, its still frame under reduced motion, nothing under Saver, with Background off or in tests; a tick
 * redraws only the look's layer, never the page.
 */
@OptIn(ExperimentalTestApi::class)
class KitLiveBackgroundLooksTest {
    private var compositions = 0
    private var draws = 0

    @Composable
    private fun Page(appearance: KitAppearance, stillFrame: Boolean = false) {
        CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
            KitTheme(appearance, FakeKitPlatform(), "en", stillFrame = stillFrame) {
                KitLiveBackground(Modifier.size(412.dp, 915.dp)) {
                    SideEffect { compositions++ }
                    Box(Modifier.fillMaxSize().drawBehind { draws++ })
                }
            }
        }
    }

    private fun on(look: String, motion: MotionLevel = MotionLevel.Off, mode: AppearanceMode = AppearanceMode.Dark) =
        KitAppearance(mode = mode, motion = motion, liveBackground = true, liveStyle = look)

    /** Pixels that are not the flat ground (sampled at a corner the looks leave alone is not safe: count distinct). */
    private fun inked(img: BufferedImage): Int {
        val ground = img.getRGB(0, img.height - 1)
        var n = 0
        for (y in 0 until img.height step 3) for (x in 0 until img.width step 3) if (img.getRGB(x, y) != ground) n++
        return n
    }

    private fun shot(appearance: KitAppearance, stillFrame: Boolean = false): BufferedImage {
        var img: BufferedImage? = null
        runComposeUiTest {
            setContent { Page(appearance, stillFrame) }
            waitForIdle()
            img = onRoot().captureToImage().toAwtImage()
        }
        return img!!
    }

    @Test fun theRegistryHoldsTheLooksInPickerOrderWithDartIds() {
        assertEquals(listOf("topo", "oscilloscope", "radar", "silk"), kitLiveStyles.map { it.id })
        assertEquals(20, kitLiveStyleIds.size)
        assertEquals(kitLiveStyleIds.size, kitLiveStyleIds.toSet().size)
        for (id in kitLiveStyleIds) assertNotNull(kitLiveStyleLabel(id), id)
        assertNull(kitLiveStyleById("nope"))
        assertNull(kitLiveStyleById(null))
    }

    @Test fun aChosenLookIsDrawnAsItsStillFrameUnderReducedMotion() {
        for (look in listOf("radar", "oscilloscope", "silk", "topo")) {
            assertTrue(inked(shot(on(look))) > 50, "$look shows on the page")
        }
    }

    @Test fun offSaverAndTestsKeepTheFlatGround() {
        assertEquals(0, inked(shot(on("radar").copy(liveBackground = false))))
        assertEquals(0, inked(shot(on("radar", mode = AppearanceMode.Saver))))
        assertEquals(0, inked(shot(on("radar"), stillFrame = true)))
        // A stored look the native app does not draw yet keeps the ground.
        assertEquals(0, inked(shot(on("mesh"))))
    }

    @Test fun aTickRedrawsTheLookAndNeverThePage() = runComposeUiTest {
        compositions = 0
        draws = 0
        setContent { Page(on("radar", motion = MotionLevel.Full)) }
        waitForIdle()
        val before = onRoot().captureToImage().toAwtImage()
        val composed = compositions
        val drawn = draws
        val ticks = KitLiveClock.instance.ticks
        mainClock.advanceTimeBy(3_000)
        waitForIdle()
        val after = onRoot().captureToImage().toAwtImage()
        assertTrue(KitLiveClock.instance.ticks - ticks in 30..37, "about 12 ticks a second, never more: ${KitLiveClock.instance.ticks - ticks}")
        assertFalse(sameImage(before, after), "the look moved")
        assertEquals(composed, compositions, "the page did not recompose")
        assertEquals(drawn, draws, "the page did not redraw")
    }

    private fun sameImage(a: BufferedImage, b: BufferedImage): Boolean {
        for (y in 0 until a.height step 2) for (x in 0 until a.width step 2) if (a.getRGB(x, y) != b.getRGB(x, y)) return false
        return true
    }
}
