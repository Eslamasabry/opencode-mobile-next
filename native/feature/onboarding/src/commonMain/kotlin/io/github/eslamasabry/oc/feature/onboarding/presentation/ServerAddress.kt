package io.github.eslamasabry.oc.feature.onboarding.presentation

/**
 * Expands a pasted bare address into a full server address on the welcome screen (Dart `normalizeServerProfileUrl`,
 * `lib/state/profiles/validation.dart`; B-NAV-018): `host[:port]` and bare IPs gain `http://` on loopback and
 * `https://` everywhere else; anything with a scheme, or implausible, passes through untouched. Validation stays with
 * the address rules (`:core:security` AddressRules) when the profile is saved.
 */
public object ServerAddress {
    private val bare = Regex("""^\[?[A-Za-z0-9._\-:]+]?(:\d{1,5})?$""")
    private val port = Regex("""^:\d{1,5}$""")

    public fun normalize(value: String): String {
        val raw = value.trim()
        if (raw.isEmpty() || raw.contains("://")) return raw
        if (!bare.matches(raw)) return raw
        val (host, authority) = bareAuthority(raw) ?: return raw
        val scheme = if (isLoopback(host)) "http" else "https"
        return "$scheme://$authority"
    }

    /** Dart `isLoopbackHost`. */
    public fun isLoopback(host: String): Boolean = host.lowercase().let { it == "localhost" || it == "127.0.0.1" || it == "::1" }

    private fun bareAuthority(raw: String): Pair<String, String>? {
        if (raw.startsWith('[')) {
            val close = raw.indexOf(']')
            if (close < 2) return null
            val rest = raw.substring(close + 1)
            if (rest.isNotEmpty() && !port.matches(rest)) return null
            return raw.substring(1, close) to raw
        }
        val colons = raw.count { it == ':' }
        // An unbracketed IPv6 literal: its last group cannot be told from a port, so the brackets are ours.
        if (colons >= 2) return raw to "[$raw]"
        val host = if (colons == 1) raw.substringBefore(':') else raw
        if (host.isEmpty()) return null
        return host to raw
    }
}
