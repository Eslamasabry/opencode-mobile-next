package io.github.eslamasabry.oc.engine.git

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Where the project of the Git page lives, which decides how commands run and whether a GitHub sign-in is handed over. */
public enum class GitHost {
    /** The connected server (`POST /api/shell`): pushes use the server's own Git sign-in; the app never sends its token. */
    Server,
}

/** Why the Git page cannot work for the current conversation. */
public enum class GitUnavailableReason {
    /** No live connection for the conversation's server. */
    NotConnected,

    /** The conversation has no project folder. */
    NoProject,

    /** The server cannot run commands (it said so, or it has no command endpoint). */
    ServerCannotRunCommands,
}

/** One project, reachable through one shell. */
public class GitWorkspace(
    public val host: GitHost,
    /** The project folder as [shell] sees it. */
    public val directory: String,
    public val shell: GitShell,
    /** The server's saved name, for "uses the Git sign-in set up on …". */
    public val placeName: String? = null,
) {
    /** Pushes use the sign-in that environment already has (every host today). */
    public val usesOwnGitSignIn: Boolean get() = usesOwnGitSignIn(host)

    override fun toString(): String = "GitWorkspace($host)"

    public companion object {
        /** Pushes from [host] use the Git sign-in set up there; the app's GitHub sign-in is not offered (B-SEC-082). */
        public fun usesOwnGitSignIn(host: GitHost): Boolean = host == GitHost.Server
    }
}

public sealed interface GitWorkspaceResolution {
    public data class Available(val workspace: GitWorkspace) : GitWorkspaceResolution

    public data class Unavailable(val reason: GitUnavailableReason) : GitWorkspaceResolution
}

/** Finds the conversation's project and the shell that reaches it; bound by the app (Android: `GitWiring`). */
public fun interface GitWorkspaces {
    public suspend fun resolve(ref: ConversationRef): GitWorkspaceResolution
}

/**
 * B-NAV-022, gated on capability and never on the protocol flavor: no connection or no project means no Git; a server
 * that advertises the command endpoint gets a server shell (with no token); one that reports it absent is unavailable
 * whatever its flavor. [remoteShell] null = not advertised either way: the shell is tried and a 404 answers
 * "this server can't run Git" ([GitShellFailure.Unsupported]).
 */
public fun resolveGitWorkspace(
    connected: Boolean,
    directory: String?,
    remoteShell: Boolean?,
    placeName: String? = null,
    shell: (String) -> GitShell,
): GitWorkspaceResolution {
    if (!connected) return GitWorkspaceResolution.Unavailable(GitUnavailableReason.NotConnected)
    val folder = directory?.trim()
    if (folder.isNullOrEmpty()) return GitWorkspaceResolution.Unavailable(GitUnavailableReason.NoProject)
    if (remoteShell == false) return GitWorkspaceResolution.Unavailable(GitUnavailableReason.ServerCannotRunCommands)
    return GitWorkspaceResolution.Available(GitWorkspace(GitHost.Server, folder, shell(folder), placeName))
}

/** What the page body shows. */
public sealed interface GitPageView {
    public data object Loading : GitPageView

    public data class Unavailable(val reason: GitUnavailableReason) : GitPageView

    /** The folder is not a Git repository (a state, not an error). */
    public data object NotRepository : GitPageView

    /** The first read failed: there is nothing to show yet. */
    public data class Failed(val failure: GitFailure) : GitPageView

    public data class Ready(val snapshot: GitSnapshot) : GitPageView
}

/** One file's diff as the page shows it. */
public sealed interface GitDiffView {
    public val file: GitFileChange

    public data class Loading(override val file: GitFileChange) : GitDiffView

    public data class Ready(override val file: GitFileChange, val patch: String, val binary: Boolean) : GitDiffView {
        override fun toString(): String = "Ready(${file.path.length})"
    }

    public data class Failed(override val file: GitFileChange, val failure: GitFailure) : GitDiffView
}

/** Save-and-send's two halves, so the busy line names each one (B-NAV-021). */
public enum class GitBusy { Refresh, Pull, Push, Commit, SaveThenSend, SwitchBranch, CreateBranch, Discard, SetIdentity, AddRemote, Clone }

