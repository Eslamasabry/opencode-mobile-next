package io.github.eslamasabry.oc.engine.transcript

import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.engine.transcript.Ev.S
import io.github.eslamasabry.oc.engine.transcript.Ev.T0
import io.github.eslamasabry.oc.engine.transcript.Ev.TAIL
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A handle on the test scheduler, its clock at [T0] + virtual time, history answered from [pages]. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.openHandle(
    vararg pages: List<MessageWithParts>,
    watched: Boolean = true,
    history: HistorySource? = null,
): Pair<TranscriptHandle, CoroutineScope> {
    // Not backgroundScope: TestScope.advanceUntilIdle ignores background tasks, and the lane's work must run.
    val parent = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
    val scripted = ScriptedHistory().apply { pages.forEach { queued += Ev.page(it) } }
    val handle =
        TranscriptHandle(
            S, StandardTestDispatcher(testScheduler), parent, nowMs = { T0 + testScheduler.currentTime },
            history = history ?: scripted.source,
        )
    if (watched) parent.launch { handle.state.collect { } } // a watcher: hidden handles skip derivation
    runCurrent()
    return handle to parent
}

/**
 * Plan 09 §14.2 counters, run against the Flutter fixture shapes of `test/perf_chat_test.dart` and
 * `test/chat_rebuild_budget_test.dart`. Flutter asserts `stream_flushes ≤ tokens`; its flush algorithm
 * (`chat_stream.dart` `_scheduleStreamFlush`) gives exactly 10 and 1, and [FlutterFlushModel] computes the same numbers
 * from the same schedule. The engine must match them exactly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PublishCountTest {
    @Test
    fun `B-CHAT-096 publish counts match the Flutter perf fixture for every history shape`() {
        for (fragmented in listOf(false, true)) {
            for (count in listOf(1000, 5000)) {
                runTest {
                    val (handle, parent) = openHandle(Ev.history(count, fragmented))
                    advanceUntilIdle()
                    val tailPart = "p${count - 1}"
                    fun token() = handle.submit(Ev.delta(TAIL, tailPart, "x"))

                    // spaced: token(); pump(); pump(60 ms) — ten times.
                    val flutterSpaced = FlutterFlushModel()
                    val p0 = handle.counters.publishes
                    repeat(10) {
                        token()
                        flutterSpaced.task(testScheduler.currentTime, deltas = 1)
                        runCurrent()
                        testScheduler.advanceTimeBy(60)
                        runCurrent()
                    }
                    advanceUntilIdle()
                    flutterSpaced.finish()
                    val spaced = handle.counters.publishes - p0
                    assertEquals(flutterSpaced.flushes, spaced, "spaced count=$count fragmented=$fragmented")
                    assertEquals(10, spaced)

                    // burst: 100 tokens in one synchronous task, then pump(); pump(60 ms).
                    val flutterBurst = FlutterFlushModel()
                    val p1 = handle.counters.publishes
                    repeat(100) { token() }
                    flutterBurst.task(testScheduler.currentTime, deltas = 100)
                    runCurrent()
                    testScheduler.advanceTimeBy(60)
                    advanceUntilIdle()
                    flutterBurst.finish()
                    val burst = handle.counters.publishes - p1
                    assertEquals(flutterBurst.flushes, burst, "burst count=$count fragmented=$fragmented")
                    assertEquals(1, burst) // plan 09 §14.2: exactly 1 (slice-0 P3 D8)
                    assertTrue(burst <= 100 && spaced <= 10) // Flutter's own assertion: stream_flushes ≤ tokens
                    val tail = handle.state.value.rows.first { it.key == "b:$TAIL/$tailPart:0" } as BlockRow
                    assertEquals("Part ${count - 1}." + "x".repeat(110), tail.text)
                    parent.cancel()
                }
            }
        }
    }

    @Test
    fun `B-CHAT-096 the first delta after quiet is published without advancing time, a trailing one within 50 ms`() =
        runTest {
            val (handle, parent) = openHandle(Ev.history(10, fragmented = false))
            advanceUntilIdle()
            testScheduler.advanceTimeBy(1_000)
            val before = handle.state.value.revision
            val at = testScheduler.currentTime
            handle.submit(Ev.delta(TAIL, "p9", "!"))
            runCurrent()
            assertEquals(before + 1, handle.state.value.revision)
            assertEquals(at, testScheduler.currentTime)
            // A second delta inside the window waits for its end, never longer.
            testScheduler.advanceTimeBy(10)
            handle.submit(Ev.delta(TAIL, "p9", "?"))
            runCurrent()
            assertEquals(before + 1, handle.state.value.revision)
            testScheduler.advanceTimeBy(39)
            runCurrent()
            assertEquals(before + 1, handle.state.value.revision)
            testScheduler.advanceTimeBy(1)
            runCurrent()
            assertEquals(before + 2, handle.state.value.revision)
            assertEquals(at + 50, testScheduler.currentTime)
            parent.cancel()
        }

    @Test
    fun `OtherConversationQuietTest another conversation streaming at 20 per second for 10 s publishes nothing here`() =
        runTest {
            val (handle, parent) = openHandle(Ev.history(10, fragmented = false))
            advanceUntilIdle()
            val before = handle.counters.publishes
            val derives = handle.counters.derives
            repeat(200) {
                handle.submit(Ev.delta("x", "y", "z", session = "other"))
                handle.submit(GatewayEvent.MessageUpserted(Ev.info("x", Role.Assistant, session = "other")))
                testScheduler.advanceTimeBy(50)
            }
            advanceUntilIdle()
            assertEquals(before, handle.counters.publishes)
            assertEquals(derives, handle.counters.derives)
            parent.cancel()
        }

    @Test
    fun `NewestTurnOnlyTest ten deltas into a 1,000-turn history pass at most 20 messages`() =
        runTest {
            // chat_rebuild_budget_test.dart:104: "Ten flushes of a two-message turn, not ten passes over 200 messages."
            val (handle, parent) = openHandle(Ev.history(2_000, fragmented = false))
            advanceUntilIdle()
            val before = handle.counters.displayPassMessages
            repeat(10) {
                handle.submit(Ev.delta(TAIL, "p1999", " more"))
                runCurrent()
                testScheduler.advanceTimeBy(60)
                runCurrent()
            }
            advanceUntilIdle()
            val passed = handle.counters.displayPassMessages - before
            assertTrue(passed <= 10 * 2, "passed $passed")
            assertTrue((handle.state.value.rows.first { it.key == "b:$TAIL/p1999:0" } as BlockRow).text.endsWith("more more more"))
            parent.cancel()
        }

    @Test
    fun `HiddenHandleTest a hidden handle keeps applying but skips derivation until watched`() =
        runTest {
            val (handle, parent) = openHandle(Ev.history(10, fragmented = false), watched = false)
            advanceUntilIdle()
            val derives = handle.counters.derives
            repeat(20) {
                handle.submit(Ev.delta(TAIL, "p9", "x"))
                testScheduler.advanceTimeBy(60)
            }
            advanceUntilIdle()
            assertEquals(derives, handle.counters.derives)
            assertTrue(handle.counters.skippedWhileHidden > 0)
            val watcher = parent.launch { handle.state.collect { } }
            advanceUntilIdle()
            assertEquals(derives + 1, handle.counters.derives)
            assertEquals("Part 9." + "x".repeat(20), (handle.state.value.rows.first { it.key == "b:$TAIL/p9:0" } as BlockRow).text)
            watcher.cancel()
            parent.cancel()
        }
}
