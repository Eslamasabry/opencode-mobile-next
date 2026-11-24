package io.github.eslamasabry.oc.feature.git.presentation

/** The parts of the Git page of a project, in the order a person meets them. */
public enum class GitSection {
    /** A failed refresh over the last look, or a Git operation in progress. */
    Notices,

    /** The changed files (or the words saying there are none). */
    Changes,

    /** The message and the Save buttons, with the name form right above them when Git has no name yet (the form alone when nothing changed). */
    Save,

    /** What the last action did, in place under what the person pressed. */
    Result,

    /** Branch, Get latest, Send. */
    Sync,

    /** Recent saves. */
    History,

    /** Remotes, copying a repository, repair and the server's project tools: rare, folded, last. */
    Tools,
}

/**
 * Issue 147 walk F-021: what a person came for goes first (what changed, then saving it with a message, then history);
 * rare tools go last and folded, and an action's result sits under the buttons that ran it, never above the fold.
 */
public fun gitSections(hasChanges: Boolean, hasHistory: Boolean, needsIdentity: Boolean = false): List<GitSection> =
    buildList {
        add(GitSection.Notices)
        add(GitSection.Changes)
        if (hasChanges || needsIdentity) add(GitSection.Save)
        add(GitSection.Result)
        add(GitSection.Sync)
        if (hasHistory) add(GitSection.History)
        add(GitSection.Tools)
    }

/**
 * Get latest and Send are the same kind of row: while the project has no remote, either one's tap opens Remotes to add
 * one, and both say so with the same chevron (issue 147 recheck F-021: only Get latest had it).
 */
public fun gitSyncOpensRemotes(hasRemote: Boolean, detached: Boolean, operationInProgress: Boolean): Boolean =
    !hasRemote && !detached && !operationInProgress

/**
 * Get latest can also link a branch that tracks nothing yet (B-GIT-157): its row is open when the snapshot lists a
 * branch of the same name on the remote a first push would go to, and its line is then the Send row's "First time,
 * links this branch". Only the same name counts; a branch the remote lacks still says to send it first.
 */
public fun gitPullLinksBranch(
    hasUpstream: Boolean,
    head: String?,
    pushRemote: String?,
    remoteBranches: List<String>,
): Boolean = !hasUpstream && head != null && pushRemote != null && "$pushRemote/$head" in remoteBranches
