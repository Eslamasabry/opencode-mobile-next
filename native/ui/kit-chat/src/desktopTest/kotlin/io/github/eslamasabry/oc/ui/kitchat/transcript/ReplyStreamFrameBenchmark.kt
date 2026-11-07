package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import io.github.eslamasabry.oc.ui.kitchat.markdown.P3Fixtures
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.RevealRegistry
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * STREAM-FIX JVM benchmark, UI half: the `perf:stream-6000` reply streams at its recorded pace
 * (1–12 character deltas at 40–90 per second, published at most every 50 ms like the engine) into
 * the transcript list of [KitReplyBlock]s at the live edge, with the reveal and the fade at motion
 * level Full. It runs twice, on the same frames:
 * - `whole`: P3's block shape, where the 400-line fence is one block (what the emulator measured);
 * - `chunked`: the STREAM-FIX engine's shape, where every block is cut into chunks of at most 16
 *   lines.
 *
 * Per test frame (16 ms test clock) it records the wall time of recomposition + measure + layout +
 * draw, the text layouts and the characters laid out ([RevealRegistry.layoutChars]), and the item
 * compositions, split by tenths of the reply. Results go to build/streamfix/ui.json (sizes and
 * times only). The deterministic gates: in `chunked` no frame lays out more than a chunk's worth of
 * characters, and the per-frame layout work of the last tenth is no larger than the first tenth's.
 * Wall times are reported, and checked only loosely (shared container, software raster).
 *
 * Time budget: `runComposeUiTest`'s default 60 s timeout is not a measurement, and on a slower host
 * it killed the run with no frame numbers at all. Each run now states its own budget, [FRAME_BUDGET_MS]
 * of wall time per 16 ms test frame (plus a fixed setup allowance), and the harness checks the
 * mean frame against it with the numbers in the message. The `whole` run is the contrast only: it
 * stops [CONTRAST_TAIL_FRAMES] frames after its open fence settles, since the prose after it is
 * the same work the `chunked` run already measures.
 */
@OptIn(ExperimentalTestApi::class)
class ReplyStreamFrameBenchmark {
    private class Frames(
        val name: String,
        val ns: LongArray,
        val layoutChars: LongArray,
        val layouts: IntArray,
        val compositions: IntArray,
        val tenth: IntArray,
        val inFence: BooleanArray,
        val rows: Int,
    )

    private class FrameSchedule(
        /** Null preserves the previous rows: only the original publication frames assign state. */
        val publications: Array<List<FeedRow>?>,
        val tenth: IntArray,
        val inFence: BooleanArray,
        val finalRows: List<FeedRow>,
        /** Reply characters published by each frame (the text the rows cover). */
        val published: IntArray,
        /** The last frame with an open fence at the live edge, or -1. */
        val lastFenceFrame: Int,
    )

    /**
     * Fixture parsing is not part of the UI measurement; prepare the exact publication schedule
     * before the harness.
     */
    private fun prepareFrames(reply: String, chunkLines: Int): FrameSchedule {
        val paced = StreamFeed.paced(reply)
        val feed = StreamFeed(chunkLines)
        val frameMs = 16L
        val end = paced.last().first + 1_500 // the reveal and the last fades finish
        val n = (end / frameMs).toInt() + 1
        val publications = arrayOfNulls<List<FeedRow>>(n)
        val tenth = IntArray(n)
        val fence = BooleanArray(n)
        val published = IntArray(n)
        var publishedChars = 0
        val text = StringBuilder()
        var rows = emptyList<FeedRow>()
        var next = 0
        var lastPublish = Long.MIN_VALUE / 2
        var dirty = false
        for (f in 0 until n) {
            val now = f * frameMs
            while (next < paced.size && paced[next].first <= now) {
                text.append(paced[next++].second)
                dirty = true
            }
            val ended = next == paced.size
            if (
                (dirty && now - lastPublish >= 50) || (ended && dirty)
            ) { // the engine's ≤ 1 publish per 50 ms
                rows = feed.rows(text.toString(), ended)
                publications[f] = rows
                publishedChars = text.length
                lastPublish = now
                dirty = false
            }
            tenth[f] = (text.length * 10L / (reply.length + 1)).toInt()
            fence[f] = (rows.firstOrNull()?.let { it.live && it.kind == KitBlockKind.Code }) == true
            published[f] = publishedChars
        }
        return FrameSchedule(
            publications,
            tenth,
            fence,
            feed.rows(reply, ended = true),
            published,
            fence.lastIndexOf(true),
        )
    }

