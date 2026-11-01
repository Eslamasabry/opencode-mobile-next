package io.github.eslamasabry.oc.protocol.paseo.mappers

import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.toJson
import kotlinx.serialization.json.JsonObject

/** Port of the Dart `agentJson` helper (test/paseo_gateway_test.dart) for pure mapper tests. */
fun mapperAgent(
    id: String,
    cwd: String = "/work/app",
    status: String = "idle",
    loaded: Boolean = true,
    updatedAt: String? = "2026-09-19T03:35:31.806Z",
    provider: String = "claude",
    title: String? = "Agent $id",
): JsonObject {
    val map = linkedMapOf<String, Any?>(
        "id" to id,
        "provider" to provider,
        "persistence" to mapOf("provider" to provider, "sessionId" to "native-$id"),
        "cwd" to cwd,
        "model" to "claude-haiku-4-5",
        "title" to title,
        "status" to status,
        "createdAt" to "2026-09-19T03:35:27.840Z",
        "currentModeId" to "default",
        "lastUsage" to mapOf("totalCostUsd" to 0.04),
    )
    if (updatedAt != null) map["updatedAt"] = updatedAt
    if (loaded) {
        map["activeTurn"] = if (status == "running") mapOf("turnId" to "turn-$id", "startedAt" to updatedAt) else null
    }
    return toJson(map) as JsonObject
}

/** One timeline entry (Dart `_entry`). */
fun entry(item: JsonObject, seq: Int, provider: String = "claude", turnId: String = "turn-1"): JsonObject =
    jsonOf(
        "provider" to provider,
        "item" to item,
        "timestamp" to "2026-10-10T10:00:0$seq.000Z",
        "seqStart" to seq,
        "turnId" to turnId,
    )

fun item(vararg pairs: Pair<String, Any?>): JsonObject = jsonOf(*pairs)

/** The single tool part's state of a mapped item. */
val MessageWithParts.toolState: ToolState get() = parts.single().tool!!

/** Dart ToolState.declined. */
val ToolState.declined: Boolean
    get() = status == io.github.eslamasabry.oc.core.model.transcript.ToolStatus.Error &&
        (output ?: "").lowercase().let { "user declined" in it || "user rejected permission" in it }
