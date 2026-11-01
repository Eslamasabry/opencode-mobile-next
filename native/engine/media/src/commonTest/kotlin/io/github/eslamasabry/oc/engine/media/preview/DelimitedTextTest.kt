package io.github.eslamasabry.oc.engine.media.preview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of `test/delimited_text_test.dart`. */
class DelimitedTextTest {
    private fun rows(text: String, sep: Char) = DelimitedText.parse(text, sep).rows.map { it.toList() }

    @Test
    fun `B-CHAT-021 CSV preserves quoted separators, quotes, multiline fields and trailing cells`() {
        val data = DelimitedText.parse("﻿name,value,\r\n\"a,b\",\"say \"\"hi\"\"\",\r\n\"line\r\nbreak\",2,", ',')
        assertNull(data.failure)
        assertEquals(
            listOf(listOf("name", "value", ""), listOf("a,b", "say \"hi\"", ""), listOf("line\r\nbreak", "2", "")),
            data.rows.map { it.toList() },
        )
        assertFalse(data.hasMore)
    }

    @Test
    fun `B-CHAT-022 TSV retains ragged rows and treats formulas and markup as literal data`() {
        val data = DelimitedText.parse("=SUM(A1)\t<script>\n# Heading\n\"a\tb\"\t", '\t')
        assertEquals(listOf(listOf("=SUM(A1)", "<script>"), listOf("# Heading"), listOf("a\tb", "")), data.rows.map { it.toList() })
        assertEquals(2, data.columns)
    }

    @Test
    fun `B-CHAT-023 blank input and trailing record terminator do not invent rows`() {
        assertTrue(rows("", ',').isEmpty())
        assertTrue(rows("﻿", ',').isEmpty())
        assertEquals(listOf(listOf("a")), rows("a\n", ','))
        assertEquals(listOf(listOf(""), listOf("")), rows("\n\n", ','))
    }

    @Test
    fun `B-CHAT-023 malformed quotes fall back without fabricated cells`() {
        for (text in listOf("\"open", "a\"b,c", "\"a\"suffix,b")) {
            val data = DelimitedText.parse(text, ',')
            assertEquals(DelimitedFailure.Malformed, data.failure, text)
            assertTrue(data.rows.isEmpty())
        }
    }

    @Test
    fun `B-CHAT-024 bounds distinguish a row prefix from an unavailable table`() {
        val prefix = DelimitedText.parse(List(201) { "a,b" }.joinToString("\n"), ',')
        assertEquals(200, prefix.rows.size)
        assertTrue(prefix.hasMore)
        assertFalse(DelimitedText.parse(List(200) { "a,b" }.joinToString("\n"), ',').hasMore)
        assertEquals(DelimitedFailure.TooLarge, DelimitedText.parse("x".repeat(256 * 1024 + 1), ',').failure)
        assertEquals(DelimitedFailure.TooLarge, DelimitedText.parse("é".repeat(128 * 1024 + 1), ',').failure)
        assertEquals(DelimitedFailure.FieldTooLong, DelimitedText.parse("x".repeat(4097), ',').failure)
        assertEquals(DelimitedFailure.TooWide, DelimitedText.parse(List(33) { "x" }.joinToString(","), ',').failure)
    }

    @Test
    fun `B-CHAT-024 an explicit incompatible MIME cannot become a table from its filename`() {
        assertEquals(',', DelimitedText.separator("report.CSV", "text/plain"))
        assertEquals('\t', DelimitedText.separator("report", "text/tab-separated-values"))
        assertNull(DelimitedText.separator("report.csv", "application/pdf"))
        assertNull(DelimitedText.separator("report.txt", "text/plain"))
        // The preview follows the same rule: a PDF named .csv is not decoded as a table.
        assertNull(PreviewData("report.csv", "application/pdf", bytes = "a,b".encodeToByteArray()).table())
        assertEquals(1, PreviewData("report.csv", null, bytes = "a,b".encodeToByteArray()).table()?.rows?.size)
    }
}
