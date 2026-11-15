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
}
