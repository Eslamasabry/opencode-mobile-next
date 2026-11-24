package io.github.eslamasabry.oc.engine.git

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** The generated scripts against the real `sh`, `git` and `base64` of this machine (B-BUILD-017). */
class RealGitTest {
    private val root: File = Files.createTempDirectory("oc-git-real").toFile()

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    /** A local [GitShell]: `sh -c <script>` with HOME pointing into the temp folder (no user config). */
    private val shell =
        GitShell { script, timeoutMs ->
            val process =
                ProcessBuilder("sh", "-c", script)
                    .redirectErrorStream(true)
                    .apply {
                        environment()["HOME"] = root.absolutePath
                        environment()["GIT_CONFIG_NOSYSTEM"] = "1"
                    }
                    .start()
            val text = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) GitShellReply.Failed(GitShellFailure.TimedOut)
            else GitShellReply.Output(text)
        }

    private fun git(dir: File, vararg args: String) {
        val p = ProcessBuilder(listOf("git") + args).directory(dir).redirectErrorStream(true)
            .apply { environment()["HOME"] = root.absolutePath; environment()["GIT_CONFIG_NOSYSTEM"] = "1" }.start()
        val out = p.inputStream.bufferedReader().readText()
        check(p.waitFor() == 0) { "git ${args.toList()} failed: $out" }
    }

    private fun repo(name: String): File {
        val dir = File(root, name).apply { mkdirs() }
        git(dir, "init", "-q", "-b", "main")
        git(dir, "config", "user.name", "Test")
        git(dir, "config", "user.email", "test\u0040example.invalid")
        git(dir, "config", "commit.gpgsign", "false")
        return dir
    }

    @Test
    fun `B-BUILD-017 not a repository is a state`() = runTest {
        val plain = File(root, "plain folder").apply { mkdirs() }
        assertEquals(GitSnapshotResult.NotRepository, GitRepository(shell, plain.absolutePath).snapshot())
    }

    @Test
    fun `W2-019 a missing system temp folder does not make a plain folder unreachable`() = runTest {
        val plain = File(root, "plain folder").apply { mkdirs() }
        val noTemp =
            GitShell { script, timeoutMs ->
                val process =
                    ProcessBuilder("sh", "-c", script)
                        .redirectErrorStream(true)
                        .apply {
                            environment()["HOME"] = root.absolutePath
                            environment()["TMPDIR"] = File(root, "no such temp").absolutePath
                            environment()["GIT_CONFIG_NOSYSTEM"] = "1"
                        }
                        .start()
                val text = process.inputStream.bufferedReader().readText()
                process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                GitShellReply.Output(text)
            }
        assertEquals(GitSnapshotResult.NotRepository, GitRepository(noTemp, plain.absolutePath).snapshot())
    }

    @Test
    fun `B-BUILD-017 snapshot handles unicode renames, untracked files and no upstream`() = runTest {
        val dir = repo("my repo")
        File(dir, "old name.md").writeText("hello\n")
        git(dir, "add", "-A")
        git(dir, "commit", "-q", "-m", "first")
        // The Arabic name is made by the shell from octal bytes, so the JVM's own file-name encoding never matters.
        val made = ProcessBuilder("sh", "-c", "git mv 'old name.md' \"\$(printf '\\331\\205\\330\\261\\330\\255\\330\\250\\330\\247') new.md\"")
            .directory(dir).redirectErrorStream(true).start()
        check(made.waitFor() == 0) { made.inputStream.bufferedReader().readText() }
        File(dir, "to do.txt").writeText("x\n")
        val ready = assertIs<GitSnapshotResult.Ready>(GitRepository(shell, dir.absolutePath).snapshot())
        val status = ready.snapshot.status
        assertEquals("main", status.branch.head)
        assertFalse(status.branch.hasUpstream)
        val renamed = status.files.single { it.staged == GitChangeKind.Renamed }
        assertEquals("مرحبا new.md", renamed.path)
        assertEquals("old name.md", renamed.originalPath)
        assertTrue(status.files.any { it.isUntracked && it.path == "to do.txt" })
        assertEquals("first", ready.snapshot.commits.single().subject)
        assertFalse(ready.snapshot.identity!!.needsSetup)
    }

    @Test
    fun `B-BUILD-017 commit only the chosen paths with a hostile message, then branch, diff and discard`() = runTest {
        val dir = repo("work")
        File(dir, "a.txt").writeText("a\n")
        File(dir, "b.txt").writeText("b\n")
        val repository = GitRepository(shell, dir.absolutePath)
        val message = "it's \$(touch pwned) `touch pwned2` \"q\""
        val saved = repository.commit(listOf("a.txt"), message)
        assertTrue(saved.ok, saved.toString())
        assertEquals(1, saved.count)
        assertFalse(File(dir, "pwned").exists())
        assertFalse(File(dir, "pwned2").exists())
        val after = assertIs<GitSnapshotResult.Ready>(repository.snapshot()).snapshot
        assertEquals(message, after.commits.first().subject)
        assertEquals(listOf("b.txt"), after.status.files.map { it.path })

        assertTrue(repository.createBranch("feature/x").ok)
        assertTrue(repository.switchBranch("main").ok)
        assertEquals(GitProblem.BranchNotFound, repository.switchBranch("nope").problem)

        File(dir, "a.txt").writeText("a\nmore\n")
        val changed = assertIs<GitSnapshotResult.Ready>(repository.snapshot()).snapshot.status.files.single { it.path == "a.txt" }
        assertEquals(1, changed.totalAdded)
        val diff = assertIs<GitDiffResult.Ready>(repository.diff(changed))
        assertTrue("+more" in diff.patch)
        val untracked = assertIs<GitDiffResult.Ready>(repository.diff(GitFileChange("b.txt", unstaged = GitChangeKind.Untracked)))
        assertTrue("+b" in untracked.patch)
        assertTrue(repository.discard(changed).ok)
        assertEquals("a\n", File(dir, "a.txt").readText())
    }

    @Test
    fun `B-BUILD-017 push and pull work, a non-fast-forward pull and a rejected push are reported`() = runTest {
        val remote = File(root, "remote.git").apply { mkdirs() }
        git(remote, "init", "-q", "--bare", "-b", "main")
        val one = repo("one")
        git(one, "remote", "add", "origin", remote.absolutePath)
        File(one, "f.txt").writeText("1\n")
        val first = GitRepository(shell, one.absolutePath)
        assertTrue(first.commit(listOf("f.txt"), "one").ok)
        val pushed = first.push()
        assertEquals(GitActionNote.PushedAndTracking, pushed.note, pushed.toString())
        assertEquals("origin/main", pushed.target)

        git(root, "clone", "-q", remote.absolutePath, "two")
        val two = File(root, "two")
        git(two, "config", "user.name", "Two")
        git(two, "config", "user.email", "two\u0040example.invalid")
        File(two, "f.txt").writeText("2\n")
        val second = GitRepository(shell, two.absolutePath)
        assertTrue(second.commit(listOf("f.txt"), "two").ok)
        assertEquals(GitActionNote.Pushed, second.push().note)

        assertEquals(GitActionNote.Pulled, first.pull().note)
        File(one, "f.txt").writeText("3\n")
        assertTrue(first.commit(listOf("f.txt"), "three").ok)
        File(two, "g.txt").writeText("g\n")
        assertTrue(second.commit(listOf("g.txt"), "g").ok)
        assertTrue(second.push().ok)
        assertEquals(GitProblem.PushRejected, first.push().problem)
        assertEquals(GitProblem.CannotFastForward, first.pull().problem)
    }

    private fun gitOut(dir: File, vararg args: String): String {
        val p = ProcessBuilder(listOf("git") + args).directory(dir).redirectErrorStream(true)
            .apply { environment()["HOME"] = root.absolutePath; environment()["GIT_CONFIG_NOSYSTEM"] = "1" }.start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return out
    }

    @Test
    fun `B-BUILD-017 a rename saved with its old path leaves nothing staged and only the new file`() = runTest {
        val dir = repo("rename")
        File(dir, "old.kt").writeText("x\n")
        git(dir, "add", "old.kt")
        git(dir, "commit", "-q", "-m", "first")
        git(dir, "mv", "old.kt", "new.kt")
        val repo = GitRepository(shell, dir.absolutePath)
        val file = assertIs<GitSnapshotResult.Ready>(repo.snapshot()).snapshot.status.files.single()
        assertEquals("new.kt", file.path)
        assertEquals("old.kt", file.originalPath)
        // What the page saves for a ticked rename: both halves.
        val saved = repo.commit(listOf(file.path), "rename", renamedFrom = listOfNotNull(file.originalPath))
        assertTrue(saved.ok, "${saved.failure?.problem} ${saved.failure?.details}")
        assertEquals("", gitOut(dir, "status", "--porcelain").trim())
        assertEquals("new.kt", gitOut(dir, "ls-tree", "--name-only", "HEAD").trim())
    }

    @Test
    fun `B-BUILD-014 add remote, clone beside the project and a quiet fetch`() = runTest {
        val origin = repo("origin")
        File(origin, "a.txt").writeText("a\n")
        git(origin, "add", "a.txt")
        git(origin, "commit", "-q", "-m", "first")
        val work = repo("work")
        val repo = GitRepository(shell, work.absolutePath)
        assertEquals(GitProblem.RemoteAddressHasPassword, repo.addRemote("origin", "https://me:" + "pw@example.invalid/r.git").problem)
        assertEquals(GitProblem.InvalidRemoteName, repo.addRemote("-x", origin.absolutePath).problem)
        val added = repo.addRemote("origin", origin.absolutePath)
        assertTrue(added.ok, added.failure?.details)
        assertEquals(GitActionNote.RemoteAdded, added.note)
        assertEquals("origin", assertIs<GitSnapshotResult.Ready>(repo.snapshot()).snapshot.remotes.single().name)
        assertEquals(GitProblem.RemoteExists, repo.addRemote("origin", origin.absolutePath).problem)

        val cloned = repo.clone(origin.absolutePath, "copy")
        assertTrue(cloned.ok, cloned.failure?.details)
        assertEquals(File(root, "copy").absolutePath, cloned.clonedTo)
        assertTrue(File(root, "copy/a.txt").isFile)
        assertEquals(GitProblem.FolderExists, repo.clone(origin.absolutePath, "copy").problem)

        // The clone tracks origin/main: another computer pushes, a fetch makes "behind" true.
        val copy = GitRepository(shell, File(root, "copy").absolutePath)
        File(origin, "b.txt").writeText("b\n")
        git(origin, "add", "b.txt")
        git(origin, "commit", "-q", "-m", "second")
        assertEquals(0, assertIs<GitSnapshotResult.Ready>(copy.snapshot()).snapshot.status.branch.behind)
        assertTrue(copy.fetch().ok)
        assertEquals(1, assertIs<GitSnapshotResult.Ready>(copy.snapshot()).snapshot.status.branch.behind)
        assertEquals(GitProblem.NoUpstream, repo.fetch().problem)
    }

    @Test
    fun `B-GIT-147 a commit's patch and the whole branch's patch name the committed file's change`() = runTest {
        val dir = repo("committed")
        File(dir, "index.html").writeText("hello\n")
        git(dir, "add", "index.html")
        git(dir, "commit", "-q", "-m", "first")
        val repository = GitRepository(shell, dir.absolutePath)
        // All the work sits on main: the whole branch is still listed (against the empty tree).
        val branch = assertIs<GitDiffResult.Ready>(repository.branchPatch())
        assertTrue("+hello" in branch.patch && "index.html" in branch.patch, branch.patch)
        val id = ProcessBuilder("git", "rev-parse", "HEAD").directory(dir).start().inputStream.bufferedReader().readText().trim()
        val commit = assertIs<GitDiffResult.Ready>(repository.commitPatch(id))
        assertTrue("+hello" in commit.patch, commit.patch)
        // A name that is not a commit id never reaches a script.
        assertIs<GitDiffResult.Failed>(repository.commitPatch("--output=/tmp/x"))
    }

    @Test
    fun `B-GIT-157 get latest links a branch the remote already has and gets its commits`() = runTest {
        val origin = repo("origin")
        File(origin, "a.txt").writeText("a\n")
        git(origin, "add", "a.txt")
        git(origin, "commit", "-q", "-m", "first")
        git(root, "clone", "-q", origin.absolutePath, "copy")
        val copy = File(root, "copy")
        // The copy lost its link to origin/main, and origin then moved on.
        git(copy, "branch", "--unset-upstream")
        File(origin, "b.txt").writeText("b\n")
        git(origin, "add", "b.txt")
        git(origin, "commit", "-q", "-m", "second")
        val repository = GitRepository(shell, copy.absolutePath)

        val result = repository.pull()

        assertTrue(result.ok, "${result.problem} ${result.failure?.details}")
        assertEquals(GitActionNote.Pulled, result.note)
        assertEquals(1, result.count)
        assertEquals("origin/main", result.target)
        assertTrue(File(copy, "b.txt").isFile)
        assertEquals("origin/main", gitOut(copy, "rev-parse", "--abbrev-ref", "main@{u}").trim())
        // Linked now: the second Get latest takes the normal path.
        assertEquals(GitActionNote.UpToDate, repository.pull().note)

        // An unborn branch (git init plus a remote, no commit yet) links and pulls too.
        val fresh = repo("fresh")
        val unborn = GitRepository(shell, fresh.absolutePath)
        assertTrue(unborn.addRemote("origin", origin.absolutePath).ok)
        val born = unborn.pull()
        assertTrue(born.ok, "${born.problem} ${born.failure?.details}")
        assertTrue(File(fresh, "b.txt").isFile)
        assertEquals("origin/main", gitOut(fresh, "rev-parse", "--abbrev-ref", "main@{u}").trim())
    }

    @Test
    fun `B-GIT-157 get latest on a branch the remote lacks still says push it first`() = runTest {
        val origin = repo("origin")
        File(origin, "a.txt").writeText("a\n")
        git(origin, "add", "a.txt")
        git(origin, "commit", "-q", "-m", "first")
        git(root, "clone", "-q", origin.absolutePath, "copy")
        val copy = File(root, "copy")
        git(copy, "config", "user.name", "Copy")
        git(copy, "config", "user.email", "copy\u0040example.invalid")
        git(copy, "switch", "-c", "only-here")
        File(copy, "mine.txt").writeText("mine\n")
        val repository = GitRepository(shell, copy.absolutePath)
        assertTrue(repository.commit(listOf("mine.txt"), "mine").ok)

        val result = repository.pull()

        // A guard: the same words as before the fix, and nothing was linked.
        assertEquals(GitProblem.NoUpstream, result.problem)
        assertFalse(assertIs<GitSnapshotResult.Ready>(repository.snapshot()).snapshot.status.branch.hasUpstream)
    }

    @Test
    fun `B-GIT-157 get latest with no remote says there is no remote`() = runTest {
        val dir = repo("alone")
        File(dir, "a.txt").writeText("a\n")
        val repository = GitRepository(shell, dir.absolutePath)
        assertTrue(repository.commit(listOf("a.txt"), "first").ok)

        assertEquals(GitProblem.NoRemote, repository.pull().problem)
    }
}
