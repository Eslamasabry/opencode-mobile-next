package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.navigationevent.NavigationEvent
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Plan 08 §5.4 frame captures (`grow_open_*`, `grow_return_*`, `predictive_*`), rendered headless with Skia on the
 * desktop JVM at a phone-sized window. Written to build/p2-frames for review; the asserts only check that each frame
 * was produced. These are evidence, not goldens (Roborazzi goldens are P7).
 */
@OptIn(ExperimentalTestApi::class)
class FrameCaptureTest {
    private val out = File(System.getProperty("user.dir"), "build/p2-frames").apply { mkdirs() }

    private fun ComposeUiTest.capture(name: String) {
        val file = File(out, "$name.png")
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
        assertTrue(file.length() > 0, name)
    }

    @Test
    fun `B-KIT-054 grow open and button return frames`() = runDesktopComposeUiTest(W, H) {
        val h = Harness()
        setContent { h.Content() }
        waitForIdle()
        mainClock.autoAdvance = false
        onNodeWithText("Row 3").performClick()
        var t = 0L
        for (at in listOf(0L, 90L, 180L, 270L, 360L, 450L)) {
            mainClock.advanceTimeBy(at - t)
            t = at
            capture("grow_open_${at}ms")
        }
        mainClock.autoAdvance = true
        waitForIdle()
        mainClock.autoAdvance = false
        onNodeWithContentDescription("Back").performClick()
        t = 0L
        for (at in listOf(0L, 60L, 120L, 240L, 300L)) {
            mainClock.advanceTimeBy(at - t)
            t = at
            capture("grow_return_${at}ms")
        }
    }

    private fun predictive(rtl: Boolean) = runDesktopComposeUiTest(W, H) {
        val h = Harness()
        setContent { h.Content(rtl = rtl) }
        onNodeWithText("Row 3").performClick()
        waitForIdle()
        for (edge in listOf(NavigationEvent.EDGE_LEFT, NavigationEvent.EDGE_RIGHT)) {
            val edgeName = if (edge == NavigationEvent.EDGE_LEFT) "left" else "right"
            runOnIdle { h.input.start(edge) }
            for (pct in listOf(25, 50, 75)) {
                runOnIdle { h.input.progress(edge, pct / 100f) }
                waitForIdle()
                capture("predictive_${pct}pct_${edgeName}_${if (rtl) "rtl" else "ltr"}")
            }
            runOnIdle { h.input.cancel() }
            waitForIdle()
        }
    }

    @Test
    fun `B-KIT-054 predictive preview frames LTR`() = predictive(rtl = false)

    @Test
    fun `B-KIT-054 predictive preview frames RTL`() = predictive(rtl = true)

    @Test
    fun `B-KIT-054 reduced motion frames`() = runDesktopComposeUiTest(W, H) {
        val h = Harness(scale = 0f)
        setContent { h.Content() }
        waitForIdle()
        mainClock.autoAdvance = false
        onNodeWithText("Row 3").performClick()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        capture("off_open_2frames")
        mainClock.autoAdvance = true
        waitForIdle()
        runOnIdle {
            h.input.start(NavigationEvent.EDGE_LEFT)
            h.input.progress(NavigationEvent.EDGE_LEFT, 0.5f)
        }
        waitForIdle()
        capture("off_predictive_50pct")
        runOnIdle { h.input.cancel() }
    }

    private companion object {
        const val W = 412
        const val H = 860
    }
}
