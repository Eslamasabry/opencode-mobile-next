package io.github.eslamasabry.oc.engine.runtime.bridge

import io.github.eslamasabry.oc.core.common.getOrNull
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The script checks that need a real shell (port of `every script parses as shell` and `project folders` of
 * test/builtin_linux_test.dart). JVM only: commonTest cannot start processes. Each test passes vacuously on a machine
 * without `sh`, `bash` or `git` (never on the Linux CI and dev machines, which have all three).
 */
class BuiltinScriptsShellTest {
    private data class Run(val exitCode: Int, val stdout: String, val stderr: String)

    private fun has(tool: String): Boolean =
        System.getenv("PATH").orEmpty().split(File.pathSeparator).any { File(it, tool).canExecute() }

    private fun run(vararg command: String, cwd: File? = null): Run {
        val process = ProcessBuilder(*command).apply { cwd?.let(::directory) }.start()
        process.outputStream.close()
        val out = process.inputStream.bufferedReader().readText()
        val err = process.errorStream.bufferedReader().readText()
        check(process.waitFor(60, TimeUnit.SECONDS)) { "timed out: ${command.first()}" }
        return Run(process.exitValue(), out, err)
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    @Test
    fun `B-LINUX-024 every script parses as shell`() {
        if (!has("sh") || !has("bash")) return
        val scripts =
            mapOf(
                "install" to BuiltinScripts.installOpenCodeScript(OpenCodeRuntime.OpenCode2).getOrNull()!!,
                "server" to BuiltinScripts.serverScript(OpenCodeRuntime.OpenCode2).getOrNull()!!,
                "password" to BuiltinScripts.writePasswordScript("pw")!!,
                "version" to BuiltinScripts.versionScript(OpenCodeRuntime.OpenCode2),
                "list" to BuiltinScripts.listProjectsScript(),
                "exists" to BuiltinScripts.folderExistsScript("/root/projects/a b"),
                "create" to BuiltinScripts.createFolderScript("/root/projects/it's")!!,
                "repoState" to BuiltinScripts.repoStateScript("/root/projects/it's")!!,
                "firstCommit" to BuiltinScripts.firstCommitScript("/root/projects/it's")!!,
            )
        for ((name, script) in scripts) {
            val result = run("sh", "-n", "-c", script)
            assertEquals(0, result.exitCode, "$name: ${result.stderr}")
        }
        // The heredoc body itself is bash (arrays), not sh.
        val body = run("bash", "-n", "-c", BuiltinScriptTexts.UBUNTU_SETUP)
        assertEquals(0, body.exitCode, body.stderr)
    }

    @Test
    fun `B-LINUX-024 project folder create makes a git project once and list and exists read them back`() {
        if (!has("sh") || !has("git")) return
        val root = Files.createTempDirectory("oc-projects-").toFile()
        val other = Files.createTempDirectory("oc-bare-").toFile()
        try {
            // A quote in the path proves the quoting, not just the happy path.
            val path = "${root.path}/it's-new"
            fun sh(script: String) = run("sh", "-c", script)

            assertEquals(1, sh(BuiltinScripts.folderExistsScript(path)).exitCode)
            val created = sh(BuiltinScripts.createFolderScript(path)!!)
            assertEquals(0, created.exitCode, created.stderr)
            assertEquals("created $path\n", created.stdout)
            assertTrue(File("$path/.git").exists())
            // A new project has its first commit.
            assertEquals("ready\n", sh(BuiltinScripts.repoStateScript(path)!!).stdout)
            val bare = File(other, "bare").apply { mkdirs() }
            run("git", "init", "-q", bare.path)
            assertEquals("empty\n", sh(BuiltinScripts.repoStateScript(bare.path)!!).stdout)
            assertEquals("notgit\n", sh(BuiltinScripts.repoStateScript("${root.path}/nope")!!).stdout)
            assertEquals(0, sh(BuiltinScripts.folderExistsScript(path)).exitCode)

            assertEquals("exists $path\n", sh(BuiltinScripts.createFolderScript(path)!!).stdout)

            File(root, ".cache").mkdirs()
            val listed = sh(BuiltinScripts.listProjectsScript().replace(BuiltinScripts.PROJECTS_DIR, root.path))
            assertEquals(listOf("it's-new"), BuiltinScripts.parseProjectList(listed.stdout))
        } finally {
            root.deleteRecursively()
            other.deleteRecursively()
        }
    }

    /**
     * The shared texts and the two composed scripts, pinned to the SHA-256 of the Flutter constants they port (computed
     * from lib/ at the port). A deliberate change to a text updates its hash here with it.
     */
    @Test
    fun `B-LINUX-024 the shared setup text and the scripts made from it only change on purpose`() {
        assertEquals(3014, BuiltinScriptTexts.UBUNTU_SETUP.length)
        assertEquals("c777eea3f666671ba72c4a130dfec804dd2766ce3336056771fa776eee541b10", sha256(BuiltinScriptTexts.UBUNTU_SETUP))
        assertEquals("b14970782f0daaaeccb46365605ca14abdced821b839ba33da960a58163cd1c1", sha256(BuiltinScriptTexts.HISTORY_COPY))
        assertEquals("8e06d46d604a993e196664dae531f06af56050e08d2de2a1c2beeebad2888352", sha256(BuiltinScriptTexts.PHONE_CONTEXT))
        assertEquals(
            "8af3079f6a4bafea5697bdc3bb54f7416a976f506d7940273792b8949a99cfd1",
            sha256(BuiltinScriptTexts.FAST_PREREQUISITES),
        )
        assertEquals(
            "59638cb254ca73432b54cbb5fb8d559c05a681760bb4e7f0a042269fa850aeba",
            sha256(BuiltinScripts.installOpenCodeScript().getOrNull()!!),
        )
        assertEquals("deac5091b852089a39d1a3ae9c115acb0a40436ad416012435b9fa7d86592fda", sha256(BuiltinScripts.serverScript().getOrNull()!!))
    }
}
