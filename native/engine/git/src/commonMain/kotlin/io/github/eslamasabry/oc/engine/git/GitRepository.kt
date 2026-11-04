package io.github.eslamasabry.oc.engine.git

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Every action the page offers; also the page's busy state. */
public enum class GitAction {
    Refresh,
    Pull,
    Push,
    Commit,
    SwitchBranch,
    CreateBranch,
    Discard,
    SetIdentity,
    Diff,
    AddRemote,
    Clone,
}

/** Something worth saying about an action that worked. */
public enum class GitActionNote {
    UpToDate,
    Pulled,
    Pushed,
    PushedAndTracking,
    Committed,
    Switched,
    Created,
    Discarded,
    IdentitySaved,
    RemoteAdded,
    Cloned,
}

public data class GitActionResult(
    val action: GitAction,
    val note: GitActionNote? = null,
    val failure: GitFailure? = null,
    /** Short id of the commit a commit action made. */
    val commit: String? = null,
    /** How many files a commit saved, or how many new commits a pull brought. */
    val count: Int? = null,
    /** The remote branch a push went to or a pull came from (`origin/main`). */
    val target: String? = null,
    /** The folder a clone made (`/srv/other`). */
    val clonedTo: String? = null,
) {
    val ok: Boolean
        get() = failure == null

    val problem: GitProblem?
        get() = failure?.problem

    public companion object {
        public fun success(
            action: GitAction,
            note: GitActionNote,
            commit: String? = null,
            count: Int? = null,
            target: String? = null,
            clonedTo: String? = null,
        ): GitActionResult = GitActionResult(action, note, null, commit, count, target, clonedTo)

        public fun failure(action: GitAction, failure: GitFailure): GitActionResult =
            GitActionResult(action, failure = failure)
    }
}

/** What [GitRepository.snapshot] found. */
public sealed interface GitSnapshotResult {
    public data class Ready(val snapshot: GitSnapshot) : GitSnapshotResult

    /** The folder is not inside a repository (a state, not an error). */
    public data object NotRepository : GitSnapshotResult

    public data class Failed(val failure: GitFailure) : GitSnapshotResult
}

/**
 * One file's unified patch (`git diff` for the working tree plus `git diff --cached` for the
 * index).
 */
public sealed interface GitDiffResult {
    public data class Ready(val path: String, val patch: String, val binary: Boolean) :
        GitDiffResult {
        override fun toString(): String = "Ready(${patch.length} chars)"
    }

    public data class Failed(val failure: GitFailure) : GitDiffResult
}

/**
 * True when [name] is a branch name Git accepts (`git check-ref-format --branch`), checked before
 * any command.
 */
public fun isValidGitBranchName(name: String): Boolean {
    if (name.isEmpty() || name.length > 250) return false
    if (name == "@" || name.startsWith("-") || name.startsWith("/")) return false
    if (name.endsWith("/") || name.endsWith(".")) return false
    if (".." in name || "//" in name || "@{" in name) return false
    for (c in name) {
        if (c.code < 0x20 || c.code == 0x7f) return false
        if (c in " ~^:?*[\\") return false
    }
    for (part in name.split('/')) {
        if (part.isEmpty() || part.startsWith(".") || part.endsWith(".lock")) return false
    }
    return true
}

private val SIMPLE_NAME = Regex("^[A-Za-z0-9_][A-Za-z0-9._-]{0,99}$")

/** True when [name] can be a remote's name (`origin`). */
public fun isValidGitRemoteName(name: String): Boolean = SIMPLE_NAME.matches(name)

/** True when [name] can be the folder a clone makes beside the project. */
public fun isValidGitFolderName(name: String): Boolean =
    SIMPLE_NAME.matches(name) && !name.endsWith(".lock")

private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.-]*)://")
private val CONTROL = Regex("[\\s\\x00-\\x1f\\x7f]")

/**
 * Why [url] cannot be a remote's address, or null when it can (port of `gitRemoteAddressProblem`).
 * Checked before any command runs: `https://`, `http://`, `ssh://`, `git://` and `file://`
 * addresses, `user@host:path` and a folder path are accepted; one that carries a password or token
 * is refused, so a credential is never saved in the repository.
 */
