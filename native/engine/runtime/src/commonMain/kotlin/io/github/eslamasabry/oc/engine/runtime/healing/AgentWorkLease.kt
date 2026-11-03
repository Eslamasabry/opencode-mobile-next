package io.github.eslamasabry.oc.engine.runtime.healing

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Port of lib/builtin/phone_agent_work_watch.dart: one bounded CPU lease for all local agent work owned by a profile.
 * Unknown work does not start a lease or end an already observed busy run; a change of owner or a known idle ends it.
 * Native monotonic lifetime and exact helper ownership stay authoritative. Native calls run one at a time, in order.
 * The Dart 5-minute renewal timer is [renew], which the shell calls on its own schedule.
 */
public class AgentWorkLease(
    private val host: HealingHost,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val newLeaseId: () -> String = ::randomLeaseId,
) {
    private class Run(val profileId: String, val id: String, val started: Instant) {
        var lastNow: Instant = started
        var closed = false
    }

    private var chain: Job? = null
    private var run: Run? = null
    private var disposed = false

    public fun observe(profileId: String?, busy: Boolean?) {
        if (disposed) return
        val owner = profileId?.takeIf { it.isNotEmpty() }
        if (run != null && run!!.profileId != owner) endRun()
        if (owner == null || busy == false) {
            endRun()
            return
        }
        val existing = run
        if (existing != null) {
            if (!existing.closed) remaining(existing)
            return
        }
        if (busy != true) return
        val started = clock.now()
        val next = Run(owner, newLeaseId(), started)
        run = next
        queueOn(next)
    }

    /** The periodic renewal (every 5 minutes while the run lasts). */
    public fun renew() {
        val current = run ?: return
        if (current(current) && remaining(current) != null) queueOn(current)
    }

    private fun remaining(run: Run): Duration? {
        val now = clock.now()
        if (now < run.lastNow || now - run.started >= CEILING) {
            close(run)
            enqueue { cpuClose(run) }
            return null
        }
        run.lastNow = now
        return CEILING - (now - run.started)
    }

    private fun retained(run: Run): Boolean = !disposed && this.run === run

    private fun current(run: Run): Boolean = retained(run) && !run.closed

    private fun enqueue(block: suspend () -> Unit) {
        val previous = chain
        chain = scope.launch {
            previous?.join()
            block()
        }
    }

    private fun queueOn(run: Run) =
        enqueue {
            if (!current(run)) return@enqueue
            val left = remaining(run) ?: return@enqueue
            val answer = host.setAgentWorkLease(run.profileId, run.id, on = true, hold = if (left < TTL) left else TTL)
            if (answer !is Outcome.Ok) {
                // A partial handoff may own CPU, but failure does not prove idle.
                if (retained(run)) close(run)
                cpuClose(run)
                return@enqueue
            }
            if (!retained(run)) {
                off(run)
                return@enqueue
            }
            if (run.closed || remaining(run) == null) {
                cpuClose(run)
                return@enqueue
            }
            if (answer.value.capped) {
                // Keep the exhausted logical name until actual idle or owner release.
                close(run)
            } else if (!answer.value.held) {
                close(run)
                cpuClose(run)
            }
        }

    private fun close(run: Run) {
        run.closed = true
    }

    /** Closes CPU only; keeps the native logical busy key. Cannot acquire a token or reset its lifetime. */
    private suspend fun cpuClose(run: Run) {
        if (!retained(run)) {
            off(run)
            return
        }
        host.setAgentWorkLease(run.profileId, run.id, on = true, hold = Duration.ZERO)
        if (!retained(run)) off(run)
    }

    private suspend fun off(run: Run) {
        host.setAgentWorkLease(run.profileId, run.id, on = false, hold = TTL)
    }

    private fun endRun() {
        val ended = run ?: return
        run = null
        close(ended)
        // No current-intent guard: a newer owner or run never erases an owed off.
        enqueue { off(ended) }
    }

    public fun dispose() {
        if (disposed) return
        disposed = true
        endRun()
    }

    public companion object {
        public val TTL: Duration = 15.minutes
        public val RENEW_EVERY: Duration = 5.minutes
        public val CEILING: Duration = 6.hours

        /** `agent.` plus 16 random bytes as hex. */
        public fun randomLeaseId(): String =
            "agent." + Random.nextBytes(16).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}
