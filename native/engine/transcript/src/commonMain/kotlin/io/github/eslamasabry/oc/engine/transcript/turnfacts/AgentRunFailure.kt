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

private const val SECOND_MS = 1_000.0
private const val MINUTE_MS = 60 * SECOND_MS
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS

/** Epoch seconds have ten digits until 2286; longer reset values are milliseconds. */
private const val EPOCH_SECONDS_DIGITS = 10

/** Milliseconds per [unit] of a wait ("ms", "s", "min", "h", "d"; none means seconds); null when unknown. */
private fun unitMs(unit: String): Double? =
    when {
        unit.startsWith("ms") || unit.startsWith("milli") -> 1.0
        unit.isEmpty() || unit.startsWith("s") -> SECOND_MS
        unit.startsWith("m") -> MINUTE_MS
        unit.startsWith("h") -> HOUR_MS
        unit.startsWith("d") -> DAY_MS
        else -> null
    }

/** The wait until an absolute reset; null without the phone's clock (an absolute time means nothing then). */
private fun resetWaitMs(digits: String, nowMs: Long?): Long? {
    val raw = digits.toLongOrNull()
    if (raw == null || nowMs == null) return null
    val atMs = if (digits.length <= EPOCH_SECONDS_DIGITS) raw * SECOND_MS.toLong() else raw
    return (atMs - nowMs).coerceAtLeast(0)
}

/** How long the error says to wait, or null when it names no wait. */
private fun waitMs(text: String, nowMs: Long?): Long? {
    val reset = RESET_AT.find(text)
    val wait = if (reset == null) WAIT.find(text) else null
    val n = wait?.groupValues?.get(1)?.toDoubleOrNull()
    val factor = wait?.groupValues?.get(2)?.lowercase()?.let(::unitMs)
    return when {
        reset != null -> resetWaitMs(reset.groupValues[1], nowMs)
        n == null || factor == null -> null
        else -> (n * factor).toLong()
    }
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
    val noCredit = NO_CREDIT.containsMatchIn(text)
    return when {
        text.isEmpty() -> AgentRunFailure(AgentRunFailureKind.Unknown)
        SIGN_IN.containsMatchIn(text) && !noCredit -> AgentRunFailure(AgentRunFailureKind.SignIn)
        // Waiting cannot add credit.
        noCredit -> AgentRunFailure(AgentRunFailureKind.NoCredit, out = true)
        MODEL_UNAVAILABLE.containsMatchIn(text) -> AgentRunFailure(AgentRunFailureKind.ModelUnavailable)
        else -> {
            val cause = causeOf(text)
            AgentRunFailure(kindOf(cause, text), out = causeLasts(cause, text, nowMs))
        }
    }
}

/** A plan's usage, the free tier and credit do not come back in a minute; a rate limit only when [rateLimitLasts]. */
private fun causeLasts(cause: Cause?, text: String, nowMs: Long?): Boolean =
    when (cause) {
        Cause.UsageLimit,
        Cause.FreeTierLimit,
        Cause.OutOfCredit -> true
        Cause.RateLimited -> rateLimitLasts(text, nowMs)
        else -> false
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
