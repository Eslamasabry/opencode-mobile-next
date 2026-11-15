package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Issue 147: a folder chosen in the phone's storage, as the Linux side that will work in it finds it. The binds a
 * process was started with decide what it sees; the identity's own listing and write decide what it can do there.
 */
class SharedFolderReachTest {
    @get:Rule val temp = TemporaryFolder()

    private val folder = "/storage/emulated/0/MyProj"

    @Test
    fun `I147-STORAGE a process sees a chosen folder by either spelling, and only the folders it was started with`() {
        val view = SharedStorageView(broad = false, roots = listOf("/sdcard/MyProj"))
        assertTrue(view.covers(folder))
        assertTrue(view.covers("/sdcard/MyProj"))
        assertTrue(view.covers("/sdcard/MyProj/app/src"))
        assertFalse("a sibling sharing the name's start is another folder", view.covers("/storage/emulated/0/MyProj2"))
        assertFalse("a folder chosen after the start is not bound", view.covers("/storage/emulated/0/Other"))
        assertFalse("the whole volume is never bound for one folder", view.covers("/sdcard"))
        assertTrue(SharedStorageView(broad = true, roots = emptyList()).covers("/storage/emulated/0/Other"))
    }

    @Test
    fun `I147-STORAGE the answer names the real reason, the access first and a stale start last`() {
        val started = SharedStorageView(broad = false, roots = listOf("/storage/emulated/0/Old"))
        fun decide(access: Boolean = true, sees: Boolean = true, running: SharedStorageView? = null, probe: Int? = 0) =
            SharedFolderReach.decide(access, sees, folder, running, probe)
        assertEquals(SharedFolderReach.AppAccess, decide(access = false, sees = false, probe = null))
        assertEquals(SharedFolderReach.Missing, decide(sees = false, probe = null))
        assertEquals(SharedFolderReach.Denied, decide(probe = 3))
        assertEquals(SharedFolderReach.Denied, decide(probe = 6))
        assertEquals("a restart would not help a side that can't open it", SharedFolderReach.Denied, decide(running = started, probe = 5))
        assertEquals(SharedFolderReach.Restart, decide(running = started))
        assertEquals(SharedFolderReach.Ready, decide(running = SharedStorageView(false, listOf(folder))))
        assertEquals("none runs: the next start binds what the check saw", SharedFolderReach.Ready, decide(running = null))
        assertEquals(SharedFolderReach.Unknown, decide(probe = null))
    }

    private fun probe(path: String): Int {
        val process =
            ProcessBuilder("/bin/sh", "-c", SharedFolderReach.PROBE, "oc-folder-check", path)
                .redirectErrorStream(true)
                .start()
        process.outputStream.close()
        assertTrue(process.waitFor(20, TimeUnit.SECONDS))
        return process.exitValue()
    }

    @Test
    fun `I147-STORAGE the identity's check lists and really writes, and leaves nothing behind`() {
        val ready = temp.newFolder("MyProj")
        File(ready, "notes.txt").writeText("x")
        assertEquals(0, probe(ready.absolutePath))
        assertEquals(listOf("notes.txt"), ready.list()!!.toList())
        assertEquals(3, probe(File(temp.root, "Gone").absolutePath))
        assertEquals(4, probe(File(ready, "notes.txt").absolutePath))
    }

    @Test
    fun `I147-STORAGE a folder the identity may read but not write is not ready`() {
        // Root writes anywhere; the check is about an ordinary identity.
        assumeFalse(System.getProperty("user.name") == "root")
        val readOnly = temp.newFolder("ReadOnly")
        assertTrue(readOnly.setWritable(false, false))
        try {
            assertEquals(6, probe(readOnly.absolutePath))
        } finally {
            readOnly.setWritable(true, false)
        }
    }
}
