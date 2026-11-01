package io.github.eslamasabry.oc.engine.transcript

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.WireExtras
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.engine.transcript.Ev.TAIL
import io.github.eslamasabry.oc.engine.transcript.Ev.info
import io.github.eslamasabry.oc.engine.transcript.Ev.msg
import io.github.eslamasabry.oc.engine.transcript.Ev.textPart
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.OlderRow
import io.github.eslamasabry.oc.engine.transcript.state.OlderState
import io.github.eslamasabry.oc.engine.transcript.state.Origin
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * History through the handle (plan 09 §3.4, map CHAT-GENUI-03): open reads the newest page, older pages go by cursor,
 * a read is fenced by load generation and by the event versions at its start, and a refresh never replays the stream.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryMergeTest {
    private fun turn(n: Int) =
        listOf(
            msg(info("u$n", Role.User, created = n * 10L), textPart("u$n", "t", "Question $n")),
            msg(info("a$n", Role.Assistant, created = n * 10L + 1, completed = n * 10L + 2), textPart("a$n", "t", "Answer $n.")),
        )

    private fun pageAnswer(items: List<io.github.eslamasabry.oc.core.model.transcript.MessageWithParts>, next: String? = null) =
        Ev.page(items, next)

    @Test
    fun `B-PROTO-061 open reads the newest page, older pages continue by cursor only and land at the far end`() =
        runTest {
            val history = ScriptedHistory()
            val (handle, parent) = openHandle(history = history.source)
            assertEquals(TranscriptPhase.Loading, handle.state.value.phase)
            history.reads.single().answer.complete(pageAnswer(turn(3) + turn(4), next = "c-older"))
            advanceUntilIdle()
            val first = handle.state.value
            assertEquals(TranscriptPhase.Live, first.phase)
            assertEquals(OlderState.Available, first.older)
            assertEquals(OlderRow(OlderState.Available), first.rows.last()) // the top of the screen
            assertEquals(listOf(null), history.reads.map { it.cursor })

            val older = async { handle.loadOlder() }
            runCurrent()
            assertEquals(OlderState.Loading, handle.state.value.older)
            assertEquals("c-older", history.reads.last().cursor)
            history.reads.last().answer.complete(pageAnswer(turn(1) + turn(2), next = null))
            advanceUntilIdle()
            assertIs<Outcome.Ok<Unit>>(older.await())
            val s = handle.state.value
            assertEquals(OlderState.Exhausted, s.older)
            assertTrue(s.rows.none { it is OlderRow })
            assertEquals(listOf("u4", "u3", "u2", "u1"), s.rows.filterIsInstance<PromptRow>().map { it.messageId })
            assertEquals(mapOf("u1" to 0, "a1" to 1, "u2" to 2, "a2" to 3, "u3" to 4, "a3" to 5, "u4" to 6, "a4" to 7), s.messageOrder)
            assertTrue(s.rows.filterIsInstance<BlockRow>().all { it.origin == Origin.Loaded }, "loaded rows never animate")
            parent.cancel()
        }

    @Test
    fun `B-PROTO-061 a reused cursor or an expired one asks for a reload, never a loop`() =
        runTest {
            val history = ScriptedHistory()
            history.queued += pageAnswer(turn(5), next = "c1")
            history.queued += pageAnswer(turn(4), next = "c1") // the server hands the same cursor back
            val (handle, parent) = openHandle(history = history.source)
            advanceUntilIdle()
            handle.loadOlder()
            advanceUntilIdle()
            assertEquals(OlderState.NeedsReload, handle.state.value.older)
            assertEquals(OlderRow(OlderState.NeedsReload), handle.state.value.rows.last())
            // "Reload" re-reads the newest page from scratch instead of following the cursor.
            history.queued += pageAnswer(turn(5), next = "c2")
            handle.loadOlder()
            advanceUntilIdle()
            assertEquals(OlderState.Available, handle.state.value.older)
            // HTTP 410 on an older page: the cursor expired.
            history.queued += Outcome.Failed(Failure.Net.Rejected(410, null, WireExtras.None))
            handle.loadOlder()
            advanceUntilIdle()
            assertEquals(OlderState.NeedsReload, handle.state.value.older)
            assertEquals(4, handle.counters.historyReads)
            parent.cancel()
        }

    @Test
    fun `CHAT-GENUI-03 what moved during a read wins over the snapshot, what did not is replaced`() =
        runTest {
            val history = ScriptedHistory()
            history.queued += pageAnswer(turn(1) + turn(2))
            val (handle, parent) = openHandle(history = history.source)
            advanceUntilIdle()
            handle.refreshRecent(RefreshReason.Resume)
            advanceUntilIdle() // the debounced read starts and waits
            val read = history.reads.single()
            // While the read is out: a2 keeps streaming, a new step arrives.
            handle.submit(Ev.delta("a2", "t", " More"))
            handle.submit(Ev.assistant("a3", created = 30))
            handle.submit(Ev.text("a3", "t", "New step."))
            advanceUntilIdle()
            // The snapshot was taken before those: an older a2 text, no a3, and a server edit of a1.
            val snapshot =
                listOf(
                    turn(1)[0],
                    msg(info("a1", Role.Assistant, created = 11, completed = 12), textPart("a1", "t", "Answer 1, edited.")),
                ) + turn(2)
            read.answer.complete(pageAnswer(snapshot))
            advanceUntilIdle()
            val texts = handle.state.value.rows.filterIsInstance<BlockRow>().associate { it.messageId to it.text }
            assertEquals("Answer 2. More", texts["a2"]) // newer live part kept
            assertEquals("New step.", texts["a3"]) // newer message kept although the page lacks it
            assertEquals("Answer 1, edited.", texts["a1"]) // untouched since the read: the server's copy wins
            parent.cancel()
        }

    @Test
    fun `CHAT-GENUI-03 a read that a newer load replaced is dropped`() =
        runTest {
            val history = ScriptedHistory()
            history.queued += pageAnswer(turn(1))
            val (handle, parent) = openHandle(history = history.source)
            advanceUntilIdle()
            handle.refreshRecent(RefreshReason.Stale)
            advanceUntilIdle()
            val stale = history.reads.single()
            val reload = async { handle.reload(reset = false) }
            runCurrent()
            val fresh = history.reads.last()
            fresh.answer.complete(pageAnswer(turn(1) + turn(2)))
            advanceUntilIdle()
            reload.await()
            stale.answer.complete(pageAnswer(emptyList())) // late, from an older generation
            advanceUntilIdle()
            assertEquals(4, handle.state.value.messageCount)
            parent.cancel()
        }

    @Test
    fun `CHAT-GENUI-03 a burst of refresh reasons shares one read after 150 ms`() =
        runTest {
            val history = ScriptedHistory()
            history.queued += pageAnswer(turn(1))
            val (handle, parent) = openHandle(history = history.source)
            advanceUntilIdle()
            val reads = handle.counters.historyReads
            handle.refreshRecent(RefreshReason.Stale)
            testScheduler.advanceTimeBy(100)
            handle.submit(GatewayEvent.TranscriptStale(Ev.SID))
            testScheduler.advanceTimeBy(100)
            runCurrent()
            assertEquals(reads, handle.counters.historyReads) // still inside the debounce window
            history.queued += pageAnswer(turn(1))
            advanceUntilIdle()
            assertEquals(reads + 1, handle.counters.historyReads)
            assertEquals(2, handle.counters.refreshRequests)
            parent.cancel()
        }

    @Test
    fun `CHAT-GENUI-54 a failed refresh never shows an error over a transcript on screen`() =
        runTest {
            val history = ScriptedHistory()
            history.queued += Outcome.Failed(Failure.Net.Offline())
            val (empty, p1) = openHandle(history = history.source)
            advanceUntilIdle()
            assertIs<TranscriptPhase.Failed>(empty.state.value.phase) // nothing on screen: the open failed
            p1.cancel()

            history.queued += pageAnswer(turn(1))
            val (handle, p2) = openHandle(history = history.source)
            advanceUntilIdle()
            val rows = handle.state.value.rows
            history.queued += Outcome.Failed(Failure.Net.Offline())
            assertIs<Outcome.Failed>(handle.reload(reset = false))
            advanceUntilIdle()
            assertEquals(TranscriptPhase.Live, handle.state.value.phase)
            assertEquals(rows.map { it.key }, handle.state.value.rows.map { it.key })
            p2.cancel()
        }

    @Test
    fun `B-PROTO-088 a drop mid-stream re-reads instead of replaying, and no word arrives twice`() =
        runTest {
            val history = ScriptedHistory()
            history.queued += pageAnswer(turn(1))
            val (handle, parent) = openHandle(history = history.source)
            advanceUntilIdle()
            handle.submit(Ev.prompt("u2", "Go")[0])
            handle.submit(Ev.prompt("u2", "Go")[1])
            handle.submit(Ev.runStarted)
            handle.submit(Ev.assistant("a2", created = 21))
            handle.submit(Ev.text("a2", "t"))
            handle.submit(Ev.delta("a2", "t", "Half of the "))
            advanceUntilIdle()
            // The link drops; the deltas sent meanwhile are lost (never replayed). The stream reconnects.
            handle.submit(GatewayEvent.Connected)
            advanceUntilIdle()
            assertTrue(handle.state.value.run.crossedDrop)
            val read = history.reads.single()
            read.answer.complete(
                pageAnswer(
                    turn(1) +
                        listOf(
                            msg(info("u2", Role.User, created = 20), textPart("u2", "u2-t", "Go")),
                            msg(info("a2", Role.Assistant, created = 21), textPart("a2", "t", "Half of the answer, then the rest.")),
                        ),
                ),
            )
            advanceUntilIdle()
            // Deltas after the read continue the server's text, not the lost local one.
            handle.submit(Ev.delta("a2", "t", " Done."))
            handle.submit(Ev.runEnded())
            advanceUntilIdle()
            val live = handle.state.value.rows.filterIsInstance<BlockRow>().single { it.messageId == "a2" }
            assertEquals("Half of the answer, then the rest. Done.", live.text)
            // The run that crossed the drop re-reads once more when it ends; the server's copy agrees.
            history.reads.last().answer.complete(
                pageAnswer(
                    turn(1) +
                        listOf(
                            msg(info("u2", Role.User, created = 20), textPart("u2", "u2-t", "Go")),
                            msg(info("a2", Role.Assistant, created = 21, completed = 22), textPart("a2", "t", "Half of the answer, then the rest. Done.")),
                        ),
                ),
            )
            advanceUntilIdle()
            val reply = handle.state.value.rows.filterIsInstance<BlockRow>().single { it.messageId == "a2" }
            assertEquals("Half of the answer, then the rest. Done.", reply.text)
            assertEquals(2, history.reads.size)
            assertEquals(1, handle.state.value.rows.count { it is PromptRow && it.messageId == "u2" })
            assertEquals(listOf(RefreshReason.Reconnected, RefreshReason.RunEndedAfterDrop).size, handle.counters.refreshRequests)
            parent.cancel()
        }

    @Test
    fun `CHAT-GENUI-02 a late edit to old history is not a new row and re-reads when it may be recent`() =
        runTest {
            val history = ScriptedHistory()
            history.queued += pageAnswer(turn(5) + turn(6), next = "older")
            val (handle, parent) = openHandle(history = history.source)
            advanceUntilIdle()
            val before = handle.state.value.messageCount
            // Older than everything loaded (created 15 < 50): not a row, no re-read.
            handle.submit(GatewayEvent.MessageUpserted(info("a1", Role.Assistant, created = 15, completed = 16)))
            advanceUntilIdle()
            assertEquals(before, handle.state.value.messageCount)
            assertEquals(0, handle.counters.refreshRequests)
            // Inside the loaded window (50 ≤ 55 ≤ 61) but unknown: it waits and asks the server for the order.
            handle.submit(GatewayEvent.MessageUpserted(info("x", Role.Assistant, created = 55)))
            advanceUntilIdle()
            assertEquals(1, handle.counters.refreshRequests)
            assertEquals(before, handle.state.value.messageCount)
            // A brand-new message after the newest is a new row at once.
            handle.submit(Ev.assistant(TAIL, created = 99))
            advanceUntilIdle()
            assertEquals(before + 1, handle.state.value.messageCount)
            parent.cancel()
        }
}
