package io.github.eslamasabry.oc.core.security

import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Outcome

/** AGENTS.md invariant as a type (plan 03 S7): only ExternalLinkPolicy creates an ApprovedLink. */
public enum class LinkOrigin { AppAuthored, ServerContent, Markdown, AgentCard, Terminal, Notification }

public class ApprovedLink internal constructor(public val uri: String, public val origin: LinkOrigin)

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
        if (scheme !in setOf("http", "https", "mailto")) return LinkDecision.Refuse(LinkRefusal.BadScheme)
        val authority = match.groupValues[2]
        if ('@' in authority && scheme != "mailto") return LinkDecision.Refuse(LinkRefusal.CredentialsInUrl)
        val link = ApprovedLink(text, origin)
        return if (origin == LinkOrigin.AppAuthored) LinkDecision.Open(link)
        else LinkDecision.AskFirst(link, authority.substringBefore(':'))
    }

    private companion object {
        const val MAX_LENGTH = 2048
        val URL = Regex("""^([a-zA-Z][a-zA-Z0-9+.-]*):(?://)?([^/?#\s]*)([^\s]*)$""")
    }
}

/** Implemented once on each platform (Android: :android:platform). */
public fun interface ExternalLinkOpener {
    public fun open(link: ApprovedLink): Outcome<Unit>
}
