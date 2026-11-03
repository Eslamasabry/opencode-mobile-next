package io.github.eslamasabry.oc.feature.git

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.git.GitHubSignIn
import io.github.eslamasabry.oc.engine.git.GitHubSignInControl
import io.github.eslamasabry.oc.engine.git.GitPage
import io.github.eslamasabry.oc.engine.git.GitPageView
import io.github.eslamasabry.oc.engine.git.GitShell
import io.github.eslamasabry.oc.engine.git.GitShellReply
import io.github.eslamasabry.oc.engine.git.GitUnavailableReason
import io.github.eslamasabry.oc.engine.git.resolveGitWorkspace
import io.github.eslamasabry.oc.feature.git.presentation.GitConfirm
import io.github.eslamasabry.oc.feature.git.presentation.GitPagePorts
import io.github.eslamasabry.oc.feature.git.presentation.GitPresenter
import io.github.eslamasabry.oc.feature.git.presentation.GitSheet
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

private fun b64(s: String) = Base64.encode(s.encodeToByteArray())

/** A server whose repository has two changed files, one conflicted; answers each script by its step names. */
private class Repo : GitShell {
    val scripts = mutableListOf<String>()
    var status =
        "# branch.oid abc\u0000# branch.head main\u0000# branch.upstream origin/main\u0000# branch.ab +1 -0\u0000" +
            "1 .M N... 100644 100644 100644 a b app.kt\u0000? notes.md\u0000u UU N... 100644 100644 100644 100644 a b c clash.kt\u0000"
    var commitErr: String? = null

    override suspend fun run(script: String, timeoutMs: Long): GitShellReply {
        scripts += script
        val replies =
            linkedMapOf(
                "status" to (0 to status), "unstaged" to (0 to ""), "staged" to (0 to ""), "log" to (0 to ""),
                "branches" to (0 to ""), "operation" to (0 to ""),
                "remotes" to (0 to "remote.origin.url https://github.com/o/r.git\n"),
                "add" to (0 to ""), "commit" to (if (commitErr == null) 0 to "" else 1 to ""),
                "id" to (0 to "abc1234\n"), "files" to (0 to "app.kt\n"), "run" to (0 to ""), "discard" to (0 to ""),
                "upstream" to (0 to "origin/main\n"), "fetch" to (0 to ""), "clone" to (0 to ""),
            )
        val text =
            buildString {
                append("@@OCG begin\n")
                for ((name, reply) in replies) {
                    if ("ocg_s $name " !in script) continue
                    val err = if (name == "commit") commitErr.orEmpty() else ""
                    append("@@OCG out $name ${b64(reply.second)}\n@@OCG err $name ${b64(err)}\n@@OCG exit $name ${reply.first}\n")
                }
                append("@@OCG end\n")
            }
        return GitShellReply.Output(text)
    }
}

private class FakeAccount : GitHubSignInControl {
    override val state = MutableStateFlow<GitHubSignIn>(GitHubSignIn.Loading)
    var started = 0
    var signedOut = 0

    override suspend fun load() {
        state.value = GitHubSignIn.SignedOut()
    }

    override fun startSignIn() {
        started++
        state.value = GitHubSignIn.WaitingForUser("WDJB-MJHT", "https://github.com/login/device", 0)
    }

    override fun cancel() {
        state.value = GitHubSignIn.SignedOut()
    }

    override suspend fun signOut(): Outcome<Unit> {
        signedOut++
        state.value = GitHubSignIn.SignedOut()
        return Outcome.Ok(Unit)
    }
}

class GitPresenterTest {
    private val ref = ConversationRef(ProfileId("p"), SessionId("s"))
    private val opened = mutableListOf<String>()

    @OptIn(RedactorApi::class)
    private val ports =
        GitPagePorts(
            details = { f -> f?.details?.takeIf { it.isNotBlank() }?.let { Redacted("redacted: " + it.take(20)) } },
            signInDetails = { null },
            openVerification = { opened += it; true },
            manageAccess = { opened += "manage"; true },
        )

