package io.github.eslamasabry.oc.feature.chats.presentation

/** A row title as the screen draws it; the screen picks the words for [New] and [Untitled]. */
public sealed interface RowTitle {
    public data class Text(val text: String) : RowTitle

    /** The server's dated placeholder: "New conversation". */
    public data object New : RowTitle

    /** Nothing readable: the surface's own untitled wording. */
    public data object Untitled : RowTitle
}

/**
 * Display rules for session titles (Dart `displaySessionTitleText`, `presentedSessionTitleText`,
 * `isPlaceholderSessionTitle`, `stripIsoStamp`). The stored title is never changed; every list goes through [present].
 */
public object SessionTitles {
    private val markers = listOf("<tool_call", "</tool_call", "<tool_use", "<function", "</function", "<|")
    private val truncatedMarker = Regex("""(<[A-Za-z_|/]*)\s*(?:…|\.\.\.)\s*$""")
    private val trailingEllipsis = Regex("""[\s…]*(?:\.\.\.)?[\s…]*$""")
    private val placeholder =
        Regex(
            """^(?:(?:New|Child)\s+session|Fork(?:ed)?(?:\s+session)?)?\s*[-:–]?\s*""" +
                """\d{4}-\d{2}-\d{2}T[\d:.]+(?:Z|[+-]\d{2}:?\d{2})?$""",
            RegexOption.IGNORE_CASE,
        )
    private val isoStamp = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?(?:Z|[+-]\d{2}:?\d{2})?""")

    /** The title with leaked model markup cut off, trimmed; empty when nothing readable is left. */
    public fun text(title: String?): String {
        var value = title?.trim().orEmpty()
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

    public fun isPlaceholder(title: String): Boolean = placeholder.matches(title.trim())

    public fun present(raw: String?): RowTitle {
        val title = text(raw)
        if (title.isEmpty()) return RowTitle.Untitled
        if (isPlaceholder(title)) return RowTitle.New
        val plain =
            title.replace(isoStamp, "")
                .replace(Regex("""\s*[-:–]\s*$"""), "")
                .replace(Regex("""\s{2,}"""), " ")
                .trim()
        return if (plain.isEmpty()) RowTitle.New else RowTitle.Text(plain)
    }
}
