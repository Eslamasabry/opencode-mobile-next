package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.SkillInfo
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.*

internal object Oc2SessionSkills {
    /** Skills are location-scoped: without the query the server lists its default project's. */
    suspend fun list(
        gateway: Oc2Gateway,
        location: Map<String, String?>,
    ): Outcome<ImmutableList<SkillInfo>> {
        val read = gateway.json("/skill", query = location)
        if (read is Outcome.Failed) return read
        val body = (read as Outcome.Ok).value
        val rows =
            ((body as? JsonObject)?.get("data") ?: body) as? JsonArray
                ?: return Failure.UnreadableReply().failed()
        return rows
            .mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                fun text(key: String) =
                    (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
                val id = text("id") ?: text("name") ?: return@mapNotNull null
                SkillInfo(
                    id,
                    text("name") ?: id,
                    text("description"),
                    text("location").orEmpty(),
                    "",
                    false,
                )
            }
            .toImmutableList()
            .ok()
    }

    suspend fun activate(
        gateway: Oc2Gateway,
        session: SessionId,
        skill: String,
        resume: Boolean,
    ): Outcome<Unit> =
        gateway
            .json(
                "/session/" + session.raw.encodeURLPathPart() + "/skill",
                HttpMethod.Post,
                buildJsonObject {
                    put("skill", skill)
                    put("resume", resume)
                },
            )
            .map {}

    fun unsupported(result: Outcome<Unit>): Boolean {
        val f = (result as? Outcome.Failed)?.failure
        return (f is Failure.NotFound &&
            f.tag?.raw !in listOf("SessionNotFoundError", "SkillNotFoundError")) ||
            (f is Failure.Rejected && f.status in listOf(405, 501)) ||
            (f is Failure.Server && f.status == 501)
    }
}
