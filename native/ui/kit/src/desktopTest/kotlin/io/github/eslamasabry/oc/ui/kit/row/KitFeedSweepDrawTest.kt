@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The light that sweeps over a running row's "Working" is drawn over the word's letters and placed while drawing
 * (KitFeedItem, kitLiveSweep): the row is not composed or laid out again as it moves (jank pass 2026-11-10; each
 * running row used to be composed and its line laid out again every frame).
 *
 * Guards: the light still moves (the word's pixels differ half a lap apart), it touches nothing but the word (the rest
 * of the line is the same in both frames), and with motion off nothing moves.
 */
class KitFeedSweepDrawTest {
    private fun frames(motion: MotionLevel): Pair<List<Color>, List<Color>> {
        var a = emptyList<Color>()
        var b = emptyList<Color>()
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent {
                KitTheme(KitAppearance(mode = AppearanceMode.Dark, motion = motion), FakeKitPlatform(), "en") {
                    Box(Modifier.size(360.dp, 80.dp).background(Color.Black).testTag("row")) {
                        KitFeedItem("Add dark mode", "beta", "now", KitRowState("Working", KitRowStateTone.Running), {})
                    }
                }
            }
            fun shot(): List<Color> {
                val px = onNodeWithTag("row").captureToImage().toPixelMap()
                return List(px.width * px.height) { px[it % px.width, it / px.width] }
            }
            mainClock.advanceTimeBy(MotionTokens.LIVE_SWEEP_LAP.toLong() * 3 / 10)
            a = shot()
            mainClock.advanceTimeBy(MotionTokens.LIVE_SWEEP_LAP.toLong() * 3 / 10)
            b = shot()
        }
        return a to b
    }

    @Test
    fun `the light moves over the word and nothing else changes`() {
        val (a, b) = frames(MotionLevel.Full)
        val changed = a.indices.filter { a[it] != b[it] }
        assertTrue(changed.isNotEmpty(), "the light moved between the two frames")
        // "Working" is the line's first word: every changed pixel lies in the row's first third, on the meta line.
        val width = 360
        val xs = changed.map { it % width }
        val ys = changed.map { it / width }
        assertTrue(xs.max() < width / 3, "only the word changes (changed up to x=${xs.max()})")
        assertTrue(ys.max() - ys.min() < 24, "only the meta line changes (rows ${ys.min()}..${ys.max()})")
    }

    @Test
    fun `with motion off nothing moves`() {
        val (a, b) = frames(MotionLevel.Off)
        assertEquals(a, b)
    }
}
