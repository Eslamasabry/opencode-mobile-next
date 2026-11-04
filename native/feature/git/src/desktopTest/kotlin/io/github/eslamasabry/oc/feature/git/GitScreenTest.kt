package io.github.eslamasabry.oc.feature.git

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.engine.git.GitActionResult
import io.github.eslamasabry.oc.engine.git.GitAction
import io.github.eslamasabry.oc.engine.git.GitBranchState
import io.github.eslamasabry.oc.engine.git.GitChangeKind
import io.github.eslamasabry.oc.engine.git.GitFailure
import io.github.eslamasabry.oc.engine.git.GitFileChange
import io.github.eslamasabry.oc.engine.git.GitHubSignIn
import io.github.eslamasabry.oc.engine.git.GitPageView
import io.github.eslamasabry.oc.engine.git.GitProblem
import io.github.eslamasabry.oc.engine.git.GitRemote
import io.github.eslamasabry.oc.engine.git.GitRemoteKind
import io.github.eslamasabry.oc.engine.git.GitSnapshot
import io.github.eslamasabry.oc.engine.git.GitStatus
import io.github.eslamasabry.oc.engine.git.GitUnavailableReason
import io.github.eslamasabry.oc.feature.git.presentation.GitActions
import io.github.eslamasabry.oc.feature.git.presentation.GitSheet
import io.github.eslamasabry.oc.feature.git.presentation.GitUi
import io.github.eslamasabry.oc.feature.git.ui.GitScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** The real GitScreen, by visible text (B-NAV-020/022, B-SEC-084). */
@OptIn(ExperimentalTestApi::class)
class GitScreenTest {
    private var previous: Locale = Locale.getDefault()
    private val calls = mutableListOf<String>()

