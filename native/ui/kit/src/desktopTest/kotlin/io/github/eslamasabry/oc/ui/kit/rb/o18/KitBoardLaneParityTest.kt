package io.github.eslamasabry.oc.ui.kit.rb.o18

import io.github.eslamasabry.oc.ui.kit.team.KitBoardLaneLayout
import kotlin.test.Test
import kotlin.test.assertEquals

/** The constants and lane maths of lib/ui/kit/kit_board_lane.dart (and KitLayout's lane widths) at 8302de537. */
class KitBoardLaneParityTest {
    @Test fun constants() {
        assertEquals(320f, KitBoardLaneLayout.NARROWEST_WINDOW) // kit_board_lane.dart:110
        assertEquals(20f, KitBoardLaneLayout.LANE_PEEK) // kit_layout.dart:124
        assertEquals(296f, KitBoardLaneLayout.LANE_MIN_WIDTH) // kit_layout.dart:125
        assertEquals(4, KitBoardLaneLayout.SKELETON_CARDS) // kit_board_lane.dart:609
        assertEquals(2, KitBoardLaneLayout.MIN_COLUMNS) // kit_board_lane.dart:77
        assertEquals(8, KitBoardLaneLayout.MAX_COLUMNS)
    }

    @Test fun paged_lane_width_peeks_twenty_on_a_phone_and_none_at_320() {
        assertEquals(372f, KitBoardLaneLayout.pagedLaneWidth(412f))
        assertEquals(320f, KitBoardLaneLayout.pagedLaneWidth(320f))
        assertEquals(320f, KitBoardLaneLayout.pagedLaneWidth(340f)) // peek 10 each side
        assertEquals(400f, KitBoardLaneLayout.pagedLaneWidth(700f)) // capped at laneMaxWidth
    }

    @Test fun side_by_side_fits_four_lanes_at_1280() {
        val room = 1280f - 2 * 16f
        val fit = KitBoardLaneLayout.fit(room, 12f, 5)
        assertEquals(4, fit)
        assertEquals(303f, KitBoardLaneLayout.sideBySideLane(room, 12f, fit))
        assertEquals(1, KitBoardLaneLayout.fit(200f, 12f, 5))
        assertEquals(2, KitBoardLaneLayout.fit(5000f, 12f, 2))
    }
}
