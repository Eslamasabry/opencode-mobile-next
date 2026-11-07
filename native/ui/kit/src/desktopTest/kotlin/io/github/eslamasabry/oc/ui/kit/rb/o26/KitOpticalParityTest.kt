package io.github.eslamasabry.oc.ui.kit.rb.o26

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.text.KitOptical
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every constant and rule of lib/ui/kit/kit_optical.dart at 8302de537. */
class KitOpticalParityTest {
    private fun near(expected: Dp, actual: Dp) = assertEquals(expected.value, actual.value, 1e-4f)

    @Test fun `the ink table is Dart's, code point by code point`() {
        assertEquals(
            mapOf(
                0xe13a to (.34375f to .28125f),
                0xe136 to (.15625f to .15625f),
                0xe13c to (.15625f to .15625f),
                0xe1fe to (.1875f to .1875f),
                0xe4f6 to (.1875f to .1875f),
                0xe182 to (.125f to .09375f),
                0xe1cc to (.125f to .125f),
            ),
            KitOptical.ink,
        )
    }

    @Test fun `left to right every listed glyph uses its end margin`() {
        val size = 20.dp
        near(size * .28125f, KitOptical.endInset(KitIcons.chevronRight, size, LayoutDirection.Ltr))
        near(size * .15625f, KitOptical.endInset(KitIcons.chevronDown, size, LayoutDirection.Ltr))
        near(size * .15625f, KitOptical.endInset(KitIcons.chevronUp, size, LayoutDirection.Ltr))
        near(size * .1875f, KitOptical.endInset(KitIcons.more, size, LayoutDirection.Ltr))
        near(size * .1875f, KitOptical.endInset(KitIcons.close, size, LayoutDirection.Ltr))
        near(size * .09375f, KitOptical.endInset(KitIcons.check, size, LayoutDirection.Ltr))
        near(size * .125f, KitOptical.endInset(KitIcons.copy, size, LayoutDirection.Ltr))
    }

    @Test fun `right to left a mirroring glyph keeps its end margin and a fixed one uses its start`() {
        val size = 24.dp
        near(size * .28125f, KitOptical.endInset(KitIcons.chevronRight, size, LayoutDirection.Rtl))
        near(size * .125f, KitOptical.endInset(KitIcons.check, size, LayoutDirection.Rtl))
        near(size * .1875f, KitOptical.endInset(KitIcons.more, size, LayoutDirection.Rtl))
    }

    @Test fun `a glyph not listed or not from the regular family is placed by its box`() {
        near(0.dp, KitOptical.endInset(KitIcons.add, 20.dp, LayoutDirection.Ltr))
        near(0.dp, KitOptical.endInset(KitIcons.workspaceSelected, 20.dp, LayoutDirection.Ltr))
        val foreign = ImageVector.Builder("not-a-kit-glyph", 24.dp, 24.dp, 24f, 24f).build()
        near(0.dp, KitOptical.endInset(foreign, 20.dp, LayoutDirection.Rtl))
    }

    @Test fun `endPad leaves rail less inset and is never negative`() {
        near(16.dp - 20.dp * .28125f, KitOptical.endPad(KitIcons.chevronRight, 20.dp, 16.dp, LayoutDirection.Ltr))
        near(0.dp, KitOptical.endPad(KitIcons.chevronRight, 20.dp, 2.dp, LayoutDirection.Ltr))
        near(16.dp, KitOptical.endPad(KitIcons.add, 20.dp, 16.dp, LayoutDirection.Ltr))
    }
}
