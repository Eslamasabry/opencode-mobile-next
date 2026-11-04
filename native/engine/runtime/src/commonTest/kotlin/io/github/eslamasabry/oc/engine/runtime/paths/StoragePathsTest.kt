package io.github.eslamasabry.oc.engine.runtime.paths

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FolderProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.failureOrNull
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of test/phone_storage_folders_test.dart and test/shared_storage_path_test.dart (the pure-path parts). */
class StoragePathsTest {
    private val root = PhoneStorageFolders.ROOT

    /** A folder tree in memory: path → children. */
    private class FakeFiles(val tree: Map<String, List<PhoneFolderChild>>, val denied: Set<String> = emptySet()) :
        PhoneFolderFiles {
        override suspend fun isDirectory(path: String): Boolean = path in tree || path in denied

        override suspend fun children(path: String): Outcome<List<PhoneFolderChild>> =
            when {
                path in denied -> Failure.Folder(FolderProblem.Denied).failed()
                else -> tree[path]?.ok() ?: Failure.Folder(FolderProblem.Missing).failed()
            }
    }

    private fun dir(name: String) = PhoneFolderChild(name, isDirectory = true)

    private fun file(name: String) = PhoneFolderChild(name, isDirectory = false)

    // ---- B-ANDR-051

    @Test
    fun `B-ANDR-051 sdcard reads as the internal storage and nothing outside it does`() {
        assertEquals(root, PhoneStorageFolders.normalize("/sdcard"))
        assertEquals("$root/Code", PhoneStorageFolders.normalize("/sdcard/Code/"))
        assertNull(PhoneStorageFolders.normalize("/data/data"))
        assertNull(PhoneStorageFolders.normalize("$root/../x"))
    }

    @Test
    fun `B-ANDR-051 the parent stops at the internal storage`() {
        assertNull(PhoneStorageFolders.parentOf(root))
        assertEquals("$root/a", PhoneStorageFolders.parentOf("$root/a/b"))
    }

    @Test
    fun `B-ANDR-051 a path outside is invalid and a missing folder is said so`() = runTest {
        val folders = PhoneStorageFolders(FakeFiles(mapOf(root to emptyList())))
        assertEquals(Failure.Refused(RefusalReason.InvalidAddress), folders.list("/etc").failureOrNull())
        assertEquals(
            Failure.Folder(FolderProblem.Missing),
            folders.list("$root/definitely-not-here").failureOrNull(),
        )
    }

    @Test
    fun `B-ANDR-051 folders are listed by name with a hint of what is inside`() = runTest {
        val files =
            FakeFiles(
                mapOf(
                    root to listOf(dir("zeta"), file("notes.txt"), dir("Alpha"), dir(".hidden"), dir("locked")),
                    "$root/Alpha" to listOf(dir("src"), file("b.txt"), file("package.json"), dir(".git"), file("a.md")),
                    "$root/zeta" to emptyList(),
                ),
                denied = setOf("$root/locked"),
            )
        val entries = PhoneStorageFolders(files).list("/sdcard").getOrNull()!!
        assertEquals(listOf(".hidden", "Alpha", "locked", "zeta"), entries.map { it.name })
        val alpha = entries[1]
        assertEquals("$root/Alpha", alpha.path)
        assertTrue(alpha.isGit)
        assertEquals(listOf("package.json", "src"), alpha.inside, "project files first, then folders")
        assertEquals(2, alpha.more)
        assertNull(entries[0].inside, "hidden folders get no hint")
        assertEquals(emptyList(), entries[2].inside, "an unreadable folder still shows")
        assertEquals(emptyList(), entries[3].inside)
        assertFalse(entries[3].isGit)
    }

    // ---- B-ANDR-079

    @Test
    fun `B-ANDR-079 shared storage paths are recognised by every name Android gives`() {
        for (
        path in listOf(
            "/sdcard",
            "/sdcard/CodeAnything",
            "/sdcard/CodeAnything/",
            "/storage/emulated/0/CodeAnything",
            "/storage/emulated/10/Documents",
            "/storage/self/primary/x",
            "/storage/1A2B-3C4D/projects/app",
            "/mnt/sdcard/app",
            "/data/data/com.termux/files/home/storage/shared/app",
            "~/storage/shared/app",
            "/root/projects/../../sdcard/app",
        )
        ) {
            assertTrue(SharedStoragePath.isShared(path), path)
        }
    }

    @Test
    fun `B-ANDR-079 the app project space and other servers are not shared storage`() {
        for (
        path in listOf(
            null,
            "",
            "/",
            "/root/projects/app",
            "/root/projects/sdcard",
            "/home/me/sdcard",
            "/data/data/com.termux/files/home/projects/app",
            "/data/data/com.termux/files/home",
            "/storage",
            "/storage/emulated",
            "/storagefoo/emulated/0",
            "/sdcardx/app",
            "C:/Users/me/project",
        )
        ) {
            assertFalse(SharedStoragePath.isShared(path), "$path")
        }
    }

    // ---- B-ANDR-080

    @Test
    fun `B-ANDR-080 every spelling of an opened folder becomes one canonical root`() {
        val want = "/storage/emulated/0/CodeAnything"
        for (
        path in listOf(
            "/sdcard/CodeAnything",
            "/sdcard/CodeAnything/",
            "/storage/self/primary/CodeAnything",
            "/mnt/user/0/primary/CodeAnything",
            "/root/projects/../../sdcard/CodeAnything",
        )
        ) {
            assertEquals(want, SharedStoragePath.projectRoot(path), path)
        }
        assertEquals("/storage/1A2B-3C4D/Work", SharedStoragePath.projectRoot("/storage/1A2B-3C4D/Work"))
        assertEquals("/storage/emulated/10/Docs", SharedStoragePath.projectRoot("/mnt/user/0/emulated/10/Docs"))
    }

    @Test
    fun `B-ANDR-080 a whole volume, the app space and Termux are never a root`() {
        for (
        path in listOf(
            "/sdcard",
            "/storage/emulated/0",
            "/storage",
            "/root/projects/app",
            "/data/data/com.termux/files/home/storage/shared/x",
            null,
        )
        ) {
            assertNull(SharedStoragePath.projectRoot(path), "$path")
        }
    }

    @Test
    fun `B-ANDR-080 proot binds the whole storage before AI Team and only opened folders after`() {
        assertEquals(
            listOf("--bind=/storage", "--bind=/storage/emulated/0:/sdcard"),
            SharedStoragePath.prootBinds(emptyList(), confined = false),
        )
        assertEquals(
            listOf(
                "--bind=/storage/emulated/0/CodeAnything",
                "--bind=/storage/emulated/0/CodeAnything:/sdcard/CodeAnything",
                "--bind=/storage/1A2B-3C4D/Work",
            ),
            SharedStoragePath.prootBinds(
                listOf("/sdcard/CodeAnything", "/storage/emulated/0/CodeAnything", "/storage/1A2B-3C4D/Work", "/sdcard"),
                confined = true,
            ),
        )
        assertEquals(emptyList(), SharedStoragePath.prootBinds(emptyList(), confined = true))
    }
}
