package io.github.eslamasabry.oc.protocol.paseo.requests

import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.model.PermissionTool
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.protocol.paseo.arr
import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.paseoString
import io.github.eslamasabry.oc.protocol.paseo.paseoText
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Maps a daemon permission request to the app's permission card (port of Dart `paseoPermission`,
 * lib/paseo/mappers.dart). The permission name follows the OpenCode vocabulary (`edit`, `bash`, …) so the existing
 * card applies. "Always allow" is offered only when the provider suggested a standing rule the daemon can apply
 * (`canAlwaysAllow`, B-AGENT-148). Throws PaseoException(InvalidResponse) when the request has no usable id.
 */
public fun paseoPermission(agentId: String, request: JsonObject): PermissionRequest {
    val id = paseoString(request["id"], max = 512)
    val name = paseoText(request["name"], max = 256)
    val detail = request.obj("detail") ?: JsonObject(emptyMap())
    val input = request.obj("input") ?: JsonObject(emptyMap())
    val permission: String
    var patterns = mutableListOf<String>()
    var covers: List<String>? = null
    val metadata = LinkedHashMap<String, JsonElement>()
    val facts = mutableListOf<Pair<String, String>>()
    fun text(key: String, max: Int = 4096) = paseoText(detail[key], max = max)
    fun fact(key: String, value: String) {
        facts += key to value
    }
    val type = detail.str("type")
    when {
        type == "write" || type == "edit" -> {
            permission = "edit"
            val path = text("filePath")
            if (path.isNotEmpty()) {
                patterns = mutableListOf(path)
                metadata["filePath"] = JsonPrimitive(path)
            }
            val unified = text("unifiedDiff", 1024 * 1024)
            metadata["diff"] = JsonPrimitive(
                when {
                    type == "write" -> unifiedPatch(path, null, text("content", 1024 * 1024))
                    unified.isNotEmpty() -> unified
                    else -> unifiedPatch(path, text("oldString", 512 * 1024), text("newString", 512 * 1024))
                },
            )
        }
        type == "shell" -> {
            permission = "bash"
            val command = text("command", 65536)
            patterns = mutableListOf(command)
            metadata["command"] = JsonPrimitive(command)
            if (text("cwd").isNotEmpty()) metadata["cwd"] = JsonPrimitive(text("cwd"))
        }
        type == "read" -> {
            permission = "read"
            val path = text("filePath")
            if (path.isNotEmpty()) {
                patterns = mutableListOf(path)
                metadata["filePath"] = JsonPrimitive(path)
            }
            detail["offset"]?.takeIf { it.isNumber() }?.let { metadata["offset"] = it }
            detail["limit"]?.takeIf { it.isNumber() }?.let { metadata["limit"] = it }
        }
        type == "fetch" -> {
            permission = "webfetch"
            val url = text("url")
            if (url.isNotEmpty()) patterns = mutableListOf(url)
            metadata["url"] = JsonPrimitive(url)
            if (text("prompt").isNotEmpty()) metadata["prompt"] = JsonPrimitive(text("prompt"))
            covers = listOf(permission)
        }
        type == "search" -> {
            permission = when (detail.str("toolName")) {
                "web_search" -> "websearch"
                "glob" -> "glob"
                else -> "grep"
            }
            val query = text("query")
            if (query.isNotEmpty()) patterns = mutableListOf(query)
            metadata["query"] = JsonPrimitive(query)
            fact("query", query)
            covers = listOf(permission)
        }
        type == "sub_agent" -> {
            permission = "task"
            val description = text("description")
            if (description.isNotEmpty()) patterns = mutableListOf(description)
            metadata["description"] = JsonPrimitive(description)
            if (description.isNotEmpty()) fact("task", description)
            if (text("subAgentType").isNotEmpty()) metadata["subagent_type"] = JsonPrimitive(text("subAgentType", 256))
            covers = listOf(permission)
        }
        type == "plain_text" && name.lowercase() == "skill" -> {
            permission = "skill"
            val label = text("label", 512)
            if (label.isNotEmpty()) patterns = mutableListOf(label)
            metadata["label"] = JsonPrimitive(label)
            if (label.isNotEmpty()) fact("skill", label)
            covers = listOf(permission)
        }
        else -> {
            permission = when (request.str("kind")) {
                "plan" -> "plan"
                "question" -> "question"
                "mode" -> "mode"
                else -> if (name.isEmpty()) "tool" else toolId(name)
            }
            val shown = if (type == "unknown" && detail["input"] is JsonObject) detail.obj("input")!! else input
            for ((key, value) in shown.entries.take(32)) {
                if (value is JsonPrimitive && value !is kotlinx.serialization.json.JsonNull) {
                    metadata[key] = if (value.isString) JsonPrimitive(paseoText(value, max = 65536)) else value
                }
            }
            patterns = metadata.entries.take(6).map { (key, value) -> "${askLabel(key)}: ${display(value)}" }.toMutableList()
            for ((key, value) in metadata.entries.take(6)) fact(key, display(value))
            covers = listOf(permission)
        }
    }
    val covered = covers ?: patterns.toList()
    // What the agent passed besides what its detail already says: one plain line each.
    if (type != null && type != "unknown") {
        val said = patterns.toSet() + metadata.values.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        for ((key, value) in input.entries.take(16)) {
            if (patterns.size >= 6) break
            val v = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
            if (v.isBlank() || v.length > 300 || '\n' in v || key in INPUT_SHOWN_ELSEWHERE || v in said) continue
            patterns += "${askLabel(key)}: $v"
            fact(key, v)
        }
    }
    if (facts.isNotEmpty()) {
        metadata["facts"] = JsonArray(facts.map { (k, v) -> JsonObject(mapOf("key" to JsonPrimitive(k), "value" to JsonPrimitive(v))) })
    }
    val toolUse = request.obj("metadata")?.str("toolUseId")
    val title = request.str("title")?.trim()?.takeIf { it.isNotEmpty() }
    val description = request.str("description")?.trim()?.takeIf { it.isNotEmpty() && it != title }
    val words = listOfNotNull(title, description)
    val grantable = (request.arr("suggestions")?.size ?: 0) > 0
    return PermissionRequest(
        id = RequestId(id),
        sessionId = SessionId(agentId),
        permission = permission,
        patterns = patterns.toImmutableList(),
        metadata = JsonObject(metadata),
        always = if (grantable) (if (covered.isEmpty()) listOf(permission) else covered).toImmutableList() else kotlinx.collections.immutable.persistentListOf(),
        tool = toolUse?.takeIf { it.isNotEmpty() && it.length <= 256 }?.let { PermissionTool(MessageId(it), it) },
        canAlwaysAllow = grantable,
        message = if (words.isEmpty()) null else paseoText(JsonPrimitive(words.joinToString("\n")), max = 4096),
    )
}

