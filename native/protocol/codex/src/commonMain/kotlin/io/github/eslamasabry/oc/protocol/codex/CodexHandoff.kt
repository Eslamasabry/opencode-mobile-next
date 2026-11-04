package io.github.eslamasabry.oc.protocol.codex

import io.github.eslamasabry.oc.core.model.transcript.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.json.*

/**
 * Handoff-only persisted projection. No reasoning, patch body, source, credential metadata or
 * remote failure body enters this result. Text remains whole until the common record builder masks
 * secrets and budgets whole turns. The English notices are the transfer document's wire format, not
 * app UI copy.
 */
internal fun codexHandoffMessages(thread: JsonObject): ImmutableList<MessageWithParts> {
    val threadId = thread["id"].str(256)
    if (thread.text("historyMode") !in setOf("legacy", "paginated")) malformed()
    val result = mutableListOf<MessageWithParts>()
    val turnIds = mutableSetOf<String>()
    val itemIds = mutableSetOf<String>()
    val messageIds = mutableSetOf<String>()
    var itemCount = 0
    var partCount = 0
    fun add(message: MessageWithParts) {
        partCount += message.parts.size
        if (result.size >= 5000 || partCount > 10000 || !messageIds.add(message.info.id))
            malformed()
        result += message
    }
    for (rawTurn in thread["turns"].list(5000)) {
        val turn = rawTurn.obj()
        val turnId = turn["id"].str(256)
        if (!turnIds.add(turnId) || turn.text("itemsView") != "full") malformed()
        val status = turn["status"].str(32)
        if (status !in setOf("inProgress", "completed", "interrupted", "failed")) malformed()
        val created = turn.millis("startedAt")
        val completed = turn.millis("completedAt")
        val items = turn["items"].list()
        itemCount += items.size
        if (itemCount > 10000) malformed()
        for (rawItem in items) {
            val item = rawItem.obj()
            val id = item["id"].str(256)
            if (!itemIds.add(id)) malformed()
            val type = item["type"].str(128)
            val parts = mutableListOf<Part>()
            fun text(value: String) {
                parts += Part("$id:${parts.size}", id, threadId, PartType.Text, "text", value)
            }
            fun tool(
                name: String,
                state: ToolStatus,
                input: JsonObject = json(),
                output: String? = null,
                metadata: JsonObject? = null,
                executed: Boolean = true,
            ) {
                parts +=
                    Part(
                        "$id:${parts.size}",
                        id,
                        threadId,
                        PartType.Tool,
                        "tool",
                        callId = id,
                        toolName = name,
                        tool =
                            ToolState(
                                state,
                                input = input,
                                output = output,
                                metadata = metadata,
                                executedFlag = executed,
                            ),
                    )
            }
            when (type) {
                "userMessage" ->
                    for (raw in item["content"].list(1000)) {
                        val input = raw.obj()
                        if (input["type"].str(128) == "text")
                            text(input["text"].str(1024 * 1024, true))
                        else
                            parts +=
                                Part(
                                    "$id:${parts.size}",
                                    id,
                                    threadId,
                                    PartType.File,
                                    "file",
                                    filename = "Codex input (contents excluded)",
                                )
                    }
                "agentMessage" -> {
                    val phase = item["phase"]?.takeUnless { it == JsonNull }?.str(128)
                    if (phase != null && phase !in setOf("final_answer", "commentary")) malformed()
                    val answer = item["text"].str(1024 * 1024, true)
                    if (phase == "commentary") continue
                    if (phase == null)
                        text(
                            "Codex did not record whether the following assistant message was a final answer."
                        )
                    text(answer)
                }
                "reasoning" -> continue
                "commandExecution" -> {
                    val state = handoffToolState(item["status"])
                    val exit = item["exitCode"]?.takeUnless { it == JsonNull }
                    if (
                        exit != null &&
                            (exit !is JsonPrimitive || exit.isString || exit.intOrNull == null)
                    )
                        malformed()
                    val output =
                        item["aggregatedOutput"]
                            ?.takeUnless { it == JsonNull }
                            ?.str(1024 * 1024, true)
                    tool(
                        "shell",
                        if (
                            state == ToolStatus.Completed &&
                                (exit as? JsonPrimitive)?.intOrNull?.let { it != 0 } == true
                        )
                            ToolStatus.Error
                        else state,
                        json(
                            "command" to s(item["command"].str(1024 * 1024, true)),
                            "cwd" to s(item["cwd"].str()),
                        ),
                        output,
                        json("exitCode" to exit),
                        item.text("status") != "declined",
                    )
                }
                "mcpToolCall" -> {
                    val output =
                        item["result"]
                            ?.takeUnless { it == JsonNull }
                            ?.obj()
                            ?.let { textChunks(it["content"], "text") }
                    tool(
                        "mcp:${item["tool"].str(256)}",
                        handoffCallState(item["status"]),
                        toolInput(item["arguments"]),
                        output,
                    )
                }
                "dynamicToolCall" -> {
                    val success = item["success"]?.takeUnless { it == JsonNull }
                    if (
                        success != null &&
                            (success !is JsonPrimitive || success.booleanOrNull == null)
                    )
                        malformed()
                    val state = handoffCallState(item["status"])
                    tool(
                        "dynamic:${item["tool"].str(256)}",
                        if ((success as? JsonPrimitive)?.booleanOrNull == false) ToolStatus.Error
                        else state,
                        toolInput(item["arguments"]),
                        item["contentItems"]
                            ?.takeUnless { it == JsonNull }
                            ?.let { textChunks(it, "inputText") },
                    )
                }
                "functionCallOutput" -> {
                    val output = item["output"]
                    tool(
                        "function:${item["name"].str(256)}",
                        ToolStatus.Pending,
                        output =
                            if (output is JsonPrimitive && output.isString)
                                output.str(1024 * 1024, true)
                            else textChunks(output, "input_text"),
                    )
                    text("Result recorded; success not reported.")
                }
                "collabAgentToolCall" ->
                    tool(
                        "agent:${item["tool"].str(256)}",
                        if (item.text("status") == "interrupted") ToolStatus.Cancelled
                        else handoffCallState(item["status"]),
                    )
                "fileChange" -> {
                    val state = handoffToolState(item["status"])
                    for (raw in item["changes"].list(1000)) {
                        val change = raw.obj()
                        val path = change["path"].str()
                        val kind = change["kind"].obj()
                        val operation = kind["type"].str(32)
                        if (operation !in setOf("add", "delete", "update")) malformed()
                        val moved =
                            if (operation == "update")
                                kind["move_path"]?.takeUnless { it == JsonNull }?.str()
                            else null
                        for (changedPath in setOfNotNull(path, moved)) tool(
                            "edit_file",
                            state,
                            json("path" to s(changedPath)),
                            "Codex file change ($operation): ${item.text("status")}.",
                            executed = item.text("status") != "declined",
                        )
                    }
                    if (parts.isEmpty()) text("Codex reported a file-change step with no paths.")
                }
                "plan" -> text("Plan reported by Codex:\n${item["text"].str(1024 * 1024,true)}")
                "contextCompaction" ->
                    parts += Part("$id:0", id, threadId, PartType.Compaction, "v2:compaction")
                else ->
                    text(
                        "Codex recorded an unsupported item. Its contents are excluded from this handoff."
                    )
            }
            add(
                MessageWithParts(
                    MessageInfo(
                        id,
                        threadId,
                        if (type == "userMessage") Role.User else Role.Assistant,
                        providerId = if (type == "userMessage") null else "codex",
                        time = MsgTime(created, completed),
                    ),
                    parts.toImmutableList(),
                )
            )
        }
        if (status in setOf("failed", "interrupted", "inProgress")) {
            val id = "handoff:turn:$turnId"
            val failed = status == "failed"
            val parts =
                if (failed) persistentListOf()
                else
                    persistentListOf(
                        Part(
                            "$id:0",
                            id,
                            threadId,
                            PartType.Text,
                            "text",
                            if (status == "interrupted")
                                "The Codex turn was interrupted before completion."
                            else "The Codex turn was still in progress when read.",
                        )
                    )
            add(
                MessageWithParts(
                    MessageInfo(
                        id,
                        threadId,
                        Role.Assistant,
                        providerId = "codex",
                        time = MsgTime(created, completed),
                        errorText =
                            if (failed)
                                "The Codex turn failed. Check the project state before retrying."
                            else null,
                        errorKind = if (failed) MessageErrorKind.Unknown else null,
                    ),
                    parts,
                )
            )
        }
    }
    return result.toImmutableList()
}

