package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.engine.transcript.ledger.LedgerTurn
import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnWork
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
import io.github.eslamasabry.oc.engine.transcript.ledger.turnWork
import io.github.eslamasabry.oc.engine.transcript.turnfacts.BackgroundTask
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ChangedFile
import io.github.eslamasabry.oc.engine.transcript.turnfacts.CommandFact
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ContextLevel
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ContextSheetFacts
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ReplyTiming
import io.github.eslamasabry.oc.engine.transcript.turnfacts.RetryBannerFacts
import io.github.eslamasabry.oc.engine.transcript.turnfacts.RunNote
import io.github.eslamasabry.oc.engine.transcript.turnfacts.RunOutcomeKind
import io.github.eslamasabry.oc.engine.transcript.turnfacts.TurnStallEvidence
import io.github.eslamasabry.oc.engine.transcript.turnfacts.TurnStallNotice
import io.github.eslamasabry.oc.engine.transcript.turnfacts.TurnStallTracker
import io.github.eslamasabry.oc.engine.transcript.turnfacts.contextLevel
import io.github.eslamasabry.oc.engine.transcript.turnfacts.contextPercent
import io.github.eslamasabry.oc.engine.transcript.turnfacts.contextReading
import io.github.eslamasabry.oc.engine.transcript.turnfacts.contextSheetFacts
import io.github.eslamasabry.oc.engine.transcript.turnfacts.editedFileStats
import io.github.eslamasabry.oc.engine.transcript.turnfacts.isCompactionLineLive
import io.github.eslamasabry.oc.engine.transcript.turnfacts.retryBannerFacts
import io.github.eslamasabry.oc.engine.transcript.turnfacts.runResult
import io.github.eslamasabry.oc.engine.transcript.turnfacts.runningBackgroundTasks
import io.github.eslamasabry.oc.feature.chat.world.ChatLink
import io.github.eslamasabry.oc.feature.chat.world.TurnFeed
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What a finished turn did (B-CHAT-068/100/101): changed files with counts, commands with exit codes, and timing. */
public data class TurnResultUi(
    val outcome: RunOutcomeKind,
    val files: List<ChangedFile>,
    val commands: List<CommandFact>,
    val notes: Set<RunNote>,
    /** B-CHAT-092: how long the reply took in the app; null when it was not timed here. */
    val timing: ReplyTiming?,
)

/** The context ring and its sheet (B-CHAT-019/057/102). */
public data class ContextUi(val percent: Int, val level: ContextLevel, val sheet: ContextSheetFacts, val compacting: Boolean)

/** What the live line says instead of "Working…" (no new bar: it folds into the line that is already there). */
public sealed interface LiveNoticeUi {
    /** B-CHAT-016: the server retries; rate limit only when it is one, the attempt, a countdown. */
    public data class Retry(val facts: RetryBannerFacts) : LiveNoticeUi

    /** B-CHAT-118: the turn went silent and the app checked why. */
    public data class Stall(val notice: TurnStallNotice) : LiveNoticeUi

    /** B-CHAT-116: the conversation is being shortened right now. */
    public data object Compacting : LiveNoticeUi
}

/**
 * Everything the transcript shows beside the engine's rows, keyed by turn id (CHAT-TRANSCRIPT). Side tables only: a
 * change here never copies the transcript rows.
 */
public data class TurnFactsUi(
    val work: Map<String, TurnWork> = emptyMap(),
    val results: Map<String, TurnResultUi> = emptyMap(),
    val context: ContextUi? = null,
    val live: LiveNoticeUi? = null,
    /** B-CHAT-007/008: background commands still running, oldest first. */
    val background: List<BackgroundTask> = emptyList(),
    /** The clock these facts were read at (a running step's own time). */
    val nowMs: Long = 0,
) {
    public companion object {
        public val Empty: TurnFactsUi = TurnFactsUi()
    }
}

/**
 * Turns the [TurnFeed] into [TurnFactsUi] (CHAT-TRANSCRIPT). Pure except for [Clock] and the stall tracker it keeps:
 * [present] is called with the time; [states] ticks once a second only while something counts (a run, a retry
 * countdown, background work), so an idle conversation costs nothing.
 */
public class TurnFactsPresenter(private val clock: Clock) {
    private val stall = TurnStallTracker()
    private var lastProgress: Long? = null
    private var sessionKey = "turn"

