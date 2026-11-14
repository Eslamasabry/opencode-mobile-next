package io.github.eslamasabry.oc.ui.kit.chip

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Coordinator decision 2026-11-14: a scrolling chip row never drops a chip; the cut one is under the edge fade. */
@OptIn(ExperimentalTestApi::class)
class KitChipRowOverflowTest {
    private val chips = listOf("All projects", "All connections", "All sources", "Running", "Needs you")

    private fun check(style: Style, fontScale: Float) = runComposeUiTest {
        for (w in listOf(240, 300, 360)) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                    KitTheme(KitAppearance(motion = MotionLevel.Off, style = style), FakeKitPlatform(), "en", stillFrame = true) {
                        InWindow(480, 300) {
                            Box(Modifier.width(w.dp)) {
                                KitFilterChips(bleed = true) {
                                    chips.forEachIndexed { i, l -> KitChip(l, Modifier.testTag("chip-$i"), kind = KitChipKind.Action, onClick = {}) }
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            val line = onNodeWithTag("kit-filter-chips").getUnclippedBoundsInRoot()
            val boxes = chips.indices.map { onNodeWithTag("chip-$it", useUnmergedTree = true).getUnclippedBoundsInRoot() }
            // every chip is in the row's content, in order, with the line's own gap (none carried away)
            for (i in 1 until boxes.size) assertEquals(8f, (boxes[i].left - boxes[i - 1].right).value, 0.6f, "gap before chip $i at $w")
            val cut = boxes.firstOrNull { it.left < line.right && it.right > line.right + 0.5.dp }
            if (boxes.last().right > line.right) {
                // overflowing: the chip at the edge is cut, and the fade (zero for a whole chip, else over the piece) is its cue
                assertTrue(cut != null || boxes.any { it.left >= line.right }, "overflow at $w shows no cut chip")
                if (cut != null) {
                    val spans = FilterChipsRules.spans(boxes.map { (it.right - it.left).value.toInt() }.toIntArray(), 8f, boxes[0].left.value - line.left.value, 0f)
                    val (_, end) = FilterChipsRules.edgeFades(spans, (line.right - line.left).value, FilterChipsRules.CHIP_FADE_DP.toFloat())
                    assertTrue(end > 0f, "the cut chip at $w is not under the fade")
                }
            }
        }
    }

    @Test fun `W2-028 glass at 1_0`() = check(Style.Glass, 1f)
    @Test fun `W2-028 glass at 1_3`() = check(Style.Glass, 1.3f)
    @Test fun `W2-028 solid at 1_0`() = check(Style.Solid, 1f)
    @Test fun `W2-028 solid at 1_3`() = check(Style.Solid, 1.3f)
}
