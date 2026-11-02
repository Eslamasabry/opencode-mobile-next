package io.github.eslamasabry.oc.engine.cards

import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull

/** Test builders mirroring the Flutter `gen_ui_codec_test.dart` helpers. */
internal object CardFixtures {
    val scope = CardScope(profileId = "profile", sourceId = "source", directory = "/project")

    val CONFIRM: Map<String, Any?> = mapOf("kind" to "confirm")

    fun input(title: String = "Example", ask: Any? = CONFIRM, id: String = "example"): Map<String, Any?> =
        buildMap {
            put("v", 1L)
            put("id", id)
            put("title", title)
            put("body", emptyList<Any?>())
            if (ask != null) put("ask", ask)
        }

    fun json(value: Any?): JsonElement =
        when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k as String to json(v) })
            is List<*> -> JsonArray(value.map(::json))
            else -> error("not json")
        }

    fun part(
        input: Map<String, Any?> = input(),
        call: String = "call",
        message: String = "assistant",
        name: String = "oc-ui_show",
        status: ToolStatus = ToolStatus.Completed,
        executed: Boolean = true,
        synthetic: Boolean = false,
        session: String = "session",
    ): Part =
        Part(
            id = "part-$call",
            messageId = message,
            sessionId = session,
            type = PartType.Tool,
            rawType = "tool",
            callId = call,
            toolName = name,
            synthetic = synthetic,
            tool = ToolState(status = status, input = json(input) as JsonObject, executedFlag = executed),
        )

    fun parse(input: Map<String, Any?>, call: String = "call", scope: CardScope = this.scope): CardParse? =
        CardCodec.fromPart(part(input, call = call), scope, "session", "assistant")

    fun card(input: Map<String, Any?> = input()): AgentCard = (parse(input) as CardParse.Parsed).card

    fun assistant(id: String = "assistant", call: String = "call", input: Map<String, Any?> = input()): MessageWithParts =
        MessageWithParts(MessageInfo(id, "session", Role.Assistant), persistentListOf(part(input, call = call, message = id)))

    fun user(
        text: String,
        synthetic: Boolean = false,
        role: Role = Role.User,
        session: String = "session",
        id: String = "user",
    ): MessageWithParts =
        MessageWithParts(
            MessageInfo(id, session, role),
            persistentListOf(Part(id = "p-$id", messageId = id, sessionId = session, type = PartType.Text, rawType = "text", text = text, synthetic = synthetic)),
        )
}
