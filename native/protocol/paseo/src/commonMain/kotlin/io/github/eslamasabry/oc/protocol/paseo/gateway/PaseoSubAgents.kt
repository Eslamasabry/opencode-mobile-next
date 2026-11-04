package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.longOrNull
import io.github.eslamasabry.oc.protocol.paseo.mappers.paseoTimeline
import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.objectOrNull
import io.github.eslamasabry.oc.protocol.paseo.paseoList
import io.github.eslamasabry.oc.protocol.paseo.paseoMillis
import io.github.eslamasabry.oc.protocol.paseo.paseoText
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Claude Code's sub-agents (its Agent tool) as the helper reports them (`agent.provider_subagents.*`, Paseo 0.9), a
 * port of lib/paseo/gateway/subagents.dart (B-AGENT-165): each one is a read-only child session of the conversation
 * that started it, with its own timeline, so the chat's sub-agent card opens it the way an OpenCode task opens its
 * child session (the card's tool metadata gains `sessionId`).
 */

/** The session id of the helper's sub-agent [subagentId]. */
public fun paseoSubagentSessionId(subagentId: String): String = "sub_$subagentId"

internal class PaseoSubagent(val parentReal: String, val subagentId: String)

/** Sub-agent bookkeeping of one gateway (lane-confined). */
internal class PaseoSubagentBook {
    val subagents = LinkedHashMap<String, PaseoSubagent>()

    /** Which sub-agent session a tool call started. */
    val byCall = LinkedHashMap<String, String>()

    /** Tool parts shown so far, by call id, so a sub-agent reported after its card still links to it. */
    val toolParts = LinkedHashMap<String, Part>()

    /** Parents whose sub-agent list was read beside their history. */
    val loaded = HashSet<String>()

    fun clear() {
        subagents.clear()
        byCall.clear()
        toolParts.clear()
        loaded.clear()
    }
}

internal fun PaseoGateway.isSubagent(id: String): Boolean = id in subagentBook.subagents

/** One `agent.provider_subagents.update` push. */
internal fun PaseoGateway.onSubagentUpdate(payload: JsonObject) {
    when (payload.str("kind")) {
        "upsert" -> payload.obj("subagent")?.let(::upsertSubagent)
        "timeline" -> {
            val subagentId = payload.str("subagentId") ?: return
            val item = payload.obj("item") ?: return
            val id = paseoSubagentSessionId(subagentId)
            if (id !in subagentBook.subagents) return
            onStream(
                id,
                jsonOf("type" to "timeline", "provider" to payload["provider"], "item" to item),
                payload,
            )
        }
        "remove" -> {
            val id = paseoSubagentSessionId(payload.str("subagentId") ?: return)
            if (subagentBook.subagents.remove(id) == null) return
            sessions.remove(id)
            statuses.remove(id)
            live.remove(id)
            subagentBook.byCall.entries.removeAll { it.value == id }
            emit(GatewayEvent.SessionDeleted(SessionId(id)))
        }
    }
}

/** Records a sub-agent [descriptor] of a conversation in this folder and announces it as that conversation's child. */
internal fun PaseoGateway.upsertSubagent(descriptor: JsonObject) {
    val subagentId = descriptor.str("id") ?: return
    val parentReal = descriptor.str("parentAgentId") ?: return
    val parentApp = app(parentReal)
    if (parentApp !in agents && parentApp !in sessions) return
    val id = paseoSubagentSessionId(subagentId)
    subagentBook.subagents[id] = PaseoSubagent(parentReal, subagentId)
    // A sub-agent started by another sub-agent is that one's child.
    val nested = descriptor.str("parentSubagentId")?.takeIf { it.isNotEmpty() }
    val parentId = nested?.let(::paseoSubagentSessionId) ?: parentApp
    val title = listOfNotNull(descriptor.str("title"), descriptor.str("description")).map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: "Sub-agent"
    val session = Session(
        id = SessionId(id),
        title = paseoText(JsonPrimitive(title), max = 4096),
        parentId = SessionId(parentId),
        directory = directoryValue,
        time = SessionTime(createdMs = paseoMillis(descriptor["createdAt"]), updatedMs = paseoMillis(descriptor["updatedAt"])),
        // It works with its main conversation's model; nothing to choose.
        selection = sessions[parentApp]?.selection,
    )
    sessions[id] = session
    emit(GatewayEvent.SessionUpserted(session))
    val known = statuses[id] != null
    val status = if (descriptor.str("status") == "running") SessionStatus.Busy else SessionStatus.Idle
    if (statuses[id] != status) {
        emitStatus(id, status)
        if (status == SessionStatus.Idle && known) emit(GatewayEvent.RunEnded(SessionId(id), io.github.eslamasabry.oc.core.domain.events.RunOutcome.Succeeded))
    }
    val callId = descriptor.str("toolCallId")?.takeIf { it.isNotEmpty() } ?: return
    val before = subagentBook.byCall[callId]
    subagentBook.byCall[callId] = id
    // The card already shown for the call that started it now opens it.
    val shown = subagentBook.toolParts[callId]
    if (before != id && shown != null) emit(GatewayEvent.PartUpserted(linkedToolPart(shown)))
}

/** [part] with the sub-agent its call started (`metadata.sessionId`), for the card's Open. */
internal fun PaseoGateway.linkedToolPart(part: Part): Part {
    val callId = part.callId ?: return part
    val child = subagentBook.byCall[callId] ?: return part
    val tool = part.tool ?: return part
    val metadata = JsonObject(LinkedHashMap(tool.metadata ?: JsonObject(emptyMap())).apply { put("sessionId", JsonPrimitive(child)) })
    return part.copy(tool = tool.copy(metadata = metadata))
}

/** Remembers a tool part shown in its session and answers it linked to its sub-agent when one is known. */
internal fun PaseoGateway.rememberAndLink(part: Part): Part {
    val callId = part.callId
    if (part.type != PartType.Tool || callId.isNullOrEmpty()) return part
    val book = subagentBook
    book.toolParts.remove(callId)
    book.toolParts[callId] = part
    while (book.toolParts.size > 2048) book.toolParts.remove(book.toolParts.keys.first())
    return linkedToolPart(part)
}

/** [messages] with their sub-agent cards linked, each tool part remembered for later links. */
internal fun PaseoGateway.linkMessages(messages: List<MessageWithParts>): ImmutableList<MessageWithParts> =
    messages.map { m -> if (m.parts.none { it.type == PartType.Tool }) m else m.copy(parts = m.parts.map(::rememberAndLink).toImmutableList()) }
        .toImmutableList()

/** The sub-agents the helper knows for [parentId] (an old conversation's too), read beside its history. */
internal fun PaseoGateway.loadSubagents(parentId: String) {
    if (parentId in drafts || !subagentBook.loaded.add(parentId)) return
    scope.launch {
        try {
            val result = transport.request("agent.provider_subagents.list.request", jsonOf("parentAgentId" to real(parentId)), timeoutMs = 8_000)
            for (raw in paseoList(result["subagents"], max = 500)) raw.objectOrNull()?.let(::upsertSubagent)
        } catch (e: CancellationException) {
            throw e
        } catch (_: PaseoException) {
            // An older helper, or none yet: the cards stay as they are.
            subagentBook.loaded.remove(parentId)
        }
    }
}

/** A sub-agent's own conversation, read from the helper. */
internal suspend fun PaseoGateway.readSubagentMessages(id: String): ImmutableList<MessageWithParts> {
    val subagent = subagentBook.subagents.getValue(id)
    val result = transport.request(
        "agent.provider_subagents.timeline.get.request",
        jsonOf("parentAgentId" to subagent.parentReal, "subagentId" to subagent.subagentId, "direction" to "tail", "limit" to 200),
        timeoutMs = PaseoGateway.HISTORY_TIMEOUT_MS,
    )
    val provider = result["provider"]
    val rows = paseoList(result["rows"], max = 20_000).mapNotNull { row ->
        row.objectOrNull()?.let { if (provider != null) JsonObject(LinkedHashMap(it).apply { put("provider", provider) }) else it }
    }
    val timeline = paseoTimeline(id, jsonOf("entries" to rows), busy = statuses[id] == SessionStatus.Busy)
    val state = live.getOrPut(id) { PaseoLive() }
    state.announced.clear()
    state.runId = null
    state.runType = null
    for (message in timeline.messages) state.announced[message.info.id] = StringBuilder(message.parts.firstOrNull()?.text.orEmpty())
    result.str("epoch")?.let { state.epoch = it }
    result.obj("endCursor")?.get("seq")?.longOrNull()?.let { state.lastSeq = it }
    return linkMessages(timeline.messages)
}

/** The sub-agent sessions of [parent] this gateway knows. */
internal fun PaseoGateway.subagentChildren(parent: String): ImmutableList<Session> =
    subagentBook.subagents.keys.mapNotNull { sessions[it] }.filter { it.parentId?.raw == parent }.toImmutableList()
