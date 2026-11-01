package io.github.eslamasabry.oc.engine.diagnostics.exit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExitClassifierTest {
    private fun kind(reason: Int, sub: Int = -1, description: String? = null) =
        ExitRecord(reason = reason, timestampMs = OWNER_AT, subReason = sub, importance = 125, description = description).kind

    @Test
    fun `B-ANDR-004 the owner's FORCE STOP by the system is a force stop`() {
        // dumpsys exit-info on the owner's RedMagic: reason=10 (USER REQUESTED) subreason=21 (FORCE STOP) importance=125.
        val record =
            ExitRecord(
                reason = 10, timestampMs = OWNER_AT, subReason = 21, importance = 125,
                description = "stop io.github.eslamasabry.opencode_mobile due to from pid 2285",
            )
        assertEquals(ExitReason.USER_REQUESTED, record.reason)
        assertEquals(ExitReason.SUB_FORCE_STOP, record.subReason)
        assertEquals(ExitKind.ForceStop, record.kind)
        assertTrue(record.kind.notable)
    }

    @Test
    fun `B-ANDR-004 every Android reason lands on one kind`() {
        assertEquals(ExitKind.ForceStop, kind(10))
        assertEquals(ExitKind.ForceStop, kind(11))
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
        for (kind in listOf(ExitKind.ForceStop, ExitKind.LowMemory, ExitKind.MemoryLimit, ExitKind.Killed)) {
            assertTrue(kind.offersKeepAlive, kind.name)
        }
    }

    private companion object {
        const val OWNER_AT = 1_790_380_793_000L
    }
}