private fun handoffToolState(value: JsonElement?): ToolStatus =
    when (value.str(32)) {
        "inProgress" -> ToolStatus.Running
        "completed" -> ToolStatus.Completed
        "failed" -> ToolStatus.Error
        "declined" -> ToolStatus.Cancelled
        else -> malformed()
    }

private fun handoffCallState(value: JsonElement?): ToolStatus =
    when (value.str(32)) {
        "inProgress" -> ToolStatus.Running
        "completed" -> ToolStatus.Completed
        "failed" -> ToolStatus.Error
        else -> malformed()
    }

private fun toolInput(value: JsonElement?): JsonObject {
    val args = value as? JsonObject ?: return json()
    return JsonObject(
        listOf("command", "cmd", "filePath", "file_path", "path", "cwd", "workdir")
            .mapNotNull { key ->
                (args[key] as? JsonPrimitive)
                    ?.takeIf { it.isString }
                    ?.let { key to s(it.str(1024 * 1024, true)) }
            }
            .toMap()
    )
}

private fun textChunks(value: JsonElement?, type: String): String {
    val chunks = mutableListOf<String>()
    var characters = 0
    var omitted = false
    for (raw in value.list(1000)) {
        val chunk = raw as? JsonObject
        if (chunk == null || chunk.text("type") != type) {
            omitted = true
            continue
        }
        val text = chunk["text"].str(1024 * 1024, true)
        characters += text.length + 1
        if (characters > 1024 * 1024) malformed()
        chunks += text
    }
    if (omitted) chunks += "Non-text result content excluded."
    return chunks.joinToString("\n")
}
