package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ReplyTimingEvent.AssistantMessageSeen
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ReplyTimingEvent.Busy
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ReplyTimingEvent.DeltaSeen
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ReplyTimingEvent.Ended
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ReplyTimingEvent.PartSeen
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ReplyTimingEvent.UserMessageSeen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Drives the reducer like the Dart test's fake source: [now] is the app clock in ms. */
private class Driver(val inApp: Boolean = true) {
    var state = ReplyTimingState()
    var now = 0L

    fun emit(event: ReplyTimingEvent) {
        state = state.on(event, now)
    }

    fun prompt(session: String = "s1", created: Long = 1000) =
        emit(UserMessageSeen(session, "u-$session-$created", created, inApp))

    fun assistant(session: String = "s1") = emit(AssistantMessageSeen(session, "a-$session", "opencode", "big-pickle"))

    fun text(session: String = "s1", start: Long? = null) = emit(PartSeen(session, "a-$session", PartType.Text, start))
}

class ReplyTimingTest {
    @Test
    fun `B-CHAT-092 times prompt to first output to end, by the app and the server`() {
        val d = Driver()
        d.prompt(created = 50_000)
        d.emit(Busy("s1"))
        d.now = 1200
        d.assistant()
        // The user's own text part is not the reply's first output.
        d.emit(PartSeen("s1", "u-s1-50000", PartType.Text, null))
        assertNull(d.state.runs.getValue("s1").firstMs)
        d.now = 3400
        d.text(start = 53_100)
        d.now = 9000
        d.text(start = 53_100)
        d.emit(Ended("s1", failed = false))

        val last = assertNotNull(d.state.lastInApp)
        assertEquals(3400L, last.firstTokenMs)
        assertEquals(3100L, last.serverFirstTokenMs)
        assertEquals(9000L, last.totalMs)
        assertEquals("opencode/big-pickle", last.model)
        assertFalse(last.failed)
        assertTrue(last.inApp)
        assertEquals(9000L, last.finishedAtMs)
        assertSame(last, d.state.last)
        assertTrue(d.state.runs.isEmpty())
    }

    @Test
    fun `B-CHAT-092 a reply that fails before any output says so`() {
        val d = Driver()
        d.prompt()
        d.now = 2000
        d.emit(Ended("s1", failed = true))

        val last = assertNotNull(d.state.lastInApp)
        assertNull(last.firstTokenMs)
        assertTrue(last.failed)
        assertEquals(2000L, last.totalMs)
    }

    @Test
    fun `B-CHAT-092 a reply on another server is timed but is not the in-app one`() {
        val d = Driver(inApp = false)
        d.prompt()
        d.assistant()
        d.now = 500
        d.emit(DeltaSeen("s1", "a-s1"))
        d.emit(Ended("s1", failed = false))

        val last = assertNotNull(d.state.last)
        assertEquals(500L, last.firstTokenMs)
        assertFalse(last.inApp)
        assertNull(d.state.lastInApp)
    }

    @Test
    fun `B-CHAT-092 a user message seen again after the reply does not time a new one`() {
        val d = Driver()
        d.prompt()
        d.emit(Busy("s1"))
        d.emit(Ended("s1", failed = false))
        val first = d.state.last
        // OpenCode updates the user message (its summary) after the reply.
        d.prompt()
        d.emit(Ended("s1", failed = false))
        assertSame(first, d.state.last)
    }

    @Test
    fun `B-CHAT-092 a prompt queued behind a running reply belongs to that run`() {
        val d = Driver()
        d.prompt(created = 1)
        d.emit(Busy("s1"))
        d.now = 100
        d.prompt(created = 2)
        d.now = 300
        d.emit(Ended("s1", failed = false))
        assertEquals(300L, d.state.last?.totalMs)
    }

    @Test
    fun `B-CHAT-092 output of an unknown message and a run left over an hour are not timed`() {
        val d = Driver()
        d.prompt(session = "old")
        d.emit(DeltaSeen("old", "not-an-assistant"))
        assertNull(d.state.runs.getValue("old").firstMs)
        d.now = ReplyTimingState.STALE_RUN_MS + 1
        d.prompt(session = "new")
        assertEquals(setOf("new"), d.state.runs.keys)
        d.emit(Ended("old", failed = false))
        assertNull(d.state.last)
    }
}
