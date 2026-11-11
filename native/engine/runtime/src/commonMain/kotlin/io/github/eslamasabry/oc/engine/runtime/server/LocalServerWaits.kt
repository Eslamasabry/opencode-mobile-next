package io.github.eslamasabry.oc.engine.runtime.server

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

/**
 * Connection attempts to this phone's own server wait for it OUTSIDE the connection runtime's command lock.
 *
 * The lock (PreviewRuntime.command) serialises the changes to the runtime's connection state: the saved active server,
 * the live connections, the list's sources. Waiting for the phone's server to answer ([LocalServerGate], up to its
 * booting window plus one restart and its wait, about 150 s) changes none of that, and held inside the lock it made a
 * switch to another server, an explicit Try again, a Stop and the healing's check all wait behind it while the home
 * could only say "Connecting". Here the wait runs first, then the attempt takes the lock for its state changes.
 *
 * - One wait per profile: attempts for the same server share it, so the server is never restarted twice for them.
 * - A newer command supersedes a waiting attempt, which then ends at once (null from [run]'s `stale`) and changes
 *   nothing: a newer attempt for the same server (an explicit Try again, a newer connect; a background attempt never
 *   supersedes one that makes the server the active one), or [cancel] (Disconnect, the person's Stop, removal). A
 *   cancel also ends the shared wait.
 * - An attempt that would make its server the active one, overtaken by a later choice of another server, may still
 *   connect it in the background ([Turn.overtaken]); it never takes the active server back.
 * - [waiting] counts the attempts from their start until they let go of the lock, so the runtime's busy flag stays true
 *   while the phone's server boots ("Starting", then "Connecting") and drops when the last one ends or is superseded.
 */
public class LocalServerWaits(
    private val scope: CoroutineScope,
    private val gate: suspend (ProfileId) -> LocalServerWait,
    private val onWaitingChanged: () -> Unit = {},
) {
    /** What an attempt may do once it holds the lock. */
    public class Turn(
        /** How the wait for the phone's server ended; null when the attempt did not wait (another server). */
        public val local: LocalServerWait?,
        /** The attempt may make its server the active one. */
        public val activate: Boolean,
        /** It was meant to, but the person chose another server meanwhile. */
        public val overtaken: Boolean,
    )

    private class Attempt(val id: ProfileId, val ticket: Long, val makeActive: Boolean)

    private data class Tickets(
        val last: Long = 0,
        val newestFor: Map<ProfileId, Long> = emptyMap(),
        val newestActiveFor: Map<ProfileId, Long> = emptyMap(),
        val newestActive: Long = 0,
        val cancelledAt: Map<ProfileId, Long> = emptyMap(),
        val waiting: Int = 0,
    )

    private class Shared(val result: CompletableDeferred<LocalServerWait?>, val job: Job)

    private val tickets = MutableStateFlow(Tickets())
    private val shared = MutableStateFlow<Map<ProfileId, Shared>>(emptyMap())

    /** Attempts that waited, or wait, for the phone's server and have not yet let go of the lock. */
    public val waiting: Int
        get() = tickets.value.waiting

    /**
     * One connection attempt. [waits] is true for this phone's own server (the caller's rule): the attempt waits for
     * it first, outside [locked], and is superseded as described on the class. Otherwise it goes straight to [locked],
     * as every command did before, and only records that it was issued (a later choice of server).
     */
    public suspend fun <T> run(
        id: ProfileId,
        makeActive: Boolean,
        waits: Boolean,
        locked: suspend (suspend () -> T) -> T,
        stale: () -> T,
        connect: suspend (Turn) -> T,
    ): T {
        val attempt = begin(id, makeActive)
        if (!waits) return locked { connect(Turn(null, makeActive, overtaken = false)) }
        count(+1)
        try {
            val local = await(attempt) ?: return stale()
            return locked {
                if (superseded(tickets.value, attempt)) {
                    stale()
                } else {
                    val overtaken = makeActive && tickets.value.newestActive > attempt.ticket
                    connect(Turn(local, makeActive && !overtaken, overtaken))
                }
            }
        } finally {
            count(-1)
        }
    }

    /** Disconnect, the person's Stop, removal: waiting attempts for [ids] end, and so does the wait for their server. */
    public fun cancel(ids: Collection<ProfileId>) {
        if (ids.isEmpty()) return
        tickets.update { t ->
            val at = t.last + 1
            t.copy(last = at, cancelledAt = t.cancelledAt + ids.associateWith { at })
        }
        val ended = shared.updateAndGetOld { it - ids.toSet() }.filterKeys { it in ids }.values
        for (wait in ended) wait.job.cancel()
    }

    private fun begin(id: ProfileId, makeActive: Boolean): Attempt {
        val t =
            tickets.updateAndGet { t ->
                val at = t.last + 1
                t.copy(
                    last = at,
                    newestFor = t.newestFor + (id to at),
                    newestActiveFor = if (makeActive) t.newestActiveFor + (id to at) else t.newestActiveFor,
                    newestActive = if (makeActive) at else t.newestActive,
                )
            }
        return Attempt(id, t.last, makeActive)
    }

    private fun superseded(t: Tickets, a: Attempt): Boolean =
        (t.cancelledAt[a.id] ?: 0) > a.ticket ||
            ((if (a.makeActive) t.newestActiveFor[a.id] else t.newestFor[a.id]) ?: 0) > a.ticket

    private fun count(delta: Int) {
        tickets.update { it.copy(waiting = it.waiting + delta) }
        onWaitingChanged()
    }

    /** The shared wait's result, or null when the attempt was superseded (or the wait cancelled) first. */
    private suspend fun await(attempt: Attempt): LocalServerWait? {
        if (superseded(tickets.value, attempt)) return null
        val wait = sharedWait(attempt.id)
        return coroutineScope {
            val overtaken = async {
                tickets.first { superseded(it, attempt) }
                null
            }
            try {
                select<LocalServerWait?> {
                    overtaken.onAwait { it }
                    wait.result.onAwait { it }
                }
            } finally {
                overtaken.cancel()
            }
        }
    }

    private fun sharedWait(id: ProfileId): Shared {
        while (true) {
            val current = shared.value
            current[id]?.takeIf { it.result.isActive }?.let { return it }
            val result = CompletableDeferred<LocalServerWait?>()
            val job = scope.launch(start = CoroutineStart.LAZY) { result.complete(gate(id)) }
            val next = Shared(result, job)
            if (shared.compareAndSet(current, current + (id to next))) {
                job.invokeOnCompletion {
                    // Cancelled (or broken): the waiters end without a result and change nothing.
                    result.complete(null)
                    shared.update { if (it[id] === next) it - id else it }
                }
                job.start()
                return next
            }
            job.cancel()
        }
    }

    private inline fun <V> MutableStateFlow<V>.updateAndGetOld(transform: (V) -> V): V {
        while (true) {
            val old = value
            if (compareAndSet(old, transform(old))) return old
        }
    }
}
