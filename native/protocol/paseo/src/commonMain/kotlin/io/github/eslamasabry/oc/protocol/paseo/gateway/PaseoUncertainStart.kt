package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.objectOrNull
import io.github.eslamasabry.oc.protocol.paseo.paseoList
import io.github.eslamasabry.oc.protocol.paseo.paseoMillis
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.serialization.json.JsonObject

/**
 * A first message whose create request got no answer (a cold daemon past its 90 s, or a dropped link) may or may not
 * have started an agent (B-AGENT-188; port of Dart `gateway/uncertain_start.dart`). The draft stays uncertain until a
 * read settles it: the daemon's agent whose first prompt carries this message's id becomes the draft's agent; with
 * none, the draft is free to send again. Without this every retry failed at once and the chat could never recover.
 */
internal data class PaseoUncertainStart(val messageId: String, val provider: String, val sentAtMs: Long)

/** Whether the draft [id] became a daemon conversation after all (lane-confined). */
internal suspend fun PaseoGateway.draftStarted(id: String): Boolean = id in uncertain && adoptUncertainDraft(id)

/**
 * Whether a send to [id] must wait for a reload. An uncertain draft that started nothing may send again; one that did
 * reloads first, so its first message is never sent twice.
 */
internal suspend fun PaseoGateway.sendIsUncertain(id: String): Boolean =
    id in uncertain && (id !in drafts || adoptUncertainDraft(id))

/**
 * Settles the uncertain draft [id]. True when the daemon's agent was found and adopted (the draft is now that
 * conversation); false when nothing was started, and the draft may send again.
 */
internal suspend fun PaseoGateway.adoptUncertainDraft(id: String): Boolean {
    val start = uncertainStarts.remove(id)
    if (start == null) {
        uncertain.remove(id)
        return false
    }
    val scope = scopeDir
    val epoch = locationEpoch
    val result = transport.request(
        "fetch_agents_request",
        jsonOf(
            "sort" to listOf(mapOf("key" to "updated_at", "direction" to "desc")),
            "page" to mapOf("limit" to 20),
        ),
    )
    checkLocation(scope, epoch)
    for (raw in paseoList(result["entries"], max = 200)) {
        val entry = raw.objectOrNull()?.get("agent").objectOrNull() ?: continue
        val realId = entry.str("id")
        val created = paseoMillis(entry["createdAt"])
        // Only an agent no conversation here owns yet, started for this folder and runtime around the send (ten
        // minutes allows for clocks).
        if (realId == null || realId in appIds || entry.str("cwd") != scope || entry.str("provider") != start.provider ||
            created == null || created < start.sentAtMs - 600_000
        ) {
            continue
        }
        val timeline = transport.request(
            "fetch_agent_timeline_request",
            jsonOf("agentId" to realId, "direction" to "tail", "limit" to 0, "projection" to "projected"),
            timeoutMs = 45_000,
        )
        checkLocation(scope, epoch)
        if (!startedBy(timeline, start.messageId)) continue
        realIds[id] = realId
        appIds[realId] = id
        if (remember(entry) == null) {
            realIds.remove(id)
            appIds.remove(realId)
            continue
        }
        if (entry.str("status") != "error" && entry.str("status") != "closed") liveAgentSessions += id
        drafts.remove(id)
        draftProviders.remove(id)
        draftModels.remove(id)
        draftVariants.remove(id)
        return true
    }
    uncertain.remove(id)
    return false
}

/** Whether the first user message of [timeline] is [messageId]. */
private fun startedBy(timeline: JsonObject, messageId: String): Boolean {
    for (entry in paseoList(timeline["entries"], max = 20_000)) {
        val item = entry.objectOrNull()?.get("item").objectOrNull() ?: continue
        if (item.str("type") != "user_message") continue
        return item.str("messageId") == messageId || item.str("clientMessageId") == messageId
    }
    return false
}
