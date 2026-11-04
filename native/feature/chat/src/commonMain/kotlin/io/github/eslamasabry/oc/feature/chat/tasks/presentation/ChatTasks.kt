package io.github.eslamasabry.oc.feature.chat.tasks.presentation

import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

public enum class ChatTaskStatus {
    Pending,
    InProgress,
    Completed,
    Cancelled,
}

public data class ChatTask(val text: String, val status: ChatTaskStatus)

public data class ChatTaskList(val tasks: List<ChatTask>) {
    val done: Int
        get() = tasks.count { it.status == ChatTaskStatus.Completed }

    val total: Int
        get() = tasks.count { it.status != ChatTaskStatus.Cancelled }
}

/**
 * The newest todo tool owns the plan; an invalid or all-cancelled replacement hides the old one.
 */
public fun latestChatTasks(messages: List<MessageWithParts>): ChatTaskList? {
    for (message in messages.asReversed()) {
        if (message.info.role != Role.Assistant) continue
        for (part in message.parts.asReversed()) {
            if (part.type != PartType.Tool || part.toolName !in setOf("todowrite", "todo")) continue
            val tool = part.tool ?: return null
            val todos =
                (tool.metadata?.get("todos") ?: tool.input["todos"]) as? JsonArray ?: return null
            if (todos.isEmpty() || todos.size > 64) return null
            var totalText = 0
            val tasks = ArrayList<ChatTask>()
            for (raw in todos) {
                val item = raw as? JsonObject ?: return null
                fun word(key: String): String? =
                    (item[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
                val text = word("content") ?: return null
                totalText += text.length
                if (text.isBlank() || text.length > 1024 || totalText > 32768) return null
                val status =
                    when (word("status")) {
                        "pending" -> ChatTaskStatus.Pending
                        "in_progress" -> ChatTaskStatus.InProgress
                        "completed" -> ChatTaskStatus.Completed
                        "cancelled" -> ChatTaskStatus.Cancelled
                        else -> return null
                    }
                if (
                    item["priority"] != null &&
                        item["priority"] != JsonNull &&
                        word("priority") !in setOf("high", "medium", "low")
                )
                    return null
                tasks += ChatTask(text, status)
            }
            return ChatTaskList(tasks).takeIf { it.total > 0 }
        }
    }
    return null
}
