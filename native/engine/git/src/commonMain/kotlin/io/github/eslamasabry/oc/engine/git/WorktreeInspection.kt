package io.github.eslamasabry.oc.engine.git

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.WorktreeInspectionGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * On-demand reads of one copy. Failures stay failures; an empty successful list alone means clean.
 */
public class WorktreeInspection(
    private val gateway: WorktreeInspectionGateway,
    private val capabilities: () -> ServerCapabilities,
) {
    private fun permitted(): Boolean = capabilities().worktreeInspection

    private fun valid(value: String): Boolean =
        value.isNotBlank() && value.length <= 4096 && value.none { it.code < 32 || it.code == 127 }

    private fun valid(scope: WorktreeScope): Boolean =
        valid(scope.directory) &&
            valid(scope.projectId) &&
            (scope.workspaceId?.let(::valid) != false)

    private fun unavailable(): Outcome.Failed =
        Outcome.Failed(Failure.Unavailable(FeatureId("worktreeInspection")))

    private fun invalid(): Outcome.Failed = Outcome.Failed(Failure.Rejected(400))

    public suspend fun copies(location: ServerLocation): Outcome<ImmutableList<WorktreeScope>> {
        if (!permitted()) return unavailable()
        val directory = location.directory ?: return invalid()
        if (!valid(directory) || (location.workspace?.let { !valid(it) } == true)) return invalid()
        val project =
            when (val result = gateway.inspectionProject(location)) {
                is Outcome.Failed -> return result
                is Outcome.Ok -> result.value ?: return Outcome.Failed(Failure.NotFound())
            }
        if (!valid(project.id)) return Outcome.Failed(Failure.UnreadableReply())
        val worktrees =
            when (val result = gateway.inspectionWorktrees(project.id)) {
                is Outcome.Failed -> return result
                is Outcome.Ok -> result.value
            }
        val directories = (listOf(directory) + worktrees.map { it.directory }).distinct()
        if (directories.any { !valid(it) }) return Outcome.Failed(Failure.UnreadableReply())
        return Outcome.Ok(
            directories.map { WorktreeScope(project.id, it, location.workspace) }.toImmutableList()
        )
    }

    public suspend fun loadChanges(
        scope: WorktreeScope
    ): Outcome<ImmutableList<VersionControlFile>> {
        if (!permitted()) return unavailable()
        if (!valid(scope)) return invalid()
        return gateway.inspectionChanges(ServerLocation(scope.directory, scope.workspaceId))
    }

    /** Keep the server's continuation even when no root on this page belongs to this exact copy. */
    public suspend fun conversationPage(
        scope: WorktreeScope,
        cursor: String? = null,
        limit: Int = 50,
        includeArchived: Boolean = true,
    ): Outcome<ServerPage<Session>> {
        if (!permitted()) return unavailable()
        if (!valid(scope) || limit !in 1..200 || cursor?.let { !valid(it) } == true)
            return invalid()
        return when (val result = gateway.inspectionSessions(cursor, limit, includeArchived)) {
            is Outcome.Failed -> result
            is Outcome.Ok ->
                Outcome.Ok(
                    ServerPage(
                        result.value.items
                            .filter { session ->
                                session.directory == scope.directory &&
                                    session.projectId?.raw == scope.projectId &&
                                    session.workspaceId?.raw == scope.workspaceId &&
                                    session.parentId == null
                            }
                            .toImmutableList(),
                        result.value.nextCursor,
                    )
                )
        }
    }
}
