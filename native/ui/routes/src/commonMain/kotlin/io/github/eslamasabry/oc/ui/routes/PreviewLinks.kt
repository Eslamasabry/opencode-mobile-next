package io.github.eslamasabry.oc.ui.routes

/**
 * A link to a server running on "this machine": `localhost`, `127.0.0.1`, `0.0.0.0` or `[::1]`, with an explicit port
 * (Flutter `DevPreviewLink`, `lib/domain/dev_preview.dart`). [path] always starts with `/`.
 */
public data class PreviewLink(val port: Int, val https: Boolean = false, val path: String = "/", val query: String? = null) {
    public val scheme: String
        get() = if (https) "https" else "http"

    public val pathAndQuery: String
        get() = if (query == null) path else "$path?$query"

    /** The identity the conversation's port list de-duplicates on. */
    public val key: String
        get() = "$scheme:$port"
}

/** Finding local links in text (B-CHAT-025, B-CHAT-026). Pure; the chat and the preview cannot disagree. */
public object PreviewLinks {
    private const val MAX_LENGTH = 2048
    private val local = setOf("localhost", "127.0.0.1", "0.0.0.0", "[::1]", "::1")
    private val shape = Regex("""^([A-Za-z][A-Za-z0-9+.-]*)://([^/?#]*)(.*)$""")
    private val inText =
        Regex("""https?://(?:localhost|127\.0\.0\.1|0\.0\.0\.0|\[::1]):\d{1,5}[^\s<>()\[\]`'"]*""", RegexOption.IGNORE_CASE)
    private val trailing = Regex("""[.,;:!?'"*_~]+$""")

    /**
     * [value] as a local link, or null when it is anything else: another host, another scheme, a missing or impossible
     * port, or embedded credentials. Null means "not ours": the caller sends it on through the external link policy.
     */
    public fun parse(value: String?): PreviewLink? {
        val text = value?.trim() ?: return null
        if (text.isEmpty() || text.length > MAX_LENGTH) return null
        val match = shape.matchEntire(text) ?: return null
        val scheme = match.groupValues[1].lowercase()
        if (scheme != "http" && scheme != "https") return null
        val authority = match.groupValues[2]
        if ('@' in authority) return null
        val colon = if (authority.startsWith("[")) authority.indexOf("]:").let { if (it < 0) -1 else it + 1 } else authority.lastIndexOf(':')
        if (colon <= 0) return null
        val host = authority.substring(0, colon).lowercase()
        if (host !in local) return null
        val portText = authority.substring(colon + 1)
        if (portText.isEmpty() || portText.length > 5 || !portText.all { it in '0'..'9' }) return null
        val port = portText.toInt()
        if (port !in 1..65535) return null
        val rest = match.groupValues[3].substringBefore('#')
        val path = rest.substringBefore('?').ifEmpty { "/" }
        if (!path.startsWith("/")) return null
        val query = if ('?' in rest) rest.substringAfter('?').ifEmpty { null } else null
        return PreviewLink(port, scheme == "https", path, query)
    }

    /** Every local link written in [text], in order, trailing punctuation left out. */
    public fun scan(text: String): List<PreviewLink> =
        inText.findAll(text).mapNotNull { parse(it.value.replace(trailing, "")) }.toList()

    /** The links from [texts] (oldest first): newest first, one per scheme and port, at most [limit]. */
    public fun recent(texts: Iterable<String>, limit: Int = 5): List<PreviewLink> {
        val seen = HashSet<String>()
        val out = ArrayList<PreviewLink>()
        for (link in texts.flatMap(::scan).asReversed()) {
            if (seen.add(link.key)) out += link
            if (out.size >= limit) break
        }
        return out
    }
}
