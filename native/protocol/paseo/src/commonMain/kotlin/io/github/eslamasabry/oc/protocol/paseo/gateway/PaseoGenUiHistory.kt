package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.arrayOrNull
import io.github.eslamasabry.oc.protocol.paseo.boolOrNull
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.longOrNull
import io.github.eslamasabry.oc.protocol.paseo.mappers.paseoTimeline
import io.github.eslamasabry.oc.protocol.paseo.objectOrNull
import io.github.eslamasabry.oc.protocol.paseo.paseoFail
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Bounded recovery history, read oldest first within a page. */
public data class PaseoHistoryPage(
    val items: List<MessageWithParts>,
    val olderCursor: String?,
    val hasMore: Boolean,
)

private fun historyRequest(sessionId: String, cursor: String?, limit: Int) {
    if (
        sessionId.isEmpty() ||
            sessionId.length > 512 ||
            limit !in 1..50 ||
            (cursor != null && (cursor.isEmpty() || cursor.length > 4096))
    ) {
        paseoFail(PaseoFailureKind.InvalidResponse)
    }
}

private fun validCursor(cursor: JsonObject): Boolean =
    cursor.str("epoch")?.let { it.isNotEmpty() && it.length <= 512 } == true &&
        cursor["seq"].longOrNull()?.let { it >= 0 } == true

/** Fresh idle proof from the private, bounded connection; a draft or child never qualifies. */
public suspend fun PaseoGateway.genUiSessionIdle(session: SessionId): Outcome<Boolean> = laneCall {
    val id = app(session.raw)
    historyRequest(id, null, 1)
    if (isSubagent(id) || id in drafts) return@laneCall false
    val scope = scopeDir
    val epoch = locationEpoch
    val realId = real(id)
    val result = transport.requestBoundedHistory(jsonOf("agentId" to realId), agentSnapshot = true)
    checkLocation(scope, epoch)
    val agent = result["agent"].objectOrNull()
    if (
        agent == null ||
            agent.str("id") != realId ||
            agent.str("cwd") != scope ||
            real(id) != realId
    ) {
        paseoFail(PaseoFailureKind.InvalidResponse)
    }
    agent.str("status") == "idle" &&
        agent["archivedAt"].let { it == null || it is kotlinx.serialization.json.JsonNull } &&
        id !in awaitingTurn &&
        id !in turnActive
}

/** A capped history page with echoed identity, stable epoch and cursor validation. */
public suspend fun PaseoGateway.genUiHistoryPage(
    session: SessionId,
    cursor: String?,
    limit: Int,
): Outcome<PaseoHistoryPage> = laneCall {
    val id = app(session.raw)
    historyRequest(id, cursor, limit)
    if (isSubagent(id) || id in drafts) paseoFail(PaseoFailureKind.Unavailable)
    val before = cursor?.let {
        val value = Json.parseToJsonElement(it).objectOrNull()
        if (value == null || !validCursor(value)) paseoFail(PaseoFailureKind.InvalidResponse)
        value
    }
    val scope = scopeDir
    val epoch = locationEpoch
    val realId = real(id)
    val result =
        transport.requestBoundedHistory(
            jsonOf(
                    "agentId" to realId,
                    "direction" to if (before == null) "tail" else "before",
                    "cursor" to before,
                    "limit" to limit,
                    "projection" to "projected",
                )
                .withoutNulls()
        )
    checkLocation(scope, epoch)
    if (realId != real(id)) paseoFail(PaseoFailureKind.StaleRequest)
    val entries = result["entries"].arrayOrNull()
    val historyEpoch = result.str("epoch")
    val more = result["hasOlder"].boolOrNull()
    val newer = result["hasNewer"].boolOrNull()
    if (
        result.str("agentId") != realId ||
            entries == null ||
            entries.size > limit ||
            entries.any { it !is JsonObject } ||
            historyEpoch.isNullOrEmpty() ||
            more == null ||
            newer == null ||
            result["staleCursor"].boolOrNull() == true ||
            result["gap"].boolOrNull() == true ||
            (before == null && newer) ||
            (before != null &&
                (result["reset"].boolOrNull() == true || historyEpoch != before.str("epoch")))
    )
        paseoFail(PaseoFailureKind.InvalidResponse)
    val next =
        if (more) {
            val start = result["startCursor"].objectOrNull()
            if (start == null || !validCursor(start) || start.str("epoch") != historyEpoch)
                paseoFail(PaseoFailureKind.InvalidResponse)
            jsonOf("epoch" to start.str("epoch"), "seq" to start["seq"]).toString().also {
                if (it == cursor) paseoFail(PaseoFailureKind.InvalidResponse)
            }
        } else null
    PaseoHistoryPage(
        paseoTimeline(id, result, busy = statuses[id] == SessionStatus.Busy).messages,
        next,
        more,
    )
}
