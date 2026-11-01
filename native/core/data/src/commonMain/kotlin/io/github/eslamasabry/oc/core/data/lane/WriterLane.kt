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
import kotlin.concurrent.atomics.AtomicReference

/**
 * One ordered write lane (ADR §9.5, plan 03 §17.3) with the audit fix for the drain race
 * (`audit/codex-01+03` BLOCKER 1) and the admission-order fix (review w1-data-oc2 MAJOR 4).
 *
 * Admission and ordering are one atomic step: a write is admitted by a compare-and-set on the state that holds the
 * admission flag, the in-flight tickets AND the tail of the queue. The new ticket becomes the tail and remembers the
 * ticket admitted just before it; it runs only after that one has finished. So writes run in admission order, whatever
 * happens between admission and running (a writer paused after admission cannot be overtaken by a later one, which a
 * mutex alone allowed: mutex fairness orders mutex waiters, not admissions). `stopAdmission()` flips the flag on that
 * state, so after it returns no new ticket can be added, and `drain()` awaits every ticket admitted before.
 *
 * Cancellation: a writer cancelled before running never runs its block; its ticket is completed only when the ticket
 * before it has completed (without suspending the cancelled writer), so the chain never lets a later write start
 * early. A writer cancelled inside its block is awaited until the block returns or throws; blocks that must finish a
 * platform commit use `withContext(NonCancellable)` (plan 03 §17.5 rule 2).
 */
public class WriterLane(public val name: String) {
    private class State(
        val admitting: Boolean,
        val inFlight: PersistentSet<CompletableDeferred<Unit>>,
        val tail: CompletableDeferred<Unit>?,
    )

    private val state = AtomicReference(State(admitting = true, inFlight = persistentHashSetOf(), tail = null))

    /** Test seam: runs after a write is admitted and before it waits for its turn (the race window of the audits). */
    internal var afterAdmission: (suspend () -> Unit)? = null

    public val isAdmitting: Boolean get() = state.load().admitting

    /**
     * Runs [block] after every write admitted before it, in admission order. Refused with
     * [RefusalReason.ProfileClosing] once [stopAdmission] was called.
     */
    public suspend fun <T> write(block: suspend () -> Outcome<T>): Outcome<T> {
        val ticket = CompletableDeferred<Unit>()
        val previous = admit(ticket) ?: return Outcome.Failed(Failure.Refused(RefusalReason.ProfileClosing))
        var ran = false
        try {
            afterAdmission?.invoke()
            previous.value?.await()
            ran = true
            return block()
        } finally {
            val before = previous.value
            if (ran || before == null || before.isCompleted) {
                release(ticket)
            } else {
                // Cancelled while waiting: hand the turn on only when the write before this one is done.
                before.invokeOnCompletion { release(ticket) }
            }
        }
    }

    /** Synchronous: the first step of a profile close or delete. Writes asked for after this returns are refused. */
    public fun stopAdmission() {
        while (true) {
            val s = state.load()
            if (!s.admitting) return
            if (state.compareAndSet(s, State(admitting = false, inFlight = s.inFlight, tail = s.tail))) return
        }
    }

    /** Opens the lane again after a failed deletion (plan 05 §3.5 step 8: keep the profile, reopen everything). */
    public fun resumeAdmission() {
        while (true) {
            val s = state.load()
            if (s.admitting) return
            if (state.compareAndSet(s, State(admitting = true, inFlight = s.inFlight, tail = s.tail))) return
        }
    }

    /**
     * Returns when every write admitted before this call has finished (run, failed, or cancelled before running).
     * After [stopAdmission] that is every write the lane will ever run until [resumeAdmission].
     */
    public suspend fun drain() {
        for (ticket in state.load().inFlight) ticket.await()
    }

    /** The ticket admitted before [ticket] (boxed: it may be null), or null when the lane refused it. */
    private fun admit(ticket: CompletableDeferred<Unit>): Box? {
        while (true) {
            val s = state.load()
            if (!s.admitting) return null
            val next = State(admitting = true, inFlight = s.inFlight.adding(ticket), tail = ticket)
            if (state.compareAndSet(s, next)) return Box(s.tail)
        }
    }

    private class Box(val value: CompletableDeferred<Unit>?)

    private fun release(ticket: CompletableDeferred<Unit>) {
        while (true) {
            val s = state.load()
            val next = State(admitting = s.admitting, inFlight = s.inFlight.removing(ticket), tail = s.tail)
            if (state.compareAndSet(s, next)) break
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
