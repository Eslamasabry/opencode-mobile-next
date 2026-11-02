package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import kotlin.math.roundToLong

// Protocol-neutral classification rules from lib/paseo/mappers.dart (B-CHAT-069). The Paseo item mapper itself is
// out of scope; it uses these to turn an agent's reply text into an error row instead of prose, and a compaction
// item into a notice instead of a reply.

/** How an agent's reply text is read. */
public enum class AgentErrorClass {
    /** The agent can no longer use its sign-in: an error row with Sign in again; raw text kept for Details. */
    SignIn,

    /** The helper bridge's own RPC failure report ("[System Error] ..."): an error row, never prose. */
    HelperError,

    /** Claude Code's own failed-call line ("API Error: ...", usage limit, low credit): a failed turn. */
    TurnFailure,

    /** Ordinary reply text. */
    Other,
}

/**
 * An agent's own words for a sign-in it can no longer use (Dart paseoSignInFailure). Not the transient "another
 * process is refreshing it", which passes on retry.
 */
public val AgentSignInFailure: Regex =
    Regex(
        "failed to authenticate|oauth (session|token) (has )?expired|could not be refreshed|please run /login|" +
            "invalid api key|authentication_error|not logged in",
        RegexOption.IGNORE_CASE,
    )

private val SIGN_IN_ASK = Regex("\\b(sign|log) ?in\\b", RegexOption.IGNORE_CASE)
private val CLAUDE_TURN_FAILURE = Regex("^\\s*(?:API Error\\b|Claude AI usage limit reached\\b|Credit balance is too low\\b)")

/** How the helper's ACP bridge words an agent's RPC failure in the reply text. */
public fun isHelperSystemError(text: String): Boolean = text.trimStart().startsWith("[System Error]")

/**
 * Dart paseoItemMessage's assistant_message rule: a sign-in failure anywhere, or inside the bridge's error report
 * any ask to sign in (in a plain reply it could be advice), is [AgentErrorClass.SignIn]; other bridge reports are
 * [AgentErrorClass.HelperError]; Claude Code's failed-call line is [AgentErrorClass.TurnFailure].
 */
public fun classifyAgentError(text: String): AgentErrorClass {
    val system = isHelperSystemError(text)
    return when {
        AgentSignInFailure.containsMatchIn(text) || (system && SIGN_IN_ASK.containsMatchIn(text)) -> AgentErrorClass.SignIn
        system -> AgentErrorClass.HelperError
        CLAUDE_TURN_FAILURE.containsMatchIn(text) -> AgentErrorClass.TurnFailure
        else -> AgentErrorClass.Other
    }
}

/** The error kind such a message carries: provider auth for a sign-in, otherwise none (null for prose too). */
public fun agentErrorKind(errorClass: AgentErrorClass): MessageErrorKind? =
    if (errorClass == AgentErrorClass.SignIn) MessageErrorKind.ProviderAuth else null

/** Whether reply text becomes an error row (no parts, the text kept as errorText for Details). */
public fun isAgentErrorRow(errorClass: AgentErrorClass): Boolean = errorClass != AgentErrorClass.Other

/** A compaction is a notice line in the transcript (raw part type `v2:compaction`), never an assistant reply. */
public const val COMPACTION_PART_TYPE: String = "v2:compaction"

public enum class CompactionState { Running, Completed }

/** What started a compaction, when the item says. */
public enum class CompactionTrigger { Auto, Manual }

public data class CompactionNoticeFacts(
    val state: CompactionState,
    val trigger: CompactionTrigger?,
    /** The conversation's size before shortening, only once done and only when positive. */
    val preTokens: Long?,
)

/** Dart paseoItemMessage `compaction` case: status `completed` is done, anything else is still running. */
public fun compactionNoticeFacts(status: String?, trigger: String?, preTokens: Double?): CompactionNoticeFacts {
    val done = status == "completed"
    return CompactionNoticeFacts(
        state = if (done) CompactionState.Completed else CompactionState.Running,
        trigger =
            when (trigger) {
                "auto" -> CompactionTrigger.Auto
                "manual" -> CompactionTrigger.Manual
                else -> null
            },
        preTokens = if (done && preTokens != null && preTokens > 0) preTokens.roundToLong() else null,
    )
}
