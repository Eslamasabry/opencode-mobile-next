package io.github.eslamasabry.oc.protocol.paseo.mappers

import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.protocol.paseo.PASEO_TEXT_MAX
import io.github.eslamasabry.oc.protocol.paseo.PaseoWireJson
import io.github.eslamasabry.oc.protocol.paseo.arr
import io.github.eslamasabry.oc.protocol.paseo.objectOrNull
import io.github.eslamasabry.oc.protocol.paseo.paseoText
import io.github.eslamasabry.oc.protocol.paseo.stringOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Private helpers of the tool-call mapping, ports of the private functions of lib/paseo/mappers.dart
 * (_toolInput, _taskNoticeInput, _withBackgroundTask, _taskNoticeOutput, _toolMetadata, _toolOutput,
 * _webResultsText, _structured) and of Dart ToolState.fromJson's output rules.
 */

internal val EMPTY: JsonObject = JsonObject(emptyMap())

/** The card reads a call the person declined as their choice (Dart ToolState.declined matches "user declined"). */
internal const val DECLINED_TEXT: String = "The user declined this tool call"

/** Dart `backgroundTaskMetadataKey` (lib/domain/background_tasks.dart). */
private const val BACKGROUND_TASK_KEY = "backgroundTask"

/**
 * Dart `ToolState.fromJson` for the Paseo shape: a completed call's text is its output, a failed call's its error
 * words, anything else has none; the structured value is kept beside the text. A string input (an `unknown` detail
 * whose input is raw text) has no structured form and travels as [ToolState.inputRaw].
 */
