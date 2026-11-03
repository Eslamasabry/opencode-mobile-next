package io.github.eslamasabry.oc.feature.chat.world

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.CatalogSnapshot
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.engine.transcript.ledger.LedgerSnapshot
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnLedger
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ReplyTiming
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ReplyTimingEvent
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ReplyTimingState
import io.github.eslamasabry.oc.engine.transcript.turnfacts.on
import io.github.eslamasabry.oc.core.model.transcript.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The server is retrying the run (core `RunRetrying`): attempt and when the next try starts. Retry text stays out. */
public data class RunRetry(val attempt: Int, val nextAtMs: Long?, val tag: String?, val seenAtMs: Long)

/**
 * Everything the turn facts read from one open conversation (CHAT-TRANSCRIPT): the ledger of full messages, whether a
 * run is going, the server's retry, when the last progress arrived, and each model's context window. Plain values,
 * no words; [io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter] derives what the screen shows.
 */
public data class TurnFeed(
    val ledger: LedgerSnapshot = LedgerSnapshot.Empty,
    val busy: Boolean = false,
    /** When the current run started (receipt time), null while idle. */
    val runStartedAtMs: Long? = null,
    /** Receipt time of the newest event that showed progress in this conversation. */
    val lastProgressAtMs: Long? = null,
    val retry: RunRetry? = null,
    /** How the last run ended; null while running or before any end. */
    val lastOutcome: RunOutcome? = null,
    /** `providerId/modelId` → context window in tokens; empty until the catalog is read. */
    val windows: Map<String, Long> = emptyMap(),
    val link: ChatLink = ChatLink.Connecting,
    /** B-CHAT-092: each timed reply, by the turn it answered (prompt → first output → end, app clock). */
    val timings: Map<String, ReplyTiming> = emptyMap(),
) {
    public companion object {
        public val Empty: TurnFeed = TurnFeed()

        public fun windowKey(providerId: String, modelId: String): String = "$providerId/$modelId"
    }
}

/** A world that can feed the turn facts (the real [GraphChatWorld]; fakes need not). */
public interface TurnFeedSource {
    public val turnFeed: StateFlow<TurnFeed>
}

/**
 * Keeps the [TurnFeed] of one conversation. The chat world calls it from its single collector context: [event] for
 * every gateway event with its receipt time, [history] for every page it reads, [reset] when it binds another graph.
 */
internal class TurnFeedKeeper(private val sessionId: String) {
    private var ledger = TurnLedger(sessionId)
    private var timing = ReplyTimingState()
    private val state = MutableStateFlow(TurnFeed.Empty)
    val feed: StateFlow<TurnFeed> = state.asStateFlow()

    fun reset() {
        ledger = TurnLedger(sessionId)
        timing = ReplyTimingState()
        state.value = TurnFeed(windows = state.value.windows)
    }

    fun link(link: ChatLink) {
        if (state.value.link != link) state.value = state.value.copy(link = link)
    }

    /** The fence for a history read that starts now (B-CHAT-112/117): pass it back to [history] with the page. */
    fun historyVersion(): Long = ledger.historyVersion()

    fun history(page: List<MessageWithParts>, beforeVersion: Long = Long.MAX_VALUE) {
        ledger.load(page, beforeVersion)
        state.value = state.value.copy(ledger = ledger.snapshot())
    }

    /** A run is going now (the session status said busy when the chat opened; [atMs] null = start unknown). */
    fun running(atMs: Long?) {
        val s = state.value
        if (!s.busy) state.value = s.copy(busy = true, runStartedAtMs = atMs, lastProgressAtMs = atMs, lastOutcome = null)
    }

