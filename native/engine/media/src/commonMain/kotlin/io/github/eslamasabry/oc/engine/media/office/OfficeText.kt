package io.github.eslamasabry.oc.engine.media.office

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.media.xml.XmlEvent
import io.github.eslamasabry.oc.engine.media.xml.XmlLimits
import io.github.eslamasabry.oc.engine.media.xml.XmlPull
import io.github.eslamasabry.oc.engine.media.xml.XmlRefused

/**
 * What an Office file says, as text (plan 10 §10.7; port of `lib/domain/office_text.dart`). A workbook becomes CSV
 * sheet by sheet under `# Sheet: <name>`; a Word document becomes its paragraphs separated by blank lines.
 *
 * [sections] = sheets in a workbook (1 for a document); [lines] = rows across all sheets, or paragraphs.
 */
public data class OfficeText(val text: String, val sections: Int, val lines: Int, val truncated: Boolean) {
    public companion object {
        /** Enough for a large sheet, small enough to fit a prompt. */
        public const val MAX_CHARS: Int = 400_000

        /** `.xlsx`, `.xlsm` and `.docx` are read this way; the old binary `.xls`/`.doc` only share the name. */
        public fun isOfficeDocument(name: String): Boolean {
            val lower = name.lowercase()
            return lower.endsWith(".xlsx") || lower.endsWith(".xlsm") || lower.endsWith(".docx")
        }

        /** Pure and CPU-bound: callers run it on `dispatchers.default`. */
        public fun read(name: String, bytes: ByteArray): Outcome<OfficeText> {
            val files =
                when (val zip = ZipCentralDirectory.read(bytes)) {
                    is ZipCentralDirectory.Result.Entries -> zip.files
                    else -> return unreadable()
                }
            return try {
                val result = if (name.lowercase().endsWith(".docx")) document(files) else workbook(files)
                result ?: unreadable()
            } catch (_: XmlRefused) {
                unreadable()
            }
        }

        private fun unreadable(): Outcome<Nothing> = Outcome.Failed(Failure.Media(MediaProblem.OfficeUnreadable))
    }
}

private fun pull(bytes: ByteArray): XmlPull = XmlPull(bytes.decodeToString().removePrefix("﻿"), XmlLimits.Office)

private inline fun XmlPull.each(block: (XmlEvent) -> Unit) {
    while (true) block(next() ?: return)
}

// ---- XLSX -----------------------------------------------------------------------------------------------------------

private fun workbook(files: Map<String, ByteArray>): Outcome<OfficeText>? {
    val workbook = files["xl/workbook.xml"] ?: return null
    val shared = sharedStrings(files["xl/sharedStrings.xml"])
    val targets = HashMap<String, String>()
    files["xl/_rels/workbook.xml.rels"]?.let { rels ->
        pull(rels).each { e ->
            if (e is XmlEvent.Start && e.name.local == "Relationship") {
                val id = e.attr("Id")
                val target = e.attr("Target")
                if (id != null && target != null) targets[id] = if (target.startsWith("/")) target.substring(1) else "xl/$target"
            }
        }
    }
    val sheets = ArrayList<Pair<String, String?>>()
    pull(workbook).each { e ->
        if (e is XmlEvent.Start && e.name.local == "sheet") {
            val name = e.attr("name") ?: "Sheet ${sheets.size + 1}"
            val rel = e.attributes.firstOrNull { it.name.local == "id" && it.name.prefix != null }?.value
            sheets += name to rel
        }
    }
    if (sheets.isEmpty()) return Outcome.Failed(Failure.Media(MediaProblem.OfficeEmpty))
    val out = StringBuilder()
    var rows = 0
    var truncated = false
    for ((index, sheet) in sheets.withIndex()) {
        val bytes = files[targets[sheet.second]] ?: files["xl/worksheets/sheet${index + 1}.xml"] ?: continue
        if (out.isNotEmpty()) out.append('\n')
        out.append("# Sheet: ").append(sheet.first).append('\n')
        val cut = sheetRows(bytes, shared, out) { rows++ }
        if (cut) {
            truncated = true
            break
        }
    }
    return Outcome.Ok(finish(out, sheets.size, rows, truncated))
}

private fun sharedStrings(bytes: ByteArray?): List<String> {
    if (bytes == null) return emptyList()
    val shared = ArrayList<String>()
    var item: StringBuilder? = null
    var inT = false
    var phonetic = 0
    pull(bytes).each { e ->
        when (e) {
            is XmlEvent.Start ->
                when (e.name.local) {
                    "si" -> item = StringBuilder()
                    "rPh" -> if (!e.selfClosing) phonetic++
                    "t" -> inT = !e.selfClosing
                }
            is XmlEvent.End ->
                when (e.name.local) {
                    "si" -> {
                        shared += item?.toString().orEmpty()
                        item = null
                    }
                    "rPh" -> if (phonetic > 0) phonetic--
                    "t" -> inT = false
                }
            is XmlEvent.Text -> if (inT && phonetic == 0) item?.append(e.text)
            is XmlEvent.Instruction -> Unit
        }
    }
    return shared
}

