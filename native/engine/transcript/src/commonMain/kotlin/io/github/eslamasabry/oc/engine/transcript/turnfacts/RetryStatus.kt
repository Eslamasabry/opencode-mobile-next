package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role

// B-CHAT-016. Pure decisions behind the retry banner (lib/ui/screens/chat/attention_card.dart retryBannerHeadline),
// the assistant error card (assistant_error_row.dart, non-agent-run path) and the unanswered prompt
// (transcript_turns.dart _unansweredTurn). Words stay in the feature.

// ---- Recognised error causes (lib/domain/agent_error_text.dart classifyAgentError), module-internal ----

internal enum class Cause {
    RequestTooLarge, FreeTierLimit, UsageLimit, OutOfCredit, RateLimited, ProviderAuth, ProviderBusy,
    ServerDiskFull, TimedOut, ProviderUnreachable, ConnectionDropped,
}

private fun ci(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

// Order matters: the most specific signal first (exactly the Dart rule order).
private val CAUSE_RULES: List<Pair<Regex, Cause>> =
    listOf(
        ci("queue overflow|payload too large|request entity too large|\\b413\\b|request too large|body exceeded") to
            Cause.RequestTooLarge,
        ci("free.?usage.?limit|free (?:tier|model)s? limit") to Cause.FreeTierLimit,
        ci(
            "usage limit (?:reached|exceeded)|usagelimiterror" +
                "|(?:free|plan|daily|weekly|monthly|\\d+.?hour) limit (?:reached|exceeded)",
        ) to Cause.UsageLimit,
        ci(
            "insufficient.?(quota|credit|funds|balance)|out of credit" +
                "|exceeded your current quota|billing|payment required|\\b402\\b",
        ) to Cause.OutOfCredit,
        ci("rate.?limit|too many requests|\\b429\\b") to Cause.RateLimited,
        ci(
            "invalid.?api.?key|incorrect api key|api.?key (?:is )?missing|missing api.?key" +
                "|authentication (?:failed|required)|unauthorized|\\b401\\b",
        ) to Cause.ProviderAuth,
        ci(
            "overloaded|service unavailable|bad gateway|gateway time-?out|temporarily unavailable" +
                "|\\b50[234]\\b|\\b529\\b",
        ) to Cause.ProviderBusy,
        Regex("ENOSPC|no space left on device") to Cause.ServerDiskFull,
        ci("ETIMEDOUT|ESOCKETTIMEDOUT|timed? ?out|deadline exceeded") to Cause.TimedOut,
        ci(
            "ECONNREFUSED|ENOTFOUND|EAI_AGAIN|EHOSTUNREACH|ENETUNREACH" +
                "|getaddrinfo|fetch failed|unable to connect|network is unreachable",
        ) to Cause.ProviderUnreachable,
        ci(
            "ECONNRESET|EPIPE|ECONNABORTED|socket hang up" +
                "|socket connection was closed|connection (was )?(closed|reset|lost)" +
                "|premature close|stream (closed|ended) unexpectedly|terminated",
        ) to Cause.ConnectionDropped,
    )

internal fun causeOf(raw: String): Cause? {
    if (raw.isBlank()) return null
    return CAUSE_RULES.firstOrNull { (pattern, _) -> pattern.containsMatchIn(raw) }?.second
}

// ---- Retry banner ----

/**
 * The retry banner's facts. [rateLimited] only when the server's reason is a rate limit, or it gave none. [attempt]
 * is null when the server sent no positive attempt (there is no ceiling to invent). [secondsLeft] is null when there
 * is no next time or it is already past: a clock stuck at 0:00 reads as a frozen app.
 */
public data class RetryBannerFacts(val rateLimited: Boolean, val attempt: Int?, val secondsLeft: Int?)

public fun retryBannerFacts(attempt: Int, nextAtMs: Long?, message: String?, nowMs: Long): RetryBannerFacts {
    val cause = causeOf(message ?: "")
    val delta = nextAtMs?.let { it - nowMs }
    return RetryBannerFacts(
        rateLimited = cause == null || cause == Cause.RateLimited,
        attempt = attempt.takeIf { it > 0 },
        secondsLeft = if (delta == null || delta <= 0) null else (delta / 1000).toInt(),
    )
}

/** Whether the retry card should show the server's reason line: never when the headline already says rate limit. */
public fun retryReasonIsRateLimit(message: String?): Boolean = causeOf(message ?: "") == Cause.RateLimited

/** The countdown as digits: "0:42", "1:05", past an hour "1:00:05" (Dart _countdown). */
public fun formatRetryCountdown(seconds: Int): String {
    val minutes = seconds / 60
    val secs = (seconds % 60).toString().padStart(2, '0')
    if (minutes >= 60) return "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}:$secs"
    return "$minutes:$secs"
}

// ---- Assistant error card ----

private val SIGN_IN_GONE =
    Regex(
        "not logged in|please run /login|failed to authenticate|oauth (session|token) (has )?expired|" +
            "could not be refreshed|invalid api key|authentication_error",
        RegexOption.IGNORE_CASE,
    )

private fun errorKindFromName(name: String?): MessageErrorKind? {
    val trimmed = name?.trim()
    if (trimmed.isNullOrEmpty()) return null
    val key = trimmed.lowercase().replace(Regex("[^a-z]"), "")
    return when {
        "contextoverflow" in key || "contextlength" in key -> MessageErrorKind.ContextOverflow
        "outputlength" in key || key == "length" -> MessageErrorKind.OutputLength
        "providerauth" in key || "needsauth" in key || "unauthorized" in key || "authentication" in key ->
            MessageErrorKind.ProviderAuth
        "contentfilter" in key -> MessageErrorKind.ContentFilter
        "abort" in key -> MessageErrorKind.Aborted
        "modelnotfound" in key -> MessageErrorKind.ModelNotFound
        else -> MessageErrorKind.Unknown
    }
}

/** Dart MessageErrorKind.refineFromText: the error class may sit in the message text rather than the name. */
private fun refineErrorKind(kind: MessageErrorKind, text: String?): MessageErrorKind {
    if (kind != MessageErrorKind.Unknown || text == null) return kind
    if (SIGN_IN_GONE.containsMatchIn(text)) return MessageErrorKind.ProviderAuth
    val head = text.trimStart().split(Regex("[:\\s]")).first()
    return errorKindFromName(head) ?: MessageErrorKind.Unknown
}

/** Which plain-language card an assistant error shows (the `error-card-<id>` the Dart row keys). */
public enum class AssistantErrorState {
    ContextOverflow, OutputLength, ProviderAuth, ModelNotFound,

    /** OpenCode's free tier refused a large request: shorten, or continue in a new conversation. */
    FreeTier,

    /** A plan's usage allowance for this model is used up: another model. */
    UsageLimit,

    /** Refused by the content filter: no resend, no other model. */
    ContentFilter,

    /** A stop, not an error: the muted Stopped row. */
    Stopped,
    Generic,
}

/**
 * The card for an assistant error with no agent-run failure behind it (agent-run kinds come from the helper's run
 * record and are not decided here). [canShortenOrStartNew] / [canChooseModel] mirror Dart's guards: the free-tier and
 * usage-limit cards appear only when their way on exists.
 */
public fun assistantErrorState(
    kind: MessageErrorKind?,
    errorText: String?,
    recovered: Boolean = false,
    canChooseModel: Boolean = true,
    canShortenOrStartNew: Boolean = true,
): AssistantErrorState {
    val raw = errorText ?: ""
    val refined = refineErrorKind(kind ?: MessageErrorKind.Unknown, raw)
    val cause = causeOf(raw)
    val choose = canChooseModel && !recovered
    if (!recovered && cause == Cause.FreeTierLimit && (canShortenOrStartNew || choose)) return AssistantErrorState.FreeTier
    if (cause == Cause.UsageLimit && choose) return AssistantErrorState.UsageLimit
    return when (refined) {
        MessageErrorKind.ModelNotFound -> AssistantErrorState.ModelNotFound
        MessageErrorKind.ContextOverflow -> AssistantErrorState.ContextOverflow
        MessageErrorKind.ProviderAuth -> AssistantErrorState.ProviderAuth
        MessageErrorKind.OutputLength -> AssistantErrorState.OutputLength
        MessageErrorKind.ContentFilter -> AssistantErrorState.ContentFilter
        MessageErrorKind.Aborted -> AssistantErrorState.Stopped
        MessageErrorKind.Unknown -> AssistantErrorState.Generic
    }
}

// ---- A prompt that got no answer ----

/** NotAnswered: ended on an error or refused before any step. NoReply: ended silently, no words, steps or error. */
public enum class UnansweredState { NotAnswered, NoReply }

public data class UnansweredTurn(
    val promptIndex: Int,
    /** The step that ends the turn, or null when no step came. */
    val endIndex: Int?,
    val state: UnansweredState,
)

private fun variantRawType(message: MessageWithParts): String? =
    message.parts.firstOrNull { it.rawType.startsWith("v2:") }?.rawType

private fun isPrompt(message: MessageWithParts): Boolean = message.info.role == Role.User && variantRawType(message) == null

/**
 * The newest turn when it got no answer (Dart _unansweredTurn). Null when it was answered, stopped (an abort, or
 * [stoppedPromptId]), or is still [running]. [refused]: the server refused the prompt before any step.
 */
public fun unansweredTurn(
    messages: List<MessageWithParts>,
    refused: Boolean,
    running: Boolean,
    stoppedPromptId: String? = null,
): UnansweredTurn? {
    if (running) return null
    val prompt = messages.indexOfLast(::isPrompt)
    if (prompt < 0) return null
    var last: Int? = null
    var stepped = false
    for (index in prompt + 1 until messages.size) {
        val message = messages[index]
        // A command that answers with the conversation compacted did answer.
        if (variantRawType(message) == "v2:compaction") return null
        if (message.info.role != Role.Assistant) continue
        last = index
        if (message.parts.any { it.type == PartType.Text && it.text.isNotBlank() }) return null
        stepped = stepped || message.parts.any { it.type == PartType.Tool }
    }
    val stopped = messages[prompt].info.id == stoppedPromptId
    // A command (`/compact`) may do its work without a word back.
    val command = messages[prompt].parts.any { it.type == PartType.Text && it.text.trimStart().startsWith("/") }
    if (last == null) {
        if (refused) return UnansweredTurn(prompt, null, UnansweredState.NotAnswered)
        return if (stopped || command) null else UnansweredTurn(prompt, null, UnansweredState.NoReply)
    }
    val info = messages[last].info
    val raw = info.errorText
    if (raw == null) {
        val done = (info.time?.completedMs ?: 0) > 0
        if (stepped || stopped || !done) return null
        return UnansweredTurn(prompt, last, UnansweredState.NoReply)
    }
    if (refineErrorKind(info.errorKind ?: MessageErrorKind.Unknown, raw) == MessageErrorKind.Aborted) return null
    return UnansweredTurn(prompt, last, UnansweredState.NotAnswered)
}