private fun JsonElement.isNumber(): Boolean = this is JsonPrimitive && !isString && this !is kotlinx.serialization.json.JsonNull &&
    content.toDoubleOrNull() != null

private fun display(value: JsonElement): String = (value as? JsonPrimitive)?.content ?: value.toString()

/** Input values the card shows another way (the change as a diff, the plan). */
private val INPUT_SHOWN_ELSEWHERE = setOf("old_string", "new_string", "content", "command", "new_source", "plan")

private val CAMEL = Regex("([a-z0-9])([A-Z])")

/** "NotebookEdit" as "notebook_edit"; names the app already words as one id keep it. */
internal fun toolId(name: String): String {
    val lower = name.lowercase()
    if (lower in setOf("webfetch", "websearch", "todowrite", "todoread", "multiedit")) return lower
    return name.replace(CAMEL) { "${it.groupValues[1]}_${it.groupValues[2]}" }.lowercase()
}

/** "maxResults" and "max_results" as "Max results". */
internal fun askLabel(key: String): String {
    val spaced = key.replace(CAMEL) { "${it.groupValues[1]} ${it.groupValues[2]}" }.replace(Regex("[_-]+"), " ").trim().lowercase()
    return if (spaced.isEmpty()) key else spaced[0].uppercaseChar() + spaced.substring(1)
}

/** A unified patch for before/after text (a missing [before] is a new file), so the diff view draws it. */
internal fun unifiedPatch(path: String, before: String?, after: String): String {
    fun lines(text: String) = if (text.isEmpty()) emptyList() else text.split('\n')
    val removed = if (before == null) emptyList() else lines(before)
    val added = lines(after)
    return (
        listOf(
            "--- ${if (before == null) "/dev/null" else "a/$path"}",
            "+++ b/$path",
            "@@ -${if (removed.isEmpty()) 0 else 1},${removed.size} +${if (added.isEmpty()) 0 else 1},${added.size} @@",
        ) + removed.map { "-$it" } + added.map { "+$it" }
        ).joinToString("\n")
}
