package io.github.eslamasabry.oc.engine.transcript

import io.github.eslamasabry.oc.engine.transcript.internal.TranscriptModel
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.GatewayEvent
import io.github.eslamasabry.oc.engine.transcript.state.NoticeRow
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.engine.transcript.state.sessionIdOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * P3 acceptance on the JVM: the recorded OC2 stream through the real (async) TranscriptEngine handle, with publish
 * counts compared against the Flutter flush algorithm, and the engine's derive cost measured. Numbers are written to
 * build/p3/engine.json (synthetic sizes and timings only, never transcript content).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordedStreamTest {
    private val session: String get() = Oc2Sse.adapt(RecordedStream.recordingBody()).firstNotNullOf { it.event.sessionIdOrNull }

    @Test
    fun `the Flutter OC2 recording reduces to prompt, notice, work line, two replies and one footer`() {
        val events = Oc2Sse.adapt(RecordedStream.recordingBody())
        val model = TranscriptModel(session, TranscriptCounters())
        events.forEach { model.apply(it.event) }
        val s = model.derive(1)
        // Newest first: footer, the second step's words, the work line, the first step's words, the notice, the prompt.
        assertEquals(6, s.rows.size, s.rows.joinToString { it.key })
        assertTrue(s.rows[0] is FooterRow)
        assertEquals("pong.", (s.rows[1] as BlockRow).text)
        val work = s.rows[2] as WorkLineRow
        assertEquals(1, work.calls)
        assertEquals(WorkState.Done, work.state)
        assertEquals("pong.", (s.rows[3] as BlockRow).text)
        assertTrue(s.rows[4] is NoticeRow)
        assertTrue((s.rows[5] as PromptRow).text.startsWith("Reply with exactly the word: pong."))
        assertTrue(s.rows.filterIsInstance<BlockRow>().none { it.live })
        assertEquals(1, s.rows.map { it.turnId }.toSet().size) // the notice never started a turn
    }

    @Test
    fun `P3 a recorded 5,000-part stream publishes like the Flutter flush and ends settled`() =
        runTest(timeout = 10.minutes) {
            val stream = RecordedStream.build()
            val shape = stream.shape
            assertEquals(5004, shape.parts)
            val timed = Oc2Sse.adapt(stream.sse)
            val sid = timed.firstNotNullOf { it.event.sessionIdOrNull }

            // Which events change what is drawn (the engine publishes only for those), from a synchronous model.
            val probe = TranscriptModel(sid, TranscriptCounters())
            val changing = BooleanArray(timed.size) { probe.apply(timed[it].event) }

            // Reference 1: the ADR publisher fed every drawing change (what native must do).
            val reference = FlutterFlushModel()
            // Reference 2: the Flutter app as it is: deltas through the flush, every other change a direct setState.
            val flutterDeltaOnly = FlutterFlushModel()
            var flutterDirect = 0
            val groups = timed.indices.groupBy { timed[it].atMs }.toSortedMap()
            for ((at, idx) in groups) {
                reference.task(at, idx.count { changing[it] })
                flutterDeltaOnly.task(at, idx.count { changing[it] && timed[it].event is GatewayEvent.PartDelta })
                flutterDirect += idx.count { changing[it] && timed[it].event !is GatewayEvent.PartDelta }
            }
            reference.finish()
            flutterDeltaOnly.finish()

            // The engine: the same frames at the same virtual times, through the lane and the 50 ms publisher.
            val parent = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
            val handle = TranscriptHandle(sid, StandardTestDispatcher(testScheduler), parent, nowMs = { testScheduler.currentTime })
            parent.launch { handle.state.collect { } }
            runCurrent()
            val start = groups.firstKey()
            for ((at, idx) in groups) {
                val target = at - start
                if (target > testScheduler.currentTime) testScheduler.advanceTimeBy(target - testScheduler.currentTime)
                runCurrent()
                idx.forEach { handle.submit(timed[it].event) }
            }
            advanceUntilIdle()
            val publishes = handle.counters.publishes

            assertEquals(reference.flushes, publishes, "engine publishes vs the ADR/Flutter flush on the same schedule")
            val durationMs = groups.lastKey() - start
            assertTrue(publishes <= durationMs / 50 + 2, "publishes are ≥ 50 ms apart")
            assertTrue(publishes < flutterDeltaOnly.flushes + flutterDirect, "fewer rebuild triggers than Flutter today")

            val s = handle.state.value
            assertEquals(834, s.rows.count { it is PromptRow })
            assertEquals(834, s.rows.count { it is FooterRow })
            assertEquals(834, s.rows.count { it is WorkLineRow })
            assertTrue(s.rows.filterIsInstance<BlockRow>().none { it.live })
            assertEquals(s.rows.size, s.rows.map { it.key }.toSet().size, "keys are unique")
            for (i in listOf(0, 1, s.rows.size / 2, s.rows.size - 1)) assertEquals(i, s.indexOf(s.rows[i].key))
            assertEquals(0, handle.refreshRequests)

            // Derive cost on the JVM: replay synchronously, deriving at each reference publish point.
            val timing = deriveTimings(sid, timed, groups, changing)
            val metrics =
                linkedMapOf(
                    "turns" to shape.turns, "parts" to shape.parts, "sse_frames" to shape.frames,
                    "text_deltas" to shape.deltas, "text_chars" to shape.textChars, "stream_ms" to durationMs,
                    "adapted_events" to timed.size, "drawing_changes" to changing.count { it },
                    "engine_publishes" to publishes, "reference_flushes" to reference.flushes,
                    "flutter_delta_flushes" to flutterDeltaOnly.flushes, "flutter_direct_setstates" to flutterDirect,
                    "final_rows" to s.rows.size, "display_pass_messages" to handle.counters.displayPassMessages,
                    "tail_reparses" to handle.counters.tailReparses, "full_parses" to handle.counters.markdownParses,
                ) + timing
            report("engine.json", metrics)
            parent.cancel()
        }

    private fun deriveTimings(
        sid: String,
        timed: List<Timed>,
        groups: Map<Long, List<Int>>,
        changing: BooleanArray,
    ): Map<String, Any> {
        val model = TranscriptModel(sid, TranscriptCounters())
        val flush = FlutterFlushModel()
        val derives = ArrayList<Long>()
        var applyNs = 0L
        var rev = 0L
        fun timedDerive() {
            val t0 = System.nanoTime()
            model.derive(++rev)
            derives += System.nanoTime() - t0
        }
        for ((at, idx) in groups) {
            val before = flush.flushes
            flush.task(at, idx.count { changing[it] })
            val t0 = System.nanoTime()
            idx.forEach { model.apply(timed[it].event) }
            applyNs += System.nanoTime() - t0
            // Publish points: a trailing flush fired before this frame and/or a leading one after it.
            repeat(flush.flushes - before) { timedDerive() }
        }
        val beforeEnd = flush.flushes
        flush.finish()
        repeat(flush.flushes - beforeEnd) { timedDerive() }
        val warm = derives.drop(derives.size / 10).sorted() // drop JIT warm-up
        fun pct(p: Double) = warm[((warm.size - 1) * p).toInt()] / 1000
        return linkedMapOf(
            "derive_count" to derives.size,
            "derive_us_p50" to pct(0.5), "derive_us_p95" to pct(0.95), "derive_us_p99" to pct(0.99),
            "derive_us_max" to warm.last() / 1000, "apply_total_ms" to applyNs / 1_000_000,
        )
    }

    private fun report(name: String, metrics: Map<String, Any>) {
        val json = metrics.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":$v" }
        println("P3_ENGINE $json")
        File("build/p3").apply { mkdirs() }.resolve(name).writeText(json + "\n")
    }
}
