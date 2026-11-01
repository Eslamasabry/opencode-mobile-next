package io.github.eslamasabry.oc.ui.kitchat.markdown

import kotlin.random.Random

/**
 * The P3 fixtures, as text for the renderers (docs/native/slice0/P3-transcript.md §1.4):
 * - [reply]/[deltas]: plan 09 §14.3 `perf:stream-6000`, the same seeded generator as
 *   `native/quality/src/test/kotlin/io/github/eslamasabry/oc/quality/p3/Stream6000.kt` (same seed → the same 6,000-word
 *   reply with 3 fences, one 400 lines, a table and two lists, cut into 1–12 character deltas). Copied, not shared:
 *   `:quality` is not a dependency of a kit module; the generator moves to `:core:testing` with the slice-2 model types.
 * - [sseTextDeltas]: the text deltas of the real OC2 recording `events.sse` (Flutter `test/fixtures/api2/events.sse`).
 */
object P3Fixtures {
    fun reply(seed: Int = 6000): String {
        val rnd = Random(seed)
        val words = "the agent reads a file then edits one line and runs the tests while the list follows the newest words".split(' ')
        fun sentence(n: Int) = (0 until n).joinToString(" ") { words[rnd.nextInt(words.size)] }.replaceFirstChar { it.uppercase() } + "."
        val sb = StringBuilder()
        var count = 0
        fun para(n: Int) {
            sb.append(sentence(n)).append("\n\n")
            count += n
        }
        sb.append("## Plan\n\n")
        count += 2
        repeat(20) { para(rnd.nextInt(20, 60)) }
        sb.append("```kotlin\n")
        repeat(400) { sb.append("val line$it = compute($it)\n") }
        sb.append("```\n\n")
        count += 400 * 3
        repeat(15) { para(rnd.nextInt(20, 60)) }
        sb.append("- ").append(sentence(6)).append("\n- ").append(sentence(7)).append("\n- ").append(sentence(5)).append("\n\n")
        count += 18
        sb.append("| file | lines |\n| --- | --- |\n| build.gradle.kts | 12 |\n| Main.kt | 40 |\n\n")
        count += 16
        sb.append("```bash\n./gradlew test\n```\n\n")
        count += 2
        sb.append("1. ").append(sentence(8)).append("\n2. ").append(sentence(9)).append("\n\n")
        count += 19
        sb.append("```json\n{ \"ok\": true }\n```\n\n")
        count += 4
        while (count < 6000) para(rnd.nextInt(20, 60).coerceAtMost(6000 - count).coerceAtLeast(1))
        return sb.toString().trimEnd()
    }

    /** The reply as P3's deltas: 1–12 characters each (seed + 1, the same draw order as `Stream6000.events`). */
    fun deltas(reply: String = reply(), seed: Int = 6000): List<String> {
        val rnd = Random(seed + 1)
        val out = ArrayList<String>()
        var i = 0
        while (i < reply.length) {
            val n = rnd.nextInt(1, 13).coerceAtMost(reply.length - i)
            rnd.nextInt(40, 91) // the delta's pacing draw (kept so the cut points match P3's stream)
            out += reply.substring(i, i + n)
            i += n
        }
        return out
    }

    private val delta = Regex(""""type":"session\.text\.delta".*?"assistantMessageID":"([^"]+)".*?"delta":"((?:[^"\\]|\\.)*)"""")

    /** `(assistant message id, text delta)` in wire order from an SSE body. */
    fun sseTextDeltas(sse: String): List<Pair<String, String>> =
        sse.lineSequence()
            .filter { it.startsWith("data: ") }
            .mapNotNull { line -> delta.find(line)?.let { it.groupValues[1] to unescape(it.groupValues[2]) } }
            .toList()

    private fun unescape(s: String): String =
        s.replace("\\n", "\n").replace("\\\"", "\"").replace("\\t", "\t").replace("\\\\", "\\")
}
