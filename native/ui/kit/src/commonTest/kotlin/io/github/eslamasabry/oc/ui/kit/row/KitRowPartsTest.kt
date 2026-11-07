package io.github.eslamasabry.oc.ui.kit.row

import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import kotlin.test.Test
import kotlin.test.assertEquals

/** KitRowParts' pure rules. */
class KitRowPartsTest {
    @Test fun `B-KIT-R10-050 a selected row's tile steps to surface1 so the surface3 highlight keeps it`() {
        assertEquals(SurfaceLevel.Surface3, rowTileLevel(selected = false))
        assertEquals(SurfaceLevel.Surface1, rowTileLevel(selected = true))
    }

    @Test fun `B-KIT-R10-051 the caret-right glyph's ink stands 28 percent in from its end`() {
        assertEquals(.28125f, KitRowOptical.CHEVRON_RIGHT_INK)
    }
}
