package io.github.eslamasabry.oc.engine.linuxtools

import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Port of test/builtin_folders_test.dart: folders of OpenCode inside the app, read straight from Ubuntu's files on the
 * phone (no server, no Ubuntu run), against a real temporary tree.
 */
class BuiltinFoldersTest {
    private val temp: File = tempDir("rootfs")
    private val rootfs = File(temp, "ubuntu").apply { mkdirs() }
    private val fs = NioRootfsFileSystem()

    private fun folder(path: String) = File(rootfs.path + path).mkdirs()

    init {
        folder("/root/projects/demo/.git")
        folder("/root/projects/demo/src")
        folder("/root/projects/Notes")
        folder("/root/projects/.cache")
        File(rootfs, "root/projects/README.md").createNewFile()
        symlink(File(rootfs, "root/projects/alias"), "/etc")
        // A worktree's .git is a file, not a folder.
        folder("/root/projects/wt")
        File(rootfs, "root/projects/wt/.git").writeText("gitdir:")
        for (name in listOf("dev", "proc", "sys", "usr", "opt")) folder("/$name")
    }

    @AfterTest
    fun cleanUp() {
        temp.deleteTree()
    }

    private fun lister() = BuiltinRootfsFolders(fs, locate = { rootfs.path })

    /** A stopped Ubuntu: asking it for anything but the projects names fails the test. */
    private class StoppedLinux : ProjectFolderNames {
        val calls = mutableListOf<String>()
        var projects = listOf<String>()

        override suspend fun list() = projects.also { calls += "list" }.ok()
    }

    private val projects = File(temp, "projects")

    private fun retained() =
        BuiltinRootfsFolders(fs, locate = { if (rootfs.exists()) rootfs.path else null }, locateProjects = { projects.path })

    private fun migrate() = check(File(rootfs, "root/projects").renameTo(projects))

    @Test
    fun `B-LINUX-231 lists folders with their git mark, sorted, without hidden, files or links`() = runTest {
        val entries = lister().list("/root/projects").valueOrNull()!!
        assertEquals(
            listOf(
                Triple("demo", "/root/projects/demo", true),
                Triple("Notes", "/root/projects/Notes", false),
                Triple("wt", "/root/projects/wt", true),
            ),
            entries.map { Triple(it.name, it.path, it.isGit) },
        )
    }

    @Test
    fun `B-LINUX-231 goes into a folder and back up to the root`() = runTest {
        val folders = lister()
        assertEquals(listOf("/root/projects/demo/src"), folders.list("/root/projects/demo").valueOrNull()!!.map { it.path })
        assertEquals("/root/projects", BuiltinRootfsFolders.parentOf("/root/projects/demo"))
        assertEquals("/", BuiltinRootfsFolders.parentOf("/root"))
        assertNull(BuiltinRootfsFolders.parentOf("/"))
        // /dev, /proc and /sys are Android's at run time: empty here, not shown.
        assertEquals(listOf("opt", "root", "usr"), folders.list("/").valueOrNull()!!.map { it.name })
    }

    @Test
    fun `B-LINUX-231 a fresh install without the projects folder lists nothing`() = runTest {
        File(rootfs, "root/projects").deleteTree()
        assertEquals(emptyList(), lister().list("/root/projects").valueOrNull())
        assertEquals(FolderListProblem.Missing, lister().list("/root/elsewhere").problemOrNull())
    }

    @Test
    fun `B-SEC-022 never follows a link or climbs out with dot-dot`() = runTest {
        assertEquals(FolderListProblem.Linked, lister().list("/root/projects/alias").problemOrNull())
        assertEquals(FolderListProblem.Invalid, lister().list("/root/../../..").problemOrNull())
        assertEquals("/root/projects/demo", BuiltinRootfsFolders.normalize("/root//projects/./demo/"))
        assertNull(BuiltinRootfsFolders.normalize("root/projects"))
        assertNull(lister().hostDirectory("/root/projects/alias"), "a link is never a host folder")
        assertNull(lister().hostDirectory("/root/../etc"))
        assertEquals("${rootfs.path}/root/projects/demo", lister().hostDirectory("/root/projects/demo"))
    }

    @Test
    fun `B-LINUX-231 lists with the server stopped and never runs Ubuntu`() = runTest {
        val linux = StoppedLinux()
        val entries = BuiltinFolders(lister(), linux).list("/root/projects").valueOrNull()!!
        assertEquals(listOf("demo", "Notes", "wt"), entries.map { it.name })
        assertTrue(linux.calls.isEmpty())
    }

