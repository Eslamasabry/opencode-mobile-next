package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.core.model.transcript.PartType

// Port of the timing half of lib/builtin/reply_watch.dart (B-CHAT-092): prompt → first output → end, as a pure
// immutable reducer. The wake-lock lease, perf-trace spans and the persisted "last in-app" value stay with the feature.

/** One finished reply, timed in the app from the server's events (Dart ReplyTiming). */
public data class ReplyTiming(
    /** It ran on the in-app server. */
    val inApp: Boolean,
    /** Prompt seen back from the server → the reply's first output; null when it ended without any. */
    val firstTokenMs: Long?,
    /** The same wait by the server's clock (user message created → first part started); null when not said. */
    val serverFirstTokenMs: Long?,
    /** Prompt → the reply's end. */
    val totalMs: Long,
    /** `provider/model` of the reply, when the server said. */
    val model: String?,
    /** The reply ended with an error. */
    val failed: Boolean,
    val finishedAtMs: Long,
)

/** A reply being timed in one session. */
public data class ReplyRun(
    val startMs: Long,
    val inApp: Boolean,
    val serverCreatedMs: Long?,
    val busy: Boolean = false,
    val firstMs: Long? = null,
    val serverFirstMs: Long? = null,
    val model: String? = null,
)

/** What the feature maps server events to. */
public sealed interface ReplyTimingEvent {
    /** `message.updated` for a user message: the prompt, back from the server. */
    public data class UserMessageSeen(
        val sessionId: String,
        val messageId: String,
        val serverCreatedMs: Long?,
        val onInAppServer: Boolean,
    ) : ReplyTimingEvent

    /** `message.updated` for an assistant message. */
    public data class AssistantMessageSeen(
        val sessionId: String,
        val messageId: String,
        val providerId: String?,
        val modelId: String?,
    ) : ReplyTimingEvent

    /** `message.part.updated`: only text, reasoning and tool parts of a known assistant message are output. */
    public data class PartSeen(
        val sessionId: String,
        val messageId: String,
        val type: PartType,
        /** The part's (or its tool state's) `time.start`. */
        val serverStartMs: Long?,
    ) : ReplyTimingEvent

    /** `message.part.delta` of a known assistant message. */
    public data class DeltaSeen(val sessionId: String, val messageId: String) : ReplyTimingEvent

    /** `session.status` busy or retry. */
    public data class Busy(val sessionId: String) : ReplyTimingEvent

    /** `session.status` idle / `session.idle` (failed false) or `session.error` (failed true). */
    public data class Ended(val sessionId: String, val failed: Boolean) : ReplyTimingEvent
}

public data class ReplyTimingState(
    val runs: Map<String, ReplyRun> = emptyMap(),
    /** Recently seen user message ids, oldest first, at most [REMEMBERED]. */
    val userMessages: List<String> = emptyList(),
    /** Recently seen assistant message ids, oldest first, at most [REMEMBERED]. */
    val assistantMessages: List<String> = emptyList(),
    /** The last reply on any server. */
    val last: ReplyTiming? = null,
    /** The last reply on the in-app server. */
    val lastInApp: ReplyTiming? = null,
) {
    public companion object {
        public const val REMEMBERED: Int = 400

        /** A run whose end never came (a lost connection) is dropped after this, so it cannot time a later reply. */
        public const val STALE_RUN_MS: Long = 60 * 60 * 1000L
    }
}

public fun ReplyTimingState.on(event: ReplyTimingEvent, nowMs: Long): ReplyTimingState =
    when (event) {
        is ReplyTimingEvent.UserMessageSeen -> replyOnUser(event, nowMs)
        is ReplyTimingEvent.AssistantMessageSeen -> {
            val remembered = replyRemember(assistantMessages, event.messageId)
            val run = runs[event.sessionId]
            val model = replyModelOf(event.providerId, event.modelId)
            val next = if (remembered != null) copy(assistantMessages = remembered) else this
            if (run != null && run.model == null && model != null) {
                next.copy(runs = runs + (event.sessionId to run.copy(model = model)))
            } else {
                next
            }
        }
        is ReplyTimingEvent.PartSeen ->
            if (event.type != PartType.Text && event.type != PartType.Reasoning && event.type != PartType.Tool) this
            else if (event.messageId !in assistantMessages) this
            else replyFirstOutput(event.sessionId, event.serverStartMs, nowMs)
        is ReplyTimingEvent.DeltaSeen ->
            if (event.messageId !in assistantMessages) this else replyFirstOutput(event.sessionId, null, nowMs)
        is ReplyTimingEvent.Busy -> {
            val run = runs[event.sessionId]
            if (run == null || run.busy) this else copy(runs = runs + (event.sessionId to run.copy(busy = true)))
        }
        is ReplyTimingEvent.Ended -> replyFinish(event.sessionId, event.failed, nowMs)
    }

private fun ReplyTimingState.replyOnUser(event: ReplyTimingEvent.UserMessageSeen, nowMs: Long): ReplyTimingState {
    // A user message seen again (OpenCode updates its summary after the reply) never starts a new timing.
    val remembered = replyRemember(userMessages, event.messageId) ?: return this
    val current = runs[event.sessionId]
    // A prompt queued behind a running reply belongs to that run.
    if (current != null && (current.busy || current.firstMs != null)) return copy(userMessages = remembered)
    val live = runs.filterValues { nowMs - it.startMs <= ReplyTimingState.STALE_RUN_MS }
    val run = ReplyRun(startMs = nowMs, inApp = event.onInAppServer, serverCreatedMs = event.serverCreatedMs)
    return copy(userMessages = remembered, runs = live + (event.sessionId to run))
}

private fun ReplyTimingState.replyFirstOutput(sessionId: String, serverStartMs: Long?, nowMs: Long): ReplyTimingState {
    val run = runs[sessionId]
    if (run == null || run.firstMs != null) return this
    val created = run.serverCreatedMs
    val serverFirst =
        if (created != null && serverStartMs != null && serverStartMs >= created) serverStartMs - created else null
    return copy(runs = runs + (sessionId to run.copy(firstMs = nowMs, serverFirstMs = serverFirst)))
}

private fun ReplyTimingState.replyFinish(sessionId: String, failed: Boolean, nowMs: Long): ReplyTimingState {
    val run = runs[sessionId] ?: return this
    val timing =
        ReplyTiming(
            inApp = run.inApp,
            firstTokenMs = run.firstMs?.let { it - run.startMs },
            serverFirstTokenMs = run.serverFirstMs,
            totalMs = nowMs - run.startMs,
            model = run.model,
            failed = failed,
            finishedAtMs = nowMs,
        )
    return copy(runs = runs - sessionId, last = timing, lastInApp = if (run.inApp) timing else lastInApp)
}

/** The ids with [id] appended (oldest dropped past the bound), or null when [id] was already seen. */
private fun replyRemember(ids: List<String>, id: String): List<String>? {
    if (id in ids) return null
    val next = ids + id
    return if (next.size > ReplyTimingState.REMEMBERED) next.drop(next.size - ReplyTimingState.REMEMBERED) else next
}

private fun replyModelOf(providerId: String?, modelId: String?): String? =
    if (providerId == null || modelId == null) null else "$providerId/$modelId"
