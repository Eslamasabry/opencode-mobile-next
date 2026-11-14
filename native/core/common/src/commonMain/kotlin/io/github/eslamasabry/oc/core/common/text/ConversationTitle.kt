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
}
