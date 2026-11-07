package io.github.eslamasabry.oc.ui.kit.row

import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * P-113: the selected server row ("This phone") is filled with surface3, and its leading tile was surface3 too, so the
 * row read as tileless. On a selected row the tile steps to the panel's surface1.
 */
class KitRowSelectedTileTest {
    @Test
    fun `B-KIT-113 a selected row's tile stands apart from the selection fill`() {
        assertNotEquals(SELECTED_ROW_LEVEL, rowTileLevel(selected = true))
        assertEquals(SurfaceLevel.Surface1, rowTileLevel(selected = true))
    }

    @Test
    fun `B-KIT-113 a row at rest keeps Flutter's surface3 tile`() {
        assertEquals(SurfaceLevel.Surface3, rowTileLevel(selected = false))
    }
}
