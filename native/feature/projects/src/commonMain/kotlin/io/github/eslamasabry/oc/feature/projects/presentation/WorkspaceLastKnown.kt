package io.github.eslamasabry.oc.feature.projects.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.VersionControlFile
import io.github.eslamasabry.oc.core.model.VersionControlHealth
import io.github.eslamasabry.oc.core.model.VersionControlSetupState
import io.github.eslamasabry.oc.core.model.WorktreeInfo
import io.github.eslamasabry.oc.core.model.WorktreeScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable

/**
 * Cache-first (owner rule, 2026-11-09): a project's checkout sheet (branch, changed files, worktrees, the copies
 * inspection) opens on what it last showed, then the live read replaces it in place. The app binds this to its
 * last-known store; without a binding the sheet loads as before.
 */
public interface WorkspaceLastKnown {
    public fun peek(profile: ProfileId, directory: String): KeptWorkspace?

    public suspend fun keep(profile: ProfileId, directory: String, kept: KeptWorkspace)
}

/** No store: the sheet opens cold. The app replaces this. */
@ContributesBinding(AppScope::class)
@Inject
public class NoWorkspaceLastKnown : WorkspaceLastKnown {
    override fun peek(profile: ProfileId, directory: String): KeptWorkspace? = null

    override suspend fun keep(profile: ProfileId, directory: String, kept: KeptWorkspace) {}
}

/**
 * The display facts of the sheet: file names and line counts (never patches), branch names, worktree folders and
 * conversation titles. Nothing in it is a credential.
 */
@Serializable
public data class KeptWorkspace(
    val setup: String? = null,
    val branch: String? = null,
    val defaultBranch: String? = null,
    val changes: List<KeptFile> = emptyList(),
    val worktrees: List<KeptTree> = emptyList(),
    val scopes: List<KeptScope> = emptyList(),
    val scope: String? = null,
    val scopeChanges: List<KeptFile>? = null,
    val sessions: List<KeptSession> = emptyList(),
) {
    @Serializable
    public data class KeptFile(val path: String, val status: String = "", val additions: Int = 0, val deletions: Int = 0)

    @Serializable
    public data class KeptTree(
        val name: String,
        val directory: String,
        val branch: String?,
        val ready: Boolean,
        val projectId: String? = null,
    )

    @Serializable
    public data class KeptScope(val projectId: String, val directory: String, val workspaceId: String? = null)

    @Serializable
    public data class KeptSession(val id: String, val title: String? = null)

    /** True when there is anything to draw instead of a skeleton. */
    val isEmpty: Boolean
        get() = setup == null && changes.isEmpty() && worktrees.isEmpty() && scopes.isEmpty()

    /** Lays the kept facts over a fresh state; flags and capabilities stay the live ones. */
    internal fun over(state: ProjectWorkspaceUi): ProjectWorkspaceUi {
        val health =
            setup?.let { s ->
                VersionControlHealth(
                    branch,
                    defaultBranch,
                    kotlinx.collections.immutable.persistentListOf(),
                    VersionControlSetupState.entries.firstOrNull { it.name == s } ?: VersionControlSetupState.Unknown,
                )
            }
        val scopeList = scopes.map { WorktreeScope(it.projectId, it.directory, it.workspaceId) }
        return state.copy(
            health = health,
            changes = changes.map { FileDiff(it.path, additions = it.additions, deletions = it.deletions, status = it.status) }.toImmutableList(),
            worktrees = worktrees.map { WorktreeInfo(it.name, it.directory, it.branch, it.ready, it.projectId) }.toImmutableList(),
            inspectionScopes = if (state.canInspect) scopeList.toImmutableList() else state.inspectionScopes,
            inspectionScope = if (state.canInspect) scopeList.firstOrNull { it.directory == scope } else null,
            inspectionChanges =
                if (state.canInspect) scopeChanges?.map { VersionControlFile(it.path, it.status, it.additions, it.deletions) }?.toImmutableList() else null,
            inspectionSessions =
                if (state.canInspect) sessions.map { Session(SessionId(it.id), it.title) }.toImmutableList() else state.inspectionSessions,
        )
    }

    public companion object {
        public const val MAX_FILES: Int = 100
        public const val MAX_TREES: Int = 50
        public const val MAX_SESSIONS: Int = 50

        internal fun of(state: ProjectWorkspaceUi): KeptWorkspace =
            KeptWorkspace(
                setup = state.health?.setupState?.name,
                branch = state.health?.branch,
                defaultBranch = state.health?.defaultBranch,
                changes = state.changes.take(MAX_FILES).map { KeptFile(it.file, it.status.orEmpty(), it.additions ?: 0, it.deletions ?: 0) },
                worktrees = state.worktrees.take(MAX_TREES).map { KeptTree(it.name, it.directory, it.branch, it.ready, it.projectId) },
                scopes = state.inspectionScopes.take(MAX_TREES).map { KeptScope(it.projectId, it.directory, it.workspaceId) },
                scope = state.inspectionScope?.directory,
                scopeChanges = state.inspectionChanges?.take(MAX_FILES)?.map { KeptFile(it.path, it.status, it.additions, it.deletions) },
                sessions = state.inspectionSessions.take(MAX_SESSIONS).map { KeptSession(it.id.raw, it.title) },
            )
    }
}
