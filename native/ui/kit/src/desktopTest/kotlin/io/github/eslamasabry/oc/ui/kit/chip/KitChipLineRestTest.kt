package io.github.eslamasabry.oc.ui.kit.chip

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Review 2026-11-13 #18: the home filter line rested on "Al", a 2-letter stump of its third chip. At rest the last chip
 * a chip line shows is whole, or cut with at least [FilterChipsRules.MIN_PEEK_DP] of it on screen.
 */
@OptIn(ExperimentalTestApi::class)
class KitChipLineRestTest {
    private val chips = listOf("All projects", "All connections", "All sources", "Running", "Needs you")

    @Test
    fun `B-KIT-CHIP-018 at rest a chip line never ends on a stump of a chip, at any width`() = runComposeUiTest {
        var width by mutableIntStateOf(200)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                    InWindow(480, 300) {
                        Box(Modifier.width(width.dp)) {
                            KitFilterChips(bleed = true) {
                                chips.forEachIndexed { i, label ->
                                    KitChip(label, Modifier.testTag("chip-$i"), kind = KitChipKind.Action, onClick = {})
                                }
                            }
                        }
                    }
                }
            }
        }
        var widened = 0
        var cutWell = 0
        for (w in 160..460 step 3) {
            width = w
            waitForIdle()
            val line = onNodeWithTag("kit-filter-chips").getUnclippedBoundsInRoot()
            val boxes = chips.indices.map { onNodeWithTag("chip-$it", useUnmergedTree = true).getUnclippedBoundsInRoot() }
            for (b in boxes) {
                if (b.left < line.right && b.right > line.right + 0.5.dp) {
                    val shown = (line.right - b.left).value
                    assertTrue(
                        shown >= FilterChipsRules.MIN_PEEK_DP - 1f,
                        "at $w dp the line rests on a ${shown}dp piece of a chip ($b in $line)",
                    )
                    cutWell++
                }
            }
            val gap = (boxes[1].left - boxes[0].right).value
            if (gap > 8.5f) widened++
        }
        assertTrue(widened > 0, "some widths would have ended on a stump and widened the gaps instead")
        assertTrue(cutWell > 0, "some widths end on a chip cut with enough of it showing")
    }

    @Test
    fun `B-KIT-CHIP-019 the rest gap carries a stump past the edge and leaves a good cut alone`() {
        val sizes = intArrayOf(100, 120, 90)
        // Natural: chip 1 spans 108..228. At room 130 only 22 px of it would show: the one gap before it grows to 31.
        assertEquals(8f + 23f, FilterChipsRules.restGap(sizes, 8f, 130f, 64f))
        // 80 px of it showing: a good cut, the gap stays.
        assertEquals(8f, FilterChipsRules.restGap(sizes, 8f, 188f, 64f))
        // The edge falls in the gap between chips, or everything fits: nothing changes.
        assertEquals(8f, FilterChipsRules.restGap(sizes, 8f, 104f, 64f))
        assertEquals(8f, FilterChipsRules.restGap(sizes, 8f, 500f, 64f))
        // The first chip is never pushed.
        assertEquals(8f, FilterChipsRules.restGap(sizes, 8f, 40f, 64f))
        // A stump of the third chip: both gaps before it share the push.
        assertEquals(8f + 11f, FilterChipsRules.restGap(sizes, 8f, 257f, 64f))
    }

    @Test
    fun `B-KIT-CHIP-020 an edge fades over the cut chip only, never over a whole chip beside it`() {
        val spans = FilterChipsRules.spans(intArrayOf(100, 120, 90), 8f, 0f, 0f)
        // Chip 1 spans 108..228: cut at 200 with 92 px showing; the fade is its full width.
        assertEquals(0f to 32f, FilterChipsRules.edgeFades(spans, 200f, 32f))
        // Cut at 120 with 12 px showing: the fade covers the piece, not the whole chip 0 before it.
        assertEquals(0f to 12f, FilterChipsRules.edgeFades(spans, 120f, 32f))
        // The edge between chips: no fade.
        assertEquals(0f to 0f, FilterChipsRules.edgeFades(spans, 104f, 32f))
        // Scrolled 50 px: chip 0 is cut at the start with 50 px showing.
        val scrolled = FilterChipsRules.spans(intArrayOf(100, 120, 90), 8f, 0f, 50f)
        assertEquals(32f to 0f, FilterChipsRules.edgeFades(scrolled, 400f, 32f))
    }
}
