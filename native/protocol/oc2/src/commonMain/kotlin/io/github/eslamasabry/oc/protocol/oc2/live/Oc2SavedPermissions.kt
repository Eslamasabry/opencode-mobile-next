package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.model.SavedPermission
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.*

/**
 * Flutter ops_sessions.dart's current-project saved grants; all requests retain the gateway's
 * location.
 */
internal suspend fun Oc2Gateway.savedGrants(): Outcome<ImmutableList<SavedPermission>> {
    val at = location.value
    val project = json("/project/current", query = loc(at))
    if (project is Outcome.Failed) return project
    val rawProject = (project as Outcome.Ok).value
    val obj = (rawProject as? JsonObject)?.let { (it["data"] as? JsonObject) ?: it }
    val id = (obj?.get("id") as? JsonPrimitive)?.contentOrNull
    val reply =
        json(
            "/permission/saved",
            query = loc(at, mapOf("projectID" to id?.takeIf(String::isNotBlank))),
        )
    if (reply is Outcome.Failed) return reply
    val raw = (reply as Outcome.Ok).value
    val data = (raw as? JsonObject)?.get("data") ?: raw
    val list = data as? JsonArray ?: return Failure.UnreadableReply().failed()
    val grants =
        list
            .mapNotNull { item ->
                val row = item as? JsonObject ?: return@mapNotNull null
                fun value(name: String): String =
                    (row[name] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val key = value("id").takeIf(String::isNotEmpty) ?: return@mapNotNull null
                SavedPermission(key, value("projectID"), value("action"), value("resource"))
            }
            .sortedWith(compareBy<SavedPermission> { it.action }.thenBy { it.resource })
            .toImmutableList()
    return grants.ok()
}

internal suspend fun Oc2Gateway.revokeGrant(id: String): Outcome<Unit> {
    if (id.isBlank()) return Failure.Refused(RefusalReason.InvalidAddress).failed()
    return json(
            "/permission/saved/${id.encodeURLPathPart()}",
            HttpMethod.Delete,
            query = loc(location.value),
        )
        .map {}
}
