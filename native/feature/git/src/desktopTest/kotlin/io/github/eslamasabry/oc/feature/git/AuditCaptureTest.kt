package io.github.eslamasabry.oc.feature.git

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.engine.git.GitAction
import io.github.eslamasabry.oc.engine.git.GitActionNote
import io.github.eslamasabry.oc.engine.git.GitActionResult
import io.github.eslamasabry.oc.engine.git.GitBranch
import io.github.eslamasabry.oc.engine.git.GitBranchState
import io.github.eslamasabry.oc.engine.git.GitBranches
import io.github.eslamasabry.oc.engine.git.GitBusy
import io.github.eslamasabry.oc.engine.git.GitChangeKind
import io.github.eslamasabry.oc.engine.git.GitCheck
import io.github.eslamasabry.oc.engine.git.GitCommit
import io.github.eslamasabry.oc.engine.git.GitConflictKind
import io.github.eslamasabry.oc.engine.git.GitDiffView
import io.github.eslamasabry.oc.engine.git.GitFailure
import io.github.eslamasabry.oc.engine.git.GitFileChange
import io.github.eslamasabry.oc.engine.git.GitHubRepository
import io.github.eslamasabry.oc.engine.git.GitHubSignIn
import io.github.eslamasabry.oc.engine.git.GitHubSignInProblem
import io.github.eslamasabry.oc.engine.git.GitIdentity
import io.github.eslamasabry.oc.engine.git.GitOperation
import io.github.eslamasabry.oc.engine.git.GitPageView
import io.github.eslamasabry.oc.engine.git.GitProblem
import io.github.eslamasabry.oc.engine.git.GitRemote
import io.github.eslamasabry.oc.engine.git.GitRemoteKind
import io.github.eslamasabry.oc.engine.git.GitSnapshot
import io.github.eslamasabry.oc.engine.git.GitStatus
import io.github.eslamasabry.oc.engine.git.GitUnavailableReason
import io.github.eslamasabry.oc.feature.git.presentation.GitActions
import io.github.eslamasabry.oc.feature.git.presentation.GitConfirm
import io.github.eslamasabry.oc.feature.git.presentation.GitRepairUi
import io.github.eslamasabry.oc.feature.git.presentation.GitRepoPicker
import io.github.eslamasabry.oc.feature.git.presentation.GitSheet
import io.github.eslamasabry.oc.feature.git.presentation.GitUi
import io.github.eslamasabry.oc.feature.git.ui.GitScreen
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** Audit capture only: the Git page in every state its model allows. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private val m = "git"

    private val actions =
        object : GitActions {
            override fun back() {}
            override fun refresh() {}
            override fun pull() {}
            override fun push() {}
            override fun toggleFile(path: String) {}
            override fun commit(message: String) {}
            override fun commitAndPush(message: String) {}
            override fun openBranches() {}
            override fun openRemotes() {}
            override fun openClone() {}
            override fun addRemote(name: String, url: String) {}
            override fun clone(url: String, folder: String) {}
            override fun initialize() {}
            override fun openPublish() {}
            override fun publish(name: String, isPrivate: Boolean) {}
            override fun pickGitHubRepository() {}
            override fun closeRepositoryChoices() {}
            override fun closeSheet() {}
            override fun switchBranch(name: String) {}
            override fun createBranch(name: String) {}
            override fun openDiff(path: String) {}
            override fun closeDiff() {}
            override fun askDiscard(path: String) {}
            override fun confirm() {}
            override fun dismissConfirm() {}
            override fun setIdentity(name: String, email: String) {}
            override fun signIn() {}
            override fun cancelSignIn() {}
            override fun askSignOut() {}
            override fun openGitHub() {}
            override fun manageGitHubAccess() {}
            override fun dismissResult() {}
            override fun repairObjects() {}
        }

    @Composable
    private fun Page(state: GitUi) {
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) { GitScreen(state, actions) }
    }

    private val changed = GitFileChange("src/app.kt", unstaged = GitChangeKind.Modified, addedLines = 3, deletedLines = 1)
    private val added = GitFileChange("src/new_file.kt", unstaged = GitChangeKind.Untracked)
    private val staged = GitFileChange("README.md", staged = GitChangeKind.Modified, stagedAddedLines = 10, stagedDeletedLines = 2)
    private val longPath = "modules/feature/deeply/nested/package/structure/with/an/extremely_long_generated_file_name_that_overflows.kt"
    private val longFile = GitFileChange(longPath, unstaged = GitChangeKind.Modified, addedLines = 120, deletedLines = 45)
    private val binary = GitFileChange("assets/logo.png", unstaged = GitChangeKind.Modified, binary = true)
    private val deleted = GitFileChange("old/unused.kt", unstaged = GitChangeKind.Deleted, deletedLines = 40)
    private val conflict = GitFileChange("src/conflict.kt", conflict = GitConflictKind.BothModified)

    private val origin = GitRemote("origin", GitRemoteKind.GitHubHttps, "https://github.com/o/r.git")

    private fun snap(
        files: List<GitFileChange> = listOf(changed, added, staged),
        branch: GitBranchState = GitBranchState(head = "main", commit = "abc1234", upstream = "origin/main", ahead = 2),
        remotes: List<GitRemote> = listOf(origin),
        operation: GitOperation = GitOperation.None,
        commits: Boolean = true,
        identity: GitIdentity? = GitIdentity("Octo Cat", "octo@example.com"),
    ) = GitSnapshot(
        status = GitStatus(branch, persistentListOf(*files.toTypedArray())),
        commits = if (commits) persistentListOf(
            GitCommit("a1", "a1b2c3d", "Octo Cat", "octo@example.com", 1_760_000_000, "Add the combo counter"),
            GitCommit("b2", "b2c3d4e", "Octo Cat", "octo@example.com", 1_759_900_000, "Fix the screen shake on very small devices when the user rotates the phone twice in a row"),
        ) else persistentListOf(),
        branches = GitBranches(
            local = persistentListOf(GitBranch("main", false, current = true, upstream = "origin/main"), GitBranch("feature/combo", false)),
            remote = persistentListOf(GitBranch("origin/main", true, "origin"), GitBranch("origin/release/2.0", true, "origin")),
        ),
        operation = operation,
        remotes = persistentListOf(*remotes.toTypedArray()),
        identity = identity,
    )

    private fun ready(s: GitSnapshot = snap()) = GitUi(view = GitPageView.Ready(s), signIn = GitHubSignIn.SignedOut())

    @Test
    fun pageViews() = Audit.guard("${m}__page__build") {
        Audit.shoot(m, "git", "loading") { Page(GitUi(view = GitPageView.Loading, signIn = GitHubSignIn.SignedOut())) }
        Audit.shoot(m, "git", "not-repository") { Page(GitUi(view = GitPageView.NotRepository, signIn = GitHubSignIn.SignedOut())) }
        Audit.shoot(m, "git", "unavailable-server") {
            Page(GitUi(view = GitPageView.Unavailable(GitUnavailableReason.ServerCannotRunCommands), signIn = GitHubSignIn.SignedOut()))
        }
        Audit.shoot(m, "git", "unavailable-not-connected") {
            Page(GitUi(view = GitPageView.Unavailable(GitUnavailableReason.NotConnected), signIn = GitHubSignIn.SignedOut()))
        }
        Audit.shoot(m, "git", "unavailable-no-project") {
            Page(GitUi(view = GitPageView.Unavailable(GitUnavailableReason.NoProject), signIn = GitHubSignIn.SignedOut()))
        }
        Audit.shoot(m, "git", "failed-network") {
            Page(GitUi(view = GitPageView.Failed(GitFailure(GitProblem.Network)), signIn = GitHubSignIn.SignedOut()))
        }
        Audit.shoot(m, "git", "failed-git-missing") {
            Page(GitUi(view = GitPageView.Failed(GitFailure(GitProblem.GitMissing)), signIn = GitHubSignIn.SignedOut()))
        }
    }

    @Test
    fun readyStates() = Audit.guard("${m}__ready__build") {
        Audit.shoot(m, "git", "ready-changes") { Page(ready().copy(selected = persistentSetOf("src/app.kt"), offersGitHubSignIn = true)) }
        Audit.shoot(m, "git", "ready-clean") { Page(ready(snap(files = emptyList(), branch = GitBranchState("main", "abc", "origin/main")))) }
        Audit.shoot(m, "git", "ready-behind") { Page(ready(snap(branch = GitBranchState("main", "abc", "origin/main", ahead = 0, behind = 3)))) }
        Audit.shoot(m, "git", "ready-no-upstream") { Page(ready(snap(branch = GitBranchState("feature/combo", "abc", null)))) }
        Audit.shoot(m, "git", "ready-detached") { Page(ready(snap(branch = GitBranchState(null, "abc1234", null)))) }
        Audit.shoot(m, "git", "ready-unborn") { Page(ready(snap(files = listOf(added), branch = GitBranchState(), commits = false, remotes = emptyList()))) }
        Audit.shoot(m, "git", "ready-no-remote") { Page(ready(snap(remotes = emptyList()))) }
        Audit.shoot(m, "git", "ready-many-kinds") {
            Page(ready(snap(files = listOf(changed, added, staged, longFile, binary, deleted))).copy(selected = persistentSetOf("README.md", longPath)))
        }
        Audit.shoot(m, "git", "ready-conflicts") {
            Page(ready(snap(files = listOf(conflict, changed), operation = GitOperation.Merge)))
        }
        Audit.shoot(m, "git", "ready-rebase-in-progress") { Page(ready(snap(operation = GitOperation.Rebase))) }
        Audit.shoot(m, "git", "ready-needs-identity") { Page(ready(snap(identity = GitIdentity())).copy(selected = persistentSetOf("src/app.kt"))) }
        Audit.shoot(m, "git", "ready-server-place") { Page(ready().copy(placeName = "Home box", directory = "/srv/projects/my-app")) }
        Audit.shoot(m, "git", "write-it-for-me") {
            Page(ready().copy(selected = persistentSetOf("src/app.kt"), summary = "Added a combo counter and screen shake."))
        }
        Audit.shoot(m, "git", "check-failed") { Page(ready().copy(check = GitCheck.Failed)) }
        Audit.shoot(m, "git", "check-running") { Page(ready().copy(check = GitCheck.Checking)) }
        Audit.shoot(m, "git", "repair-offered") { Page(ready().copy(repair = GitRepairUi(available = true))) }
    }

    @Test
    fun busyAndResults() = Audit.guard("${m}__busy__build") {
        for (b in listOf(GitBusy.Refresh, GitBusy.Pull, GitBusy.Push, GitBusy.SaveThenSend, GitBusy.Commit)) {
            Audit.shoot(m, "git-busy", b.name.lowercase()) { Page(ready().copy(busy = b, selected = persistentSetOf("src/app.kt"))) }
        }
        Audit.shoot(m, "git-result", "pushed") {
            Page(ready().copy(result = GitActionResult.success(GitAction.Push, GitActionNote.Pushed, target = "origin/main")))
        }
        Audit.shoot(m, "git-result", "committed") {
            Page(ready().copy(result = GitActionResult.success(GitAction.Commit, GitActionNote.Committed, commit = "a1b2c3d", count = 2)))
        }
        Audit.shoot(m, "git-result", "cannot-fast-forward") {
            Page(ready().copy(result = GitActionResult.failure(GitAction.Pull, GitFailure(GitProblem.CannotFastForward, "fatal: not possible to fast-forward"))))
        }
        Audit.shoot(m, "git-result", "push-rejected") {
            Page(ready().copy(result = GitActionResult.failure(GitAction.Push, GitFailure(GitProblem.PushRejected))))
        }
        Audit.shoot(m, "git-result", "saved-not-sent") {
            Page(ready().copy(savedNotSent = true, result = GitActionResult.failure(GitAction.Push, GitFailure(GitProblem.Network))))
        }
        Audit.shoot(m, "git-result", "refresh-failed") { Page(ready().copy(refreshFailure = GitFailure(GitProblem.Network))) }
    }

    @Test
    fun signIn() = Audit.guard("${m}__signin__build") {
        Audit.shoot(m, "git-signin", "signed-out-offered") { Page(ready().copy(offersGitHubSignIn = true)) }
        Audit.shoot(m, "git-signin", "requesting-code") { Page(ready().copy(signIn = GitHubSignIn.RequestingCode, offersGitHubSignIn = true)) }
        Audit.shoot(m, "git-signin", "waiting-for-user") {
            Page(ready().copy(signIn = GitHubSignIn.WaitingForUser("WDJB-MJHT", "https://github.com/login/device", 0), offersGitHubSignIn = true))
        }
        Audit.shoot(m, "git-signin", "signed-in") { Page(ready().copy(signIn = GitHubSignIn.SignedIn("octocat"), offersGitHubSignIn = true)) }
        Audit.shoot(m, "git-signin", "expired") {
            Page(ready().copy(signIn = GitHubSignIn.SignedOut(GitHubSignInProblem.Expired), offersGitHubSignIn = true))
        }
        Audit.shoot(m, "git-signin", "network-problem") {
            Page(ready().copy(signIn = GitHubSignIn.SignedOut(GitHubSignInProblem.Network), offersGitHubSignIn = true))
        }
    }

    @Test
    fun sheets() = Audit.guard("${m}__sheets__build") {
        val signedIn = ready().copy(signIn = GitHubSignIn.SignedIn("octocat"), offersGitHubSignIn = true)
        Audit.shoot(m, "git-sheet", "branches") { Page(ready().copy(sheet = GitSheet.Branches)) }
        Audit.shoot(m, "git-sheet", "remotes") { Page(ready().copy(sheet = GitSheet.Remotes)) }
        Audit.shoot(m, "git-sheet", "remotes-signed-in") { Page(signedIn.copy(sheet = GitSheet.Remotes)) }
        Audit.shoot(m, "git-sheet", "clone") { Page(ready().copy(sheet = GitSheet.Clone, directory = "/home/oc/my-app")) }
        Audit.shoot(m, "git-sheet", "publish") {
            Page(ready(snap(remotes = emptyList())).copy(signIn = GitHubSignIn.SignedIn("octocat"), offersGitHubSignIn = true, sheet = GitSheet.Publish, directory = "/home/oc/my app"))
        }
        Audit.shoot(m, "git-sheet", "repo-picker-loading") { Page(signedIn.copy(sheet = GitSheet.Remotes, repoPicker = GitRepoPicker(loading = true))) }
        Audit.shoot(m, "git-sheet", "repo-picker-none") { Page(signedIn.copy(sheet = GitSheet.Remotes, repoPicker = GitRepoPicker(none = true))) }
        Audit.shoot(m, "git-sheet", "repo-picker-list") {
            Page(
                signedIn.copy(
                    sheet = GitSheet.Remotes,
                    repoPicker = GitRepoPicker(
                        choices = persistentListOf(
                            GitHubRepository("octocat/app", "https://github.com/octocat/app", "https://github.com/octocat/app.git", "main", true),
                            GitHubRepository("octocat/site", "https://github.com/octocat/site", "https://github.com/octocat/site.git", "main", false),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun diffAndConfirm() = Audit.guard("${m}__diff__build") {
        val patch = "@@ -1,4 +1,5 @@\n fun main() {\n-    println(\"hi\")\n+    println(\"hello\")\n+    println(\"again\")\n }\n"
        Audit.shoot(m, "git-diff", "ready") { Page(ready().copy(diff = GitDiffView.Ready(changed, patch, binary = false))) }
        Audit.shoot(m, "git-diff", "loading") { Page(ready().copy(diff = GitDiffView.Loading(changed))) }
        Audit.shoot(m, "git-diff", "binary") { Page(ready().copy(diff = GitDiffView.Ready(binary, "", binary = true))) }
        Audit.shoot(m, "git-diff", "failed") { Page(ready().copy(diff = GitDiffView.Failed(changed, GitFailure(GitProblem.TimedOut)))) }
        Audit.shoot(m, "git-diff", "long-path") { Page(ready().copy(diff = GitDiffView.Ready(longFile, patch, binary = false))) }
        Audit.shoot(m, "git-confirm", "discard") { Page(ready().copy(confirm = GitConfirm.Discard(changed))) }
        Audit.shoot(m, "git-confirm", "sign-out") { Page(ready().copy(signIn = GitHubSignIn.SignedIn("octocat"), confirm = GitConfirm.SignOut)) }
    }
}
