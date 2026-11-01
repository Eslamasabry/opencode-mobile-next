package io.github.eslamasabry.oc.engine.delivery.live

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.delivery.LocalTurn
import io.github.eslamasabry.oc.engine.delivery.PromptDelivery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Clock

/** The phases of the live line (09 §4.1). */
public enum class LivePhase { Sending, WaitingServer, WaitingFirstWord, Thinking, Working, Writing, WaitingForYou, Retrying, Stuck }

/** A provider retry is a condition of the running turn, not a request (09 §4.1 rule 4). */
public data class RetryFacts(val attempt: Int, val nextAtMs: Long?, val rateLimit: Boolean)

/** A running command the stall tracker thinks is stuck (rule 5). [command] is shown as named; never logged. */
public data class StuckFacts(val command: String, val sinceMs: Long) {
    override fun toString(): String = "StuckFacts(sinceMs=$sinceMs)"
}

/**
 * What the transcript and the requests engine know about one conversation's turn, bound by the app graph from the
 * open `TranscriptHandle` (09 §4: the live line is for the open chat only). Plain facts, no words.
 */
public data class TurnFacts(
    val turnId: String? = null,
    /** `transcript.run.busy`. */
    val busy: Boolean = false,
    val busySinceMs: Long? = null,
    /** A permission, question or form waits on the person (rule 3). */
    val waitingRequest: Boolean = false,
    val retry: RetryFacts? = null,
    val stuck: StuckFacts? = null,
    /** `ToolPresentation.runningPhrase` of the step that runs (Working). */
    val runningStep: String? = null,
    /** The newest printed line of a running command, or a plan step. */
    val secondary: String? = null,
    val thinking: Boolean = false,
    val writing: Boolean = false,
    /** The last `session.idle` (rule 6: an idle without a prior busy clears the local guess). */
    val idleAtMs: Long? = null,
    /** A finished step that is not `tool-calls`, or has an error, with the conversation idle (rule 2). */
    val endedAtMs: Long? = null,
)

public data class LiveTurn(
    /** Null before the server has a turn (local guess). */
    val turnId: String?,
    val phase: LivePhase,
    val sinceMs: Long,
    /** More than 8 s in a starting phase ("starting slow"). */
    val slow: Boolean,
    val step: String?,
    val secondary: String?,
    /** Countdown only while [RetryFacts.nextAtMs] is in the future; a stuck "0:00" becomes plain "Retrying…". */
    val retry: RetryFacts?,
    val stuck: StuckFacts?,
)

/** The pure rules of the live line (map CHAT-GENUI-08, 23, 40, 50; `ConversationActivityTest`). */
public object LiveTurnRules {
    public const val SLOW_MS: Long = 8_000

    private val starting = setOf(LivePhase.Sending, LivePhase.WaitingServer, LivePhase.WaitingFirstWord)

    /** Rule 1 with rules 2 and 6: is the local guess still standing? */
    public fun localStands(facts: TurnFacts, local: LocalTurn?): Boolean {
        if (local == null) return false
        if (local.sending) return true
        val since = local.sinceMs
        if (!facts.busy && facts.idleAtMs != null && facts.idleAtMs >= since) return false
        if (!facts.busy && facts.endedAtMs != null && facts.endedAtMs >= since) return false
        return true
    }

    public fun derive(facts: TurnFacts, local: LocalTurn?, nowMs: Long): LiveTurn? {
        val localStands = localStands(facts, local)
        val serverEnded = !facts.busy && facts.endedAtMs != null && (local == null || facts.endedAtMs >= local.sinceMs) &&
            local?.sending != true
        val running = (facts.busy && !serverEnded) || localStands
        if (!running) return null
        val phase = when {
            facts.waitingRequest -> LivePhase.WaitingForYou // never a spinner, never "Thinking…"
            facts.retry != null -> LivePhase.Retrying
            facts.stuck != null -> LivePhase.Stuck
            local?.sending == true -> LivePhase.Sending
            !facts.busy -> LivePhase.WaitingServer
            facts.runningStep != null -> LivePhase.Working
            facts.writing -> LivePhase.Writing
            facts.thinking -> LivePhase.Thinking
            else -> LivePhase.WaitingFirstWord
        }
        val since = (if (localStands) local?.sinceMs else null) ?: facts.busySinceMs ?: local?.sinceMs ?: nowMs
        val retry = facts.retry?.let { r -> if (r.nextAtMs != null && r.nextAtMs <= nowMs) r.copy(nextAtMs = null) else r }
        return LiveTurn(
            turnId = facts.turnId,
            phase = phase,
            sinceMs = since,
            slow = phase in starting && nowMs - since > SLOW_MS,
            step = if (phase == LivePhase.Working) facts.runningStep else null,
            secondary = if (phase == LivePhase.Working) facts.secondary else null,
            retry = if (phase == LivePhase.Retrying) retry else null,
            stuck = if (phase == LivePhase.Stuck) facts.stuck else null,
        )
    }

    /** When the line needs a fresh look without new facts: a retry countdown (1 s) or the 8 s "slow" flip. */
    public fun nextTickMs(turn: LiveTurn?, facts: TurnFacts, nowMs: Long): Long? {
        if (turn == null) return null
        facts.retry?.nextAtMs?.let { if (turn.phase == LivePhase.Retrying && it > nowMs) return 1_000 }
        if (turn.phase in starting && !turn.slow) return (turn.sinceMs + SLOW_MS + 1 - nowMs).coerceAtLeast(1)
        return null
    }
}

/**
 * "What the agent is doing right now" for the open chat (09 §4.1). Notifications, widgets and the Conversations list
 * never read this; they read the handle-free per-session status in `:engine:connection`.
 */
public class ConversationActivity(
    private val delivery: PromptDelivery,
    private val facts: (ConversationRef) -> Flow<TurnFacts>,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val turns = HashMap<ConversationRef, StateFlow<LiveTurn?>>()

    public fun liveTurn(ref: ConversationRef): StateFlow<LiveTurn?> =
        turns.getOrPut(ref) {
            combine(facts(ref), delivery.localTurns.map { it[ref] }) { f, local -> f to local }
                .transformLatest { (f, local) ->
                    while (true) {
                        val now = clock.now().toEpochMilliseconds()
                        val turn = LiveTurnRules.derive(f, local, now)
                        emit(turn)
                        val wait = LiveTurnRules.nextTickMs(turn, f, now) ?: break
                        delay(wait) // only while a countdown runs or the slow flip is ahead; never while idle
                    }
                }
                .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)
        }
}
