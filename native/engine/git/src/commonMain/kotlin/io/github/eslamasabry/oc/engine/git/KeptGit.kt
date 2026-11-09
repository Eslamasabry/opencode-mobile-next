package io.github.eslamasabry.oc.engine.git

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable

/**
 * Cache-first (owner rule, 2026-11-09): the Git page opens on the snapshot it last read (branch, ahead/behind,
 * changed files, recent saves) and the live read replaces it in place. The app binds this to its last-known store
 * (per profile and server, size-capped, cleared with the profile); without a binding the page opens as before.
 */
public interface GitLastKnown {
    /** Memory only, for the first frame. */
    public fun peek(ref: ConversationRef): KeptGit?

    public suspend fun read(ref: ConversationRef): KeptGit?

    public suspend fun keep(ref: ConversationRef, kept: KeptGit)
}

/**
 * The display facts of a [GitSnapshot], and nothing else: no author emails, no identity, no remote addresses with
 * user info (the snapshot's remotes already carry none, but the page only needs names and kinds here).
 */
@Serializable
public data class KeptGit(
    val head: String? = null,
    val commit: String? = null,
    val upstream: String? = null,
    val ahead: Int = 0,
    val behind: Int = 0,
    val files: List<KeptFile> = emptyList(),
    val commits: List<KeptCommit> = emptyList(),
    val remotes: List<KeptRemote> = emptyList(),
    val localBranches: List<KeptBranch> = emptyList(),
    val remoteBranches: List<KeptBranch> = emptyList(),
) {
    @Serializable
    public data class KeptFile(
        val path: String,
        val originalPath: String? = null,
        val staged: GitChangeKind? = null,
        val unstaged: GitChangeKind? = null,
        val conflict: GitConflictKind? = null,
        val submodule: Boolean = false,
        val addedLines: Int? = null,
        val deletedLines: Int? = null,
        val stagedAddedLines: Int? = null,
        val stagedDeletedLines: Int? = null,
        val binary: Boolean = false,
    )

    @Serializable
    public data class KeptCommit(
        val id: String,
        val shortId: String,
        val authorName: String,
        val time: Long,
        val subject: String,
        val merge: Boolean = false,
    )

    @Serializable public data class KeptRemote(val name: String, val kind: GitRemoteKind)

    @Serializable
    public data class KeptBranch(
        val name: String,
        val remote: Boolean,
        val remoteName: String? = null,
        val current: Boolean = false,
        val ahead: Int = 0,
        val behind: Int = 0,
        val time: Long? = null,
        val subject: String? = null,
    )

    /** The snapshot this draws; the identity stays unread (null) until the live read brings it. */
    public fun toSnapshot(): GitSnapshot =
        GitSnapshot(
            status =
                GitStatus(
                    GitBranchState(head, commit, upstream, ahead, behind),
                    files
                        .map {
                            GitFileChange(
                                it.path, it.originalPath, it.staged, it.unstaged, it.conflict, it.submodule,
                                it.addedLines, it.deletedLines, it.stagedAddedLines, it.stagedDeletedLines, it.binary,
                            )
                        }
                        .toImmutableList(),
                ),
            commits =
                commits
                    .map {
                        GitCommit(
                            it.id, it.shortId, it.authorName, "", it.time, it.subject,
                            parents = if (it.merge) listOf("", "").toImmutableList() else kotlinx.collections.immutable.persistentListOf(),
                        )
                    }
                    .toImmutableList(),
            branches =
                GitBranches(
                    localBranches.map(::branch).toImmutableList(),
                    remoteBranches.map(::branch).toImmutableList(),
                ),
            remotes = remotes.map { GitRemote(it.name, it.kind) }.toImmutableList(),
        )

    private fun branch(b: KeptBranch) =
        GitBranch(b.name, b.remote, b.remoteName, current = b.current, ahead = b.ahead, behind = b.behind, timeEpochSeconds = b.time, subject = b.subject)

    public companion object {
        public const val MAX_FILES: Int = 300
        public const val MAX_COMMITS: Int = 30
        public const val MAX_BRANCHES: Int = 60

        public fun of(s: GitSnapshot): KeptGit =
            KeptGit(
                head = s.status.branch.head,
                commit = s.status.branch.commit,
                upstream = s.status.branch.upstream,
                ahead = s.status.branch.ahead,
                behind = s.status.branch.behind,
                files =
                    s.status.files.take(MAX_FILES).map {
                        KeptFile(
                            it.path, it.originalPath, it.staged, it.unstaged, it.conflict, it.submodule,
                            it.addedLines, it.deletedLines, it.stagedAddedLines, it.stagedDeletedLines, it.binary,
                        )
                    },
                commits =
                    s.commits.take(MAX_COMMITS).map {
                        KeptCommit(it.id, it.shortId, it.authorName, it.timeEpochSeconds, it.subject, it.isMerge)
                    },
                remotes = s.remotes.map { KeptRemote(it.name, it.kind) },
                localBranches = s.branches.local.take(MAX_BRANCHES).map(::keptBranch),
                remoteBranches = s.branches.remote.take(MAX_BRANCHES).map(::keptBranch),
            )

        private fun keptBranch(b: GitBranch) =
            KeptBranch(b.name, b.remote, b.remoteName, b.current, b.ahead, b.behind, b.timeEpochSeconds, b.subject)
    }
}
