package io.github.eslamasabry.oc.engine.agents.errors

/**
 * The recognised cause of an agent runtime error (Flutter `lib/domain/agent_error_text.dart`, B-AGENT-012). Runtime
 * text is from one programmer to another; the person needs what happened and what to do, so the cause is a typed value
 * and the words live in [AgentErrorWords]. The original text stays for a Details view.
 */
public enum class AgentErrorCause {
    /** The link between the server and the model provider broke mid-request. */
    ConnectionDropped,

    /** The provider did not answer in time. */
    TimedOut,

    /** The server could not reach the provider at all (DNS, refused, offline). */
    ProviderUnreachable,

    /** The request was too big for the connection or the provider. */
    RequestTooLarge,

    /** The provider is limiting the request rate. */
    RateLimited,

    /** The provider is overloaded or temporarily unavailable. */
    ProviderBusy,

    /** The provider account has no credit or quota left. */
    OutOfCredit,

    /** OpenCode's free models refused the request; shortening or starting a new chat helps, waiting need not. */
    FreeTierLimit,

    /** A plan's usage allowance for this model is used up for now. */
    UsageLimit,

    /** The provider needs a missing or rejected credential replaced (sign-in recovery). */
    ProviderAuth,

    /** The machine running the server has no disk space left. */
    ServerDiskFull,
    ;

    /** Causes a server may retry by itself. Actual retry state must come from the server. */
    public val mayHealOnServer: Boolean get() = this in SELF_HEALING

    /** The person recovers by signing in to the provider again. */
    public val offersSignIn: Boolean get() = this == ProviderAuth

    public companion object {
        private val SELF_HEALING = setOf(ConnectionDropped, TimedOut, RateLimited, ProviderBusy)
    }
}

public object AgentErrorText {
    private fun ci(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    // Order matters: the most specific signal first (Flutter `_rules`, verbatim).
    private val RULES: List<Pair<Regex, AgentErrorCause>> = listOf(
        ci(
            "queue overflow|payload too large|request entity too large|\\b413\\b|request too large|body exceeded",
        ) to AgentErrorCause.RequestTooLarge,
        ci("free.?usage.?limit|free (?:tier|model)s? limit") to AgentErrorCause.FreeTierLimit,
        ci(
            "usage limit (?:reached|exceeded)|usagelimiterror" +
                "|(?:free|plan|daily|weekly|monthly|\\d+.?hour) limit (?:reached|exceeded)",
        ) to AgentErrorCause.UsageLimit,
        ci(
            "insufficient.?(quota|credit|funds|balance)|out of credit" +
                "|exceeded your current quota|billing|payment required|\\b402\\b",
        ) to AgentErrorCause.OutOfCredit,
        ci("rate.?limit|too many requests|\\b429\\b") to AgentErrorCause.RateLimited,
        ci(
            "invalid.?api.?key|incorrect api key|api.?key (?:is )?missing" +
                "|missing api.?key|authentication (?:failed|required)" +
                "|unauthorized|\\b401\\b",
        ) to AgentErrorCause.ProviderAuth,
        ci(
            "overloaded|service unavailable|bad gateway|gateway time-?out" +
                "|temporarily unavailable|\\b50[234]\\b|\\b529\\b",
        ) to AgentErrorCause.ProviderBusy,
        Regex("ENOSPC|no space left on device") to AgentErrorCause.ServerDiskFull,
        ci("ETIMEDOUT|ESOCKETTIMEDOUT|timed? ?out|deadline exceeded") to AgentErrorCause.TimedOut,
        ci(
            "ECONNREFUSED|ENOTFOUND|EAI_AGAIN|EHOSTUNREACH|ENETUNREACH" +
                "|getaddrinfo|fetch failed|unable to connect|network is unreachable",
        ) to AgentErrorCause.ProviderUnreachable,
        ci(
            "ECONNRESET|EPIPE|ECONNABORTED|socket hang up" +
                "|socket connection was closed|connection (was )?(closed|reset|lost)" +
                "|premature close|stream (closed|ended) unexpectedly|terminated",
        ) to AgentErrorCause.ConnectionDropped,
    )

    private val DEVELOPER_ADVICE = ci(
        "\\s*For more information, pass `?verbose: ?true`? in the second argument to fetch\\(\\)\\.?",
    )
    private val ERRNO_PREFIX = Regex("^(E[A-Z]{3,}|[A-Za-z]+Error)\\s*:\\s*")

    /** The recognised cause of [raw], or null (blank or unrecognised: a typed or generic fallback applies). */
    public fun classify(raw: String): AgentErrorCause? {
        if (raw.isBlank()) return null
        return RULES.firstOrNull { (pattern, _) -> pattern.containsMatchIn(raw) }?.second
    }

    /** [text] without library-flag advice and the errno or exception class in front; for Details, never a headline. */
    public fun clean(text: String): String {
        var cleaned = text.replace(DEVELOPER_ADVICE, "").trim()
        val prefixed = ERRNO_PREFIX.find(cleaned)
        if (prefixed != null && cleaned.length > prefixed.range.last + 1) {
            cleaned = cleaned.substring(prefixed.range.last + 1).trim()
        }
        return cleaned.ifEmpty { text.trim() }
    }
}
