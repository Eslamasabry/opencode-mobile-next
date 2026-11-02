package io.github.eslamasabry.oc.engine.git

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class GitRepositoryTest {
    private val nul = "\u0000"
    private val clean = StepReply("status", 0, "# branch.oid abc$nul# branch.head main$nul# branch.upstream origin/main$nul# branch.ab +0 -0$nul")

    @Test
    fun `B-BUILD-018 a snapshot is read in one round trip`() = runTest {
        val shell =
            FakeShell.steps(
                clean,
                StepReply("unstaged"),
                StepReply("staged"),
                StepReply("log"),
                StepReply("branches"),
                StepReply("operation", 0, "MERGE_HEAD\n"),
                StepReply("remotes", 0, "remote.origin.url https://github.com/o/r.git\n"),
                StepReply("identity", 0, "Ada\nada@example.invalid\n"),
            )
        val result = assertIs<GitSnapshotResult.Ready>(GitRepository(shell, "/p").snapshot())
        assertEquals(1, shell.scripts.size)
        assertEquals(GitOperation.Merge, result.snapshot.operation)
        assertEquals("origin", result.snapshot.pushRemote?.name)
        assertFalse(result.snapshot.identity!!.needsSetup)
    }

    @Test
    fun `B-BUILD-018 not a repository and unborn branch are states`() = runTest {
        val notRepo = FakeShell.steps(StepReply("status", 128, "", "fatal: not a git repository (or any parent)"))
        assertEquals(GitSnapshotResult.NotRepository, GitRepository(notRepo, "/p").snapshot())
        val unborn =
            FakeShell.steps(
                StepReply("status", 0, "# branch.oid (initial)$nul# branch.head main$nul"),
                StepReply("unstaged"), StepReply("staged"),
                StepReply("log", 128, "", "fatal: your current branch 'main' does not have any commits yet"),
                StepReply("branches"), StepReply("operation"), StepReply("remotes"),
            )
        val ready = assertIs<GitSnapshotResult.Ready>(GitRepository(unborn, "/p").snapshot())
        assertTrue(ready.snapshot.status.branch.unborn)
        assertTrue(ready.snapshot.commits.isEmpty())
    }

    @Test
    fun `B-BUILD-018 bridge and envelope failures map to problems`() = runTest {
        suspend fun problemOf(reply: GitShellReply): GitProblem =
            assertIs<GitSnapshotResult.Failed>(GitRepository(FakeShell { reply }, "/p").snapshot()).failure.problem
        assertEquals(GitProblem.ServerCannotRunCommands, problemOf(GitShellReply.Failed(GitShellFailure.Unsupported)))
        assertEquals(GitProblem.EnvironmentUnavailable, problemOf(GitShellReply.Failed(GitShellFailure.Unavailable)))
        assertEquals(GitProblem.TimedOut, problemOf(GitShellReply.Failed(GitShellFailure.TimedOut)))
        assertEquals(GitProblem.FolderMissing, problemOf(GitShellReply.Output("@@OCG begin\n@@OCG nodir\n")))
        assertEquals(GitProblem.GitMissing, problemOf(GitShellReply.Output("@@OCG begin\n@@OCG nogit\n")))
        assertEquals(GitProblem.OutputTooLarge, problemOf(GitShellReply.Output("@@OCG begin\n")))
        // A required step that never reported is a cut-off answer too.
        assertEquals(GitProblem.OutputTooLarge, problemOf(GitShellReply.Output(envelopeOf(clean))))
    }

    @Test
    fun `B-BUILD-019 commit stages exactly the chosen quoted paths with a hostile message`() = runTest {
        val shell = FakeShell.steps(StepReply("add"), StepReply("commit"), StepReply("id", 0, "abc1234\n"), StepReply("files", 0, "a b.kt\n"))
        val result = GitRepository(shell, "/p").commit(listOf("a b.kt"), "fix: it's \$(rm -rf ~) `id`")
        assertTrue(result.ok)
        assertEquals(GitActionNote.Committed, result.note)
        assertEquals("abc1234", result.commit)
        assertEquals(1, result.count)
        val script = shell.scripts.single()
        assertTrue("ocg_s add 'git' '--no-pager' '-c' 'color.ui=false' 'add' '-A' '--' 'a b.kt'" in script)
        assertTrue("'commit' '-m' 'fix: it'\\''s \$(rm -rf ~) `id`' '--' 'a b.kt'" in script)
    }

    @Test
    fun `B-BUILD-019 nothing chosen or an empty message runs nothing`() = runTest {
        val shell = FakeShell.steps()
        val repo = GitRepository(shell, "/p")
        assertEquals(GitProblem.NothingSelected, repo.commit(emptyList(), "msg").problem)
        assertEquals(GitProblem.EmptyMessage, repo.commit(listOf("a"), "  ").problem)
        assertTrue(shell.scripts.isEmpty())
    }

    @Test
    fun `B-BUILD-019 missing identity is typed with Git text kept for Details, nothing to commit is read from stdout`() = runTest {
        val identity =
            FakeShell.steps(StepReply("add"), StepReply("commit", 128, "", "*** Please tell me who you are.\n\nRun git config"))
        val failed = GitRepository(identity, "/p").commit(listOf("a"), "m")
        assertEquals(GitProblem.IdentityMissing, failed.problem)
        assertTrue("Please tell me who you are" in failed.failure!!.details)
        val nothing = FakeShell.steps(StepReply("add"), StepReply("commit", 1, "nothing to commit, working tree clean\n", ""))
        assertEquals(GitProblem.NothingToCommit, GitRepository(nothing, "/p").commit(listOf("a"), "m").problem)
    }

    @Test
    fun `B-BUILD-020 pull is fast-forward only with typed states`() = runTest {
        val noUpstream = FakeShell.steps(StepReply("head", 0, "main\n"), StepReply("upstream", 128, "", "fatal: no upstream configured for branch 'main'"))
        assertEquals(GitProblem.NoUpstream, GitRepository(noUpstream, "/p").pull().problem)
        val detached = FakeShell.steps(StepReply("head", 1, "", ""))
        assertEquals(GitProblem.DetachedHead, GitRepository(detached, "/p").pull().problem)
        val diverged =
            FakeShell.steps(
                StepReply("head", 0, "main\n"), StepReply("upstream", 0, "origin/main\n"),
                StepReply("pull", 128, "", "hint: Diverging branches can't be fast-forwarded\nfatal: Not possible to fast-forward, aborting."),
            )
        assertEquals(GitProblem.CannotFastForward, GitRepository(diverged, "/p").pull().problem)
        val upToDate = FakeShell.steps(StepReply("head", 0, "main\n"), StepReply("upstream", 0, "origin/main\n"), StepReply("pull", 0, "Already up to date.\n"))
        assertEquals(GitActionNote.UpToDate, GitRepository(upToDate, "/p").pull().note)
        val pulled =
            FakeShell.steps(
                StepReply("head", 0, "main\n"), StepReply("upstream", 0, "origin/main\n"), StepReply("pull", 0, "Fast-forward\n"), StepReply("count", 0, "3\n"),
            )
        val result = GitRepository(pulled, "/p").pull()
        assertEquals(GitActionNote.Pulled, result.note)
        assertEquals(3, result.count)
        assertEquals("origin/main", result.target)
        assertTrue("'pull' '--ff-only' '--no-rebase'" in pulled.scripts.single())
    }

    @Test
    fun `B-BUILD-020 first push sets upstream on origin, no remote is typed, a rejected push says pull first`() = runTest {
        val first =
            FakeShell.steps(StepReply("head", 0, "feature/x\n"), StepReply("upstream", 128, "", "fatal: no upstream"), StepReply("remotes", 0, "fork\norigin\n"), StepReply("push", 0, "", "To github.com"))
        val pushed = GitRepository(first, "/p").push()
        assertEquals(GitActionNote.PushedAndTracking, pushed.note)
        assertEquals("origin/feature/x", pushed.target)
        assertTrue("'push' '--set-upstream' 'origin' 'feature/x'" in first.scripts.last())
        val none = FakeShell.steps(StepReply("head", 0, "main\n"), StepReply("upstream", 128), StepReply("remotes", 0, ""))
        assertEquals(GitProblem.NoRemote, GitRepository(none, "/p").push().problem)
        val rejected =
            FakeShell.steps(
                StepReply("head", 0, "main\n"), StepReply("upstream", 0, "origin/main\n"), StepReply("remotes", 0, "origin\n"),
                StepReply("push", 1, "", " ! [rejected]        main -> main (fetch first)\nerror: failed to push some refs"),
            )
        assertEquals(GitProblem.PushRejected, GitRepository(rejected, "/p").push().problem)
    }

    @Test
    fun `B-BUILD-021 switch and create use git switch, discard picks the command by change kind`() = runTest {
        val shell = FakeShell.steps(StepReply("run"), StepReply("discard"), StepReply("remove"))
        val repo = GitRepository(shell, "/p")
        assertTrue(repo.switchBranch("feature/x").ok)
        assertTrue("'switch' 'feature/x'" in shell.scripts.last())
        assertTrue(repo.createBranch("new-one").ok)
        assertTrue("'switch' '-c' 'new-one'" in shell.scripts.last())
        assertEquals(GitProblem.InvalidBranchName, repo.createBranch("bad name").problem)

        repo.discard(GitFileChange("n.txt", unstaged = GitChangeKind.Untracked))
        assertTrue("'clean' '-f' '-q' '--' 'n.txt'" in shell.scripts.last())
        repo.discard(GitFileChange("a.kt", staged = GitChangeKind.Added))
        assertTrue("'rm' '-f' '-q' '--' 'a.kt'" in shell.scripts.last())
        repo.discard(GitFileChange("new.kt", originalPath = "old.kt", staged = GitChangeKind.Renamed))
        assertTrue("'restore' '--staged' '--worktree' '--' 'old.kt'" in shell.scripts.last())
        repo.discard(GitFileChange("m.kt", unstaged = GitChangeKind.Modified))
        assertTrue("'restore' '--staged' '--worktree' '--' 'm.kt'" in shell.scripts.last())
    }

    @Test
    fun `B-BUILD-021 a conflicted file is never discarded`() = runTest {
        val shell = FakeShell.steps()
        val result = GitRepository(shell, "/p").discard(GitFileChange("c.kt", conflict = GitConflictKind.BothModified))
        assertEquals(GitProblem.CannotDiscardConflict, result.problem)
        assertTrue(shell.scripts.isEmpty())
    }

    @Test
    fun `B-BUILD-018 a file diff joins the working tree and the index and reads untracked files as new`() = runTest {
        val tracked = FakeShell.steps(StepReply("work", 0, "diff --git a/a b/a\n+x\n"), StepReply("index", 0, "diff --git a/a b/a\n-y\n"))
        val ready = assertIs<GitDiffResult.Ready>(GitRepository(tracked, "/p").diff(GitFileChange("a", staged = GitChangeKind.Modified, unstaged = GitChangeKind.Modified)))
        assertTrue("+x" in ready.patch && "-y" in ready.patch)
        val untracked = FakeShell.steps(StepReply("work", 1, "diff --git a/n b/n\nnew file mode 100644\n+hello\n"))
        val fresh = assertIs<GitDiffResult.Ready>(GitRepository(untracked, "/p").diff(GitFileChange("n", unstaged = GitChangeKind.Untracked)))
        assertTrue("+hello" in fresh.patch)
        assertTrue("'--no-index' '--' '/dev/null' 'n'" in untracked.scripts.single())
        val binary = FakeShell.steps(StepReply("work", 0, "Binary files a/logo.png and b/logo.png differ\n"), StepReply("index"))
        assertTrue(assertIs<GitDiffResult.Ready>(GitRepository(binary, "/p").diff(GitFileChange("logo.png", unstaged = GitChangeKind.Modified))).binary)
    }
}
