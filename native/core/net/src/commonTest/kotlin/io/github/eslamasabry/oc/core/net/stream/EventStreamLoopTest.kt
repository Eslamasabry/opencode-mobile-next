package io.github.eslamasabry.oc.core.net.stream

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.net.gate.NetworkGate
import io.github.eslamasabry.oc.core.model.NetworkState.Online
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.security.HostClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The loop's timing table under virtual time (plan 04 §6.2, N4-D6, N4-D7, N4-D8; plan 13 PB-51, PB-52).
 * Every number here is a Flutter number (`sse2.dart`, `reconnect_backoff.dart`) or a plan row.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventStreamLoopTest {
    private class Harness(
        val scope: TestScope,
        val connector: FakeConnector,
        val network: FakeNetworkStatus,
        val loop: EventStreamLoop,
        val hidden: MutableStateFlow<Boolean>,
    ) {
        val signals = mutableListOf<Pair<Long, StreamSignal>>()
        val statuses get() = signals.mapNotNull { (at, s) -> (s as? StreamSignal.Status)?.let { at to it.status } }
        val frames get() = signals.mapNotNull { (_, s) -> (s as? StreamSignal.Frame)?.let { it.frame.data } }

        fun start() {
            scope.backgroundScope.launch { loop.signals().collect { signals += scope.testScheduler.currentTime to it } }
            scope.runCurrent()
        }
    }

    private fun TestScope.harness(
        host: HostClass = HostClass.Public,
        policy: BackoffPolicy = BackoffPolicy.OpenCode2,
        online: Online = Online.Yes,
        resume: ResumeMode = ResumeMode.None,
        scripts: List<Script> = emptyList(),
    ): Harness {
        val connector = FakeConnector(this, scripts)
        val network = FakeNetworkStatus(online)
        val hidden = MutableStateFlow(false)
        val loop = EventStreamLoop(
            connector = connector,
            policy = policy,
            hostClass = host,
            gate = NetworkGate(network),
            clock = VirtualClock(this),
            random = Random(7),
            hidden = hidden,
            resume = resume,
        )
        return Harness(this, connector, network, loop, hidden)
    }

    private fun frame(data: String) = "data: $data\n\n"

    @Test
    fun `N4-D6 backoff 1 2 4 8 then every 16 s, label disconnected from the 6th failure`() = runTest {
        val h = harness()
        h.start()
        advanceTimeBy(100_000)
        assertEquals(listOf(0L, 1_000, 3_000, 7_000, 15_000, 31_000, 47_000, 63_000, 79_000, 95_000), h.connector.opens)
        val waits = h.statuses.mapNotNull { (at, s) ->
            when (s) {
                is StreamStatus.Reconnecting -> Triple(at, s.attempt, s.nextAttemptAtMs) to "reconnecting"
                is StreamStatus.Disconnected -> Triple(at, s.attempt, s.nextAttemptAtMs) to "disconnected"
                else -> null
            }
        }
        // The schedule is known when the status goes out (nextAttemptAt set before publishing).
        val scheduled = waits.filter { it.first.third != null && it.first.third!! > it.first.first }
        assertEquals(
            listOf(1 to 1_000L, 2 to 3_000L, 3 to 7_000L, 4 to 15_000L, 5 to 31_000L, 6 to 47_000L),
            scheduled.take(6).map { it.first.second to it.first.third!! },
        )
        assertEquals(listOf("reconnecting", "reconnecting", "reconnecting", "reconnecting", "reconnecting", "disconnected"),
            scheduled.take(6).map { it.second })
        assertEquals(StreamStatus.Connecting, h.statuses.first().second)
    }

    @Test
    fun `N4-D6 a connection stable for 30 s resets the backoff`() = runTest {
        val h = harness(
            scripts = listOf(
                Script.Fail(), Script.Fail(), Script.Fail(), // 0, 1 s, 3 s → next wait 8 s
                Script.Body(listOf(0L to frame("a"), 31_000L to frame("b")), Script.End.Error), // up at 7 s, drops at 38 s
                Script.Body(listOf(0L to frame("c"), 5_000L to frame("d")), Script.End.Error), // up at 39 s, drops at 44 s
            ),
        )
        h.start()
        advanceTimeBy(60_000)
        // After 31 s up, the drop waits 1 s (attempt reset); after 5 s up it waits 2 s.
        assertEquals(listOf(0L, 1_000, 3_000, 7_000, 39_000, 46_000), h.connector.opens.take(6))
    }

    @Test
    fun `N4-D10 refetch after reconnect, a new generation is announced before its first frame`() = runTest {
        val h = harness(
            scripts = listOf(
                Script.Body(listOf(0L to frame("one")), Script.End.Error),
                Script.Body(listOf(10L to frame("two")), Script.End.Hang),
            ),
        )
        val refetchedBefore = mutableListOf<String>()
        var generation = 0
        backgroundScope.launch {
            h.loop.signals().collect { signal ->
                when (signal) {
                    is StreamSignal.Status -> if (signal.status == StreamStatus.Connected) {
                        // The generation is the count of Connected signals (plan 04 §6.2).
                        generation += 1
                        if (generation > 1) refetchedBefore += "refetch@$generation"
                    }
                    is StreamSignal.Frame -> {
                        assertEquals(generation, signal.generation)
                        refetchedBefore += signal.frame.data
                    }
                }
            }
        }
        advanceTimeBy(5_000)
        assertEquals(listOf("one", "refetch@2", "two"), refetchedBefore)
    }

    @Test
    fun `B-PROTO-033 a frame cut by a dropped body is discarded and a clean end flushes the tail`() = runTest {
        val h = harness(
            scripts = listOf(
                Script.Body(listOf(0L to frame("whole") + "data: {\"cut\""), Script.End.Error),
                Script.Body(listOf(0L to frame("after") + "data: tail"), Script.End.Clean),
            ),
        )
        h.start()
        advanceTimeBy(1_500)
        assertEquals(listOf("whole", "after", "tail"), h.frames)
    }

    @Test
    fun `B-PROTO-132 no byte for 45 s counts as dropped and reconnects`() = runTest {
        val h = harness(scripts = listOf(Script.Body(listOf(0L to frame("hello")), Script.End.Hang)))
        h.start()
        advanceTimeBy(44_999)
        assertEquals(listOf(0L), h.connector.opens)
        advanceTimeBy(1_002) // stall at 45 s, then the 1 s first retry
        assertEquals(listOf(0L, 46_000), h.connector.opens)
    }

    @Test
    fun `B-PROTO-132 heartbeat comments count as activity`() = runTest {
        val beats = (1..40).map { 15_000L to ": heartbeat\n\n" }
        val h = harness(scripts = listOf(Script.Body(listOf(0L to frame("hello")) + beats, Script.End.Hang)))
        h.start()
        advanceTimeBy(600_000)
        assertEquals(listOf(0L), h.connector.opens)
        assertTrue(h.statuses.last().second is StreamStatus.Connected)
    }

    @Test
    fun `B-PROTO-133 offline remote stream waits without dialing and connects at once when the network returns`() =
        runTest {
            val h = harness(online = Online.No, scripts = listOf(Script.Body(listOf(0L to frame("x")), Script.End.Hang)))
            h.start()
            advanceTimeBy(600_000)
            assertEquals(emptyList(), h.connector.opens) // PB-52: zero attempts while offline
            assertEquals(StreamStatus.WaitingForNetwork, h.statuses.last().second)
            h.network.set(Online.Yes, id = 2)
            runCurrent()
            assertEquals(listOf(600_000L), h.connector.opens)
            assertTrue(h.statuses.last().second is StreamStatus.Connected)
        }

    @Test
    fun `B-PROTO-133 losing the network during backoff spends no attempts, and its return resets the backoff`() =
        runTest {
            val h = harness()
            h.start()
            advanceTimeBy(3_500) // failures at 0, 1 s, 3 s; next try planned at 7 s
            h.network.set(Online.No, id = null)
            advanceTimeBy(300_000)
            assertEquals(listOf(0L, 1_000, 3_000), h.connector.opens)
            assertEquals(StreamStatus.WaitingForNetwork, h.statuses.last().second)
            h.network.set(Online.Yes, id = 3)
            runCurrent()
            advanceTimeBy(1_500)
            // Back at once (303.5 s), then the reset schedule: first wait is 1 s again.
            assertEquals(listOf(0L, 1_000, 3_000, 303_500, 304_500), h.connector.opens)
        }

    @Test
    fun `B-PROTO-133 the phone's own server never waits for the network`() = runTest {
        val h = harness(host = HostClass.Loopback, online = Online.No)
        h.start()
        advanceTimeBy(3_500)
        assertEquals(listOf(0L, 1_000, 3_000), h.connector.opens)
        assertTrue(h.statuses.none { it.second == StreamStatus.WaitingForNetwork })
    }

    @Test
    fun `B-PROTO-133 a new default network reconnects a live remote stream at once`() = runTest {
        val h = harness(
            scripts = listOf(
                Script.Body(listOf(0L to frame("a")), Script.End.Hang),
                Script.Body(listOf(0L to frame("b")), Script.End.Hang),
            ),
        )
        h.start()
        advanceTimeBy(10_000)
        h.network.set(Online.Yes, id = 9)
        runCurrent()
        assertEquals(listOf(0L, 10_000), h.connector.opens)
        assertEquals(listOf("a", "b"), h.frames)
    }

    @Test
    fun `N4-D6 401 stops the stream until retryNow`() = runTest {
        val h = harness(
            scripts = listOf(Script.Fail(Failure.SignIn(SignInWhy.PasswordRejected), stop = true), Script.Body(emptyList(), Script.End.Hang)),
        )
        h.start()
        advanceTimeBy(600_000)
        assertEquals(listOf(0L), h.connector.opens)
        assertTrue(h.statuses.last().second is StreamStatus.Stopped)
        h.loop.retryNow()
        runCurrent()
        assertEquals(listOf(0L, 600_000), h.connector.opens)
    }

    @Test
    fun `N4-D6 retryNow cancels the wait and resets the backoff`() = runTest {
        val h = harness()
        h.start()
        advanceTimeBy(5_000) // fails at 0, 1, 3 s; waiting for 7 s
        h.loop.retryNow()
        runCurrent()
        advanceTimeBy(1_001) // advanceTimeBy excludes its end instant
        assertEquals(listOf(0L, 1_000, 3_000, 5_000, 6_000), h.connector.opens)
    }

    @Test
    fun `PB-51 hidden retries 1 s 2 s then 60-120 s`() = runTest {
        val h = harness()
        h.hidden.value = true
        h.start()
        advanceTimeBy(1_000_000)
        val gaps = h.connector.opens.zipWithNext { a, b -> b - a }
        assertEquals(listOf(1_000L, 2_000L), gaps.take(2))
        assertTrue(gaps.drop(2).isNotEmpty())
        assertTrue(gaps.drop(2).all { it in 60_000L..120_000L }, "$gaps")
    }

    @Test
    fun `PB-51 return to foreground retries at once`() = runTest {
        val h = harness()
        h.hidden.value = true
        h.start()
        advanceTimeBy(10_000) // 0, 1 s, 3 s, then a 60–120 s wait
        assertEquals(3, h.connector.opens.size)
        h.hidden.value = false
        runCurrent()
        assertEquals(listOf(0L, 1_000, 3_000, 10_000), h.connector.opens)
    }

    @Test
    fun `N4-D6 last event id resume passes the last seen id`() = runTest {
        val h = harness(
            resume = ResumeMode.LastEventId,
            scripts = listOf(Script.Body(listOf(0L to "id: 41\ndata: a\n\nid: 42\ndata: b\n\n"), Script.End.Error)),
        )
        h.start()
        advanceTimeBy(1_500)
        assertEquals(listOf(null, "42"), h.connector.lastEventIds)
    }
}
