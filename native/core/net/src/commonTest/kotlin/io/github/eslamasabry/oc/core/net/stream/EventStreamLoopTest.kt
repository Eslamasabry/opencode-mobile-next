package io.github.eslamasabry.oc.core.net.stream

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.common.pace.Visibility
import io.github.eslamasabry.oc.core.net.gate.NetworkGate
import io.github.eslamasabry.oc.core.model.NetworkState.Online
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.security.HostClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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
        val visibility: MutableStateFlow<Visibility>,
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
        reconnectAllowed: MutableStateFlow<Boolean> = MutableStateFlow(true),
    ): Harness {
        val connector = FakeConnector(this, scripts)
        val network = FakeNetworkStatus(online)
        val visibility = MutableStateFlow(Visibility.Foreground)
        val loop = EventStreamLoop(
            connector = connector,
            policy = policy,
            hostClass = host,
            gate = NetworkGate(network),
            clock = VirtualClock(this),
            random = Random(7),
            visibility = visibility,
            resume = resume,
            reconnectAllowed = reconnectAllowed,
        )
        return Harness(this, connector, network, loop, visibility)
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
    fun `N4-D6 a terminal sign-in failure (stop) parks the stream until retryNow`() = runTest {
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
        h.visibility.value = Visibility.HiddenWatching
        h.start()
        advanceTimeBy(3_600_000)
        val gaps = h.connector.opens.zipWithNext { a, b -> b - a }
        assertEquals(listOf(1_000L, 2_000L), gaps.take(2))
        assertTrue(gaps.drop(2).size >= 29, "$gaps") // an hour of 60–120 s waits
        assertTrue(gaps.drop(2).all { it in 60_000L..120_000L }, "$gaps")
    }

    @Test
    fun `PB-51 at most one try per 60 s after the second, also once a hidden stream drops`() = runTest {
        // Connected while hidden, then dropped: the same pace applies to the reconnects (attempt counted from the drop).
        val h = harness(scripts = listOf(Script.Body(listOf(0L to frame("a"), 10_000L to frame("b")), Script.End.Error)))
        h.visibility.value = Visibility.HiddenWatching
        h.start()
        advanceTimeBy(1_800_000)
        val afterDrop = h.connector.opens.drop(1)
        assertEquals(listOf(11_000L, 13_000L), afterDrop.take(2)) // dropped at 10 s: 1 s, then 2 s
        val tail = afterDrop.drop(1)
        // Every 60 s window after the second retry holds at most one attempt.
        tail.forEach { t -> assertTrue(tail.count { it in t until t + 60_000 } <= 1, "$tail") }
    }

    @Test
    fun `PB-51 return to foreground retries at once`() = runTest {
        val h = harness()
        h.visibility.value = Visibility.HiddenWatching
        h.start()
        advanceTimeBy(10_000) // 0, 1 s, 3 s, then a 60–120 s wait
        assertEquals(3, h.connector.opens.size)
        h.visibility.value = Visibility.Foreground
        runCurrent()
        assertEquals(listOf(0L, 1_000, 3_000, 10_000), h.connector.opens)
        // The try on return counts as attempt 1: if it fails, the foreground schedule starts over at 1 s.
        advanceTimeBy(1_001)
        assertEquals(listOf(0L, 1_000, 3_000, 10_000, 11_000), h.connector.opens)
    }

    @Test
    fun `PB-51 a hidden wait is not shortened by a flap on the same network, only by a new default network`() = runTest {
        val h = harness()
        h.visibility.value = Visibility.HiddenWatching
        h.start()
        advanceTimeBy(10_000) // waiting 60–120 s from 3 s
        h.network.set(Online.No)
        runCurrent()
        h.network.set(Online.Yes) // same default network id
        runCurrent()
        assertEquals(3, h.connector.opens.size)
        h.network.set(Online.Yes, id = 2) // Wi-Fi → mobile: a new network to dial on (N4-D8)
        runCurrent()
        assertEquals(listOf(0L, 1_000, 3_000, 10_000), h.connector.opens)
    }

    @Test
    fun `PB-52 no dial while offline, hidden or not`() = runTest {
        Visibility.entries.filter { it != Visibility.HiddenIdle }.forEach { visibility ->
            val h = harness(online = Online.No)
            h.visibility.value = visibility
            val t0 = testScheduler.currentTime
            h.start()
            advanceTimeBy(600_000) // 10 min of airplane mode (PR-04)
            assertEquals(emptyList(), h.connector.opens, "$visibility")
            assertEquals(StreamStatus.WaitingForNetwork, h.statuses.last().second)
            h.network.set(Online.Yes)
            runCurrent()
            assertEquals(listOf(t0 + 600_000L), h.connector.opens, "$visibility")
        }
    }

    @Test
    fun `PB-52 losing the network while hidden spends no tries`() = runTest {
        val h = harness()
        h.visibility.value = Visibility.HiddenWatching
        h.start()
        advanceTimeBy(10_000)
        h.network.set(Online.No)
        advanceTimeBy(600_000)
        assertEquals(listOf(0L, 1_000, 3_000), h.connector.opens)
    }

    @Test
    fun `PB-53 hidden idle opens no stream and arms no timer`() = runTest {
        val h = harness(scripts = listOf(Script.Body(listOf(0L to frame("a")), Script.End.Hang)))
        h.visibility.value = Visibility.HiddenIdle
        h.start()
        assertEquals(StreamStatus.Suspended, h.statuses.last().second)
        val before = testScheduler.currentTime
        advanceUntilIdle() // PB-40: with nothing scheduled, virtual time does not move (0 wake-ups)
        assertEquals(before, testScheduler.currentTime)
        advanceTimeBy(1_800_000)
        assertEquals(emptyList(), h.connector.opens)
        h.visibility.value = Visibility.Foreground
        runCurrent()
        assertEquals(listOf(1_800_000L), h.connector.opens)
        assertEquals(StreamStatus.Connected, h.statuses.last().second)
    }

    @Test
    fun `PB-53 going hidden idle closes a live stream and cancels a pending retry`() = runTest {
        val h = harness(scripts = listOf(Script.Body(listOf(0L to frame("a")), Script.End.Hang)))
        h.start()
        assertEquals(StreamStatus.Connected, h.statuses.last().second)
        h.visibility.value = Visibility.HiddenIdle
        runCurrent()
        assertEquals(StreamStatus.Suspended, h.statuses.last().second)
        assertEquals(0, h.connector.openSessions)
        advanceTimeBy(3_600_000)
        assertEquals(listOf(0L), h.connector.opens)

        // A pending retry (the failure path) is cancelled too, with no timer left behind.
        val g = harness()
        g.visibility.value = Visibility.HiddenWatching
        val t0 = testScheduler.currentTime
        g.start()
        advanceTimeBy(10_000)
        g.visibility.value = Visibility.HiddenIdle
        runCurrent()
        assertEquals(StreamStatus.Suspended, g.statuses.last().second)
        advanceTimeBy(3_600_000)
        assertEquals(listOf(t0, t0 + 1_000, t0 + 3_000), g.connector.opens)
    }

    @Test
    fun `PB-53 hidden idle to watching dials at once with the count reset`() = runTest {
        val h = harness()
        h.visibility.value = Visibility.HiddenIdle
        h.start()
        advanceTimeBy(60_000)
        h.visibility.value = Visibility.HiddenWatching // a reply watch starts
        runCurrent()
        advanceTimeBy(3_001)
        assertEquals(listOf(60_000L, 61_000, 63_000), h.connector.opens)
    }

    @Test
    fun `PB-53 a retryNow while hidden idle dials nothing until the person returns`() = runTest {
        val h = harness()
        h.visibility.value = Visibility.HiddenIdle
        h.start()
        h.loop.retryNow()
        advanceTimeBy(60_000)
        assertEquals(emptyList(), h.connector.opens)
        h.visibility.value = Visibility.Foreground
        runCurrent()
        assertEquals(listOf(60_000L), h.connector.opens) // the stale retryNow does not add a second dial
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

    @Test
    fun `N4-D6 a retryNow sent while a refused request was in flight is kept (review w1-core-net MAJOR 8)`() = runTest {
        // The person saves a new password while the request that is about to be refused is still out: the new
        // password must be tried at once, not swallowed by the stop.
        val h = harness(
            scripts = listOf(
                Script.Fail(Failure.SignIn(SignInWhy.PasswordRejected), stop = true, afterMs = 5_000),
                Script.Body(emptyList(), Script.End.Hang),
            ),
        )
        h.start()
        advanceTimeBy(2_000)
        h.loop.retryNow()
        advanceTimeBy(10_000)
        assertEquals(listOf(0L, 5_000L), h.connector.opens)
        assertEquals(StreamStatus.Connected, h.statuses.last().second)
    }

    @Test
    fun `N4-D6 OpenCode 2 sign-in failure keeps the backoff and is published as lastFailure`() = runTest {
        val rejected = Failure.SignIn(SignInWhy.PasswordRejected)
        val h = harness(scripts = listOf(Script.Fail(rejected), Script.Fail(rejected), Script.Body(emptyList(), Script.End.Hang)))
        h.start()
        assertEquals(rejected, h.loop.lastFailure.value)
        assertTrue(h.statuses.last().second is StreamStatus.Reconnecting)
        advanceTimeBy(3_500)
        assertEquals(listOf(0L, 1_000L, 3_000L), h.connector.opens)
        assertEquals(null, h.loop.lastFailure.value) // the next Connected clears it
        assertTrue(h.statuses.none { it.second is StreamStatus.Stopped })
    }

    @Test
    fun `B-PROTO-146 reconnect off - a drop schedules nothing and retryNow dials once`() = runTest {
        val h = harness(
            reconnectAllowed = MutableStateFlow(false),
            scripts = listOf(Script.Body(listOf(0L to frame("a")), Script.End.Error)),
        )
        h.start()
        advanceTimeBy(100_000)
        assertEquals(listOf(0L), h.connector.opens)
        assertEquals(StreamStatus.Disconnected(1, null), h.statuses.last().second)
        assertEquals(listOf("a"), h.frames) // data received before the drop is kept
        h.network.set(Online.Yes, id = 9) // a new network does not dial while reconnecting is off
        runCurrent()
        assertEquals(listOf(0L), h.connector.opens)
        h.loop.retryNow()
        runCurrent()
        advanceTimeBy(100_000)
        assertEquals(listOf(0L, 100_000L), h.connector.opens) // one try, then nothing scheduled again
        assertEquals(StreamStatus.Disconnected(2, null), h.statuses.last().second)
    }

    @Test
    fun `B-PROTO-146 turning reconnect off during a wait cancels the scheduled try, turning it on dials`() = runTest {
        val allowed = MutableStateFlow(true)
        val h = harness(reconnectAllowed = allowed)
        h.start() // fails at 0; next try scheduled for 1 s
        advanceTimeBy(500)
        allowed.value = false
        runCurrent()
        assertEquals(StreamStatus.Disconnected(1, null), h.statuses.last().second)
        advanceTimeBy(60_000)
        assertEquals(listOf(0L), h.connector.opens)
        allowed.value = true
        runCurrent()
        assertEquals(listOf(0L, 60_500L), h.connector.opens)
    }

    @Test
    fun `B-PROTO-146 a live stream stays up when reconnect turns off, and a new network does not drop it`() = runTest {
        val allowed = MutableStateFlow(true)
        val h = harness(
            reconnectAllowed = allowed,
            scripts = listOf(Script.Body(listOf(0L to frame("a"), 20_000L to frame("b")), Script.End.Hang)),
        )
        h.start()
        allowed.value = false
        h.network.set(Online.Yes, id = 9)
        advanceTimeBy(30_000)
        assertEquals(listOf(0L), h.connector.opens)
        assertEquals(StreamStatus.Connected, h.statuses.last().second)
        assertEquals(listOf("a", "b"), h.frames)
    }

    @Test
    fun `N4-D6 the resume cursor moves only after the collector took the frame (review w1-core-net MAJOR 9)`() = runTest {
        val h = harness(
            resume = ResumeMode.LastEventId,
            scripts = listOf(
                Script.Body(listOf(0L to "id: 1\ndata: a\n\nid: 2\ndata: b\n\n"), Script.End.Hang),
                Script.Body(emptyList(), Script.End.Hang),
            ),
        )
        // A busy collector: after frame 1 it asks for a new try (as a network change would) and stays busy, so the
        // hand-over of frame 2 is cancelled. Frame 2 was never received, so the next try must resume after 1.
        backgroundScope.launch {
            h.loop.signals().collect { s ->
                if (s is StreamSignal.Frame && s.frame.id == "1") {
                    h.loop.retryNow()
                    delay(1_000)
                }
            }
        }
        advanceTimeBy(5_000)
        assertEquals(listOf(null, "1"), h.connector.lastEventIds)
    }
}
