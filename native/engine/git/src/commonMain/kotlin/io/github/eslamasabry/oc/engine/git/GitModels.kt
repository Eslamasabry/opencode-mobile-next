package io.github.eslamasabry.oc.engine.git

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

// Typed state of one Git repository, as the Git page shows it (port of lib/domain/git/git_models.dart).

/** What happened to a file on one side (index or working tree). */
public enum class GitChangeKind { Added, Modified, Deleted, Renamed, Copied, TypeChanged, Untracked, Ignored }

/** Which side of an unmerged path did what (`git status` `u` entries). */
public enum class GitConflictKind { BothModified, BothAdded, BothDeleted, AddedByUs, AddedByThem, DeletedByUs, DeletedByThem }

/**
 * One path with changes. [staged] is the index side, [unstaged] the working tree; an untracked file has only
 * [unstaged] = [GitChangeKind.Untracked].
 */
public data class GitFileChange(
    /** Repository-relative path (the new name of a rename). */
    val path: String,
    /** The old name of a rename or the source of a copy. */
    val originalPath: String? = null,
    val staged: GitChangeKind? = null,
    val unstaged: GitChangeKind? = null,
    /** Non-null while the path is unmerged. */
    val conflict: GitConflictKind? = null,
    val submodule: Boolean = false,
    val addedLines: Int? = null,
    val deletedLines: Int? = null,
    val stagedAddedLines: Int? = null,
    val stagedDeletedLines: Int? = null,
    /** Git reported `-` for the counts: the file is binary. */
    val binary: Boolean = false,
) {
    val isConflicted: Boolean get() = conflict != null
    val isUntracked: Boolean get() = unstaged == GitChangeKind.Untracked

    /** The one kind a row shows: the index side, then the working tree. */
    val kind: GitChangeKind? get() = staged ?: unstaged

    val totalAdded: Int? get() = sum(addedLines, stagedAddedLines)
    val totalDeleted: Int? get() = sum(deletedLines, stagedDeletedLines)

    private fun sum(a: Int?, b: Int?): Int? = if (a == null && b == null) null else (a ?: 0) + (b ?: 0)
}

/** The branch line of `git status --porcelain=v2 --branch`. */
public data class GitBranchState(
    /** Current branch name; null when HEAD is detached. */
    val head: String? = null,
    /** HEAD's commit id; null before the first commit. */
    val commit: String? = null,
    /** `origin/main`; null when the branch tracks nothing. */
    val upstream: String? = null,
    val ahead: Int = 0,
    val behind: Int = 0,
) {
    val detached: Boolean get() = head == null
    val unborn: Boolean get() = commit == null
    val hasUpstream: Boolean get() = upstream != null
}

public data class GitStatus(val branch: GitBranchState, val files: ImmutableList<GitFileChange> = persistentListOf()) {
    val hasConflicts: Boolean get() = files.any { it.isConflicted }
    val clean: Boolean get() = files.isEmpty()
}

/** One commit of `git log`. */
public data class GitCommit(
    val id: String,
    val shortId: String,
    val authorName: String,
    val authorEmail: String,
    val timeEpochSeconds: Long,
    val subject: String,
    val parents: ImmutableList<String> = persistentListOf(),
    val refs: ImmutableList<String> = persistentListOf(),
) {
    val isMerge: Boolean get() = parents.size > 1

    /** Never prints the author's email. */
    override fun toString(): String = "GitCommit($shortId)"
}

/** One local or remote-tracking branch. */
public data class GitBranch(
    /** `main` for a local branch, `origin/main` for a remote one. */
    val name: String,
    val remote: Boolean,
    /** `origin` for `origin/main`; null for local branches. */
    val remoteName: String? = null,
    val commit: String? = null,
    val upstream: String? = null,
    val ahead: Int = 0,
    val behind: Int = 0,
    /** The upstream it tracked was deleted on the remote. */
    val upstreamGone: Boolean = false,
    val current: Boolean = false,
    val timeEpochSeconds: Long? = null,
    val subject: String? = null,
) {
    /** The name a local branch made from this remote one would get (`origin/feature/x` → `feature/x`). */
    val localName: String
        get() =
            if (remote && remoteName != null && name.startsWith("$remoteName/")) name.substring(remoteName.length + 1)
            else name
}

public data class GitBranches(
    val local: ImmutableList<GitBranch> = persistentListOf(),
    val remote: ImmutableList<GitBranch> = persistentListOf(),
) {
    val current: GitBranch? get() = local.firstOrNull { it.current }

    /** Remote branches with no local branch of the same short name yet. */
    val remoteOnly: List<GitBranch>
        get() {
            val names = local.map { it.name }.toSet()
            return remote.filter { it.localName !in names }
        }
}

/** A merge, rebase, cherry-pick or revert left half-done in the repository. */
public enum class GitOperation { None, Merge, Rebase, CherryPick, Revert }

/** Where a remote points: GitHub over HTTPS or SSH, or somewhere else. */
public enum class GitRemoteKind { GitHubHttps, GitHubSsh, Other }

public data class GitRemote(
    val name: String,
    val kind: GitRemoteKind,
    /** Where the remote points, with any user info removed so a credential never reaches the screen. */
    val address: String? = null,
) {
    val isGitHub: Boolean get() = kind != GitRemoteKind.Other
}

/** The name and email Git would put on a commit made here. */
public data class GitIdentity(val name: String = "", val email: String = "") {
    /** Missing, or a placeholder an agent or installer left on the device (`aiteam@localhost`). */
    val needsSetup: Boolean
        get() {
            val mail = email.trim().lowercase()
            return name.isBlank() || mail.isEmpty() || '@' !in mail || mail.endsWith("@localhost") ||
                mail.endsWith("@localhost.localdomain")
        }

    override fun toString(): String = "GitIdentity(set=${!needsSetup})"
}

/** Everything the page shows, read in one round trip. */
public data class GitSnapshot(
    val status: GitStatus,
    val commits: ImmutableList<GitCommit> = persistentListOf(),
    val branches: GitBranches = GitBranches(),
    val operation: GitOperation = GitOperation.None,
    val remotes: ImmutableList<GitRemote> = persistentListOf(),
    /** Null when it could not be read: nothing is asked then. */
    val identity: GitIdentity? = null,
) {
    val hasRemote: Boolean get() = remotes.isNotEmpty()

    /** The remote a first push would go to: `origin`, else the only one. */
    val pushRemote: GitRemote?
        get() = remotes.firstOrNull { it.name == "origin" } ?: remotes.singleOrNull()
}
