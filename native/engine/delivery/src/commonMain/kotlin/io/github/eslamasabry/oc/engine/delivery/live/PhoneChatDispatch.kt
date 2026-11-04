package io.github.eslamasabry.oc.engine.delivery.live

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.SessionStatus

/**
 * The admission fence on the phone's own server (Dart `Api2Gateway.beforeSessionDispatch` / `sessionDispatchSettled`,
 * row B-PROTO-029). Every chat send to a session — a prompt, a shell line or a command — first waits for [admit]; a
 * refused admission sends nothing and settles nothing. An admitted dispatch settles exactly once, whatever the send
 * answered: a settled request is not proof of idle (a transport failure may follow acceptance).
 */
public class PhoneChatDispatch(
    private val admit: suspend (SessionId) -> Outcome<Unit>,
    private val settled: (SessionId) -> Unit,
) {
    public suspend fun <T> dispatch(session: SessionId, send: suspend () -> Outcome<T>): Outcome<T> {
        when (val admission = admit(session)) {
            is Outcome.Failed -> return admission
            is Outcome.Ok -> Unit
        }
        try {
            return send()
        } finally {
            settled(session)
        }
    }
}

/**
 * Which phone chats may still be working (Dart `PhoneChatDispatchTracker`). A turn stays tracked while a dispatch is
 * pending, and after it settles until a status read newer than the settlement saw it busy and then idle in the same
 * folder. An uncertain send is therefore kept, never replayed, until the server shows it ran and finished.
 */
public class PhoneChatDispatchTracker {
    private data class Turn(val pending: Int, val settledEpoch: Int, val directory: String?, val started: Boolean, val retained: Boolean)

    public var epoch: Int = 0
        private set

    private val turns = LinkedHashMap<SessionId, Turn>()

    public val sessionIds: Set<SessionId> get() = turns.keys.toSet()
    public val directories: List<String> get() = turns.values.mapNotNull { it.directory }
    public val isNotEmpty: Boolean get() = turns.isNotEmpty()

    public operator fun contains(id: SessionId): Boolean = id in turns

    public fun begin(id: SessionId, directory: String?) {
        val previous = turns[id]
        turns[id] = Turn((previous?.pending ?: 0) + 1, ++epoch, directory, started = false, retained = previous != null)
    }

    public fun settled(id: SessionId, removeUnsent: Boolean = false) {
        val turn = turns[id] ?: return
        val pending = turn.pending - 1
        if (removeUnsent && pending == 0 && !turn.retained) {
            turns.remove(id)
        } else {
            // An overlapping request can observe the earlier turn still busy: require a new busy read after this batch.
            turns[id] = turn.copy(pending = pending, settledEpoch = ++epoch, started = turn.started && !turn.retained)
        }
    }

    /** Applies one status read taken at [readEpoch] for [directory]. Absent from the map reads as idle. */
    public fun reconcile(statuses: Map<SessionId, SessionStatus>, readEpoch: Int, directory: String?) {
        for ((id, status) in statuses) {
            val turn = turns[id] ?: continue
            if (turn.pending == 0 && readEpoch >= turn.settledEpoch && turn.directory == directory &&
                (status == SessionStatus.Busy || status == SessionStatus.Retry)
            ) {
                turns[id] = turn.copy(started = true)
            }
        }
        turns.entries.removeAll { (id, turn) ->
            turn.pending == 0 && turn.started && readEpoch >= turn.settledEpoch && turn.directory == directory &&
                (statuses[id] == null || statuses[id] == SessionStatus.Idle)
        }
    }

    public fun reset() {
        turns.clear()
        epoch++
    }
}
