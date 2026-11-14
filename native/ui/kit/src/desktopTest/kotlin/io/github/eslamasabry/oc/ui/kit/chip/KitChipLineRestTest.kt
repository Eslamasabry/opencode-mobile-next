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
    fun `B-KIT-CHIP-019 the rest gap never moves a chip out of view`() {
        val sizes = intArrayOf(100, 120, 90)
        for (room in listOf(40f, 104f, 130f, 188f, 257f, 500f)) assertEquals(8f, FilterChipsRules.restGap(sizes, 8f, room, 64f))
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
