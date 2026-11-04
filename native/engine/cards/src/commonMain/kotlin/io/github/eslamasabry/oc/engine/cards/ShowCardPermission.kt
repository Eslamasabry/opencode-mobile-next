package io.github.eslamasabry.oc.engine.cards

/**
 * B-CHAT-049 (Flutter `paseo/gateway/permissions.dart`): a qualified, exact `show` request is allowed once without an
 * approval card. Everything else stays an ordinary permission request. Qualification is asked again for every request
 * (a disable or a revoked qualification wins at once); a location change retires a queued approval; a failed approval
 * is never retried; a qualification check that throws counts as "no".
 */
public class ShowCardPermission(
    /** The profile-owned authority, rechecked per request; null = not qualified here. */
    public var qualified: (() -> Boolean)? = null,
) {
    /** One permission request as the gateway sees it. */
    public data class Request(
        val id: String,
        val sessionId: String,
        /** Raw tool name, compared exactly. */
        val toolName: String?,
        /** "tool" for a tool call; questions and plans are never card grants. */
        val kind: String,
        /** The agent's provider; null = not said (a known Claude agent still qualifies). */
        val provider: String?,
        /** The session's known agent provider (from the session list), null = unknown session. */
        val sessionProvider: String?,
        /** The request's directory equals the open location. */
        val inLocation: Boolean,
        /** The daemon reported a tested version (Paseo `paseoSupports`); an unqualified daemon never auto-allows (B-SEC-080). */
        val daemonTested: Boolean,
    )

    public enum class Decision {
        /** Reply allow now; show no card. */
        AllowOnce,

        /** An ordinary permission request. */
        Ask,

        /** Already allowed once: a re-sent pending copy is neither replied to again nor shown. */
        AlreadyAllowed,
    }

    private val answered = HashSet<String>()
    private val failed = HashSet<String>()
    private val queued = LinkedHashMap<String, Request>()

    /** Decides one incoming request. AllowOnce means: reply allow now, show no card, and call [replied] after. */
    public fun decide(request: Request): Decision {
        if (request.id in answered) return Decision.AlreadyAllowed
        if (request.id in failed) return Decision.Ask
        if (!exact(request)) return Decision.Ask
        if (!stillQualified()) {
            queued[request.id] = request
            return Decision.Ask
        }
        return Decision.AllowOnce
    }

    /** The allow reply went out: the same request id is never allowed again (a re-sent pending copy stays silent). */
    public fun replied(requestId: String) {
        answered += requestId
        queued.remove(requestId)
    }

    /** The allow reply failed: the request stays an ordinary permission and is never retried automatically. */
    public fun replyFailed(requestId: String) {
        failed += requestId
        queued.remove(requestId)
    }

    /** True when [requestId] was already handled by an automatic allow (its pending copies must not show a card). */
    public fun handled(requestId: String): Boolean = requestId in answered

    /** Qualification just became true: queued exact requests that are still current, to allow once each. */
    public fun sweep(): List<Request> {
        if (!stillQualified()) return emptyList()
        return queued.values.filter { it.id !in answered && it.id !in failed }.also { queued.clear() }
    }

    /** The open location changed: a queued approval for the old place is retired, never sent. */
    public fun locationChanged() {
        queued.clear()
    }

    private fun stillQualified(): Boolean =
        try {
            qualified?.invoke() == true
        } catch (e: IllegalStateException) {
            false
        } catch (e: RuntimeException) {
            false
        }

    private fun exact(r: Request): Boolean {
        if (!r.daemonTested) return false
        if (r.kind != "tool" || r.toolName != CLAUDE_SHOW) return false
        if (!r.inLocation || r.sessionProvider == null) return false
        if (r.sessionProvider != "claude") return false
        return r.provider == null || r.provider == "claude"
    }

    public companion object {
        /** Only Claude Code's exact raw name: no case folding, trimming or prefix match. */
        public const val CLAUDE_SHOW: String = "mcp__oc-ui__show"
    }
}
