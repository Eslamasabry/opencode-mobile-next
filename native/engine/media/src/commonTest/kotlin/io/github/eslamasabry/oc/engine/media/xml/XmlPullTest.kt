package io.github.eslamasabry.oc.engine.media.xml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The XML reader under Office text and the SVG check (plan 10 §10.7 M7; serves B-MEDIA-006 and B-CHAT-107). */
class XmlPullTest {
    private fun events(xml: String, limits: XmlLimits = XmlLimits.Office): List<XmlEvent> {
        val parser = XmlPull(xml, limits)
        return generateSequence { parser.next() }.toList()
    }

    private fun refused(xml: String, limits: XmlLimits = XmlLimits.Office): XmlProblem =
        assertFailsWith<XmlRefused> { events(xml, limits) }.problem

    @Test
    fun `B-MEDIA-006 reads elements, attributes, text, CDATA and the five entities`() {
        val e = events("<?xml version=\"1.0\"?><a x='1' y=\"&lt;&amp;&#65;&#x42;\"><!-- c --><b/>t&gt;<![CDATA[<raw>]]></a>")
        assertEquals(XmlEvent.Instruction("xml"), e[0])
        val a = e[1] as XmlEvent.Start
        assertEquals("a", a.name.local)
        assertEquals("<&AB", a.attr("y"))
        assertEquals(listOf("b", "b"), e.subList(2, 4).map { (it as? XmlEvent.Start)?.name?.local ?: (it as XmlEvent.End).name.local })
        assertEquals(XmlEvent.Text("t>"), e[4])
        assertEquals(XmlEvent.Text("<raw>"), e[5])
    }

    @Test
    fun `B-MEDIA-006 resolves namespaces for elements and prefixed attributes`() {
        val e = events("<w:doc xmlns:w=\"urn:w\" xmlns:r=\"urn:r\"><w:p r:id=\"1\" plain=\"2\"/></w:doc>")
        val p = e[1] as XmlEvent.Start
        assertEquals("urn:w", p.name.namespace)
        assertEquals("w:p", p.name.qualified)
        assertEquals("urn:r", p.attributes[0].name.namespace)
        assertEquals(null, p.attributes[1].name.namespace)
    }

    @Test
    fun `B-MEDIA-006 a DOCTYPE, an unknown entity or a bad structure fails the read`() {
        assertEquals(XmlProblem.Declaration, refused("<!DOCTYPE a [<!ENTITY x \"y\">]><a>&x;</a>"))
        assertEquals(XmlProblem.Entity, refused("<a>&nbsp;</a>"))
        assertEquals(XmlProblem.Malformed, refused("<a><b></a></b>"))
        assertEquals(XmlProblem.Malformed, refused("<a>"))
        assertEquals(XmlProblem.Malformed, refused("<a/><b/>"))
        assertEquals(XmlProblem.Malformed, refused("<a x='1' x='2'/>"))
        assertEquals(XmlProblem.Malformed, refused("<a>&#0;</a>"))
        assertEquals(XmlProblem.Malformed, refused("<p:a/>"))
        assertEquals(XmlProblem.Malformed, refused(""))
        assertEquals(XmlProblem.Malformed, refused("text<a/>"))
    }

    @Test
    fun `B-MEDIA-006 depth and element count are bounded`() {
        val limits = XmlLimits(maxDepth = 3, maxElements = 5)
        assertEquals(XmlProblem.TooDeep, refused("<a><b><c><d/></c></b></a>", limits))
        assertEquals(XmlProblem.TooLarge, refused("<a>${"<b/>".repeat(5)}</a>", limits))
        events("<a><b><c/></b></a>", limits)
        assertEquals(64, XmlLimits.Office.maxDepth)
        assertEquals(2_000_000, XmlLimits.Office.maxElements)
    }
}
