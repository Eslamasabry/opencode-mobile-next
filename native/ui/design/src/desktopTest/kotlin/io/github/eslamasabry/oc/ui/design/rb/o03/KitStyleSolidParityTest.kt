package io.github.eslamasabry.oc.ui.design.rb.o03

import io.github.eslamasabry.oc.ui.design.style.Knobs
import io.github.eslamasabry.oc.ui.design.style.SolidSpec
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** lib/ui/kit/style/kit_style_solid.dart: defaults, keepsNestedSurfaces, flatLists; every role keeps the part's own look. */
class KitStyleSolidParityTest {
    @Test fun tokens() {
        val t = SolidSpec.tokens
        assertEquals(Knobs(), t.defaults)
        assertTrue(t.keepsNestedSurfaces)
        assertTrue(t.flatLists)
        assertTrue(t.type.isNone)
        assertNull(t.live.maxStrength)
        assertTrue(!t.live.offWhenChosen)
    }

    @Test fun everyRoleKeepsThePartsOwnLook() {
        for (roles in bothModes) for (role in SurfaceRole.entries) assertNull(SolidSpec.resolve(req(roles, role)))
    }

    @Test fun noOnColorAndNoFilledLayerAndNoRoleChange() {
        for (roles in bothModes) {
            val r = req(roles, SurfaceRole.Control)
            assertNull(SolidSpec.onColor(r))
            assertNull(SolidSpec.resolveFilledLayer(r))
            assertEquals(roles, SolidSpec.adjustRoles(roles))
        }
    }
}
