package io.github.eslamasabry.oc.engine.connectors.text

/**
 * The small part of Dart's `Uri.tryParse` the connector code relies on: scheme (lower case), user info, host (lower
 * case, IPv6 without brackets), port, path, query and fragment, and decoded query parameters (last one wins, `+` is a
 * space). Null when the text cannot be an address (bad authority, bad port, bad percent escape in the query).
 */
public class WebAddress private constructor(
    public val scheme: String,
    public val userInfo: String,
    public val host: String,
    public val port: Int?,
    public val path: String,
    public val query: String?,
    public val fragment: String?,
    public val hasAuthority: Boolean,
) {
    public val hasQuery: Boolean get() = query != null
    public val hasFragment: Boolean get() = fragment != null

    public val queryParameters: Map<String, String> by lazy {
        val out = LinkedHashMap<String, String>()
        val q = query ?: return@lazy out
        if (q.isEmpty()) return@lazy out
        for (pair in q.split('&')) {
            if (pair.isEmpty()) continue
            val eq = pair.indexOf('=')
            val key = decode(if (eq < 0) pair else pair.substring(0, eq), plusIsSpace = true) ?: continue
            val value = if (eq < 0) "" else decode(pair.substring(eq + 1), plusIsSpace = true) ?: continue
            out[key] = value
        }
        out
    }

    public companion object {
        private val SCHEME = Regex("""^([A-Za-z][A-Za-z0-9+.\-]*):""")

        public fun parse(raw: String): WebAddress? {
            var rest = raw
            var fragment: String? = null
            val hash = rest.indexOf('#')
            if (hash >= 0) {
                fragment = rest.substring(hash + 1)
                rest = rest.substring(0, hash)
            }
            var query: String? = null
            val mark = rest.indexOf('?')
            if (mark >= 0) {
                query = rest.substring(mark + 1)
                rest = rest.substring(0, mark)
            }
            if (query != null && !validEscapes(query)) return null
            val schemeMatch = SCHEME.find(rest)
            val scheme = schemeMatch?.groupValues?.get(1)?.lowercase() ?: ""
            if (schemeMatch != null) rest = rest.substring(schemeMatch.range.last + 1)
            if (!rest.startsWith("//")) {
                return WebAddress(scheme, "", "", null, rest, query, fragment, hasAuthority = false)
            }
            rest = rest.substring(2)
            val slash = rest.indexOf('/')
            val authority = if (slash < 0) rest else rest.substring(0, slash)
            val path = if (slash < 0) "" else rest.substring(slash)
            val at = authority.lastIndexOf('@')
            val userInfo = if (at < 0) "" else authority.substring(0, at)
            val hostPort = if (at < 0) authority else authority.substring(at + 1)
            val host: String
            var portText: String? = null
            if (hostPort.startsWith("[")) {
                val close = hostPort.indexOf(']')
                if (close < 0) return null
                host = hostPort.substring(1, close)
                val tail = hostPort.substring(close + 1)
                if (tail.isNotEmpty()) {
                    if (!tail.startsWith(":")) return null
                    portText = tail.substring(1)
                }
            } else {
                val colon = hostPort.lastIndexOf(':')
                if (colon >= 0) {
                    host = hostPort.substring(0, colon)
                    portText = hostPort.substring(colon + 1)
                } else {
                    host = hostPort
                }
            }
            if (host.any { it.isWhitespace() || it in "<>\"{}|\\^`" }) return null
            val port =
                when {
                    portText == null || portText.isEmpty() -> null
                    portText.all { it in '0'..'9' } && portText.length <= 5 -> portText.toInt()
                    else -> return null
                }
            return WebAddress(scheme, userInfo, host.lowercase(), port, path, query, fragment, hasAuthority = true)
        }

        /** Percent-decodes [s] as UTF-8; null on a malformed escape or malformed UTF-8 (Dart throws there). */
        public fun decode(s: String, plusIsSpace: Boolean = false): String? {
            if ('%' !in s && !(plusIsSpace && '+' in s)) return s
            val bytes = ArrayList<Byte>(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                when {
                    c == '%' -> {
                        if (i + 2 >= s.length) return null
                        val hi = s[i + 1].digitToIntOrNull(16) ?: return null
                        val lo = s[i + 2].digitToIntOrNull(16) ?: return null
                        bytes.add((hi * 16 + lo).toByte())
                        i += 3
                    }
                    plusIsSpace && c == '+' -> {
                        bytes.add(' '.code.toByte())
                        i++
                    }
                    else -> {
                        val end = if (c.isHighSurrogate() && i + 1 < s.length) i + 2 else i + 1
                        s.substring(i, end).encodeToByteArray().forEach(bytes::add)
                        i = end
                    }
                }
            }
            return try {
                bytes.toByteArray().decodeToString(throwOnInvalidSequence = true)
            } catch (_: CharacterCodingException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        private fun validEscapes(s: String): Boolean {
            var i = 0
            while (i < s.length) {
                if (s[i] == '%') {
                    if (i + 2 >= s.length) return false
                    if (s[i + 1].digitToIntOrNull(16) == null || s[i + 2].digitToIntOrNull(16) == null) return false
                    i += 3
                } else {
                    i++
                }
            }
            return true
        }
    }
}