    private fun TestScope.presenter(repo: Repo, account: GitHubSignInControl = FakeAccount()): GitPresenter {
        val page = GitPage(ref) { resolveGitWorkspace(true, "/srv/app", true, "Home box") { repo } }
        return GitPresenter(page, account, ports) { block -> launch { block() } }
    }

    @Test
    fun `B-NAV-020 the page loads, every changed file starts ticked except a conflict, and a tick can be removed`() = runTest {
        val repo = Repo()
        val p = presenter(repo)
        p.start()
        advanceUntilIdle()
        val ui = p.initial()
        assertIs<GitPageView.Ready>(ui.view)
        assertEquals(setOf("app.kt", "notes.md"), ui.selected)
        assertEquals("Home box", ui.placeName)
        p.toggleFile("notes.md")
        assertEquals(setOf("app.kt"), p.initial().selected)
        p.toggleFile("notes.md")
        assertEquals(setOf("app.kt", "notes.md"), p.initial().selected)
    }

    @Test
    fun `B-BUILD-019 save commits exactly the ticked files and clears the ticks`() = runTest {
        val repo = Repo()
        val p = presenter(repo)
        p.start()
        advanceUntilIdle()
        p.toggleFile("notes.md")
        p.commit("Update app")
        advanceUntilIdle()
        val commit = repo.scripts.single { "ocg_s commit " in it }
        assertTrue("'--' 'app.kt'" in commit)
        assertFalse("notes.md" in commit)
        assertFalse("clash.kt" in commit)
        assertEquals("abc1234", p.initial().result?.commit)
    }

    @Test
    fun `B-NAV-020 a failed save keeps Git's text for Details only`() = runTest {
        val repo = Repo().apply { commitErr = "*** Please tell me who you are." }
        val p = presenter(repo)
        p.start()
        advanceUntilIdle()
        p.commit("m")
        advanceUntilIdle()
        val ui = p.initial()
        assertEquals("IdentityMissing", ui.result?.problem?.name)
        assertTrue(ui.resultDetails!!.text.startsWith("redacted: "))
    }

    @Test
    fun `B-NAV-020 discard asks first and runs only after yes`() = runTest {
        val repo = Repo()
        val p = presenter(repo)
        p.start()
        advanceUntilIdle()
        p.askDiscard("app.kt")
        assertIs<GitConfirm.Discard>(p.initial().confirm)
        p.dismissConfirm()
        advanceUntilIdle()
        assertTrue(repo.scripts.none { "ocg_s discard " in it })
        p.askDiscard("app.kt")
        p.confirm()
        advanceUntilIdle()
        assertNull(p.initial().confirm)
        assertTrue(repo.scripts.any { "ocg_s discard " in it && "'restore'" in it })
    }

    @Test
    fun `B-BUILD-021 the branch sheet switches and creates through git switch`() = runTest {
        val repo = Repo()
        val p = presenter(repo)
        p.start()
        advanceUntilIdle()
        p.openBranches()
        assertEquals(GitSheet.Branches, p.initial().sheet)
        p.createBranch("  feature/x ")
        advanceUntilIdle()
        assertNull(p.initial().sheet)
        assertTrue(repo.scripts.any { "'switch' '-c' 'feature/x'" in it })
    }

    @Test
    fun `B-SEC-084 GitHub sign-in starts from the page, opens only GitHub's page, and sign-out asks first`() = runTest {
        val account = FakeAccount()
        val p = presenter(Repo(), account)
        p.start()
        advanceUntilIdle()
        assertEquals(GitHubSignIn.SignedOut(), p.initial().signIn)
        p.signIn()
        assertEquals(1, account.started)
        p.openGitHub()
        assertEquals(listOf("https://github.com/login/device"), opened)
        p.askSignOut()
        assertEquals(GitConfirm.SignOut, p.initial().confirm)
        p.confirm()
        advanceUntilIdle()
        assertEquals(1, account.signedOut)
    }