    /**
     * Streams the reply into the list frame by frame. With [contrastOnly] the run stops
     * [CONTRAST_TAIL_FRAMES] after the open fence settles and checks what was published so far.
     */
    private fun stream(name: String, chunkLines: Int, contrastOnly: Boolean = false): Frames {
        val reply = P3Fixtures.reply()
        val schedule = prepareFrames(reply, chunkLines)
        val n =
            if (contrastOnly && schedule.lastFenceFrame >= 0) {
                minOf(schedule.publications.size, schedule.lastFenceFrame + 1 + CONTRAST_TAIL_FRAMES)
            } else {
                schedule.publications.size
            }
        val budgetMs = SETUP_BUDGET_MS + (n + 121L) * FRAME_BUDGET_MS
        lateinit var result: Frames
        runComposeUiTest(testTimeout = budgetMs.milliseconds) {
            mainClock.autoAdvance = false
            val registry = RevealRegistry()
            var rows by mutableStateOf(emptyList<FeedRow>())
            var compositions = 0
            lateinit var list: TranscriptListState
            setContent {
                ChatTestTheme {
                    list = rememberTranscriptListState()
                    Box(Modifier.size(412.dp, 860.dp)) {
                        KitTranscriptList(
                            list,
                            rows.firstOrNull()?.key,
                            jumpLabel = "Jump to latest",
                            motion = KitMotionLevel.Full,
                        ) {
                            items(rows, key = { it.key }, contentType = { it.kind }) { r ->
                                compositions++
                                KitReplyBlock(
                                    text = r.text,
                                    kind = r.kind,
                                    live = r.live,
                                    streamed = true,
                                    revealKey = r.key,
                                    revealGroup = "b:m/t",
                                    registry = registry,
                                    motion = KitMotionLevel.Full,
                                    continued = r.continued,
                                    continues = r.continues,
                                )
                            }
                        }
                    }
                }
            }
            mainClock.advanceTimeByFrame()
            val ns = LongArray(n)
            val chars = LongArray(n)
            val layouts = IntArray(n)
            val comps = IntArray(n)
            var notAtEdge = 0
            for (f in 0 until n) {
                schedule.publications[f]?.let { rows = it }
                val c0 = registry.layoutChars
                val l0 = registry.layouts
                val k0 = compositions
                val t0 = System.nanoTime()
                mainClock.advanceTimeByFrame()
                ns[f] = System.nanoTime() - t0
                chars[f] = registry.layoutChars - c0
                layouts[f] = registry.layouts - l0
                comps[f] = compositions - k0
                if (list.lazy.firstVisibleItemIndex != 0) notAtEdge++
            }
            val meanMs = ns.average() / 1e6
            assertTrue(
                meanMs <= FRAME_BUDGET_MS,
                "$name: mean wall time per test frame %.2f ms over the %d ms budget (p50 %.2f, %d frames)"
                    .format(meanMs, FRAME_BUDGET_MS, ns.sorted()[n / 2] / 1e6, n),
            )
            val expected =
                if (n == schedule.publications.size) {
                    rows = schedule.finalRows
                    reply
                } else {
                    reply.substring(0, schedule.published[n - 1])
                }
            repeat(120) { mainClock.advanceTimeByFrame() }
            assertEquals(0, notAtEdge, "$name: frames where the newest row was not at the bottom")
            assertEquals(0, registry.activeCount, "$name: blocks still revealing at the end")
            val drawn = rows.reversed().joinToString("") { it.text }.filterNot { it.isWhitespace() }
            assertEquals(
                expected.filterNot { it.isWhitespace() },
                drawn,
                "$name: every character, in order",
            )
            result =
                Frames(
                    name,
                    ns,
                    chars,
                    layouts,
                    comps,
                    schedule.tenth.copyOf(n),
                    schedule.inFence.copyOf(n),
                    rows.size,
                )
        }
        return result
    }

    private fun LongArray.pct(p: Double): Double =
        if (isEmpty()) 0.0 else sorted()[((size - 1) * p).toInt()].toDouble()

    private fun Frames.select(warm: Int, keep: (Int) -> Boolean) = (warm until ns.size).filter(keep)

