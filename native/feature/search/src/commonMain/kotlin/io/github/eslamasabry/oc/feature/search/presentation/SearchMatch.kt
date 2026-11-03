package io.github.eslamasabry.oc.feature.search.presentation

/** Pure matching rules for Search (no Compose, no I/O). */
public object SearchMatch {
    /** Characters kept on each side of a content match in the snippet. */
    public const val SNIPPET_RADIUS: Int = 48

    /** The most recent conversations whose text is searched when "Search inside" is on. */
    public const val CONTENT_CONVERSATIONS: Int = 20

    /** Messages read from each of those conversations (newest first). */
    public const val CONTENT_MESSAGES: Int = 50

    /** The query as matched: trimmed, inner whitespace collapsed. Blank = no search. */
    public fun normalize(query: String): String = query.trim().split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ")

    /** Case-insensitive title match; an untitled conversation never matches a non-blank query. */
    public fun titleMatches(title: String?, query: String): Boolean {
        val q = normalize(query)
        if (q.isEmpty()) return true
        return title != null && title.contains(q, ignoreCase = true)
    }

    /**
     * A one-line excerpt around the first case-insensitive match of [query] in [text], with "…" where it was cut, or
     * null when there is no match. Whitespace (including newlines) collapses to single spaces first.
     */
    public fun snippet(text: String, query: String, radius: Int = SNIPPET_RADIUS): String? {
        val q = normalize(query)
        if (q.isEmpty()) return null
        val flat = normalize(text)
        val at = flat.indexOf(q, ignoreCase = true)
        if (at < 0) return null
        val start = (at - radius).coerceAtLeast(0)
        val end = (at + q.length + radius).coerceAtMost(flat.length)
        val head = if (start > 0) "…" else ""
        val tail = if (end < flat.length) "…" else ""
        return head + flat.substring(start, end).trim() + tail
    }

    /** The newest message that mentions [query], with its excerpt. [messages] are oldest first. */
    public fun newestMatch(messages: List<SearchMessage>, query: String): Pair<SearchMessage, String>? {
        for (message in messages.asReversed()) {
            val excerpt = snippet(message.text, query) ?: continue
            return message to excerpt
        }
        return null
    }

    /** Recent searches after using [query]: it moves to the front, duplicates (ignoring case) go, at most [limit]. */
    public fun remember(recent: List<String>, query: String, limit: Int = RecentSearchStore.RECENT_LIMIT): List<String> {
        val q = normalize(query)
        if (q.isEmpty()) return recent.take(limit)
        return (listOf(q) + recent.filterNot { it.equals(q, ignoreCase = true) }).take(limit)
    }

    /** The last folder name of a project directory, for a row's quiet line. */
    public fun projectName(directory: String?): String? =
        directory?.trimEnd('/', '\\')?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf { it.isNotBlank() }

    private val WHITESPACE = Regex("\\s+")
}
