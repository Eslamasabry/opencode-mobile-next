package io.github.eslamasabry.oc.engine.transcript

import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.supervisedContext
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.NewestFirst
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptPhase
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The engine around the handles (plan 09 §3.1, §3.3, §14.4): wiring to the gateway, ref counts and the warm LRU. */
@OptIn(ExperimentalCoroutinesApi::class)
class EngineTest {
    private fun ref(s: String) = ConversationRef(ProfileId("p"), SessionId(s))

    @Test
    fun `OtherDirectoryHandleTest a handle reads the newest page and every event of its session from the all view`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val gateway = FakeServerGateway()
            val scope = ConnectionCoroutineScope(supervisedContext(null, dispatcher, "connection"))
            val engine = TranscriptEngine(gateway, TestDispatchers(dispatcher), TestClock(), scope)
            val handle = engine.open(ref(Ev.S))
            val watcher = scope.launch { handle.state.collect { } }
            runCurrent()
            assertTrue(gateway.calls.contains("messagePage ${Ev.S}"))
            // The graph moved to another directory: the open chat still hears its own events (the `all` view).
            gateway.setLocation(gateway.location.value.copy(directory = "/elsewhere"))
            gateway.events.emit(Ev.assistant("a"), directory = "/project-x")
            gateway.events.emit(Ev.text("a", "t", "Hello from X"), directory = "/project-x")
            gateway.events.emit(Ev.delta("o", "t", "other chat", session = "other"), directory = "/elsewhere")
            advanceUntilIdle()
            val s = handle.state.value
            assertEquals(TranscriptPhase.Live, s.phase)
            assertEquals("Hello from X", s.rows.filterIsInstance<BlockRow>().single().text)
            watcher.cancel()
            scope.cancel()
        }

    @Test
    fun `HandleLruTest open is ref-counted, an unheld handle closes after 60 s and at most 3 unheld stay warm`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = ConnectionCoroutineScope(supervisedContext(null, dispatcher, "connection"))
            val engine = TranscriptEngine(FakeServerGateway(), TestDispatchers(dispatcher), TestClock(), scope)
            val a = engine.open(ref("a"))
            assertSame(a, engine.open(ref("a")))
            a.close()
            testScheduler.advanceTimeBy(120_000)
            runCurrent()
            assertEquals(1, engine.warm, "still held once")
            a.close()
            testScheduler.advanceTimeBy(59_000)
            runCurrent()
            assertEquals(1, engine.warm)
            assertSame(a, engine.open(ref("a")), "reopened within 60 s: the warm handle")
            a.close()
            testScheduler.advanceTimeBy(60_001)
            runCurrent()
            assertEquals(0, engine.warm)
            assertNotSame(a, engine.open(ref("a")).also { it.close() })
            for (s in listOf("b", "c", "d", "e")) engine.open(ref(s)).close()
            assertEquals(3, engine.warm, "LRU of 3 unheld")
            scope.cancel()
        }
}

/** `NewestFirst` (plan 09 §3.7): ReversedViewTest and IndexStableOnAppendTest. */
class ReversedViewTest {
    @Test
    fun `ReversedViewTest the view reads newest first, equals by content and by source identity`() {
        val src = persistentListOf(1, 2, 3)
        val view = NewestFirst(src)
        assertEquals(listOf(3, 2, 1), view.toList())
        assertEquals(listOf(2, 1), view.subList(1, 3).toList())
        assertEquals(view, NewestFirst(src))
        assertEquals(listOf(3, 2, 1), view)
        assertNotEquals(view, NewestFirst(src.adding(4)))
        assertSame(src, view.source)
    }

    @Test
    fun `IndexStableOnAppendTest appending a block changes no existing index entry`() {
        val h = Harness()
        h.apply(Ev.prompt("u", "Go") + listOf(Ev.runStarted, Ev.assistant("a"), Ev.text("a", "t")))
        h.apply(Ev.delta("a", "t", "One.\n\n"))
        val before = h.derive()
        h.apply(Ev.delta("a", "t", "Two.\n\nThree"))
        val after = h.derive()
        for ((key, pos) in before.positions) assertEquals(pos, after.positions[key], key)
        assertEquals(before.rows.size + 2, after.rows.size)
        assertSame((before.rows as NewestFirst).source[0], (after.rows as NewestFirst).source[0])
    }

    @Test
    fun `B-CHAT-096 the settled prefix is shared, never copied, between publishes`() {
        val h = Harness()
        h.load(Ev.history(1000, fragmented = false))
        val a = h.derive()
        h.apply(Ev.delta(Ev.TAIL, "p999", "x"))
        val b = h.derive()
        assertEquals(a.rows.size, b.rows.size)
        // Every row but the changed tail block is the same instance.
        assertEquals(1, a.rows.indices.count { a.rows[it] !== b.rows[it] })
        assertTrue(h.counters.rowsReplaced < a.rows.size + 5, "a tail update writes O(changed) rows, not the list")
    }
}
