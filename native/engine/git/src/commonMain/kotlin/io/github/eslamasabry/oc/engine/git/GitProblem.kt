package io.github.eslamasabry.oc.engine.git

/**
 * What went wrong with a Git action, in categories the page has words for (port of lib/domain/git/git_problem.dart).
 * The page shows the sentence for a [GitProblem] from `:ui:words`, and keeps Git's text, scrubbed of secrets, for
 * Details only ([GitFailure.details]). Commands run with `LC_ALL=C`, so the English patterns below hold.
 */
public enum class GitProblem {
    // The environment
    NotARepository,
    GitMissing,
    FolderMissing,
    EnvironmentUnavailable,
    ServerCannotRunCommands,
    OutputTooLarge,
    TimedOut,
    UnsafeRepository,
    RepositoryLocked,

    // Branch and remote
    NoUpstream,
    NoRemote,
    DetachedHead,
    CannotFastForward,
    PushRejected,
    LocalChangesBlock,

    // Sign-in and network
    AuthRequired,
    GitHubTokenRejected,
    NoPermission,
    Network,
    RemoteNotFound,

    // Commit
    NothingSelected,
    EmptyMessage,
    NothingToCommit,
    IdentityMissing,
    SigningFailed,
    HookRejected,

    // Branches
    InvalidBranchName,
    BranchExists,
    BranchNotFound,

    // Remotes and clone
    InvalidRemoteName,
    InvalidRemoteUrl,
    RemoteAddressHasPassword,
    RemoteExists,
    InvalidFolderName,
    FolderExists,

    // Conflicts and half-done operations
    Conflicts,
    OperationInProgress,
    CannotDiscardConflict,

    Unknown,
}

/** A failed Git action: the category and Git's own text for Details (secrets already masked). */
public data class GitFailure(val problem: GitProblem, val details: String = "") {
    /** Never prints Git's text. */
    override fun toString(): String = "GitFailure($problem)"
}

/**
 * The category for [stderr] of a failed command, or null when nothing matches (the caller falls back to its own
 * default). [usedGitHubToken] turns an authentication failure into [GitProblem.GitHubTokenRejected].
 */
public fun classifyGitError(stderr: String, usedGitHubToken: Boolean = false): GitProblem? {
    val text = stderr.lowercase()
    fun has(s: String) = s in text

    return when {
        has("not a git repository") -> GitProblem.NotARepository
        has("dubious ownership") || has("safe.directory") -> GitProblem.UnsafeRepository
        has("index.lock") && (has("file exists") || has("unable to create")) -> GitProblem.RepositoryLocked
        has("you have unmerged paths") || has("unmerged files") || has("fix conflicts") || has("is unmerged") ||
            has("you need to resolve your current index first") -> GitProblem.Conflicts
        has("cannot do a partial commit during a merge") || has("you have not concluded your merge") ||
            has("rebase in progress") || has("cherry-pick is already in progress") ||
            has("a cherry-pick or revert is already in progress") -> GitProblem.OperationInProgress
        has("would be overwritten by") || has("please commit your changes or stash them") ->
            GitProblem.LocalChangesBlock
        has("not possible to fast-forward") || has("diverging branches can't be fast-forwarded") ||
            has("have diverged") -> GitProblem.CannotFastForward
        has("[rejected]") || (has("[remote rejected]") && has("non-fast-forward")) ||
            has("updates were rejected") || (has("failed to push some refs") && has("fetch first")) ->
            GitProblem.PushRejected
        has("no upstream configured") || has("no tracking information") || has("has no upstream branch") ->
            GitProblem.NoUpstream
        has("head does not point to a branch") || has("you are not currently on a branch") -> GitProblem.DetachedHead
        has("no configured push destination") ||
            (has("does not appear to be a git repository") && !has("repository not found")) -> GitProblem.NoRemote
        has("repository not found") || has("the requested url returned error: 404") -> GitProblem.RemoteNotFound
        (has("permission to") && has("denied")) || has("the requested url returned error: 403") ||
            has("error: 403") -> GitProblem.NoPermission
        has("authentication failed") || has("invalid username or password") ||
            has("the requested url returned error: 401") || has("could not read username") ||
            has("could not read password") || has("terminal prompts disabled") ||
            has("permission denied (publickey") || has("host key verification failed") ->
            if (usedGitHubToken && !has("permission denied (publickey") && !has("host key verification failed")) {
                GitProblem.GitHubTokenRejected
            } else {
                GitProblem.AuthRequired
            }
        has("could not resolve host") || has("could not resolve hostname") || has("connection timed out") ||
            has("connection refused") || has("network is unreachable") || has("failed to connect to") ||
            has("unable to access") || has("operation timed out") || has("ssl certificate problem") ||
            has("could not read from remote repository") -> GitProblem.Network
        has("please tell me who you are") || has("author identity unknown") ||
            has("unable to auto-detect email address") || has("empty ident name") -> GitProblem.IdentityMissing
        has("gpg failed to sign") || (has("failed to write commit object") && has("sign")) ||
            has("error: cannot run gpg") || (has("ssh-keygen") && has("sign")) -> GitProblem.SigningFailed
        has("nothing to commit") || has("nothing added to commit") || has("no changes added to commit") ->
            GitProblem.NothingToCommit
        has("hook") && (has("exited") || has("declined") || has("failed")) -> GitProblem.HookRejected
        has("remote") && has("already exists") -> GitProblem.RemoteExists
        has("destination path") && has("already exists") -> GitProblem.FolderExists
        has("already exists") && has("branch") -> GitProblem.BranchExists
        has("is not a valid branch name") || has("not a valid branch name") -> GitProblem.InvalidBranchName
        has("invalid reference") || (has("did not match any file(s) known to git") && has("switch")) ||
            has("a branch is expected") -> GitProblem.BranchNotFound
        else -> null
    }
}

private val URL_USER_INFO = Regex("://[^/\\s@]+@")

/** [text] with every one of [secrets] masked, and remote addresses with user info masked, for Details. */
public fun scrubGitText(text: String, secrets: Iterable<String>): String {
    var out = text
    for (secret in secrets) {
        if (secret.isEmpty()) continue
        out = out.replace(secret, "•••")
    }
    return URL_USER_INFO.replace(out, "://•••@")
}
