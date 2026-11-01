package io.github.eslamasabry.oc.engine.media.preview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Port of `test/static_svg_test.dart`. */
class StaticSvgTest {
    @Test
    fun `B-CHAT-107 local shapes, text and gradient paints keep their exact source`() {
        val source =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 300 180\"><defs><linearGradient id=\"paint\">" +
                "<stop offset=\"0\" stop-color=\"#fff\"/><stop offset=\"1\" stop-color=\"#123456\"/></linearGradient></defs>" +
                "<rect width=\"300\" height=\"180\" fill=\"url(#paint)\"/><text x=\"10\" y=\"40\">Local result</text></svg>"
        val svg = assertNotNull(StaticSvg.parse(source))
        assertEquals(source, svg.source)
        assertEquals(300.0, svg.width)
        assertEquals(180.0, svg.height)
        // Through the preview model too.
        assertNotNull(PreviewData("a.svg", null, bytes = source.encodeToByteArray()).svg())
    }

    @Test
    fun `B-CHAT-107 active or external SVG keeps the Source fallback`() {
        for (content in listOf(
            "<script>alert(1)</script>",
            "<foreignObject/>",
            "<image href=\"https://example.org/private\"/>",
            "<use href=\"#cycle\"/>",
            "<animate attributeName=\"x\"/>",
            "<rect onclick=\"run()\"/>",
            "<rect fill=\"url(https://example.org/a)\"/>",
            "<rect style=\"fill: u\\72l(https://example.org/a)\"/>",
            "<style>@import \"https://example.org/a\";</style>",
            "<rect xmlns=\"http://example.org/other\"/>",
            "<rect id=\"same\"/><circle id=\"same\"/>",
            "<rect fill=\"url(#missing)\"/>",
            "<linearGradient id=\"g\"><rect/></linearGradient>",
        )) {
            assertNull(StaticSvg.parse("<svg viewBox=\"0 0 10 10\">$content</svg>"), content)
        }
    }

    @Test
    fun `B-CHAT-107 declarations, malformed XML and resource bounds are rejected`() {
        assertNull(StaticSvg.parse("<!DOCTYPE svg [<!ENTITY a \"value\">]><svg/>"))
        assertNull(StaticSvg.parse("<?xml-stylesheet href=\"https://example.org/a\"?><svg/>"))
        assertNull(StaticSvg.parse("<svg><g>"))
        assertNull(StaticSvg.parse("<svg width=\"Infinity\"/>"))
        assertNull(StaticSvg.parse("<svg viewBox=\"0 0 0 10\"/>"))
        assertNull(StaticSvg.parse("<svg>${"<g>".repeat(33)}${"</g>".repeat(33)}</svg>"))
        assertNull(StaticSvg.parse("<svg>${"<rect/>".repeat(1001)}</svg>"))
        assertNull(StaticSvg.parse("<svg><path d=\"${"M 0 0 ".repeat(3000)}\"/></svg>"))
        assertNull(StaticSvg.parse("x".repeat(256 * 1024 + 1)))
        // The XML declaration itself is fine.
        assertNotNull(StaticSvg.parse("<?xml version=\"1.0\"?><svg viewBox=\"0 0 10 10\"/>"))
    }

    @Test
    fun `B-CHAT-107 dash expansion is rejected before any renderer sees it`() {
        for (attributes in listOf(
            "stroke-dasharray=\"1e-300 1e-300\"",
            "stroke-dashoffset=\"1\"",
            "style=\"stroke-dasharray:1e-300 1e-300\"",
            "style=\"stroke-dashoffset:1\"",
        )) {
            assertNull(
                StaticSvg.parse(
                    "<svg viewBox=\"0 0 10 10\"><path d=\"M0 0 L10 0\" fill=\"none\" stroke=\"#000\" $attributes/></svg>",
                ),
                attributes,
            )
        }
    }
}
