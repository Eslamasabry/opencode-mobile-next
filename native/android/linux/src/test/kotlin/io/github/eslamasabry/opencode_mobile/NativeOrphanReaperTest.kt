package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeOrphanReaperTest {
    private val uid = 10234
    private val app = 9000
    private val loader = "/data/app/~~a1/io.github.eslamasabry.opencode_mobile-b2/lib/arm64/libproot-loader.so"
    private val proot = "/data/app/~~a1/io.github.eslamasabry.opencode_mobile-b2/lib/arm64/libproot.so"

    private fun entry(pid: Int, parent: Int, tracer: Int? = 0, exe: String? = loader, name: String = "bash") =
        ProcEntry(pid, uid, parent, tracer, 1000L + pid, name, exe, 'R')

    // The phone at 01:26 on 2026-10-27: the agent helper's old proot was
    // SIGKILLed, so Claude's tool shells and a flutter_tester ran on with
    // parent 1 and no tracer; a new proot runs the new helper and Claude.
    private val incident = listOf(
        entry(app, 600, exe = "/system/bin/app_process64", name = "ommasabry.opencode_mobile"),
        entry(9100, 600, exe = "/system/bin/app_process64", name = ":local_pdf"),
        entry(20000, app, exe = proot, name = "libproot.so"),
        entry(20005, 20000, tracer = 20000, name = "node"),
        entry(20100, 20005, tracer = 20000, name = "claude"),
        // A live proot's own orphan (a background job) is still traced by it.
        entry(20200, 1, tracer = 20000, name = "python3"),
        entry(21000, app, exe = "/data/app/~~a1/x/lib/arm64/libaiteam_engine.so", name = "libaiteam_engine"),
        entry(15001, 1), entry(15002, 1), entry(15003, 1), entry(15004, 1), entry(15005, 1),
        entry(15010, 1, name = "flutter_tester"),
        // A child of an orphaned shell: untraced too, its parent still alive.
        entry(15020, 15001, name = "dart"),
    )

    private fun reaped(entries: List<ProcEntry>, roots: Set<Int> = setOf(20000, 21000)) =
        NativeOrphanReaper.orphans(entries, uid, app, roots).map { it.pid }.sorted()

    @Test fun theIncidentsOrphansAreChosenAndNothingAlive() {
        assertEquals(listOf(15001, 15002, 15003, 15004, 15005, 15010, 15020), reaped(incident))
    }

    @Test fun neverTheAppALiveProotItsProgramsOrTheEngine() {
        val chosen = reaped(incident).toSet()
        for (pid in listOf(app, 9100, 20000, 20005, 20100, 20200, 21000)) assertFalse("$pid", pid in chosen)
    }

    @Test fun nothingIsChosenEvenWithNoLiveRootKnown() {
        // The rules alone protect what runs: the app, a traced program, Android's own processes.
        val chosen = reaped(incident, roots = emptySet())
        for (pid in listOf(app, 9100, 20000, 20005, 20100, 20200, 21000)) assertFalse("$pid", pid in chosen)
    }

    @Test fun aLiveRootAndItsTreeAreNeverChosenEvenWhenParentedByInit() {
        val entries = listOf(entry(16000, 1), entry(16001, 16000))
        assertEquals(emptyList<Int>(), reaped(entries, roots = setOf(16000)))
        assertEquals(listOf(16000, 16001), reaped(entries, roots = emptySet()))
    }

    @Test fun unknownOrNotGuestOrDeadOrAnotherUserIsLeftAlone() {
        val entries = listOf(
            entry(17001, 1, tracer = null),
            entry(17002, 1, exe = null),
            entry(17003, 1, exe = proot, name = "libproot.so"),
            entry(17004, 1, exe = "/system/bin/sh"),
            entry(17005, 1).copy(state = 'Z'),
            entry(17006, 1).copy(uid = 10999),
            entry(17007, 1, tracer = 4242),
            entry(1, 0), entry(app, 1),
        )
        assertEquals(emptyList<Int>(), reaped(entries))
    }

    @Test fun aLoaderReplacedByAnAppUpdateStillMarksAGuestProgram() {
        assertTrue(NativeOrphanReaper.isGuestProgram("$loader (deleted)"))
        assertTrue(NativeOrphanReaper.isGuestProgram(loader))
        assertFalse(NativeOrphanReaper.isGuestProgram(proot))
        assertFalse(NativeOrphanReaper.isGuestProgram("/usr/bin/libproot-loader"))
        assertFalse(NativeOrphanReaper.isGuestProgram(null))
        assertEquals(listOf(18000), reaped(listOf(entry(18000, 1, exe = "$loader (deleted)"))))
    }

    @Test fun statIsParsedWithSpacesAndParensInTheName() {
        val line = "15010 (flutter (tester) x) R 1 15010 15010 0 -1 4194560 100 0 0 0 " +
            "700 300 0 0 20 0 12 0 98765 1234567 4321 18446744073709551615"
        val parsed = ProcEntry.stat(line, uid, 0, loader)!!
        assertEquals(15010, parsed.pid)
        assertEquals(1, parsed.parent)
        assertEquals("flutter (tester) x", parsed.name)
        assertEquals('R', parsed.state)
        assertEquals(98765L, parsed.startTicks)
        assertEquals(1000L, parsed.cpuTicks)
        assertEquals(4321L, parsed.rssPages)
        assertNull(ProcEntry.stat("garbage", uid, 0, loader))
        assertNull(ProcEntry.stat("12 (short) R 1 2 3", uid, 0, loader))
    }

    @Test fun aReusedPidIsNotTheSameProcess() {
        val before = entry(15001, 1)
        assertTrue(before.sameProcess(before.copy()))
        assertFalse(before.sameProcess(before.copy(startTicks = before.startTicks + 1)))
        assertFalse(before.sameProcess(before.copy(uid = 10999)))
        assertFalse(before.sameProcess(null))
    }
}
