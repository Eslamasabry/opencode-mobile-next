package io.github.eslamasabry.oc.engine.linuxtools

import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Port of the script-running half of test/termux_folders_test.dart: the generated scripts run for real with bash, as
 * Termux runs them (`bash -s`), against a temporary folder tree; a stand-in `proot-distro` runs the command after `--`.
 */
class TermuxFoldersScriptTest {
    private val temp: File = tempDir("termux-folders")
    private val bin = File(temp, "bin").apply { mkdirs() }

    init {
        // proot-distro login <distro> -- <command...>
        writeExecutable(File(bin, "proot-distro"), "#!/bin/sh\nshift 3\nexec \"\$@\"\n")
    }

    @AfterTest
    fun cleanUp() {
        temp.deleteTree()
    }

    private fun bash(script: String, cwd: File? = null): TermuxCommandResult {
        requireHermetic(temp, bin.path, (cwd ?: temp).path + "/x")
        val ran = runProcess(listOf("bash", "-s"), mapOf("PATH" to "${bin.path}:${System.getenv("PATH")}"), cwd ?: temp, script)
        return TermuxCommandResult(ran.stdout, ran.stderr, ran.exitCode)
    }

    private fun folders(cwd: File? = null) = TermuxFolders({ script, _ -> bash(script, cwd).ok() })

    /** A folder tree whose own path and children carry every awkward name. */
    private fun tree(): File {
        val root = File(temp, "it's \"a\" \$(touch PWNED) `touch PWNED` dir").apply { mkdirs() }
        for (name in listOf(
            "plain", "with space", "quote's \"double\"", "\$(touch PWNED)", "`touch PWNED`", "semi;colon & amp",
            ".hidden", "new\nline",
        )) {
            File(root, name).mkdirs()
        }
        // Made through the shell in UTF-8: the JVM's file-name encoding follows the host locale.
        runProcess(listOf("bash", "-s"), mapOf("D" to root.path), stdin = "mkdir -p -- \"\$D/تطبيق\"\n")
        File(root, "repo/.git").mkdirs()
        File(root, "worktree").mkdirs()
        File(root, "worktree/.git").writeText("gitdir: x")
        File(root, "a-file").writeText("x")
        symlink(File(root, "link-out"), "/etc")
        return root
    }

    @Test
    fun `B-LINUX-231 lists child folders with git marks and names are data, never code`() = runTest {
        val root = tree()
        val entries = folders(root).list(root.path).valueOrNull()!!
        assertEquals(
            setOf(
                "plain" to false, "with space" to false, "quote's \"double\"" to false, "\$(touch PWNED)" to false,
                "`touch PWNED`" to false, "semi;colon & amp" to false, "تطبيق" to false, "repo" to true,
                "worktree" to true,
            ),
            entries.map { it.name to it.isGit }.toSet(),
            "hidden, the newline name, files and links are left out",
        )
        assertEquals(9, entries.size)
        assertEquals("${root.path}/with space", entries.first { it.name == "with space" }.path)
        // Nothing ran: neither in the working folder nor next to the tree.
        assertFalse(File(root, "PWNED").exists())
        assertFalse(File(temp, "PWNED").exists())
    }

    @Test
    fun `B-LINUX-231 listing a folder with an awkward name goes into it exactly`() = runTest {
        val root = tree()
        val inner = "${root.path}/quote's \"double\""
        File(inner, "child").mkdirs()
        assertEquals(listOf("$inner/child"), folders(root).list(inner).valueOrNull()!!.map { it.path })
        assertFalse(File(root, "PWNED").exists())
    }

    @Test
    fun `B-LINUX-231 the script itself refuses a newline path, whatever it is sent`() {
        val encoded = Base64.encode("${temp.path}/x\n/etc".encodeToByteArray())
        val script = "set -u\nsh -c '${TermuxFolders.LIST_INNER_SCRIPT}' -- '$encoded'\n"
        assertEquals("oc-folders-invalid", bash(script).stdout.trim())
    }

    @Test
    fun `B-LINUX-231 a link, a missing folder and the root answer as the sheet expects`() = runTest {
        val root = tree()
        assertEquals(FolderListProblem.Linked, folders().list("${root.path}/link-out").problemOrNull())
        assertEquals(FolderListProblem.Missing, folders().list("${root.path}/gone").problemOrNull())
        val names = folders().list("/").valueOrNull()!!.map { it.name }
        assertFalse("proc" in names)
        assertFalse("sys" in names)
        assertFalse("dev" in names)
        assertTrue("tmp" in names)
    }

    @Test
    fun `B-LINUX-231 makes a folder with an awkward name and finds it the second time`() = runTest {
        val root = tree()
        val path = "${root.path}/new \$(touch PWNED) \"project\""
        val termux = folders(root)
        assertEquals(CreatedFolder(path, created = true), termux.create(path).valueOrNull())
        assertTrue(File(path).isDirectory)
        assertEquals(CreatedFolder(path, created = false), termux.create(path).valueOrNull())
        assertFalse(File(root, "PWNED").exists())
    }

    @Test
    fun `B-LINUX-231 the project folder script makes exactly one folder and refuses a path`() {
        val projects = File(temp, "projects")
        // The stand-in container: /root/projects is redirected by running the script with a rewritten base folder.
        val script = termuxCreateProjectFolderScript("my-app").replace(MANAGED_PROJECTS_DIRECTORY, projects.path)
        val made = bash(script)
        assertEquals(0, made.exitCode, made.stderr)
        assertEquals("${projects.path}/my-app", made.stdout.trim().lines().last())
        assertTrue(File(projects, "my-app").isDirectory)
        val refused = bash(termuxCreateProjectFolderScript("../etc").replace(MANAGED_PROJECTS_DIRECTORY, projects.path))
        assertEquals(64, refused.exitCode)
        assertTrue(refused.stderr.contains("invalid-folder-name"))
    }
}
