package io.github.eslamasabry.oc.protocol.codex.gateway

import io.github.eslamasabry.oc.core.common.ids.ProjectId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.protocol.codex.CodexFailureKind
import io.github.eslamasabry.oc.protocol.codex.codexFail
import io.github.eslamasabry.oc.protocol.codex.codexList
import io.github.eslamasabry.oc.protocol.codex.codexMillis
import io.github.eslamasabry.oc.protocol.codex.codexObject
import io.github.eslamasabry.oc.protocol.codex.codexString
import io.github.eslamasabry.oc.protocol.codex.intOrNull
import io.github.eslamasabry.oc.protocol.codex.jsonOf
import io.github.eslamasabry.oc.protocol.codex.stringOrNull
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/*
 * Chat projections of app-server threads (port of lib/codex/mappers.dart). IDs come from persisted items, so streamed
 * and resumed messages coalesce (B-AGENT-063).
 */

private const val TEXT_MAX = 1024 * 1024

/** A thread as a session; a thread whose `cwd` is not [directory] is refused with ScopeMismatch (B-AGENT-063). */
public fun codexSession(thread: JsonObject, directory: String): Session {
    val cwd = codexString(thread["cwd"])
    if (cwd != directory) codexFail(CodexFailureKind.ScopeMismatch)
    val id = codexString(thread["id"], max = 256)
    val name = thread["name"].stringOrNull()
    return Session(
        id = SessionId(id),
        title = if (name != null && name.isNotBlank()) codexString(name) else "Codex conversation",
        directory = cwd,
        projectId = thread["projectId"].stringOrNull()?.let(::ProjectId),
        parentId = thread["parentThreadId"].stringOrNull()?.let(::SessionId),
        time = SessionTime(createdMs = codexMillis(thread["createdAt"]), updatedMs = codexMillis(thread["updatedAt"])),
        model = thread["model"].stringOrNull()?.let { "codex/$it" },
    )
}

public fun codexSessionStatus(thread: JsonObject): SessionStatus =
    when (codexObject(thread["status"])["type"].stringOrNull()) {
        "active" -> SessionStatus.Busy
        "idle", "notLoaded", "systemError" -> SessionStatus.Idle
        else -> codexFail(CodexFailureKind.InvalidResponse)
    }

/** One persisted or streamed item as a message; its part ids are `<itemId>:<n>`. */
public fun codexItemMessage(
    threadId: String,
    item: JsonObject,
    createdMs: Long? = null,
    completedMs: Long? = null,
    turnStatus: String? = null,
): MessageWithParts {
    val id = codexString(item["id"], max = 256)
    val type = codexString(item["type"], max = 128)
    fun part(n: Int, kind: PartType, raw: String, text: String = "", tool: ToolState? = null, name: String? = null) =
        Part(
            id = "$id:$n", messageId = id, sessionId = threadId, type = kind, rawType = raw, text = text,
            callId = if (kind == PartType.Tool) id else null, toolName = name, tool = tool,
        )
    val parts = mutableListOf<Part>()
    when (type) {
        "userMessage" ->
            codexList(item["content"], max = 1000).forEachIndexed { i, raw ->
                val value = codexObject(raw)
                val text =
                    if (value["type"].stringOrNull() == "text") codexString(value["text"], max = TEXT_MAX, empty = true)
                    else "This Codex input is available on the server."
                parts += part(i, PartType.Text, "text", text)
            }
        "agentMessage" -> parts += part(0, PartType.Text, "text", codexString(item["text"], max = TEXT_MAX, empty = true))
        "reasoning" -> {
            val summary = item["summary"]?.takeUnless { it is JsonNull }?.let { codexList(it, max = 1000) }.orEmpty()
            parts += part(0, PartType.Reasoning, "reasoning", summary.joinToString("\n") { codexString(it, max = 65_536, empty = true) })
        }
        "commandExecution" -> {
            val state = codexString(item["status"], max = 32)
            val exit = item["exitCode"]
            val status = when {
                state == "inProgress" -> ToolStatus.Running
                state == "completed" && (exit == null || exit is JsonNull || exit.intOrNull() == 0L) -> ToolStatus.Completed
                else -> ToolStatus.Error
            }
            val output = item["aggregatedOutput"].stringOrNull()?.let { codexString(it, max = TEXT_MAX, empty = true) } ?: ""
            val tool = ToolState(status, input = jsonOf("command" to codexString(item["command"], max = 65_536, empty = true)), output = output)
            parts += part(0, PartType.Tool, "tool", tool = tool, name = "shell")
        }
        "fileChange" -> {
            val text = codexList(item["changes"], max = 1000).joinToString("\n") { raw ->
                val change = codexObject(raw)
                val path = codexString(change["path"])
                val diff = change["diff"].stringOrNull()?.let { codexString(it, max = TEXT_MAX, empty = true) } ?: ""
                "$path\n$diff"
            }
            val status = when (item["status"].stringOrNull()) {
                "inProgress" -> ToolStatus.Running
                "completed" -> ToolStatus.Completed
                else -> ToolStatus.Error
            }
            parts += part(0, PartType.Tool, "tool", tool = ToolState(status, output = text), name = "patch")
        }
        else -> parts += part(0, PartType.Text, "text", "Codex recorded an item that this mobile client cannot display yet.")
    }
    return MessageWithParts(
        info = MessageInfo(
            id = id,
            sessionId = threadId,
            role = if (type == "userMessage") Role.User else Role.Assistant,
            providerId = if (type == "userMessage") null else "codex",
            time = MsgTime(createdMs, completedMs),
            finish = when (turnStatus) {
                "completed" -> "stop"
                "interrupted" -> "cancelled"
                "failed" -> "error"
                else -> null
            },
            errorText = if (turnStatus == "failed" && type != "userMessage") "The Codex turn failed. Review the server before retrying." else null,
        ),
        parts = parts.toImmutableList(),
    )
}

/** The authoritative transcript of a resumed thread; duplicate item ids are refused. */
public fun codexMessages(thread: JsonObject): List<MessageWithParts> {
    val threadId = codexString(thread["id"], max = 256)
    val result = mutableListOf<MessageWithParts>()
    val seen = HashSet<String>()
    for (rawTurn in codexList(thread["turns"])) {
        val turn = codexObject(rawTurn)
        val status = codexString(turn["status"], max = 32)
        if (status !in TURN_STATES) codexFail(CodexFailureKind.InvalidResponse)
        for (rawItem in codexList(turn["items"])) {
            val message = codexItemMessage(
                threadId, codexObject(rawItem), codexMillis(turn["startedAt"]), codexMillis(turn["completedAt"]), status,
            )
            if (!seen.add(message.info.id)) codexFail(CodexFailureKind.InvalidResponse)
            result += message
        }
    }
    return result
}

internal val TURN_STATES = setOf("inProgress", "completed", "interrupted", "failed")