/** The quiet look at the remote after the page opens ("behind" is only true after it). Never shown as busy. */
public enum class GitCheck { Idle, Checking, Done, Failed }

public data class GitPageState(
    val view: GitPageView = GitPageView.Loading,
    /** The action running now; at most one (B-NAV-020). */
    val busy: GitBusy? = null,
    /** The last finished write, said once on the status line. */
    val result: GitActionResult? = null,
    /** Save worked but send failed: the save is kept and the line says so (B-NAV-021). */
    val savedNotSent: Boolean = false,
    /** A refresh failed while an older snapshot is still shown (B-NAV-020). */
    val refreshFailure: GitFailure? = null,
    val diff: GitDiffView? = null,
    val host: GitHost? = null,
    val placeName: String? = null,
    /** The project folder, once resolved. */
    val directory: String? = null,
    /** The quiet remote check (`git fetch`) after opening. */
    val check: GitCheck = GitCheck.Idle,
    /** Bumped on every finished write, so the screen can clear its fields once. */
    val generation: Int = 0,
) {
    val snapshot: GitSnapshot? get() = (view as? GitPageView.Ready)?.snapshot
}

/**
 * The Git page model for one conversation's project (port of lib/state/git_page_controller.dart; B-NAV-020/021).
 * Loads (Loading → Ready), shows not-a-repository and failures as views, keeps the last snapshot when a refresh fails,
 * never runs on an unavailable workspace, records a write's result then rereads, and runs one action at a time:
 * a tap while another action runs is ignored (steer, never queue).
 */
