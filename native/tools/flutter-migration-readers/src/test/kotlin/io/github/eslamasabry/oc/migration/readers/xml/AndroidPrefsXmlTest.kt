package io.github.eslamasabry.oc.migration.readers.xml

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidPrefsXmlTest {

    private fun parse(s: String) = AndroidPrefsXml.parse(s.toByteArray(Charsets.UTF_8))

    @Test
    fun `reads every type SharedPreferencesImpl writes, in file order`() {
        val r = parse(
            """
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
            <map>
                <string name="flutter.s">hello</string>
                <boolean name="flutter.b" value="true" />
                <long name="flutter.l" value="-9223372036854775808" />
                <int name="i" value="7" />
                <float name="f" value="1.5" />
                <double name="d" value="2.25" />
                <set name="set">
                    <string>a</string>
                    <string>b</string>
                </set>
                <null name="n" />
                <string name="empty"></string>
                <string name="selfClosed" />
            </map>
            """.trimIndent(),
        )
        assertEquals(AndroidLoad.Loaded, r.load)
        assertEquals(emptyList(), r.problems)
        assertEquals(listOf("flutter.s", "flutter.b", "flutter.l", "i", "f", "d", "set", "n", "empty", "selfClosed"), r.entries.keys.toList())
        assertEquals(RawPref.Str("hello"), r.entries["flutter.s"])
        assertEquals(RawPref.Bool(true), r.entries["flutter.b"])
        assertEquals(RawPref.Int64(Long.MIN_VALUE), r.entries["flutter.l"])
        assertEquals(RawPref.Int32(7), r.entries["i"])
        assertEquals(RawPref.F32(1.5f), r.entries["f"])
        assertEquals(RawPref.F64(2.25), r.entries["d"])
        assertEquals(RawPref.StrSet(linkedSetOf("a", "b")), r.entries["set"])
        assertEquals(RawPref.Null, r.entries["n"])
        assertEquals(RawPref.Str(""), r.entries["empty"])
        assertEquals(RawPref.Str(""), r.entries["selfClosed"])
    }

    @Test
    fun `decodes FastXmlSerializer escapes including control characters a strict XML parser rejects`() {
        val r = parse("<map><string name=\"k&amp;&quot;\">a&lt;b&gt;&amp;&#10;&#1;&#27;&#x1F680;&apos;</string></map>")
        assertEquals(AndroidLoad.Loaded, r.load)
        assertEquals(RawPref.Str("a<b>&\n\u0001\u001b🚀'"), r.entries["k&\""])
    }

    @Test
    fun `writer output round-trips through the reader byte-exactly for awkward text`() {
        val text = "<b>&\"q\"</b>\tTab\nLine\r\u0000\u0001\u001f مرحبا 🚀 �"
        val entries = linkedMapOf<String, RawPref>(
            "flutter.x" to RawPref.Str(text),
            "flutter.list" to RawPref.StrSet(linkedSetOf("<", "&", "")),
            "flutter.l" to RawPref.Int64(1),
            "flutter.n" to RawPref.Null,
        )
        val back = AndroidPrefsXml.parse(AndroidPrefsXmlWriter.write(entries))
        assertEquals(AndroidLoad.Loaded, back.load)
        assertEquals(entries, back.entries)
    }

    @Test
    fun `an empty map is loaded, an empty file loads as empty map with a fatal problem`() {
        assertEquals(AndroidLoad.Loaded, parse("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map />\n").load)
        val empty = AndroidPrefsXml.parse(ByteArray(0))
        assertEquals(AndroidLoad.LoadsEmpty, empty.load)
        assertEquals(listOf(XmlProblem.EmptyFile), empty.problems)
    }

    @Test
    fun `a truncated file loads empty on Android but keeps a salvage count for the integrity check`() {
        val full = AndroidPrefsXmlWriter.write(linkedMapOf("flutter.a" to RawPref.Str("1"), "flutter.b" to RawPref.Str("2"), "flutter.c" to RawPref.Str("3")))
        val cut = full.copyOfRange(0, full.size - 30)
        val r = AndroidPrefsXml.parse(cut)
        assertEquals(AndroidLoad.LoadsEmpty, r.load)
        assertTrue(XmlProblem.Truncated in r.problems)
        assertEquals(2, r.entries.size, "salvaged entries before the cut")
        assertTrue(r.androidMap.isEmpty())
        // Every cut before the end of </map> loads empty; only the trailing newline may go. Never throws.
        val endOfMap = String(full, Charsets.UTF_8).lastIndexOf("</map>") + "</map>".length
        for (n in 0 until full.size) {
            val p = AndroidPrefsXml.parse(full.copyOfRange(0, n))
            assertEquals(if (n >= endOfMap) AndroidLoad.Loaded else AndroidLoad.LoadsEmpty, p.load, "cut at $n")
        }
    }

    @Test
    fun `garbage and wrong roots load empty`() {
        for (bad in listOf("not xml at all", "<string name=\"x\">v</string>", "</map>", "<map><string name=\"x\">v</map>", "<map><string name=\"x\"><b/></string></map>")) {
            val r = parse(bad)
            assertEquals(AndroidLoad.LoadsEmpty, r.load, bad)
        }
    }

    @Test
    fun `bad primitives and unknown tags fail the whole file like XmlUtils does`() {
        val badLong = parse("<map><string name=\"ok\">1</string><long name=\"x\" value=\"12abc\" /><string name=\"after\">2</string></map>")
        assertEquals(AndroidLoad.LoadsEmpty, badLong.load)
        assertTrue(XmlProblem.BadPrimitive in badLong.problems)
        assertEquals(setOf("ok", "after"), badLong.entries.keys, "salvage continues past the bad element")

        val badBool = parse("<map><boolean name=\"x\" value=\"yes\" /></map>")
        assertEquals(AndroidLoad.LoadsEmpty, badBool.load)

        val unknown = parse("<map><widget name=\"x\" value=\"1\" /></map>")
        assertEquals(AndroidLoad.LoadsEmpty, unknown.load)
        assertTrue(XmlProblem.UnknownTag in unknown.problems)

        val unknownEntity = parse("<map><string name=\"x\">&nbsp;</string></map>")
        assertEquals(AndroidLoad.LoadsEmpty, unknownEntity.load)
    }

    @Test
    fun `tags XmlUtils can read but the editor never writes are kept as Other`() {
        val r = parse("<map><int-array name=\"arr\" num=\"2\"><item value=\"1\" /><item value=\"2\" /></int-array></map>")
        assertEquals(AndroidLoad.Loaded, r.load)
        val v = r.entries["arr"]
        assertTrue(v is RawPref.Other && v.tag == "int-array" && v.rawXml.startsWith("<int-array"))
    }

    @Test
    fun `duplicates keep the last value, nameless values are skipped, trailing content is ignored`() {
        val r = parse("<map><string name=\"k\">1</string><string>nameless</string><string name=\"k\">2</string></map>junk")
        assertEquals(AndroidLoad.Loaded, r.load)
        assertEquals(RawPref.Str("2"), r.entries["k"])
        assertEquals(1, r.entries.size)
        assertTrue(r.problems.containsAll(listOf(XmlProblem.DuplicateName, XmlProblem.MissingName, XmlProblem.TrailingContent)))
    }

    @Test
    fun `binary XML and malformed UTF-8 are reported, BOM is accepted`() {
        val abx = AndroidPrefsXml.parse(byteArrayOf(0x41, 0x42, 0x58, 0x00, 1, 2, 3))
        assertEquals(listOf(XmlProblem.BinaryXml), abx.problems)
        assertEquals(AndroidLoad.LoadsEmpty, abx.load)

        val badUtf8 = "<map><string name=\"k\">".toByteArray() + byteArrayOf(0xC3.toByte(), 0x28) + "</string></map>".toByteArray()
        val r = AndroidPrefsXml.parse(badUtf8)
        assertEquals(AndroidLoad.Loaded, r.load)
        assertTrue(XmlProblem.MalformedUtf8 in r.problems)

        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "<map><boolean name=\"b\" value=\"FALSE\" /></map>".toByteArray()
        assertEquals(RawPref.Bool(false), AndroidPrefsXml.parse(bom).entries["b"])
    }

    @Test
    fun `the bak twin is authoritative, as in SharedPreferencesImpl loadFromDisk`() {
        val dir = Files.createTempDirectory("prefs").toFile()
        val f = SharedPrefsFile(dir, "FlutterSharedPreferences")
        assertNull(f.read())
        assertFalse(f.exists())
        f.xml.writeBytes(AndroidPrefsXmlWriter.write(mapOf("flutter.k" to RawPref.Str("live"))))
        assertEquals(RawPref.Str("live"), f.read()!!.entries["flutter.k"])
        f.bak.writeBytes(AndroidPrefsXmlWriter.write(mapOf("flutter.k" to RawPref.Str("from-bak"))))
        assertEquals(f.bak, f.authoritative())
        assertEquals(RawPref.Str("from-bak"), f.read()!!.entries["flutter.k"])
        dir.deleteRecursively()
    }

    @Test
    fun `toString never prints values`() {
        val secretish = "server-password-in-a-prompt"
        val r = parse("<map><string name=\"k\">$secretish</string><set name=\"s\"><string>$secretish</string></set></map>")
        assertFalse(r.toString().contains(secretish))
        assertFalse(r.entries.toString().contains(secretish))
    }
}
