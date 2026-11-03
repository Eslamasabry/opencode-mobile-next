package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProjectId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ids.WorkspaceId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.model.GlobalSessionResult
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Session
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2UsageStatistics
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Read-only OC2 reads for the native Search and Usage screens (unit P12-SEARCH-USAGE), kept in their own file so
 * Oc2Gateway.kt is untouched. Each returns null when [gateway] is not an OC2 gateway, so the caller can say the
 * server cannot do it rather than guess.
 *
 * Both are GETs: retrying the stable `/experimental` path after a clean 404 never repeats a mutation.
 */
public object Oc2SearchUsage {
    /**
     * Conversations from every project on the server, newest first, roots only (Flutter `listGlobalSessions`:
     * `unscoped: true, rootsOnly: true, order: desc`). [search] is the server's own title search.
     */
    public suspend fun searchSessions(
        gateway: ServerGateway,
        search: String?,
        cursor: String?,
        limit: Int = 50,
        includeArchived: Boolean = false,
    ): Outcome<ServerPage<GlobalSessionResult>>? {
        val oc2 = gateway as? Oc2Gateway ?: return null
        val query =
            if (cursor != null) mapOf("cursor" to cursor)
            else
                mapOf(
                    "limit" to limit.toString(),
                    "order" to "desc",
                    "search" to search?.trim()?.takeIf { it.isNotEmpty() },
                    "parentID" to "null",
                )
        return when (val result = oc2.json("/session", query = query)) {
            is Outcome.Failed -> result
            is Outcome.Ok -> {
                val root = result.value
                val rows = rows(root) ?: return Failure.UnreadableReply().failed()
                val items =
                    rows.mapNotNull { row ->
                        val wire = Oc2Session.fromJson(data(row) as? JsonObject ?: return@mapNotNull null)
                            ?: return@mapNotNull null
                        if (!includeArchived && wire.archived) null
                        else GlobalSessionResult(session(wire), projectName = null, projectDirectory = wire.directory)
                    }
                ServerPage(items.toImmutableList(), next(root)?.takeIf { it.isNotEmpty() }).ok()
            }
        }
    }

    /**
     * Projects the server knows, for the usage project filter: the gateway's current project first (Flutter resolves it
     * directly before sending its id), then those of the newest root conversations (archived ones too). Each is an id
     * and the directory it was seen in. A current project that cannot be read leaves the conversation projects.
     */
    public suspend fun projectChoices(gateway: ServerGateway, limit: Int = 100): Outcome<List<Oc2ProjectChoice>>? {
        val page = searchSessions(gateway, null, null, limit, includeArchived = true) ?: return null
        return when (page) {
            is Outcome.Failed -> page
            is Outcome.Ok -> {
                val seen =
                    page.value.items.mapNotNull { row ->
                        val id = row.session.projectId?.raw?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        Oc2ProjectChoice(id, row.projectDirectory)
                    }
                (listOfNotNull((gateway as? Oc2Gateway)?.let { currentProject(it) }) + seen).distinctBy { it.id }.ok()
            }
        }
    }

    /**
     * The project of the gateway's location: `GET /location` (`{directory, project:{id, directory}}`), or the older
     * `GET /project/current` (a bare or `data`-wrapped project) where `/location` is absent. Null when neither answers.
     */
    private suspend fun currentProject(gateway: Oc2Gateway): Oc2ProjectChoice? {
        val here = gateway.location.value
        val query = mapOf("location[directory]" to here.directory, "location[workspace]" to here.workspace)
        val located = gateway.json("/location", query = query)
        val project =
            when {
                located is Outcome.Ok -> (data(located.value) as? JsonObject)?.get("project") as? JsonObject
                located is Outcome.Failed && located.failure is Failure.NotFound ->
                    (gateway.json("/project/current", query = query) as? Outcome.Ok)
                        ?.value
                        ?.let { data(it) as? JsonObject }
                else -> null
            } ?: return null
        fun text(key: String): String? =
            (project[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        val id = text("id") ?: return null
        return Oc2ProjectChoice(id, text("directory") ?: text("worktree"))
    }

    /**
     * `GET /api/session/stats` (beta) or `/api/experimental/session/stats` (stable). [query] is the exact wire query
     * (from, to, timezone, project, tools). A strict body that does not validate is an unreadable reply.
     */
    public suspend fun usageStatistics(
        gateway: ServerGateway,
        query: Map<String, String?>,
    ): Outcome<Oc2UsageStatistics>? {
        val oc2 = gateway as? Oc2Gateway ?: return null
        var result = oc2.json("/session/stats", query = query)
        if (result is Outcome.Failed && result.failure is Failure.NotFound) {
            result = oc2.json("/experimental/session/stats", query = query)
        }
        return when (result) {
            is Outcome.Failed ->
                if (absent(result.failure)) Failure.Unavailable(FeatureId(USAGE)).failed() else result
            is Outcome.Ok -> {
                val body = data(result.value) as? JsonObject ?: return Failure.UnreadableReply().failed()
                Oc2UsageStatistics.fromJson(body)?.ok() ?: Failure.UnreadableReply().failed()
            }
        }
    }

    /** The FeatureId an absent statistics endpoint reports. */
    public const val USAGE: String = "usageStatistics"

    private fun absent(failure: Failure): Boolean =
        failure is Failure.NotFound ||
            (failure is Failure.Rejected && (failure.status == 405 || failure.status == 501)) ||
            (failure is Failure.Server && failure.status == 501)

    private fun data(element: JsonElement): JsonElement = (element as? JsonObject)?.get("data") ?: element

    private fun rows(element: JsonElement): List<JsonElement>? =
        when (val value = data(element)) {
            is JsonArray -> value.toList()
            is JsonObject -> (value["items"] as? JsonArray)?.toList()
            else -> null
        }

    private fun next(element: JsonElement): String? {
        val obj = element as? JsonObject ?: return null
        fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        return (obj["cursor"] as? JsonObject)?.text("next") ?: obj.text("next") ?: obj.text("nextCursor")
    }

    private fun session(wire: Oc2Session): Session =
        Session(
            SessionId(wire.id),
            title = wire.title,
            projectId = wire.projectID?.let(::ProjectId),
            parentId = wire.parentID?.let(::SessionId),
            directory = wire.location?.directory,
            workspaceId = wire.location?.workspaceID?.let(::WorkspaceId),
            path = wire.subpath,
            reverted = wire.reverted,
            time =
                SessionTime(wire.time.created, wire.time.updated, wire.time.archived, wire.time.idle, wire.time.viewed),
            agent = wire.agent,
            model = wire.model?.let { "${it.providerID}/${it.id}" },
        )
}

/** One project the usage filter can offer: the server's project id and a directory it lives in, when known. */
public data class Oc2ProjectChoice(val id: String, val directory: String?)
