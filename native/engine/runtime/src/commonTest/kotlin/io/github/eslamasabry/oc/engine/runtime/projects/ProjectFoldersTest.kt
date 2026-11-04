package io.github.eslamasabry.oc.engine.runtime.projects

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FolderProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Folders of OpenCode inside the app, read from Ubuntu's files on the phone (test/builtin_folders_test.dart). */
class ProjectFoldersTest {
    /** A phone filesystem in memory: absolute path → kind; links are never followed. */
    private class MemoryFiles : PhoneFiles {
        val nodes = mutableMapOf<String, NodeKind>()

        fun folder(path: String) {
            var at = ""
            for (segment in path.split('/').filter { it.isNotEmpty() }) {
                at += "/$segment"
                nodes.getOrPut(at) { NodeKind.Directory }
            }
        }

        fun file(path: String) {
            folder(path.substringBeforeLast('/'))
            nodes[path] = NodeKind.Other
        }

        fun link(path: String) {
            folder(path.substringBeforeLast('/'))
            nodes[path] = NodeKind.Link
        }

        fun delete(path: String) {
            nodes.keys.removeAll { it == path || it.startsWith("$path/") }
        }

        fun rename(from: String, to: String) {
            val moved = nodes.filterKeys { it == from || it.startsWith("$from/") }
            delete(from)
            folder(to.substringBeforeLast('/'))
            moved.forEach { (path, kind) -> nodes[to + path.removePrefix(from)] = kind }
        }

        override suspend fun kind(path: String): NodeKind = nodes[path] ?: NodeKind.Missing

        override suspend fun names(path: String): Outcome<List<String>> =
            nodes.keys.filter { it.substringBeforeLast('/') == path }.map { it.substringAfterLast('/') }.ok()
    }

    private val files = MemoryFiles()
    private val temp = "/data/user/0/app/files-test"
    private val rootfs = "$temp/ubuntu"
    private val projects = "$temp/projects"

    init {
        files.folder(rootfs)
        files.folder("$rootfs/root/projects/demo/.git")
        files.folder("$rootfs/root/projects/demo/src")
        files.folder("$rootfs/root/projects/Notes")
        files.folder("$rootfs/root/projects/.cache")
        files.file("$rootfs/root/projects/README.md")
        files.link("$rootfs/root/projects/alias")
        // A worktree's .git is a file, not a folder.
        files.folder("$rootfs/root/projects/wt")
        files.file("$rootfs/root/projects/wt/.git")
        for (name in listOf("dev", "proc", "sys", "usr", "opt")) files.folder("$rootfs/$name")
    }

    private fun lister() = ProjectFolders(files, locate = { rootfs })

    private fun retained() =
        ProjectFolders(files, locate = { rootfs.takeIf { files.nodes.containsKey(it) } }, locateProjects = { projects })

    private fun Outcome<List<FolderEntry>>.entries(): List<FolderEntry> =
        (this as? Outcome.Ok)?.value ?: fail("expected a listing, got $this")

    private fun Outcome<*>.failure(): Failure = (this as? Outcome.Failed)?.failure ?: fail("expected a failure, got $this")

    private fun migrate() = files.rename("$rootfs/root/projects", projects)

    /** Ubuntu that must never be run: every call fails the test. */
    private val stoppedUbuntu = ProjectNames { fail("Ubuntu was run") }

    @Test
    fun `B-LINUX-016 lists folders with their git mark, sorted, without hidden ones, files or links`() = runTest {
        assertEquals(
            listOf(
                FolderEntry("demo", "/root/projects/demo", true),
                FolderEntry("Notes", "/root/projects/Notes", false),
                FolderEntry("wt", "/root/projects/wt", true),
            ),
            lister().list("/root/projects").entries(),
        )
    }

    @Test
    fun `B-LINUX-016 goes into a folder and back up to the root`() = runTest {
        assertEquals(listOf("/root/projects/demo/src"), lister().list("/root/projects/demo").entries().map { it.path })
        assertEquals("/root/projects", ProjectFolders.parentOf("/root/projects/demo"))
        assertEquals("/", ProjectFolders.parentOf("/root"))
        assertNull(ProjectFolders.parentOf("/"))
        // /dev, /proc and /sys are Android's at run time: empty here, not shown.
        assertEquals(listOf("opt", "root", "usr"), lister().list("/").entries().map { it.name })
    }

    @Test
    fun `B-LINUX-016 a fresh install without the projects folder lists nothing`() = runTest {
        files.delete("$rootfs/root/projects")
        assertEquals(emptyList(), lister().list("/root/projects").entries())
        assertEquals(Failure.Folder(FolderProblem.Missing), lister().list("/root/elsewhere").failure())
    }

    @Test
    fun `B-LINUX-016 never follows a link or climbs out with dot-dot`() = runTest {
        assertEquals(Failure.Folder(FolderProblem.Linked), lister().list("/root/projects/alias").failure())
        assertEquals(Failure.Refused(RefusalReason.InvalidAddress), lister().list("/root/../../..").failure())
        assertEquals("/root/projects/demo", ProjectFolders.normalize("/root//projects/./demo/"))
        assertNull(ProjectFolders.normalize("root/projects"))
        assertNull(lister().hostDirectory("/root/projects/alias"))
        assertEquals("$rootfs/root/projects/demo", lister().hostDirectory("/root/projects/demo"))
    }