public fun gitRemoteAddressProblem(url: String): GitProblem? {
    val value = url.trim()
    if (value.isEmpty() || value.startsWith("-") || CONTROL.containsMatchIn(value))
        return GitProblem.InvalidRemoteUrl
    val scheme = SCHEME.find(value) ?: return null
    val name = scheme.groupValues[1].lowercase()
    if (name !in setOf("https", "http", "ssh", "git", "file")) return GitProblem.InvalidRemoteUrl
    val rest = value.substring(scheme.value.length)
    val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
    val userInfo = if ('@' in authority) authority.substringBeforeLast('@') else ""
    val host =
        authority
            .substringAfterLast('@')
            .substringBefore(':')
            .removePrefix("[")
            .substringBefore(']')
    if (name != "file" && host.isEmpty()) return GitProblem.InvalidRemoteUrl
    if (':' in userInfo && name in setOf("https", "http", "git"))
        return GitProblem.RemoteAddressHasPassword
    return null
}

/**
 * The folder name a clone of [url] would get (`https://github.com/o/app.git` → `app`); empty when
 * none fits.
 */
public fun suggestGitCloneFolder(url: String): String {
    val value = url.trim().trimEnd('/')
    val cut = value.indexOfLast { it == '/' || it == ':' }
    var name = if (cut < 0) value else value.substring(cut + 1)
    if (name.endsWith(".git")) name = name.dropLast(4)
    return if (isValidGitFolderName(name)) name else ""
}

/**
 * The folder holding [path] (`/` for a top-level folder): where a clone beside the project lands.
 */
public fun gitParentFolder(path: String): String {
    var value = path
    while (value.length > 1 && value.endsWith("/")) value = value.dropLast(1)
    val cut = value.lastIndexOf('/')
    if (cut < 0) return "."
    return if (cut == 0) "/" else value.substring(0, cut)
}

/** Where a clone of [folder] beside [directory] lands. */
public fun gitCloneTarget(directory: String, folder: String): String {
    val parent = gitParentFolder(directory)
    return if (parent.endsWith("/")) parent + folder else "$parent/$folder"
}

/**
 * The Git page's operations on one repository, over any [GitShell] (port of
 * lib/domain/git/git_repository.dart). Reads come back as typed models; writes come back as a
 * [GitActionResult] whose failure is a [GitProblem] with Git's text kept for Details. Nothing here
 * throws for a Git failure: "not a repository", "no upstream" and conflicts are states the page
 * shows. The GitHub token is never handed to a server shell: remote servers push with their own
 * sign-in (B-SEC-082).
 */
