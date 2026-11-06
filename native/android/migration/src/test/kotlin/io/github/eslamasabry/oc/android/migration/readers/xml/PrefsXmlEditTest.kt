package io.github.eslamasabry.oc.android.migration.readers.xml

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** E00-S08: the in-place editor the Flutter-era sweep writes retained copies with (B-SEC-201). */
class PrefsXmlEditTest {
    private val file = AndroidPrefsXmlWriter.write(
        linkedMapOf(
            "keep.a" to RawPref.Str("cipher<&>\"a\"\n"),
            "doomed" to RawPref.Str("secret"),
            "keep.b" to RawPref.Int64(7),
            "keep.set" to RawPref.StrSet(linkedSetOf("x", "y")),
            "rows" to RawPref.Str("[1,2]"),
        ),
    )

    private fun edited(r: PrefsXmlEdit.Result): String = String(assertIs<PrefsXmlEdit.Result.Edited>(r).bytes, Charsets.UTF_8)

    @Test
    fun `B-SEC-201 removing an entry leaves every other byte of the file as it was`() {
        val out = edited(PrefsXmlEdit.edit(file, setOf("doomed"), emptyMap()))
        val expected = String(file, Charsets.UTF_8).lines().filterNot { "name=\"doomed\"" in it }.joinToString("\n")
        assertEquals(expected, out)
    }

    @Test
    fun `B-SEC-201 a replaced and an added value read back as written, the rest unchanged`() {
        val out = PrefsXmlEdit.edit(
            file,
            setOf("doomed"),
            linkedMapOf("rows" to RawPref.Str("[2]"), "added" to RawPref.Str("<new & \"quoted\">")),
        )
        val read = AndroidPrefsXml.parse(assertIs<PrefsXmlEdit.Result.Edited>(out).bytes)
        assertEquals(AndroidLoad.Loaded, read.load)
        assertEquals(
            mapOf(
                "keep.a" to RawPref.Str("cipher<&>\"a\"\n"),
                "keep.b" to RawPref.Int64(7),
                "keep.set" to RawPref.StrSet(linkedSetOf("x", "y")),
                "rows" to RawPref.Str("[2]"),
                "added" to RawPref.Str("<new & \"quoted\">"),
            ),
            read.entries,
        )
    }

    @Test
    fun `B-SEC-201 every copy of a duplicated name goes, so the earlier one cannot resurface`() {
        val text = String(file, Charsets.UTF_8).replace("</map>", "    <string name=\"doomed\">again</string>\n</map>")
        val out = edited(PrefsXmlEdit.edit(text.toByteArray(), setOf("doomed"), emptyMap()))
        assertEquals(false, "doomed" in AndroidPrefsXml.parse(out.toByteArray()).entries)
        assertEquals(false, "secret" in out || "again" in out)
    }

    @Test
    fun `B-SEC-201 nothing to do, a file Android loads empty, or a value it never writes are not edited`() {
        assertEquals(PrefsXmlEdit.Result.Unchanged, PrefsXmlEdit.edit(file, setOf("absent"), emptyMap()))
        val truncated = String(file, Charsets.UTF_8).substringBefore("</map>").toByteArray()
        assertEquals(PrefsXmlEdit.Result.Refused(PrefsXmlEdit.Why.NotLoaded), PrefsXmlEdit.edit(truncated, setOf("doomed"), emptyMap()))
        assertEquals(
            PrefsXmlEdit.Result.Refused(PrefsXmlEdit.Why.Unwritable),
            PrefsXmlEdit.edit(file, emptySet(), mapOf("x" to RawPref.Other("int-array", "<int-array />"))),
        )
        val empty = AndroidPrefsXmlWriter.write(emptyMap())
        assertEquals(PrefsXmlEdit.Result.Refused(PrefsXmlEdit.Why.NoMapEnd), PrefsXmlEdit.edit(empty, emptySet(), mapOf("x" to RawPref.Str("v"))))
    }
}
