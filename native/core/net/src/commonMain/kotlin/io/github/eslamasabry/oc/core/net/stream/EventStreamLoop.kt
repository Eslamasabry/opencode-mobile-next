package io.github.eslamasabry.oc.core.net.stream

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.pace.Visibility
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.net.gate.NetworkGate
import io.github.eslamasabry.oc.core.net.sse.SseFrame
import io.github.eslamasabry.oc.core.net.sse.SseParser
import io.github.eslamasabry.oc.core.security.HostClass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random
import kotlin.time.Clock

/**
 * One event stream with reconnect (plan 04 §6.2; port of Flutter `Api2EventStream`).
 *
 * Timing (all under the injected [clock] and the caller's dispatcher, so tests run it in virtual time):
 * - a failed or dropped try waits [BackoffPolicy.delayMs] (OpenCode 2: 1, 2, 4, 8, then every 16 s, N4-D6);
 *   30 s connected resets the count; from the 6th failure the status says Disconnected but tries go on;
 * - no byte for [BackoffPolicy.stallTimeoutMs] (45 s) is a drop (N4-D7, B-PROTO-132);
 * - a non-loopback host with no network waits in [StreamStatus.WaitingForNetwork] without spending tries and connects
 *   at once when the network returns, with the count reset; a new default network does the same, also for a live
 *   stream (N4-D8, B-PROTO-133); loopback is never gated;
 * - every wait comes from [BackoffPolicy.delayMs] with the current [visibility] (plan 13 amendment A2): out of sight
 *   while something watches (HiddenWatching) tries go 1 s, 2 s, then every 60–120 s jittered (PB-51); a network flap
 *   on the same default network never shortens that wait, a new default network does (N4-D8); a change to Foreground
 *   fires a waiting try at once, counted as attempt 1 (a failure then waits 1 s);
 * - HiddenIdle (nothing watching) dials nothing and arms no timer (PB-40, PB-53): a live stream is closed, a pending
 *   wait is cancelled, the status is [StreamStatus.Suspended], and the loop only waits for the visibility to change;
 *   then it dials at once with the count reset. A [retryNow] made while suspended is answered by that dial;
 * - a failed try's failure is published in [lastFailure] (401 → `SignIn(PasswordRejected)`, so the graph can set
 *   `passwordRejected`) and cleared by the next `Connected`. An OpenCode 2 401/403 keeps the normal backoff (plan 04
 *   §4.8); only a connector result with `stop = true` (a backend whose sign-in failure is terminal) parks the loop in
 *   [StreamStatus.Stopped] until [retryNow]. A [retryNow] made while that refused request was in flight is kept, so a
 *   password saved meanwhile is tried at once (review w1-core-net MAJOR 8);
 * - [reconnectAllowed] false (N4-D21, plan 04 §6.2): a live stream stays up; after a drop or a failed try no try is
 *   scheduled, the status is `Disconnected(attempt, nextAttemptAtMs = null)`, a network change dials nothing, and only
 *   [retryNow] (one attempt) or the flag turning true dials again (review w1-core-net MAJOR 7).
 *
 * Every connect after the first is announced as [StreamStatus.Connected] with a higher generation **before** any of
 * its frames, so the owner refetches (reconcile by refetch, never replay; N4-D10, ADR §9.5).
 *
 * Memory (audit codex-04 MAJOR 10): there is no frame queue. The flow is rendezvous-buffered and frames are handed
 * over with a suspending send after each read, so a slow collector stops the reader (and TCP pushes back). At most one
 * read buffer (16 KiB) of frames plus one 16 MiB frame are held.
 *
 * A body that ends cleanly flushes its trailing frame; a body that fails or stalls discards the partial frame.
 * The resume cursor ([ResumeMode.LastEventId]) moves only after the collector has taken the frame: a frame whose
 * hand-over was cancelled (network change, [retryNow]) is asked for again on the next try (review w1-core-net MAJOR 9).
 * One collector at a time.
 */
