package io.github.eslamasabry.oc.ui.kit.row

import kotlin.test.Test
import kotlin.test.assertEquals

/** KitExpandRow's chevron placement against kit_row_parts.dart and kit_optical.dart at 8302de537. */
class KitExpandRowTest {
    @Test fun `B-KIT-R10-020 the caret's ink ends on the row's 12 dp trailing rail`() {
        assertEquals(12, KitRowOptical.RAIL)
        // 20 dp caret-down: ink stands .15625 x 20 = 3.125 in, so the box keeps 8.875 after it.
        assertEquals(8.875f, KitRowOptical.endPad(KitRowOptical.CHEVRON_DOWN_INK, 20f, 12f))
    }

    @Test fun `B-KIT-R10-021 the end pad is never negative`() {
        assertEquals(0f, KitRowOptical.endPad(.5f, 40f, 12f))
    }
}
