package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Open-transitions (Galaxy S22, build 10050): Home → + → New conversation showed a fully black screen while New slid
 * up, the home gone from under it. Nothing may be blank between two pages: during a slide-up push the covered page
 * stays drawn, unchanged, wherever the arriving page does not cover it yet, from the push's first frame to its last.
 */
@OptIn(ExperimentalTestApi::class)
class ShellSlideUpCoverTest {
    /** Mean channel difference (0-255) over the rows [fromY, toY) of two same-size images. */
    private fun diff(a: BufferedImage, b: BufferedImage, fromY: Int, toY: Int): Double {
        var sum = 0L
        var n = 0L
        for (y in fromY until toY) for (x in 0 until a.width step 2) {
            val p = a.getRGB(x, y)
            val q = b.getRGB(x, y)
            sum += abs((p shr 16 and 255) - (q shr 16 and 255)) + abs((p shr 8 and 255) - (q shr 8 and 255)) + abs((p and 255) - (q and 255))
            n += 3
        }
        return sum.toDouble() / n
    }

    @Test
    fun `OT-14 the page a slide-up page covers stays drawn under it in every frame of the push`() = runDesktopComposeUiTest(W, H) {
        val h = Harness()
        setContent { h.Content() }
        waitForIdle()
        val before = onRoot().captureToImage().toAwtImage()
        mainClock.autoAdvance = false
        runOnIdle { h.navigator.open(ChatsRoute.New(profile = null)) }
        // Frame by frame through the first quarter of the slide (on the phone every frame was black from the first):
        // the top fifth is the home's, as the arriving page is still well below it.
        var t = 0L
        while (t < ShellMotion.STANDARD / 4) {
            mainClock.advanceTimeByFrame()
            t += 16
            val frame = onRoot().captureToImage().toAwtImage()
            val top = diff(before, frame, 0, frame.height / 5)
            assertTrue(top < 4.0, "frame at ${t} ms: the covered page is not drawn above the arriving one (mean diff $top)")
        }
    }

    private companion object {
        const val W = 412
        const val H = 860
    }
}
