package io.github.eslamasabry.oc.engine.project.gitops

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the project's Git runs. */
public enum class GitHost {
    /** Ubuntu inside the app: GitHub sign-in handed over per command. */
    PhoneLinux,

    /** Termux's own Git, with its own sign-in. */
    Termux,

    /** A server's shell, with the server's own sign-in. */
    Server,
}

/** The facts of the last snapshot the abilities depend on. */
public data class GitSnapshotFacts(
    val detached: Boolean,
    val unborn: Boolean,
    val hasUpstream: Boolean,
    val hasRemote: Boolean,
    /** A merge, rebase, cherry-pick or revert is half done. */
    val operationInProgress: Boolean,
    val changedFiles: Int,
    val hasConflicts: Boolean,
)

/** What the page may offer, from where the project lives and what the last snapshot says. */
public data class GitPageAbilities(
    val canPull: Boolean = false,
    val canPush: Boolean = false,
    val canCommit: Boolean = false,
    val canSwitchBranch: Boolean = false,
    /** Show GitHub sign-in (Ubuntu inside the app only). */
    val offersGitHubSignIn: Boolean = false,
    /** Pushes use the environment's own sign-in (Termux, servers): say so. */
    val usesOwnGitSignIn: Boolean = false,
) {
    public companion object {
        /** Sign-in follows the drop, never the host name: only an environment the app can write into privately has one. */
        public fun of(hasCredentialDrop: Boolean, snapshot: GitSnapshotFacts?): GitPageAbilities {
            if (snapshot == null) return GitPageAbilities(offersGitHubSignIn = hasCredentialDrop, usesOwnGitSignIn = !hasCredentialDrop)
            return GitPageAbilities(
                canPull = !snapshot.detached && snapshot.hasUpstream && !snapshot.operationInProgress,
                canPush = !snapshot.detached && !snapshot.unborn && snapshot.hasRemote,
                canCommit = snapshot.changedFiles > 0 && !snapshot.hasConflicts,
                canSwitchBranch = !snapshot.operationInProgress,
                offersGitHubSignIn = hasCredentialDrop,
                usesOwnGitSignIn = !hasCredentialDrop,
            )
        }
    }
}

/** The signed-in GitHub account of the profile, as the Git page sees it. */
public interface GitHubTokenSource {
    /** The token for one command, or null when signed out. Never logged. */
    public suspend fun tokenForGit(): String?

    /** GitHub refused the token: sign the account out. */
    public suspend fun tokenRejected()
}

/** Which environment a workspace is: the host and, only on the built-in Ubuntu, the drop. */
public data class GitPushWorkspace(val host: GitHost, val directory: String, val shell: GitOpsShell, val credentials: GitCredentialDrop?)

/**
 * The push half of the Git page (port of GitPageController's token rules, B-SEC-082): a push on the built-in Ubuntu
 * hands the signed-in token to the drop and a rejected token signs the account out; on Termux and servers the token is
 * never asked for. One action at a time.
 */
public class GitPushPage(workspace: GitPushWorkspace, private val account: GitHubTokenSource?) {
    private val ops = GitRemoteOps(workspace.shell, workspace.directory, workspace.credentials)
    private val hasDrop = workspace.credentials != null
    private val _busy = MutableStateFlow<GitOpsAction?>(null)
    private val _snapshot = MutableStateFlow<GitSnapshotFacts?>(null)
    private val _lastResult = MutableStateFlow<GitOpsResult?>(null)

    public val busy: StateFlow<GitOpsAction?> = _busy.asStateFlow()
    public val lastResult: StateFlow<GitOpsResult?> = _lastResult.asStateFlow()

    public val abilities: GitPageAbilities get() = GitPageAbilities.of(hasDrop, _snapshot.value)

    /** The page's snapshot reader hands its facts here after each read. */
    public fun onSnapshot(facts: GitSnapshotFacts?) {
        _snapshot.value = facts
    }

    /** Pushes; null when another action is running. */
    public suspend fun push(): GitOpsResult? {
        if (_busy.value != null) return null
        _busy.value = GitOpsAction.Push
        try {
            val token = if (hasDrop) account?.tokenForGit() else null
            val result = ops.push(gitHubToken = token)
            if (result.problem == GitOpsProblem.GitHubTokenRejected) account?.tokenRejected()
            _lastResult.value = result
            return result
        } finally {
            _busy.value = null
        }
    }
}
