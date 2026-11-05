package io.github.eslamasabry.oc.ui.kit.rb.o04

import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.color.withAccent
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.StyleResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** kit_style_theme.dart `restyled`: the school's adjustRoles, the accent guard re-run, the roles kept when unchanged. */
class KitStyleThemeParityTest {
    private fun base(dark: Boolean) = ThemeResolver.roles(PackId.Opencode, null, dark, false, null)

    @Test fun unchangedRolesAreReturnedAsIs() {
        for (dark in listOf(false, true)) {
            val roles = base(dark)
            for (style in Style.entries) {
                val spec = StyleResolver.spec(style)
                if (spec.adjustRoles(roles) == roles) assertSame(roles, StyleResolver.adjust(roles, style))
            }
        }
    }

    @Test fun adjustedRolesGetTheAccentGuard() {
        for (dark in listOf(false, true)) {
            val roles = base(dark)
            for (style in Style.entries) {
                val adjusted = StyleResolver.spec(style).adjustRoles(roles)
                if (adjusted != roles) assertEquals(adjusted.withAccent(adjusted.accent), StyleResolver.adjust(roles, style))
            }
        }
    }
}