    @Test
    fun `B-LINUX-016 lists with the server stopped and never runs Ubuntu`() = runTest {
        val folders = ProjectFolders(files, locate = { rootfs }, throughUbuntu = stoppedUbuntu)
        assertEquals(listOf("demo", "Notes", "wt"), folders.list("/root/projects").entries().map { it.name })
    }

    @Test
    fun `B-LINUX-017 the same browser follows migration and keeps projects after removal`() = runTest {
        val folders = retained()
        assertEquals(listOf("demo", "Notes", "wt"), folders.list("/root/projects").entries().map { it.name })
        migrate()
        files.delete(rootfs)
        val browser = ProjectFolders(files, { rootfs.takeIf { files.nodes.containsKey(it) } }, { projects }, stoppedUbuntu)
        assertEquals(
            listOf("/root/projects/demo" to true, "/root/projects/Notes" to false, "/root/projects/wt" to true),
            browser.list("/root/projects").entries().map { it.path to it.isGit },
        )
        assertEquals(listOf("/root/projects/demo/src"), browser.list("/root/projects/demo").entries().map { it.path })
        // A later setup uses the retained binding over an empty mountpoint.
        files.folder("$rootfs/root/projects")
        assertEquals(listOf("demo", "Notes", "wt"), browser.list("/root/projects").entries().map { it.name })
    }

    @Test
    fun `B-LINUX-017 root navigation shows projects without a mountpoint or runtime`() = runTest {
        val folders = retained()
        migrate()
        files.folder("$projects/.git")
        assertEquals(listOf("/root/projects" to true), folders.list("/root").entries().map { it.path to it.isGit })
        files.delete(rootfs)
        assertEquals(listOf("/root"), folders.list("/").entries().map { it.path })
        assertEquals(listOf("/root/projects"), folders.list("/root").entries().map { it.path })
    }

    @Test
    fun `B-LINUX-017 external projects override the legacy path, with no duplicate row`() = runTest {
        val folders = retained()
        files.folder("$projects/retained")
        assertEquals(listOf("/root/projects"), folders.list("/root").entries().map { it.path })
        assertEquals(listOf("/root/projects/retained"), folders.list("/root/projects").entries().map { it.path })
        assertEquals(Failure.Folder(FolderProblem.Missing), folders.list("/root/projects/demo").failure())
        files.folder("$rootfs/root/projects-other/legacy")
        assertEquals(listOf("legacy"), folders.list("/root/projects-other").entries().map { it.name })
    }

    @Test
    fun `B-LINUX-017 external storage never follows links or traversal paths`() = runTest {
        val folders = retained()
        migrate()
        for (path in listOf("/root/projects/alias", "/root/projects/alias/child")) {
            assertEquals(Failure.Folder(FolderProblem.Linked), folders.list(path).failure(), path)
        }
        assertEquals(Failure.Refused(RefusalReason.InvalidAddress), folders.list("/root/projects/../outside").failure())
        files.rename(projects, "$temp/other")
        files.link(projects)
        assertEquals(Failure.Folder(FolderProblem.Linked), folders.list("/root/projects").failure())
    }

    @Test
    fun `B-LINUX-017 missing nested folders and removed storage read as missing`() = runTest {
        val folders = retained()
        migrate()
        files.delete(rootfs)
        assertEquals(Failure.Folder(FolderProblem.Missing), folders.list("/root/projects/missing").failure())
        files.delete(projects)
        assertEquals(Failure.Folder(FolderProblem.NotInstalled), folders.list("/root/projects").failure())
    }

    @Test
    fun `B-LINUX-017 without Ubuntu's files only the projects folder lists, through Ubuntu`() = runTest {
        var runs = 0
        val folders = ProjectFolders(files, locate = { null }, throughUbuntu = ProjectNames {
            runs++
            listOf("hello").ok()
        })
        val entries = folders.list("/root/projects").entries()
        assertEquals(listOf("/root/projects/hello"), entries.map { it.path })
        assertEquals(false, entries.single().isGit, "not claimed unchecked")
        assertTrue(folders.list("/root").failure() is Failure.Unknown)
        assertEquals(1, runs)
    }

    @Test
    fun `B-LINUX-017 Ubuntu's place on the phone is files linux ubuntu, installed by its ready mark`() = runTest {
        val phone = MemoryFiles()
        val locations = PhoneFolderLocations(phone, "/files")
        assertNull(locations.rootfs(), "nothing unpacked")
        phone.folder("/files/linux/ubuntu")
        assertNull(locations.rootfs(), "unpacked but not marked ready")
        phone.file("/files/linux/ubuntu.ready")
        assertEquals("/files/linux/ubuntu", locations.rootfs())
        assertEquals("/files/projects", locations.projectStorage())
        assertNull(PhoneFolderLocations(phone, null).rootfs(), "a platform without the built-in Linux")
        assertNull(PhoneFolderLocations(phone, null).projectStorage())
    }
}
