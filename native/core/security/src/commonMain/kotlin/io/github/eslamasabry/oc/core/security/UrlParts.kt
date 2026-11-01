package io.github.eslamasabry.oc.core.security

/**
 * A small absolute-URL splitter in common code (no java.net, so the iOS klib compiles). It never decodes or
 * normalises percent-escapes; it only finds the parts the trust rules need.
 */
internal class UrlParts(
    val scheme: String,
    val userInfo: String?,
    /** Lower-case, without IPv6 brackets. */
    val host: String,
    val port: Int?,
    val path: String,
    val query: String?,
    val fragment: String?,
) {
    fun effectivePort(): Int? = port ?: DEFAULT_PORTS[scheme]

    /** scheme://host[:port]path, no user info, query or fragment (Dart diagnostics `Uri(...)` rebuild). */
    fun withoutSecrets(): String = "$scheme://${bracketed(host)}${port?.let { ":$it" } ?: ""}$path"

    companion object {
        private val DEFAULT_PORTS = mapOf("http" to 80, "https" to 443, "ws" to 80, "wss" to 443)
        private val SCHEME = Regex("""^([A-Za-z][A-Za-z0-9+.\-]*)://""")

        fun bracketed(host: String): String = if (':' in host) "[$host]" else host

        /** Null when [raw] is not `scheme://authority…` or the authority is malformed. */
        fun parse(raw: String): UrlParts? {
            val s = raw.trim()
            val schemeMatch = SCHEME.find(s) ?: return null
            val scheme = schemeMatch.groupValues[1].lowercase()
            var rest = s.substring(schemeMatch.range.last + 1)
            val fragment = rest.substringAfter('#', missingDelimiterValue = "").takeIf { '#' in rest }
            rest = rest.substringBefore('#')
            val query = rest.substringAfter('?', missingDelimiterValue = "").takeIf { '?' in rest }
            rest = rest.substringBefore('?')
            val slash = rest.indexOf('/')
            val authority = if (slash < 0) rest else rest.substring(0, slash)
            val path = if (slash < 0) "" else rest.substring(slash)
            val at = authority.lastIndexOf('@')
            val userInfo = if (at >= 0) authority.substring(0, at) else null
            val hostPort = if (at >= 0) authority.substring(at + 1) else authority
            val host: String
            val portText: String?
            if (hostPort.startsWith("[")) {
                val close = hostPort.indexOf(']')
                if (close < 0) return null
                host = hostPort.substring(1, close)
                val after = hostPort.substring(close + 1)
                portText = if (after.isEmpty()) null else if (after.startsWith(":")) after.substring(1) else return null
            } else {
                val colon = hostPort.lastIndexOf(':')
                host = if (colon >= 0) hostPort.substring(0, colon) else hostPort
                portText = if (colon >= 0) hostPort.substring(colon + 1) else null
                if (':' in host) return null
            }
            val port =
                when {
                    portText == null -> null
                    portText.isEmpty() -> null
                    portText.all(Char::isDigit) && portText.length <= 5 -> portText.toInt().takeIf { it in 0..65535 } ?: return null
                    else -> return null
                }
            return UrlParts(scheme, userInfo, host.lowercase(), port, path, query, fragment)
        }
    }
}
