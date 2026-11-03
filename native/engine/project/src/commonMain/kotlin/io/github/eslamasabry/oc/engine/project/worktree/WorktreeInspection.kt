package io.github.eslamasabry.oc.engine.project.worktree

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.model.GlobalSessionResult
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.VersionControlFile
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** The two host-gateway reads the inspection uses (bound to the connection's host gateway by the app graph). */
public interface WorktreeHost {
    public suspend fun listWorktreeFileStatuses(directory: String): Outcome<List<VersionControlFile>>

    public suspend fun listGlobalSessions(includeArchived: Boolean, cursor: String?, limit: Int): Outcome<ServerPage<GlobalSessionResult>>
}

/** Read-only worktree abilities derived from the server's capability flags. */
public data class WorktreeCapabilities(val projectManagement: Boolean, val globalSessionSearch: Boolean) {
    /** Per-directory uncommitted file status is implemented by project gateways. */
    val worktreeUncommittedChanges: Boolean get() = projectManagement

    /** Root conversations can be associated with copies from paged session data. */
    val worktreeConversations: Boolean get() = projectManagement && globalSessionSearch

    /** Neither supported server contract offers a merge-copy-back operation. */
    val worktreeMerge: Boolean get() = false
}

/**
 * On-demand reads for one copy (port of lib/domain/worktree_inspection.dart). An empty successful read means clean at
 * the time of the read; a failure means unknown, never clean. Unsupported or invalid scopes dispatch no request.
 */
public class WorktreeInspection(private val host: WorktreeHost, public val capabilities: WorktreeCapabilities) {
    /** Uncommitted changes in exactly [directory], without changing the selected copy. */
    public suspend fun loadChanges(directory: String): Outcome<ImmutableList<VersionControlFile>> {
        if (!capabilities.worktreeUncommittedChanges) return Failure.Unavailable(FeatureId("projectManagement")).failed()
        if (directory.isBlank()) return Failure.Unexpected().failed()
        return host.listWorktreeFileStatuses(directory).map { it.toImmutableList() }
    }

    /**
     * One server-wide page, keeping root conversations whose directory, project and workspace exactly match this copy.
     * Paths are never normalized or prefix-matched; the opaque continuation is kept even when no item matches.
     */
    public suspend fun conversationPage(
        directory: String,
        projectId: String,
        workspaceId: String? = null,
        cursor: String? = null,
        limit: Int = 50,
        includeArchived: Boolean = true,
    ): Outcome<ServerPage<Session>> {
        if (!capabilities.worktreeConversations) return Failure.Unavailable(FeatureId("globalSessionSearch")).failed()
        if (directory.isBlank() || projectId.isBlank() || (workspaceId != null && workspaceId.isBlank()) || limit <= 0) {
            return Failure.Unexpected().failed()
        }
        return host.listGlobalSessions(includeArchived, cursor, limit).map { page ->
            ServerPage(
                page.items.map { it.session }.filter { s ->
                    s.directory == directory && s.projectId?.raw == projectId && s.workspaceId?.raw == workspaceId && s.parentId == null
                }.toImmutableList(),
                page.nextCursor,
            )
        }
    }
}

/** True when more pages remain: callers load them before claiming there are no conversations. */
public val ServerPage<*>.hasMore: Boolean get() = nextCursor != null
