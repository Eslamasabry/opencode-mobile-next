package io.github.eslamasabry.oc.core.data.lane

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicReference

/**
 * One ordered write lane (ADR §9.5, plan 03 §17.3) with the audit fix for the drain race
 * (`audit/codex-01+03` BLOCKER 1).
 *
 * The plan's version checked an admission flag and THEN queued on the mutex, so a writer that passed the check but had
 * not reached the lock was invisible to `drain()`: drain could return, the sweep could run, and the writer would then
 * restore deleted data. Here admission and registration are one atomic step: a write is admitted only by adding its
 * ticket to the in-flight set with a compare-and-set on the same state that holds the admission flag. `stopAdmission()`
 * flips the flag on that state, so after it returns no new ticket can be added, and `drain()` awaits every ticket that
 * was admitted before, including writers still waiting for the mutex or paused before reaching it.
 *
 * Cancellation: a writer cancelled while waiting for the mutex never runs its block; its ticket is completed in
 * `finally`, so drain does not wait for it. A writer cancelled inside its block is awaited until the block returns or
 * throws; blocks that must finish a platform commit use `withContext(NonCancellable)` (plan 03 §17.5 rule 2).
 *
 * Writes run in FIFO order (`Mutex` is fair), like Dart's chained futures.
 */
public class WriterLane(public val name: String) {
    private class State(val admitting: Boolean, val inFlight: PersistentSet<CompletableDeferred<Unit>>)

    private val state = AtomicReference(State(admitting = true, inFlight = persistentHashSetOf()))
    private val mutex = Mutex()

    /** Test seam: runs after a write is admitted and before it asks for the mutex (the race window of the audit). */
    internal var afterAdmission: (suspend () -> Unit)? = null

    public val isAdmitting: Boolean get() = state.load().admitting

    /**
     * Runs [block] after every write admitted before it. Refused with [RefusalReason.ProfileClosing] once
     * [stopAdmission] was called.
     */
    public suspend fun <T> write(block: suspend () -> Outcome<T>): Outcome<T> {
        val ticket = CompletableDeferred<Unit>()
        if (!admit(ticket)) return Outcome.Failed(Failure.Refused(RefusalReason.ProfileClosing))
        try {
            afterAdmission?.invoke()
            return mutex.withLock { block() }
        } finally {
            release(ticket)
        }
    }

    /** Synchronous: the first step of a profile close or delete. Writes asked for after this returns are refused. */
    public fun stopAdmission() {
        while (true) {
            val s = state.load()
            if (!s.admitting) return
            if (state.compareAndSet(s, State(admitting = false, inFlight = s.inFlight))) return
        }
    }

    /** Opens the lane again after a failed deletion (plan 05 §3.5 step 8: keep the profile, reopen everything). */
    public fun resumeAdmission() {
        while (true) {
            val s = state.load()
            if (s.admitting) return
            if (state.compareAndSet(s, State(admitting = true, inFlight = s.inFlight))) return
        }
    }

    /**
     * Returns when every write admitted before this call has finished (run, failed, or cancelled before running).
     * After [stopAdmission] that is every write the lane will ever run until [resumeAdmission].
     */
    public suspend fun drain() {
        for (ticket in state.load().inFlight) ticket.await()
    }

    private fun admit(ticket: CompletableDeferred<Unit>): Boolean {
        while (true) {
            val s = state.load()
            if (!s.admitting) return false
            if (state.compareAndSet(s, State(admitting = true, inFlight = s.inFlight.adding(ticket)))) return true
        }
    }

    private fun release(ticket: CompletableDeferred<Unit>) {
        while (true) {
            val s = state.load()
            if (state.compareAndSet(s, State(admitting = s.admitting, inFlight = s.inFlight.removing(ticket)))) break
        }
        ticket.complete(Unit)
    }

    override fun toString(): String = "WriterLane($name)"
}

/** The app's lanes: one device-wide lane and one lane per profile (plan 03 §10.5). */
public interface WriterLanes {
    /** Device-wide preferences. */
    public val app: WriterLane

    /** Created on first use; closed by profile close/delete. A closed lane stays closed (late writes are fenced). */
    public fun forProfile(profile: ProfileId): WriterLane

    /** Plan 05 §3.5 step 1: close admission synchronously for the profile's lane. Drain it with [drainProfile]. */
    public fun closeProfile(profile: ProfileId): WriterLane = forProfile(profile).also { it.stopAdmission() }

    public suspend fun drainProfile(profile: ProfileId) {
        forProfile(profile).drain()
    }
}

public class DefaultWriterLanes : WriterLanes {
    override val app: WriterLane = WriterLane("app")
    private val lanes = AtomicReference<PersistentMap<String, WriterLane>>(persistentHashMapOf())

    override fun forProfile(profile: ProfileId): WriterLane {
        while (true) {
            val current = lanes.load()
            current[profile.raw]?.let { return it }
            val lane = WriterLane("profile:${profile.raw}")
            if (lanes.compareAndSet(current, current.putting(profile.raw, lane))) return lane
        }
    }
}
