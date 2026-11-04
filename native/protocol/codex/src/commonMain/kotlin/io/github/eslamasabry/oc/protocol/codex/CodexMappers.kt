package io.github.eslamasabry.oc.protocol.codex

import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.model.transcript.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.json.*

internal fun JsonObject.millis(key: String): Long? {
    val raw = this[key] ?: return null
    if (raw == JsonNull) return null
    val value = (raw as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull ?: malformed()
    if (value !in 0..8_640_000_000_000L) malformed()
    return value * 1000L
}

internal fun codexSession(thread: JsonObject, directory: String): Session {
    val cwd = thread["cwd"].str()
    if (cwd != directory) stale()
    return Session(
        id = SessionId(thread["id"].str(256)),
        title = thread.text("name")?.takeIf { it.isNotBlank() },
        directory = cwd,
        parentId = thread.text("parentThreadId")?.let(::SessionId),
        projectId = thread.text("projectId")?.let(::ProjectId),
        time =
            SessionTime(
                createdMs = thread.millis("createdAt"),
                updatedMs = thread.millis("updatedAt"),
            ),
        model = thread.text("model")?.let { "codex/$it" },
    )
}

internal fun codexStatus(thread: JsonObject): SessionStatus =
    when (thread["status"].obj()["type"].str(64)) {
        "active" -> SessionStatus.Busy
        "idle",
        "notLoaded",
        "systemError" -> SessionStatus.Idle
        else -> malformed()
    }

internal fun codexMessages(thread: JsonObject): ImmutableList<MessageWithParts> {
    val session = thread["id"].str(256)
    val rows = mutableListOf<MessageWithParts>()
    val ids = mutableSetOf<String>()
    for (raw in thread["turns"].list()) {
        val turn = raw.obj()
        val status = turn["status"].str(32)
        if (status !in setOf("inProgress", "completed", "interrupted", "failed")) malformed()
        for (rawItem in turn["items"].list()) {
            val item = rawItem.obj()
            if (!ids.add(item["id"].str(256))) malformed()
            rows +=
                codexItem(
                    session,
                    item,
                    turn.millis("startedAt"),
                    turn.millis("completedAt"),
                    status,
                )
        }
    }
    return rows.toImmutableList()
}

internal fun codexItem(
    session: String,
    item: JsonObject,
    created: Long? = null,
    completed: Long? = null,
    turnStatus: String? = null,
): MessageWithParts {
    val id = item["id"].str(256)
    val type = item["type"].str(128)
    val parts = mutableListOf<Part>()
    fun text(value: String, index: Int = 0, reasoning: Boolean = false) {
        parts +=
            Part(
                "$id:$index",
                id,
                session,
                if (reasoning) PartType.Reasoning else PartType.Text,
                if (reasoning) "reasoning" else "text",
                value,
            )
    }
    fun tool(name: String, input: JsonObject, output: String?, status: ToolStatus) {
        parts +=
            Part(
                "$id:0",
                id,
                session,
                PartType.Tool,
                "tool",
                callId = id,
                toolName = name,
                tool = ToolState(status, input = input, output = output),
            )
    }
    when (type) {
        "userMessage" ->
            item["content"].list(1000).forEachIndexed { index, raw ->
                val value = raw.obj()
                when (value["type"].str(128)) {
                    "text" -> text(value["text"].str(1024 * 1024, true), index)
                    "image",
                    "localImage" ->
                        parts +=
                            Part(
                                "$id:$index",
                                id,
                                session,
                                PartType.File,
                                "file",
                                mime = "image/*",
                                url = value.text("url"),
                            )
                    else ->
                        parts +=
                            Part(
                                "$id:$index",
                                id,
                                session,
                                PartType.Unknown,
                                "codex.input.${value.text("type")}",
                            )
                }
            }
        "agentMessage" -> text(item["text"].str(1024 * 1024, true))
        "reasoning" ->
            text(
                (item["summary"] ?: JsonArray(emptyList())).list(1000).joinToString("\n") {
                    it.str(65536, true)
                },
                reasoning = true,
            )
        "commandExecution" -> {
            val status = item["status"].str(32)
            val exit = (item["exitCode"] as? JsonPrimitive)?.intOrNull
            tool(
                "shell",
                json("command" to s(item["command"].str(65536, true)), "cwd" to item["cwd"]),
                item.text("aggregatedOutput"),
                when {
                    status == "inProgress" -> ToolStatus.Running
                    status == "completed" && (exit == null || exit == 0) -> ToolStatus.Completed
                    else -> ToolStatus.Error
                },
            )
        }
        "fileChange" -> {
            val changes = item["changes"].list(1000)
            val output =
                changes.joinToString("\n") { raw ->
                    val change = raw.obj()
                    change["path"].str() + "\n" + (change.text("diff") ?: "")
                }
            tool(
                "patch",
                json(),
                output,
                when (item["status"].str(32)) {
                    "inProgress" -> ToolStatus.Running
                    "completed" -> ToolStatus.Completed
                    else -> ToolStatus.Error
                },
            )
        }
        else -> parts += Part("$id:0", id, session, PartType.Unknown, "codex.$type")
    }
    return MessageWithParts(
        MessageInfo(
            id = id,
            sessionId = session,
            role = if (type == "userMessage") Role.User else Role.Assistant,
            providerId = if (type == "userMessage") null else "codex",
            time = MsgTime(created, completed),
            finish =
                when (turnStatus) {
                    "completed" -> "stop"
                    "interrupted" -> "cancelled"
                    "failed" -> "error"
                    else -> null
                },
            errorKind =
                if (turnStatus == "failed" && type != "userMessage") MessageErrorKind.Unknown
                else null,
        ),
        parts.toImmutableList(),
    )
}
