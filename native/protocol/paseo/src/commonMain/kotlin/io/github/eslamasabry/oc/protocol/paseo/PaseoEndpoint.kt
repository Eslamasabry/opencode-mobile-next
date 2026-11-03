package io.github.eslamasabry.oc.protocol.paseo

import io.github.eslamasabry.oc.core.security.AddressRules

/**
 * A validated daemon endpoint: `ws`/`wss`, host on this device or the tailnet, path forced to `/ws` (plan 04 §8,
 * B-SEC-113). Port of Dart `paseoEndpoint`. [host] is lower-case without IPv6 brackets.
 */
public data class PaseoEndpoint(val scheme: String, val host: String, val port: Int?) {
    /** `ws://host:port/ws` — the socket URL. Never carries credentials, query or fragment. */
    val url: String get() = "$scheme://${if (':' in host) "[$host]" else host}${port?.let { ":$it" } ?: ""}/ws"

    /** The upgrade request's URL (`http`/`https`). */
    val httpUrl: String get() = (if (scheme == "wss") "https" else "http") + url.removePrefix(scheme)

    val isLoopback: Boolean get() = AddressRules.isLoopback(host)

    override fun toString(): String = url

    public companion object {
        /** Throws [PaseoException] ([PaseoFailureKind.InvalidEndpoint]) unless [raw] is a usable daemon address. */
        public fun parse(raw: String): PaseoEndpoint {
            val parts = PaseoUrl.parse(raw.trim()) ?: paseoFail(PaseoFailureKind.InvalidEndpoint)
            if (parts.scheme !in setOf("ws", "wss") || parts.host.isEmpty() || parts.userInfo != null ||
                parts.query != null || parts.fragment != null || parts.path !in setOf("", "/", "/ws")
            ) {
                paseoFail(PaseoFailureKind.InvalidEndpoint)
            }
            if (!isPaseoCleartextHost(parts.host)) paseoFail(PaseoFailureKind.InvalidEndpoint)
            return PaseoEndpoint(parts.scheme, parts.host, parts.port)
        }
    }
}

/** Connections are accepted only where the network itself is private: this device or a Tailscale address. */
public fun isPaseoCleartextHost(host: String): Boolean = AddressRules.isLoopback(host) || AddressRules.isTailnet(host)

/** Why a typed Paseo address is refused (Dart `validatePaseoServerUrl`; the words live in :ui:words). */
public enum class PaseoUrlProblem { Empty, Incomplete, WrongScheme, CredentialsInAddress, QueryOrFragment, HasPath, NotPrivate }

/** Profile validators (B-SEC-113): they agree with [PaseoEndpoint.parse]. */
public object PaseoProfileRules {
    /** Null when [value] is a usable daemon address. */
    public fun validateUrl(value: String): PaseoUrlProblem? {
        val raw = value.trim()
        if (raw.isEmpty()) return PaseoUrlProblem.Empty
        val uri = PaseoUrl.parse(raw)
        if (uri == null || uri.host.isEmpty()) return PaseoUrlProblem.Incomplete
        if (uri.scheme != "wss" && uri.scheme != "ws") return PaseoUrlProblem.WrongScheme
        if (uri.userInfo != null) return PaseoUrlProblem.CredentialsInAddress
        if (!uri.query.isNullOrEmpty() || !uri.fragment.isNullOrEmpty()) return PaseoUrlProblem.QueryOrFragment
        if (uri.path.isNotEmpty() && uri.path != "/" && uri.path != "/ws") return PaseoUrlProblem.HasPath
        if (!isPaseoCleartextHost(uri.host)) return PaseoUrlProblem.NotPrivate
        return null
    }

    /** A bare authority gains `ws://` where cleartext is allowed, else `wss://`. A full URL is kept. */
    public fun normalizeUrl(value: String): String {
        val raw = value.trim()
        if (raw.isEmpty() || "://" in raw) return raw
        val host = PaseoUrl.parse("ws://$raw")?.host.orEmpty()
        return if (isPaseoCleartextHost(host)) "ws://$raw" else "wss://$raw"
    }

    /**
     * An empty password is valid (a private daemon may run without one). It travels in a WebSocket subprotocol: at
     * most 4096 characters, no whitespace, commas or control characters.
     */
    public fun passwordAcceptable(value: String): Boolean =
        value.isEmpty() || (value.length <= 4096 && value.none { it.isWhitespace() || it == ',' || it.code <= 0x1f || it.code in 0x7f..0x9f })
}

/** A minimal absolute-URL splitter (no java.net in common code). Never decodes escapes. */
internal class PaseoUrl(
    val scheme: String,
    val userInfo: String?,
    val host: String,
    val port: Int?,
    val path: String,
    val query: String?,
    val fragment: String?,
) {
    companion object {
        private val SCHEME = Regex("""^([A-Za-z][A-Za-z0-9+.\-]*)://""")

        fun parse(raw: String): PaseoUrl? {
            val match = SCHEME.find(raw) ?: return null
            val scheme = match.groupValues[1].lowercase()
            var rest = raw.substring(match.range.last + 1)
            val fragment = if ('#' in rest) rest.substringAfter('#') else null
            rest = rest.substringBefore('#')
            val query = if ('?' in rest) rest.substringAfter('?') else null
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
                portText = when {
                    after.isEmpty() -> null
                    after.startsWith(":") -> after.substring(1)
                    else -> return null
                }
            } else {
                val colon = hostPort.lastIndexOf(':')
                host = if (colon >= 0) hostPort.substring(0, colon) else hostPort
                portText = if (colon >= 0) hostPort.substring(colon + 1) else null
                if (':' in host) return null
            }
            val port = when {
                portText.isNullOrEmpty() -> null
                portText.all(Char::isDigit) && portText.length <= 5 -> portText.toInt().takeIf { it in 0..65535 } ?: return null
                else -> return null
            }
            // B-SEC-113: only plain hostname/IP characters. Anything else (`\`, `%`, `;`, spaces…) can make a socket
            // library end the authority earlier than this splitter did and reach a different host with the password.
            val bracketed = hostPort.startsWith("[")
            if (host.isNotEmpty() && !(if (bracketed) IPV6_HOST.matches(host) else NAME_HOST.matches(host))) return null
            return PaseoUrl(scheme, userInfo?.takeIf { it.isNotEmpty() }, host.lowercase(), port, path, query, fragment)
        }

        /** Dot-separated labels of letters, digits, `-` and `_`; one trailing dot allowed. */
        private val NAME_HOST = Regex("""^[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+)*\.?$""")
        private val IPV6_HOST = Regex("""^[0-9A-Fa-f:.]+$""")
    }
}
