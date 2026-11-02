package io.github.eslamasabry.opencode_mobile.proof

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

/**
 * Copied from :engine:transcript desktopTest for the on-device P3 benchmark (benchmark build type only).
 * The P3 recorded stream (plan 09 §14.3 `perf:history-5000`, §15.3 fixtures). It is the real OC2 recording
 * `test/fixtures/api2/events.sse` (one turn: prompt, notice, two steps, reasoning, text, a read tool) replayed as
 * [turns] turns of seeded content, so every frame keeps the wire shape the Flutter fixture has. Each turn carries 6
 * parts (prompt text, 2 reasoning, 2 text, 1 tool): 834 turns = 5,004 parts. Text arrives as deltas of 1–12 characters
 * at a jittered 40–90 deltas per second, identical on every run (seeded). The SSE body is built in memory (about
 * 12 MB; too big to commit) and can be written out with [writeTo] for inspection.
 */
object RecordedStream {
    /** Android copy: the recording is an asset of the benchmark build type, set once before [build]. */
    lateinit var recording: String

    data class Shape(val turns: Int, val parts: Int, val frames: Int, val deltas: Int, val textChars: Int, val durationMs: Long)

    class Stream(val sse: String, val shape: Shape)

    fun recordingBody(): String = recording

    fun build(turns: Int = 834, seed: Int = 3): Stream {
        val template = Oc2Sse.frames(recording).toList()
        val turnFrames =
            template.dropWhile { it.type != "session.inbox.enqueued" }.takeWhile { it.type != "session.deleted" }
        val header = template.takeWhile { it.type != "session.inbox.enqueued" }
        val rnd = Random(seed)
        val out = StringBuilder()
        var now = header.lastOrNull()?.created ?: 0L
        var frames = 0
        var deltas = 0
        var chars = 0
        var parts = 0
        var lastText = ""
        fun emit(o: JsonObject) {
            out.append("data: ").append(o.toString()).append("\n\n")
            frames++
        }
        header.forEach(::emit)
        val sessionId = turnFrames.first().data.str("sessionID")!!
        for (t in 0 until turns) {
            val inbox = "msg_t${t}_prompt"
            val steps = HashMap<String, String>()
            val call = "call_t$t"
            for (f in turnFrames) {
                now += if (f.type == "session.text.delta") 0 else rnd.nextLong(3, 20)
                val data = f.data
                val step = data.str("assistantMessageID")?.let { steps.getOrPut(it) { "msg_t${t}_s${steps.size}" } }
                val patch = HashMap<String, JsonElement>()
                if (step != null) patch["assistantMessageID"] = JsonPrimitive(step)
                when (f.type) {
                    "session.inbox.enqueued" -> {
                        patch["inboxID"] = JsonPrimitive(inbox)
                        val item = data["item"]!!.jsonObject
                        val payload = JsonObject(mapOf("text" to JsonPrimitive(prose(rnd, 6, 20, markdown = false))))
                        patch["item"] = JsonObject(item + ("payload" to payload))
                        parts++
                    }
                    "session.inbox.delivered" -> patch["inboxID"] = JsonPrimitive(inbox)
                    "session.reasoning.started", "session.tool.input.started" -> parts++
                    "session.text.started" -> parts++
                    "session.tool.input.ended", "session.tool.called", "session.tool.success" -> Unit
                }
                if ("id" in data && f.type.startsWith("session.tool.")) patch["id"] = JsonPrimitive(call)
                if (f.type == "session.text.delta") {
                    // The recorded single delta becomes a seeded reply streamed in 1–12 character deltas.
                    val text = prose(rnd, 10, 40, markdown = true)
                    var i = 0
                    while (i < text.length) {
                        val n = rnd.nextInt(1, 13).coerceAtMost(text.length - i)
                        now += (1000.0 / rnd.nextInt(40, 91)).toLong()
                        emit(frame(f, "evt_p3_$frames", now, data, patch + ("delta" to JsonPrimitive(text.substring(i, i + n)))))
                        i += n
                        deltas++
                    }
                    chars += text.length
                    lastText = text
                    continue
                }
                if (f.type == "session.text.ended") patch["text"] = JsonPrimitive(lastText)
                emit(frame(f, "evt_p3_$frames", now, data, patch))
            }
            now += 500
        }
        return Stream(out.toString(), Shape(turns, parts, frames, deltas, chars, now - (header.lastOrNull()?.created ?: 0L)))
    }

    private val JsonObject.type: String get() = this["type"]!!.jsonPrimitive.content
    private val JsonObject.created: Long? get() = this["created"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
    private val JsonObject.data: JsonObject get() = this["data"]!!.jsonObject

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun frame(f: JsonObject, id: String, at: Long, data: JsonObject, patch: Map<String, JsonElement>): JsonObject =
        JsonObject(f + mapOf("id" to JsonPrimitive(id), "created" to JsonPrimitive(at), "data" to JsonObject(data + patch)))

    private val words =
        ("the a reply reads files then edits one line in the build script and runs the tests again because " +
            "a flaky step failed once on the phone while streaming text into the list without jumping").split(' ')

    /** Seeded prose; with [markdown], sometimes a heading, a list or a short code fence. */
    fun prose(rnd: Random, min: Int, max: Int, markdown: Boolean): String {
        val sb = StringBuilder()
        if (markdown && rnd.nextInt(6) == 0) sb.append("## ").append(sentence(rnd, 3)).append('\n')
        sb.append(sentence(rnd, rnd.nextInt(min, max + 1))).append('.')
        if (markdown) {
            when (rnd.nextInt(5)) {
                0 -> sb.append("\n\n- ").append(sentence(rnd, 4)).append("\n- ").append(sentence(rnd, 5))
                1 -> sb.append("\n\n```kotlin\nval n = ").append(rnd.nextInt(100)).append("\nprintln(n)\n```")
                else -> Unit
            }
        }
        return sb.toString()
    }

    private fun sentence(rnd: Random, n: Int): String =
        (0 until n).joinToString(" ") { words[rnd.nextInt(words.size)] }.replaceFirstChar { it.uppercase() }
}
