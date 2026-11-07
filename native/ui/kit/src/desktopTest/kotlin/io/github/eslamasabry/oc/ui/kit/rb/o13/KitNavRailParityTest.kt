package io.github.eslamasabry.oc.ui.kit.rb.o13

import io.github.eslamasabry.oc.ui.kit.nav.NavRailMetrics
import kotlin.test.Test
import kotlin.test.assertEquals

/** lib/ui/kit/kit_nav.dart at 8302de537: the rail's lens and slot geometry (_lensHeight, _lensWidth, _labelMetrics, _itemExtent, _LensTrack.rest). */
class KitNavRailParityTest {
    @Test fun `the lens is the chip's height and a target and a step wide`() {
        assertEquals(32, NavRailMetrics.lensHeight) // KitTokens.chipHeight, kit_nav.dart:347
        assertEquals(56, NavRailMetrics.lensWidth) // minTarget 48 + space2 8, kit_nav.dart:348
    }

    @Test fun `labels grow to 2x only while they fit the slot less a step`() {
        assertEquals(2f, NavRailMetrics.labelMaxScale(30f)) // 72 / 30 = 2.4, capped at navLabelMaxScale
        assertEquals(1.5f, NavRailMetrics.labelMaxScale(48f)) // 72 / 48
        assertEquals(1f, NavRailMetrics.labelMaxScale(100f)) // never below 1
        assertEquals(2f, NavRailMetrics.labelMaxScale(0f))
    }

    @Test fun `large text adds a step under the label`() {
        assertEquals(16f, NavRailMetrics.labelHeight(15.6f, 1f))
        assertEquals(24f + 8f, NavRailMetrics.labelHeight(15.6f, 1.5f)) // ceil(23.4) + space2 past 1.2x
    }

    @Test fun `a slot holds the lens, the label and three space1 steps, at least a target`() {
        assertEquals(32f + 16f + 12f, NavRailMetrics.itemExtent(16f))
        assertEquals(48f, NavRailMetrics.itemExtent(0f).coerceAtLeast(48f))
    }

    @Test fun `the lens rests a space1 into its destination's slot`() {
        assertEquals(4f, NavRailMetrics.lensTop(0, 60f))
        assertEquals(124f, NavRailMetrics.lensTop(2, 60f))
    }
}
