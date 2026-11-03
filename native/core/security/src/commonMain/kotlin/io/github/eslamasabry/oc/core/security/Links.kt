package io.github.eslamasabry.oc.core.security

import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Outcome

/** AGENTS.md invariant as a type (plan 03 S7): only ExternalLinkPolicy creates an ApprovedLink. */
public enum class LinkOrigin { AppAuthored, ServerContent, Markdown, AgentCard, Terminal, Notification }

public class ApprovedLink internal constructor(public val uri: String, public val origin: LinkOrigin) {
    /** Plain http: allowed only so the caller can warn before it opens (B-SEC-064). */
    public val insecure: Boolean get() = uri.startsWith("http:", ignoreCase = true)
}

public sealed interface LinkDecision {
    public data class Open(val link: ApprovedLink) : LinkDecision
    public data class AskFirst(val link: ApprovedLink, val shownHost: String) : LinkDecision
    public data class Refuse(val reason: LinkRefusal) : LinkDecision
}

public enum class LinkRefusal { BadScheme, CredentialsInUrl, TooLong, NotAUrl, IntentScheme }

@Inject
public class ExternalLinkPolicy {
    public fun check(raw: String, origin: LinkOrigin): LinkDecision {
        val text = raw.trim()
        if (text.length > MAX_LENGTH) return LinkDecision.Refuse(LinkRefusal.TooLong)
        val match = URL.matchEntire(text) ?: return LinkDecision.Refuse(LinkRefusal.NotAUrl)
        val scheme = match.groupValues[1].lowercase()
        if (scheme == "intent") return LinkDecision.Refuse(LinkRefusal.IntentScheme)
        // B-SEC-064: only https, and http so the caller can warn; every other scheme (mailto, tel, file…) is refused.
        if (scheme !in setOf("http", "https")) return LinkDecision.Refuse(LinkRefusal.BadScheme)
        if (match.groupValues[2] != "//") return LinkDecision.Refuse(LinkRefusal.NotAUrl)
        val authority = match.groupValues[3]
        if ('@' in authority) return LinkDecision.Refuse(LinkRefusal.CredentialsInUrl)
        val host = hostOf(authority) ?: return LinkDecision.Refuse(LinkRefusal.NotAUrl)
        // Normalise the scheme so `HTTPS://` cannot present itself as something else.
        val link = ApprovedLink(scheme + text.substring(scheme.length), origin)
        return if (origin == LinkOrigin.AppAuthored) LinkDecision.Open(link)
        else LinkDecision.AskFirst(link, host)
    }

    /** The host of `host[:port]` or `[v6][:port]`; null when it is empty or malformed (hostless links never open). */
    private fun hostOf(authority: String): String? {
        val host: String
        val rest: String
        if (authority.startsWith("[")) {
            val end = authority.indexOf(']')
            if (end < 2) return null
            host = authority.substring(0, end + 1)
            rest = authority.substring(end + 1)
            if (rest.isNotEmpty() && !rest.startsWith(":")) return null
        } else {
            host = authority.substringBefore(':')
            rest = authority.substring(host.length)
            if ('[' in host || ']' in host) return null
        }
        if (host.isEmpty()) return null
        val port = rest.removePrefix(":")
        if (rest.isNotEmpty() && (port.isEmpty() || port.length > 5 || !port.all { it in '0'..'9' })) return null
        return host
    }

    private companion object {
        const val MAX_LENGTH = 2048
        val URL = Regex("""^([a-zA-Z][a-zA-Z0-9+.-]*):(//)?([^/?#\s]*)([^\s]*)$""")
    }
}

/** Implemented once on each platform (Android: :android:platform). */
public fun interface ExternalLinkOpener {
    public fun open(link: ApprovedLink): Outcome<Unit>
}
