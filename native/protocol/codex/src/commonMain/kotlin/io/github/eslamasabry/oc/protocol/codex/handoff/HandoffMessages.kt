package io.github.eslamasabry.oc.protocol.codex.handoff

import io.github.eslamasabry.oc.protocol.codex.CodexException
import io.github.eslamasabry.oc.protocol.codex.CodexFailureKind
import io.github.eslamasabry.oc.protocol.codex.boolOrNull
import io.github.eslamasabry.oc.protocol.codex.codexFail
import io.github.eslamasabry.oc.protocol.codex.codexList
import io.github.eslamasabry.oc.protocol.codex.codexMillis
import io.github.eslamasabry.oc.protocol.codex.codexObject
import io.github.eslamasabry.oc.protocol.codex.codexString
import io.github.eslamasabry.oc.protocol.codex.intOrNull
import io.github.eslamasabry.oc.protocol.codex.stringOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/*
 * The handoff-only view of `thread/read(includeTurns: true)` (port of lib/codex/handoff_record.dart). It deliberately
 * does not reuse the chat mapper: patch bodies and reasoning are excluded, command cwd/exit outcome and changed paths
 * survive. Text is never shortened here; the record builder masks credentials before its size budget (B-AGENT-058).
 */

public enum class HandoffRole { User, Assistant }

/** One part of a handoff message. Tool [status] is free text (the record writes it verbatim). */
public sealed interface HandoffPart {
    public data class Text(val text: String, val synthetic: Boolean = false) : HandoffPart

    /** Contents excluded; only a label. */
    public data class File(val filename: String) : HandoffPart

    public data class Tool(
        val name: String,
        val status: String,
        val executed: Boolean = true,
        /** Allow-listed string fields only (command, cwd, path …). */
        val input: Map<String, String> = emptyMap(),
        val output: String? = null,
        val exitCode: Long? = null,
        val pruned: Boolean = false,
    ) : HandoffPart

    public data object Compaction : HandoffPart
}

public data class HandoffMessage(
    val id: String,
    val sessionId: String,
    val role: HandoffRole,
    val parts: List<HandoffPart>,
    val createdMs: Long? = null,
    val completedMs: Long? = null,
    /** App-authored fixed copy only; never a server error body. */
    val errorText: String? = null,
)