    @Test
    fun `B-LINUX-231 the same browser follows migration and preserved projects after removal`() = runTest {
        val folders = retained()
        assertEquals(listOf("demo", "Notes", "wt"), folders.list("/root/projects").valueOrNull()!!.map { it.name })
        migrate()
        rootfs.deleteTree()
        val linux = StoppedLinux()
        val browser = BuiltinFolders(folders, linux)
        assertEquals(
            listOf("/root/projects/demo" to true, "/root/projects/Notes" to false, "/root/projects/wt" to true),
            browser.list("/root/projects").valueOrNull()!!.map { it.path to it.isGit },
        )
        assertEquals(listOf("/root/projects/demo/src"), browser.list("/root/projects/demo").valueOrNull()!!.map { it.path })
        assertTrue(linux.calls.isEmpty())
        // A later setup uses the retained binding over an empty mountpoint.
        folder("/root/projects")
        assertEquals(listOf("demo", "Notes", "wt"), browser.list("/root/projects").valueOrNull()!!.map { it.name })
    }

    @Test
    fun `B-LINUX-231 root navigation includes projects without a mountpoint or runtime`() = runTest {
        val folders = retained()
        migrate()
        File(projects, ".git").mkdirs()
        assertEquals(listOf("/root/projects" to true), folders.list("/root").valueOrNull()!!.map { it.path to it.isGit })
        rootfs.deleteTree()
        assertEquals(listOf("/root"), folders.list("/").valueOrNull()!!.map { it.path })
        assertEquals(listOf("/root/projects"), folders.list("/root").valueOrNull()!!.map { it.path })
    }

    @Test
    fun `B-LINUX-231 external projects override the legacy path, with no duplicate row`() = runTest {
        val folders = retained()
        File(projects, "retained").mkdirs()
        assertEquals(listOf("/root/projects"), folders.list("/root").valueOrNull()!!.map { it.path })
        assertEquals(listOf("/root/projects/retained"), folders.list("/root/projects").valueOrNull()!!.map { it.path })
        assertEquals(FolderListProblem.Missing, folders.list("/root/projects/demo").problemOrNull())
        folder("/root/projects-other/legacy")
        assertEquals(listOf("legacy"), folders.list("/root/projects-other").valueOrNull()!!.map { it.name })
    }

    @Test
    fun `B-SEC-022 external storage never follows links or traversal paths`() = runTest {
        val folders = retained()
        migrate()
        for (path in listOf("/root/projects/alias", "/root/projects/alias/child")) {
            assertEquals(FolderListProblem.Linked, folders.list(path).problemOrNull(), path)
        }
        assertEquals(FolderListProblem.Invalid, folders.list("/root/projects/../outside").problemOrNull())
        check(projects.renameTo(File(temp, "other")))
        symlink(projects, "${temp.path}/other")
        assertEquals(FolderListProblem.Linked, folders.list("/root/projects").problemOrNull())
        assertNull(folders.hostDirectory("/root/projects"), "a linked project store is never read")
    }

    @Test
    fun `B-LINUX-231 missing nested folders and removed storage stay missing`() = runTest {
        val folders = retained()
        migrate()
        rootfs.deleteTree()
        assertEquals(FolderListProblem.Missing, folders.list("/root/projects/missing").problemOrNull())
        projects.deleteTree()
        assertEquals(FolderListProblem.NotInstalled, folders.list("/root/projects").problemOrNull())
    }

    @Test
    fun `B-LINUX-231 without Ubuntu's files only the projects folder is listed, through Ubuntu`() = runTest {
        val linux = StoppedLinux().apply { projects = listOf("hello") }
        val folders = BuiltinFolders(BuiltinRootfsFolders(fs, locate = { null }), linux)
        val entries = folders.list("/root/projects").valueOrNull()!!
        assertEquals(listOf("/root/projects/hello"), entries.map { it.path })
        assertFalse(entries.single().isGit, "not claimed unchecked")
        assertEquals(FolderListProblem.Failed, folders.list("/root").problemOrNull())
    }

    @Test
    fun `B-LINUX-231 Ubuntu's place on the phone matches the Android side`() {
        val kotlin = File(ANDROID_LINUX_SOURCES, "BuiltinLinux.kt").readText()
        assertTrue(kotlin.contains("val home = File(context.filesDir, \"linux\")"))
        assertTrue(kotlin.contains("val rootfs = File(home, \"ubuntu\")"))
        assertTrue(kotlin.contains("File(home, \"ubuntu.ready\")"))
    }
}

/** The native Android Linux bridge sources (:android:linux), read by the source-contract tests. */
internal val ANDROID_LINUX_SOURCES: File =
    File("../../android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile").absoluteFile
