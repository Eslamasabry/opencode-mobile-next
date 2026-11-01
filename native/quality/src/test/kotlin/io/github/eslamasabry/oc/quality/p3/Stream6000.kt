package io.github.eslamasabry.oc.quality.p3

import io.github.eslamasabry.oc.engine.transcript.state.DeltaField
import io.github.eslamasabry.oc.engine.transcript.state.GatewayEvent
import io.github.eslamasabry.oc.engine.transcript.state.MessageInfo
import io.github.eslamasabry.oc.engine.transcript.state.Part
import io.github.eslamasabry.oc.engine.transcript.state.PartType
import io.github.eslamasabry.oc.engine.transcript.state.Role
import io.github.eslamasabry.oc.engine.transcript.state.RunOutcome
import kotlin.random.Random

/** One event at a virtual time (ms from the start of the stream). */
data class At(val ms: Long, val event: GatewayEvent)

/**
 * Plan 09 §14.3 `perf:stream-6000`: a fixed 6,000-word reply with 3 fenced code blocks (one 400 lines), a table and two
 * lists, as text deltas of 1–12 characters at a jittered 40–90 deltas per second, identical on every run (seeded).
 * P3 builds the GatewayEvents directly (the SSE-shaped 5,000-part fixture lives in :engine:transcript's tests); the
 * shared generator moves to :core:testing with the slice-2 model types.
 */
object Stream6000 {
    const val SESSION = "ses_p3"

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

    /** Prompt, run start, the reply as deltas, run end. */
    fun events(turn: String = "u1", reply: String = reply(), seed: Int = 6000, startMs: Long = 0): List<At> {
        val rnd = Random(seed + 1)
        val out = ArrayList<At>()
        var t = startMs
        val step = "$turn-a"
        out += At(t, GatewayEvent.MessageUpserted(MessageInfo(turn, SESSION, Role.User)))
        out += At(t, GatewayEvent.PartUpserted(Part("$turn-t", turn, SESSION, PartType.Text, text = "Write the long reply.", ended = true)))
        out += At(t, GatewayEvent.RunStarted(SESSION))
        out += At(t, GatewayEvent.MessageUpserted(MessageInfo(step, SESSION, Role.Assistant)))
        out += At(t, GatewayEvent.PartUpserted(Part("text-0", step, SESSION, PartType.Text)))
        var i = 0
        while (i < reply.length) {
            val n = rnd.nextInt(1, 13).coerceAtMost(reply.length - i)
            t += (1000.0 / rnd.nextInt(40, 91)).toLong()
            out += At(t, GatewayEvent.PartDelta(SESSION, step, "text-0", DeltaField.Text, reply.substring(i, i + n)))
            i += n
        }
        t += 20
        out += At(t, GatewayEvent.PartUpserted(Part("text-0", step, SESSION, PartType.Text, text = reply, ended = true)))
        out += At(t, GatewayEvent.MessageUpserted(MessageInfo(step, SESSION, Role.Assistant, completedMs = t)))
        out += At(t, GatewayEvent.RunEnded(SESSION, RunOutcome.Succeeded))
        return out
    }
}
