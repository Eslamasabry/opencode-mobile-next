package io.github.eslamasabry.oc.engine.media.preview

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * A bounded table projection of CSV or TSV text (port of `lib/domain/delimited_text.dart`). Cells are data, never
 * Markdown or formulas. A failure means "no table": the viewer shows Source instead.
 */
public data class DelimitedText(
    val rows: ImmutableList<ImmutableList<String>>,
    /** True when the file has more rows than [MAX_ROWS] and only a prefix is shown. */
    val hasMore: Boolean,
    val failure: DelimitedFailure?,
) {
    val columns: Int get() = rows.maxOfOrNull { it.size } ?: 0

    public companion object {
        public const val MAX_BYTES: Int = 256 * 1024
        public const val MAX_ROWS: Int = 200
        public const val MAX_COLUMNS: Int = 32
        public const val MAX_FIELD_LENGTH: Int = 4096

        /** The separator for [name] and its declared [mime], or null. An explicit incompatible MIME wins over the name. */
        public fun separator(name: String, mime: String?): Char? {
            if (mime == "text/csv") return ','
            if (mime == "text/tab-separated-values") return '\t'
            if (mime != null && mime != "text/plain" && mime != "application/octet-stream") return null
            val lower = name.lowercase()
            return when {
                lower.endsWith(".csv") -> ','
                lower.endsWith(".tsv") -> '\t'
                else -> null
            }
        }

        public fun parse(source: String, separator: Char): DelimitedText {
            fun fail(reason: DelimitedFailure) = DelimitedText(persistentListOf(), false, reason)
            if (source.length > MAX_BYTES || StrictUtf8.encodedLength(source) > MAX_BYTES) {
                return fail(DelimitedFailure.TooLarge)
            }
            if (source.isEmpty() || source == "﻿") return DelimitedText(persistentListOf(), false, null)
            val rows = ArrayList<ImmutableList<String>>()
            var row = ArrayList<String>()
            val field = StringBuilder()
            var quoted = false
            var closedQuote = false
            var endedRecord = false
            var i = if (source.startsWith('﻿')) 1 else 0
            while (i < source.length) {
                val c = source[i]
                endedRecord = false
                if (quoted) {
                    if (c == '"') {
                        if (i + 1 < source.length && source[i + 1] == '"') {
                            field.append('"')
                            i++
                        } else {
                            quoted = false
                            closedQuote = true
                        }
                    } else {
                        field.append(c)
                    }
                } else if (c == separator || c == '\r' || c == '\n') {
                    row += field.toString()
                    if (row.size > MAX_COLUMNS) return fail(DelimitedFailure.TooWide)
                    field.clear()
                    closedQuote = false
                    if (c != separator) {
                        if (c == '\r' && i + 1 < source.length && source[i + 1] == '\n') i++
                        rows += row.toImmutableList()
                        row = ArrayList()
                        endedRecord = true
                        if (rows.size == MAX_ROWS) return DelimitedText(rows.toImmutableList(), i + 1 < source.length, null)
                    }
                } else if (c == '"' && field.isEmpty() && !closedQuote) {
                    quoted = true
                } else {
                    if (closedQuote || c == '"') return fail(DelimitedFailure.Malformed)
                    field.append(c)
                }
                if (field.length > MAX_FIELD_LENGTH) return fail(DelimitedFailure.FieldTooLong)
                i++
            }
            if (quoted) return fail(DelimitedFailure.Malformed)
            if (!endedRecord) {
                row += field.toString()
                if (row.size > MAX_COLUMNS) return fail(DelimitedFailure.TooWide)
                rows += row.toImmutableList()
            }
            return DelimitedText(rows.toImmutableList(), false, null)
        }
    }
}

public enum class DelimitedFailure { Malformed, TooLarge, TooWide, FieldTooLong }
