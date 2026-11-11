package io.github.eslamasabry.oc.feature.servers.links

/**
 * The sending half of a conversation link that carries the server's address
 * (F: lib/domain/session_address_link.dart `SessionAddressLink.build` + `encode`). It holds the server's HTTPS
 * origin, the installation id and the conversation id, and never a password, token or any sign-in. The receiving
 * half is [SessionAddressLinks.validate]; whatever this builds, that must accept (the link is checked by it before it
 * is returned, so a rule drifting apart on one side cannot ship a code the other phone rejects).
 */
public object SessionAddressBuilder {
    /** Why no link was built. The first reason, in Flutter's order. */
    public sealed interface Built {
        public data class Link(val link: String) : Built

        public data class Refused(val failure: SessionAddressFailure) : Built
    }

    private val originShape = Regex("^https://([A-Za-z0-9.-]+)(?::([0-9]{1,5}))?$", RegexOption.IGNORE_CASE)

    /**
     * The host and effective port ("device.tailnet.ts.net:443") of a saved address, or null when it cannot go in a
     * link (only a private HTTPS `.ts.net` name can). F: `sessionAddressHostPort` after `normalizeOrigin`.
     */
    public fun hostPort(savedAddress: String, containsSecret: (String) -> Boolean): String? {
        val origin = normalizeOrigin(savedAddress, containsSecret) ?: return null
        val m = originShape.matchEntire(origin) ?: return null
        val port = m.groupValues[2].ifEmpty { "443" }
        return m.groupValues[1] + ":" + port
    }

    /**
     * Builds `opencode-mobile://session/v2?server=…&instance=…&session=…` from the three ids. [includeServerAddress]
     * is the person's switch: false never builds one (F: consentRequired). [containsSecret] is the app's redactor
     * check: a value that looks like a credential is refused.
     */
    public fun build(
        savedAddress: String,
        instanceId: String,
        sessionId: String,
        includeServerAddress: Boolean,
        containsSecret: (String) -> Boolean,
    ): Built {
        if (!includeServerAddress) return Built.Refused(SessionAddressFailure.Unavailable)
        if (savedAddress.contains('@')) return Built.Refused(SessionAddressFailure.Credentials)
        if (listOf(savedAddress, instanceId, sessionId).any { containsSecret(it) }) {
            return Built.Refused(SessionAddressFailure.Credentials)
        }
        val origin = normalizeOrigin(savedAddress, containsSecret) ?: return Built.Refused(SessionAddressFailure.InvalidLink)
        val link =
            "opencode-mobile://session/v2?server=" + encode(origin) +
                "&instance=" + encode(instanceId.lowercase()) +
                "&session=" + encode(sessionId)
        val failure = SessionAddressLinks.validate(link, containsSecret)
        return if (failure == null) Built.Link(link) else Built.Refused(failure)
    }

    /** Lower-case host, `:443` dropped; null unless it is a private HTTPS `.ts.net` name. */
    private fun normalizeOrigin(value: String, containsSecret: (String) -> Boolean): String? {
        if (value.isEmpty() || value.length > SessionAddressLinks.MAX_LENGTH || containsSecret(value)) return null
        val m = originShape.matchEntire(value) ?: return null
        val host = m.groupValues[1].lowercase()
        val port = m.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 443
        if (port !in 1..65535) return null
        return "https://" + host + (if (port == 443) "" else ":$port")
    }

    /** Dart's `Uri.encodeQueryComponent` for the printable ASCII these fields are limited to. */
    private fun encode(value: String): String {
        val out = StringBuilder()
        for (c in value) {
            if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_' || c == '.' || c == '~') {
                out.append(c)
            } else {
                val code = c.code
                out.append('%').append("0123456789ABCDEF"[code shr 4 and 0xF]).append("0123456789ABCDEF"[code and 0xF])
            }
        }
        return out.toString()
    }
}
