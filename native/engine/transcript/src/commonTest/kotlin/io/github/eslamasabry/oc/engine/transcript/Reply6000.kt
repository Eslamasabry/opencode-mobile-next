package io.github.eslamasabry.oc.engine.transcript

import io.github.eslamasabry.oc.engine.transcript.state.DeltaField
import io.github.eslamasabry.oc.engine.transcript.state.GatewayEvent
import io.github.eslamasabry.oc.engine.transcript.state.MessageInfo
import io.github.eslamasabry.oc.engine.transcript.state.Part
import io.github.eslamasabry.oc.engine.transcript.state.PartType
import io.github.eslamasabry.oc.engine.transcript.state.Role
import kotlin.random.Random

/**
 * Plan 09 §14.3 `perf:stream-6000`, the same seeded reply as `:quality` `Stream6000` and the device
 * proof's `Stream6000` (identical text and delta sizes, so engine, JVM UI and emulator numbers
 * describe one stream): 6,413 words, one 400-line fence and two short ones, a table and two lists,
 * as text deltas of 1–12 characters. Engine tests cannot see `:quality`, so the generator is
 * repeated here; it moves to `:core:testing` with S2-TX.
 */
internal object Reply6000 {
    const val SESSION: String = "s"

    fun reply(seed: Int = 6000): String {
        val rnd = Random(seed)
        val words =
            "the agent reads a file then edits one line and runs the tests while the list follows the newest words"
                .split(' ')
        fun sentence(n: Int) =
            (0 until n)
                .joinToString(" ") { words[rnd.nextInt(words.size)] }
                .replaceFirstChar { it.uppercase() } + "."
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
        sb.append("- ")
            .append(sentence(6))
            .append("\n- ")
            .append(sentence(7))
            .append("\n- ")
            .append(sentence(5))
            .append("\n\n")
        count += 18
        sb.append(
            "| file | lines |\n| --- | --- |\n| build.gradle.kts | 12 |\n| Main.kt | 40 |\n\n"
        )
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

    /** The reply's text deltas (1–12 characters, seeded like Stream6000.events). */
    fun deltas(reply: String = reply(), seed: Int = 6000): List<String> {
        val rnd = Random(seed + 1)
        val out = ArrayList<String>()
        var i = 0
        while (i < reply.length) {
            val n = rnd.nextInt(1, 13).coerceAtMost(reply.length - i)
            rnd.nextInt(40, 91) // the pacing draw of Stream6000.events, kept so the sizes match it
            out += reply.substring(i, i + n)
            i += n
        }
        return out
    }

    /** Prompt, run start and the empty assistant text part that the deltas then fill. */
    fun opening(turn: String = "u1", step: String = "u1-a"): List<GatewayEvent> =
        listOf(
            GatewayEvent.MessageUpserted(MessageInfo(turn, SESSION, Role.User)),
            GatewayEvent.PartUpserted(
                Part(
                    "$turn-t",
                    turn,
                    SESSION,
                    PartType.Text,
                    text = "Write the long reply.",
                    ended = true,
                )
            ),
            GatewayEvent.RunStarted(SESSION),
            GatewayEvent.MessageUpserted(MessageInfo(step, SESSION, Role.Assistant)),
            GatewayEvent.PartUpserted(Part("text-0", step, SESSION, PartType.Text)),
        )

    fun delta(d: String, step: String = "u1-a"): GatewayEvent =
        GatewayEvent.PartDelta(SESSION, step, "text-0", DeltaField.Text, d)
}
