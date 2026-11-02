package io.github.eslamasabry.oc.engine.transcript.turnfacts

// Port of lib/domain/turn_stall.dart (B-CHAT-118, B-CHAT-119). Clock-free: every call that reads time takes
// `nowMs` (a monotonic reading the owner supplies). Dart's async bounded probe is modelled as a ticket: the owner
// runs the probe, then resolves the ticket with [TurnStallTracker.completeProbe], or with
// [TurnStallTracker.expireProbe] once the budget has passed. A ticket is single-use, so a late result is discarded.

/** A 5-second tick starts a bounded probe after 45 seconds of silence. */
public const val TURN_STALL_SILENCE_MS: Long = 45_000L
public const val TURN_STALL_TICK_MS: Long = 5_000L
public const val TURN_STALL_PROBE_BUDGET_MS: Long = 10_000L

/** How often an open turn re-reads the waiting permissions and questions, in case the stream lost an ask. */
public const val TURN_STALL_REREAD_EVERY_MS: Long = 15_000L

/** Dart TurnStallKind: modelSlow, helperDown, network. */
public enum class TurnStallKind { ModelSlow, HelperStopped, ConnectionLost }

/** What the stall notice says; the feature maps each to words (Dart TurnStallDiagnosis.message). */
public enum class TurnStallNotice {
    ModelSlow,

    /** Helper down, no exit record that proves an unexpected stop. */
    HelperStopped,
    HelperStoppedUnexpectedly,

    /** Android may have stopped it to free memory or limit background processes (not proof of cause). */
    HelperStoppedResourceKill,

    /** The endpoint is unreachable or the transport is down. */
    ConnectionLost,

    /** Connected, but reachability could not be established. */
    ConnectionUnchecked,
}

/** Narrow native helper observations (Dart AgentHelperStatus). */
public data class TurnStallHelperFacts(
    val running: Boolean? = null,
    val lastExitAtMs: Long? = null,
    val lastStopRequested: Boolean? = null,
    val possibleResourceKill: Boolean = false,
) {
    public val unexpectedStop: Boolean
        get() = running == false && lastExitAtMs != null && lastStopRequested == false
}

/** Safe observations only. Null means the probe could not establish that fact. */
public data class TurnStallEvidence(
    val transportConnected: Boolean,
    val helperRunning: Boolean? = null,
    val helperStatus: TurnStallHelperFacts? = null,
    val endpointReachable: Boolean? = null,
) {
    public companion object {
        /** What a probe that threw, timed out or was cancelled yields: nothing established. */
        public fun unknown(transportConnected: Boolean): TurnStallEvidence =
            TurnStallEvidence(transportConnected = transportConnected)
    }
}

/**
 * An unavailable helper is direct evidence. A healthy reachable endpoint makes a slow model an inference. Every
 * other case is an interrupted or unconfirmed connection.
 */
public fun classifyTurnStall(evidence: TurnStallEvidence): TurnStallKind {
    if (evidence.helperRunning == false) return TurnStallKind.HelperStopped
    if (evidence.transportConnected && evidence.endpointReachable == true) return TurnStallKind.ModelSlow
    return TurnStallKind.ConnectionLost
}

/** Dart boundedTurnStallProbe: a probe that failed (null result) yields only safe unknown evidence. */
public fun boundedTurnStallEvidence(result: TurnStallEvidence?, transportConnected: Boolean): TurnStallEvidence =
    result ?: TurnStallEvidence.unknown(transportConnected)

public data class TurnStallDiagnosis(val kind: TurnStallKind, val evidence: TurnStallEvidence, val silentForMs: Long) {
    public val notice: TurnStallNotice
        get() =
            when (kind) {
                TurnStallKind.ModelSlow -> TurnStallNotice.ModelSlow
                TurnStallKind.HelperStopped -> {
                    val status = evidence.helperStatus
                    when {
                        status == null || !status.unexpectedStop -> TurnStallNotice.HelperStopped
                        status.possibleResourceKill -> TurnStallNotice.HelperStoppedResourceKill
                        else -> TurnStallNotice.HelperStoppedUnexpectedly
                    }
                }
                TurnStallKind.ConnectionLost ->
                    if (evidence.endpointReachable == false || !evidence.transportConnected) {
                        TurnStallNotice.ConnectionLost
                    } else {
                        TurnStallNotice.ConnectionUnchecked
                    }
            }
}

/** A probe ticket, valid only for the active turn and the exact silence it checked. */
public class TurnStallProbe internal constructor(
    public val sessionId: String,
    public val startedAtMs: Long,
    internal val revision: Int,
)