    fun event(event: GatewayEvent, atMs: Long) {
        time(event, atMs)
        val s = state.value
        when (event) {
            is GatewayEvent.RunStarted ->
                if (event.sessionId.raw == sessionId)
                    state.value =
                        s.copy(
                            busy = true,
                            runStartedAtMs = s.runStartedAtMs.takeIf { s.busy } ?: atMs,
                            lastProgressAtMs = atMs,
                            retry = null,
                            lastOutcome = null,
                        )
            is GatewayEvent.RunRetrying ->
                if (event.sessionId.raw == sessionId)
                    state.value =
                        s.copy(
                            busy = true,
                            // B-CHAT-016: OpenCode 2 sends no tag; its reason is in the (redacted) details, and only that tells
                            // a rate limit from an overload or a dropped connection.
                            retry = RunRetry(event.attempt, event.nextAtMs, event.tag?.raw ?: event.details?.text, atMs),
                            lastProgressAtMs = atMs,
                        )
            is GatewayEvent.RunEnded ->
                if (event.sessionId.raw == sessionId)
                    state.value =
                        s.copy(
                            busy = false,
                            runStartedAtMs = null,
                            retry = null,
                            lastProgressAtMs = atMs,
                            // Idle never replaces an earlier failure or stop of the same run.
                            lastOutcome =
                                if (event.outcome == RunOutcome.Idle && s.lastOutcome != null && !s.busy) s.lastOutcome
                                else event.outcome,
                        )
            else ->
                if (ledger.apply(event)) {
                    state.value =
                        s.copy(
                            ledger = ledger.snapshot(),
                            lastProgressAtMs = atMs,
                            // Fresh output means the retry worked.
                            retry = null,
                        )
                } else if (event is GatewayEvent.PartDelta && event.sessionId.raw == sessionId) {
                    state.value = s.copy(lastProgressAtMs = atMs, retry = null)
                }
        }
    }

    /** B-CHAT-092: times the reply from this conversation's own events; a finished timing joins its turn. */
    private fun time(event: GatewayEvent, atMs: Long) {
        val e: ReplyTimingEvent =
            when (event) {
                is GatewayEvent.MessageUpserted ->
                    if (event.message.role == Role.User)
                        ReplyTimingEvent.UserMessageSeen(
                            event.message.sessionId,
                            event.message.id,
                            event.message.time?.createdMs,
                            onInAppServer = false,
                        )
                    else
                        ReplyTimingEvent.AssistantMessageSeen(
                            event.message.sessionId,
                            event.message.id,
                            event.message.providerId,
                            event.message.modelId,
                        )
                is GatewayEvent.PartUpserted ->
                    ReplyTimingEvent.PartSeen(event.part.sessionId, event.part.messageId, event.part.type, event.part.tool?.startedAtMs)
                is GatewayEvent.PartDelta -> ReplyTimingEvent.DeltaSeen(event.sessionId.raw, event.messageId.raw)
                is GatewayEvent.RunStarted -> ReplyTimingEvent.Busy(event.sessionId.raw)
                is GatewayEvent.RunRetrying -> ReplyTimingEvent.Busy(event.sessionId.raw)
                is GatewayEvent.RunEnded ->
                    ReplyTimingEvent.Ended(event.sessionId.raw, failed = event.outcome is RunOutcome.Failed)
                else -> return
            }
        val mine =
            when (e) {
                is ReplyTimingEvent.UserMessageSeen -> e.sessionId
                is ReplyTimingEvent.AssistantMessageSeen -> e.sessionId
                is ReplyTimingEvent.PartSeen -> e.sessionId
                is ReplyTimingEvent.DeltaSeen -> e.sessionId
                is ReplyTimingEvent.Busy -> e.sessionId
                is ReplyTimingEvent.Ended -> e.sessionId
            } == sessionId
        if (!mine) return
        val before = timing.last
        timing = timing.on(e, atMs)
        val after = timing.last
        if (after != null && after !== before && event is GatewayEvent.RunEnded) {
            val turn = ledger.snapshot().turns.lastOrNull()?.turnId ?: return
            state.value = state.value.copy(timings = state.value.timings + (turn to after))
        }
    }

    /** Reads every model's context window once per graph; a failed read leaves the ring hidden (B-CHAT-019). */
    suspend fun loadWindows(read: suspend () -> Outcome<CatalogSnapshot>) {
        if (state.value.windows.isNotEmpty()) return
        val catalog = (read() as? Outcome.Ok)?.value ?: return
        val windows = HashMap<String, Long>()
        for (m in catalog.models) if (m.contextLimit > 0) windows[TurnFeed.windowKey(m.providerId, m.id)] = m.contextLimit.toLong()
        state.value = state.value.copy(windows = windows)
    }
}