public class GitPage(
    private val ref: ConversationRef,
    private val workspaces: GitWorkspaces,
) {
    private val mutableState = MutableStateFlow(GitPageState())
    public val state: StateFlow<GitPageState> = mutableState.asStateFlow()

    private var repository: GitRepository? = null

    /** First load, or a refresh. Resolves the workspace once (again after it was unavailable). */
    public suspend fun refresh() {
        if (!begin(GitBusy.Refresh)) return
        try {
            read()
        } finally {
            end()
        }
    }

    public suspend fun pull(): Unit = write(GitBusy.Pull) { it.pull() }

    public suspend fun push(): Unit = write(GitBusy.Push) { it.push() }

    public suspend fun commit(paths: List<String>, message: String, renamedFrom: List<String> = emptyList()): Unit =
        write(GitBusy.Commit) { it.commit(paths, message, renamedFrom) }

    /**
     * Save-and-send in one tap (B-NAV-021): saves, then sends, then reads once, with the busy line naming each half. A
     * failed save stops there; a failed send keeps the save and says so.
     */
    public suspend fun commitAndPush(paths: List<String>, message: String, renamedFrom: List<String> = emptyList()) {
        val repo = repository ?: return
        if (!begin(GitBusy.Commit)) return
        try {
            val saved = repo.commit(paths, message, renamedFrom)
            if (!saved.ok) {
                finish(saved, savedNotSent = false)
                return
            }
            mutableState.update { it.copy(busy = GitBusy.SaveThenSend) }
            val sent = repo.push()
            finish(sent, savedNotSent = !sent.ok)
            read()
        } finally {
            end()
        }
    }

    public suspend fun addRemote(name: String, url: String): Unit = write(GitBusy.AddRemote) { it.addRemote(name, url) }

    public suspend fun clone(url: String, folder: String): Unit = write(GitBusy.Clone) { it.clone(url, folder) }

    /**
     * Looks at the remote quietly and rereads, so "Up to date" and new remote branches are true (Flutter
     * `checkForUpdates`, run after the page opens). Not an action: it holds no button and never reports a failure as
     * one; [GitPageState.check] says how it went. A branch with no upstream has nothing to check.
     */
    public suspend fun checkForUpdates() {
        val repo = repository ?: return
        val snapshot = state.value.snapshot ?: return
        if (state.value.check == GitCheck.Checking) return
        if (!snapshot.status.branch.hasUpstream) {
            mutableState.update { it.copy(check = GitCheck.Idle) }
            return
        }
        mutableState.update { it.copy(check = GitCheck.Checking) }
        val fetched = repo.fetch()
        if (!fetched.ok) {
            mutableState.update { it.copy(check = GitCheck.Failed) }
            return
        }
        mutableState.update { it.copy(check = GitCheck.Done) }
        if (state.value.busy == null) read()
    }

    public suspend fun switchBranch(name: String): Unit = write(GitBusy.SwitchBranch) { it.switchBranch(name) }

    public suspend fun createBranch(name: String): Unit = write(GitBusy.CreateBranch) { it.createBranch(name) }

    public suspend fun discard(file: GitFileChange): Unit = write(GitBusy.Discard) { it.discard(file) }

    public suspend fun setIdentity(name: String, email: String): Unit = write(GitBusy.SetIdentity) { it.setIdentity(name, email) }

    /** Opens one file's diff. Reads only; may run beside nothing else but never blocks the page's actions. */
    public suspend fun openDiff(file: GitFileChange) {
        val repo = repository ?: return
        mutableState.update { it.copy(diff = GitDiffView.Loading(file)) }
        val shown =
            when (val read = repo.diff(file)) {
                is GitDiffResult.Ready -> GitDiffView.Ready(file, read.patch, read.binary)
                is GitDiffResult.Failed -> GitDiffView.Failed(file, read.failure)
            }
        mutableState.update { if (it.diff?.file == file) it.copy(diff = shown) else it }
    }

    public fun closeDiff() {
        mutableState.update { it.copy(diff = null) }
    }

    /** The status line was read; a finished action's line may be dismissed. */
    public fun dismissResult() {
        mutableState.update { it.copy(result = null, savedNotSent = false, refreshFailure = null) }
    }

    private suspend fun write(busy: GitBusy, run: suspend (GitRepository) -> GitActionResult) {
        val repo = repository ?: return
        if (!begin(busy)) return
        try {
            finish(run(repo), savedNotSent = false)
            read()
        } finally {
            end()
        }
    }

    private fun finish(result: GitActionResult, savedNotSent: Boolean) {
        mutableState.update { it.copy(result = result, savedNotSent = savedNotSent, generation = it.generation + 1) }
    }

    private suspend fun read() {
        val repo =
            repository
                ?: when (val found = workspaces.resolve(ref)) {
                    is GitWorkspaceResolution.Unavailable -> {
                        mutableState.update { it.copy(view = GitPageView.Unavailable(found.reason)) }
                        return
                    }
                    is GitWorkspaceResolution.Available -> {
                        val made = GitRepository(found.workspace.shell, found.workspace.directory)
                        repository = made
                        mutableState.update { it.copy(host = found.workspace.host, placeName = found.workspace.placeName, directory = found.workspace.directory) }
                        made
                    }
                }
        when (val read = repo.snapshot()) {
            is GitSnapshotResult.Ready ->
                mutableState.update { it.copy(view = GitPageView.Ready(read.snapshot), refreshFailure = null) }
            GitSnapshotResult.NotRepository -> mutableState.update { it.copy(view = GitPageView.NotRepository, refreshFailure = null) }
            is GitSnapshotResult.Failed -> {
                // The server has no command endpoint: the page is unavailable, not failed.
                val unsupported = read.failure.problem == GitProblem.ServerCannotRunCommands
                if (unsupported) repository = null
                mutableState.update {
                    when {
                        unsupported -> it.copy(view = GitPageView.Unavailable(GitUnavailableReason.ServerCannotRunCommands))
                        // Keep the last snapshot; say the refresh failed (B-NAV-020).
                        it.view is GitPageView.Ready -> it.copy(refreshFailure = read.failure)
                        else -> it.copy(view = GitPageView.Failed(read.failure))
                    }
                }
            }
        }
    }

    private fun begin(busy: GitBusy): Boolean {
        var admitted = false
        mutableState.update {
            if (it.busy != null) {
                admitted = false
                it
            } else {
                admitted = true
                it.copy(busy = busy)
            }
        }
        return admitted
    }

    private fun end() {
        mutableState.update { it.copy(busy = null) }
    }

    override fun toString(): String = "GitPage"
}
