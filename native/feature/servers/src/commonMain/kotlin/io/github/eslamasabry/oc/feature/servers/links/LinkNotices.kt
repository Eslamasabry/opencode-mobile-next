package io.github.eslamasabry.oc.feature.servers.links

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Every address-link failure category (F: lib/domain/session_address_link.dart `SessionAddressFailureCode`). [word]
 * is the fixed category shown under Details, the only technical text the sheet shows (never the link, host,
 * installation or conversation id).
 */
public enum class SessionAddressFailure(public val word: String) {
    Unavailable("unavailable"),
    InvalidLink("invalidLink"),
    TooLarge("tooLarge"),
    Credentials("credentials"),
}

/**
 * A conversation link that carries the server's address (`opencode-mobile://session/v2?server=…&instance=…&session=…`,
 * F: lib/domain/session_address_link.dart). Pure, no I/O. Released Flutter builds its address coordinator closed
 * (session_address_controller.dart:435: "generation and reception stay unavailable until the host deployment is
 * verified"), so a well-formed link answers [SessionAddressFailure.Unavailable]; a malformed one says why. Nothing is
 * contacted, saved or opened on the link's word.
 */
public object SessionAddressLinks {
    public const val MAX_LENGTH: Int = 1024

    private val envelope = Regex("^opencode-mobile://session/v2\\?([^#]+)$", RegexOption.IGNORE_CASE)
    private val uuid = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private val label = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")
    private val origin = Regex("^https://([A-Za-z0-9.-]+)(?::([0-9]{1,5}))?$", RegexOption.IGNORE_CASE)
    private val sessionId = Regex("^[A-Za-z0-9_-]{1,128}$")
    private val badEscape = Regex("%(?![0-9a-fA-F]{2})")

    /** The link is an address link (the v2 session form), so it goes to the address sheet, not the session route. */
    public fun isAddressLink(raw: String): Boolean = raw.lowercase().startsWith("opencode-mobile://session/v2")

    /** Why this link cannot open here. [containsSecret] is the app's redactor check (a feature may not import it). */
    public fun check(raw: String, containsSecret: (String) -> Boolean): SessionAddressFailure =
        validate(raw, containsSecret) ?: SessionAddressFailure.Unavailable

    /** Null for a well-formed link; else the first failure, in Flutter's order (`SessionAddressLink.require`). */
    public fun validate(raw: String, containsSecret: (String) -> Boolean): SessionAddressFailure? {
        bounded(raw)?.let { return it }
        if (containsSecret(raw)) return SessionAddressFailure.Credentials
        if (badEscape.containsMatchIn(raw)) return SessionAddressFailure.InvalidLink
        val match = envelope.matchEntire(raw) ?: return SessionAddressFailure.InvalidLink
        if (!raw.substring(0, raw.indexOf('?')).endsWith("/v2")) return SessionAddressFailure.InvalidLink
        val fields = mutableMapOf<String, String>()
        for (pair in match.groupValues[1].split('&')) {
            val at = pair.indexOf('=')
            if (at <= 0 || pair.indexOf('=', at + 1) >= 0) return SessionAddressFailure.InvalidLink
            val key = pair.substring(0, at)
            if (key !in setOf("server", "instance", "session") || key in fields) return SessionAddressFailure.InvalidLink
            val value = decode(pair.substring(at + 1)) ?: return SessionAddressFailure.InvalidLink
            if (containsSecret(value)) return SessionAddressFailure.Credentials
            if (value.isEmpty() || value.any { it.code < 0x21 || it.code > 0x7e }) return SessionAddressFailure.InvalidLink
            fields[key] = value
        }
        if (fields.size != 3) return SessionAddressFailure.InvalidLink
        val server = fields.getValue("server")
        if ('@' in server) return SessionAddressFailure.Credentials
        if (!validOrigin(server)) return SessionAddressFailure.InvalidLink
        if (!uuid.matches(fields.getValue("instance"))) return SessionAddressFailure.InvalidLink
        if (!sessionId.matches(fields.getValue("session"))) return SessionAddressFailure.InvalidLink
        return null
    }

    private fun bounded(value: String): SessionAddressFailure? =
        when {
            value.length > MAX_LENGTH -> SessionAddressFailure.TooLarge
            value.isEmpty() || value.any { it.code < 0x21 || it.code > 0x7e } -> SessionAddressFailure.InvalidLink
            else -> null
        }

    /** Only a private Tailscale HTTPS name (`device.tailnet.ts.net`, F: normalizeOrigin) can be in a link. */
    private fun validOrigin(value: String): Boolean {
        val m = origin.matchEntire(value) ?: return false
        val host = m.groupValues[1].lowercase()
        val labels = host.split('.')
        if (host.length > 253 || labels.size != 4 || labels[2] != "ts" || labels[3] != "net") return false
        if (labels.any { !label.matches(it) }) return false
        val port = m.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 443
        return port in 1..65535
    }

    /** One level of query decoding (`+` is a space, which the printable check then refuses); null when malformed. */
    private fun decode(value: String): String? {
        val out = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '+' -> { out.append(' '); i++ }
                c == '%' -> {
                    if (i + 2 >= value.length) return null
                    val hex = value.substring(i + 1, i + 3).toIntOrNull(16) ?: return null
                    if (hex > 0x7f) return null
                    out.append(hex.toChar()); i += 3
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }
}

/**
 * What a link that cannot complete says (F: lib/main_parts/link_routes.dart). A link that cannot open is consumed
 * with one of these instead of silently landing on Servers.
 */
public sealed interface LinkNotice {
    /** The conversation is on a server this phone has not saved: Add server or Dismiss. */
    public data object ServerMissing : LinkNotice

    /** The saved server needs its password (or agent token) again; Servers is open behind it. */
    public data object Reentry : LinkNotice

    /** Connecting to the saved server failed; Servers is open behind it. */
    public data object ConnectionFailed : LinkNotice

    /** An address link: the sheet says why it cannot open here. */
    public data class AddressLink(val failure: SessionAddressFailure) : LinkNotice
}

/** Which notice a session link (`opencode-mobile://session?...`) leaves before any connection is tried. */
public object SessionLinkNotices {
    /** [saved]: the link's server is saved here; [needsSecret]: it needs its password or agent token again. */
    public fun before(saved: Boolean, needsSecret: Boolean): LinkNotice? =
        when {
            !saved -> LinkNotice.ServerMissing
            needsSecret -> LinkNotice.Reentry
            else -> null
        }
}

/** The one notice slot (newest wins). Main thread only. */
public class LinkNotices {
    private val mutable = MutableStateFlow<LinkNotice?>(null)
    public val current: StateFlow<LinkNotice?> = mutable.asStateFlow()

    public fun show(notice: LinkNotice) {
        mutable.value = notice
    }

    public fun dismiss() {
        mutable.value = null
    }
}
