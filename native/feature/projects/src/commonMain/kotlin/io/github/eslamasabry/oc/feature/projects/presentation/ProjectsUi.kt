package io.github.eslamasabry.oc.feature.projects.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.platform.SharedStorageBlock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

public enum class ProjectsPhase {
    /** No live server yet (or still reading the list of servers). */
    NoServer,
    Ready,
}

/** A server the picker can show projects of (chips only when there is more than one). */
public data class ServerChoiceUi(val profile: ProfileId, val name: String, val selected: Boolean)

/** A folder row: "Opened before" and the server's projects. */
public data class FolderRowUi(
    val directory: String,
    val name: String,
    val conversations: Int = 0,
    /** False when [conversations] was never read for this folder (opened before, found by a search): no count is shown. */
    val counted: Boolean = true,
    val running: Int = 0,
    val isGit: Boolean? = null,
    /** The server's project id, when it keeps a project list (needed to rename). */
    val id: String? = null,
    val kind: io.github.eslamasabry.oc.engine.files.PhoneProjectKind? = null,
    /** Extra working copies (worktrees) the server keeps for this project; 0 = none or not told. */
    val worktrees: Int = 0,
    /**
     * The connection this folder's page opens on, when it is not the chosen server's own: a folder only This phone's
     * agents work in opens on their connection, where its conversations are. Null = the chosen server.
     */
    val opensOn: io.github.eslamasabry.oc.core.common.ids.ProfileId? = null,
)

/**
 * Projects (07 §5 D15/D16): obvious options first as visible rows (New project, Open a folder on
 * the server, Folder on this phone), then Opened before, then the server's projects. New project
 * and the phone folder say why they can't run when the server can't make folders or the phone's own
 * server isn't set up.
 */
public data class ProjectsPickerUi(
    val phase: ProjectsPhase = ProjectsPhase.NoServer,
    val servers: ImmutableList<ServerChoiceUi> = persistentListOf(),
    val serverName: String = "",
    val openedBefore: ImmutableList<FolderRowUi> = persistentListOf(),
    val projects: ImmutableList<FolderRowUi> = persistentListOf(),
    val projectsLoading: Boolean = false,
    val canFindPhoneProjects: Boolean = false,
    val findingPhoneProjects: Boolean = false,
    val phoneProjects: ImmutableList<FolderRowUi> = persistentListOf(),
    val scanEnd: io.github.eslamasabry.oc.engine.files.ProjectScanEnd? = null,
    val projectsFailure: Failure? = null,
    val query: String = "",
    val showSearch: Boolean = false,
    val askingPath: Boolean = false,
    val canCreate: Boolean = false,
    val askingCreate: Boolean = false,
    val createName: String = "",
    val creatingFolder: Boolean = false,
    val folderFailure: Failure? = null,
    /** A typed path that does not exist yet: "Create this folder?" is asked for it. */
    val missingPath: String? = null,
    val creatingMissing: Boolean = false,
    val missingFailure: Failure? = null,
    /** The project being renamed (its directory), and the rename's state. */
    val renaming: FolderRowUi? = null,
    val renamingWorking: Boolean = false,
    val renameFailure: Failure? = null,
    /** True when this server keeps a project list whose names can be changed. */
    val canRename: Boolean = false,
    /** The folder used last, so the path field starts next to it. */
    val pathStart: String = "",
    val canBrowsePhone: Boolean = false,
    val canBrowseBuiltin: Boolean = false,
    val browsingBuiltin: Boolean = false,
    val phonePath: String? = null,
    val phoneParent: String? = null,
    val phoneFolders: ImmutableList<PhoneFolderUi> = persistentListOf(),
    val phoneLoading: Boolean = false,
    val phoneFailure: Failure? = null,
    /**
     * The server works in one folder (Codex, Paseo: no project list, no file browsing): the page says so and
     * shows [configuredFolder] instead of a project choice (Flutter `_oneFolder`).
     */
    val oneFolder: Boolean = false,
    val configuredFolder: String? = null,
)

public interface ProjectsPickerActions {
    public fun back()

    public fun chooseServer(profile: ProfileId)

    public fun findPhoneProjects()

    public fun cancelPhoneScan()

    public fun askPath()

    public fun askCreate() {}

    public fun changeCreateName(name: String) {}

    public fun closeCreate() {}

    public fun createFolder() {}

    /** "Create this folder?" answered yes / no. */
    public fun createMissing() {}

    public fun dismissMissing() {}

    public fun askRename(project: FolderRowUi) {}

