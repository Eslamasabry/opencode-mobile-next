package io.github.eslamasabry.oc.engine.transcript

import io.github.eslamasabry.oc.engine.transcript.internal.TranscriptModel
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * STREAM-FIX JVM benchmark, engine half: the lane's cost per publish (apply the deltas since the
 * last publish, then derive) while the `perf:stream-6000` reply streams, by position in the reply,
 * and for the same reply repeated 1×, 2× and 4× in one part. A cost proportional to the newest turn
 * shows as later tenths and longer replies costing more; the fixed engine is flat. Timings go to
 * build/streamfix/engine.json (sizes and times only, no text).
 *
 * Wall-clock numbers on a shared container are noisy: the deterministic proof is `StreamCostTest`;
 * this file reports the times and only fails when the 4× reply's late publishes cost over 2× the 1×
 * reply's (a regression to O(reply)).
 */
class StreamCostBenchmark {
    private class Run(val tenths: List<LongArray>, val fence: LongArray, val prose: LongArray)

    private fun run(reply: String): Run {
        val model = TranscriptModel(Reply6000.SESSION, TranscriptCounters())
        var revision = 0L
        Reply6000.opening().forEach { model.apply(it) }
        model.derive(++revision)
        val deltas = Reply6000.deltas(reply)
        val tenths = List(10) { ArrayList<Long>() }
        val fence = ArrayList<Long>()
        val prose = ArrayList<Long>()
        val fenced = fenced(reply)
        var at = 0
        for (group in deltas.chunked(3)) { // about one publish per 50 ms at 40–90 deltas per second
            val t0 = System.nanoTime()
            group.forEach { model.apply(Reply6000.delta(it)) }
            model.derive(++revision)
            val ns = System.nanoTime() - t0
            at += group.sumOf { it.length }
            tenths[(at * 10L / (reply.length + 1)).toInt()] += ns
            (if (fenced[at - 1]) fence else prose) += ns
        }
        return Run(tenths.map { it.toLongArray() }, fence.toLongArray(), prose.toLongArray())
    }

    /** True for every character inside a fenced code block (fence lines included). */
    private fun fenced(reply: String): BooleanArray {
        val out = BooleanArray(reply.length)
        var open = false
        var ls = 0
        while (ls < reply.length) {
            val nl = reply.indexOf('\n', ls).let { if (it < 0) reply.length else it }
            val fenceLine = reply.startsWith("```", ls)
            val closes = open && fenceLine
            if (fenceLine) open = true
            if (open) for (i in ls until minOf(nl + 1, reply.length)) out[i] = true
            if (closes) open = false
            ls = nl + 1
        }
        return out
    }

    private fun LongArray.pct(p: Double): Double {
        if (isEmpty()) return 0.0
        val s = sorted()
        return s[((s.size - 1) * p).toInt()] / 1_000.0
    }

    @Test
    fun `B-CHAT-096 PB-18 the lane's cost per publish does not grow with the reply`() {
        val one = Reply6000.reply()
        repeat(2) { run(one) } // JIT warm-up
        val sizes = linkedMapOf(1 to one, 2 to "$one\n\n$one", 4 to "$one\n\n$one\n\n$one\n\n$one")
        val out = LinkedHashMap<String, Any>()
        val lateP50 = HashMap<Int, Double>()
        for ((times, reply) in sizes) {
            val runs = List(3) { run(reply) }
            // Median over the three runs of each tenth's p50, so one GC does not decide a tenth.
            val tenthP50 = (0 until 10).map { t -> runs.map { it.tenths[t].pct(0.5) }.sorted()[1] }
            val tenthP95 = (0 until 10).map { t -> runs.map { it.tenths[t].pct(0.95) }.sorted()[1] }
            lateP50[times] = tenthP50.last()
            out["x${times}_chars"] = reply.length
            out["x${times}_tenth_p50_us"] =
                tenthP50.joinToString(",", "[", "]") { "%.1f".format(it) }
            out["x${times}_tenth_p95_us"] =
                tenthP95.joinToString(",", "[", "]") { "%.1f".format(it) }
            out["x${times}_open_fence_p50_us"] =
                "%.1f".format(runs.map { it.fence.pct(0.5) }.sorted()[1])
            out["x${times}_prose_p50_us"] =
                "%.1f".format(runs.map { it.prose.pct(0.5) }.sorted()[1])
        }
        val json =
            out.entries.joinToString(",\n  ", "{\n  ", "\n}") { (k, v) ->
                "\"$k\": ${if (v is String && !v.startsWith("[")) "\"$v\"" else v}"
            }
        println("STREAMFIX_ENGINE $json")
        File("build/streamfix").apply { mkdirs() }.resolve("engine.json").writeText(json + "\n")
        val ratio = lateP50.getValue(4) / lateP50.getValue(1).coerceAtLeast(1.0)
        assertTrue(
            ratio <= 2.0,
            "late publish p50: 4× reply ${lateP50[4]} µs vs 1× ${lateP50[1]} µs",
        )
    }
}