/**
 * Timer-free, protocol-neutral turn liveness. The connection owner feeds target-session progress and schedules the
 * tick and probes. Global heartbeats and events from other conversations must not call [noteProgress].
 */
public class TurnStallTracker(
    public val silenceThresholdMs: Long = TURN_STALL_SILENCE_MS,
    public val maximumSessions: Int = 100,
) {
    init {
        require(silenceThresholdMs > 0)
        require(maximumSessions > 0)
    }

    private class SilentTurn(var lastProgressMs: Long, var revision: Int) {
        var waiting = false
        var toolRunning = false
        var probing = false
        var diagnosis: TurnStallDiagnosis? = null
    }

    private val turns = LinkedHashMap<String, SilentTurn>()
    private var revision = 0

    /** Start only at a new prompt/busy transition; an already-active turn keeps its original silence clock. */
    public fun begin(sessionId: String, nowMs: Long) {
        if (sessionId.isEmpty() || turns.containsKey(sessionId)) return
        if (turns.size >= maximumSessions) turns.remove(turns.keys.first())
        turns[sessionId] = SilentTurn(nowMs, ++revision)
    }

    public fun noteProgress(sessionId: String, nowMs: Long) {
        val turn = turns[sessionId] ?: return
        turn.lastProgressMs = nowMs
        turn.revision = ++revision
        turn.probing = false
        turn.diagnosis = null
    }

    /** A permission, question, card or form waiting for a person is not a stall; resuming restarts silence. */
    public fun setWaiting(sessionId: String, waiting: Boolean, nowMs: Long) {
        val turn = turns[sessionId] ?: return
        if (turn.waiting == waiting) return
        turn.waiting = waiting
        noteProgress(sessionId, nowMs)
    }

    /** A pending or running tool call is the agent working; when the last one finishes silence restarts. */
    public fun setToolRunning(sessionId: String, running: Boolean, nowMs: Long) {
        val turn = turns[sessionId] ?: return
        if (turn.toolRunning == running) return
        turn.toolRunning = running
        noteProgress(sessionId, nowMs)
    }

    public fun finish(sessionId: String) {
        turns.remove(sessionId)
    }

    /** Recheck changed helper/transport facts without inventing turn progress. */
    public fun invalidateEvidence(sessionId: String) {
        val turn = turns[sessionId] ?: return
        turn.revision = ++revision
        turn.probing = false
        turn.diagnosis = null
    }

    public fun clear() {
        turns.clear()
    }

    public fun diagnosisFor(sessionId: String): TurnStallDiagnosis? = turns[sessionId]?.diagnosis

    /** A fixed snapshot, oldest turn first. */
    public fun dueSessionIds(nowMs: Long): List<String> = turns.entries.filter { due(it.value, nowMs) }.map { it.key }

    private fun due(turn: SilentTurn, nowMs: Long): Boolean =
        !turn.waiting && !turn.toolRunning && !turn.probing && turn.diagnosis == null &&
            nowMs - turn.lastProgressMs >= silenceThresholdMs

    public fun takeProbe(sessionId: String, nowMs: Long): TurnStallProbe? {
        val turn = turns[sessionId] ?: return null
        if (!due(turn, nowMs)) return null
        turn.probing = true
        return TurnStallProbe(sessionId, nowMs, turn.revision)
    }

    /**
     * Null means progress, a user wait, stop, reconnect, a new turn or an earlier resolution of the same ticket
     * overtook the probe. A classification never finishes, resends, or cancels a turn.
     */
    public fun completeProbe(probe: TurnStallProbe, evidence: TurnStallEvidence, nowMs: Long): TurnStallDiagnosis? {
        val turn = turns[probe.sessionId] ?: return null
        if (turn.revision != probe.revision || !turn.probing || turn.waiting || turn.toolRunning) return null
        turn.probing = false
        val diagnosis = TurnStallDiagnosis(classifyTurnStall(evidence), evidence, nowMs - turn.lastProgressMs)
        turn.diagnosis = diagnosis
        return diagnosis
    }

    /**
     * The probe budget ran out before a result: resolves the ticket with safe unknown evidence. Null before the
     * budget is spent, or when the ticket is no longer current.
     */
    public fun expireProbe(
        probe: TurnStallProbe,
        transportConnected: Boolean,
        nowMs: Long,
        budgetMs: Long = TURN_STALL_PROBE_BUDGET_MS,
    ): TurnStallDiagnosis? {
        if (nowMs - probe.startedAtMs < budgetMs) return null
        return completeProbe(probe, TurnStallEvidence.unknown(transportConnected), nowMs)
    }
}
