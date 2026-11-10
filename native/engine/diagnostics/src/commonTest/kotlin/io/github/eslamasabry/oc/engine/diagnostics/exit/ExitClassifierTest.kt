package io.github.eslamasabry.oc.engine.diagnostics.exit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExitClassifierTest {
    private fun kind(reason: Int, sub: Int = -1, description: String? = null) =
        ExitRecord(reason = reason, timestampMs = OWNER_AT, subReason = sub, importance = 125, description = description).kind

    @Test
    fun `B-ANDR-004 the owner's FORCE STOP by the system is a stop in the background`() {
        // dumpsys exit-info on the owner's RedMagic: reason=10 (USER REQUESTED) subreason=21 (FORCE STOP) importance=125.
        val record =
            ExitRecord(
                reason = 10, timestampMs = OWNER_AT, subReason = 21, importance = 125,
                description = "stop io.github.eslamasabry.opencode_mobile due to from pid 2285",
            )
        assertEquals(ExitReason.USER_REQUESTED, record.reason)
        assertEquals(ExitReason.SUB_FORCE_STOP, record.subReason)
        // Issue #138: the maker's battery manager (pid 2285) records the person's own reason; importance 125 says the
        // app was in the background, so the notice keeps a softer line instead of going quiet.
        assertEquals(ExitKind.BackgroundStop, record.kind)
        assertTrue(record.kind.notable)
        assertTrue(record.kind.announced)
        assertFalse(record.kind.closedBySystem)
    }

    @Test
    fun `B-ANDR-004 every Android reason lands on one kind`() {
        // kind() records importance 125: in the background.
        assertEquals(ExitKind.BackgroundStop, kind(10))
        assertEquals(ExitKind.BackgroundStop, kind(11))
        assertEquals(ExitKind.ForceStop, kind(13, sub = 21))
        assertEquals(ExitKind.ForceStop, kind(13, sub = 22))
        assertEquals(ExitKind.ForceStop, kind(13, sub = 23))
        assertEquals(ExitKind.LowMemory, kind(3))
        assertEquals(ExitKind.LowMemory, kind(13, sub = 6))
        assertEquals(ExitKind.LowMemory, kind(2, sub = 5))
        for (reason in listOf(4, 5, 6, 7)) assertEquals(ExitKind.Crash, kind(reason))
        assertEquals(ExitKind.Killed, kind(9))
        assertEquals(ExitKind.Killed, kind(13))
        assertEquals(ExitKind.Killed, kind(14))
        // Every reason 0..16 and an unknown future one map to exactly one kind without throwing.
        for (reason in -1..20) for (sub in listOf(-1, 2, 21, 25, 99)) kind(reason, sub)
    }

    @Test
    fun `B-ANDR-004 a normal exit and an update say nothing`() {
        assertEquals(ExitKind.Normal, kind(1))
        assertEquals(ExitKind.Normal, kind(0))
        assertEquals(ExitKind.Update, kind(16))
        assertEquals(ExitKind.Update, kind(15))
        // Android ends the old process "because the user asked" on update.
        assertEquals(ExitKind.Update, kind(10, sub = 25))
        assertEquals(ExitKind.Update, kind(13, sub = 25))
        assertFalse(ExitKind.Normal.notable)
        assertFalse(ExitKind.Update.notable)
    }

    @Test
    fun `B-ANDR-004 the Android 17 memory limiter is its own kind and its description is never kept`() {
        val description = "MemoryLimiter:AnonSwap private-token /data/user/0/secret"
        val record = ExitRecord(reason = ExitReason.OTHER, timestampMs = OWNER_AT, description = description)
        assertEquals(ExitKind.MemoryLimit, record.kind)
        assertTrue(record.kind.notable)
        assertFalse("private-token" in record.toString())
        assertFalse("MemoryLimiter" in record.toString())
        assertFalse(record.traceAttrs.values.any { "private" in it.toString() })
        // Only REASON_OTHER carries the marker's meaning.
        assertEquals(ExitKind.Crash, ExitRecord(reason = 4, timestampMs = OWNER_AT, description = description).kind)
        assertEquals(ExitKind.Killed, ExitRecord(reason = 13, timestampMs = OWNER_AT, description = "other").kind)
    }

    @Test
    fun `B-ANDR-005 a record without a time is no record`() {
        val noTime = RawLaunchReport(exit = ExitRecord(reason = 10, timestampMs = 0))
        assertEquals(null, LaunchReport.from(noTime).exit)
        assertEquals(null, LaunchReport.from(RawLaunchReport(exit = ExitRecord(reason = 10, timestampMs = -5))).exit)
        assertFalse(ExitRecord(reason = 10, timestampMs = 0).hasTime)
    }

    @Test
    fun `B-STATE-026 a crash is attributed to the app rather than a phone setting`() {
        val crash = ExitRecord(reason = ExitReason.CRASH, timestampMs = OWNER_AT)
        assertEquals(ExitKind.Crash, crash.kind)
        assertTrue(crash.kind.notable)
        assertFalse(crash.kind.offersKeepAlive)
        val kinds =
            listOf(ExitKind.ForceStop, ExitKind.BackgroundStop, ExitKind.LowMemory, ExitKind.MemoryLimit, ExitKind.Killed)
        for (kind in kinds) {
            assertTrue(kind.offersKeepAlive, kind.name)
        }
    }

    private fun announced(reason: Int, sub: Int = -1, description: String? = null, importance: Int = IN_FRONT) =
        ExitRecord(
            reason = reason, timestampMs = OWNER_AT, subReason = sub, importance = importance,
            description = description,
        ).kind.announced

    private fun stopKind(reason: Int, sub: Int, importance: Int) =
        ExitRecord(reason = reason, timestampMs = OWNER_AT, subReason = sub, importance = importance).kind

    private fun bySystem(reason: Int, sub: Int = -1) =
        ExitRecord(reason = reason, timestampMs = OWNER_AT, subReason = sub).kind.closedBySystem

    @Test
    fun `EXIT-NOTICE-001 a stop while the app is in front is never announced`() {
        // Upgrade test 2026-11-09: `am force-stop` (USER_REQUESTED, FORCE STOP, importance 100) showed
        // "Android closed OpenCode Mobile" on the next start.
        assertFalse(announced(ExitReason.USER_REQUESTED, ExitReason.SUB_FORCE_STOP))
        assertFalse(announced(ExitReason.USER_REQUESTED))
        assertFalse(announced(ExitReason.USER_REQUESTED, ExitReason.SUB_STOP_APP))
        assertFalse(announced(ExitReason.USER_STOPPED))
        assertFalse(announced(ExitReason.USER_STOPPED, ExitReason.SUB_FORCE_STOP))
        // A swipe from Recents or a stop that only the sub-reason names.
        assertFalse(announced(ExitReason.OTHER, ExitReason.SUB_REMOVE_TASK))
        assertFalse(announced(ExitReason.OTHER, ExitReason.SUB_FORCE_STOP))
        assertFalse(announced(ExitReason.OTHER, ExitReason.SUB_STOP_APP))
        assertFalse(bySystem(ExitReason.USER_REQUESTED, ExitReason.SUB_FORCE_STOP))
        assertFalse(bySystem(ExitReason.USER_STOPPED))
    }

    @Test
    fun `EXIT-NOTICE-006 a stop in the background gets the softer line, a Recents swipe and an update never do`() {
        for (reason in listOf(ExitReason.USER_REQUESTED, ExitReason.USER_STOPPED)) {
            for (importance in listOf(FOREGROUND_SERVICE, VISIBLE, CACHED)) {
                val case = "$reason/$importance"
                assertEquals(ExitKind.BackgroundStop, stopKind(reason, ExitReason.SUB_FORCE_STOP, importance), case)
                assertEquals(ExitKind.BackgroundStop, stopKind(reason, ExitReason.SUB_UNKNOWN, importance), case)
                assertTrue(announced(reason, ExitReason.SUB_FORCE_STOP, importance = importance), case)
            }
            // In front, or no importance recorded: the person's own stop, quiet.
            assertEquals(ExitKind.ForceStop, stopKind(reason, ExitReason.SUB_FORCE_STOP, IN_FRONT))
            assertEquals(ExitKind.ForceStop, stopKind(reason, ExitReason.SUB_FORCE_STOP, ExitReason.IMPORTANCE_UNKNOWN))
            // A swipe from Recents, whatever the importance: quiet.
            assertEquals(ExitKind.ForceStop, stopKind(reason, ExitReason.SUB_REMOVE_TASK, CACHED))
            assertFalse(announced(reason, ExitReason.SUB_REMOVE_TASK, importance = CACHED))
            // Right after an update: silent.
            assertEquals(ExitKind.Update, stopKind(reason, ExitReason.SUB_PACKAGE_UPDATE, CACHED))
            assertFalse(announced(reason, ExitReason.SUB_PACKAGE_UPDATE, importance = CACHED))
        }
        assertTrue(ExitKind.BackgroundStop.offersKeepAlive, "Keep it running leads to the maker's steps")
        assertFalse(ExitKind.BackgroundStop.closedBySystem)
    }

    @Test
    fun `EXIT-NOTICE-002 an update and a normal exit are never announced`() {
        assertFalse(announced(ExitReason.PACKAGE_UPDATED))
        assertFalse(announced(ExitReason.PACKAGE_STATE_CHANGE))
        assertFalse(announced(ExitReason.USER_REQUESTED, ExitReason.SUB_PACKAGE_UPDATE))
        assertFalse(announced(ExitReason.OTHER, ExitReason.SUB_PACKAGE_UPDATE))
        assertFalse(announced(ExitReason.EXIT_SELF))
        assertFalse(announced(ExitReason.UNKNOWN))
        for (reason in listOf(ExitReason.PACKAGE_UPDATED, ExitReason.EXIT_SELF, ExitReason.UNKNOWN)) {
            assertFalse(bySystem(reason), "reason $reason")
        }
    }

    @Test
    fun `EXIT-NOTICE-003 every system kill is announced`() {
        assertTrue(announced(ExitReason.LOW_MEMORY))
        assertTrue(announced(ExitReason.FREEZER))
        assertTrue(announced(ExitReason.EXCESSIVE_RESOURCE_USAGE))
        assertTrue(announced(ExitReason.SIGNALED))
        assertTrue(announced(ExitReason.OTHER))
        assertTrue(announced(ExitReason.DEPENDENCY_DIED))
        assertTrue(announced(ExitReason.PERMISSION_CHANGE))
        assertTrue(announced(ExitReason.OTHER, ExitReason.SUB_MEMORY_PRESSURE))
        assertTrue(announced(ExitReason.SIGNALED, ExitReason.SUB_LARGE_CACHED))
        assertTrue(announced(ExitReason.OTHER, description = "MemoryLimiter:AnonSwap"))
        val kills =
            listOf(
                ExitReason.LOW_MEMORY, ExitReason.FREEZER, ExitReason.EXCESSIVE_RESOURCE_USAGE, ExitReason.SIGNALED,
                ExitReason.OTHER,
            )
        for (reason in kills) {
            assertTrue(bySystem(reason), "reason $reason")
        }
    }

    @Test
    fun `EXIT-NOTICE-004 a crash is announced but is not a system kill`() {
        val crashes =
            listOf(ExitReason.CRASH, ExitReason.CRASH_NATIVE, ExitReason.ANR, ExitReason.INITIALIZATION_FAILURE)
        for (reason in crashes) {
            assertTrue(announced(reason), "reason $reason")
            assertFalse(bySystem(reason), "reason $reason")
        }
    }

    @Test
    fun `EXIT-NOTICE-005 the sub-reason is read from Android's record text`() {
        val text =
            "ApplicationExitInfo(timestamp=11/9/26 10:02 PM pid=4242 realUid=10190 packageUid=10190 " +
                "definingUid=10190 user=0 process=io.github.eslamasabry.opencode_mobile " +
                "reason=10 (USER REQUESTED) subreason=21 (FORCE STOP) status=0 importance=100 pss=0.00 rss=0.00 " +
                "description=stop io.github.eslamasabry.opencode_mobile due to from pid 9 state=empty trace=null"
        assertEquals(ExitReason.SUB_FORCE_STOP, ExitReason.subReasonIn(text))
        assertEquals(ExitReason.SUB_UNKNOWN, ExitReason.subReasonIn("reason=3 (LOW MEMORY) subreason=-1 (UNKNOWN)"))
        assertEquals(ExitReason.SUB_UNKNOWN, ExitReason.subReasonIn("reason=3 (LOW MEMORY)"))
        assertEquals(ExitReason.SUB_UNKNOWN, ExitReason.subReasonIn(null))
    }

    private companion object {
        const val OWNER_AT = 1_790_380_793_000L

        // ActivityManager.RunningAppProcessInfo importances.
        const val IN_FRONT = 100
        const val FOREGROUND_SERVICE = 125
        const val VISIBLE = 200
        const val CACHED = 400
    }
}
