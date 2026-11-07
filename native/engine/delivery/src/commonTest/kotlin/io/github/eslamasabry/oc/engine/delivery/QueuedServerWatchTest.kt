package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.delivery.queue.QueueRead
import io.github.eslamasabry.oc.engine.delivery.queue.QueuedPromptCodec
import io.github.eslamasabry.oc.engine.delivery.queue.QueuedServerPorts
import io.github.eslamasabry.oc.engine.delivery.queue.QueuedServerWatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class QueuedServerWatchTest {
    /** One `oc.offlineQueue` entry exactly as Flutter 2.1.4 writes it (`QueuedPrompt.toJson`). */
    private fun entry(id: String, profile: String, dispatchedAt: Long? = null) =
        """{"id":"$id","profileID":"$profile","sessionID":"ses_q","text":"e2e:echo queued once",""" +
            """"attachments":[],"mentions":[],"modelProviderID":null,"modelID":null,"agent":null,""" +
            """"variant":null,"createdAt":1762500000000,"error":null,"dispatchedAt":${dispatchedAt ?: "null"}}"""

    private class Ports : QueuedServerPorts {
        var raw = "[]"
        val live = HashSet<ProfileId>()
        var reachable = HashSet<ProfileId>()
        var watching = true
        val opens = ArrayList<ProfileId>()
        val released = ArrayList<ProfileId>()

        override fun queue(): QueueRead = QueueRead.Readable(QueuedPromptCodec.decodeQueue(raw))

        override fun live(profile: ProfileId): Boolean = profile in live

        override fun mayWatch(): Boolean = watching

        override suspend fun open(profile: ProfileId): Outcome<Unit> {
            opens += profile
            return if (profile in reachable) {
                live += profile
                Outcome.Ok(Unit)
            } else Outcome.Failed(Failure.Network())
        }

        override suspend fun release(profile: ProfileId) {
            released += profile
            live -= profile
        }
    }

    private val b = ProfileId("fx-oc2-b")

    @Test
    fun `B-146 a prompt queued for a saved server not in use is handed to it once it answers`() = runTest {
        val ports = Ports().apply { raw = "[" + entry("q1", b.raw) + "]" }
        val watch = QueuedServerWatch(ports, backgroundScope) { currentTime }
        watch.start()
        runCurrent()
        assertEquals(listOf(b), ports.opens, "checked at once")
        // Still cut off: retried with a bounded backoff, never a tight loop.
        advanceTimeBy(60_000)
        runCurrent()
        assertTrue(ports.opens.size in 3..5, "a handful of checks a minute, not more: ${ports.opens.size}")
        // The server is back: the next check opens it, and it is not opened again while live.
        ports.reachable += b
        advanceTimeBy(QueuedServerWatch.LONGEST_WAIT.inWholeMilliseconds + QueuedServerWatch.ROUND.inWholeMilliseconds)
        runCurrent()
        assertTrue(b in ports.live)
        val opensWhenLive = ports.opens.size
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(opensWhenLive, ports.opens.size, "a live server is never opened twice")
        // Its own queue sent the prompt (the entry is gone): the watch lets go of the connection.
        ports.raw = "[]"
        advanceTimeBy(QueuedServerWatch.ROUND.inWholeMilliseconds)
        runCurrent()
        assertEquals(listOf(b), ports.released)
    }

    @Test
    fun `B-146 a dispatched prompt is never a reason to reconnect, and nothing runs without leave`() = runTest {
        val ports = Ports().apply { raw = "[" + entry("q1", b.raw, dispatchedAt = 1762500001000) + "]" }
        val watch = QueuedServerWatch(ports, backgroundScope) { currentTime }
        watch.start()
        advanceTimeBy(120_000)
        runCurrent()
        assertTrue(ports.opens.isEmpty(), "possibly sent: only the person decides")

        ports.raw = "[" + entry("q2", b.raw) + "]"
        ports.watching = false // battery saver, offline or not in use
        advanceTimeBy(120_000)
        runCurrent()
        assertTrue(ports.opens.isEmpty())
    }

    @Test
    fun `B-146 at most three saved servers are checked per round and an unreadable queue checks none`() = runTest {
        val ports =
            Ports().apply {
                raw = "[" + (1..5).joinToString(",") { entry("q$it", "p$it") } + "]"
            }
        val watch = QueuedServerWatch(ports, backgroundScope) { currentTime }
        watch.start()
        runCurrent()
        assertEquals(QueuedServerWatch.MAX_SERVERS, ports.opens.size)
        assertEquals(emptySet(), QueuedServerWatch.waiting(QueueRead.Unreadable))
    }
}
