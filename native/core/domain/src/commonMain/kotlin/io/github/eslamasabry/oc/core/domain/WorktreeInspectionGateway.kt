package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.*
import kotlinx.collections.immutable.ImmutableList

/**
 * Read-only copy inspection. Implementing this does not promise project management or mutations.
 */
public interface WorktreeInspectionGateway {
    public suspend fun inspectionProject(location: ServerLocation): Outcome<WorkspaceProject?>

    public suspend fun inspectionWorktrees(projectId: String): Outcome<ImmutableList<WorktreeInfo>>

    public suspend fun inspectionChanges(
        location: ServerLocation
    ): Outcome<ImmutableList<VersionControlFile>>

    public suspend fun inspectionSessions(
        cursor: String?,
        limit: Int,
        includeArchived: Boolean,
    ): Outcome<ServerPage<Session>>
}
