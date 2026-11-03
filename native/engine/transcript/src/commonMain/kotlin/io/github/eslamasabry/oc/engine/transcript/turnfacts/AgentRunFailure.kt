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
public fun classifyAgentRunFailure(reason: String?): AgentRunFailureKind {
    val text = reason?.trim().orEmpty()
    if (text.isEmpty()) return AgentRunFailureKind.Unknown
    val noCredit = NO_CREDIT.containsMatchIn(text)
    if (SIGN_IN.containsMatchIn(text) && !noCredit) return AgentRunFailureKind.SignIn
    if (noCredit) return AgentRunFailureKind.NoCredit
    if (MODEL_UNAVAILABLE.containsMatchIn(text)) return AgentRunFailureKind.ModelUnavailable
    return when (causeOf(text)) {
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
}