/** Appends one sheet's rows as CSV; returns true when the output passed [OfficeText.MAX_CHARS]. */
private fun sheetRows(bytes: ByteArray, shared: List<String>, out: StringBuilder, onRow: () -> Unit): Boolean {
    var cells: HashMap<Int, String>? = null
    var next = 0
    var column = 0
    var type: String? = null
    var value: StringBuilder? = null
    var inlineText: StringBuilder? = null
    var capture = 0 // 1 = inside <v>, 2 = inside <is>…<t>
    val parser = pull(bytes)
    while (true) {
        when (val e = parser.next() ?: return false) {
            is XmlEvent.Start ->
                when (e.name.local) {
                    "row" -> {
                        cells = HashMap()
                        next = 0
                    }
                    "c" -> {
                        column = column(e.attr("r")) ?: next
                        next = column + 1
                        type = e.attr("t")
                        value = null
                        inlineText = null
                    }
                    "v" -> if (!e.selfClosing) {
                        value = StringBuilder()
                        capture = 1
                    }
                    "t" -> if (type == "inlineStr" && !e.selfClosing) {
                        if (inlineText == null) inlineText = StringBuilder()
                        capture = 2
                    }
                }
            is XmlEvent.Text ->
                when (capture) {
                    1 -> value?.append(e.text)
                    2 -> inlineText?.append(e.text)
                }
            is XmlEvent.End ->
                when (e.name.local) {
                    "v", "t" -> capture = 0
                    "c" -> {
                        val text = cellText(type, value?.toString(), inlineText?.toString(), shared)
                        if (text.isNotEmpty()) cells?.put(column, text)
                    }
                    "row" -> {
                        val row = cells
                        cells = null
                        if (!row.isNullOrEmpty()) {
                            val width = row.keys.max() + 1
                            for (i in 0 until width) {
                                if (i > 0) out.append(',')
                                out.append(csv(row[i].orEmpty()))
                            }
                            out.append('\n')
                            onRow()
                            if (out.length > OfficeText.MAX_CHARS) return true
                        }
                    }
                }
            is XmlEvent.Instruction -> Unit
        }
    }
}

private fun cellText(type: String?, value: String?, inline: String?, shared: List<String>): String {
    if (type == "inlineStr") return inline.orEmpty()
    val v = value.orEmpty()
    return when (type) {
        "s" -> shared.getOrNull(v.trim().toIntOrNull() ?: -1).orEmpty()
        "b" -> if (v == "1") "TRUE" else "FALSE"
        else -> v
    }
}

/** "BC12" → 54 (zero-based column). */
private fun column(reference: String?): Int? {
    if (reference == null) return null
    var column = 0
    var letters = 0
    for (ch in reference) {
        val upper = ch.uppercaseChar()
        if (upper !in 'A'..'Z') break
        column = column * 26 + (upper - 'A' + 1)
        letters++
        if (letters > 3) return null // XFD is the last column Excel writes
    }
    return if (letters == 0) null else column - 1
}

private fun csv(value: String): String =
    if (value.any { it == '"' || it == ',' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

// ---- DOCX -----------------------------------------------------------------------------------------------------------

private fun document(files: Map<String, ByteArray>): Outcome<OfficeText>? {
    val body = files["word/document.xml"] ?: return null
    val out = StringBuilder()
    var paragraphs = 0
    var truncated = false
    var line: StringBuilder? = null
    var paragraphDepth = 0
    var inText = false
    val parser = pull(body)
    loop@ while (true) {
        val e = parser.next() ?: break
        when (e) {
            is XmlEvent.Start ->
                when (e.name.qualified) {
                    "w:p" -> if (!e.selfClosing) {
                        if (paragraphDepth == 0) line = StringBuilder()
                        paragraphDepth++
                    }
                    "w:t" -> inText = !e.selfClosing
                    "w:tab" -> line?.append('\t')
                    "w:br" -> line?.append('\n')
                }
            is XmlEvent.Text -> if (inText) line?.append(e.text)
            is XmlEvent.End ->
                when (e.name.qualified) {
                    "w:t" -> inText = false
                    "w:p" -> if (paragraphDepth > 0) {
                        paragraphDepth--
                        if (paragraphDepth == 0) {
                            val text = line.toString().trimEnd()
                            line = null
                            if (text.isNotEmpty()) {
                                out.append(text).append("\n\n")
                                paragraphs++
                                if (out.length > OfficeText.MAX_CHARS) {
                                    truncated = true
                                    break@loop
                                }
                            }
                        }
                    }
                }
            is XmlEvent.Instruction -> Unit
        }
    }
    return Outcome.Ok(finish(out, 1, paragraphs, truncated))
}

private fun finish(out: StringBuilder, sections: Int, lines: Int, truncated: Boolean): OfficeText {
    var text = out.toString().trimEnd()
    var cut = truncated
    if (text.length > OfficeText.MAX_CHARS) {
        var end = OfficeText.MAX_CHARS
        if (text[end - 1].isHighSurrogate()) end-- // never split a surrogate pair
        text = text.substring(0, end)
        cut = true
    }
    return OfficeText(text = text, sections = sections, lines = lines, truncated = cut)
}
