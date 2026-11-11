package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotTmpCleanupTest {
    @Test
    fun `it is safe only when nothing of ours runs`() {
        assertTrue(ProotTmpCleanup.safeToClean(false, false, false, false, false))
        assertFalse(ProotTmpCleanup.safeToClean(true, false, false, false, false))
        assertFalse(ProotTmpCleanup.safeToClean(false, true, false, false, false))
        assertFalse(ProotTmpCleanup.safeToClean(false, false, true, false, false))
        assertFalse(ProotTmpCleanup.safeToClean(false, false, false, true, false))
        assertFalse(ProotTmpCleanup.safeToClean(false, false, false, false, true))
    }

    @Test
    fun `an entry touched in the last minute stays`() {
        val now = 1_000_000L
        assertTrue(ProotTmpCleanup.old(now - ProotTmpCleanup.GRACE_MS - 1, now))
        assertFalse(ProotTmpCleanup.old(now - 1_000, now))
    }

    @Test
    fun `old files and emptied folders go, the folder itself and fresh files stay`() {
        val dir = Files.createTempDirectory("proot-tmp").toFile()
        val old = File(dir, "sub/old.tmp").apply { parentFile.mkdirs(); writeText("x") }
        val fresh = File(dir, "fresh.tmp").apply { writeText("y") }
        val long = System.currentTimeMillis() - 10 * 60_000L
        old.setLastModified(long)
        old.parentFile.setLastModified(long)
        val removed = ProotTmpCleanup.clean(dir)
        assertEquals(2, removed)
        assertFalse(old.exists())
        assertFalse(old.parentFile.exists())
        assertTrue(fresh.exists())
        assertTrue(dir.exists())
        dir.deleteRecursively()
    }

    @Test
    fun `a link in the folder's place is left alone, and so is what it points to`() {
        val base = Files.createTempDirectory("proot-link").toFile()
        val target = File(base, "elsewhere").apply { mkdirs() }
        val kept = File(target, "keep.txt").apply { writeText("z"); setLastModified(System.currentTimeMillis() - 600_000L) }
        val link = File(base, "proot-tmp")
        Files.createSymbolicLink(link.toPath(), target.toPath())
        assertEquals(0, ProotTmpCleanup.clean(link))
        assertTrue(Files.isSymbolicLink(link.toPath()))
        assertTrue(kept.exists())
        base.deleteRecursively()
    }

    @Test
    fun `the owner pid is read from proot's own folder name`() {
        assertEquals(4242L, ProotTmpCleanup.ownerPid("proot-4242-AbC123"))
        assertEquals(null, ProotTmpCleanup.ownerPid("proot-x-AbC123"))
        assertEquals(null, ProotTmpCleanup.ownerPid("scratch"))
    }

    @Test
    fun `a folder of a live proot stays whole even when old, a dead one's goes`() {
        val dir = Files.createTempDirectory("proot-tmp").toFile()
        val long = System.currentTimeMillis() - 10 * 60_000L
        val live = File(dir, "proot-111-aaaaaa/loader").apply { parentFile.mkdirs(); writeText("x") }
        val dead = File(dir, "proot-222-bbbbbb/loader").apply { parentFile.mkdirs(); writeText("y") }
        val other = File(dir, "stray.tmp").apply { writeText("z") }
        for (f in listOf(live, live.parentFile, dead, dead.parentFile, other)) f.setLastModified(long)
        val removed = ProotTmpCleanup.clean(dir, pidAlive = { it == 111L })
        assertEquals(3, removed)
        assertTrue(live.exists() && live.parentFile.exists())
        assertFalse(dead.parentFile.exists())
        assertFalse(other.exists())
        dir.deleteRecursively()
    }

    @Test
    fun `a folder made after the check is still protected when its process runs`() {
        val dir = Files.createTempDirectory("proot-tmp").toFile()
        val late = File(dir, "proot-333-cccccc/bind").apply { parentFile.mkdirs(); writeText("x") }
        late.setLastModified(System.currentTimeMillis() - 3_600_000L)
        late.parentFile.setLastModified(System.currentTimeMillis() - 3_600_000L)
        assertEquals(0, ProotTmpCleanup.clean(dir, pidAlive = { true }))
        assertTrue(late.exists())
        dir.deleteRecursively()
    }
}
