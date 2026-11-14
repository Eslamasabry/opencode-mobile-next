package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.JoinBox
import io.github.eslamasabry.oc.ui.design.effects.JoinOutline
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Audit 2026-11-14 (c26, Glass light aurora, a reply streaming): the message box's green working rim ran straight across
 * the bottom of the joined "Jump to latest" pill, so a line cut the pill from the box. Joined, the glow runs round the
 * one body: over the pill's top, and nowhere along the box's own top edge under the pill.
 */
@OptIn(ExperimentalTestApi::class)
class ComposerGlowJoinedTest {
    /** The box, 320 x 80 dp, and the pill (120 x 32 dp) sunk 8 dp into its top, at 2 px per dp. */
    private class PillOnBox : GlowOutline {
        val box = JoinBox.of(0f, 0f, 640f, 160f, 52f)
        val pill = JoinBox.of(200f, -48f, 440f, 16f, 32f)

        override val joined: Boolean = true

        override fun band(widthPx: Float, centred: Boolean, origin: Offset): Path =
            path(if (centred) -widthPx / 2f else 0f, if (centred) widthPx / 2f else widthPx, origin)

        override fun fill(origin: Offset): Path = path(0f, null, origin)

        private fun path(outer: Float, inner: Float?, origin: Offset): Path =
            Path().apply {
                fillType = PathFillType.EvenOdd
                polygon(JoinOutline.trace(box, pill, REACH_PX, outer), origin)
                if (inner != null) polygon(JoinOutline.trace(box, pill, REACH_PX, inner), origin)
            }

        private fun Path.polygon(p: FloatArray, o: Offset) {
            moveTo(p[0] + o.x, p[1] + o.y)
            var i = 2
            while (i < p.size) {
                lineTo(p[i] + o.x, p[i + 1] + o.y)
                i += 2
            }
            close()
        }
    }

    @Test
    fun `B-GLASS-JOIN-GLOW-01 joined to the pill the ring runs over the pill and not across its foot`() = runComposeUiTest {
        var ring = Color.Unspecified
        setContent {
            // Calm: the still, lit ring at full strength (no travelling highlight to wait for).
            MotionTheme(level = MotionLevel.Calm) {
                CompositionLocalProvider(LocalDensity provides Density(2f)) {
                    ring = LocalKitColors.current.live
                    Box(Modifier.size(360.dp, 160.dp).background(Color.Black)) {
                        ComposerGlow(
                            active = true,
                            modifier = Modifier.padding(start = 20.dp, top = TOP_DP.dp),
                            choice = KitGlowChoice.Classic,
                            corner = 26.dp,
                            outline = PillOnBox(),
                        ) {
                            Box(Modifier.size(320.dp, 80.dp))
                        }
                    }
                }
            }
        }
        waitForIdle()
        val map = onRoot().captureToImage().toPixelMap()
        // The box's left edge at x 40 px; its top at y 120 px; the pill's top at y 72 px; its centre at x 360 px.
        val x = 40 + 320
        val overPill = closest(map, x, 72 - 1..72 + 4, ring)
        val acrossFoot = closest(map, x, 120 - 1..120 + 4, ring)
        val boxTopAway = closest(map, 40 + 100, 120 - 1..120 + 4, ring)
        assertTrue(overPill < .2f, "the ring runs over the pill's top (distance to the ring's colour $overPill)")
        assertTrue(boxTopAway < .2f, "the ring runs along the box's top away from the pill ($boxTopAway)")
        assertTrue(acrossFoot > .4f, "no ring across the pill's foot on the box's top ($acrossFoot)")
    }

    /** The smallest per-channel distance to [c] among the pixels of column [x] over [rows]. */
    private fun closest(map: PixelMap, x: Int, rows: IntRange, c: Color): Float =
        rows.filter { it in 0 until map.height }.minOf { y ->
            val p = map[x, y]
            maxOf(abs(p.red - c.red), abs(p.green - c.green), abs(p.blue - c.blue))
        }

    private companion object {
        const val TOP_DP = 60
        const val REACH_PX = 112f
    }
}
