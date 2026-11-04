package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.protocol.paseo.*
import io.github.eslamasabry.oc.protocol.paseo.mappers.paseoTimeline
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The helper's read-only child sessions and the task parts which open them. */
internal class PaseoSubagents(private val g: PaseoGateway) {
    private data class Child(val parentReal: String, val id: String)

    private val children = LinkedHashMap<String, Child>()
    private val byCall = LinkedHashMap<String, String>()
    private val shown = LinkedHashMap<String, Part>()

    fun contains(id: String): Boolean = id in children

    fun clear() {
        children.clear()
        byCall.clear()
        shown.clear()
    }

    fun forget(id: String) {
        val remove =
            children.filter { it.key == id || g.app(it.value.parentReal) == id }.keys.toList()
        for (key in remove) {
            children.remove(key)
            g.sessions.remove(key)
            g.statuses.remove(key)
            g.live.remove(key)
        }
        byCall.entries.removeAll { it.value in remove }
        shown.entries.removeAll { it.value.sessionId == id || it.value.sessionId in remove }
    }

    fun linked(part: Part): Part {
        val call = part.callId ?: return part
        val tool = part.tool ?: return part
        shown[call] = part
        while (shown.size > 2048) shown.remove(shown.keys.first())
        val child = byCall[call] ?: return part
        return part.copy(
            tool =
                tool.copy(
                    metadata = (tool.metadata ?: jsonOf()).with("sessionId", JsonPrimitive(child))
                )
        )
    }

    fun linkMessages(messages: List<MessageWithParts>): ImmutableList<MessageWithParts> =
        messages.map { it.copy(parts = it.parts.map(::linked).toImmutableList()) }.toImmutableList()

    fun push(payload: JsonObject) {
        when (payload.str("kind")) {
            "upsert" -> payload.obj("subagent")?.let(::upsert)
            "remove" ->
                payload.str("subagentId")?.let { raw ->
                    val id = "sub_$raw"
                    if (!contains(id)) return
                    forget(id)
                    g.emit(GatewayEvent.SessionDeleted(SessionId(id)))
                }
            "timeline" -> {
                val raw = payload.str("subagentId") ?: return
                val id = "sub_$raw"
                val child = children[id] ?: return
                if (payload.str("parentAgentId") != child.parentReal) return
                val item = payload.obj("item") ?: return
                val live = g.live.getOrPut(id) { PaseoLive() }
                val seq = payload["seq"].longOrNull()
                val epoch = payload.str("epoch")
                if (seq != null && epoch != null) {
                    if (live.epoch == epoch && seq <= live.lastSeq) return
                    live.epoch = epoch
                    live.lastSeq = seq
                }
                g.onTimelineItem(
                    id,
                    live,
                    item,
                    payload.str("provider").orEmpty(),
                    seq,
                    paseoMillis(payload["timestamp"]),
                )
            }
        }
    }

    private fun upsert(descriptor: JsonObject) {
        val raw = descriptor.str("id")?.takeIf { it.isNotEmpty() && it.length <= 256 } ?: return
        val parentReal = descriptor.str("parentAgentId") ?: return
        val parent = g.app(parentReal)
        if (parent !in g.agents && parent !in g.sessions) return
        val id = "sub_$raw"
        val known = contains(id)
        children[id] = Child(parentReal, raw)
        while (children.size > 500) forget(children.keys.first())
        val nested = descriptor.str("parentSubagentId")?.takeIf { "sub_$it" in children }
        val session =
            Session(
                id = SessionId(id),
                title =
                    (descriptor.str("title")?.takeIf { it.isNotBlank() }
                            ?: descriptor.str("description"))
                        ?.take(4096),
                parentId = SessionId(nested?.let { "sub_$it" } ?: parent),
                directory = g.sessions[parent]?.directory ?: g.directory,
                time =
                    SessionTime(
                        createdMs = paseoMillis(descriptor["createdAt"]),
                        updatedMs = paseoMillis(descriptor["updatedAt"]),
                    ),
                selection = g.sessions[parent]?.selection,
            )
        g.sessions[id] = session
        g.emit(GatewayEvent.SessionUpserted(session))
        val status =
            if (descriptor.str("status") == "running") SessionStatus.Busy else SessionStatus.Idle
        val previous = g.statuses[id]
        g.emitStatus(id, status)
        if (known && previous != status && status == SessionStatus.Idle)
            g.emit(GatewayEvent.RunEnded(SessionId(id), RunOutcome.Succeeded))
        descriptor
            .str("toolCallId")
            ?.takeIf { it.isNotEmpty() }
            ?.let { call ->
                val old = byCall.put(call, id)
                if (old != id) shown[call]?.let { g.emit(GatewayEvent.PartUpserted(linked(it))) }
            }
    }

    suspend fun load(parent: String) {
        if (parent in g.drafts || contains(parent)) return
        val scope = g.scopeDir
        val epoch = g.locationEpoch
        try {
            val response =
                g.transport.request(
                    "agent.provider_subagents.list.request",
                    jsonOf("parentAgentId" to g.real(parent)),
                    timeoutMs = 8_000,
                )
            g.checkLocation(scope, epoch)
            for (raw in paseoList(response["subagents"], max = 500)) raw.objectOrNull()
                ?.let(::upsert)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: PaseoException) {
            /* Older daemons may not have a child inventory; no child is invented. */
        }
    }

    fun session(id: String): Session? = g.sessions[id]?.takeIf { contains(id) }

    suspend fun messages(id: String): ImmutableList<MessageWithParts> {
        val child = children[id] ?: paseoFail(PaseoFailureKind.NotFound)
        val scope = g.scopeDir
        val epoch = g.locationEpoch
        val response =
            g.transport.request(
                "agent.provider_subagents.timeline.get.request",
                jsonOf(
                    "parentAgentId" to child.parentReal,
                    "subagentId" to child.id,
                    "direction" to "tail",
                    "limit" to 200,
                ),
                timeoutMs = 45_000,
            )
        g.checkLocation(scope, epoch)
        if (response["error"] != null && response["error"] != kotlinx.serialization.json.JsonNull)
            paseoFail(PaseoFailureKind.Unavailable)
        val provider = response["provider"]
        val rows =
            paseoList(response["rows"], max = 20_000).mapNotNull { row ->
                row.objectOrNull()?.let {
                    if (provider == null) it else it.with("provider", provider)
                }
            }
        val mapped =
            paseoTimeline(
                id,
                jsonOf("entries" to JsonArray(rows)),
                g.statuses[id] == SessionStatus.Busy,
            )
        val live = g.live.getOrPut(id) { PaseoLive() }
        live.announced.clear()
        live.runId = null
        live.runType = null
        for (message in mapped.messages) live.announced[message.info.id] =
            StringBuilder(message.parts.firstOrNull()?.text.orEmpty())
        response.str("epoch")?.let { live.epoch = it }
        response.obj("endCursor")?.get("seq")?.longOrNull()?.let { live.lastSeq = it }
        return linkMessages(mapped.messages)
    }

    suspend fun children(parent: SessionId): Outcome<ImmutableList<Session>> =
        withContext(g.lane) {
            paseoAttempt {
                val id = g.app(parent.raw)
                if (id !in g.agents && !contains(id)) g.fetchAgent(id)
                load(id)
                g.sessions.values.filter { it.parentId?.raw == id }.toImmutableList()
            }
        }
}
