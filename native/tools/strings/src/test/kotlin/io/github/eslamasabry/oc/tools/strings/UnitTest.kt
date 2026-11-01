package io.github.eslamasabry.oc.tools.strings

import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluralRulesTest {
    @Test
    fun `Arabic picks zero one two few many other for 0 1 2 3 11 100`() {
        assertEquals(PluralRules.ALL, listOf(0, 1, 2, 3, 11, 100).map { PluralRules.category("ar", it) })
        assertEquals("few", PluralRules.category("ar", 103))
        assertEquals("many", PluralRules.category("ar", 111))
    }

    @Test
    fun `other languages`() {
        assertEquals(listOf("other", "one", "other"), listOf(0, 1, 2).map { PluralRules.category("en", it) })
        assertEquals(listOf("one", "one", "other", "many"), listOf(0, 1, 2, 1_000_000).map { PluralRules.category("pt", it) })
        assertEquals(listOf("many", "one", "few", "many", "many", "one"), listOf(0, 1, 2, 5, 11, 21).map { PluralRules.category("ru", it) })
        assertEquals("other", PluralRules.category("zh", 1))
    }

    @Test
    fun `sole members are where =N can stand in for a category`() {
        assertEquals(1, PluralRules.soleMember("en", "one"))
        assertEquals(0, PluralRules.soleMember("ar", "zero"))
        assertEquals(2, PluralRules.soleMember("ar", "two"))
        assertNull(PluralRules.soleMember("pt", "one"))
        assertNull(PluralRules.soleMember("ru", "one"))
    }
}

class IcuTest {
    @Test
    fun `parses arguments, plurals and literal apostrophes`() {
        val n = Icu.parse("It's {name}: {count, plural, =0{none} one{1 item} other{{count} items}}!")
        assertEquals(IcuNode.Text("It's "), n[0])
        assertEquals(IcuNode.Arg("name"), n[1])
        val p = assertIs<IcuNode.Plural>(n[3])
        assertEquals(listOf("=0", "one", "other"), p.branches.keys.toList())
        assertEquals(listOf("name", "count"), Icu.argOrder(n))
    }

    @Test
    fun `empty branches and multi-line text`() {
        val p = assertIs<IcuNode.Plural>(Icu.parse("{n, plural, =0{} other{a\nb}}").single())
        assertEquals(emptyList(), p.branches.getValue("=0"))
    }

    @Test
    fun `rejects broken ICU`() {
        assertFailsWith<IcuParseException> { Icu.parse("{n, plural, one{x}") }
        assertFailsWith<IcuParseException> { Icu.parse("a } b") }
        assertFailsWith<IcuParseException> { Icu.parse("{n, number}") }
        assertFailsWith<IcuParseException> { Icu.parse("{n, plural, offset:1 other{x}}") }
    }
}

class ConverterTest {
    private fun arb(locale: String, vararg pairs: Pair<String, String>, meta: Map<String, LinkedHashMap<String, PlaceholderMeta>> = emptyMap()) =
        ArbFile(locale, linkedMapOf(*pairs), meta)

    @Test
    fun `nested plurals, selects and translation-only placeholders are problems, not output`() {
        val c = Converter({ "chat" }).convert(
            listOf(
                arb("en", "a" to "{n, plural, one{{m, plural, one{x} other{y}}} other{z}}", "b" to "Hi {name}", "c" to "{n, plural, one{1} other{#}}"),
                arb("ar", "b" to "مرحبا {nom}", "c" to "{n, select, a{x} other{y}}"),
            ),
        )
        val kinds = c.problems.map { "${it.locale}:${it.key}:${it.kind}" }
        assertEquals(
            listOf("en:a:NESTED_PLURAL", "ar:b:UNKNOWN_PLACEHOLDER", "ar:c:SELECT"),
            kinds,
        )
        assertEquals(setOf("b", "c"), c["en"].convertedKeys())
        assertEquals(emptySet(), c["ar"].convertedKeys())
        assertEquals(listOf("b", "c"), c["ar"].skipped)
    }