    @Test
    fun `B-BUILD-019 a ticked rename saves its old path too`() = runTest {
        val repo = Repo()
        repo.status = "# branch.oid abc\u0000# branch.head main\u0000# branch.upstream origin/main\u0000" +
            "2 R. N... 100644 100644 100644 a b R100 new.kt\u0000old.kt\u0000"
        val p = presenter(repo)
        p.start()
        advanceUntilIdle()
        assertEquals(setOf("new.kt"), p.initial().selected)
        p.commit("Rename")
        advanceUntilIdle()
        val commit = repo.scripts.single { "ocg_s commit " in it }
        assertTrue("'new.kt'" in commit && "'old.kt'" in commit, commit)
    }

    @Test
    fun `B-BUILD-019 a failed save keeps the files left out`() = runTest {
        val repo = Repo().apply { commitErr = "pre-commit hook exited with error code 1" }
        val p = presenter(repo)
        p.start()
        advanceUntilIdle()
        p.toggleFile("notes.md")
        p.commit("m")
        advanceUntilIdle()
        assertEquals("HookRejected", p.initial().result?.problem?.name)
        assertEquals(setOf("app.kt"), p.initial().selected)
        p.commitAndPush("m")
        advanceUntilIdle()
        assertEquals(setOf("app.kt"), p.initial().selected)
        assertTrue(repo.scripts.filter { "ocg_s commit " in it }.none { "notes.md" in it })
    }

    @Test
    fun `B-BUILD-014 opening looks at the remote quietly, and the server's sign-in hides the app's GitHub sign-in`() = runTest {
        val repo = Repo()
        val p = presenter(repo)
        p.start()
        advanceUntilIdle()
        assertTrue(repo.scripts.any { "ocg_s fetch " in it && "'fetch' '--quiet'" in it })
        assertEquals("Done", p.initial().check.name)
        assertNull(p.initial().busy)
        assertFalse(p.initial().offersGitHubSignIn)
        assertEquals("/srv/app", p.initial().directory)
    }

    @Test
    fun `B-BUILD-014 Remotes adds a remote and Clone copies a repository beside the project`() = runTest {
        val repo = Repo()
        val p = presenter(repo)
        p.start()
        advanceUntilIdle()
        p.openRemotes()
        assertEquals(GitSheet.Remotes, p.initial().sheet)
        p.addRemote("backup", "https://me:" + "pw@example.invalid/r.git")
        advanceUntilIdle()
        assertEquals("RemoteAddressHasPassword", p.initial().result?.problem?.name)
        assertTrue(repo.scripts.none { "'remote' 'add'" in it })
        p.addRemote(" backup ", " https://example.invalid/r.git ")
        advanceUntilIdle()
        assertTrue(repo.scripts.any { "'remote' 'add' 'backup' 'https://example.invalid/r.git'" in it })
        assertEquals(GitSheet.Remotes, p.initial().sheet)

        p.openClone()
        assertEquals(GitSheet.Clone, p.initial().sheet)
        p.clone("https://github.com/o/tool.git", "")
        advanceUntilIdle()
        val clone = repo.scripts.single { "ocg_s clone " in it }
        assertTrue("'clone' '--' 'https://github.com/o/tool.git' 'tool'" in clone, clone)
        assertTrue("cd -- '/srv'" in clone || "'/srv'" in clone, clone)
        assertEquals("/srv/tool", p.initial().result?.clonedTo)
        assertNull(p.initial().sheet)
    }

    @Test
    fun `B-NAV-022 an unavailable workspace shows its reason and runs nothing`() = runTest {
        val repo = Repo()
        // The fake reaches the real resolver: a command run on the unavailable workspace would show in its scripts.
        val page = GitPage(ref) { resolveGitWorkspace(connected = true, directory = null, remoteShell = true) { repo } }
        val p = GitPresenter(page, FakeAccount(), ports) { block -> launch { block() } }
        p.start()
        advanceUntilIdle()
        p.pull()
        p.commit("x")
        advanceUntilIdle()
        assertEquals(GitPageView.Unavailable(GitUnavailableReason.NoProject), p.initial().view)
        assertTrue(repo.scripts.isEmpty())
    }
}
