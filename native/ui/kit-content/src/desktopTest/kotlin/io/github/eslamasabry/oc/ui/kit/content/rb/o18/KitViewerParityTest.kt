package io.github.eslamasabry.oc.ui.kit.content.rb.o18

import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerContent
import io.github.eslamasabry.oc.ui.kit.content.viewer.VIEWER_MAX_LINES
import io.github.eslamasabry.oc.ui.kit.content.viewer.ViewerFind
import io.github.eslamasabry.oc.ui.kit.content.viewer.ViewerPrepared
import io.github.eslamasabry.oc.ui.kit.content.viewer.ViewerView
import io.github.eslamasabry.oc.ui.kit.content.viewer.knownTotal
import io.github.eslamasabry.oc.ui.kit.content.viewer.truncatedFlag
import io.github.eslamasabry.oc.ui.kit.content.viewer.viewBoxOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rules of lib/ui/kit/kit_viewer.dart and kit_viewer_build.dart at 8302de537 that the native viewer carries as
 * logic: the 2,000-line cap (`_Prepared`), Find (`_onQuery`, `_step`), what the body draws (`_sourceView`, `_codeText`,
 * `_isEmpty`), the truncation inputs, and the SVG's drawn size (flutter_svg's view box).
 */
class KitViewerParityTest {
    @Test
    fun theCapIsTwoThousandLines() {
        assertEquals(2000, VIEWER_MAX_LINES) // kit_viewer.dart:302
        val text = (1..2500).joinToString("\n") { "l$it" }
        val prepared = ViewerPrepared(text)
        assertTrue(prepared.capped)
        assertEquals(2500, prepared.totalLines)
        assertEquals(2000, prepared.shownLines)
        assertEquals(2000, prepared.shown.split('\n').size)
    }

    @Test
    fun aTrailingNewlineAddsNoLine() {
        val prepared = ViewerPrepared("a\nbb\n")
        assertEquals(2, prepared.totalLines)
        assertFalse(prepared.capped)
        assertEquals("bb", prepared.longest)
    }

    @Test
    fun findIsLiteralCaseInsensitiveAndWraps() {
        assertEquals(listOf(0..3, 9..12), ViewerFind.marks("Name and name", "name"))
        assertEquals(listOf(1..2), ViewerFind.marks("a.*b", ".*"))
        assertTrue(ViewerFind.marks("abc", "").isEmpty())
        assertEquals(0, ViewerFind.step(3, 1, 4))
        assertEquals(3, ViewerFind.step(0, -1, 4))
    }

    @Test
    fun theNoticeGroupsThousands() {
        assertEquals("5,210", ViewerFind.grouped(5210))
        assertEquals("80", ViewerFind.grouped(80))
        assertEquals("1,234,567", ViewerFind.grouped(1234567))
    }

    @Test
    fun showSourceAppliesToMarkdownAndToSvgAndTablesWithAnOriginal() {
        val md = KitViewerContent.Markdown("# T")
        assertTrue(ViewerView(md, false).sourceApplies)
        assertNull(ViewerView(md, false).codeText)
        assertEquals("# T", ViewerView(md, true).codeText)
        val svg = KitViewerContent.Svg("<svg/>")
        assertFalse(ViewerView(svg, true).sourceApplies)
        assertNull(ViewerView(svg, true).codeText)
        val table = KitViewerContent.Delimited(listOf(listOf("a")), original = "a")
        assertEquals("a", ViewerView(table, true).codeText)
        assertTrue(ViewerView(KitViewerContent.Delimited(emptyList()), false).empty)
        assertTrue(ViewerView(KitViewerContent.Code("  \n"), false).empty)
    }

    @Test
    fun truncationInputsFollowTheContent() {
        val code = KitViewerContent.Code("x", truncated = true, totalLines = 5210)
        assertTrue(code.truncatedFlag())
        assertEquals(5210, code.knownTotal())
        assertNull(KitViewerContent.Markdown("x", truncated = true).knownTotal())
        assertFalse(KitViewerContent.Binary().truncatedFlag())
    }

    @Test
    fun anSvgIsDrawnAtItsViewBox() {
        assertEquals(120f to 80f, viewBoxOf("""<svg viewBox="0 0 120 80" width="240" height="160"/>"""))
        assertNull(viewBoxOf("<svg width=\"10\"/>"))
    }
}