    @OptIn(ExperimentalCoroutinesApi::class)
    public fun states(feed: Flow<TurnFeed>, waiting: Flow<Boolean>, scope: CoroutineScope, started: SharingStarted): StateFlow<TurnFactsUi> {
        val ticks =
            feed.map { ticking(it) }.distinctUntilChanged().flatMapLatest { on ->
                if (on) flow {
                    while (true) {
                        emit(clock.now().toEpochMilliseconds())
                        delay(TICK_MS)
                    }
                } else flowOf(clock.now().toEpochMilliseconds())
            }
        return combine(feed, waiting, ticks) { f, w, now -> present(f, now, w) }
            .stateIn(scope, started, TurnFactsUi.Empty)
    }

    private fun ticking(f: TurnFeed): Boolean = f.busy || f.retry != null

    /** The facts at [nowMs]. [waitingForYou]: a request card waits for the person (no stall, no spinner). */
    public fun present(feed: TurnFeed, nowMs: Long, waitingForYou: Boolean = false): TurnFactsUi {
        val snapshot = feed.ledger
        val stopped = feed.lastOutcome is RunOutcome.Interrupted
        val work = turnWork(snapshot, feed.busy, stopped = stopped, waitingForYou = waitingForYou)
        val results = HashMap<String, TurnResultUi>()
        for ((index, turn) in snapshot.turns.withIndex()) {
            val newest = index == snapshot.turns.lastIndex
            if (newest && feed.busy) continue
            result(turn, feed.timings[turn.turnId])?.let { results[turn.turnId] = it }
        }
        val compacting = feed.busy && snapshot.messages.lastOrNull()?.parts?.any(::isCompactionLineLive) == true
        return TurnFactsUi(
            work = work,
            results = results,
            context = context(feed, compacting),
            live = live(feed, nowMs, waitingForYou, work, compacting),
            background = runningBackgroundTasks(snapshot.messages, nowMs),
            nowMs = nowMs,
        )
    }

    private fun result(turn: LedgerTurn, timing: ReplyTiming?): TurnResultUi? {
        val run = runResult(turn.messages, historyComplete = turn.turnId != "head") ?: return null
        return TurnResultUi(
            outcome = run.outcome.kind,
            files = editedFileStats(turn.messages),
            commands = run.commands,
            notes = run.notes,
            timing = timing,
        )
    }

    private fun context(feed: TurnFeed, compacting: Boolean): ContextUi? {
        if (feed.windows.isEmpty()) return null
        val reading =
            contextReading(feed.ledger.messages) { provider, model -> feed.windows[TurnFeed.windowKey(provider, model)] }
                ?: return null
        val fraction = reading.fraction
        return ContextUi(
            percent = contextPercent(fraction),
            level = contextLevel(fraction),
            sheet = contextSheetFacts(feed.ledger.messages, reading.limit),
            compacting = compacting,
        )
    }

    private fun live(
        feed: TurnFeed,
        nowMs: Long,
        waiting: Boolean,
        work: Map<String, TurnWork>,
        compacting: Boolean,
    ): LiveNoticeUi? {
        if (!feed.busy) {
            stall.finish(sessionKey)
            lastProgress = null
            return null
        }
        feed.retry?.let { r -> return LiveNoticeUi.Retry(retryBannerFacts(r.attempt, r.nextAtMs, r.tag, nowMs)) }
        if (compacting) return LiveNoticeUi.Compacting
        // B-CHAT-118: silence per turn, reset by progress, suppressed while a request waits or a step runs.
        stall.begin(sessionKey, feed.runStartedAtMs ?: nowMs)
        val progress = feed.lastProgressAtMs
        if (progress != null && progress != lastProgress) {
            lastProgress = progress
            stall.noteProgress(sessionKey, progress)
        }
        stall.setWaiting(sessionKey, waiting, nowMs)
        val toolRunning = work.values.lastOrNull()?.steps?.any { it.state == StepState.Running } == true
        stall.setToolRunning(sessionKey, toolRunning, nowMs)
        stall.takeProbe(sessionKey, nowMs)?.let { probe ->
            // The probe: the conversation's own event stream. Live = the server answers (model slow); anything else
            // is a lost or unconfirmed connection. Helper facts belong to the built-in runtime and stay unknown here.
            val connected = feed.link == ChatLink.Live
            stall.completeProbe(probe, TurnStallEvidence(transportConnected = connected, endpointReachable = connected), nowMs)
        }
        return stall.diagnosisFor(sessionKey)?.let { LiveNoticeUi.Stall(it.notice) }
    }

    private companion object {
        const val TICK_MS = 1_000L
    }
}

/** Whether the work line of [work] opens by itself (Dart `KitWorkLine.opensByDefault`). */
internal fun TurnWork.opensByDefault(): Boolean = state == WorkLineState.EndedFailed
