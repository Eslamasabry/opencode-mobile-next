package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Issue 147: Git in the built-in Ubuntu trusts the folders chosen in the phone's storage, by both spellings, for root
 * and the agent user (both read /etc/gitconfig), and a person's own settings there stay.
 */
class SharedFolderGitTest {
    @get:Rule val temp = TemporaryFolder()

    private fun rootfs(text: String? = null): File =
        temp.newFolder("ubuntu").also { root ->
            File(root, "etc").mkdirs()
            if (text != null) File(root, "etc/gitconfig").writeText(text)
        }

    /** What Git itself reads as safe.directory from [config]. */
    private fun safeDirectories(config: File): List<String> {
        val process =
            ProcessBuilder("git", "config", "--system", "--get-all", "safe.directory")
                .apply { environment()["GIT_CONFIG_SYSTEM"] = config.absolutePath }
                .redirectErrorStream(true)
                .start()
        assertTrue(process.waitFor(20, TimeUnit.SECONDS))
        return process.inputStream.bufferedReader().readLines().filter { it.isNotBlank() }
    }

    @Test
    fun `I147-STORAGE Git trusts each chosen folder by both spellings, even with a comment sign in its name`() {
        val rootfs = rootfs("[user]\n\tname = Someone\n")
        assertTrue(
            BuiltinGitConfig.trustSharedFolders(rootfs, listOf("/sdcard/MyProj", "/storage/emulated/0/Notes #2", "/root/projects/x"))
        )
        val config = File(rootfs, "etc/gitconfig")
        assertEquals(
            listOf("/sdcard/MyProj", "/sdcard/Notes #2", "/storage/emulated/0/MyProj", "/storage/emulated/0/Notes #2"),
            safeDirectories(config),
        )
        assertTrue("the person's own setting stays", config.readText().startsWith("[user]\n\tname = Someone\n"))
        assertFalse("once is enough", BuiltinGitConfig.trustSharedFolders(rootfs, listOf("/storage/emulated/0/MyProj", "/sdcard/Notes #2")))
    }

    @Test
    fun `I147-STORAGE the block follows the chosen folders and goes when none is left`() {
        val rootfs = rootfs("[core]\n\tcreateObject = rename\n")
        val config = File(rootfs, "etc/gitconfig")
        BuiltinGitConfig.trustSharedFolders(rootfs, listOf("/storage/emulated/0/Old"))
        assertTrue(BuiltinGitConfig.trustSharedFolders(rootfs, listOf("/storage/1A2B-3C4D/Card")))
        assertEquals(listOf("/storage/1A2B-3C4D/Card"), safeDirectories(config))
        assertTrue(BuiltinGitConfig.trustSharedFolders(rootfs, emptyList()))
        assertEquals("[core]\n\tcreateObject = rename\n", config.readText())
    }

    private fun git(dir: File, vararg args: String): String {
        val process =
            ProcessBuilder(listOf("git", "-c", "user.name=t", "-c", "user.email=t@t", "-C", dir.absolutePath) + args)
                .redirectErrorStream(true)
                .start()
        assertTrue(process.waitFor(30, TimeUnit.SECONDS))
        return process.inputStream.bufferedReader().readText().trim()
    }

    /** A repository made on a computer: an executable script, committed with Git's usual `filemode = true`. */
    private fun computerRepository(at: File): File {
        at.mkdirs()
        git(at, "init", "-q")
        File(at, "run.sh").apply { writeText("#!/bin/sh\n") }.setExecutable(true)
        git(at, "add", "-A")
        git(at, "commit", "-qm", "one")
        return at
    }

    private fun storageSettings(folder: File, under: File): Int {
        val process =
            ProcessBuilder("/bin/sh", "-c", SharedFolderReach.GIT_STORAGE, "oc-folder-check", folder.absolutePath, under.absolutePath)
                .redirectErrorStream(true)
                .start()
        assertTrue(process.waitFor(30, TimeUnit.SECONDS))
        return process.exitValue()
    }

    @Test
    fun `I147-STORAGE a repository copied from a computer into the phone's storage does not show every file as changed`() {
        val storage = temp.newFolder("storage")
        val project = computerRepository(File(storage, "MyProj"))
        val inner = computerRepository(File(project, "lib"))
        // Android's storage keeps no execute bit: the copy lost it, and Git calls that a change.
        File(project, "run.sh").setExecutable(false)
        File(inner, "run.sh").setExecutable(false)
        assertEquals("M run.sh", git(project, "status", "--short", "--untracked-files=no"))
        assertEquals(0, storageSettings(project, storage))
        assertEquals("", git(project, "status", "--short", "--untracked-files=no"))
        assertEquals("", git(inner, "status", "--short", "--untracked-files=no"))
        assertEquals("false", git(project, "config", "core.fileMode"))
        assertEquals("false", git(project, "config", "core.symlinks"))
        assertEquals("true", git(project, "config", "core.ignoreCase"))
    }

    @Test
    fun `I147-STORAGE a repository whose top is outside the phone's storage is left alone`() {
        val outer = computerRepository(temp.newFolder("outer"))
        val chosen = File(outer, "inside").apply { mkdirs() }
        assertEquals(0, storageSettings(chosen, temp.newFolder("storage")))
        assertEquals("true", git(outer, "config", "core.fileMode"))
    }
}
