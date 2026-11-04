package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.WorkspaceProject
import io.github.eslamasabry.oc.core.model.WorktreeInfo
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The same callable contract as Flutter ops_projects: resolve /location then one POST
 * /worktree/{project}.
 */
internal object Oc2Worktrees {
    suspend fun current(gateway: Oc2Gateway): Outcome<WorkspaceProject?> {
        val location = gateway.location.value
        return when (
            val result =
                gateway.json(
                    "/location",
                    query =
                        mapOf(
                            "location[directory]" to location.directory,
                            "location[workspace]" to location.workspace,
                        ),
                )
        ) {
            is Outcome.Failed -> result
            is Outcome.Ok -> {
                val project =
                    (data(result.value) as? JsonObject)?.get("project") as? JsonObject
                        ?: return Failure.UnreadableReply().failed()
                val id = project.text("id") ?: return Failure.UnreadableReply().failed()
                val directory =
                    project.text("directory")
                        ?: project.text("worktree")
                        ?: location.directory
                        ?: return Failure.UnreadableReply().failed()
                WorkspaceProject(
                        id,
                        project.text("name") ?: directory.substringAfterLast('/'),
                        directory,
                        persistentListOf(),
                        0,
                        project.text("vcs") == "git",
                    )
                    .ok()
            }
        }
    }

    suspend fun create(
        gateway: Oc2Gateway,
        directory: String,
        name: String?,
    ): Outcome<WorktreeInfo> {
        if (
            directory.isBlank() ||
                directory.any { it == '\n' || it == '\r' || it == '\u0000' } ||
                name?.any { it == '\n' || it == '\r' || it == '\u0000' } == true
        )
            return Failure.UnreadableReply().failed()
        val location = gateway.location.value
        val located = gateway.json("/location", query = mapOf("location[directory]" to directory))
        val project =
            when (located) {
                is Outcome.Failed -> return located
                is Outcome.Ok -> (data(located.value) as? JsonObject)?.get("project") as? JsonObject
            } ?: return Failure.UnreadableReply().failed()
        val id = project.text("id") ?: return Failure.UnreadableReply().failed()
        if (gateway.location.value != location)
            return Failure.Refused(RefusalReason.StaleScope).failed()
        // Never retry the POST: a lost response can still have created a copy on the server.
        return when (
            val created =
                gateway.json(
                    "/worktree/${id.encodeURLPathPart()}",
                    HttpMethod.Post,
                    buildJsonObject {
                        name?.trim()?.takeIf { it.isNotEmpty() }?.let { put("name", it) }
                    },
                )
        ) {
            is Outcome.Failed -> created
            is Outcome.Ok -> {
                val value =
                    data(created.value) as? JsonObject ?: return Failure.UnreadableReply().failed()
                val path = value.text("directory") ?: return Failure.UnreadableReply().failed()
                WorktreeInfo(
                        name?.takeIf { it.isNotBlank() }
                            ?: path.trimEnd('/').substringAfterLast('/'),
                        path,
                        value.text("branch"),
                        ready = false,
                        projectId = id,
                    )
                    .ok()
            }
        }
    }

    private fun data(value: JsonElement): JsonElement = (value as? JsonObject)?.get("data") ?: value

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
}
