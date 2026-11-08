package io.github.eslamasabry.oc.engine.diagnostics.crash

import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitReason
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitRecord
import io.github.eslamasabry.oc.engine.diagnostics.exit.LaunchReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

private const val NOW_MS = 1_793_528_100_000L // 2026-11-01T10:15:00Z

private class FixedClock(val ms: Long = NOW_MS) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(ms)
}

private class MemoryMarks(var last: Long = 0L, var on: Boolean = true, var saves: Boolean = true) : CrashOfferMarks {
    override fun lastOfferedMs(): Long = last

    override suspend fun markOffered(atMs: Long): Boolean {
        if (saves) last = atMs
        return saves
    }

    override fun enabled(): Boolean = on
}

private fun exitAt(ms: Long, reason: Int = ExitReason.CRASH) = ExitRecord(reason = reason, timestampMs = ms)

private fun jvmAt(ms: Long) =
    NativeCrashRecord.from(
        mapOf(
            "timestamp" to ms,
            "exceptionClass" to "java.lang.IllegalStateException",
            "message" to "a chat text the user typed",
            "frames" to listOf("io.github.eslamasabry.oc.feature.chat.ChatPresenter.send(ChatPresenter.kt:42)"),
        )
    )!!

class CrashOfferTest {
    @Test
    fun `E02-S91-001 a fresh crash is offered once with its safe type and stack, and marked before it shows`() = runTest {
        val marks = MemoryMarks()
        val offer = CrashOffer(marks, FixedClock())
        val at = NOW_MS - 1.hours.inWholeMilliseconds
        offer.consider(LaunchReport(exit = exitAt(at), lastCrash = jvmAt(at - 30_000)))
        val facts = assertNotNull(offer.pending.value)
        assertEquals(at, marks.last, "seen before shown")
        assertEquals("java.lang.IllegalStateException", facts.exceptionType)
        assertEquals(NativeCrashRecord.REDACTED, facts.exceptionMessage, "a free message never reaches the report")
        assertEquals("crash", facts.exitReason)
        assertTrue(facts.stack.single().contains("ChatPresenter"))

        // Answered or dismissed: it never returns, not even in a new process.
        assertNotNull(offer.take())
        assertNull(offer.pending.value)
        val next = CrashOffer(marks, FixedClock())
        next.consider(LaunchReport(exit = exitAt(at), lastCrash = jvmAt(at - 30_000)))
        assertNull(next.pending.value)
    }

    @Test
    fun `E02-S91-002 nothing after a normal exit, an update, a force stop or an old crash`() = runTest {
        for (report in
            listOf(
                LaunchReport(exit = exitAt(NOW_MS - 60_000, ExitReason.EXIT_SELF)),
                LaunchReport(exit = exitAt(NOW_MS - 60_000, ExitReason.PACKAGE_UPDATED)),
                LaunchReport(exit = exitAt(NOW_MS - 60_000, ExitReason.USER_STOPPED)),
                LaunchReport(exit = exitAt(NOW_MS - 8.days.inWholeMilliseconds)),
                LaunchReport(),
            )) {
            val offer = CrashOffer(MemoryMarks(), FixedClock())
            offer.consider(report)
            assertNull(offer.pending.value, report.toString())
        }
    }

    @Test
    fun `E02-S91-003 turned off in Settings, the crash is marked seen and nothing shows`() = runTest {
        val marks = MemoryMarks(on = false)
        val offer = CrashOffer(marks, FixedClock())
        offer.consider(LaunchReport(exit = exitAt(NOW_MS - 60_000)))
        assertNull(offer.pending.value)
        assertEquals(NOW_MS - 60_000, marks.last, "turning it on later does not raise this old crash")
    }

    @Test
    fun `E02-S91-004 a mark that cannot be saved means no offer, so it never shows on every start`() = runTest {
        val offer = CrashOffer(MemoryMarks(saves = false), FixedClock())
        offer.consider(LaunchReport(exit = exitAt(NOW_MS - 60_000)))
        assertNull(offer.pending.value)
    }

    @Test
    fun `E02-S91-005 an unrelated JVM record lends nothing, a lone one is offered with its type`() = runTest {
        val apart = CrashOffer(MemoryMarks(), FixedClock())
        apart.consider(LaunchReport(exit = exitAt(NOW_MS - 60_000, ExitReason.ANR), lastCrash = jvmAt(NOW_MS - 3.hours.inWholeMilliseconds)))
        val facts = assertNotNull(apart.pending.value)
        assertNull(facts.exceptionType)
        assertEquals("not responding (ANR)", facts.exitReason)

        val lone = CrashOffer(MemoryMarks(), FixedClock())
        lone.consider(LaunchReport(lastCrash = jvmAt(NOW_MS - 10.minutes.inWholeMilliseconds)))
        assertEquals("java.lang.IllegalStateException", lone.pending.value?.exceptionType)
    }

    @Test
    fun `E02-S91-006 the details hold fixed labels, the device and a bounded stack`() {
        val facts =
            CrashOfferFacts(
                at = Instant.fromEpochMilliseconds(NOW_MS),
                exceptionMessage = "Invalid state",
                stack = List(15) { "at frame $it" },
                exitReason = "crash",
                androidRelease = "16",
                sdk = 36,
                manufacturer = "Google",
                model = "Pixel 9",
            )
        val text = facts.details()
        assertTrue(text.startsWith("Crashed: 2026-11-01 10:15:00 UTC\nAndroid exit: crash\nMessage: Invalid state"), text)
        assertTrue("Android: 16 (API 36)" in text && "Device: Google Pixel 9" in text)
        assertTrue("Stack (first 12 of 15 lines):" in text && "… 3 more lines left out" in text)
        assertFalse("at frame 12" in text)
        assertTrue("Stack: not captured" in CrashOfferFacts(at = Instant.fromEpochMilliseconds(NOW_MS)).details())
    }
}
