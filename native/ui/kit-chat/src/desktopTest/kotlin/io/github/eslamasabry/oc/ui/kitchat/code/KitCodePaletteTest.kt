package io.github.eslamasabry.oc.ui.kitchat.code

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import io.github.eslamasabry.oc.ui.design.color.Graphite
import io.github.eslamasabry.oc.ui.kit.theme.toKitColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** C-019 / C-006: the code palette and fill are Flutter's (`lib/ui/kit/kit_code_block.dart:222-246`, `:263-275`). */
class KitCodePaletteTest {
    @Test
    fun `C-019 the palette is Flutter's - keyword w600, comment italic, codeType family, plain punctuation`() {
        val dark = Graphite.dark.toKitColors()
        val c = KitCodeColors.of(dark)
        assertEquals(dark.codeKeyword, c[CodeRole.Keyword])
        assertEquals(dark.codeString, c[CodeRole.String])
        for (role in listOf(CodeRole.Type, CodeRole.Number, CodeRole.Function, CodeRole.Meta)) {
            assertEquals(dark.codeType, c[role], "$role is codeType (Function was the accent green)")
        }
        assertEquals(dark.text3, c[CodeRole.Comment])
        assertEquals(dark.text1, c[CodeRole.Punctuation], "punctuation is unclassed in Flutter")
        assertEquals(dark.text1, c[CodeRole.Plain])
        assertEquals(dark.success, c[CodeRole.Addition])
        assertEquals(dark.text2, c[CodeRole.Deletion], "codeRemoved is text2")
        assertEquals(FontWeight.W600, c.span(CodeRole.Keyword).fontWeight)
        assertEquals(FontStyle.Italic, c.span(CodeRole.Comment).fontStyle)
        assertNull(c.span(CodeRole.String).fontWeight)
    }

    @Test
    fun `C-006 the code fill is detailsSurface - surface1 in dark, the ground in light`() {
        val dark = Graphite.dark.toKitColors()
        val light = Graphite.light.toKitColors()
        assertEquals(dark.surface1, KitCodeColors.of(dark).fill)
        assertEquals(light.ground, KitCodeColors.of(light).fill)
    }

    @Test
    fun `C-019 a line takes the styles of the tokens crossing it`() {
        val c = KitCodeColors.of(Graphite.dark.toKitColors())
        // "val a" on the second line of "x\nval a": the keyword token is at 2..5 of the whole source.
        val text = lineText("val a", 2, listOf(CodeToken(2, 5, CodeRole.Keyword)), c)
        assertEquals("val a", text.text)
        val span = text.spanStyles.single()
        assertEquals(0 to 3, span.start to span.end)
        assertEquals(c[CodeRole.Keyword], span.item.color)
        assertTrue(lineText("plain", 0, emptyList(), c).spanStyles.isEmpty())
    }
}