    private fun Frames.report(out: MutableMap<String, Any>) {
        val warm = 60 // the first second: class loading and JIT
        fun ms(ix: List<Int>, p: Double) =
            "%.2f".format(LongArray(ix.size) { ns[ix[it]] }.pct(p) / 1e6)
        fun list(f: (List<Int>) -> String) =
            (0 until 10).joinToString(",", "[", "]") { t ->
                select(warm) { tenth[it] == t }.let { if (it.isEmpty()) "null" else f(it) }
            }
        val all = select(warm) { true }
        out["${name}_frames"] = ns.size
        out["${name}_rows"] = rows
        out["${name}_frame_ms_p50"] = ms(all, 0.5)
        out["${name}_frame_ms_p95"] = ms(all, 0.95)
        out["${name}_frame_ms_p99"] = ms(all, 0.99)
        out["${name}_over_8_3ms"] = all.count { ns[it] > 8_333_333 }
        out["${name}_over_16_7ms"] = all.count { ns[it] > 16_666_667 }
        out["${name}_open_fence_frame_ms_p50"] = ms(select(warm) { inFence[it] }, 0.5)
        out["${name}_open_fence_frame_ms_p95"] = ms(select(warm) { inFence[it] }, 0.95)
        out["${name}_tenth_frame_ms_p50"] = list { ms(it, 0.5) }
        out["${name}_tenth_frame_ms_p95"] = list { ms(it, 0.95) }
        out["${name}_tenth_layout_chars_per_frame_mean"] = list { ix ->
            "%.0f".format(ix.map { layoutChars[it] }.average())
        }
        out["${name}_layout_chars_per_frame_max"] = layoutChars.max()
        out["${name}_layouts_per_frame_max"] = layouts.max()
        out["${name}_item_compositions_per_frame_max"] = compositions.max()
    }

    @Test
    fun `B-CHAT-096 PB-11 a 6,000-word reply lays out a bounded chunk per frame, not the growing block`() {
        val whole = stream("whole", Int.MAX_VALUE, contrastOnly = true)
        val chunked = stream("chunked", 16)
        val out = LinkedHashMap<String, Any>()
        whole.report(out)
        chunked.report(out)
        val json =
            out.entries.joinToString(",\n  ", "{\n  ", "\n}") { (k, v) ->
                "\"$k\": ${if (v is String && !v.startsWith("[")) "\"$v\"" else v}"
            }
        println("STREAMFIX_UI $json")
        File("build/streamfix").apply { mkdirs() }.resolve("ui.json").writeText(json + "\n")

        // Deterministic: a frame lays out at most one chunk (+ a settling neighbour), never the
        // whole fence.
        val maxChars = chunked.layoutChars.max()
        assertTrue(maxChars <= 2_000, "chunked: characters laid out in one frame, max $maxChars")
        assertTrue(
            whole.layoutChars.max() > 5 * maxChars,
            "the P3 shape re-lays out the growing fence (${whole.layoutChars.max()})",
        )
        assertTrue(whole.inFence.any { it }, "the contrast run reached the open fence")
        // Flat: the last tenth lays out no more per frame than the first tenths do.
        fun Frames.meanChars(t: Int) =
            (60 until ns.size).filter { tenth[it] == t }.map { layoutChars[it] }.average()
        val early = (0..2).maxOf { chunked.meanChars(it) }
        assertTrue(
            chunked.meanChars(9) <= early * 1.5 + 50,
            "layout chars per frame, last tenth ${chunked.meanChars(9)} vs early $early",
        )
        // Loose wall-clock check: the chunked open fence costs no more than a prose tail does,
        // within 2× and 2 ms.
        fun Frames.p50(keep: (Int) -> Boolean) =
            (60 until ns.size).filter(keep).map { ns[it] }.sorted().let { it[it.size / 2] / 1e6 }
        val fenceP50 = chunked.p50 { chunked.inFence[it] }
        val proseP50 = chunked.p50 { !chunked.inFence[it] && chunked.tenth[it] < 9 }
        assertTrue(
            fenceP50 <= proseP50 * 2 + 2,
            "chunked open fence p50 $fenceP50 ms vs prose $proseP50 ms",
        )
    }

    private companion object {
        /**
         * Wall time allowed per 16 ms test frame. This container measures p50 ≈ 3 ms; a
         * phone-hosted container is several times slower. Over this, the run is not measuring
         * layout any more.
         */
        const val FRAME_BUDGET_MS = 40L

        /** Composition setup, class loading and the first frames' JIT. */
        const val SETUP_BUDGET_MS = 30_000L

        /** Frames the contrast run keeps after its fence settles (about two seconds). */
        const val CONTRAST_TAIL_FRAMES = 120
    }
}