internal fun toolState(
    status: ToolStatus,
    input: JsonElement,
    output: JsonElement,
    error: JsonElement?,
    metadata: JsonObject?,
): ToolState {
    val value: JsonElement? = when (status) {
        ToolStatus.Completed -> output
        ToolStatus.Error -> error
        else -> null
    }
    return ToolState(
        status = status,
        input = input as? JsonObject ?: EMPTY,
        inputRaw = (input as? JsonPrimitive)?.takeIf { it.isString }?.content,
        output = when (status) {
            ToolStatus.Completed, ToolStatus.Error -> pretty(value)
            // A running call keeps what it has put out so far, only its last lines: the step shows it growing in place.
            ToolStatus.Running -> (output as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(::runningTail) ?: ""
            else -> ""
        },
        outputValue = value,
        metadata = metadata,
    )
}

/**
 * A failed command's own output: the lines it printed before it failed are the explanation the person needs, not the
 * provider's raw error text (which stays out of the copy). Null for any other step, or a command that printed nothing.
 */
internal fun failedShellOutput(detail: JsonObject): JsonElement? {
    if (detail["type"].stringOrNull() != "shell") return null
    val output = detail["output"]
    val text = when {
        output is JsonPrimitive && output.isString -> output.content
        output is JsonObject -> output["output"].stringOrNull() ?: output["text"].stringOrNull()
        else -> null
    } ?: return null
    return if (text.isBlank()) null else JsonPrimitive(paseoText(JsonPrimitive(text)))
}

@OptIn(ExperimentalSerializationApi::class)
private val PRETTY = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

/** Dart `ToolState._pretty`: text as is, a map or list as indented JSON, anything else its trimmed text. */
private fun pretty(value: JsonElement?): String =
    when (value) {
        null, JsonNull -> ""
        is JsonPrimitive -> if (value.isString) value.content else value.content.trim()
        is JsonObject -> PRETTY.encodeToString(JsonObject.serializer(), value)
        is JsonArray -> PRETTY.encodeToString(JsonArray.serializer(), value)
    }

/** The words of a failed call's error, whichever shape carries them. */
internal fun toolErrorText(item: JsonObject): String {
    val error = item["error"]
    val content = (error as? JsonObject)?.let { it["content"].takeUnless { c -> c == null || c is JsonNull } ?: it["message"] } ?: error
    return when (content) {
        is JsonPrimitive -> if (content.isString) content.content else ""
        is JsonArray -> content.mapNotNull { (it as? JsonObject)?.get("text").stringOrNull() }.joinToString("")
        else -> ""
    }
}

private fun JsonElement?.isNullish(): Boolean = this == null || this is JsonNull

/** A JSON number (not a string, not a boolean), kept as sent. */
private fun JsonElement?.numberValue(): JsonPrimitive? =
    (this as? JsonPrimitive)?.takeIf { !it.isString && it !is JsonNull && it.content.toDoubleOrNull() != null }

/**
 * Paseo's detail fields under the names the tool card already reads for the same tool in OpenCode (`pattern`,
 * `subagent_type`, `name`). A step type this app has no words for keeps its plain fields.
 */
internal fun toolInput(detail: JsonObject): JsonElement {
    fun pick(vararg names: Pair<String, String>): JsonObject =
        JsonObject(
            LinkedHashMap<String, JsonElement>().apply {
                for ((from, to) in names) {
                    val value = detail[from]
                    if (!value.isNullish() && value !is JsonObject && value !is JsonArray) put(to, value!!)
                }
            },
        )
    return when (detail["type"].stringOrNull()) {
        "shell" -> pick("command" to "command")
        "read" -> pick("filePath" to "filePath", "offset" to "offset", "limit" to "limit")
        "edit" -> pick("filePath" to "filePath", "oldString" to "oldString", "newString" to "newString")
        "write" -> pick("filePath" to "filePath", "content" to "content")
        "search" -> pick("query" to if (detail["toolName"].stringOrNull() == "web_search") "query" else "pattern")
        "fetch" -> pick("url" to "url", "prompt" to "prompt")
        "sub_agent" -> pick("subAgentType" to "subagent_type", "description" to "description")
        "plain_text" -> pick("label" to "name")
        "plan", "worktree_setup" -> EMPTY
        "unknown" -> when (val raw = detail["input"]) {
            is JsonObject -> raw
            is JsonPrimitive -> if (raw.isString) raw else EMPTY
            else -> EMPTY
        }
        else -> JsonObject(
            detail.filter { (key, value) -> key != "type" && key != "output" && value !is JsonObject && value !is JsonArray },
        )
    }
}

private val NOTICE_LABEL = Regex("""^Background task (notification|\w+)$""")

/** A background task's notice as the card reads it: Claude's sentence, how the task ended and which task it was. */
internal fun taskNoticeInput(item: JsonObject, detail: JsonObject): JsonObject {
    val meta = item["metadata"].objectOrNull()
    val status = meta?.get("status").stringOrNull()
    val label = paseoText(detail["label"], max = 2000).trim()
    fun id(key: String): String? = meta?.get(key).stringOrNull()?.takeIf { it.trim().isNotEmpty() && it.length <= 256 }?.trim()
    return JsonObject(
        LinkedHashMap<String, JsonElement>().apply {
            // Paseo's own stand-in when Claude gave no summary is not a summary.
            if (label.isNotEmpty() && !NOTICE_LABEL.containsMatchIn(label)) put("summary", JsonPrimitive(label))
            if (status != null && status.trim().isNotEmpty()) put("status", JsonPrimitive(paseoText(JsonPrimitive(status), max = 64)))
            id("toolUseId")?.let { put("toolUseId", JsonPrimitive(it)) }
            id("taskId")?.let { put("taskId", JsonPrimitive(it)) }
        },
    )
}

private val BACKGROUND_COMMAND_REPLY = Regex("""Command running in background with ID:\s*([A-Za-z0-9_-]+)""")

/**
 * Claude Code's answer when its Agent tool started a sub-agent that goes on in the background (`run_in_background`):
 * "Async agent launched successfully. … agentId: <id> …"; its end is a `task_notification` like a command's.
 */
private val BACKGROUND_AGENT_REPLY = Regex("""Async agent launched successfully[\s\S]*?agentId:\s*([A-Za-z0-9_-]+)""")

/**
 * The step's metadata with the background marker when a finished step started work that goes on: a `bash` command
 * Claude Code sent to the background, or a sub-agent (`task`) it launched in the background. An agent is marked only
 * with a name to show (its description or kind), never with the generic command words.
 */
internal fun withBackgroundTask(meta: JsonObject?, toolName: String, status: ToolStatus, detail: JsonObject): JsonObject? {
    if (status != ToolStatus.Completed) return meta
    val match =
        when (toolName) {
            "bash" -> detail["output"].stringOrNull()?.let(BACKGROUND_COMMAND_REPLY::find)
            "task" ->
                listOfNotNull(
                    detail["output"].stringOrNull(),
                    detail["output"].objectOrNull()?.get("text").stringOrNull(),
                    detail["log"].stringOrNull(),
                ).firstNotNullOfOrNull(BACKGROUND_AGENT_REPLY::find)
            else -> null
        } ?: return meta
    val description =
        (detail["description"].stringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            ?: detail["subAgentType"].stringOrNull()?.trim()?.takeIf { it.isNotEmpty() && toolName == "task" })
    if (toolName == "task" && description == null) return meta
    return JsonObject(
        LinkedHashMap<String, JsonElement>().apply {
            meta?.let(::putAll)
            put(
                BACKGROUND_TASK_KEY,
                JsonObject(
                    LinkedHashMap<String, JsonElement>().apply {
                        put("id", JsonPrimitive(match.groupValues[1]))
                        if (description != null) put("label", JsonPrimitive(description))
                    },
                ),
            )
        },
    )
}

private val RESULT = Regex("""<result>\s*([\s\S]*?)\s*</result>""")

/** What a notice says beyond its sentence: the text, or only a finished agent's `<result>` from a raw envelope. */
internal fun taskNoticeOutput(detail: JsonObject): String {
    val text = paseoText(detail["text"]).trim()
    if (text.isEmpty() || text == paseoText(detail["label"]).trim()) return ""
    if ("<task-notification>" !in text) return text
    return RESULT.find(text)?.groupValues?.get(1) ?: ""
}

/** What the card reads beside the input and output: exit code, counts, the diff, sub-agent actions, setup steps. */
internal fun toolMetadata(detail: JsonObject): JsonObject? {
    val meta = LinkedHashMap<String, JsonElement?>()
    fun number(key: String): JsonElement? = detail[key].numberValue()
    when (detail["type"].stringOrNull()) {
        "shell" -> meta["exit"] = number("exitCode")
        "edit" -> meta["diff"] = detail["unifiedDiff"]
        "search" -> {
            meta["matches"] = number("numMatches")
            meta["count"] = number("numFiles")
            meta["truncated"] = detail["truncated"]
            detail.arr("webResults")?.let { meta["numResults"] = JsonPrimitive(it.size) }
        }
        "fetch" -> {
            meta["httpCode"] = number("code")
            meta["httpText"] = detail["codeText"]
        }
        "sub_agent" -> detail.arr("actions")?.let { actions ->
            meta["actions"] = JsonArray(
                actions.mapNotNull { it as? JsonObject }.take(50).map { action ->
                    JsonObject(
                        mapOf(
                            "tool" to JsonPrimitive(paseoText(action["toolName"], max = 128)),
                            "summary" to JsonPrimitive(paseoText(action["summary"], max = 300)),
                        ),
                    )
                },
            )
        }
        "worktree_setup" -> {
            meta["worktreePath"] = detail["worktreePath"]
            meta["branchName"] = detail["branchName"]
            meta["truncated"] = detail["truncated"]
            detail.arr("commands")?.let { commands ->
                meta["commands"] = JsonArray(
                    commands.mapNotNull { it as? JsonObject }.take(30).map { command ->
                        JsonObject(
                            mapOf(
                                "command" to JsonPrimitive(paseoText(command["command"], max = 2000)),
                                "log" to JsonPrimitive(paseoText(command["log"])),
                                "status" to (command["status"] ?: JsonNull),
                                "exitCode" to (command["exitCode"] ?: JsonNull),
                            ),
                        )
                    },
                )
            }
        }
    }
    val kept = meta.filterValues { !it.isNullish() }.mapValues { it.value!! }
    return if (kept.isEmpty()) null else JsonObject(kept)
}

/**
 * What a tool answered, as text or as its structured value. Paseo puts the answer in a different field per detail
 * type: `output` (text or `{output: …}`), `content`/`filePaths` for grep and glob, `webResults` for web search,
 * `result` for fetch, `text` for skills and plans, `log` for sub-agents and worktree setups.
 */
internal fun toolOutput(item: JsonObject, detail: JsonObject): JsonElement {
    // A failed step says Failed once, in the card's own words; the provider's error text is raw and is not the copy.
    val error = item["error"]
    if (error.stringOrNull()?.isNotEmpty() == true) return TEXT_NONE
    if ((error as? JsonObject)?.get("message").stringOrNull() != null) return TEXT_NONE
    val output = detail["output"]
    if (output is JsonPrimitive && output.isString) return text(output)
    if (output is JsonObject) {
        if (output["text"].stringOrNull() != null) return text(output["text"])
        val inner = output["output"]
        if (inner is JsonPrimitive && inner.isString) return text(inner)
        if (inner is JsonObject || inner is JsonArray) return structured(inner)
        if (output.isNotEmpty()) return structured(output)
    }
    if (output is JsonArray) return structured(output)
    val content = detail["content"]
    val type = detail["type"].stringOrNull()
    if (type == "read" && content.stringOrNull() != null) return text(content)
    if (type == "search") {
        if (content.stringOrNull()?.isNotEmpty() == true) return text(content)
        val paths = detail.arr("filePaths")
        if (paths != null && paths.isNotEmpty()) return JsonPrimitive(paseoText(JsonPrimitive(paths.mapNotNull { it.stringOrNull() }.joinToString("\n"))))
        return JsonPrimitive(webResultsText(detail))
    }
    if (type == "fetch" && detail["result"].stringOrNull() != null) return text(detail["result"])
    if ((type == "plain_text" || type == "plan") && detail["text"].stringOrNull() != null) return text(detail["text"])
    if ((type == "sub_agent" || type == "worktree_setup") && detail["log"].stringOrNull() != null) return text(detail["log"])
    return TEXT_NONE
}

private val TEXT_NONE = JsonPrimitive("")

private fun text(value: JsonElement?): JsonPrimitive = JsonPrimitive(paseoText(value))

/** A web search's results as Markdown: each title with its address under it, then the search's own notes. */
private fun webResultsText(detail: JsonObject): String {
    val blocks = buildList {
        detail.arr("webResults")?.mapNotNull { it as? JsonObject }?.take(50)?.forEach { result ->
            add(
                listOfNotNull(result["title"].stringOrNull()?.let { "**$it**" }, result["url"].stringOrNull()).joinToString("  \n"),
            )
        }
        detail.arr("annotations")?.mapNotNull { it.stringOrNull() }?.let(::addAll)
    }.filter { it.isNotEmpty() }
    return paseoText(JsonPrimitive(blocks.joinToString("\n\n")))
}

/** A structured answer kept as a value when it fits, otherwise its text cut to the usual bound. */
private fun structured(value: JsonElement): JsonElement {
    val encoded = PaseoWireJson.encodeToString(JsonElement.serializer(), value)
    return if (encoded.length > PASEO_TEXT_MAX) JsonPrimitive(paseoText(JsonPrimitive(encoded))) else value
}

/** A running command's output kept as its last 40 lines and 4 KB (the same bound the transcript's live tail uses). */
private fun runningTail(text: String): String {
    val kept = text.trimEnd('\n', '\r')
    var start = 0
    var newlines = 0
    var at = kept.length
    while (at > 0) {
        at = kept.lastIndexOf('\n', at - 1)
        if (at < 0) break
        newlines++
        if (newlines == RUNNING_TAIL_LINES) {
            start = at + 1
            break
        }
    }
    val tail = kept.substring(start)
    return if (tail.length > RUNNING_TAIL_CHARS) tail.substring(tail.length - RUNNING_TAIL_CHARS) else tail
}

private const val RUNNING_TAIL_LINES = 40
private const val RUNNING_TAIL_CHARS = 4 * 1024