    @Test
    fun `a plural must leave no category uncovered`() {
        val c = Converter({ "x" }).convert(listOf(arb("en", "a" to "{n, plural, one{x}}")))
        assertEquals(ProblemKind.PLURAL_WITHOUT_OTHER, c.problems.single().kind)
    }

    @Test
    fun `text that already looks like a CMP format token is refused`() {
        val c = Converter({ "x" }).convert(listOf(arb("en", "a" to "use %1\$s literally")))
        assertEquals(ProblemKind.LITERAL_FORMAT_TOKEN, c.problems.single().kind)
    }

    @Test
    fun `strings_os refuses a literal percent next to placeholders`() {
        val c = Converter({ "os" }).convert(listOf(arb("en", "a" to "{p}% used", "b" to "100% sure"), arb("ar", "a" to "{p}٪", "b" to "١٠٠٪")))
        assertEquals(listOf("en:a", "ar:a"), c.problems.map { "${it.locale}:${it.key}" })
        val xml = XmlWriter.render(c["en"].files.getValue("os"), "os", "app_en.arb")
        assertTrue("<string name=\"b\" formatted=\"false\">100% sure</string>" in xml, xml)
    }
}

class EscapeTest {
    /** What CMP 1.12.2 stores for a value: DOM textContent, then handleSpecialCharacters. */
    private fun cmpRead(escaped: String): String {
        val xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?><resources><string name=\"k\">$escaped</string></resources>"
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
        val el = doc.getElementsByTagName("string").item(0) as Element
        return XmlText.cmpUnescape(el.textContent)
    }

    private val samples = listOf(
        "It's \"quoted\" & <b>bold</b>",
        "@{name} first", "?why", "line1\nline2\ttab",
        "back\\slash", "\\n is not a newline", "\\\\u0041 stays", "trailing\\",
        "  two  spaces  ", "\u200E@x \u2068iso\u2069 \u200Bzw", "100% · %1\$s",
    )

    @Test
    fun `CMP profile reads back as the original text`() {
        for (s in samples) assertEquals(s, cmpRead(XmlText.escape(s, XmlText.Profile.CMP)), s)
    }

    @Test
    fun `Android profile reads back the same in CMP and leaves nothing aapt2 would reinterpret`() {
        for (s in samples) {
            val e = XmlText.escape(s, XmlText.Profile.ANDROID)
            assertEquals(s, cmpRead(e), s)
            assertFalse('\'' in e || '"' in e, e)
            assertFalse(e.startsWith("@") || e.startsWith("?") || e.startsWith(" ") || e.endsWith(" ") || "  " in e, e)
        }
    }

    @Test
    fun `feature files keep apostrophes and quotes readable`() {
        assertEquals("It's \"x\" &amp; y", XmlText.escape("It's \"x\" & y", XmlText.Profile.CMP))
        assertEquals("It\\u0027s \\u0022x\\u0022", XmlText.escape("It's \"x\"", XmlText.Profile.ANDROID))
        assertEquals("\\u0040me ?", XmlText.escape("@me ?", XmlText.Profile.ANDROID))
        assertEquals("a\\nb", XmlText.escape("a\nb", XmlText.Profile.CMP))
        assertEquals("\\u200E@x", XmlText.escape("\u200E@x", XmlText.Profile.CMP))
    }

    @Test
    fun `random strings over the dangerous alphabet survive both profiles`() {
        val alphabet = "\\nut0A1'\"@? \n\t&<>%\$\u200E\u061Cش"
        val rnd = Random(6)
        repeat(5_000) {
            val s = String(CharArray(rnd.nextInt(0, 12)) { alphabet[rnd.nextInt(alphabet.length)] })
            for (p in XmlText.Profile.entries) assertEquals(s, cmpRead(XmlText.escape(s, p)), "$p: ${s.map { it.code }}")
        }
    }
}
