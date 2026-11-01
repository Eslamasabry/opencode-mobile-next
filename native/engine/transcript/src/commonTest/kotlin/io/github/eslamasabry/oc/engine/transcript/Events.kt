package io.github.eslamasabry.oc.engine.transcript

import io.github.eslamasabry.oc.engine.transcript.state.DeltaField
import io.github.eslamasabry.oc.engine.transcript.state.GatewayEvent
import io.github.eslamasabry.oc.engine.transcript.state.MessageInfo
import io.github.eslamasabry.oc.engine.transcript.state.MessageWithParts
import io.github.eslamasabry.oc.engine.transcript.state.Part
import io.github.eslamasabry.oc.engine.transcript.state.PartType
import io.github.eslamasabry.oc.engine.transcript.state.Role
import io.github.eslamasabry.oc.engine.transcript.state.ToolStatus

/** Small event builders for reducer tests (session "s"). */
internal object Ev {
    const val S = "s"

    fun user(id: String) = GatewayEvent.MessageUpserted(MessageInfo(id, S, Role.User))

    fun assistant(id: String, completed: Long? = null, error: String? = null) =
        GatewayEvent.MessageUpserted(MessageInfo(id, S, Role.Assistant, completedMs = completed, errorText = error))

    fun text(msg: String, part: String, text: String = "", ended: Boolean = false) =
        GatewayEvent.PartUpserted(Part(part, msg, S, PartType.Text, text = text, ended = ended))

    fun tool(msg: String, call: String, status: ToolStatus) =
        GatewayEvent.PartUpserted(Part(call, msg, S, PartType.Tool, toolName = "read", toolStatus = status))

    fun delta(msg: String, part: String, d: String, session: String = S) =
        GatewayEvent.PartDelta(session, msg, part, DeltaField.Text, d)

    fun prompt(id: String, words: String): List<GatewayEvent> = listOf(user(id), text(id, "$id-t", words, ended = true))

    /** Flutter perf_chat_test `_history(count, fragmented)`: 1,000/5,000 parts as one reply or as plain messages. */
    fun history(count: Int, fragmented: Boolean): List<MessageWithParts> =
        if (fragmented) {
            listOf(
                MessageWithParts(
                    MessageInfo("tail", S, Role.Assistant),
                    (0 until count).map { Part("p$it", "tail", S, PartType.Text, text = "Part $it.") },
                ),
            )
        } else {
            (0 until count).map { i ->
                val id = if (i == count - 1) "tail" else "m$i"
                MessageWithParts(
                    MessageInfo(id, S, if (i % 2 == 0) Role.User else Role.Assistant, createdMs = i + 1L, completedMs = i + 2L),
                    listOf(Part("p$i", id, S, PartType.Text, text = "Part $i.")),
                )
            }
        }
}
