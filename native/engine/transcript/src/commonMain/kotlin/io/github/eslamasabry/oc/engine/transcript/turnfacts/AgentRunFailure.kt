package io.github.eslamasabry.oc.engine.transcript.turnfacts

// B-AGENT-024. Port of lib/domain/agent_run_failure.dart classifyAgentRunFailure: the few kinds of a failed or
// refused turn a person can act on. It reuses the server-cause rules of B-CHAT-016 (RetryStatus.kt causeOf), so
// the app has one classifier for the server's error text. The text itself is never copy: only Details shows it,
// redacted.

/** What a person can do about a failed or refused turn. */
public enum class AgentRunFailureKind {
    /** The provider needs a sign-in, an account or a key. */
    SignIn,

    /** The account has no credit (or quota) for this model: waiting does not fix it. */
    NoCredit,

    /** The model is unknown, not offered, or not allowed on this account (a plan's allowance used up too). */
    ModelUnavailable,

    /** The provider is limiting requests or is overloaded; later works. */
    Busy,

    /** The connection to the model provider dropped or timed out: asking to continue picks up where it stopped. */
    ConnectionLost,

    /** Anything else: the reason is still there for Details. */
    Unknown,
}

private fun ci(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

private val SIGN_IN =
    ci(
        "auth(?:entication)?[ _-]?required|not (?:signed|logged) ?in" +
            "|(?:sign|log) ?in (?:is )?required|please (?:sign|log) ?in" +
            "|(?:sign|log) ?in to (?:use|continue)|login required|no account" +
            "|requires? (?:an? )?(?:account|sign ?in|login)" +
            "|failed to authenticate|oauth (?:session|token) (?:has )?expired" +
            "|please run /login|\\b(?:run|use) \\S+ (?:login|auth)\\b" +
            "|authentication_error|unauthori[sz]ed" +
            "|invalid.?api.?key|missing api.?key|api.?key (?:is )?(?:missing|required|not set)" +
            "|\\b401\\b",
    )

private val NO_CREDIT =
    ci(
        "insufficient.?(?:credits?|quota|funds|balance)" +
            "|(?:no|not enough|out of|zero) credits?" +
            "|(?:add|buy|purchase|top ?up) (?:more |some )?credits?" +
            "|credits? (?:are |is )?(?:required|exhausted|depleted)" +
            "|requires? (?:a )?(?:paid|credits)|paid (?:model|plan) (?:is )?required" +
            "|payment required|exceeded your current quota|\\b402\\b",
    )

private val MODEL_UNAVAILABLE =
    ci(
        "model\\b[^.\\n]{0,80}?\\b(?:not found|not available|unavailable" +
            "|not supported|unsupported|not allowed|not enabled|does not exist|disabled)" +
            "|(?:unknown|invalid|unsupported) model|model_not_found|no such model" +
            "|no endpoints? found|not allowed to use (?:this|the) model" +
            "|(?:no )?access to (?:this|the) model",
    )

private val BUSY = ci("\\bbusy\\b|try again later")

/** The kind of [reason], most specific first: a sign-in fixes more than credit does, both more than another model. */
public fun classifyAgentRunFailure(reason: String?): AgentRunFailureKind = agentRunFailure(reason).kind

/**
 * B-169: a failed turn's [kind] and whether its limit is [out]: waiting a minute will not bring this model back, so
 * another model is the way on. A per-minute rate limit heals by itself (the server retries) and is never out.
 */
public data class AgentRunFailure(val kind: AgentRunFailureKind, val out: Boolean = false)

/** A rate limit's window that lasts a day or longer (OpenRouter `free-models-per-day`, "requests per day"…). */
private val LONG_WINDOW =
    ci(
        "per[ _-]?(?:day|month)|daily|monthly|weekly|per[ _-]?week|\\b(?:rpd|tpd)\\b" +
            "|(?:day|month|week)ly (?:limit|quota)",
    )

/** The server or provider said it gave up retrying. */
private val RETRIES_DONE =
    ci("(?:max(?:imum)?|all) retries|retries (?:exhausted|exceeded|used up)|gave up retrying|giving up|no more retries")

/** A wait the error names: "retry after 3600 seconds", "try again in 2 hours", "resets in 45m", "retry-after: 90". */
private val WAIT =
    ci(
        "(?:retry|try again|resets?|available again)[^0-9\\n]{0,24}?(?<!\\d)(\\d{1,7}(?:\\.\\d+)?)(?!\\d)\\s*" +
            "(ms|milliseconds?|s|secs?|seconds?|m|mins?|minutes?|h|hrs?|hours?|d|days?)?\\b",
    )

/** An absolute reset (`X-RateLimit-Reset`, `"reset": 1762646400000`), in epoch seconds or milliseconds. */
private val RESET_AT = ci("rate.?limit.?reset\"?\\s*[:=]\\s*\"?(\\d{10,13})")

/** Two minutes: a wait longer than this is a limit to switch away from, not a pause (design §1). */
public const val LIMIT_OUT_AFTER_MS: Long = 2 * 60 * 1000L

private fun waitMs(text: String, nowMs: Long?): Long? {
    RESET_AT.find(text)?.let { m ->
        // An absolute time means nothing without the phone's clock.
        val raw = m.groupValues[1].toLongOrNull() ?: return null
        nowMs ?: return null
        val atMs = if (m.groupValues[1].length <= 10) raw * 1000 else raw
        return (atMs - nowMs).coerceAtLeast(0)
    }
    val m = WAIT.find(text) ?: return null
    val n = m.groupValues[1].toDoubleOrNull() ?: return null
    val unit = m.groupValues[2].lowercase()
    val factor =
        when {
            unit.startsWith("ms") || unit.startsWith("milli") -> 1.0
            unit.isEmpty() || unit.startsWith("s") -> 1000.0
            unit.startsWith("m") -> 60_000.0
            unit.startsWith("h") -> 3_600_000.0
            unit.startsWith("d") -> 86_400_000.0
            else -> return null
        }
    return (n * factor).toLong()
}

/** Whether a rate limit in [text] lasts: a day-or-longer window, a wait over two minutes, or retries given up. */
private fun rateLimitLasts(text: String, nowMs: Long?): Boolean =
    LONG_WINDOW.containsMatchIn(text) ||
        RETRIES_DONE.containsMatchIn(text) ||
        (waitMs(text, nowMs)?.let { it > LIMIT_OUT_AFTER_MS } == true)

/**
 * [classifyAgentRunFailure] with the B-169 duration flag. [nowMs] reads an absolute reset time in the text; without
 * it only relative waits count.
 */
public fun agentRunFailure(reason: String?, nowMs: Long? = null): AgentRunFailure {
    val text = reason?.trim().orEmpty()
    if (text.isEmpty()) return AgentRunFailure(AgentRunFailureKind.Unknown)
    val noCredit = NO_CREDIT.containsMatchIn(text)
    if (SIGN_IN.containsMatchIn(text) && !noCredit) return AgentRunFailure(AgentRunFailureKind.SignIn)
    // Waiting cannot add credit.
    if (noCredit) return AgentRunFailure(AgentRunFailureKind.NoCredit, out = true)
    if (MODEL_UNAVAILABLE.containsMatchIn(text)) return AgentRunFailure(AgentRunFailureKind.ModelUnavailable)
    val cause = causeOf(text)
    val out =
        when (cause) {
            Cause.UsageLimit,
            Cause.FreeTierLimit,
            Cause.OutOfCredit -> true
            Cause.RateLimited -> rateLimitLasts(text, nowMs)
            else -> false
        }
    return AgentRunFailure(kindOf(cause, text), out)
}

private fun kindOf(cause: Cause?, text: String): AgentRunFailureKind =
    when (cause) {
        Cause.ProviderAuth -> AgentRunFailureKind.SignIn
        Cause.OutOfCredit -> AgentRunFailureKind.NoCredit
        // A plan's allowance for this model is used up: another model works now.
        Cause.UsageLimit,
        Cause.FreeTierLimit -> AgentRunFailureKind.ModelUnavailable
        Cause.RateLimited,
        Cause.ProviderBusy -> AgentRunFailureKind.Busy
        Cause.ProviderUnreachable,
        Cause.ConnectionDropped,
        Cause.TimedOut -> AgentRunFailureKind.ConnectionLost
        else -> if (BUSY.containsMatchIn(text)) AgentRunFailureKind.Busy else AgentRunFailureKind.Unknown
    }
