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
}
