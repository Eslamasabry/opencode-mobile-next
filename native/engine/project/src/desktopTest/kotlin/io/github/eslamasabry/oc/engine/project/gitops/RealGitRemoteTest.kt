package io.github.eslamasabry.oc.engine.project.gitops

import io.github.eslamasabry.oc.core.common.getOrNull
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The scripts under a real `sh` and the real git on this machine, against a local bare repository. Skips without git. */
class RealGitRemoteTest {
    private val hasGit: Boolean =
        try {
            ProcessBuilder("sh", "-c", "command -v git && command -v base64").start().waitFor() == 0
        } catch (e: java.io.IOException) {
            false
        }

    private class ShShell(private val env: Map<String, String>) : GitOpsShell {
        override suspend fun run(script: String, timeoutMs: Long): GitOpsReply {
            val p = ProcessBuilder("sh", "-c", script).redirectErrorStream(true).apply { environment().putAll(env) }.start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            return GitOpsReply.Output(out)
        }
    }

    private fun withRepo(block: suspend (tmp: File, env: Map<String, String>, bare: String, repo: String) -> Unit) = runTest {
        if (!hasGit) return@runTest
        val tmp = Files.createTempDirectory("oc-git-clone").toFile()
        try {
            val home = File(tmp, "home").apply { mkdirs() }
            val env = mapOf("HOME" to home.absolutePath, "GIT_CONFIG_NOSYSTEM" to "1")
            fun git(dir: File, vararg args: String) {
                val p = ProcessBuilder(listOf("git") + args).directory(dir).redirectErrorStream(true).apply { environment().putAll(env) }.start()
                val out = p.inputStream.bufferedReader().readText()
                check(p.waitFor() == 0) { "${args.first()}: $out" }
            }
            val bare = File(tmp, "up.git").absolutePath
            val repo = File(tmp, "work/proj").apply { mkdirs() }
            git(tmp, "init", "-q", "--bare", bare)
            git(repo, "init", "-q")
            git(repo, "config", "user.name", "T")
            git(repo, "config", "user.email", "t@example.invalid")
            File(repo, "a.txt").writeText("a\n")
            git(repo, "add", ".")
            git(repo, "commit", "-q", "-m", "first")
            git(repo, "remote", "add", "origin", bare)
            git(repo, "push", "-q", "-u", "origin", "HEAD")
            block(tmp, env, bare, repo.absolutePath)
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `B-BUILD-014 with real git add a remote, see it with its address, refuse a duplicate`() =
        withRepo { _, env, bare, repo ->
            val ops = GitRemoteOps(ShShell(env), repo)
            val added = ops.addRemote("backup", bare)
            assertEquals(GitOpsNote.RemoteAdded, added.note, "${added.failure}")
            val remotes = ops.remotes().getOrNull()!!
            assertTrue(remotes.map { it.name }.containsAll(listOf("origin", "backup")))
            assertEquals(bare, remotes.first { it.name == "backup" }.address)
            assertEquals(GitOpsProblem.RemoteExists, ops.addRemote("backup", bare).problem)
        }

    @Test
    fun `B-BUILD-014 with real git clone lands beside the project and a taken folder is refused`() =
        withRepo { tmp, env, bare, repo ->
            val ops = GitRemoteOps(ShShell(env), repo)
            val done = ops.clone(bare, "copy")
            assertEquals(GitOpsNote.Cloned, done.note, "${done.failure}")
            assertEquals("${tmp.absolutePath}/work/copy", done.clonedTo)
            assertTrue(File(tmp, "work/copy/a.txt").exists())
            assertEquals(GitOpsProblem.FolderExists, ops.clone(bare, "copy").problem)
            val missing = ops.clone("${tmp.absolutePath}/nowhere.git", "x")
            assertFalse(missing.ok)
            assertFalse(File(tmp, "work/x").exists())
        }
}
