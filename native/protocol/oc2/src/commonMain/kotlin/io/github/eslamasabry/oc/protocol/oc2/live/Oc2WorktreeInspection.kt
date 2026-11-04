package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.domain.WorktreeInspectionGateway
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.protocol.oc2.wire.str
import io.ktor.http.encodeURLPathPart
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.*

/** The current OC2 read contracts; inspection never changes the shared connection location. */
internal class Oc2WorktreeInspection(private val gateway: Oc2Gateway) : WorktreeInspectionGateway {
    private fun invalid(): Outcome.Failed = Outcome.Failed(Failure.Rejected(400))

    private fun unreadable(): Outcome.Failed = Outcome.Failed(Failure.UnreadableReply())

    private fun valid(value: String): Boolean =
        value.isNotBlank() && value.length <= 4096 && value.none { it.code < 32 || it.code == 127 }

    private fun valid(location: ServerLocation): Boolean =
        location.directory?.let { valid(it) && it.startsWith('/') } == true &&
            (location.workspace?.let(::valid) != false)

    private fun data(value: JsonElement): JsonElement = (value as? JsonObject)?.get("data") ?: value

    private fun rows(value: JsonElement): JsonArray? =
        when (val body = data(value)) {
            is JsonArray -> body
            is JsonObject -> body["items"] as? JsonArray
            else -> null
        }

    private fun query(location: ServerLocation): Map<String, String?> =
        mapOf(
            "location[directory]" to location.directory,
            "location[workspace]" to location.workspace,
        )

    private suspend fun read(
        path: String,
        query: Map<String, String?> = emptyMap(),
    ): Outcome<JsonElement> {
        val before = gateway.location.value
        val result = gateway.json(path, query = query)
        return if (gateway.location.value == before) result
        else Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
    }

    override suspend fun inspectionProject(location: ServerLocation): Outcome<WorkspaceProject?> {
        if (!valid(location)) return invalid()
        var reply = read("/location", query(location))
        val project =
            when {
                reply is Outcome.Ok ->
                    (data(reply.value) as? JsonObject)?.get("project") as? JsonObject
                reply is Outcome.Failed && reply.failure is Failure.NotFound -> {
                    reply = read("/project/current", query(location))
                    (reply as? Outcome.Ok)?.value?.let { data(it) as? JsonObject }
                }
                else -> null
            }
        if (reply is Outcome.Failed) return reply
        project ?: return unreadable()
        val id = project.str("id")?.takeIf(::valid) ?: return unreadable()
        val directory =
            (project.str("directory") ?: project.str("canonical") ?: project.str("worktree"))
                ?.takeIf(::valid) ?: return unreadable()
        val copies = (project["worktrees"] ?: project["sandboxes"]) as? JsonArray
        return Outcome.Ok(
            WorkspaceProject(
                id,
                project.str("name").orEmpty(),
                directory,
                copies
                    .orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                    .toImmutableList(),
                ((project["time"] as? JsonObject)?.get("updated") as? JsonPrimitive)?.longOrNull
                    ?: 0,
                project.str("vcs")?.let { it == "git" },
            )
        )
    }

    override suspend fun inspectionWorktrees(
        projectId: String
    ): Outcome<ImmutableList<WorktreeInfo>> {
        if (!valid(projectId) || projectId in setOf(".", "..")) return invalid()
        return when (val reply = read("/worktree/${projectId.encodeURLPathPart()}")) {
            is Outcome.Failed -> reply
            is Outcome.Ok -> {
                val items = rows(reply.value) ?: return unreadable()
                if (items.size > 2000) return unreadable()
                val result = ArrayList<WorktreeInfo>()
                for (item in items) {
                    val row = item as? JsonObject ?: return unreadable()
                    val directory =
                        row.str("directory")?.takeIf { valid(it) && it.startsWith('/') }
                            ?: return unreadable()
                    result +=
                        WorktreeInfo(
                            directory.trimEnd('/').substringAfterLast('/'),
                            directory,
                            row.str("branch"),
                            true,
                        )
                }
                Outcome.Ok(result.toImmutableList())
            }
        }
    }

    override suspend fun inspectionChanges(
        location: ServerLocation
    ): Outcome<ImmutableList<VersionControlFile>> {
        if (!valid(location)) return invalid()
        return when (val reply = read("/vcs/status", query(location))) {
            is Outcome.Failed -> reply
            is Outcome.Ok -> {
                val items = rows(reply.value) ?: return unreadable()
                if (items.size > 20_000) return unreadable()
                val result = ArrayList<VersionControlFile>()
                for (item in items) {
                    val row = item as? JsonObject ?: return unreadable()
                    val path =
                        (row.str("file") ?: row.str("path"))?.takeIf(::valid) ?: return unreadable()
                    fun count(key: String): Int? {
                        val field = row[key] ?: return 0
                        return (field as? JsonPrimitive)?.intOrNull?.takeIf { it >= 0 }
                    }
                    result +=
                        VersionControlFile(
                            path,
                            row.str("status").orEmpty(),
                            count("additions") ?: return unreadable(),
                            count("deletions") ?: return unreadable(),
                        )
                }
                Outcome.Ok(result.toImmutableList())
            }
        }
    }

    override suspend fun inspectionSessions(
        cursor: String?,
        limit: Int,
        includeArchived: Boolean,
    ): Outcome<ServerPage<Session>> {
        if (limit !in 1..200 || cursor?.let { !valid(it) } == true) return invalid()
        val query =
            if (cursor != null) mapOf("cursor" to cursor)
            else mapOf("limit" to limit.toString(), "order" to "desc", "parentID" to "null")
        return when (val reply = read("/session", query)) {
            is Outcome.Failed -> reply
            is Outcome.Ok -> {
                val items = rows(reply.value) ?: return unreadable()
                if (items.size > 2000) return unreadable()
                val sessions = ArrayList<Session>()
                for (item in items) {
                    val session =
                        (attempt({ Failure.UnreadableReply() }) { gateway.sessionValue(data(item)) }
                                as? Outcome.Ok)
                            ?.value ?: return unreadable()
                    if (includeArchived || (session.time?.archivedMs ?: 0) <= 0) sessions += session
                }
                val root = reply.value as? JsonObject
                val next =
                    (root?.get("cursor") as? JsonObject)?.str("next")
                        ?: root?.str("next")
                        ?: root?.str("nextCursor")
                Outcome.Ok(ServerPage(sessions.toImmutableList(), next?.takeIf { it.isNotEmpty() }))
            }
        }
    }
}
