package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.transcript.*
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.protocol.oc2.adapter.Oc2CoreProjection
import io.github.eslamasabry.oc.protocol.oc2.adapter.Oc2EventAdapter
import io.github.eslamasabry.oc.protocol.oc2.adapter.noticePartId
import io.github.eslamasabry.oc.protocol.oc2.events.Oc2Envelope
import io.github.eslamasabry.oc.protocol.oc2.wire.*
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.*

/** Fetched history shares content part IDs and tool mapping with the live event adapter. */
internal class Oc2History(private val redactor: Redactor) {
    fun message(session: String, value: JsonElement): MessageWithParts? {
        val wire = Oc2Message.fromJson(value as? JsonObject ?: return null) ?: return null
        val assistant = wire as? Oc2Message.Assistant
        val info =
            MessageInfo(
                wire.id,
                session,
                if (wire is Oc2Message.User) Role.User else Role.Assistant,
                agent = assistant?.agent,
                providerId = assistant?.model?.providerID,
                modelId = assistant?.model?.id,
                cost = assistant?.cost ?: 0.0,
                tokens =
                    assistant?.tokens?.let {
                        Tokens(it.input, it.output, it.reasoning, it.cacheRead, it.cacheWrite)
                    } ?: Tokens(),
                time = MsgTime(wire.time.created, wire.time.completed),
                finish = assistant?.finish,
                errorText = assistant?.error?.let { redactor.redact(it.message?.takeIf(String::isNotBlank).orEmpty()) },
                errorKind = assistant?.error?.let {
                    refineMessageErrorKind(messageErrorKind(it.type) ?: MessageErrorKind.Unknown, it.message)
                },
            )
        val parts =
            if (assistant != null) {
                val content = (value as? JsonObject)?.get("content") ?: JsonArray(emptyList())
                val event =
                    Oc2Envelope.decode(
                        buildJsonObject {
                            put("type", "session.message.content.updated")
                            put(
                                "data",
                                buildJsonObject {
                                    put("sessionID", session)
                                    put("messageID", wire.id)
                                    put("content", content)
                                },
                            )
                        }
                    )
                val adapter = Oc2EventAdapter { wire.time.created ?: 0L }
                val projection = Oc2CoreProjection(redactor::details)
                adapter
                    .adapt(event)
                    .map(projection::toCore)
                    .filterIsInstance<GatewayEvent.PartUpserted>()
                    .map { it.part.copy(ended = wire.time.completed != null) }
            } else if (wire is Oc2Message.User) {
                buildList {
                    if (wire.text.isNotEmpty())
                        add(
                            Part(
                                "text-0",
                                wire.id,
                                session,
                                PartType.Text,
                                "text",
                                text = wire.text,
                                ended = true,
                            )
                        )
                    for ((index, file) in wire.files.withIndex()) {
                        val uri =
                            file.sourceUri
                                ?: file.data?.let {
                                    "data:${file.mime ?: "application/octet-stream"};base64,$it"
                                }
                        add(
                            Part(
                                "file-$index",
                                wire.id,
                                session,
                                PartType.File,
                                "file",
                                url = uri,
                                filename = file.name,
                                mime = file.mime,
                                ended = true,
                            )
                        )
                    }
                }
            } else {
                listOf(special(session, wire))
            }
        return MessageWithParts(info, parts.toImmutableList())
    }

    private fun special(session: String, wire: Oc2Message): Part {
        fun notice(type: PartType, raw: String, kind: String, text: String = ""): Part =
            Part(
                noticePartId(wire.id),
                wire.id,
                session,
                type,
                raw,
                text = text,
                ended = true,
                noticeMetadata = persistentMapOf("kind" to kind),
            )
        return when (wire) {
            is Oc2Message.Synthetic -> notice(PartType.Notice, "v2:notice", "synthetic", wire.text)
            is Oc2Message.System ->
                if (wire.description?.startsWith("Instructions updated") == true)
                    notice(PartType.Notice, "v2:notice", "instructions")
                else notice(PartType.Notice, "v2:notice", "system", wire.text)
            is Oc2Message.Skill -> notice(PartType.Notice, "v2:notice", "skill", wire.text)
            is Oc2Message.AgentSwitched -> notice(PartType.Switch, "v2:switch", "agent", wire.agent)
            is Oc2Message.ModelSwitched ->
                notice(PartType.Switch, "v2:switch", "model", wire.model?.id ?: "")
            is Oc2Message.LocationSwitched ->
                notice(
                    PartType.Switch,
                    "v2:switch",
                    "location",
                    wire.location?.directory?.substringAfterLast('/') ?: "",
                )
            is Oc2Message.Compaction ->
                notice(PartType.Compaction, "v2:compaction", wire.status, wire.summary ?: "")
            is Oc2Message.Shell -> {
                val toolStatus =
                    when {
                        wire.status == "running" -> ToolStatus.Running
                        wire.status == "exited" && (wire.exit ?: 0) == 0 -> ToolStatus.Completed
                        else -> ToolStatus.Error
                    }
                val callId = wire.shellID ?: "shell-${wire.id}"
                Part(
                    callId,
                    wire.id,
                    session,
                    PartType.Tool,
                    "tool",
                    callId = callId,
                    toolName = "shell",
                    tool =
                        ToolState(
                            toolStatus,
                            input = buildJsonObject { put("command", wire.command) },
                            output = wire.outputText,
                            metadata =
                                buildJsonObject {
                                    put("shellStatus", wire.status)
                                    wire.shellID?.let { put("shellID", it) }
                                    put("truncated", wire.outputTruncated)
                                    wire.exit?.let { put("exit", it) }
                                },
                        ),
                    ended = wire.status != "running",
                )
            }
            else -> notice(PartType.Notice, "v2:notice", "unknown")
        }
    }
}
