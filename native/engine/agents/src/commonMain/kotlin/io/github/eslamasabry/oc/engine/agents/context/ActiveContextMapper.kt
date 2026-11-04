package io.github.eslamasabry.oc.engine.agents.context

import io.github.eslamasabry.oc.engine.agents.json.AgentJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The explicit content inspector's projection of one OpenCode 2 message (port of lib/api2/active_context_mapper.dart
 * over the `Api2Message.fromJson` reads it relies on). Metadata, binary bodies and attachment addresses are never
 * rendered. Unlike the ordinary transcript, this view shows the text of system, synthetic and skill messages. Returns
 * null when the message has no string `id` (Dart `Api2Message.fromJson` == null).
 */
public object ActiveContextMapper {
    public fun map(message: JsonObject): ActiveContextMessage? {
        val id = message.str("id") ?: return null
        val type = message.str("type") ?: "unknown"
        val parts = ArrayList<ContextContent>()
        fun add(kind: ContextContentKind, text: String?, name: String? = null) {
            if (!text.isNullOrEmpty()) parts += ContextContent(kind, text, name)
        }
        fun file(name: String?, mime: String?) {
            parts += ContextContent(ContextContentKind.File, name ?: mime ?: "", mime)
        }
        when (type) {
            "user" -> {
                add(ContextContentKind.Text, message.str("text"))
                for (f in message.maps("files")) file(f.str("name"), f.str("mime"))
            }
            "assistant" -> for (item in message.maps("content")) assistant(item, parts, ::add, ::file)
            "system", "synthetic" -> add(ContextContentKind.Text, message.str("text"), message.str("description"))
            "skill" -> add(ContextContentKind.Text, message.str("text"), message.str("name") ?: "")
            "shell" -> {
                val output = message["output"] as? JsonObject
                add(ContextContentKind.ToolInput, message.str("command"))
                add(ContextContentKind.ToolOutput, output?.str("output"), message.str("status") ?: "")
                if (output?.bool("truncated") == true) parts += ContextContent(ContextContentKind.Truncated, "")
            }
            "compaction" -> {
                add(ContextContentKind.Text, message.str("summary"), message.str("status") ?: "")
                add(ContextContentKind.Notice, message.str("reason"))
            }
            "agent-switched" -> add(ContextContentKind.Notice, message.str("agent"))
            "model-switched" -> add(ContextContentKind.Notice, modelId(message["model"]))
            "location-switched" -> add(ContextContentKind.Notice, (message["location"] as? JsonObject)?.str("directory"))
            else -> parts += ContextContent(ContextContentKind.Notice, "", type)
        }
        return ActiveContextMessage(id, type, parts)
    }

    private fun assistant(
        item: JsonObject,
        parts: MutableList<ContextContent>,
        add: (ContextContentKind, String?, String?) -> Unit,
        file: (String?, String?) -> Unit,
    ) {
        when (item.str("type")) {
            "text" -> add(ContextContentKind.Text, item.str("text"), null)
            "reasoning" ->
                if (pruned(item)) parts += ContextContent(ContextContentKind.Pruned, "")
                else add(ContextContentKind.Reasoning, item.str("text"), null)
            "tool" -> {
                val name = item.str("name") ?: ""
                val state = item["state"] as? JsonObject ?: JsonObject(emptyMap())
                val status = state.str("status")
                val input = when (status) {
                    "streaming" -> state.str("input") ?: ""
                    "running", "completed", "error" -> pretty(state["input"] as? JsonObject ?: JsonObject(emptyMap()))
                    else -> null
                }
                add(ContextContentKind.ToolInput, input, name)
                if (!pruned(item)) {
                    if (status == "completed" || status == "error") {
                        for (result in state.maps("content")) {
                            when (result.str("type")) {
                                "text" -> add(ContextContentKind.ToolOutput, result.str("text") ?: "", name)
                                "file" -> file(result.str("name"), result.str("mime"))
                                else -> parts += ContextContent(ContextContentKind.Notice, "", name)
                            }
                        }
                    }
                    if (status == "error") {
                        add(ContextContentKind.ToolOutput, (state["error"] as? JsonObject)?.str("message")?.trim(), name)
                    }
                } else {
                    parts += ContextContent(ContextContentKind.Pruned, "", name)
                }
            }
            else -> parts += ContextContent(ContextContentKind.Notice, "", item["type"]?.let(::dartToString))
        }
    }

    /** `time.pruned` is set (Dart `_asInt(...) != null`: any number). */
    private fun pruned(item: JsonObject): Boolean {
        val p = (item["time"] as? JsonObject)?.get("pruned") as? JsonPrimitive ?: return false
        return p !is JsonNull && !p.isString && p.content.toDoubleOrNull() != null
    }

    /** Dart `Api2ModelRef.fromJson(...)?.id`: needs id and providerID; strips the `provider/` prefix. */
    private fun modelId(value: JsonElement?): String? {
        val o = value as? JsonObject ?: return null
        val id = o.str("id") ?: return null
        val provider = o.str("providerID") ?: return null
        val prefix = "$provider/"
        return if (id.startsWith(prefix) && id.length > prefix.length) id.substring(prefix.length) else id
    }

    /** Dart `raw['type']?.toString()`. */
    private fun dartToString(value: JsonElement): String? = when (value) {
        is JsonNull -> null
        is JsonPrimitive -> value.content
        else -> AgentJson.encode(value)
    }

    /** Dart `JsonEncoder.withIndent('  ')`. */
    internal fun pretty(value: JsonElement): String = StringBuilder().also { write(it, value, 0) }.toString()

    private fun write(out: StringBuilder, value: JsonElement, depth: Int) {
        when (value) {
            is JsonObject -> if (value.isEmpty()) out.append("{}") else {
                out.append("{\n")
                value.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) out.append(",\n")
                    indent(out, depth + 1)
                    out.append(AgentJson.encode(JsonPrimitive(k))).append(": ")
                    write(out, v, depth + 1)
                }
                out.append('\n')
                indent(out, depth)
                out.append('}')
            }
            is JsonArray -> if (value.isEmpty()) out.append("[]") else {
                out.append("[\n")
                value.forEachIndexed { i, v ->
                    if (i > 0) out.append(",\n")
                    indent(out, depth + 1)
                    write(out, v, depth + 1)
                }
                out.append('\n')
                indent(out, depth)
                out.append(']')
            }
            else -> out.append(AgentJson.encode(value))
        }
    }

    private fun indent(out: StringBuilder, depth: Int) {
        repeat(depth) { out.append("  ") }
    }

    private fun JsonObject.str(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.bool(key: String): Boolean? =
        (get(key) as? JsonPrimitive)?.takeIf { !it.isString }?.content?.let { if (it == "true") true else if (it == "false") false else null }

    private fun JsonObject.maps(key: String): List<JsonObject> = (get(key) as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()
}
