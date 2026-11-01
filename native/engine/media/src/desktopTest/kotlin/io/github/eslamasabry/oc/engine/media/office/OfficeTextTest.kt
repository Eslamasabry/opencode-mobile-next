package io.github.eslamasabry.oc.engine.media.office

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Port of `test/office_text_test.dart` (fixtures copied byte for byte from `test/fixtures/office/`). */
class OfficeTextTest {
    private fun fixture(name: String): ByteArray =
        requireNotNull(javaClass.classLoader.getResourceAsStream("office/$name")) { name }.use { it.readBytes() }

    private fun ok(result: Outcome<OfficeText>): OfficeText =
        when (result) {
            is Outcome.Ok -> result.value
            is Outcome.Failed -> fail("expected text, got ${result.failure}")
        }

    private fun problem(result: Outcome<OfficeText>): MediaProblem? = ((result as? Outcome.Failed)?.failure as? Failure.Media)?.problem

    @Test
    fun `B-MEDIA-006 a workbook becomes CSV sheet by sheet (openpyxl and LibreOffice)`() {
        for (name in listOf("book.xlsx", "book_libreoffice.xlsx")) {
            val result = ok(OfficeText.read(name, fixture(name)))
            assertEquals(2, result.sections, name)
            assertFalse(result.truncated)
            val lines = result.text.split('\n')
            assertEquals("# Sheet: Accounts", lines[0], name)
            assertEquals("Name,Balance,Active,Note", lines[1], name)
            assertEquals("\"Savings, main\",1520.5,TRUE,\"says \"\"hi\"\"\"", lines[2], name)
            assertEquals("Credit,-300,FALSE", lines[3], name)
            assertEquals(",,,,,far cell", lines[4], name)
            assertTrue("# Sheet: Offers\nOffer,Months\nIPP,12" in result.text, name)
            assertEquals(6, result.lines, name)
        }
    }

    @Test
    fun `B-MEDIA-006 a Word document becomes its paragraphs`() {
        val result = ok(OfficeText.read("doc.docx", fixture("doc.docx")))
        assertEquals("First paragraph.\n\nSecond\twith tab.", result.text)
        assertEquals(2, result.lines)
        assertEquals(1, result.sections)
    }

    @Test
    fun `B-MEDIA-006 a file that only has the name is refused, not half-read`() {
        assertEquals(MediaProblem.OfficeUnreadable, problem(OfficeText.read("old.xlsx", ByteArray(64) { 7 })))
        // A real ZIP that is not a workbook.
        assertEquals(MediaProblem.OfficeUnreadable, problem(OfficeText.read("notes.xlsx", fixture("doc.docx"))))
    }

    @Test
    fun `B-MEDIA-006 which files are read this way`() {
        assertTrue(OfficeText.isOfficeDocument("Budget.XLSX"))
        assertTrue(OfficeText.isOfficeDocument("macro.xlsm"))
        assertTrue(OfficeText.isOfficeDocument("brief.docx"))
        assertFalse(OfficeText.isOfficeDocument("legacy.xls"))
        assertFalse(OfficeText.isOfficeDocument("notes.txt"))
    }

    @Test
    fun `B-MEDIA-006 encrypted, DOCTYPE-bearing and sheetless workbooks are refused`() {
        val encrypted = zip("xl/workbook.xml" to "<workbook/>").also { it[6 + centralOffset(it)] = 1 } // central flag bit 0
        assertEquals(MediaProblem.OfficeUnreadable, problem(OfficeText.read("e.xlsx", encrypted)))
        val doctype = zip("xl/workbook.xml" to "<!DOCTYPE x [<!ENTITY a SYSTEM \"file:///etc/passwd\">]><workbook>&a;</workbook>")
        assertEquals(MediaProblem.OfficeUnreadable, problem(OfficeText.read("d.xlsx", doctype)))
        val empty = zip("xl/workbook.xml" to "<workbook><sheets/></workbook>")
        assertEquals(MediaProblem.OfficeEmpty, problem(OfficeText.read("s.xlsx", empty)))
    }

    @Test
    fun `B-MEDIA-006 a long document is cut at 400,000 characters with the truncated flag`() {
        val paragraph = "<w:p><w:r><w:t>${"word ".repeat(2000)}</w:t></w:r></w:p>"
        val xml = "<w:document xmlns:w=\"urn:w\"><w:body>${paragraph.repeat(60)}</w:body></w:document>"
        val result = ok(OfficeText.read("long.docx", zip("word/document.xml" to xml)))
        assertTrue(result.truncated)
        assertTrue(result.text.length <= OfficeText.MAX_CHARS)
    }

    @Test
    fun `B-MEDIA-006 inline strings and shared strings with phonetic runs read as their text`() {
        val workbook = "<workbook xmlns:r=\"urn:r\"><sheets><sheet name=\"One\" r:id=\"rId1\"/></sheets></workbook>"
        val rels = "<Relationships><Relationship Id=\"rId1\" Target=\"worksheets/a.xml\"/></Relationships>"
        val shared = "<sst><si><r><t>Ka</t></r><r><t>nji</t></r><rPh><t>phonetic</t></rPh></si></sst>"
        val sheet =
            "<worksheet><sheetData><row><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"C1\" t=\"inlineStr\"><is><t>in &amp; line</t></is></c>" +
                "</row></sheetData></worksheet>"
        val result =
            ok(
                OfficeText.read(
                    "r.xlsx",
                    zip("xl/workbook.xml" to workbook, "xl/_rels/workbook.xml.rels" to rels, "xl/sharedStrings.xml" to shared, "xl/worksheets/a.xml" to sheet),
                ),
            )
        assertEquals("# Sheet: One\nKanji,,in & line", result.text)
    }

    @Test
    fun `B-MEDIA-006 raw inflate matches java util zip and refuses bombs and corrupt streams`() {
        val random = Random(7)
        val inputs =
            listOf(
                ByteArray(0),
                "hello hello hello hello".encodeToByteArray(),
                ByteArray(200_000) { (it % 251).toByte() },
                ByteArray(70_000).also(random::nextBytes),
            )
        for (input in inputs) {
            for (level in listOf(0, 1, 6, 9)) {
                val packed = deflate(input, level)
                assertContentEquals(input, RawInflate.inflate(packed, 0, packed.size, input.size + 1), "level $level")
            }
        }
        val bomb = deflate(ByteArray(1_000_000), 9)
        assertNull(RawInflate.inflate(bomb, 0, bomb.size, 999_999))
        val packed = deflate("some text".encodeToByteArray(), 6)
        assertNull(RawInflate.inflate(packed, 0, packed.size - 2, 1000))
        assertNull(RawInflate.inflate(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()), 0, 3, 1000))
    }

    private fun deflate(input: ByteArray, level: Int): ByteArray {
        val deflater = Deflater(level, true)
        deflater.setInput(input)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return out.toByteArray()
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, text) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.encodeToByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** Offset of the first central-directory header (signature PK\1\2). */
    private fun centralOffset(bytes: ByteArray): Int =
        (0 until bytes.size - 4).first {
            bytes[it] == 0x50.toByte() && bytes[it + 1] == 0x4B.toByte() && bytes[it + 2] == 1.toByte() && bytes[it + 3] == 2.toByte()
        } + 2
}
