package io.github.eslamasabry.oc.core.security

/** Why a typed server address cannot be saved (Dart validateServerProfileUrl); the words live in :ui:words. */
public enum class ServerUrlProblem { Empty, MissingScheme, Incomplete, BadScheme, BadPort, CredentialsInUrl, QueryOrFragment, HasPath }

/**
 * Ports of lib/state/profiles/validation.dart (B-SEC-146): what the person typed → a usable URL text, and the strict
 * form policy. Remote plain HTTP is valid here; the separate cleartext confirmation ([AddressRules.needsCleartextConsent])
 * is what asks for explicit consent. `[::1]` is loopback everywhere.
 */
public object ServerUrlRules {
    private val BARE = Regex("""^\[?[A-Za-z0-9._\-:]+]?(:\d{1,5})?$""")
    private val PORT_SUFFIX = Regex("""^:\d{1,5}$""")
    private val CONTROL = Regex("""[\u0000-\u001f\u007f]""")

    /**
     * Dart normalizeServerProfileUrl: a bare `host[:port]` or IP (v4 or v6) gains a scheme — loopback http, the rest
     * https; an unbracketed IPv6 literal gains brackets. Values with a scheme, or that are not one address, come back
     * unchanged for [validate] to explain.
     */
    public fun normalizeText(value: String): String {
        val raw = value.trim()
        if (raw.isEmpty() || "://" in raw) return raw
        if (!BARE.matches(raw)) return raw
        val (host, authority) = bareAuthority(raw) ?: return raw
        val scheme = if (AddressRules.isLoopback(host)) "http" else "https"
        return "$scheme://$authority"
    }

    private fun bareAuthority(raw: String): Pair<String, String>? {
        if (raw.startsWith("[")) {
            val close = raw.indexOf(']')
            if (close < 2) return null
            val rest = raw.substring(close + 1)
            if (rest.isNotEmpty() && !PORT_SUFFIX.matches(rest)) return null
            return raw.substring(1, close) to raw
        }
        val colons = raw.count { it == ':' }
        if (colons >= 2) return raw to "[$raw]"
        val host = if (colons == 1) raw.substringBefore(':') else raw
        if (host.isEmpty()) return null
        return host to raw
    }

    /** Dart validateServerProfileUrl: null when the address can be saved. */
    public fun validate(value: String): ServerUrlProblem? {
        val raw = value.trim()
        if (raw.isEmpty()) return ServerUrlProblem.Empty
        if ("://" !in raw) return ServerUrlProblem.MissingScheme
        val scheme = raw.substringBefore("://")
        val authority = raw.substringAfter("://").split('/', '?', '#').first()
        if (authority.isEmpty() || scheme.isEmpty()) return ServerUrlProblem.Incomplete
        if (scheme.lowercase() != "https" && scheme.lowercase() != "http") return ServerUrlProblem.BadScheme
        if ('@' in authority) return ServerUrlProblem.CredentialsInUrl
        if (authority.endsWith(":")) return ServerUrlProblem.BadPort
        val parts = UrlParts.parse(raw)
        if (parts == null) {
            val portText = authority.substringAfterLast(']', authority).substringAfterLast(':', "")
            return if (portText.isNotEmpty()) ServerUrlProblem.BadPort else ServerUrlProblem.Incomplete
        }
        if (parts.host.isEmpty()) return ServerUrlProblem.Incomplete
        val port = parts.effectivePort()
        if (port == null || port < 1 || port > 65535) return ServerUrlProblem.BadPort
        if (parts.host.any { it.isWhitespace() } || '%' in parts.host || '\\' in raw || CONTROL.containsMatchIn(raw)) {
            return ServerUrlProblem.Incomplete
        }
        if (parts.query != null || parts.fragment != null) return ServerUrlProblem.QueryOrFragment
        if (parts.path.isNotEmpty() && parts.path != "/") return ServerUrlProblem.HasPath
        return null
    }

    /** Dart serverUrlNeedsCleartextConfirmation: plain HTTP to anything but this device. */
    public fun needsCleartextConfirmation(url: String): Boolean {
        val parts = UrlParts.parse(url.trim()) ?: return false
        return parts.scheme == "http" && !AddressRules.isLoopback(parts.host)
    }
}
