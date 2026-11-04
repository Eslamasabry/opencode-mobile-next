package io.github.eslamasabry.oc.engine.integrations.catalog

/**
 * A small absolute-URL splitter for the catalog rules (Dart `Uri.tryParse` as far as these rules read it). Common code
 * has no java.net; this never follows, fetches or normalises anything. [query] values are percent-decoded.
 */
public class CatalogUrl private constructor(
    public val scheme: String,
    public val userInfo: String,
    /** Lower-case, without IPv6 brackets; empty when the URL has no host. */
    public val host: String,
    public val hasAuthority: Boolean,
    public val path: String,
    public val rawQuery: String?,
) {
    public val query: Map<String, String> by lazy { parseQuery(rawQuery) }

    public companion object {
        private val SCHEME = Regex("""^([A-Za-z][A-Za-z0-9+.\-]*):""")

        /** Null when [raw] has no scheme. */
        public fun parse(raw: String): CatalogUrl? {
            val s = raw.trim()
            val match = SCHEME.find(s) ?: return null
            val scheme = match.groupValues[1].lowercase()
            var rest = s.substring(match.range.last + 1).substringBefore('#')
            val rawQuery = if ('?' in rest) rest.substringAfter('?') else null
            rest = rest.substringBefore('?')
            if (!rest.startsWith("//")) return CatalogUrl(scheme, "", "", false, rest, rawQuery)
            rest = rest.removePrefix("//")
            val slash = rest.indexOf('/')
            val authority = if (slash < 0) rest else rest.substring(0, slash)
            val path = if (slash < 0) "" else rest.substring(slash)
            val at = authority.lastIndexOf('@')
            val userInfo = if (at >= 0) authority.substring(0, at) else ""
            var host = if (at >= 0) authority.substring(at + 1) else authority
            host =
                if (host.startsWith("[")) {
                    host.substringAfter('[').substringBefore(']')
                } else {
                    host.substringBefore(':')
                }
            return CatalogUrl(scheme, userInfo, host.lowercase(), true, path, rawQuery)
        }

        /** `a=1&b=x%20y` → map; the first value of a repeated name wins (Dart `queryParameters` keeps the last). */
        public fun parseQuery(raw: String?): Map<String, String> {
            if (raw.isNullOrEmpty()) return emptyMap()
            val out = LinkedHashMap<String, String>()
            for (pair in raw.split('&')) {
                if (pair.isEmpty()) continue
                val name = decode(pair.substringBefore('='))
                val value = if ('=' in pair) decode(pair.substringAfter('=')) else ""
                out[name] = value
            }
            return out
        }

        /** Dart `Uri.encodeComponent`: everything but `A-Za-z0-9-_.!~*'()` is percent-encoded (UTF-8). */
        public fun encodeSegment(text: String): String =
            buildString {
                for (b in text.encodeToByteArray()) {
                    val c = (b.toInt() and 0xff)
                    val ch = c.toChar()
                    if (c < 0x80 && (ch.isLetterOrDigit() || ch in "-_.!~*'()")) {
                        append(ch)
                    } else {
                        append('%')
                        append(HEX[c shr 4])
                        append(HEX[c and 0xf])
                    }
                }
            }

        private const val HEX = "0123456789ABCDEF"

        /** Percent-decoding (UTF-8) plus `+` as space, like Dart's query decoding. Malformed escapes stay literal. */
        public fun decode(text: String): String {
            if ('%' !in text && '+' !in text) return text
            val bytes = ArrayList<Byte>(text.length)
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '%' && i + 2 < text.length) {
                    val hex = text.substring(i + 1, i + 3).toIntOrNull(16)
                    if (hex != null) {
                        bytes += hex.toByte()
                        i += 3
                        continue
                    }
                }
                if (c == '+') {
                    bytes += ' '.code.toByte()
                } else {
                    c.toString().encodeToByteArray().forEach { bytes += it }
                }
                i++
            }
            return bytes.toByteArray().decodeToString()
        }
    }
}

/** Control characters (C0 and DEL), the Flutter `[\x00-\x1f\x7f]` class. */
internal val CONTROL_CHARACTERS: Regex = Regex("[\\x00-\\x1f\\x7f]")
