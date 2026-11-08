package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.core.model.BackgroundWorkSupport
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlinx.serialization.json.JsonPrimitive

/**
 * S30 story 3, Prompt tools › Background (Flutter `background_work.dart` `foregroundBackgroundableParts`): the running
 * work of an unfinished reply that the server can move to the background. A subagent task always; a shell or a
 * subagent step too where the server backgrounds shells. Work already in the background is left out.
 */
public object BackgroundWork {
    public fun parts(messages: List<MessageWithParts>, support: BackgroundWorkSupport): List<Part> {
        if (support == BackgroundWorkSupport.Unavailable) return emptyList()
        return messages
            .filter { it.info.role == Role.Assistant && it.info.time?.completedMs == null }
            .flatMap { it.parts }
            .filter { part ->
                val tool = part.tool
                part.type == PartType.Tool &&
                    tool != null &&
                    tool.status == ToolStatus.Running &&
                    tool.ran &&
                    !flag(tool.metadata?.get("background")) &&
                    !flag(tool.input["background"]) &&
                    (part.toolName == "task" ||
                        (support == BackgroundWorkSupport.SubagentsAndShells &&
                            (part.toolName == "shell" || part.toolName == "subagent")))
            }
    }

    /** One part's identity across reads (Flutter `_backgroundPartKey`). */
    public fun key(part: Part): String = "${part.messageId}/${part.id}"

    private fun flag(value: kotlinx.serialization.json.JsonElement?): Boolean =
        (value as? JsonPrimitive)?.takeIf { !it.isString }?.content == "true"
}
