package io.github.eslamasabry.oc.engine.transcript

import io.github.eslamasabry.oc.engine.transcript.Ev.S
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

/**
 * Plan 09 §14.2 PublishCountTest, run against the Flutter fixture shapes of `test/perf_chat_test.dart`
 * (`_history(count, fragmented)`, then `measure('spaced', 10)` and `measure('burst', 100)`). Flutter asserts
 * `stream_flushes ≤ tokens`; its flush algorithm (`chat_stream.dart` `_scheduleStreamFlush`) gives exactly 10 and 1,
 * and [FlutterFlushModel] computes the same numbers from the same schedule. The engine must match them exactly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PublishCountTest {
    /** Not backgroundScope: TestScope.advanceUntilIdle ignores background tasks, and the lane's work must run. */
    private fun TestScope.handleParent(): CoroutineScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())

    private fun TestScope.openHandle(): TranscriptHandle {
        val parent = handleParent()
        val handle = TranscriptHandle(S, StandardTestDispatcher(testScheduler), parent, nowMs = { testScheduler.currentTime })
        parent.launch { handle.state.collect { } } // a watcher: hidden handles skip derivation
        runCurrent()
        return handle
    }

    @Test
    fun `B-CHAT-096 publish counts match the Flutter perf fixture for every history shape`() {
        for (fragmented in listOf(false, true)) {
            for (count in listOf(1000, 5000)) {
                runTest {
                    val handle = openHandle()
                    handle.loadHistory(Ev.history(count, fragmented))
                    advanceUntilIdle()
                    val tailMsg = "tail"
                    val tailPart = "p${count - 1}"
                    fun token() = handle.submit(Ev.delta(tailMsg, tailPart, "x"))

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
                    // One synchronous task = one microtask flush in Flutter; plan 09 §14.2 allows ≤ 2.
                    assertEquals(1, burst)
                    assertTrue(burst <= 100 && spaced <= 10) // Flutter's own assertion: stream_flushes ≤ tokens
                    val tail = handle.state.value.rows.first { it.key == "b:$tailMsg/$tailPart:0" } as BlockRow
                    assertEquals("Part ${count - 1}." + "x".repeat(110), tail.text)
                    handle.close()
                }
            }
        }
    }

    @Test
    fun `the first delta after quiet is published with no delay`() =
        runTest {
            val handle = openHandle()
            handle.loadHistory(Ev.history(10, fragmented = false))
            advanceUntilIdle()
            testScheduler.advanceTimeBy(1_000)
            val before = handle.state.value.revision
            val at = testScheduler.currentTime
            handle.submit(Ev.delta("tail", "p9", "!"))
            runCurrent()
            assertEquals(before + 1, handle.state.value.revision)
            assertEquals(at, testScheduler.currentTime)
            handle.close()
        }

    @Test
    fun `another conversation streaming at 20 per second publishes nothing here`() =
        runTest {
            val handle = openHandle()
            handle.loadHistory(Ev.history(10, fragmented = false))
            advanceUntilIdle()
            val before = handle.counters.publishes
            repeat(200) {
                handle.submit(Ev.delta("x", "y", "z", session = "other"))
                testScheduler.advanceTimeBy(50)
            }
            advanceUntilIdle()
            assertEquals(before, handle.counters.publishes)
            handle.close()
        }

    @Test
    fun `a hidden handle keeps applying but skips derivation until watched`() =
        runTest {
            val parent = handleParent()
            val handle = TranscriptHandle(S, StandardTestDispatcher(testScheduler), parent, nowMs = { testScheduler.currentTime })
            handle.loadHistory(Ev.history(10, fragmented = false))
            advanceUntilIdle()
            val derives = handle.counters.derives
            repeat(20) {
                handle.submit(Ev.delta("tail", "p9", "x"))
                testScheduler.advanceTimeBy(60)
            }
            advanceUntilIdle()
            assertEquals(derives, handle.counters.derives)
            assertTrue(handle.counters.skippedWhileHidden > 0)
            val watcher = parent.launch { handle.state.collect { } }
            advanceUntilIdle()
            assertEquals(derives + 1, handle.counters.derives)
            assertEquals("Part 9." + "x".repeat(20), (handle.state.value.rows.first { it.key == "b:tail/p9:0" } as BlockRow).text)
            watcher.cancel()
            handle.close()
            parent.cancel()
        }
}
