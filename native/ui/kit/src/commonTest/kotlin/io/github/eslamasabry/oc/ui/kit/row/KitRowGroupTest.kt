package io.github.eslamasabry.oc.ui.kit.row

import kotlin.test.Test
import kotlin.test.assertEquals

/** The row-group seam (kit_row_group_seam.dart, kit_row.dart:1309,1339 at 8302de537). */
class KitRowGroupTest {
    @Test fun `B-KIT-R10-030 a framed unlabelled group right under another keeps space3`() {
        assertEquals(12, kitRowGroupSeam(labelled = false, gapBefore = 0, frames = true, afterGroup = true, gap = 12))
    }

    @Test fun `B-KIT-R10-031 no seam with a label, a gap of its own, a flat list or nothing above`() {
        assertEquals(0, kitRowGroupSeam(labelled = true, gapBefore = 0, frames = true, afterGroup = true, gap = 12))
        assertEquals(0, kitRowGroupSeam(labelled = false, gapBefore = 24, frames = true, afterGroup = true, gap = 12))
        assertEquals(0, kitRowGroupSeam(labelled = false, gapBefore = 0, frames = false, afterGroup = true, gap = 12))
        assertEquals(0, kitRowGroupSeam(labelled = false, gapBefore = 0, frames = true, afterGroup = false, gap = 12))
    }
}
