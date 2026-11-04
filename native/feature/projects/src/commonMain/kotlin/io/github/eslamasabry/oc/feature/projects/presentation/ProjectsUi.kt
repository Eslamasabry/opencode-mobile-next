package io.github.eslamasabry.oc.feature.projects.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
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
    val running: Int = 0,
    val isGit: Boolean? = null,
    val kind: io.github.eslamasabry.oc.engine.files.PhoneProjectKind? = null,
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
    /** The folder used last, so the path field starts next to it. */
    val pathStart: String = "",
)

public interface ProjectsPickerActions {
    public fun back()

    public fun chooseServer(profile: ProfileId)

    public fun findPhoneProjects()

    public fun cancelPhoneScan()

    public fun askPath()

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
    val canLoadMore: Boolean = false,
    val loadingMore: Boolean = false,
    val loadMoreFailure: Failure? = null,
    val failure: Failure? = null,
    val creating: Boolean = false,
    val createFailure: Failure? = null,
)

public interface ProjectPageActions {
    public fun developmentServices()

    public fun back()

    public fun newConversationHere()

    public fun openConversation(ref: ConversationRef)

    public fun loadMore()

    public fun retry()

    public fun openServers()
}
