package io.github.eslamasabry.oc.engine.linuxtools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of test/shared_storage_path_test.dart (lib/domain/shared_storage_path.dart). */
class SharedStoragePathTest {
    @Test
    fun `B-LINUX-221 shared storage paths are recognised by every name Android gives`() {
        for (path in listOf(
            "/sdcard", "/sdcard/CodeAnything", "/sdcard/CodeAnything/", "/storage/emulated/0/CodeAnything",
            "/storage/emulated/10/Documents", "/storage/self/primary/x", "/storage/1A2B-3C4D/projects/app",
            "/mnt/sdcard/app", "/data/data/com.termux/files/home/storage/shared/app", "~/storage/shared/app",
            "/root/projects/../../sdcard/app",
        )) {
            assertTrue(isSharedStoragePath(path), path)
        }
    }

    @Test
    fun `B-LINUX-221 the app project space and other servers are not shared storage`() {
        for (path in listOf(
            null, "", "/", "/root/projects/app", "/root/projects/sdcard", "/home/me/sdcard",
            "/data/data/com.termux/files/home/projects/app", "/data/data/com.termux/files/home", "/storage",
            "/storage/emulated", "/storagefoo/emulated/0", "/sdcardx/app", "C:/Users/me/project",
        )) {
            assertFalse(isSharedStoragePath(path), "$path")
        }
    }

    @Test
    fun `B-LINUX-221 every spelling of an opened folder becomes one canonical root`() {
        val want = "/storage/emulated/0/CodeAnything"
        for (path in listOf(
            "/sdcard/CodeAnything", "/sdcard/CodeAnything/", "/storage/self/primary/CodeAnything",
            "/mnt/user/0/primary/CodeAnything", "/root/projects/../../sdcard/CodeAnything",
        )) {
            assertEquals(want, sharedProjectRoot(path), path)
        }
        assertEquals("/storage/1A2B-3C4D/Work", sharedProjectRoot("/storage/1A2B-3C4D/Work"))
    }

    @Test
    fun `B-LINUX-221 a whole volume, app space and Termux are never a root`() {
        for (path in listOf(
            "/sdcard", "/storage/emulated/0", "/storage", "/root/projects/app",
            "/data/data/com.termux/files/home/storage/shared/x", null,
        )) {
            assertNull(sharedProjectRoot(path), "$path")
        }
    }

    @Test
    fun `B-LINUX-221 before AI Team the whole shared storage is visible`() {
        assertEquals(
            listOf("--bind=/storage", "--bind=/storage/emulated/0:/sdcard"),
            sharedStorageProotBinds(emptyList(), confined = false),
        )
    }

    @Test
    fun `B-LINUX-221 with AI Team only the opened folders are bound, with alias`() {
        val binds =
            sharedStorageProotBinds(
                listOf("/sdcard/CodeAnything", "/storage/emulated/0/CodeAnything", "/storage/1A2B-3C4D/Work", "/sdcard"),
                confined = true,
            )
        assertEquals(
            listOf(
                "--bind=/storage/emulated/0/CodeAnything",
                "--bind=/storage/emulated/0/CodeAnything:/sdcard/CodeAnything",
                "--bind=/storage/1A2B-3C4D/Work",
            ),
            binds,
        )
        assertTrue(sharedStorageProotBinds(emptyList(), confined = true).isEmpty())
    }
}
