package io.github.eslamasabry.oc.ui.kit.content.rb.o19

import io.github.eslamasabry.oc.ui.design.layout.WindowClass
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerContent
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerKind
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerSource
import io.github.eslamasabry.oc.ui.kit.content.viewer.ViewerPdfPage
import io.github.eslamasabry.oc.ui.kit.content.viewer.kitViewerDefaultWrap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every constant and rule of lib/ui/kit/kit_viewer_content.dart at 8302de537 (standard §6 step 8): the enum order is the
 * wire contract, the wrap rule reads `KitCodeBlock.defaultWrap` (kind code never wraps; every other kind wraps on a
 * compact window only), every constructor is data only with Dart's defaults, `copyText` per kind, and `KitViewerSource`
 * equality is content identity plus the same load function.
 */
class KitViewerContentParityTest {
    @Test
    fun kindOrderIsTheWireContract() {
        assertEquals(
            listOf("Text", "Code", "Markdown", "Image", "Pdf", "Svg", "Delimited", "Binary"),
            KitViewerKind.entries.map { it.name },
        )
    }

    @Test
    fun defaultWrapFollowsKitCodeBlock() {
        // `KitCodeBlock.defaultWrap(context, kind == code ? code : output)`: code scrolls sideways on every window;
        // output wraps on a compact window and scrolls from medium up (kit_code_block.dart:334-337).
        for (window in WindowClass.entries) {
            assertFalse(kitViewerDefaultWrap(window, KitViewerKind.Code), "code must never wrap ($window)")
            for (kind in KitViewerKind.entries.filter { it != KitViewerKind.Code }) {
                assertEquals(window == WindowClass.Compact, kitViewerDefaultWrap(window, kind), "$kind on $window")
            }
            assertEquals(window == WindowClass.Compact, kitViewerDefaultWrap(window, null), "null kind on $window")
        }
    }

    @Test
    fun constructorsAreDataOnlyWithDartsDefaults() {
        val text = KitViewerContent.Text("hello")
        assertEquals(KitViewerKind.Text, text.kind); assertFalse(text.truncated); assertNull(text.totalLines)

        val code = KitViewerContent.Code("val x = 1")
        assertEquals(KitViewerKind.Code, code.kind); assertNull(code.language); assertFalse(code.truncated)
        assertNull(code.totalLines); assertNull(code.initialLine)

        val markdown = KitViewerContent.Markdown("# Title")
        assertEquals(KitViewerKind.Markdown, markdown.kind); assertFalse(markdown.truncated)

        val image = KitViewerContent.Image(byteArrayOf(1, 2, 3))
        assertEquals(KitViewerKind.Image, image.kind); assertNull(image.semanticsLabel)

        val pdf = KitViewerContent.Pdf(pageCount = 3, renderPage = { _, _ -> ViewerPdfPage(ByteArray(0), 1, 1) })
        assertEquals(KitViewerKind.Pdf, pdf.kind); assertEquals(3, pdf.pageCount); assertNull(pdf.cancelPage)

        val svg = KitViewerContent.Svg("<svg/>")
        assertEquals(KitViewerKind.Svg, svg.kind); assertNull(svg.original); assertFalse(svg.truncated)

        val delimited = KitViewerContent.Delimited(listOf(listOf("a", "b"), listOf("1", "2")))
        assertEquals(KitViewerKind.Delimited, delimited.kind); assertNull(delimited.original)
        assertTrue(delimited.header); assertFalse(delimited.truncated)
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), delimited.rows)

        val binary = KitViewerContent.Binary()
        assertEquals(KitViewerKind.Binary, binary.kind); assertNull(binary.mimeType); assertNull(binary.byteLength)
    }

    @Test
    fun copyTextPerKind() {
        // text, code and markdown carry their text; svg prefers the file's own text; delimited its original;
        // image, pdf and binary have none (kit_viewer_content.dart:222-229).
        assertEquals("hello", KitViewerContent.Text("hello").copyText)
        assertEquals("val x = 1", KitViewerContent.Code("val x = 1").copyText)
        assertEquals("# Title", KitViewerContent.Markdown("# Title").copyText)
        assertEquals("<svg/>", KitViewerContent.Svg("<svg/>").copyText)
        assertEquals("file", KitViewerContent.Svg("<svg/>", original = "file").copyText)
        assertNull(KitViewerContent.Delimited(listOf(listOf("a"))).copyText)
        assertEquals("a,b", KitViewerContent.Delimited(listOf(listOf("a")), original = "a,b").copyText)
        assertNull(KitViewerContent.Image(byteArrayOf(1)).copyText)
        assertNull(KitViewerContent.Pdf(pageCount = 1, renderPage = { _, _ -> ViewerPdfPage(ByteArray(0), 1, 1) }).copyText)
        assertNull(KitViewerContent.Binary().copyText)
    }

    @Test
    fun pdfPageCarriesEncodedBytesAndPixelSize() {
        val a = ViewerPdfPage(byteArrayOf(1, 2, 3), width = 640, height = 480)
        val b = ViewerPdfPage(byteArrayOf(1, 2, 3), width = 640, height = 480)
        val c = ViewerPdfPage(byteArrayOf(1, 2, 4), width = 640, height = 480)
        assertEquals(a, b); assertEquals(a.hashCode(), b.hashCode()); assertNotEquals(a, c)
        assertNotEquals(a, ViewerPdfPage(byteArrayOf(1, 2, 3), 640, 481))
    }

    @Test
    fun sourceEqualityIsContentIdentityAndTheSameLoad() {
        val content = KitViewerContent.Text("x")
        assertEquals(KitViewerSource(content), KitViewerSource(content))
        // Two equal values are different sources: Dart compares the content by identity.
        assertNotEquals(KitViewerSource(KitViewerContent.Text("x")), KitViewerSource(KitViewerContent.Text("x")))
        val load: suspend () -> KitViewerContent = { KitViewerContent.Text("x") }
        assertEquals(KitViewerSource.load(load), KitViewerSource.load(load))
        assertNotEquals(KitViewerSource.load(load), KitViewerSource.load { KitViewerContent.Text("x") })
        assertNotEquals<KitViewerSource>(KitViewerSource(content), KitViewerSource.load(load))
        // A source with content in hand exposes it; a loaded one loads on open.
        assertEquals(content, KitViewerSource(content).content)
        assertNull(KitViewerSource.load(load).content)
    }
}