/** Fails closed on any malformed field (InvalidResponse, without content). */
public fun codexHandoffMessages(thread: JsonObject): List<HandoffMessage> {
    val threadId = codexString(thread["id"], max = 256)
    if (thread["historyMode"].stringOrNull() !in setOf("legacy", "paginated")) invalid()
    val result = mutableListOf<HandoffMessage>()
    val turnIds = HashSet<String>()
    val itemIds = HashSet<String>()
    val messageIds = HashSet<String>()
    var itemCount = 0
    var partCount = 0

    fun add(message: HandoffMessage) {
        partCount += message.parts.size
        if (result.size >= MAX_MESSAGES || partCount > MAX_PARTS) invalid()
        if (!messageIds.add(message.id)) invalid()
        result += message
    }

    for (rawTurn in codexList(thread["turns"], max = 5000)) {
        val turn = codexObject(rawTurn)
        val turnId = codexString(turn["id"], max = 256)
        if (!turnIds.add(turnId)) invalid()
        // Summary / not-loaded payloads (or missing metadata) cannot establish a full-history read.
        if (turn["itemsView"].stringOrNull() != "full") invalid()
        val status = codexString(turn["status"], max = 32)
        if (status !in setOf("inProgress", "completed", "interrupted", "failed")) invalid()
        val created = codexMillis(turn["startedAt"])
        val completed = codexMillis(turn["completedAt"])
        val items = codexList(turn["items"])
        itemCount += items.size
        if (itemCount > MAX_ITEMS) invalid()

        for (rawItem in items) {
            val item = codexObject(rawItem)
            val id = codexString(item["id"], max = 256)
            if (!itemIds.add(id)) invalid()
            val type = codexString(item["type"], max = 128)
            val parts = mutableListOf<HandoffPart>()
            when (type) {
                "userMessage" ->
                    for (rawInput in codexList(item["content"], max = 1000)) {
                        val input = codexObject(rawInput)
                        val inputType = codexString(input["type"], max = 128)
                        parts += if (inputType == "text") HandoffPart.Text(text(input["text"])) else HandoffPart.File("Codex input (contents excluded)")
                    }
                "agentMessage" -> {
                    val phase = item["phase"]?.takeUnless { it is JsonNull }
                    val phaseName = phase?.let { it.stringOrNull() ?: invalid() }
                    if (phaseName != null && phaseName != "final_answer" && phaseName != "commentary") invalid()
                    val body = text(item["text"])
                    if (phaseName == "commentary") continue
                    // Older persisted messages have no phase: do not guess whether it was a final answer.
                    if (phaseName == null) parts += HandoffPart.Text("Codex did not record whether the following assistant message was a final answer.")
                    parts += HandoffPart.Text(body)
                }
                "reasoning" -> continue
                "commandExecution" -> {
                    val state = toolStatus(item["status"])
                    val exitRaw = item["exitCode"]?.takeUnless { it is JsonNull }
                    val exit = exitRaw?.let { it.intOrNull() ?: invalid() }
                    val output = item["aggregatedOutput"]?.takeUnless { it is JsonNull }
                    parts += HandoffPart.Tool(
                        name = "shell",
                        status = if (state == "completed" && exit != null && exit != 0L) "error" else state,
                        executed = item["status"].stringOrNull() != "declined",
                        input = mapOf("command" to text(item["command"]), "cwd" to codexString(item["cwd"])),
                        output = output?.let(::text),
                        exitCode = exit,
                    )
                }
                "mcpToolCall" -> {
                    val state = callStatus(item["status"])
                    val raw = item["result"]?.takeUnless { it is JsonNull }
                    parts += HandoffPart.Tool(
                        name = "mcp:${codexString(item["tool"], max = 256)}",
                        status = state,
                        input = toolInput(item["arguments"]),
                        output = raw?.let { textChunks(codexObject(it)["content"], "text") },
                    )
                }
                "dynamicToolCall" -> {
                    val state = callStatus(item["status"])
                    val successRaw = item["success"]?.takeUnless { it is JsonNull }
                    val success = successRaw?.let { it.boolOrNull() ?: invalid() }
                    val contents = item["contentItems"]?.takeUnless { it is JsonNull }
                    parts += HandoffPart.Tool(
                        name = "dynamic:${codexString(item["tool"], max = 256)}",
                        status = if (success == false) "error" else state,
                        input = toolInput(item["arguments"]),
                        output = contents?.let { textChunks(it, "inputText") },
                    )
                }
                "functionCallOutput" -> {
                    val output = item["output"]
                    parts += HandoffPart.Tool(
                        name = "function:${codexString(item["name"], max = 256)}",
                        // A persisted output has no status: its presence is not a success claim.
                        status = "result recorded; success not reported",
                        output = if (output.stringOrNull() != null) text(output) else textChunks(output, "input_text"),
                    )
                }
                "collabAgentToolCall" -> {
                    val state = if (item["status"].stringOrNull() == "interrupted") "cancelled" else callStatus(item["status"])
                    parts += HandoffPart.Tool(name = "agent:${codexString(item["tool"], max = 256)}", status = state)
                }
                "fileChange" -> {
                    val state = toolStatus(item["status"])
                    val rawStatus = item["status"].stringOrNull()
                    for (rawChange in codexList(item["changes"], max = 1000)) {
                        val change = codexObject(rawChange)
                        val path = codexString(change["path"])
                        val kind = codexObject(change["kind"])
                        val operation = codexString(kind["type"], max = 32)
                        if (operation !in setOf("add", "delete", "update")) invalid()
                        val move = kind["move_path"]?.takeUnless { it is JsonNull }
                        val movePath = if (operation == "update" && move != null) codexString(move) else null
                        // Only paths and the apply outcome: never diff text or arbitrary metadata.
                        for (changed in linkedSetOf(path).apply { movePath?.let(::add) }) {
                            parts += HandoffPart.Tool(
                                name = "edit_file",
                                status = state,
                                executed = rawStatus != "declined",
                                input = mapOf("path" to changed),
                                output = "Codex file change ($operation): $rawStatus.",
                            )
                        }
                    }
                    if (parts.isEmpty()) parts += HandoffPart.Text("Codex reported a file-change step with no paths.")
                }
                "plan" -> parts += HandoffPart.Text("Plan reported by Codex:\n${text(item["text"])}")
                "contextCompaction" -> parts += HandoffPart.Compaction
                else -> parts += HandoffPart.Text("Codex recorded an unsupported item. Its contents are excluded from this handoff.")
            }
            add(
                HandoffMessage(
                    id = id,
                    sessionId = threadId,
                    role = if (type == "userMessage") HandoffRole.User else HandoffRole.Assistant,
                    parts = parts,
                    createdMs = created,
                    completedMs = completed,
                ),
            )
        }
        // A turn can fail without an assistant item: fixed copy keeps that state, never the server error body.
        if (status == "failed" || status == "interrupted" || status == "inProgress") {
            add(
                HandoffMessage(
                    id = "handoff:turn:$turnId",
                    sessionId = threadId,
                    role = HandoffRole.Assistant,
                    errorText = if (status == "failed") "The Codex turn failed. Check the project state before retrying." else null,
                    parts =
                        when (status) {
                            "failed" -> emptyList()
                            "interrupted" -> listOf(HandoffPart.Text("The Codex turn was interrupted before completion."))
                            else -> listOf(HandoffPart.Text("The Codex turn was still in progress when read."))
                        },
                ),
            )
        }
    }
    return result
}

