package io.github.eslamasabry.oc.engine.entry.pairing

/**
 * One pairing-code address checked against the app's server-URL policy before anything is dialed (Flutter
 * `normalizeServerProfileUrl` + `validateServerProfileUrl` + `serverUrlNeedsCleartextConfirmation`). Pure text work:
 * no java.net, no DNS.
 */
public sealed interface PairingAddressCheck {
    /** Usable: [url] is the canonical origin `scheme://host[:port]`, host lower-cased. */
    public data class Usable(val url: String, val loopback: Boolean, val cleartext: Boolean) : PairingAddressCheck {
        /** The origin a cleartext confirmation is recorded against (`http://host:port`, port always written). */
        val cleartextOrigin: String? get() = if (cleartext) PairingAddress.cleartextOriginOf(url) else null
    }

    /** Ruled out before any request; [url] is the trimmed input (never shown as is: see [PairingAddress.display]). */
    public data class Unusable(val url: String, val reason: PairingReason) : PairingAddressCheck
}

public object PairingAddress {
    private const val MAX_LENGTH: Int = 2048
    private val loopbackHosts: Set<String> = setOf("localhost", "127.0.0.1", "::1")
    private val bare = Regex("""^\[?[A-Za-z0-9._\-:]+]?(:\d{1,5})?$""")
    private val hostName = Regex("""^[A-Za-z0-9.-]+$""")
    private val ipv6 = Regex("""^[0-9A-Fa-f:.]+$""")

    /** The hosts that are this device (Flutter `isLoopbackHost`). */
    public fun isLoopbackHost(host: String): Boolean = host.lowercase().removeSurrounding("[", "]") in loopbackHosts

    /**
     * A bare `host[:port]` gains a scheme: loopback becomes `http://`, everything else `https://`. A value with a
     * scheme, or one that is not a plausible single address, comes back trimmed for [check] to judge.
     */
    public fun normalize(value: String): String {
        val raw = value.trim()
        if (raw.isEmpty() || "://" in raw || !bare.matches(raw)) return raw
        val (host, authority) = bareAuthority(raw) ?: return raw
        return (if (isLoopbackHost(host)) "http://" else "https://") + authority
    }

    private fun bareAuthority(raw: String): Pair<String, String>? {
        if (raw.startsWith("[")) {
            val close = raw.indexOf(']')
            if (close < 2) return null
            val rest = raw.substring(close + 1)
            if (rest.isNotEmpty() && !Regex("""^:\d{1,5}$""").matches(rest)) return null
            return raw.substring(1, close) to raw
        }
        val colons = raw.count { it == ':' }
        if (colons >= 2) return raw to "[$raw]"
        val host = if (colons == 1) raw.substringBefore(':') else raw
        if (host.isEmpty()) return null
        return host to raw
    }

    /** Normalizes and judges one address. */
    public fun check(value: String): PairingAddressCheck {
        val raw = normalize(value)
        val bad = { reason: PairingReason -> PairingAddressCheck.Unusable(raw, reason) }
        if (raw.isEmpty() || raw.length > MAX_LENGTH) return bad(PairingReason.InvalidAddress)
        if (raw.any { it.code < 0x21 || it.code > 0x7e } || '\\' in raw || '%' in raw) return bad(PairingReason.InvalidAddress)
        val schemeEnd = raw.indexOf("://")
        if (schemeEnd <= 0) return bad(PairingReason.InvalidAddress)
        val scheme = raw.substring(0, schemeEnd).lowercase()
        if (scheme != "http" && scheme != "https") return bad(PairingReason.InvalidAddress)
        val rest = raw.substring(schemeEnd + 3)
        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, authorityEnd)
        val tail = rest.substring(authorityEnd)
        if ('@' in authority) return bad(PairingReason.CredentialsInUrl)
        if ('?' in tail || '#' in tail) return bad(PairingReason.QueryOrFragment)
        if (tail.isNotEmpty() && tail != "/") return bad(PairingReason.PathPresent)
        val (host, port) = splitAuthority(authority) ?: return bad(PairingReason.InvalidAddress)
        if (port != null && port !in 1..65535) return bad(PairingReason.InvalidAddress)
        val shownHost = if (':' in host) "[${host.lowercase()}]" else host.lowercase()
        val url = "$scheme://$shownHost" + (port?.let { ":$it" } ?: "")
        val loopback = isLoopbackHost(host)
        return PairingAddressCheck.Usable(url, loopback = loopback, cleartext = scheme == "http" && !loopback)
    }

    /** host (no brackets) and port, or null when the authority is malformed. */
    private fun splitAuthority(authority: String): Pair<String, Int?>? {
        if (authority.isEmpty()) return null
        val host: String
        val portText: String?
        if (authority.startsWith("[")) {
            val close = authority.indexOf(']')
            if (close < 2) return null
            host = authority.substring(1, close)
            if (!ipv6.matches(host) || ':' !in host) return null
            val after = authority.substring(close + 1)
            portText = when {
                after.isEmpty() -> null
                after.startsWith(":") -> after.substring(1)
                else -> return null
            }
        } else {
            if (authority.count { it == ':' } > 1) return null
            host = authority.substringBefore(':')
            portText = if (':' in authority) authority.substringAfter(':') else null
            if (!hostName.matches(host)) return null
        }
        if (host.isEmpty()) return null
        if (portText == null) return host to null
        if (portText.isEmpty() || portText.length > 5 || !portText.all { it.isDigit() }) return null
        return host to portText.toInt()
    }

    /** `http://host:port` with the default port written, for a usable [url]; null otherwise. */
    public fun cleartextOriginOf(url: String): String? {
        val usable = check(url) as? PairingAddressCheck.Usable ?: return null
        val scheme = usable.url.substringBefore("://")
        val authority = usable.url.substringAfter("://")
        val hasPort = authority.substringAfterLast(']').contains(':')
        return if (hasPort) usable.url else "$scheme://$authority:${if (scheme == "https") 443 else 80}"
    }

    /**
     * The only form of an address diagnostics may show: `scheme://host[:port]` with user-info, path, query and
     * fragment gone. Null when nothing safe can be shown (the UI then says "an invalid address").
     */
    public fun display(value: String): String? {
        val raw = value.trim()
        if (raw.isEmpty() || raw.length > MAX_LENGTH) return null
        val normalized = normalize(raw)
        val schemeEnd = normalized.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = normalized.substring(0, schemeEnd).lowercase()
        if (scheme != "http" && scheme != "https") return null
        val rest = normalized.substring(schemeEnd + 3)
        val authority = rest.substring(0, rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it })
        val hostPort = authority.substringAfterLast('@')
        val (host, port) = splitAuthority(hostPort) ?: return null
        val shownHost = if (':' in host) "[${host.lowercase()}]" else host.lowercase()
        return "$scheme://$shownHost" + (port?.let { ":$it" } ?: "")
    }
}
