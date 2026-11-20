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
    /**
     * The stretches of this run the person was asked and had not answered yet, as (asked, answered) receipt times: a
     * running step's own clock leaves them out (it runs only after the decision).
     */
    val waits: List<Pair<Long, Long>> = emptyList(),
    /** How the last run ended; null while running or before any end. */
    val lastOutcome: RunOutcome? = null,
    /** `providerId/modelId` → context window in tokens; empty until the catalog is read. */
    val windows: Map<String, Long> = emptyMap(),
    val link: ChatLink = ChatLink.Connecting,
    /** B-CHAT-092: each timed reply, by the turn it answered (prompt → first output → end, app clock). */
    val timings: Map<String, ReplyTiming> = emptyMap(),
    /**
     * B8: the server said this conversation runs nothing, after the newest history was read (its status read, or a
     * run end it sent). False while that is not known: before the status read answers, when it failed, and once the
     * conversation shows activity again. Only a said idle lets an unfinished reply read as cut off.
     */
    val idleSaid: Boolean = false,
) {
    public companion object {
        public val Empty: TurnFeed = TurnFeed()

        public fun windowKey(providerId: String, modelId: String): String = "$providerId/$modelId"
    }
}

/** The newest turn, its reply count and newest reply (B8: what an idle the server said was said for). */
private fun LedgerSnapshot.newestMark(): Triple<String, Int, String?>? =
    turns.lastOrNull()?.let { t -> Triple(t.turnId, t.messages.size, t.messages.lastOrNull()?.info?.id) }

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

    /** What the live ledger holds now (never the [known] one shown in its place). */
    private var live: LedgerSnapshot = LedgerSnapshot.Empty

    /**
     * Cache-first: the messages last known (the page kept from a past visit, or the last live ledger before a reload),
     * shown while the live ledger has nothing yet, exactly as the chat world shows the last known rows. The rows and
     * the turn facts are read from the same messages, so a reopened conversation's first frame already knows who ended
     * a step and which files an edit changed. The live ledger replaces it at its first message or history page.
     */
    private var known: LedgerSnapshot? = null

    private fun shown(): LedgerSnapshot = if (live.messages.isNotEmpty()) live else known ?: live

    /** The live ledger's messages, oldest first (what a kept page records). */
    fun liveMessages(): List<MessageWithParts> = live.messages

    /**
     * Messages to save in [ChatLastKnown]: empty until live messages or history arrived (never re-saves or overwrites
     * when nothing live was seen). When a turn ends before the first live history read succeeded, any older kept
     * messages not yet in [live] stay ahead of it so earlier turns are not lost.
     */
    fun messagesToKeep(historyReconciled: Boolean): List<MessageWithParts> {
        val current = live.messages
        if (current.isEmpty()) return emptyList()
        if (historyReconciled) return current
        val prior = known?.messages?.takeIf { it.isNotEmpty() } ?: return current
        val liveIds = current.mapTo(HashSet()) { it.info.id }
        return prior.filterNot { it.info.id in liveIds } + current
    }

    /**
     * [keepShown]: the messages shown now stay until the new live ledger has its own (a reload or another graph);
     * false forgets them too (the conversation is gone).
     */
    fun reset(keepShown: Boolean = true) {
        known = if (!keepShown) null else live.takeIf { it.messages.isNotEmpty() } ?: known
        ledger = TurnLedger(sessionId)
        live = LedgerSnapshot.Empty
        timing = ReplyTimingState()
        state.value = TurnFeed(ledger = shown(), windows = state.value.windows)
    }

    /** Cache-first: [messages] (a kept page, oldest first) stand in until the live ledger has messages. */
    fun showKept(messages: List<MessageWithParts>) {
        if (live.messages.isNotEmpty() || messages.isEmpty()) return
        known = TurnLedger(sessionId).also { it.load(messages) }.snapshot()
        state.value = state.value.copy(ledger = shown())
    }

    fun link(link: ChatLink) {
        if (state.value.link != link) state.value = state.value.copy(link = link)
    }

    /** The fence for a history read that starts now (B-CHAT-112/117): pass it back to [history] with the page. */
    fun historyVersion(): Long = ledger.historyVersion()

    /**
     * [newest]: the conversation's newest page. When it brings a newer turn or reply than the server's idle was said
     * for, that idle no longer holds (B8) until [idle] says it again; the same page read again keeps it (no flicker).
     */
    fun history(page: List<MessageWithParts>, beforeVersion: Long = Long.MAX_VALUE, newest: Boolean = false) {
        val before = live.newestMark()
        ledger.load(page, beforeVersion)
        val s = state.value
        val snapshot = ledger.snapshot()
        live = snapshot
        // The server answered: its page is the truth from here on, even when it is empty.
        known = null
        state.value = s.copy(ledger = snapshot, idleSaid = s.idleSaid && !(newest && snapshot.newestMark() != before))
    }

    /** B8: the server's answer to "does it still run?" after the newest page: idle, or running (null: no answer). */
    fun idle(said: Boolean = true) {
        val s = state.value
        val idle = said && !s.busy
        if (s.idleSaid != idle) state.value = s.copy(idleSaid = idle)
    }

    /** A run is going now (the session status said busy when the chat opened; [atMs] null = start unknown). */
    fun running(atMs: Long?) {
        val s = state.value
        if (!s.busy)
            state.value =
                s.copy(
                    busy = true,
                    runStartedAtMs = atMs,
                    lastProgressAtMs = atMs,
                    lastOutcome = null,
                    idleSaid = false,
                )
    }

    /** The requests (permission or question) of this conversation that wait for the person now, and since when. */
    private val asks = HashSet<String>()
    private var waitingSinceMs: Long? = null

    /**
     * Issue 147: the turn's clock ("Working · 12 s") does not count the time the run spent waiting for the person's
     * decision. While a request waits nothing runs; when the last one is answered the run's start moves forward by the
     * time it waited, so the clock goes on from where it stopped. A run that ended starts its next clock fresh.
     */
    private fun waitClock(event: GatewayEvent, atMs: Long) {
        fun ask(id: String) {
            if (asks.add(id) && waitingSinceMs == null) waitingSinceMs = atMs
        }

        fun answered(id: String) {
            if (!asks.remove(id) || asks.isNotEmpty()) return
            val since = waitingSinceMs ?: return
            waitingSinceMs = null
            val s = state.value
            val started = s.runStartedAtMs ?: return
            state.value =
                s.copy(
                    runStartedAtMs = started + (atMs - since).coerceAtLeast(0),
                    waits = (s.waits + (since to atMs)).takeLast(64),
                )
        }
        when (event) {
            is GatewayEvent.PermissionAsked -> if (event.request.sessionId.raw == sessionId) ask("p:" + event.request.id.raw)
            is GatewayEvent.PermissionSettled -> if (event.sessionId.raw == sessionId) answered("p:" + event.requestId.raw)
            is GatewayEvent.QuestionAsked -> if (event.question.sessionId.raw == sessionId) ask("q:" + event.question.id.raw)
            is GatewayEvent.QuestionSettled -> if (event.sessionId.raw == sessionId) answered("q:" + event.requestId.raw)
            is GatewayEvent.RunEnded -> {
                asks.clear()
                waitingSinceMs = null
                if (state.value.waits.isNotEmpty()) state.value = state.value.copy(waits = emptyList())
            }
            else -> Unit
        }
    }

    fun event(event: GatewayEvent, atMs: Long) {
        time(event, atMs)
        waitClock(event, atMs)
        val s = state.value
        when (event) {
            is GatewayEvent.RunStarted ->
                if (event.sessionId.raw == sessionId)
                    state.value =
                        s.copy(
                            busy = true,
                            runStartedAtMs = s.runStartedAtMs.takeIf { s.busy } ?: atMs,
                            lastProgressAtMs = atMs,
                            // The retry block stays while the next attempt is in flight (B-CHAT-RETRY-001): it goes only
                            // when output arrives or the run ends, so the page does not change height between attempts.
                            lastOutcome = null,
                            idleSaid = false,
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
                            idleSaid = false,
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
                            idleSaid = true,
                        )
            else ->
                if (ledger.apply(event)) {
                    val snapshot = ledger.snapshot()
                    val before = live
                    live = snapshot
                    state.value =
                        s.copy(
                            ledger = shown(),
                            lastProgressAtMs = atMs,
                            // Real output means the retry worked; an empty reply opened for the next attempt does not.
                            retry = if (isOutput(event)) null else s.retry,
                            // B8: streamed words, a new turn or reply, a reply that opens or a step that runs mean the
                            // conversation is active again: not idle as said. A durable event replayed unchanged after a
                            // reconnect is not applied at all and keeps it.
                            idleSaid =
                                s.idleSaid &&
                                    !active(event) &&
                                    snapshot.newestMark() == before.newestMark(),
                        )
                } else if (event is GatewayEvent.PartDelta && event.sessionId.raw == sessionId) {
                    state.value = s.copy(lastProgressAtMs = atMs, retry = null, idleSaid = false)
                }
        }
    }

    /** Words or a step: what shows a retry worked (an empty reply, a status or a new attempt starting do not). */
    private fun isOutput(event: GatewayEvent): Boolean =
        when (event) {
            is GatewayEvent.PartDelta -> true
            is GatewayEvent.PartUpserted ->
                event.part.type == io.github.eslamasabry.oc.core.model.transcript.PartType.Tool ||
                    event.part.text.isNotBlank()
            else -> false
        }

    /** B8: an applied event that shows the conversation working (whatever the status said before it). */
    private fun active(event: GatewayEvent): Boolean =
        when (event) {
            is GatewayEvent.PartDelta -> true
            is GatewayEvent.MessageUpserted ->
                event.message.role == Role.Assistant &&
                    event.message.time?.completedMs == null &&
                    event.message.finish == null &&
                    event.message.errorText == null
            is GatewayEvent.PartUpserted ->
                event.part.tool?.status.let {
                    it == io.github.eslamasabry.oc.core.model.transcript.ToolStatus.Pending ||
                        it == io.github.eslamasabry.oc.core.model.transcript.ToolStatus.Running
                }
            else -> false
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
