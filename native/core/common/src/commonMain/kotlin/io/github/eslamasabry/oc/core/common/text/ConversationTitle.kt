package io.github.eslamasabry.oc.core.common.text

/**
 * The name a new conversation takes from its first message (audit 1, item 1). The first line, cut to [MAX] letters at
 * a word boundary and ended with "…" when it was cut, never in the middle of a word ("a bulle").
 */
public object ConversationTitle {
    public const val MAX: Int = 80

    /** Null when the message has no words. */
    public fun from(text: String, max: Int = MAX): String? {
        val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        if (line.length <= max) return line
        // Room for the "…"; a word that starts before the cut and ends after it is dropped whole.
        val room = line.substring(0, max - 1)
        val atWord = if (line[max - 1].isWhitespace()) room.length else room.lastIndexOfAny(charArrayOf(' ', '\t'))
        val kept = (if (atWord > max / 2) room.substring(0, atWord) else room).trimEnd(' ', '\t', ',', ';', ':', '.', '-')
        return "$kept…"
    }

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
    public fun readable(title: String?): String {
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

    /**
     * How a conversation's stored title reads on any surface (the home, the edge light's panel, "Reply in"): the one
     * rule. Nothing readable is [Shown.Untitled], the server's dated placeholder is [Shown.New], never an empty text.
     */
    public fun shown(raw: String?): Shown {
        val title = readable(raw)
        if (title.isEmpty()) return Shown.Untitled
        if (isPlaceholder(title)) return Shown.New
        val plain =
            title.replace(isoStamp, "")
                .replace(Regex("""\s*[-:–]\s*$"""), "")
                .replace(Regex("""\s{2,}"""), " ")
                .trim()
        return if (plain.isEmpty()) Shown.New else Shown.Text(plain)
    }

    /** [shown] as words: [untitled] and [new] are the surface's own strings for those two cases. */
    public fun display(raw: String?, untitled: String, new: String): String =
        when (val s = shown(raw)) {
            is Shown.Text -> s.text
            Shown.New -> new
            Shown.Untitled -> untitled
        }

    public sealed interface Shown {
        public data class Text(val text: String) : Shown

        public data object New : Shown

        public data object Untitled : Shown
    }
}
