package io.github.eslamasabry.oc.ui.kit.content.rb.o18

import io.github.eslamasabry.oc.ui.kit.content.viewer.KIT_VIEWER_MAX_LINES
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerContent
import io.github.eslamasabry.oc.ui.kit.content.viewer.ViewerNotice
import io.github.eslamasabry.oc.ui.kit.content.viewer.ViewerPrepared
import io.github.eslamasabry.oc.ui.kit.content.viewer.groupDigits
import io.github.eslamasabry.oc.ui.kit.content.viewer.viewerNotice
import io.github.eslamasabry.oc.ui.kit.content.viewer.viewerSizeInWords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The rules of lib/ui/kit/kit_viewer.dart and kit_viewer_build.dart / kit_viewer_bodies.dart the frame draws from. */
class KitViewerParityTest {
    @Test
    fun sizeInWordsFollowsDart() {
        assertEquals("2.4 MB", viewerSizeInWords(2516582)) // the golden's release.zip
        assertEquals("512 B", viewerSizeInWords(512))
        assertEquals("12 KB", viewerSizeInWords(12 * 1024 + 300)) // ≥ 10: no decimal
        assertEquals("1 KB", viewerSizeInWords(1024)) // a whole tenth drops its ".0" as intl does
        assertNull(viewerSizeInWords(null))
        assertNull(viewerSizeInWords(-1))
    }

    @Test
    fun truncationNoticeCountsShownAndKnownLines() {
        val text = (1..80).joinToString("\n") { "const s$it = 'string $it';" }
        val content = KitViewerContent.Code(text, language = "dart", truncated = true, totalLines = 5210)
        assertEquals(ViewerNotice.Counted(80, 5210), viewerNotice(content, ViewerPrepared(text), sourceView = false))
        assertEquals("5,210", groupDigits(5210))
        assertEquals("80", groupDigits(80))
        assertEquals("1,000,000", groupDigits(1_000_000))
        // Nothing cut: no notice.
        assertNull(viewerNotice(KitViewerContent.Code("a"), ViewerPrepared("a"), sourceView = false))
        // Truncated with no known total: "Showing part of this file".
        assertEquals(ViewerNotice.Partial, viewerNotice(KitViewerContent.Text("a", truncated = true), ViewerPrepared("a"), false))
    }

    @Test
    fun preparedCapsAtTwoThousandLines() {
        val prepared = ViewerPrepared((1..2500).joinToString("\n"))
        assertTrue(prepared.capped)
        assertEquals(KIT_VIEWER_MAX_LINES, prepared.lines.size)
        assertEquals(2500, prepared.totalLines)
        assertEquals(ViewerNotice.Counted(2000, 2500), viewerNotice(KitViewerContent.Text((1..2500).joinToString("\n")), prepared, false))
        // A trailing newline is not a line of its own.
        assertEquals(2, ViewerPrepared("a\nb\n").totalLines)
    }

    @Test
    fun findIsLiteralAndCaseInsensitive() {
        val prepared = ViewerPrepared("Name name\nno match\nNAME")
        assertEquals(listOf(Triple(0, 0, 4), Triple(0, 5, 9), Triple(2, 0, 4)), prepared.find("name"))
        assertEquals(emptyList(), prepared.find(""))
        assertEquals(listOf(Triple(0, 4, 5), Triple(1, 2, 3)), prepared.find(" "))
    }
}

class KitViewerSvgSizeTest {
    @Test
    fun svgDrawsAtItsViewBoxSize() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 80" width="240" height="160"></svg>"""
        assertEquals(120f to 80f, io.github.eslamasabry.oc.ui.kit.content.viewer.svgViewBox(svg))
        assertEquals(10f to 5.5f, io.github.eslamasabry.oc.ui.kit.content.viewer.svgViewBox("<svg viewBox='0,0,10,5.5'/>"))
        assertNull(io.github.eslamasabry.oc.ui.kit.content.viewer.svgViewBox("<svg width='4'/>"))
        assertNull(io.github.eslamasabry.oc.ui.kit.content.viewer.svgViewBox("<svg viewBox='0 0 0 5'/>"))
    }
}
