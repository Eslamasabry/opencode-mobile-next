package io.github.eslamasabry.oc.feature.git.presentation

import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.engine.git.GitActionResult
import io.github.eslamasabry.oc.engine.git.GitBusy
import io.github.eslamasabry.oc.engine.git.GitDiffView
import io.github.eslamasabry.oc.engine.git.GitFailure
import io.github.eslamasabry.oc.engine.git.GitFileChange
import io.github.eslamasabry.oc.engine.git.GitHubSignIn
import io.github.eslamasabry.oc.engine.git.GitPageView
import io.github.eslamasabry.oc.engine.git.GitSnapshot
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/** A sheet of the Git page (screen state, never a route: plan 07 §2.2). */
public enum class GitSheet { Branches }

/** A question the page asks before an act that cannot be undone. */
public sealed interface GitConfirm {
    public data class Discard(val file: GitFileChange) : GitConfirm

    public data object SignOut : GitConfirm
}

/** Everything the Git screen draws. Words are chosen by the screen from these values; nothing here is copy. */
public data class GitUi(
    val view: GitPageView = GitPageView.Loading,
    val busy: GitBusy? = null,
    /** The last finished action, said once on the status line. */
    val result: GitActionResult? = null,
    val resultDetails: Redacted? = null,
    /** Save worked, send did not: the save is kept and the line says so (B-NAV-021). */
    val savedNotSent: Boolean = false,
    /** A refresh failed while the last snapshot is still shown (B-NAV-020). */
    val refreshFailure: GitFailure? = null,
    val refreshDetails: Redacted? = null,
    /** Details of a first read that failed ([GitPageView.Failed]). */
    val viewDetails: Redacted? = null,
    /** Paths ticked for the next save. */
    val selected: ImmutableSet<String> = persistentSetOf(),
    val diff: GitDiffView? = null,
    val signIn: GitHubSignIn = GitHubSignIn.Loading,
    val signInDetails: Redacted? = null,
    /** The server's saved name, for "uses the Git sign-in set up on …". */
    val placeName: String? = null,
    val sheet: GitSheet? = null,
    val confirm: GitConfirm? = null,
    /** Bumped when a write finished: the screen clears its message field once. */
    val generation: Int = 0,
) {
    val snapshot: GitSnapshot? get() = (view as? GitPageView.Ready)?.snapshot

    /** True while an action runs: every write button is held (one action at a time, B-NAV-020). */
    val working: Boolean get() = busy != null
}

public interface GitActions {
    public fun back()

    public fun refresh()

    /** Get latest (`git pull --ff-only`). */
    public fun pull()

    /** Send (`git push`, setting the upstream the first time). */
    public fun push()

    /** Ticks or unticks one changed file for the next save. */
    public fun toggleFile(path: String)

    /** Save the ticked files with [message]. */
    public fun commit(message: String)

    /** Save, then send, in one tap (B-NAV-021). */
    public fun commitAndPush(message: String)

    public fun openBranches()

    public fun closeSheet()

    public fun switchBranch(name: String)

    public fun createBranch(name: String)

    public fun openDiff(path: String)

    public fun closeDiff()

    public fun askDiscard(path: String)

    /** The open question was answered yes. */
    public fun confirm()

    public fun dismissConfirm()

    public fun setIdentity(name: String, email: String)

    public fun signIn()

    public fun cancelSignIn()

    public fun askSignOut()

    /** Opens the GitHub page where the code is entered. */
    public fun openGitHub()

    public fun manageGitHubAccess()

    public fun dismissResult()
}
