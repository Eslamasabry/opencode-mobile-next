package io.github.eslamasabry.oc.feature.projects.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * B-182 "Open a project" (Flutter lib/ui/widgets/folder_browser.dart and remote_folder_picker.dart, reached from the
 * home's "Open a project…"). Three pages that swap in place: where the project is, that server's projects, then the
 * folders inside one.
 */
public enum class OpenProjectStep {
    /** "Where is the project?": each connected server with its live state. */
    Servers,

    /** The chosen server's projects, Git repositories marked. */
    Projects,

    /** Inside a folder: its folders and "Open <name>". */
    Folder,
}

/** How a server answers now, in plain words on its row. */
public enum class OpenServerStatus { Connected, Connecting, NotConnected }

public data class OpenServerUi(
    val profile: ProfileId,
    val name: String,
    val onThisPhone: Boolean,
    val status: OpenServerStatus,
)

/** One folder row. [git]: a Git repository; [known]: a project the server already has ("OpenCode project"). */
public data class OpenFolderUi(
    val name: String,
    val path: String,
    val git: Boolean = false,
    val known: Boolean = false,
)

/**
 * One project opened before, on the first page (Flutter folder_browser.dart start page: "Opened before" comes first, a
 * tap opens it). [serverName] is shown only when more than one server is connected.
 */
public data class OpenRecentUi(
    val profile: ProfileId,
    val name: String,
    val path: String,
    val serverName: String? = null,
)

/** The one dialog the flow may show over its page. */
public enum class OpenProjectDialog { NewFolder, Clone, Path }

public data class OpenProjectUi(
    val step: OpenProjectStep = OpenProjectStep.Servers,
    val servers: ImmutableList<OpenServerUi> = persistentListOf(),
    /** Still reading the list of servers. */
    val serversLoading: Boolean = true,
    val serverName: String = "",
    val onThisPhone: Boolean = false,
    /** [OpenProjectStep.Folder]: the folder shown (an absolute path). */
    val path: String? = null,
    /** The projects ([OpenProjectStep.Projects]) or the folders inside [path] ([OpenProjectStep.Folder]). */
    val folders: ImmutableList<OpenFolderUi> = persistentListOf(),
    val openedBefore: ImmutableList<OpenFolderUi> = persistentListOf(),
    /** [OpenProjectStep.Servers]: the projects opened before on every connected server, before the servers. */
    val recent: ImmutableList<OpenRecentUi> = persistentListOf(),
    val loading: Boolean = false,
    /** The listing has run longer than a moment: "Still reading this folder". */
    val slow: Boolean = false,
    val failure: Failure? = null,
    /** Where New folder and Clone put the new folder; null = not offered on this page. */
    val makeIn: String? = null,
    val canMake: Boolean = false,
    val canClone: Boolean = false,
    val dialog: OpenProjectDialog? = null,
    val working: Boolean = false,
    val dialogFailure: Failure? = null,
    /** A typed path that is not there: "Create this folder?" is asked for it. */
    val missingPath: String? = null,
    /** The path field starts here (the folder shown, or next to the one opened last). */
    val pathStart: String = "",
)

public interface OpenProjectActions {
    /** One level up: folder → its parent → the projects → the servers → leave. */
    public fun back()

    public fun chooseServer(profile: ProfileId)

    public fun enterFolder(path: String)

    /** "Open <name>" for the folder shown. */
    public fun openShown()

    /** Opens [path] at once (an "Opened before" row). */
    public fun openFolder(path: String)

    /** Opens a project opened before on [profile], from the first page. */
    public fun openRecent(profile: ProfileId, path: String) {}

    public fun retry()

    public fun askNewFolder()

    public fun askClone()

    public fun askPath()

    public fun closeDialog()

    public fun createFolder(name: String)

    public fun cloneRepository(url: String, name: String)

    /** The path dialog's answer; null = cancelled. */
    public fun finishPath(path: String?)

    public fun createMissing()

    public fun dismissMissing()

    public fun openServers()
}
