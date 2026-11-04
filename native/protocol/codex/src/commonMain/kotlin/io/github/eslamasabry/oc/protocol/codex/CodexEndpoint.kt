package io.github.eslamasabry.oc.protocol.codex

/**
 * A validated Codex app-server address (Dart `codexEndpoint`): `wss://host[:port]`, or `ws://` for this device only. A
 * Codex token never rides cleartext off the device. No user info, path, query or fragment.
 */
public class CodexEndpoint private constructor(
    public val scheme: String,
    /** Lower case; an IPv6 literal without brackets. */
    public val host: String,
    public val port: Int,
) {
    /** `wss://host:port` (IPv6 bracketed). */
    public val url: String get() = "$scheme://${if (':' in host) "[$host]" else host}:$port"

    override fun toString(): String = url

    override fun equals(other: Any?): Boolean = other is CodexEndpoint && other.url == url

    override fun hashCode(): Int = url.hashCode()

    public companion object {
        /** Null when [raw] is not an acceptable Codex address. */
        public fun parse(raw: String): CodexEndpoint? {
            val parts = CodexUrl.split(raw.trim()) ?: return null
            if (CodexProfileRules.problemOf(parts) != null) return null
            return CodexEndpoint(parts.scheme, parts.host, parts.port ?: if (parts.scheme == "wss") 443 else 80)
        }
    }
}

/** Why a typed Codex address was refused (Dart `validateCodexServerUrl`); each has one sentence in `:ui:words`. */
public enum class CodexUrlProblem { Empty, Incomplete, Scheme, Credentials, QueryOrFragment, Path, Cleartext }

/** Profile-editor rules for Codex addresses, folders and tokens (lib/state/profiles/validation.dart). */
public object CodexProfileRules {
    /** Adds `ws://` (loopback) or `wss://` to a bare authority; anything else is returned trimmed. */
    public fun normalizeUrl(value: String): String {
        val raw = value.trim()
        if (raw.isEmpty() || "://" in raw) return raw
        if (!BARE.matches(raw)) return raw
        val parts = CodexUrl.split("wss://$raw") ?: return raw
        val scheme = if (isLoopbackHost(parts.host)) "ws" else "wss"
        return "$scheme://$raw"
    }

    public fun validateUrl(value: String): CodexUrlProblem? {
        val raw = value.trim()
        if (raw.isEmpty()) return CodexUrlProblem.Empty
        val parts = CodexUrl.split(raw) ?: return CodexUrlProblem.Incomplete
        return problemOf(parts)
    }

    internal fun problemOf(parts: CodexUrl): CodexUrlProblem? =
        when {
            parts.scheme != "wss" && parts.scheme != "ws" -> CodexUrlProblem.Scheme
            parts.host.isEmpty() -> CodexUrlProblem.Incomplete
            parts.userInfo -> CodexUrlProblem.Credentials
            parts.query || parts.fragment -> CodexUrlProblem.QueryOrFragment
            parts.path.isNotEmpty() && parts.path != "/" -> CodexUrlProblem.Path
            parts.scheme == "ws" && !isLoopbackHost(parts.host) -> CodexUrlProblem.Cleartext
            else -> null
        }

    /** An absolute POSIX or Windows folder, ≤ 4096 characters, no control characters. */
    public fun directoryAcceptable(value: String): Boolean =
        value.isNotEmpty() && value.length <= 4096 && !value.any(::isControl) &&
            (value.startsWith("/") || WINDOWS_ROOT.containsMatchIn(value))

    /** Non-empty, ≤ 16384 characters, no whitespace or control characters. */
    public fun tokenAcceptable(value: String): Boolean =
        value.isNotEmpty() && value.length <= 16_384 && !value.any { isControl(it) || it.isWhitespace() }

    public fun isLoopbackHost(host: String): Boolean = host.lowercase().removePrefix("[").removeSuffix("]") in LOOPBACK

    private fun isControl(c: Char): Boolean = c.code <= 0x1f || c.code in 0x7f..0x9f

    private val LOOPBACK = setOf("localhost", "127.0.0.1", "::1")
    private val BARE = Regex("""^\[?[A-Za-z0-9._\-:]+]?(:\d{1,5})?$""")
    private val WINDOWS_ROOT = Regex("""^[A-Za-z]:[\\/]""")
}

/** A strict split of `scheme://[userinfo@]host[:port][path][?query][#fragment]`. */
internal class CodexUrl(
    val scheme: String,
    val host: String,
    val port: Int?,
    val userInfo: Boolean,
    val path: String,
    val query: Boolean,
    val fragment: Boolean,
) {
    companion object {
        fun split(raw: String): CodexUrl? {
            if (raw.any { it.isWhitespace() || it.code < 0x20 || it == '\\' || it == '%' }) return null
            val sep = raw.indexOf("://")
            if (sep <= 0) return null
            val scheme = raw.substring(0, sep).lowercase()
            if (!SCHEME.matches(scheme)) return null
            val rest = raw.substring(sep + 3)
            val end = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
            var authority = rest.substring(0, end)
            val tail = rest.substring(end)
            val at = authority.lastIndexOf('@')
            val userInfo = at >= 0
            if (userInfo) authority = authority.substring(at + 1)
            val host: String
            var portText: String? = null
            if (authority.startsWith("[")) {
                val close = authority.indexOf(']')
                if (close < 0) return null
                host = authority.substring(1, close)
                val after = authority.substring(close + 1)
                if (after.isNotEmpty()) {
                    if (!after.startsWith(":")) return null
                    portText = after.substring(1)
                }
                if (!IPV6.matches(host)) return null
            } else {
                val colon = authority.lastIndexOf(':')
                if (colon >= 0) {
                    host = authority.substring(0, colon)
                    portText = authority.substring(colon + 1)
                } else {
                    host = authority
                }
                if (host.isNotEmpty() && !HOST.matches(host)) return null
            }
            val port = portText?.let { text ->
                val n = text.toIntOrNull()
                if (!PORT.matches(text) || n == null || n < 1 || n > 65_535) return null
                n
            }
            val pathEnd = tail.indexOfFirst { it == '?' || it == '#' }.let { if (it < 0) tail.length else it }
            val path = tail.substring(0, pathEnd)
            val query = tail.indexOf('?').let { it in 0 until (tail.indexOf('#').takeIf { h -> h >= 0 } ?: tail.length) }
            val fragment = '#' in tail
            return CodexUrl(scheme, host.lowercase(), port, userInfo, path, query, fragment)
        }

        private val SCHEME = Regex("[a-z][a-z0-9+.-]*")
        private val HOST = Regex("[A-Za-z0-9._-]+")
        private val IPV6 = Regex("[0-9A-Fa-f:.]+")
        private val PORT = Regex("\\d{1,5}")
    }
}
