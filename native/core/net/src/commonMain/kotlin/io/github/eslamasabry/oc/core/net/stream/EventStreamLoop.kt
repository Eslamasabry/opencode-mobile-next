package io.github.eslamasabry.oc.core.net.stream

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
 * - out of sight ([hidden]) the policy's hidden pace applies (PB-51); coming back fires a waiting try at once;
 * - 401/403 stop the loop ([StreamStatus.Stopped]) until [retryNow].
 *
 * Every connect after the first is announced as [StreamStatus.Connected] with a higher generation **before** any of
 * its frames, so the owner refetches (reconcile by refetch, never replay; N4-D10, ADR §9.5).
 *
 * Memory (audit codex-04 MAJOR 10): there is no frame queue. The flow is rendezvous-buffered and frames are handed
 * over with a suspending send after each read, so a slow collector stops the reader (and TCP pushes back). At most one
 * read buffer (16 KiB) of frames plus one 16 MiB frame are held.
 *
 * A body that ends cleanly flushes its trailing frame; a body that fails or stalls discards the partial frame.
 * One collector at a time.
 */
public class EventStreamLoop(
    private val connector: SseConnector,
    private val policy: BackoffPolicy,
    private val hostClass: HostClass,
    private val gate: NetworkGate,
    private val clock: Clock,
    private val random: Random,
    private val hidden: StateFlow<Boolean> = MutableStateFlow(false),
    private val resume: ResumeMode = ResumeMode.None,
    private val readBufferBytes: Int = 16 * 1024,
) {
    private val retry = Channel<Unit>(Channel.CONFLATED)

    /** Try now with the count reset: after a password update, a manual "Try again", or a network change. */
    public fun retryNow() {
        retry.trySend(Unit)
    }

    /** Cold: collecting runs the loop; cancelling the collector cancels the request and stops it. */
    public fun signals(): Flow<StreamSignal> = channelFlow { run() }.buffer(Channel.RENDEZVOUS)

    private enum class End { Clean, Failed, Stalled, RetryNow, NetworkChanged }

    private enum class Wake { Timer, RetryNow, NetworkChanged, Visible }

    private val gated: Boolean get() = hostClass != HostClass.Loopback

    private fun now(): Long = clock.now().toEpochMilliseconds()

    private suspend fun ProducerScope<StreamSignal>.run() {
        var attempt = 0
        var generation = 0
        var nextAt: Long? = null
        var lastEventId: String? = null
        while (true) {
            if (!gate.isUsable(hostClass)) {
                send(StreamSignal.Status(StreamStatus.WaitingForNetwork))
                gate.awaitUsable(hostClass)
                attempt = 0
                nextAt = null
            }
            send(StreamSignal.Status(inFlight(attempt, nextAt)))
            val networkAtStart = gate.currentNetworkId()
            var connectedAt: Long? = null
            var end: End? = null
            val result = connector.connect(lastEventId.takeIf { resume == ResumeMode.LastEventId }) { body ->
                generation += 1
                val gen = generation
                connectedAt = now()
                nextAt = null
                send(StreamSignal.Status(StreamStatus.Connected))
                end = readUntilEnd(body, gen, networkAtStart) { id -> lastEventId = id }
            }
            if (result is SseConnectResult.Failed && result.stop) {
                retry.tryReceive() // conflated: drops a stale request so it cannot undo the stop
                send(StreamSignal.Status(StreamStatus.Stopped(result.failure)))
                retry.receive()
                attempt = 0
                nextAt = null
                continue
            }
            if (end == End.RetryNow || end == End.NetworkChanged) {
                attempt = 0
                nextAt = null
                continue
            }
            val upSince = connectedAt
            if (upSince != null && now() - upSince >= policy.resetAfterStableMs) attempt = 0
            attempt += 1
            val wasHidden = hidden.value
            val wait = policy.delayMs(attempt, wasHidden, random)
            val at = now() + wait
            nextAt = at // known before the status goes out
            send(StreamSignal.Status(waiting(attempt, at)))
            when (waitBeforeRetry(wait, wasHidden, gate.currentNetworkId())) {
                Wake.Timer -> Unit
                Wake.Visible -> nextAt = now()
                Wake.RetryNow, Wake.NetworkChanged -> {
                    attempt = 0
                    nextAt = null
                }
            }
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
            if (gated) add(async { gate.awaitNetworkChangeFrom(networkAtStart); End.NetworkChanged })
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
            frame.id?.let(onId)
            send(StreamSignal.Frame(frame, generation))
        }
    }

    private suspend fun waitBeforeRetry(waitMs: Long, wasHidden: Boolean, networkId: Long?): Wake = coroutineScope {
        val wakes: List<Deferred<Wake>> = buildList {
            add(async { delay(waitMs); Wake.Timer })
            add(async { retry.receive(); Wake.RetryNow })
            if (wasHidden) add(async { hidden.first { !it }; Wake.Visible })
            if (gated) add(async { gate.awaitNetworkChangeFrom(networkId); Wake.NetworkChanged })
        }
        val wake = select { wakes.forEach { d -> d.onAwait { it } } }
        wakes.forEach { it.cancel() }
        wake
    }
}
