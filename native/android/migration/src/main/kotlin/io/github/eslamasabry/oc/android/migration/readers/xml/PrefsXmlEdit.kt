package io.github.eslamasabry.oc.android.migration.readers.xml

/**
 * A surgical edit of an Android `shared_prefs` XML file (E00-S08): removes or replaces named entries IN PLACE and leaves
 * every other byte as it was, so the ciphertext of an entry that is kept (another server's password, the wrapped AES
 * key of `flutter_secure_storage`) is never re-encoded. Elements it writes follow `FastXmlSerializer`'s layout.
 *
 * The result is re-parsed before it is returned: it must load the way Android loads it and hold exactly the old entries
 * minus [remove] plus [put]; anything else is refused and nothing is written.
 */
internal object PrefsXmlEdit {
    sealed interface Result {
        /** Nothing named in the edit is in the file. */
        data object Unchanged : Result

        class Edited(val bytes: ByteArray) : Result

        data class Refused(val why: Why) : Result
    }

    enum class Why {
        /** Android would load an empty map from these bytes: an editor must not guess. */
        NotLoaded,

        /** Bytes that are not UTF-8 cannot be written back unchanged. */
        NotUtf8,

        /** A new entry has nowhere to go (`<map />`). */
        NoMapEnd,

        /** A value this editor does not write (`int-array`, …). */
        Unwritable,

        /** The edited text did not re-read to the expected entries. */
        Verify,
    }

    fun edit(bytes: ByteArray, remove: Set<String>, put: Map<String, RawPref>): Result {
        val read = AndroidPrefsXml.parse(bytes)
        if (read.load != AndroidLoad.Loaded) return Result.Refused(Why.NotLoaded)
        if (XmlProblem.MalformedUtf8 in read.problems) return Result.Refused(Why.NotUtf8)
        val text = String(bytes, Charsets.UTF_8)

        val ops = ArrayList<Pair<IntRange, String>>() // [start, end] inclusive → replacement
        for (name in remove - put.keys) {
            read.spans[name].orEmpty().forEach { ops += withIndent(text, it) to "" }
        }
        val inserts = StringBuilder()
        for ((name, value) in put) {
            if (read.entries[name] == value) continue
            val element = element(name, value) ?: return Result.Refused(Why.Unwritable)
            val spans = read.spans[name].orEmpty()
            if (spans.isEmpty()) {
                inserts.append(INDENT).append(element).append('\n')
            } else {
                spans.dropLast(1).forEach { ops += withIndent(text, it) to "" }
                ops += spans.last() to element
            }
        }
        if (inserts.isNotEmpty()) {
            val end = read.mapEndOffset ?: return Result.Refused(Why.NoMapEnd)
            ops += end until end to inserts.toString()
        }
        if (ops.isEmpty()) return Result.Unchanged

        val out = StringBuilder(text)
        for ((range, replacement) in ops.sortedByDescending { it.first.first }) {
            out.replace(range.first, range.last + 1, replacement)
        }
        val edited = out.toString().toByteArray(Charsets.UTF_8)

        val expected = LinkedHashMap(read.entries)
        remove.forEach(expected::remove)
        expected.putAll(put)
        val check = AndroidPrefsXml.parse(edited)
        if (check.load != AndroidLoad.Loaded || check.entries != expected) return Result.Refused(Why.Verify)
        return Result.Edited(edited)
    }

    /** [span] plus the indentation before it and the line break that ends the previous line. */
    private fun withIndent(text: String, span: IntRange): IntRange {
        var start = span.first
        while (start > 0 && (text[start - 1] == ' ' || text[start - 1] == '\t')) start--
        if (start > 0 && text[start - 1] == '\n') start--
        return start..span.last
    }

    /** One element as `XmlUtils.writeValueXml` + `FastXmlSerializer` write it; null for a value it never writes. */
    fun element(name: String, value: RawPref): String? {
        val n = escape(name)
        return when (value) {
            is RawPref.Str -> "<string name=\"$n\">${escape(value.value)}</string>"
            is RawPref.Bool -> "<boolean name=\"$n\" value=\"${value.value}\" />"
            is RawPref.Int32 -> "<int name=\"$n\" value=\"${value.value}\" />"
            is RawPref.Int64 -> "<long name=\"$n\" value=\"${value.value}\" />"
            is RawPref.F32 -> "<float name=\"$n\" value=\"${value.value}\" />"
            is RawPref.F64 -> "<double name=\"$n\" value=\"${value.value}\" />"
            is RawPref.StrSet -> buildString {
                append("<set name=\"").append(n).append("\">\n")
                for (s in value.value) append(INDENT).append(INDENT).append("<string>").append(escape(s)).append("</string>\n")
                append(INDENT).append("</set>")
            }
            RawPref.Null -> "<null name=\"$n\" />"
            is RawPref.Other -> null
        }
    }

    /** `FastXmlSerializer`'s escape table: `&#0;`…`&#31;`, `&quot;`, `&amp;`, `&lt;`, `&gt;`. */
    fun escape(text: String): String {
        val sb = StringBuilder(text.length + 16)
        for (c in text) {
            when {
                c.code < 32 -> sb.append("&#").append(c.code).append(';')
                c == '"' -> sb.append("&quot;")
                c == '&' -> sb.append("&amp;")
                c == '<' -> sb.append("&lt;")
                c == '>' -> sb.append("&gt;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private const val INDENT = "    "
}
