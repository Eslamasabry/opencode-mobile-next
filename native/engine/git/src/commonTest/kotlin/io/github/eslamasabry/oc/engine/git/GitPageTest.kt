package io.github.eslamasabry.oc.engine.git

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class GitPageTest {
    private val ref = ConversationRef(ProfileId("p"), SessionId("s"))
    private val nul = "\u0000"
    private val snapshotSteps =
        arrayOf(
            StepReply("status", 0, "# branch.oid abc$nul# branch.head main$nul# branch.upstream origin/main${nul}1 .M N... 100644 100644 100644 a b app.kt$nul"),
            StepReply("unstaged"), StepReply("staged"), StepReply("log"), StepReply("branches"), StepReply("operation"),
            StepReply("remotes", 0, "remote.origin.url https://github.com/o/r.git\n"),
        )

    private fun page(shell: GitShell) =
        GitPage(ref) { resolveGitWorkspace(connected = true, directory = "/p", remoteShell = true) { shell } }

    @Test
    fun `B-NAV-022 resolution follows connection, project and the capability, never the flavor`() {
        val shell = FakeShell.steps()
        assertEquals(
            GitWorkspaceResolution.Unavailable(GitUnavailableReason.NotConnected),
            resolveGitWorkspace(false, "/p", true) { shell },
        )
        assertEquals(GitWorkspaceResolution.Unavailable(GitUnavailableReason.NoProject), resolveGitWorkspace(true, "  ", true) { shell })
        assertEquals(
            GitWorkspaceResolution.Unavailable(GitUnavailableReason.ServerCannotRunCommands),
            resolveGitWorkspace(true, "/p", false) { shell },
        )
        val available = assertIs<GitWorkspaceResolution.Available>(resolveGitWorkspace(true, " /p ", true, "Home box") { shell })
        assertEquals(GitHost.Server, available.workspace.host)
        assertEquals("/p", available.workspace.directory)
        assertTrue(available.workspace.usesOwnGitSignIn)
    }

    @Test
    fun `B-NAV-020 loads loading then ready, and never runs on an unavailable workspace`() = runTest {
        val p = page(FakeShell.steps(*snapshotSteps))
        assertEquals(GitPageView.Loading, p.state.value.view)
        p.refresh()
        val ready = assertIs<GitPageView.Ready>(p.state.value.view)
        assertEquals("app.kt", ready.snapshot.status.files.single().path)
        assertNull(p.state.value.busy)

        val shell = FakeShell.steps(*snapshotSteps)
        val off = GitPage(ref) { GitWorkspaceResolution.Unavailable(GitUnavailableReason.NotConnected) }
        off.refresh()
        off.pull()
        assertEquals(GitPageView.Unavailable(GitUnavailableReason.NotConnected), off.state.value.view)
        assertTrue(shell.scripts.isEmpty())
    }

    @Test
    fun `B-NAV-020 not a repository and failures are views, a failed refresh keeps the last snapshot`() = runTest {
        var reply: GitShellReply = GitShellReply.Output(envelopeOf(StepReply("status", 128, "", "fatal: not a git repository")))
        val p = page(FakeShell { reply })
        p.refresh()
        assertEquals(GitPageView.NotRepository, p.state.value.view)
        reply = GitShellReply.Failed(GitShellFailure.TimedOut)
        p.refresh()
        assertEquals(GitProblem.TimedOut, assertIs<GitPageView.Failed>(p.state.value.view).failure.problem)
        reply = GitShellReply.Output(envelopeOf(*snapshotSteps))
        p.refresh()
        assertIs<GitPageView.Ready>(p.state.value.view)
        reply = GitShellReply.Failed(GitShellFailure.Unavailable)
        p.refresh()
        assertIs<GitPageView.Ready>(p.state.value.view)
        assertEquals(GitProblem.EnvironmentUnavailable, p.state.value.refreshFailure?.problem)
    }

    @Test
    fun `B-NAV-020 a server without the command endpoint is unavailable, not failed`() = runTest {
        val p = page(FakeShell { GitShellReply.Failed(GitShellFailure.Unsupported) })
        p.refresh()
        assertEquals(GitPageView.Unavailable(GitUnavailableReason.ServerCannotRunCommands), p.state.value.view)
    }

    @Test
    fun `B-NAV-020 a write records its result then rereads, one action at a time`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var gated = false
        val shell =
            FakeShell { script ->
                if (gated && runs(script, "pull")) error("unreachable")
                GitShellReply.Output(
                    envelopeOf(
                        *(snapshotSteps.toList() + listOf(StepReply("head", 0, "main\n"), StepReply("upstream", 0, "origin/main\n"), StepReply("pull", 0, "Already up to date.\n")))
                            .filter { runs(script, it.name) }
                            .toTypedArray(),
                    ),
                )
            }
        val slow =
            object : GitShell {
                override suspend fun run(script: String, timeoutMs: Long): GitShellReply {
                    if (runs(script, "pull")) gate.await()
                    return shell.run(script, timeoutMs)
                }
            }
        val p = page(slow)
        p.refresh()
        val before = shell.scripts.size
        val first = launch { p.pull() }
        runCurrent()
        assertEquals(GitBusy.Pull, p.state.value.busy)
        gated = true
        p.push() // ignored while the pull runs: steer, never queue
        gated = false
        gate.complete(Unit)
        first.join()
        assertNull(p.state.value.busy)
        assertEquals(GitActionNote.UpToDate, p.state.value.result?.note)
        // The pull, then exactly one reread; the push never ran.
        assertEquals(before + 2, shell.scripts.size)
        assertTrue(shell.scripts.none { runs(it, "push") })
    }

    @Test
    fun `B-NAV-021 save-and-send saves, sends, then reads once, a failed save stops there, a failed send keeps the save`() = runTest {
        var pushOk = true
        var commitOk = true
        val shell =
            FakeShell { script ->
                val replies =
                    snapshotSteps.toList() +
                        listOf(
                            StepReply("add"),
                            if (commitOk) StepReply("commit") else StepReply("commit", 1, "", "*** Please tell me who you are."),
                            StepReply("id", 0, "abc\n"),
                            StepReply("files", 0, "app.kt\n"),
                            StepReply("head", 0, "main\n"),
                            StepReply("upstream", 0, "origin/main\n"),
                            StepReply("remotes", 0, "origin\n"),
                            if (pushOk) StepReply("push", 0, "", "To github.com") else StepReply("push", 128, "", "fatal: Authentication failed"),
                        )
                GitShellReply.Output(envelopeOf(*replies.filter { runs(script, it.name) }.distinctBy { it.name }.toTypedArray()))
            }
        val p = page(shell)
        p.refresh()
        var count = shell.scripts.size
        p.commitAndPush(listOf("app.kt"), "Update app")
        // commit, push plan, push, one reread
        assertEquals(count + 4, shell.scripts.size)
        assertEquals(GitActionNote.Pushed, p.state.value.result?.note)
        assertEquals(false, p.state.value.savedNotSent)

        pushOk = false
        count = shell.scripts.size
        p.commitAndPush(listOf("app.kt"), "Update app")
        assertEquals(GitProblem.AuthRequired, p.state.value.result?.problem)
        assertTrue(p.state.value.savedNotSent)
        assertEquals(count + 4, shell.scripts.size)

        commitOk = false
        count = shell.scripts.size
        p.commitAndPush(listOf("app.kt"), "Update app")
        assertEquals(GitProblem.IdentityMissing, p.state.value.result?.problem)
        assertEquals(false, p.state.value.savedNotSent)
        assertEquals(count + 1, shell.scripts.size)
    }

    @Test
    fun `B-NAV-020 a file diff opens and closes without blocking actions`() = runTest {
        val shell =
            FakeShell { script ->
                val replies = snapshotSteps.toList() + listOf(StepReply("work", 0, "diff --git a/app.kt b/app.kt\n+x\n"), StepReply("index"))
                GitShellReply.Output(envelopeOf(*replies.filter { runs(script, it.name) }.toTypedArray()))
            }
        val p = page(shell)
        p.refresh()
        val file = p.state.value.snapshot!!.status.files.single()
        p.openDiff(file)
        val diff = assertIs<GitDiffView.Ready>(p.state.value.diff)
        assertTrue("+x" in diff.patch)
        assertNull(p.state.value.busy)
        p.closeDiff()
        assertNull(p.state.value.diff)
    }
}