    public fun closeRename() {}

    /** [name] blank = use the folder's own name. */
    public fun renameProject(name: String) {}

    public fun browseBuiltin() {}

    public fun browsePhone()

    public fun enterPhoneFolder(path: String)

    public fun closePhoneFolders()

    public fun usePhoneFolder()

    public fun allowPhoneFolderAccess()

    /** The path dialog's answer; null = cancelled. */
    public fun finishPath(path: String?)

    public fun openProject(directory: String)

    public fun search(query: String)

    public fun retry()

    public fun openServers()
}

/** One conversation on the project page. */
public data class ProjectChatUi(
    val ref: ConversationRef,
    val title: ChatTitle,
    val project: String?,
    val running: Boolean,
)

public data class ProjectAttentionUi(
    val directory: String,
    val name: String,
    val waiting: Int,
    val running: Int,
    val failed: Int = 0,
    val stale: Boolean = false,
)

public enum class ProjectPagePhase {
    Opening,
    NoServer,
    Failed,
    Ready,
}

/**
 * One project's page (07 §5 D15): New conversation here first, its conversations, then "In other
 * projects".
 */
public data class ProjectPageUi(
    val directory: String,
    val name: String,
    val serverName: String = "",
    val phase: ProjectPagePhase = ProjectPagePhase.Opening,
    val conversations: ImmutableList<ProjectChatUi> = persistentListOf(),
    val elsewhere: ImmutableList<ProjectChatUi> = persistentListOf(),
    val elsewhereAttention: ImmutableList<ProjectAttentionUi> = persistentListOf(),
    val canLoadMore: Boolean = false,
    val loadingMore: Boolean = false,
    val loadMoreFailure: Failure? = null,
    val failure: Failure? = null,
    val creating: Boolean = false,
    val createFailure: Failure? = null,
    val canStartIsolated: Boolean = false,
    /** B-163: the server runs scheduled prompts, so the page offers "Scheduled prompts". */
    val canSchedule: Boolean = false,
    val copies: List<io.github.eslamasabry.oc.core.model.WorktreeInfo> = emptyList(),
    val copiesFailure: Failure? = null,
    val copiesLoading: Boolean = false,
    val isolatedShown: Boolean = false,
    val isolatedLoading: Boolean = false,
    val isolatedFailure: Failure? = null,
    val isolatedTask: String = "",
    val isolatedName: String = "",
    val isolated: io.github.eslamasabry.oc.engine.agents.worktree.IsolatedTaskState? = null,
    val isolatedDraftFailed: Boolean = false,
    val confirmRemoveCopy: Boolean = false,
    val isolatedDetails: io.github.eslamasabry.oc.core.common.Redacted? = null,
    val workspace: ProjectWorkspaceUi = ProjectWorkspaceUi(),
    val changesCount: Int? = null,
    val devServerPort: Int? = null,
    val storageBlock: SharedStorageBlock = SharedStorageBlock.None,
    val requestingStorage: Boolean = false,
    /** The server can list this project's files: the page offers Files (Flutter's project hub). */
    val canBrowseFiles: Boolean = false,
    /** The server reports language services or formatters: the page offers Project health. */
    val canHealth: Boolean = false,
)

public interface ProjectPageActions : ProjectWorkspaceActions {
    /** This project's files, with no conversation needed (Flutter's project hub Files). */
    public fun openFiles() {}

    /** Language services, formatters and branch for this project. */
    public fun openHealth() {}

    public fun refreshCopies() {}

    public fun openCopy(directory: String) {}

    public fun startIsolatedTask() {}

    public fun isolatedDraftChanged(text: String) {}

    public fun isolatedNameChanged(name: String) {}

    public fun createIsolatedTask() {}

    public fun closeIsolatedTask() {}

    public fun keepWaitingForCopy() {}

    public fun openCopyAnyway() {}

    public fun requestRemoveCopy() {}

    public fun confirmRemoveCopy(remove: Boolean) {}

    public fun openUnsentConversation() {}

    public fun requestStorage() {}

    public fun onResume() {}

    public fun developmentServices()

    /** B-163: this server's scheduled prompts, a new one seeded with this project. */
    public fun openSchedules() {}

    public fun back()

    /** AGENT-1: open another project that needs attention ("In other projects"). */
    public fun openOtherProject(directory: String) {}

    public fun newConversationHere()

    public fun openConversation(ref: ConversationRef)

    public fun loadMore()

    public fun retry()

    public fun openServers()
}
