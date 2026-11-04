package io.github.eslamasabry.oc.feature.chat.relations.presentation

/** Display-only cleanup of generated date stamps and leaked markup, matching Flutter session_title.dart. */
internal object RelationTitles {
    private val markers = listOf("<tool_call", "</tool_call", "<tool_use", "<function", "</function", "<|")
    private val truncatedMarker = Regex("""(<[A-Za-z_|/]*)\s*(?:…|\.\.\.)\s*$""")
    private val placeholder =
        Regex(
            """^(?:(?:New|Child)\s+session|Fork(?:ed)?(?:\s+session)?)?\s*[-:–]?\s*""" +
                """\d{4}-\d{2}-\d{2}T[\d:.]+(?:Z|[+-]\d{2}:?\d{2})?$""",
            RegexOption.IGNORE_CASE,
        )
    private val trailingEllipsis = Regex("""[\s…]*(?:\.\.\.)?[\s…]*$""")
    private val isoStamp = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?(?:Z|[+-]\d{2}:?\d{2})?""")

    /** Leaked markup cut off, a half-cut marker and its ellipsis too (Dart `displaySessionTitleText`). */
    private fun text(raw: String?): String {
        var value = raw?.trim().orEmpty()
        if (value.isEmpty()) return value
        val lower = value.lowercase()
        val at = markers.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull()
        var cut = false
        if (at != null) {
            value = value.substring(0, at)
            cut = true
        } else {
            val tail = truncatedMarker.find(value)
            if (tail != null) {
                val fragment = tail.groupValues[1].lowercase()
                if (markers.any { it.startsWith(fragment) }) {
                    value = value.substring(0, tail.range.first)
                    cut = true
                }
            }
        }
        if (cut) value = trailingEllipsis.replaceFirst(value, "")
        return value.trim()
    }

    fun present(raw: String?): String? {
        val value = text(raw)
        if (value.isEmpty()) return null
        if (placeholder.matches(value)) return null
        // Embedded stamps go too (Dart `stripIsoStamp`).
        val plain =
            value.replace(isoStamp, "")
                .replace(Regex("""\s*[-:–]\s*$"""), "")
                .replace(Regex("""\s{2,}"""), " ")
                .trim()
        return plain.takeIf { it.isNotEmpty() }
    }
}
