package io.github.eslamasabry.oc.engine.project.gitops

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeAccount : GitHubTokenSource {
    var asked = 0
    var rejected = 0

    override suspend fun tokenForGit(): String {
        asked++
        return FAKE_GITHUB_TOKEN
    }

    override suspend fun tokenRejected() {
        rejected++
    }
}

private val clean = GitSnapshotFacts(
    detached = false, unborn = false, hasUpstream = true, hasRemote = true, operationInProgress = false, changedFiles = 4, hasConflicts = false,
)

class GitPushPageTest {
    @Test
    fun `B-SEC-082 phone Linux push hands the signed-in token to the drop`() = runTest {
        val drop = FakeCredentialDrop()
        val shell = FakeOpsShell.of(pushPlan(), envelope(mapOf("push" to ok())))
        val page = GitPushPage(GitPushWorkspace(GitHost.PhoneLinux, "/root/projects/app", shell, drop), FakeAccount())
        page.onSnapshot(clean)
        assertTrue(page.abilities.offersGitHubSignIn)
        assertFalse(page.abilities.usesOwnGitSignIn)
        val result = page.push()!!
        assertEquals(GitOpsNote.Pushed, result.note)
        assertEquals(listOf(FAKE_GITHUB_TOKEN), drop.secrets)
        assertTrue(drop.allRemoved)
        assertFalse(FAKE_GITHUB_TOKEN in shell.scripts.joinToString())
        assertNull(page.busy.value)
        assertEquals(result, page.lastResult.value)
    }

    @Test
    fun `B-SEC-082 a rejected token signs the account out`() = runTest {
        val account = FakeAccount()
        val shell = FakeOpsShell.of(pushPlan(), envelope(mapOf("push" to failed(HTTPS_AUTH_ERROR, 128))))
        val page = GitPushPage(GitPushWorkspace(GitHost.PhoneLinux, "/root/projects/app", shell, FakeCredentialDrop()), account)
        assertEquals(GitOpsProblem.GitHubTokenRejected, page.push()!!.problem)
        assertEquals(1, account.rejected)
    }

    @Test
    fun `B-SEC-082 Termux and servers offer no sign-in and the token stays home`() = runTest {
        for (host in listOf(GitHost.Termux, GitHost.Server)) {
            val account = FakeAccount()
            val shell = FakeOpsShell.of(pushPlan(), envelope(mapOf("push" to failed(NO_PROMPT_ERROR, 128))))
            val page = GitPushPage(GitPushWorkspace(host, "/w/app", shell, credentials = null), account)
            page.onSnapshot(clean)
            assertFalse(page.abilities.offersGitHubSignIn)
            assertTrue(page.abilities.usesOwnGitSignIn)
            assertEquals(GitOpsProblem.AuthRequired, page.push()!!.problem)
            assertEquals(0, account.rejected)
            assertEquals(0, account.asked, "the token is not even read")
            assertFalse("credential.helper" in shell.scripts.joinToString())
        }
    }

    @Test
    fun `B-SEC-082 abilities follow the snapshot`() {
        val detached = GitPageAbilities.of(false, clean.copy(detached = true))
        assertFalse(detached.canPull)
        assertFalse(detached.canPush)

        val merging = GitPageAbilities.of(false, clean.copy(operationInProgress = true, hasConflicts = true))
        assertFalse(merging.canCommit)
        assertFalse(merging.canPull)
        assertFalse(merging.canSwitchBranch)

        val noRemote = GitPageAbilities.of(false, clean.copy(hasUpstream = false, hasRemote = false))
        assertFalse(noRemote.canPush)
        assertFalse(noRemote.canPull)

        val ready = GitPageAbilities.of(true, clean)
        assertTrue(ready.canPull && ready.canPush && ready.canCommit && ready.canSwitchBranch)
        val unread = GitPageAbilities.of(true, null)
        assertFalse(unread.canPush)
        assertTrue(unread.offersGitHubSignIn)
    }
}