/** Non-throwing form for callers outside the module. */
public fun codexHandoffMessagesOrNull(thread: JsonObject): List<HandoffMessage>? =
    try {
        codexHandoffMessages(thread)
    } catch (_: CodexException) {
        null
    }

private const val MAX_MESSAGES = 5000
private const val MAX_PARTS = 10_000
private const val MAX_ITEMS = 10_000
private const val TEXT_MAX = 1024 * 1024

private fun invalid(): Nothing = codexFail(CodexFailureKind.InvalidResponse)

private fun text(value: JsonElement?): String = codexString(value, max = TEXT_MAX, empty = true)

private fun toolStatus(value: JsonElement?): String =
    when (value.stringOrNull()) {
        "inProgress" -> "running"
        "completed" -> "completed"
        "failed" -> "error"
        "declined" -> "cancelled"
        else -> invalid()
    }

private fun callStatus(value: JsonElement?): String =
    when (value.stringOrNull()) {
        "inProgress" -> "running"
        "completed" -> "completed"
        "failed" -> "error"
        else -> invalid()
    }

private val TOOL_INPUT_KEYS = listOf("command", "cmd", "filePath", "file_path", "path", "cwd", "workdir")

/** Only known command/location strings; URLs, credentials and arbitrary metadata are never copied. */
private fun toolInput(arguments: JsonElement?): Map<String, String> {
    if (arguments !is JsonObject) return emptyMap()
    val out = LinkedHashMap<String, String>()
    for (key in TOOL_INPUT_KEYS) arguments[key]?.takeIf { it.stringOrNull() != null }?.let { out[key] = text(it) }
    return out
}

/** Explicit text chunks only, bounded before joining without cutting a token the redactor must see whole. */
private fun textChunks(raw: JsonElement?, textType: String): String {
    val text = mutableListOf<String>()
    var characters = 0
    var omitted = false
    for (chunk in codexList(raw, max = 1000)) {
        if (chunk !is JsonObject || chunk["type"].stringOrNull() != textType) {
            omitted = true
            continue
        }
        val value = text(chunk["text"])
        characters += value.length + 1
        if (characters > TEXT_MAX) invalid()
        text += value
    }
    if (omitted) text += "Non-text result content excluded."
    return text.joinToString("\n")
}
