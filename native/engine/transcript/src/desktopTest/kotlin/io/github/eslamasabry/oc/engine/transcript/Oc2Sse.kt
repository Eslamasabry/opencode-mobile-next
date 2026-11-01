package io.github.eslamasabry.oc.engine.transcript

import io.github.eslamasabry.oc.engine.transcript.state.DeltaField
import io.github.eslamasabry.oc.engine.transcript.state.GatewayEvent
import io.github.eslamasabry.oc.engine.transcript.state.MessageInfo
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.Part
import io.github.eslamasabry.oc.engine.transcript.state.PartType
import io.github.eslamasabry.oc.engine.transcript.state.Role
import io.github.eslamasabry.oc.engine.transcript.state.RunOutcome
import io.github.eslamasabry.oc.engine.transcript.state.ToolStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** One adapted event and the server's `created` time (ms) of the frame it came from. */
data class Timed(val atMs: Long, val event: GatewayEvent)

/**
 * Test-only OpenCode 2 SSE → GatewayEvent adapter: the transcript rows of plan 04 §7.6, enough to replay the Flutter
 * fixture `test/fixtures/api2/events.sse` (OC2 beta 18600) and the 5,000-part stream templated from it. The real
 * stateful `Oc2EventAdapter` is `:protocol:oc2` work (slice 1/2); this one exists so P3 feeds the reducer wire-shaped
 * input instead of hand-built events.
 */
object Oc2Sse {
    private val json = Json { ignoreUnknownKeys = true }

    /** Data frames of an SSE body (comments and heartbeats skipped; one `data:` line per frame, as OC2 sends). */
    fun frames(body: String): Sequence<JsonObject> =
        body.lineSequence()
            .filter { it.startsWith("data: ") }
            .map { json.parseToJsonElement(it.removePrefix("data: ")).jsonObject }

    fun adapt(body: String): List<Timed> {
        val out = ArrayList<Timed>()
        val all = frames(body).toList()
        // A frame without `created` (server.connected, some deltas) happened at the previous frame's time.
        var lastAt = all.firstNotNullOfOrNull { it["created"]?.jsonPrimitive?.longOrNull } ?: 0L
        for (f in all) {
            val at = f["created"]?.jsonPrimitive?.longOrNull ?: lastAt
            lastAt = at
            adaptFrame(f).forEach { out += Timed(at, it) }
        }
        return out
    }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun adaptFrame(f: JsonObject): List<GatewayEvent> {
        val type = f.str("type") ?: return emptyList()
        val d = f["data"]?.jsonObject ?: return emptyList()
        val sid = d.str("sessionID") ?: ""
        val mid = d.str("assistantMessageID") ?: ""
        val ordinal = d["ordinal"]?.jsonPrimitive?.intOrNull ?: 0
        fun assistant(completed: Long? = null, finish: String? = null) =
            MessageInfo(mid, sid, Role.Assistant, createdMs = f["created"]?.jsonPrimitive?.longOrNull,
                completedMs = completed, finish = finish)
        return when (type) {
            "server.connected" -> listOf(GatewayEvent.Connected)
            "session.execution.started" -> listOf(GatewayEvent.RunStarted(sid))
            "session.step.started" -> listOf(GatewayEvent.RunStarted(sid), GatewayEvent.MessageUpserted(assistant()))
            "session.step.ended" ->
                listOf(GatewayEvent.MessageUpserted(assistant(completed = f["created"]?.jsonPrimitive?.longOrNull, finish = d.str("finish"))))
            "session.execution.succeeded" -> listOf(GatewayEvent.RunEnded(sid, RunOutcome.Succeeded))
            "session.execution.interrupted" -> listOf(GatewayEvent.RunEnded(sid, RunOutcome.Interrupted(d.str("reason"))))
            "session.text.started" -> listOf(GatewayEvent.PartUpserted(Part("text-$ordinal", mid, sid, PartType.Text)))
            "session.text.delta" -> listOf(GatewayEvent.PartDelta(sid, mid, "text-$ordinal", DeltaField.Text, d.str("delta") ?: ""))
            "session.text.ended" ->
                listOf(GatewayEvent.PartUpserted(Part("text-$ordinal", mid, sid, PartType.Text, text = d.str("text") ?: "", ended = true)))
            "session.reasoning.started" -> listOf(GatewayEvent.PartUpserted(Part("reasoning-$ordinal", mid, sid, PartType.Reasoning)))
            "session.reasoning.delta" ->
                listOf(GatewayEvent.PartDelta(sid, mid, "reasoning-$ordinal", DeltaField.Reasoning, d.str("delta") ?: ""))
            "session.reasoning.ended" ->
                listOf(GatewayEvent.PartUpserted(Part("reasoning-$ordinal", mid, sid, PartType.Reasoning, text = d.str("text") ?: "", ended = true)))
            "session.tool.input.started" ->
                listOf(GatewayEvent.PartUpserted(tool(d, sid, mid, ToolStatus.Pending)))
            "session.tool.input.delta" ->
                listOf(GatewayEvent.PartDelta(sid, mid, d.str("id") ?: "", DeltaField.ToolInputRaw, d.str("delta") ?: ""))
            "session.tool.input.ended" -> listOf(GatewayEvent.PartUpserted(tool(d, sid, mid, ToolStatus.Pending)))
            "session.tool.called" -> listOf(GatewayEvent.PartUpserted(tool(d, sid, mid, ToolStatus.Running)))
            "session.tool.success" -> listOf(GatewayEvent.PartUpserted(tool(d, sid, mid, ToolStatus.Completed, ended = true)))
            "session.tool.failed" -> listOf(GatewayEvent.PartUpserted(tool(d, sid, mid, ToolStatus.Error, ended = true)))
            "session.inbox.enqueued" -> {
                // A waiting user prompt: the inbox id is the message id (04 §7.6, `9de34551c`).
                val item = d["item"]?.jsonObject
                val inboxId = d.str("inboxID") ?: return emptyList()
                if (item?.str("type") != "user") return emptyList()
                val text = item["payload"]?.jsonObject?.str("text") ?: ""
                listOf(
                    GatewayEvent.MessageUpserted(MessageInfo(inboxId, sid, Role.User, createdMs = f["created"]?.jsonPrimitive?.longOrNull)),
                    GatewayEvent.PartUpserted(Part("text-0", inboxId, sid, PartType.Text, text = text, ended = true)),
                )
            }
            "session.instructions.updated" -> {
                // Instruction text is never shown (T10): only the kind crosses. evt_… → msg_… id.
                val id = (f.str("id") ?: "evt_").replaceFirst("evt_", "msg_")
                listOf(GatewayEvent.Notice(sid, id, "$id-notice", NoticeKind.InstructionsUpdated))
            }
            else -> emptyList() // usage, deleted, step.streamed, inbox.delivered: not transcript rows in P3
        }
    }

    private fun tool(d: JsonObject, sid: String, mid: String, status: ToolStatus, ended: Boolean = false): Part =
        Part(d.str("id") ?: "", mid, sid, PartType.Tool, toolName = d.str("name"), toolStatus = status, ended = ended)
}
