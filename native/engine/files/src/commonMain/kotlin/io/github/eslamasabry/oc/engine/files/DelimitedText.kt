package io.github.eslamasabry.oc.engine.files

/** A bounded local CSV/TSV projection. Every cell is inert text. */
public enum class DelimitedFailure {
    Malformed,
    TooLarge,
    TooWide,
    FieldTooLong,
}

public data class DelimitedTable(
    val rows: List<List<String>>,
    val hasMore: Boolean = false,
    val failure: DelimitedFailure? = null,
)

public object DelimitedText {
    public const val MAX_BYTES: Int = 256 * 1024
    public const val MAX_ROWS: Int = 200
    public const val MAX_COLUMNS: Int = 32
    public const val MAX_FIELD_LENGTH: Int = 4096

    public fun separator(name: String, mime: String?): Char? =
        when {
            mime == "text/csv" -> ','
            mime == "text/tab-separated-values" -> '\t'
            mime != null && mime != "text/plain" && mime != "application/octet-stream" -> null
            name.lowercase().endsWith(".csv") -> ','
            name.lowercase().endsWith(".tsv") -> '\t'
            else -> null
        }

    public fun parse(source: String, separator: Char): DelimitedTable {
        fun fail(reason: DelimitedFailure) = DelimitedTable(emptyList(), failure = reason)
        if (source.length > MAX_BYTES || source.encodeToByteArray().size > MAX_BYTES)
            return fail(DelimitedFailure.TooLarge)
        if (source.isEmpty() || source == "\uFEFF") return DelimitedTable(emptyList())
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        var field = StringBuilder()
        var quoted = false
        var closedQuote = false
        var endedRecord = false
        var i = if (source.startsWith('\uFEFF')) 1 else 0
        while (i < source.length) {
            val char = source[i]
            endedRecord = false
            when {
                quoted -> {
                    if (char == '"') {
                        if (i + 1 < source.length && source[i + 1] == '"') {
                            field.append('"')
                            i++
                        } else {
                            quoted = false
                            closedQuote = true
                        }
                    } else field.append(char)
                }
                char == separator || char == '\r' || char == '\n' -> {
                    row.add(field.toString())
                    if (row.size > MAX_COLUMNS) return fail(DelimitedFailure.TooWide)
                    field = StringBuilder()
                    closedQuote = false
                    if (char != separator) {
                        if (char == '\r' && i + 1 < source.length && source[i + 1] == '\n') i++
                        rows.add(row.toList())
                        row = mutableListOf()
                        endedRecord = true
                        if (rows.size == MAX_ROWS)
                            return DelimitedTable(rows.toList(), i + 1 < source.length)
                    }
                }
                char == '"' && field.isEmpty() && !closedQuote -> quoted = true
                else -> {
                    if (closedQuote || char == '"') return fail(DelimitedFailure.Malformed)
                    field.append(char)
                }
            }
            if (field.length > MAX_FIELD_LENGTH) return fail(DelimitedFailure.FieldTooLong)
            i++
        }
        if (quoted) return fail(DelimitedFailure.Malformed)
        if (!endedRecord) {
            row.add(field.toString())
            if (row.size > MAX_COLUMNS) return fail(DelimitedFailure.TooWide)
            rows.add(row.toList())
        }
        return DelimitedTable(rows.toList())
    }
}
