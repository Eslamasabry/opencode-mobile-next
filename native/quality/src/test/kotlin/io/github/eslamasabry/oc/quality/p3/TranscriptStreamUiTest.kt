package io.github.eslamasabry.oc.quality.p3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.transcript.TranscriptEngine
import io.github.eslamasabry.oc.engine.transcript.TranscriptHandle
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.GatewayEvent
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscript
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscriptWords
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.RevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.stream.rememberRevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.transcript.TranscriptListState
import io.github.eslamasabry.oc.ui.kitchat.transcript.rememberTranscriptListState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

private object EnglishWords : ChatTranscriptWords {
    override val jumpToLatest = "Jump to latest"

    override fun workLine(calls: Int, failed: Int, state: WorkState) = "$calls steps"

    override fun notice(kind: NoticeKind) = "Notice"

    override fun footer(failed: Boolean) = "Copy"

    override fun thought(firstLine: String) = firstLine
}

/**
 * P3 on the desktop JVM, headless (runComposeUiTest, Skia raster): a 6,000-word reply streams through the real
 * TranscriptEngine handle (its lane and 50 ms publisher on a virtual-time dispatcher) into the `:feature:chat` block
 * LazyColumn with the draw-phase fade, at the live edge. Each test frame is timed with the wall clock; numbers go to
 * quality/build/p3/ui.json. This is a JVM proxy: the release-build Macrobenchmark on a device (J2, plan 09 §14.3) is
 * still owed. It lives in :quality (like P1's DesktopShellUiTest) because Konsist K1 scans feature test files too.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class TranscriptStreamUiTest {
    private val scheduler = TestCoroutineScheduler()
    private val lane = StandardTestDispatcher(scheduler)
    private val scope = ConnectionCoroutineScope(Job() + lane)
    private val engine =
        TranscriptEngine(
            gateway = FakeServerGateway(),
            dispatchers = TestDispatchers(lane),
            clock = object : Clock { override fun now(): Instant = Instant.fromEpochMilliseconds(scheduler.currentTime) },
            scope = scope,
        )

    @Test
    fun `P3 a 6,000-word reply streams at the live edge, and jump to latest brings a reader back`() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            val handle: TranscriptHandle = engine.open(Stream6000.SESSION)
            var state by mutableStateOf(TranscriptState.Empty)
            scope.launch { handle.state.collect { state = it } }
            lateinit var list: TranscriptListState
            lateinit var reveal: RevealRegistry
            setContent {
                KitTheme(
                    appearance = KitAppearance(mode = AppearanceMode.Light),
                    platform = DesktopKitPlatform(),
                    localeTag = "en",
                ) {
                    list = rememberTranscriptListState()
                    reveal = rememberRevealRegistry()
                    Box(Modifier.size(412.dp, 860.dp)) {
                        ChatTranscript(state, EnglishWords, list, reveal, KitMotionLevel.Full)
                    }
                }
            }
            mainClock.advanceTimeByFrame()

            val reply = Stream6000.reply()
            assertTrue(reply.split(Regex("\\s+")).size >= 6000)
            val events = Stream6000.events(reply = reply)
            val frameMs = 16L
            val frameNs = ArrayList<Long>(8000)
            val laneNs = ArrayList<Long>(8000)
            val liveKind = ArrayList<String>(8000) // what the live tail was in each frame: code, prose or none
            var next = 0
            var notAtEdge = 0
            var frame = 0L
            val end = events.last().ms + 2_000 // let the reveal and fades finish
            while (frame * frameMs <= end) {
                val target = frame * frameMs
                while (next < events.size && events[next].ms <= target) handle.submit(events[next++].event)
                val l0 = System.nanoTime()
                scheduler.advanceTimeBy(target - scheduler.currentTime)
                scheduler.runCurrent()
                laneNs += System.nanoTime() - l0
                val f0 = System.nanoTime()
                mainClock.advanceTimeByFrame()
                frameNs += System.nanoTime() - f0
                val tail = state.rows.firstOrNull() as? BlockRow
                liveKind += if (tail?.live != true) "none" else if (tail.open) "code" else "prose"
                if (list.lazy.firstVisibleItemIndex != 0) notAtEdge++
                frame++
            }

            // Followed the live edge the whole time; nothing left revealing; the reply is all there, in order.
            assertEquals(0, notAtEdge, "frames where the newest row was not at the bottom")
            assertTrue(list.following)
            assertEquals(0, reveal.activeCount)
            val blocks = state.rows.filterIsInstance<BlockRow>()
            val drawn = blocks.reversed().joinToString("") { it.text }.filterNot { it.isWhitespace() }
            assertEquals(reply.filterNot { it.isWhitespace() }, drawn, "every character arrived, in order")
            assertTrue(blocks.none { it.live })
            onNodeWithTag("jump-to-latest").assertDoesNotExist()

            val warm = frameNs.indices.drop(60) // drop the first second: class loading and JIT
            fun statsOf(kind: String?) = stats(warm.filter { kind == null || liveKind[it] == kind }.map { frameNs[it] })
            writeReport(
                linkedMapOf<String, Any>(
                    "reply_words" to reply.split(Regex("\\s+")).size, "reply_chars" to reply.length,
                    "deltas" to events.count { it.event is GatewayEvent.PartDelta },
                    "frames" to frameNs.size, "publishes" to handle.counters.publishes, "rows" to state.rows.size,
                    "reveal_frames" to reveal.totalFrames, "word_landings" to reveal.totalLandings,
                    "frames_open_fence" to liveKind.count { it == "code" },
                ) + statsOf(null).prefixed("frame_") + stats(laneNs.drop(60)).prefixed("lane_") +
                    statsOf("code").prefixed("frame_open_fence_") + statsOf("prose").prefixed("frame_prose_tail_"),
            )

            // A reader scrolls up: following stops, the jump control appears, a new reply does not pull them down.
            mainClock.autoAdvance = true
            onNodeWithTag("transcript-list").performTouchInput { swipeDown() }
            onNodeWithTag("transcript-list").performTouchInput { swipeDown() }
            mainClock.autoAdvance = false
            assertFalse(list.following)
            repeat(30) { mainClock.advanceTimeByFrame() }
            onNodeWithText("Jump to latest").assertIsDisplayed()
            assertTrue(list.lazy.firstVisibleItemIndex > 0)
            val second =
                Stream6000.events(turn = "u2", reply = "A short second reply.\n\nWith two blocks.", seed = 7, startMs = scheduler.currentTime)
            for (e in second) {
                if (e.ms > scheduler.currentTime) scheduler.advanceTimeBy(e.ms - scheduler.currentTime)
                handle.submit(e.event)
                scheduler.runCurrent()
                mainClock.advanceTimeByFrame()
            }
            repeat(60) {
                scheduler.advanceTimeBy(frameMs)
                scheduler.runCurrent()
                mainClock.advanceTimeByFrame()
            }
            assertTrue(list.lazy.firstVisibleItemIndex > 0, "a reader who scrolled up is not pulled to the new reply")
            assertFalse(list.following)

            // Jump to latest: back at the newest row, following again, the control gone.
            mainClock.autoAdvance = true
            onNodeWithText("Jump to latest").performClick()
            waitForIdle()
            assertEquals(0, list.lazy.firstVisibleItemIndex)
            assertTrue(list.following)
            onNodeWithTag("jump-to-latest").assertDoesNotExist()
            handle.close()
            scope.cancel()
        }

    private data class Stats(val p50: Double, val p95: Double, val p99: Double, val max: Double, val mean: Double, val over8: Int, val over16: Int) {
        fun prefixed(p: String): Map<String, Any> =
            linkedMapOf(
                "${p}ms_p50" to p50, "${p}ms_p95" to p95, "${p}ms_p99" to p99, "${p}ms_max" to max,
                "${p}ms_mean" to mean, "${p}over_8_3ms" to over8, "${p}over_16_7ms" to over16,
            )
    }

    private fun stats(ns: List<Long>): Stats {
        val s = ns.sorted()
        fun ms(v: Long) = Math.round(v / 10_000.0) / 100.0
        fun pct(p: Double) = ms(s[((s.size - 1) * p).toInt()])
        return Stats(pct(0.5), pct(0.95), pct(0.99), ms(s.last()), ms(s.average().toLong()),
            s.count { it > 8_333_333 }, s.count { it > 16_666_667 })
    }

    private fun writeReport(metrics: Map<String, Any>) {
        val json = metrics.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":$v" }
        println("P3_UI $json")
        File("build/p3").apply { mkdirs() }.resolve("ui.json").writeText(json + "\n")
    }
}
