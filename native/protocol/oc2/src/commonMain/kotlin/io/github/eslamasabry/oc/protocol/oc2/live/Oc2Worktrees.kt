package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.model.ProjectDirectoryInfo
import io.github.eslamasabry.oc.core.model.WorkspaceProject
import io.github.eslamasabry.oc.core.model.WorktreeInfo
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.*

/** The callable OC2 project/worktree contract from Flutter gateway_operations/ops_projects.dart. */
internal class Oc2Worktrees(private val owner: Oc2Gateway) {
    private fun data(value: JsonElement): JsonElement = (value as? JsonObject)?.get("data") ?: value

    private fun text(value: JsonObject, key: String): String? =
        (value[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private fun name(directory: String): String = directory.trimEnd('/').substringAfterLast('/')

    private fun project(value: JsonElement): WorkspaceProject? {
        val row = data(value) as? JsonObject ?: return null
        val id = text(row, "id") ?: return null
        val directory = text(row, "directory") ?: text(row, "canonical") ?: return null
        val times = row["time"] as? JsonObject
        return WorkspaceProject(
            id,
            text(row, "name") ?: name(directory),
            directory,
            ((row["worktrees"] ?: row["sandboxes"]) as? JsonArray)
                .orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                .toImmutableList(),
            (times?.get("updated") as? JsonPrimitive)?.longOrNull
                ?: (times?.get("created") as? JsonPrimitive)?.longOrNull
                ?: 0,
            text(row, "vcs") == "git",
        )
    }

    suspend fun projects(): Outcome<ImmutableList<WorkspaceProject>> {
        return when (val reply = owner.json("/project")) {
            is Outcome.Failed -> reply
            is Outcome.Ok -> {
                val rows =
                    data(reply.value) as? JsonArray
                        ?: return Outcome.Failed(Failure.UnreadableReply())
                val projects = rows.map {
                    project(it) ?: return Outcome.Failed(Failure.UnreadableReply())
                }
                Outcome.Ok(projects.toImmutableList())
            }
        }
    }

    suspend fun current(): Outcome<WorkspaceProject?> {
        val at = owner.location.value
        return when (
            val reply =
                owner.json(
                    "/project/current",
                    query =
                        mapOf(
                            "location[directory]" to at.directory,
                            "location[workspace]" to at.workspace,
                        ),
                )
        ) {
            is Outcome.Failed -> reply
            is Outcome.Ok ->
                if (data(reply.value) == JsonNull) Outcome.Ok(null)
                else
                    project(reply.value)?.let { Outcome.Ok(it) }
                        ?: Outcome.Failed(Failure.UnreadableReply())
        }
    }

    private suspend fun resolve(directory: String): Outcome<String> =
        when (
            val reply = owner.json("/location", query = mapOf("location[directory]" to directory))
        ) {
            is Outcome.Failed -> reply
            is Outcome.Ok -> {
                val body = data(reply.value) as? JsonObject
                val row = body?.get("project") as? JsonObject
                val id = row?.let { text(it, "id") }
                if (id == null) Outcome.Failed(Failure.UnreadableReply()) else Outcome.Ok(id)
            }
        }

    suspend fun list(directory: String, projectId: String?): Outcome<ImmutableList<WorktreeInfo>> {
        val id =
            if (!projectId.isNullOrBlank()) projectId
            else
                when (val result = resolve(directory)) {
                    is Outcome.Failed -> return result
                    is Outcome.Ok -> result.value
                }
        return listById(id)
    }

    private suspend fun listById(id: String): Outcome<ImmutableList<WorktreeInfo>> {
        return when (val reply = owner.json("/worktree/${id.encodeURLPathPart()}")) {
            is Outcome.Failed -> reply
            is Outcome.Ok -> {
                val rows =
                    data(reply.value) as? JsonArray
                        ?: return Outcome.Failed(Failure.UnreadableReply())
                Outcome.Ok(
                    rows
                        .map { item ->
                            val row =
                                item as? JsonObject
                                    ?: return Outcome.Failed(Failure.UnreadableReply())
                            val directory =
                                text(row, "directory")
                                    ?: return Outcome.Failed(Failure.UnreadableReply())
                            WorktreeInfo(
                                text(row, "name") ?: name(directory),
                                directory,
                                text(row, "branch"),
                                (row["ready"] as? JsonPrimitive)?.booleanOrNull == true,
                            )
                        }
                        .toImmutableList()
                )
            }
        }
    }

    suspend fun directories(id: String): Outcome<ImmutableList<ProjectDirectoryInfo>> =
        when (val result = listById(id)) {
            is Outcome.Failed -> result
            is Outcome.Ok ->
                Outcome.Ok(
                    result.value.map { ProjectDirectoryInfo(it.directory, null) }.toImmutableList()
                )
        }

    suspend fun create(directory: String, requestedName: String?): Outcome<WorktreeInfo> {
        val id =
            when (val resolved = resolve(directory)) {
                is Outcome.Failed -> return resolved
                is Outcome.Ok -> resolved.value
            }
        val body = buildJsonObject {
            requestedName?.trim()?.takeIf { it.isNotEmpty() }?.let { put("name", it) }
        }
        return when (
            val result = owner.json("/worktree/${id.encodeURLPathPart()}", HttpMethod.Post, body)
        ) {
            is Outcome.Failed -> Outcome.Failed(Failure.Refused(RefusalReason.DispatchUncertain))
            is Outcome.Ok -> {
                val row = data(result.value) as? JsonObject
                val path = row?.let { text(it, "directory") }
                if (path == null) Outcome.Failed(Failure.Refused(RefusalReason.DispatchUncertain))
                else
                    Outcome.Ok(
                        WorktreeInfo(
                            requestedName?.trim()?.takeIf { it.isNotEmpty() } ?: name(path),
                            path,
                            text(row, "branch"),
                            false,
                        )
                    )
            }
        }
    }

    suspend fun remove(projectDirectory: String, directory: String): Outcome<Unit> {
        val id =
            when (val resolved = resolve(projectDirectory)) {
                is Outcome.Failed -> return resolved
                is Outcome.Ok -> resolved.value
            }
        return when (
            val result =
                owner.json(
                    "/worktree/${id.encodeURLPathPart()}",
                    HttpMethod.Delete,
                    buildJsonObject {
                        put("directory", directory)
                        put("force", false)
                    },
                )
        ) {
            is Outcome.Failed -> result
            is Outcome.Ok -> Outcome.Ok(Unit)
        }
    }
}
