package io.github.eslamasabry.oc.core.security

import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Outcome

/** AGENTS.md invariant as a type (plan 03 S7): only ExternalLinkPolicy creates an ApprovedLink. */
public enum class LinkOrigin {
    AppAuthored,
    ServerContent,
    Markdown,
    AgentCard,
    Terminal,
    Notification,
}

public class ApprovedLink
internal constructor(public val uri: String, public val origin: LinkOrigin)

public sealed interface LinkDecision {
    public data class Open(val link: ApprovedLink) : LinkDecision

    public data class AskFirst(
        val link: ApprovedLink,
        val shownHost: String,
        val insecure: Boolean = false,
    ) : LinkDecision

    public data class Refuse(val reason: LinkRefusal) : LinkDecision
}

public enum class LinkRefusal {
    BadScheme,
    CredentialsInUrl,
    TooLong,
    NotAUrl,
    IntentScheme,
}

@Inject
public class ExternalLinkPolicy {
    /**
     * Port of Dart safeExternalLinkUri + externalLinkHost (B-SEC-064/065): only https, and http so
     * the caller can warn; every other scheme, embedded credentials (also percent-encoded), a
     * missing host, empty, unparseable or overlong values are refused before anything reaches the
     * platform. The shown host names the port only when the URL does.
     */
    public fun check(raw: String, origin: LinkOrigin): LinkDecision {
        val text = raw.trim()
        if (text.isEmpty() || text.any { it.code <= 0x20 || it.code == 0x7f || it == '\\' })
            return LinkDecision.Refuse(LinkRefusal.NotAUrl)
        if (text.length > MAX_LENGTH) return LinkDecision.Refuse(LinkRefusal.TooLong)
        val match = URL.matchEntire(text) ?: return LinkDecision.Refuse(LinkRefusal.NotAUrl)
        val scheme = match.groupValues[1].lowercase()
        if (scheme == "intent") return LinkDecision.Refuse(LinkRefusal.IntentScheme)
        if (scheme !in setOf("http", "https")) return LinkDecision.Refuse(LinkRefusal.BadScheme)
        if (match.groupValues[2] != "//") return LinkDecision.Refuse(LinkRefusal.NotAUrl)
        val authority = match.groupValues[3]
        if ('@' in authority) return LinkDecision.Refuse(LinkRefusal.CredentialsInUrl)
        if ('%' in authority) return LinkDecision.Refuse(LinkRefusal.NotAUrl)
        val parts = UrlParts.parse(text) ?: return LinkDecision.Refuse(LinkRefusal.NotAUrl)
        if (parts.host.isEmpty() || parts.host.any { it.isWhitespace() })
            return LinkDecision.Refuse(LinkRefusal.NotAUrl)
        val normalized = scheme + text.substring(match.groupValues[1].length)
        val link = ApprovedLink(normalized, origin)
        if (origin == LinkOrigin.AppAuthored) return LinkDecision.Open(link)
        val shown = UrlParts.bracketed(parts.host) + (parts.port?.let { ":$it" } ?: "")
        return LinkDecision.AskFirst(link, shown, insecure = scheme == "http")
    }

    /**
     * Dart openAgentSignInPage: the exact verified Claude sign-in page (https, claude.com,
     * /cai/oauth/authorize, no port, user info or fragment) opens without a second sheet; any other
     * address goes through [check] and asks.
     */
    public fun checkSignInPage(raw: String): LinkDecision {
        val decision = check(raw, LinkOrigin.ServerContent)
        if (decision !is LinkDecision.AskFirst) return decision
        val parts = UrlParts.parse(raw.trim()) ?: return decision
        val verified =
            raw.length <= 8192 &&
                raw.none { it.code <= 0x20 || it.code == 0x7f || it == '\\' } &&
                parts.scheme == "https" &&
                parts.userInfo == null &&
                parts.port == null &&
                parts.fragment == null &&
                parts.host == "claude.com" &&
                parts.path == "/cai/oauth/authorize"
        return if (verified) LinkDecision.Open(decision.link) else decision
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