    @BeforeTest
    fun before() {
        previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun after() {
        Locale.setDefault(previous)
    }

    private val actions =
        object : GitActions {
            override fun back() { calls += "back" }
            override fun refresh() { calls += "refresh" }
            override fun pull() { calls += "pull" }
            override fun push() { calls += "push" }
            override fun toggleFile(path: String) { calls += "toggle $path" }
            override fun commit(message: String) { calls += "commit $message" }
            override fun commitAndPush(message: String) { calls += "commitAndPush $message" }
            override fun openBranches() { calls += "branches" }
            override fun openRemotes() { calls += "remotes" }
            override fun openClone() { calls += "clone" }
            override fun addRemote(name: String, url: String) { calls += "addRemote $name $url" }
            override fun clone(url: String, folder: String) { calls += "clone $url $folder" }
            override fun closeSheet() {}
            override fun switchBranch(name: String) {}
            override fun createBranch(name: String) {}
            override fun openDiff(path: String) { calls += "diff $path" }
            override fun closeDiff() {}
            override fun askDiscard(path: String) {}
            override fun confirm() {}
            override fun dismissConfirm() {}
            override fun setIdentity(name: String, email: String) {}
            override fun signIn() { calls += "signIn" }
            override fun cancelSignIn() {}
            override fun askSignOut() {}
            override fun openGitHub() {}
            override fun manageGitHubAccess() {}
            override fun dismissResult() {}
            override fun repairObjects() {
                calls += "repairObjects"
            }
            override fun openRepairSettings() {
                calls += "repairSettings"
            }
        }

    @Composable
    private fun Screen(state: GitUi) {
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), localeTag = "en", stillFrame = true) {
                Box(Modifier.width(412.dp)) { GitScreen(state, actions) }
            }
        }
    }

    private val snapshot =
        GitSnapshot(
            status =
                GitStatus(
                    GitBranchState(head = "main", commit = "abc", upstream = "origin/main", ahead = 2),
                    persistentListOf(GitFileChange("src/app.kt", unstaged = GitChangeKind.Modified, addedLines = 3, deletedLines = 1)),
                ),
            remotes = persistentListOf(GitRemote("origin", GitRemoteKind.GitHubHttps, "https://github.com/o/r.git")),
        )

    @Test
    fun `B-NAV-022 a server that cannot run commands says so in plain words`() = runComposeUiTest {
        setContent { Screen(GitUi(view = GitPageView.Unavailable(GitUnavailableReason.ServerCannotRunCommands), signIn = GitHubSignIn.SignedOut())) }
        onNodeWithText("This server can’t run Git for the app. Use Git on that computer instead.").assertExists()
    }

    @Test
    fun `B-NAV-020 a ready page shows the branch, Get latest, Send and the changed file with its counts`() = runComposeUiTest {
        setContent { Screen(GitUi(view = GitPageView.Ready(snapshot), selected = persistentSetOf("src/app.kt"), signIn = GitHubSignIn.SignedOut())) }
        onNodeWithText("Get latest").assertExists()
        onNodeWithText("Send").assertExists()
        onNodeWithText("2 commits to push").assertExists()
        // The file row itself: its path and its kind/counts on one row (the Changes row also names the path).
        onNode(hasText("src/app.kt") and hasText("Modified · +3 −1")).assertExists()
        onNodeWithText("Save 1 file").assertExists()
        onNodeWithTag("git-push-row").performScrollTo().performClick()
        onNodeWithTag("git-diff-src/app.kt").performScrollTo().performClick()
        assertTrue("push" in calls)
        assertTrue("diff src/app.kt" in calls)
    }

    @OptIn(RedactorApi::class)
    @Test
    fun `B-NAV-020 a failed action shows plain words and keeps Git's text under Details`() = runComposeUiTest {
        val gitText = "fatal: Not possible to fast-forward, aborting."
        setContent {
            Screen(
                GitUi(
                    view = GitPageView.Ready(snapshot),
                    result = GitActionResult.failure(GitAction.Pull, GitFailure(GitProblem.CannotFastForward, gitText)),
                    resultDetails = Redacted(gitText),
                    signIn = GitHubSignIn.SignedOut(),
                ),
            )
        }
        onNodeWithText(
            "The remote and this branch both have new commits, so they can’t be combined automatically. Merge or rebase on that device.",
        ).assertExists()
        onNodeWithText(gitText).assertDoesNotExist()
        onNodeWithTag("kit-details-toggle").performClick()
        onNodeWithText(gitText).assertExists()
    }

    @Test
    fun `B-SEC-084 GitHub sign-in is a visible row and the waiting code is shown`() = runComposeUiTest {
        setContent { Screen(GitUi(view = GitPageView.Ready(snapshot), signIn = GitHubSignIn.SignedOut(), offersGitHubSignIn = true)) }
        onNodeWithText("Sign in to GitHub").performScrollTo().performClick()
        assertEquals("signIn", calls.last())
    }

    @Test
    fun `B-SEC-084 while waiting the code and Open GitHub are shown`() = runComposeUiTest {
        setContent {
            Screen(
                GitUi(
                    view = GitPageView.Ready(snapshot),
                    signIn = GitHubSignIn.WaitingForUser("WDJB-MJHT", "https://github.com/login/device", 0),
                    offersGitHubSignIn = true,
                ),
            )
        }
        onNodeWithText("WDJB-MJHT").assertExists()
        onNodeWithText("Open GitHub").assertExists()
        assertTrue(onAllNodesWithText("Enter this code on GitHub to finish signing in.").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `B-NAV-020 Arabic words are used in Arabic`() = runComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        setContent {
            CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
                KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), localeTag = "ar", stillFrame = true) {
                    Box(Modifier.width(412.dp)) { GitScreen(GitUi(view = GitPageView.NotRepository, signIn = GitHubSignIn.SignedOut()), actions) }
                }
            }
        }
        onNodeWithText("ليس مستودع Git").assertExists()
        onNodeWithText("Not a Git repository").assertDoesNotExist()
    }

    @Test
    fun `B-SEC-082 a server workspace pushes with its own sign-in, so no GitHub sign-in is offered`() = runComposeUiTest {
        setContent { Screen(GitUi(view = GitPageView.Ready(snapshot), signIn = GitHubSignIn.SignedOut(), placeName = "Home box")) }
        onNodeWithText("Sign in to GitHub").assertDoesNotExist()
        onNodeWithTag("github-sign-in").assertDoesNotExist()
    }

    @Test
    fun `B-BUILD-014 a project without a remote reaches Remotes and Clone from visible rows`() = runComposeUiTest {
        val bare = snapshot.copy(remotes = persistentListOf())
        setContent { Screen(GitUi(view = GitPageView.Ready(bare), signIn = GitHubSignIn.SignedOut())) }
        onNodeWithText("None yet").assertExists()
        onNodeWithText("Remotes").performScrollTo().performClick()
        onNodeWithText("Clone a repository").performScrollTo().performClick()
        assertEquals(listOf("remotes", "clone"), calls)
    }

    @Test
    fun `B-BUILD-014 the Remotes sheet lists remotes and refuses an address with a password before anything runs`() = runComposeUiTest {
        setContent { Screen(GitUi(view = GitPageView.Ready(snapshot), signIn = GitHubSignIn.SignedOut(), sheet = GitSheet.Remotes)) }
        onNodeWithTag("git-remote-origin").assertExists()
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("git-remote-name"))).performTextInput("backup")
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("git-remote-url"))).performTextInput("https://me:" + "pw@example.invalid/r.git")
        waitForIdle()
        onNodeWithText("Add remote").performClick()
        assertTrue(calls.none { it.startsWith("addRemote") })
    }
}