public class GitRepository(
    private val shell: GitShell,
    /** The project folder as [shell] sees it. */
    public val directory: String,
    private val readTimeoutMs: Long = 45_000,
    private val networkTimeoutMs: Long = 180_000,
    private val credentials: GitCredentialDrop? = null,
) {
    public val canUseGitHubToken: Boolean
        get() = credentials != null

    // ---------------------------------------------------------------- reads

    /**
     * Status with line counts, recent commits, branches, remotes and any half-done operation, in
     * one round trip.
     */
    public suspend fun snapshot(logLimit: Int = 30): GitSnapshotResult {
        val script =
            GitScript(
                directory,
                listOf(
                    GitStep(
                        "status",
                        GIT +
                            listOf(
                                "status",
                                "--porcelain=v2",
                                "--branch",
                                "-z",
                                "--untracked-files=normal",
                            ),
                        stopOnFailure = true,
                    ),
                    GitStep("unstaged", GIT + listOf("diff", "--numstat", "-z")),
                    GitStep("staged", GIT + listOf("diff", "--cached", "--numstat", "-z")),
                    GitStep(
                        "log",
                        GIT +
                            listOf(
                                "log",
                                "-n",
                                logLimit.coerceIn(1, 200).toString(),
                                "-z",
                                "--format=$GIT_LOG_FORMAT",
                            ),
                    ),
                    GitStep(
                        "branches",
                        GIT +
                            listOf(
                                "for-each-ref",
                                "--format=$GIT_BRANCH_FORMAT",
                                "refs/heads",
                                "refs/remotes",
                            ),
                    ),
                    GitStep("operation", listOf("sh", "-c", OPERATION_PROBE)),
                    GitStep("remotes", listOf("sh", "-c", REMOTES_PROBE)),
                    GitStep("identity", listOf("sh", "-c", IDENTITY_PROBE), optional = true),
                ),
            )
        val envelope =
            when (val run = run(script, readTimeoutMs)) {
                is Run.Failed -> return GitSnapshotResult.Failed(run.failure)
                is Run.Done -> run.envelope
            }
        val status = envelope["status"]!!
        if (!status.ok) {
            val problem = classifyGitError(status.stderr)
            if (problem == GitProblem.NotARepository) return GitSnapshotResult.NotRepository
            return GitSnapshotResult.Failed(
                GitFailure(problem ?: GitProblem.Unknown, scrubGitText(status.stderr, emptyList()))
            )
        }
        val parsed =
            withGitNumstat(
                parseGitStatus(status.stdout),
                unstaged = okOut(envelope["unstaged"], ::parseGitNumstat) ?: emptyList(),
                staged = okOut(envelope["staged"], ::parseGitNumstat) ?: emptyList(),
            )
        return GitSnapshotResult.Ready(
            GitSnapshot(
                status = parsed,
                commits = (okOut(envelope["log"], ::parseGitLog) ?: emptyList()).toImmutableList(),
                branches = okOut(envelope["branches"], ::parseGitBranches) ?: GitBranches(),
                operation = okOut(envelope["operation"], ::parseGitOperation) ?: GitOperation.None,
                remotes =
                    (okOut(envelope["remotes"], ::parseGitRemotes) ?: emptyList())
                        .toImmutableList(),
                identity = okOut(envelope["identity"], ::parseGitIdentity),
            )
        )
    }

    /**
     * One file's patch: the working tree against the index, then the index against HEAD; untracked
     * files as new.
     */
    public suspend fun diff(file: GitFileChange): GitDiffResult {
        val path = file.path
        val steps =
            if (file.isUntracked) {
                // `git diff --no-index` exits 1 when the files differ; that is the answer, not a
                // failure.
                listOf(
                    GitStep(
                        "work",
                        GIT + listOf("diff", "--no-color", "--no-index", "--", "/dev/null", path),
                    )
                )
            } else {
                listOf(
                    GitStep("work", GIT + listOf("diff", "--no-color", "-M", "--", path)),
                    GitStep(
                        "index",
                        GIT + listOf("diff", "--no-color", "--cached", "-M", "--", path),
                    ),
                )
            }
        val envelope =
            when (val run = run(GitScript(directory, steps), readTimeoutMs)) {
                is Run.Failed -> return GitDiffResult.Failed(run.failure)
                is Run.Done -> run.envelope
            }
        val parts = ArrayList<String>()
        for (step in steps) {
            val result = envelope[step.name] ?: continue
            val tolerated = file.isUntracked && result.exitCode == 1
            if (!result.ok && !tolerated) {
                return GitDiffResult.Failed(
                    GitFailure(
                        classifyGitError(result.stderr) ?: GitProblem.Unknown,
                        scrubGitText(result.stderr.trim(), emptyList()),
                    )
                )
            }
            if (result.stdout.isNotBlank()) parts += result.stdout.trimEnd('\n')
        }
        val patch = parts.joinToString("\n")
        val binary = file.binary || BINARY_LINE.containsMatchIn(patch)
        return GitDiffResult.Ready(path, patch, binary)
    }

    // --------------------------------------------------------------- writes

    /**
     * `git pull --ff-only`. A branch without upstream is [GitProblem.NoUpstream]; diverged history
     * cannot fast-forward.
     */
    public suspend fun pull(): GitActionResult {
        val script =
            GitScript(
                directory,
                listOf(
                    GitStep(
                        "head",
                        GIT + listOf("symbolic-ref", "--short", "-q", "HEAD"),
                        stopOnFailure = true,
                    ),
                    GitStep(
                        "upstream",
                        GIT + listOf("rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}"),
                        stopOnFailure = true,
                    ),
                    GitStep("pull", GIT + listOf("pull", "--ff-only", "--no-rebase")),
                    GitStep(
                        "count",
                        GIT + listOf("rev-list", "--count", "ORIG_HEAD..HEAD"),
                        optional = true,
                    ),
                ),
            )
        val envelope =
            when (val run = run(script, networkTimeoutMs)) {
                is Run.Failed -> return GitActionResult.failure(GitAction.Pull, run.failure)
                is Run.Done -> run.envelope
            }
        val head = envelope["head"]!!
        if (!head.ok)
            return fail(
                GitAction.Pull,
                classifyGitError(head.stderr) ?: GitProblem.DetachedHead,
                head,
            )
        val upstream = envelope["upstream"]!!
        if (!upstream.ok) {
            val problem =
                if (classifyGitError(upstream.stderr) == GitProblem.NotARepository)
                    GitProblem.NotARepository
                else GitProblem.NoUpstream
            return fail(GitAction.Pull, problem, upstream)
        }
        val pull = envelope["pull"]!!
        if (!pull.ok) return fail(GitAction.Pull, null, pull)
        val upToDate = "already up to date" in "${pull.stdout}\n${pull.stderr}".lowercase()
        return GitActionResult.success(
            GitAction.Pull,
            if (upToDate) GitActionNote.UpToDate else GitActionNote.Pulled,
            count = if (upToDate) null else envelope["count"]?.stdout?.trim()?.toIntOrNull(),
            target = upstream.stdout.trim(),
        )
    }

    /**
     * `git push`, or `git push --set-upstream <remote> <branch>` the first time (`origin`, else the
     * only remote).
     */
    public suspend fun push(gitHubToken: String? = null): GitActionResult {
        val drop = credentials
        if (gitHubToken.isNullOrEmpty() || drop == null) return pushWithAuth(null, emptyList())
        val file =
            when (val placed = withContext(NonCancellable) { drop.place(gitHubToken) }) {
                is Outcome.Failed ->
                    return GitActionResult.failure(
                        GitAction.Push,
                        GitFailure(GitProblem.EnvironmentUnavailable),
                    )
                is Outcome.Ok -> placed.value
            }
        var result: GitActionResult
        var removed = false
        try {
            val sent = pushWithAuth(file, listOf(gitHubToken))
            result =
                sent.copy(
                    failure =
                        sent.failure?.let {
                            it.copy(details = scrubGitText(it.details, listOf(gitHubToken)))
                        },
                    target = sent.target?.let { scrubGitText(it, listOf(gitHubToken)) },
                )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            result =
                GitActionResult.failure(
                    GitAction.Push,
                    GitFailure(GitProblem.EnvironmentUnavailable),
                )
        } finally {
            // Cancellation must not strand a credential after the exact child process has stopped.
            removed = withContext(NonCancellable) { drop.remove(file) is Outcome.Ok }
        }
        return if (removed) result
        else GitActionResult.failure(GitAction.Push, GitFailure(GitProblem.EnvironmentUnavailable))
    }

    private suspend fun pushWithAuth(file: GitSecretFile?, secrets: List<String>): GitActionResult {
        val plan =
            when (
                val run =
                    run(
                        GitScript(
                            directory,
                            listOf(
                                GitStep(
                                    "head",
                                    GIT + listOf("symbolic-ref", "--short", "-q", "HEAD"),
                                    stopOnFailure = true,
                                ),
                                GitStep(
                                    "upstream",
                                    GIT +
                                        listOf(
                                            "rev-parse",
                                            "--abbrev-ref",
                                            "--symbolic-full-name",
                                            "@{u}",
                                        ),
                                ),
                                GitStep("remotes", GIT + listOf("remote")),
                            ),
                        ),
                        readTimeoutMs,
                    )
            ) {
                is Run.Failed -> return GitActionResult.failure(GitAction.Push, run.failure)
                is Run.Done -> run.envelope
            }
        val head = plan["head"]!!
        if (!head.ok)
            return fail(
                GitAction.Push,
                classifyGitError(head.stderr) ?: GitProblem.DetachedHead,
                head,
            )
        val branch = head.stdout.trim()
        val hasUpstream = plan["upstream"]?.ok ?: false
        val pushArgs: List<String>
        val target: String
        if (hasUpstream) {
            target = plan["upstream"]?.stdout?.trim().orEmpty()
            pushArgs = listOf("push")
        } else {
            val remotes =
                plan["remotes"]
                    ?.stdout
                    .orEmpty()
                    .split('\n')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            val remote =
                when {
                    "origin" in remotes -> "origin"
                    remotes.size == 1 -> remotes.single()
                    else -> null
                }
            if (remote == null) {
                return GitActionResult.failure(
                    GitAction.Push,
                    GitFailure(
                        GitProblem.NoRemote,
                        if (remotes.isEmpty()) "" else "Remotes: ${remotes.joinToString(", ")}",
                    ),
                )
            }
            pushArgs = listOf("push", "--set-upstream", remote, branch)
            target = "$remote/$branch"
        }
        val envelope =
            when (
                val run =
                    run(
                        GitScript(
                            directory,
                            listOf(
                                GitStep(
                                    "push",
                                    GIT +
                                        (if (file == null) emptyList()
                                        else gitHubCredentialOptions) +
                                        pushArgs,
                                )
                            ),
                            environment =
                                if (file == null) emptyMap()
                                else mapOf(GIT_TOKEN_FILE to file.shellPath),
                            removeOnExit = file?.let { listOf(it.shellPath) }.orEmpty(),
                        ),
                        networkTimeoutMs,
                        secrets,
                    )
            ) {
                is Run.Failed -> return GitActionResult.failure(GitAction.Push, run.failure)
                is Run.Done -> run.envelope
            }
        val push = envelope["push"]!!
        if (!push.ok)
            return fail(
                GitAction.Push,
                classifyGitError(push.stderr, usedGitHubToken = file != null),
                push,
            )
        val text = "${push.stdout}\n${push.stderr}".lowercase()
        val note =
            when {
                "everything up-to-date" in text -> GitActionNote.UpToDate
                hasUpstream -> GitActionNote.Pushed
                else -> GitActionNote.PushedAndTracking
            }
        return GitActionResult.success(GitAction.Push, note, target = scrubGitText(target, secrets))
    }

    /**
     * Commits exactly [paths] (staging them first, untracked ones included) with [message], plus
     * [renamedFrom], the old paths of ticked renames; other staged changes stay staged and
     * uncommitted. Nothing chosen or an empty message runs nothing.
     */
    public suspend fun commit(
        paths: List<String>,
        message: String,
        renamedFrom: List<String> = emptyList(),
    ): GitActionResult {
        val selected = paths.filter { it.isNotEmpty() }.distinct()
        // The old half of a ticked rename: committed with it (else its deletion stays staged and
        // the saved tree keeps
        // both files), but not staged again (`git add` refuses a path already gone from the index
        // and the disk).
        val also = renamedFrom.filter { it.isNotEmpty() && it !in selected }.distinct()
        if (selected.isEmpty())
            return GitActionResult.failure(GitAction.Commit, GitFailure(GitProblem.NothingSelected))
        if (message.isBlank())
            return GitActionResult.failure(GitAction.Commit, GitFailure(GitProblem.EmptyMessage))
        val envelope =
            when (
                val run =
                    run(
                        GitScript(
                            directory,
                            listOf(
                                GitStep(
                                    "add",
                                    GIT + listOf("add", "-A", "--") + selected,
                                    stopOnFailure = true,
                                ),
                                GitStep(
                                    "commit",
                                    GIT + listOf("commit", "-m", message, "--") + selected + also,
                                    stopOnFailure = true,
                                ),
                                GitStep("id", GIT + listOf("rev-parse", "--short", "HEAD")),
                                GitStep(
                                    "files",
                                    GIT +
                                        listOf(
                                            "diff-tree",
                                            "--root",
                                            "--no-commit-id",
                                            "--name-only",
                                            "-r",
                                            "-M",
                                            "HEAD",
                                        ),
                                    optional = true,
                                ),
                            ),
                        ),
                        readTimeoutMs,
                    )
            ) {
                is Run.Failed -> return GitActionResult.failure(GitAction.Commit, run.failure)
                is Run.Done -> run.envelope
            }
        for (name in listOf("add", "commit")) {
            val step = envelope[name] ?: continue
            if (!step.ok) {
                // `git commit` prints "nothing to commit" on stdout.
                val text = "${step.stderr}\n${step.stdout}"
                return GitActionResult.failure(
                    GitAction.Commit,
                    GitFailure(
                        classifyGitError(text) ?: GitProblem.Unknown,
                        scrubGitText(text.trim(), emptyList()),
                    ),
                )
            }
        }
        val files = envelope["files"]
        return GitActionResult.success(
            GitAction.Commit,
            GitActionNote.Committed,
            commit = envelope["id"]?.stdout?.trim(),
            count =
                if (files == null || !files.ok) null
                else files.stdout.split('\n').count { it.isNotBlank() },
        )
    }

    /**
     * Switches to [name]. A name only a remote has (`feature/x` from `origin/feature/x`) creates a
     * tracking branch.
     */
    public suspend fun switchBranch(name: String): GitActionResult {
        if (!isValidGitBranchName(name))
            return GitActionResult.failure(
                GitAction.SwitchBranch,
                GitFailure(GitProblem.InvalidBranchName),
            )
        return single(
            GitAction.SwitchBranch,
            GIT + listOf("switch", name),
            GitActionNote.Switched,
            GitProblem.BranchNotFound,
        )
    }

    /** Creates [name] from the current commit and switches to it. */
    public suspend fun createBranch(name: String): GitActionResult {
        if (!isValidGitBranchName(name))
            return GitActionResult.failure(
                GitAction.CreateBranch,
                GitFailure(GitProblem.InvalidBranchName),
            )
        return single(
            GitAction.CreateBranch,
            GIT + listOf("switch", "-c", name),
            GitActionNote.Created,
        )
    }

    /**
     * Throws away every change to [file], staged and unstaged. Untracked files are deleted; new
     * files are removed from the index and the disk; a rename is undone. Unmerged files are
     * refused.
     */
    public suspend fun discard(file: GitFileChange): GitActionResult {
        if (file.isConflicted)
            return GitActionResult.failure(
                GitAction.Discard,
                GitFailure(GitProblem.CannotDiscardConflict),
            )
        val original = file.originalPath
        val steps =
            when {
                file.isUntracked ->
                    listOf(GitStep("discard", GIT + listOf("clean", "-f", "-q", "--", file.path)))
                file.staged == GitChangeKind.Added || file.staged == GitChangeKind.Copied ->
                    listOf(GitStep("discard", GIT + listOf("rm", "-f", "-q", "--", file.path)))
                file.staged == GitChangeKind.Renamed && original != null ->
                    listOf(
                        GitStep(
                            "remove",
                            GIT + listOf("rm", "-f", "-q", "--", file.path),
                            stopOnFailure = true,
                        ),
                        GitStep(
                            "discard",
                            GIT + listOf("restore", "--staged", "--worktree", "--", original),
                        ),
                    )
                else ->
                    listOf(
                        GitStep(
                            "discard",
                            GIT + listOf("restore", "--staged", "--worktree", "--", file.path),
                        )
                    )
            }
        val envelope =
            when (val run = run(GitScript(directory, steps), readTimeoutMs)) {
                is Run.Failed -> return GitActionResult.failure(GitAction.Discard, run.failure)
                is Run.Done -> run.envelope
            }
        for (step in steps) {
            val result = envelope[step.name]
            if (result != null && !result.ok) {
                return GitActionResult.failure(
                    GitAction.Discard,
                    GitFailure(
                        classifyGitError(result.stderr) ?: GitProblem.Unknown,
                        scrubGitText(result.stderr.trim(), emptyList()),
                    ),
                )
            }
        }
        return GitActionResult.success(GitAction.Discard, GitActionNote.Discarded)
    }

    /**
     * Saves the name and email commits are signed with, for this repository only (no `--global`).
     */
    public suspend fun setIdentity(name: String, email: String): GitActionResult {
        if (name.isBlank() || '@' !in email)
            return GitActionResult.failure(
                GitAction.SetIdentity,
                GitFailure(GitProblem.IdentityMissing),
            )
        val envelope =
            when (
                val run =
                    run(
                        GitScript(
                            directory,
                            listOf(
                                GitStep(
                                    "name",
                                    GIT + listOf("config", "user.name", name.trim()),
                                    stopOnFailure = true,
                                ),
                                GitStep(
                                    "email",
                                    GIT + listOf("config", "user.email", email.trim()),
                                ),
                            ),
                        ),
                        readTimeoutMs,
                    )
            ) {
                is Run.Failed -> return GitActionResult.failure(GitAction.SetIdentity, run.failure)
                is Run.Done -> run.envelope
            }
        for (key in listOf("name", "email")) {
            val step = envelope[key]
            if (step == null || !step.ok) {
                return GitActionResult.failure(
                    GitAction.SetIdentity,
                    GitFailure(
                        classifyGitError(step?.stderr.orEmpty()) ?: GitProblem.Unknown,
                        step?.stderr?.trim().orEmpty(),
                    ),
                )
            }
        }
        return GitActionResult.success(GitAction.SetIdentity, GitActionNote.IdentitySaved)
    }

    /**
     * Looks at the remote quietly (`git fetch`), so "behind" and new remote branches are true on
     * the next read. Says [GitProblem.NoUpstream] when the branch tracks nothing (nothing to
     * check).
     */
    public suspend fun fetch(): GitActionResult {
        val envelope =
            when (
                val run =
                    run(
                        GitScript(
                            directory,
                            listOf(
                                GitStep(
                                    "upstream",
                                    GIT +
                                        listOf(
                                            "rev-parse",
                                            "--abbrev-ref",
                                            "--symbolic-full-name",
                                            "@{u}",
                                        ),
                                    stopOnFailure = true,
                                ),
                                GitStep("fetch", GIT + listOf("fetch", "--quiet")),
                            ),
                        ),
                        networkTimeoutMs,
                    )
            ) {
                is Run.Failed -> return GitActionResult.failure(GitAction.Refresh, run.failure)
                is Run.Done -> run.envelope
            }
        val upstream = envelope["upstream"]!!
        if (!upstream.ok) return fail(GitAction.Refresh, GitProblem.NoUpstream, upstream)
        val step = envelope["fetch"]!!
        if (!step.ok) return fail(GitAction.Refresh, null, step)
        return GitActionResult.success(GitAction.Refresh, GitActionNote.UpToDate)
    }

    /**
     * `git remote add <name> <url>`, both checked first; an address with a password is refused
     * before anything runs.
     */
    public suspend fun addRemote(name: String, url: String): GitActionResult {
        val cleanName = name.trim()
        val cleanUrl = url.trim()
        if (!isValidGitRemoteName(cleanName))
            return GitActionResult.failure(
                GitAction.AddRemote,
                GitFailure(GitProblem.InvalidRemoteName),
            )
        gitRemoteAddressProblem(cleanUrl)?.let {
            return GitActionResult.failure(GitAction.AddRemote, GitFailure(it))
        }
        return single(
            GitAction.AddRemote,
            GIT + listOf("remote", "add", cleanName, cleanUrl),
            GitActionNote.RemoteAdded,
        )
    }

    /** Clones [url] into a new folder [folderName] beside this project (in its parent folder). */
    public suspend fun clone(url: String, folderName: String): GitActionResult {
        val cleanUrl = url.trim()
        gitRemoteAddressProblem(cleanUrl)?.let {
            return GitActionResult.failure(GitAction.Clone, GitFailure(it))
        }
        val folder = folderName.trim()
        if (!isValidGitFolderName(folder))
            return GitActionResult.failure(
                GitAction.Clone,
                GitFailure(GitProblem.InvalidFolderName),
            )
        val parent = gitParentFolder(directory)
        val envelope =
            when (
                val run =
                    run(
                        GitScript(
                            parent,
                            listOf(GitStep("clone", GIT + listOf("clone", "--", cleanUrl, folder))),
                        ),
                        networkTimeoutMs,
                    )
            ) {
                is Run.Failed -> return GitActionResult.failure(GitAction.Clone, run.failure)
                is Run.Done -> run.envelope
            }
        val step = envelope["clone"]!!
        if (!step.ok) return fail(GitAction.Clone, null, step)
        return GitActionResult.success(
            GitAction.Clone,
            GitActionNote.Cloned,
            clonedTo = gitCloneTarget(directory, folder),
        )
    }

    // -------------------------------------------------------------- helpers

    private suspend fun single(
        action: GitAction,
        argv: List<String>,
        note: GitActionNote,
        fallback: GitProblem = GitProblem.Unknown,
    ): GitActionResult {
        val envelope =
            when (
                val run = run(GitScript(directory, listOf(GitStep("run", argv))), readTimeoutMs)
            ) {
                is Run.Failed -> return GitActionResult.failure(action, run.failure)
                is Run.Done -> run.envelope
            }
        val step = envelope["run"]!!
        if (step.ok) return GitActionResult.success(action, note)
        return GitActionResult.failure(
            action,
            GitFailure(
                classifyGitError(step.stderr) ?: fallback,
                scrubGitText(step.stderr.trim(), emptyList()),
            ),
        )
    }

    private fun fail(
        action: GitAction,
        problem: GitProblem?,
        step: GitStepResult,
    ): GitActionResult {
        val details = scrubGitText("${step.stderr}\n${step.stdout}".trim(), emptyList())
        return GitActionResult.failure(
            action,
            GitFailure(problem ?: classifyGitError(details) ?: GitProblem.Unknown, details),
        )
    }

    private sealed interface Run {
        data class Done(val envelope: GitEnvelope) : Run

        data class Failed(val failure: GitFailure) : Run
    }

    private suspend fun run(
        script: GitScript,
        timeoutMs: Long,
        secrets: List<String> = emptyList(),
    ): Run {
        val text =
            try {
                script.render()
            } catch (e: IllegalArgumentException) {
                // A NUL byte in a path or message: refused before anything runs.
                return Run.Failed(GitFailure(GitProblem.Unknown))
            }
        val output =
            when (val reply = shell.run(text, timeoutMs)) {
                is GitShellReply.Failed ->
                    return Run.Failed(
                        GitFailure(
                            when (reply.failure) {
                                GitShellFailure.Unavailable -> GitProblem.EnvironmentUnavailable
                                GitShellFailure.Unsupported -> GitProblem.ServerCannotRunCommands
                                GitShellFailure.TimedOut -> GitProblem.TimedOut
                            },
                            reply.details,
                        )
                    )
                is GitShellReply.Output -> reply.text
            }
        val parsed = GitEnvelope.parse(output)
        val envelope =
            if (secrets.isEmpty()) parsed
            else
                parsed.copy(
                    steps =
                        parsed.steps.mapValues { (_, step) ->
                            step.copy(
                                stdout = scrubGitText(step.stdout, secrets),
                                stderr = scrubGitText(step.stderr, secrets),
                            )
                        }
                )
        envelope.problem?.let { problem ->
            return Run.Failed(
                GitFailure(
                    when (problem) {
                        GitEnvelopeProblem.FolderMissing -> GitProblem.FolderMissing
                        GitEnvelopeProblem.GitMissing -> GitProblem.GitMissing
                        GitEnvelopeProblem.NoTemp -> GitProblem.EnvironmentUnavailable
                        GitEnvelopeProblem.Truncated -> GitProblem.OutputTooLarge
                    }
                )
            )
        }
        // Every required step must have reported unless an earlier stopOnFailure step failed.
        for ((index, step) in script.steps.withIndex()) {
            if (envelope[step.name] != null || step.optional) continue
            val earlierStopped =
                script.steps.take(index).any { it.stopOnFailure && envelope[it.name]?.ok == false }
            if (!earlierStopped) return Run.Failed(GitFailure(GitProblem.OutputTooLarge))
        }
        return Run.Done(envelope)
    }

    private fun <T> okOut(step: GitStepResult?, parse: (String) -> T): T? =
        if (step == null || !step.ok) null else parse(step.stdout)

    override fun toString(): String = "GitRepository"

    public companion object {
        private val GIT = listOf("git", "--no-pager", "-c", "color.ui=false")

        private val BINARY_LINE = Regex("^Binary files .* differ$", RegexOption.MULTILINE)

        /** Prints the marker files of a half-done merge, rebase or cherry-pick. */
        private const val OPERATION_PROBE: String =
            "g=\$(git rev-parse --git-dir) || exit 1; for f in MERGE_HEAD REBASE_HEAD CHERRY_PICK_HEAD REVERT_HEAD " +
                "rebase-merge rebase-apply; do test -e \"\$g/\$f\" && echo \"\$f\"; done; exit 0"

        /** The name and email a commit here would carry, one per line. */
        private const val IDENTITY_PROBE: String =
            "printf '%s\\n%s\\n' \"\$(git config user.name)\" \"\$(git config user.email)\"; exit 0"

        /** Remote names and addresses with any user info removed before printing. */
        private const val REMOTES_PROBE: String =
            "git config --get-regexp '^remote\\..*\\.url\$' | sed -e 's#://[^/@]*@#://#'; exit 0"
    }
}