public class EventStreamLoop(
    private val connector: SseConnector,
    private val policy: BackoffPolicy,
    private val hostClass: HostClass,
    private val gate: NetworkGate,
    private val clock: Clock,
    private val random: Random,
    /** Plan 13 §11.1, bound from `LinkDemand` at graph level; picks the hidden pace (A2). */
    private val visibility: StateFlow<Visibility> = MutableStateFlow(Visibility.Foreground),
    private val resume: ResumeMode = ResumeMode.None,
    private val readBufferBytes: Int = 16 * 1024,
    /** N4-D21, from `GatewaySpec.reconnectAllowed`: whether the loop may dial again on its own. */
    private val reconnectAllowed: StateFlow<Boolean> = MutableStateFlow(true),
) {
    private val retry = Channel<Unit>(Channel.CONFLATED)

    private val mutableLastFailure = MutableStateFlow<Failure?>(null)

    /** Why the last try failed (401 → `SignIn`); null once a try connects (plan 04 §6.2). */
    public val lastFailure: StateFlow<Failure?> = mutableLastFailure

    /** Try now with the count reset: after a password update, a manual "Try again", or a network change. */
    public fun retryNow() {
        retry.trySend(Unit)
    }

    /** Cold: collecting runs the loop; cancelling the collector cancels the request and stops it. */
    public fun signals(): Flow<StreamSignal> = channelFlow { run() }.buffer(Channel.RENDEZVOUS)

    private enum class End { Clean, Failed, Stalled, RetryNow, NetworkChanged, Idle }

    private enum class Wake { Timer, RetryNow, NetworkChanged, Visible, Idle, Disallowed }

    private enum class Resume { RetryNow, Allowed }

    private val gated: Boolean get() = hostClass != HostClass.Loopback

    private fun now(): Long = clock.now().toEpochMilliseconds()

    private suspend fun ProducerScope<StreamSignal>.run() {
        var attempt = 0
        var generation = 0
        var nextAt: Long? = null
        var lastEventId: String? = null
        while (true) {
            if (visibility.value == Visibility.HiddenIdle) {
                send(StreamSignal.Status(StreamStatus.Suspended))
                visibility.first { it != Visibility.HiddenIdle } // no timer armed while suspended (PB-40)
                attempt = 0
                nextAt = null
                continue
            }
            if (!gate.isUsable(hostClass)) {
                send(StreamSignal.Status(StreamStatus.WaitingForNetwork))
                awaitUsableOrIdle()
                attempt = 0
                nextAt = null
                continue
            }
            retry.tryReceive() // a request made before this try is answered by it (it reads the newest credentials)
            send(StreamSignal.Status(inFlight(attempt, nextAt)))
            val networkAtStart = gate.currentNetworkId()
            var connectedAt: Long? = null
            var end: End? = null
            val result = connector.connect(lastEventId.takeIf { resume == ResumeMode.LastEventId }) { body ->
                generation += 1
                val gen = generation
                connectedAt = now()
                nextAt = null
                mutableLastFailure.value = null
                send(StreamSignal.Status(StreamStatus.Connected))
                end = readUntilEnd(body, gen, networkAtStart) { id -> lastEventId = id }
            }
            if (result is SseConnectResult.Failed) mutableLastFailure.value = result.failure
            if (result is SseConnectResult.Failed && result.stop) {
                send(StreamSignal.Status(StreamStatus.Stopped(result.failure)))
                // Not drained first: a retryNow sent while the refused request was in flight (a password saved
                // meanwhile) dials at once. Older requests were consumed when this try started.
                retry.receive()
                attempt = 0
                nextAt = null
                continue
            }
            if (end == End.RetryNow || end == End.NetworkChanged || end == End.Idle) {
                attempt = 0
                nextAt = null
                continue
            }
            val upSince = connectedAt
            if (upSince != null && now() - upSince >= policy.resetAfterStableMs) attempt = 0
            attempt += 1
            val seen = visibility.value
            if (seen == Visibility.HiddenIdle) continue // Suspended at the top of the loop
            if (!reconnectAllowed.value) {
                attempt = waitWhileDisallowed(attempt)
                nextAt = null
                continue
            }
            val wait = policy.delayMs(attempt, random, seen) ?: continue
            val at = now() + wait
            nextAt = at // known before the status goes out
            send(StreamSignal.Status(waiting(attempt, at)))
            when (waitBeforeRetry(wait, seen, gate.currentNetworkId())) {
                Wake.Timer -> Unit
                Wake.RetryNow, Wake.NetworkChanged, Wake.Visible, Wake.Idle -> {
                    attempt = 0
                    nextAt = null
                }
                Wake.Disallowed -> {
                    attempt = waitWhileDisallowed(attempt)
                    nextAt = null
                }
            }
        }
    }

    /** N4-D21: no scheduled try. Returns the attempt count for the next try: one by [retryNow], or all when allowed. */
    private suspend fun ProducerScope<StreamSignal>.waitWhileDisallowed(attempt: Int): Int {
        send(StreamSignal.Status(StreamStatus.Disconnected(attempt, null)))
        val resume = coroutineScope {
            val wakes = listOf(
                async { retry.receive(); Resume.RetryNow },
                async { reconnectAllowed.first { it }; Resume.Allowed },
            )
            val first = select { wakes.forEach { d -> d.onAwait { it } } }
            wakes.forEach { it.cancel() }
            first
        }
        return when (resume) {
            Resume.RetryNow -> attempt // one try; if it fails, the loop comes back here
            Resume.Allowed -> 0
        }
    }

    /** Offline: waits for a usable network, or for HiddenIdle (then the loop reports Suspended instead). */
    private suspend fun awaitUsableOrIdle() {
        coroutineScope {
            val wakes = listOf(
                async { gate.awaitUsable(hostClass) },
                async { visibility.first { it == Visibility.HiddenIdle } },
            )
            select { wakes.forEach { d -> d.onAwait { } } }
            wakes.forEach { it.cancel() }
        }
    }

    /** A new default network interrupts a live stream only while reconnecting is allowed (plan 04 §6.4). */
    private suspend fun awaitAllowedNetworkChange(from: Long?) {
        var since = from
        while (true) {
            gate.awaitNetworkChangeFrom(since)
            if (reconnectAllowed.value) return
            since = gate.currentNetworkId()
        }
    }

    private fun inFlight(attempt: Int, nextAt: Long?): StreamStatus =
        if (attempt == 0) StreamStatus.Connecting else waiting(attempt, nextAt)

    private fun waiting(attempt: Int, nextAt: Long?): StreamStatus =
        if (attempt >= policy.disconnectedLabelAfter) {
            StreamStatus.Disconnected(attempt, nextAt)
        } else {
            StreamStatus.Reconnecting(attempt, nextAt)
        }

    /** Reads the body until it ends, fails, stalls, or is interrupted by [retryNow] or a new default network. */
    private suspend fun ProducerScope<StreamSignal>.readUntilEnd(
        body: SseBody,
        generation: Int,
        networkAtStart: Long?,
        onId: (String) -> Unit,
    ): End = coroutineScope {
        val reader = async { pump(body, generation, onId) }
        val interrupts = buildList {
            add(async { retry.receive(); End.RetryNow })
            add(async { visibility.first { it == Visibility.HiddenIdle }; End.Idle }) // PB-53: 0 open streams
            if (gated) add(async { awaitAllowedNetworkChange(networkAtStart); End.NetworkChanged })
        }
        val all = interrupts + reader
        val end = select { all.forEach { d -> d.onAwait { it } } }
        all.forEach { it.cancel() }
        end
    }

    private suspend fun ProducerScope<StreamSignal>.pump(body: SseBody, generation: Int, onId: (String) -> Unit): End {
        val buffer = ByteArray(readBufferBytes)
        val pending = ArrayList<SseFrame>()
        val parser = SseParser { pending += it }
        while (true) {
            val read: Int? =
                try {
                    withTimeoutOrNull(policy.stallTimeoutMs) { body.read(buffer) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    parser.discardPartial() // a frame cut mid-write is discarded
                    return End.Failed
                }
            if (read == null) {
                parser.discardPartial()
                return End.Stalled
            }
            if (read < 0) {
                parser.flush()
                deliver(pending, generation, onId)
                return End.Clean
            }
            parser.feed(buffer, 0, read)
            deliver(pending, generation, onId)
        }
    }

    private suspend fun ProducerScope<StreamSignal>.deliver(
        pending: ArrayList<SseFrame>,
        generation: Int,
        onId: (String) -> Unit,
    ) {
        if (pending.isEmpty()) return
        val frames = pending.toList()
        pending.clear()
        for (frame in frames) {
            send(StreamSignal.Frame(frame, generation))
            frame.id?.let(onId) // only after the hand-over: a cancelled send leaves the cursor on the last frame taken
        }
    }

    /** The one retry wait (A2's `@RetryLoop`): it waits only for a value from [BackoffPolicy.delayMs]. */
    private suspend fun waitBeforeRetry(waitMs: Long, seen: Visibility, networkId: Long?): Wake = coroutineScope {
        val wakes: List<Deferred<Wake>> = buildList {
            add(async { delay(waitMs); Wake.Timer })
            add(async { retry.receive(); Wake.RetryNow })
            if (seen != Visibility.Foreground) {
                add(async { visibility.first { it == Visibility.Foreground }; Wake.Visible })
            }
            add(async { visibility.first { it == Visibility.HiddenIdle }; Wake.Idle })
            if (gated) add(async { gate.awaitNetworkChangeFrom(networkId); Wake.NetworkChanged })
            add(async { reconnectAllowed.first { !it }; Wake.Disallowed })
        }
        val wake = select { wakes.forEach { d -> d.onAwait { it } } }
        wakes.forEach { it.cancel() }
        wake
    }
}
