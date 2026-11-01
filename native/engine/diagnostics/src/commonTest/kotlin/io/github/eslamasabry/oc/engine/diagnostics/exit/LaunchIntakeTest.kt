package io.github.eslamasabry.oc.engine.diagnostics.exit

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.diagnostics.CrashCategory
import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import io.github.eslamasabry.oc.engine.diagnostics.FreezeKind
import io.github.eslamasabry.oc.engine.diagnostics.RecordingSignals
import io.github.eslamasabry.oc.engine.diagnostics.crash.CRASH_AT
import io.github.eslamasabry.oc.engine.diagnostics.crash.UNSAFE
import io.github.eslamasabry.oc.engine.diagnostics.crash.rawCrash
import io.github.eslamasabry.oc.engine.diagnostics.perf.PerfTrace
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TestTimeSource

class LaunchIntakeTest {
    private val redactor = Redactor()
    private val clock = TestClock()
    private val diagnostics = Diagnostics(redactor, clock)
    private val trace = PerfTrace(redactor, clock, TestTimeSource())
    private val signals = RecordingSignals()
    private val ledger = CrashSignalLedger.InMemory()

    private fun intake(source: LifecycleSource) = LaunchIntake(PolicyExitHistory(source), diagnostics, trace, signals, ledger)

    private fun source(raw: RawLaunchReport) = FakeLifecycle().apply { launch = { Outcome.Ok(raw) } }

    @Test
    fun `B-ANDR-036 startup records one crash with its bounded stack`() =
        runTest {
            val lifecycle = source(RawLaunchReport(lastCrash = rawCrash()))
            val intake = intake(lifecycle)
            val first = intake.runOnce()
            val second = intake.runOnce()
            assertEquals(first, second)
            assertEquals(1, lifecycle.launchReads)
            val entry = diagnostics.errors.value.single()
            assertEquals("crash.last", entry.source)
            assertEquals(CRASH_AT, entry.at)
            assertTrue("PhoneAgentHost.start (PhoneAgentHost.kt:42)" in entry.stack)
            assertTrue("Thread.run (Thread.java:101)" in entry.stack)
            val mark = trace.spans().single { it.name == "crash.last" }
            assertEquals("IllegalStateException", mark.attrs["class"])
            assertTrue("PhoneAgentHost.start" in mark.attrs["frame0"]!!)
            assertTrue("PhoneAgentHost.kt:42" in trace.reportText())
            assertEquals("none", trace.spans().single { it.name == "app.lastExit" }.attrs["kind"])
        }

    @Test
    fun `B-ANDR-036 a crash is reported to the port as a category once across starts`() =
        runTest {
            val raw =
                RawLaunchReport(
                    exit = ExitRecord(reason = ExitReason.CRASH, timestampMs = CRASH_AT.toEpochMilliseconds() + 40, description = UNSAFE),
                    lastCrash = rawCrash() + ("message" to UNSAFE),
                )
            intake(source(raw)).runOnce()
            assertEquals(listOf(CrashCategory.Kotlin to true), signals.crashes)
            // Next process: the JVM record is read again and the exit is gone; nothing new to say.
            intake(source(RawLaunchReport(lastCrash = rawCrash()))).runOnce()
            assertEquals(1, signals.crashes.size)
            assertTrue(signals.freezes.isEmpty())
        }

    @Test
    fun `B-ANDR-036 native crashes, out of memory and ANR exits become categories, never text`() =
        runTest {
            val at = CRASH_AT.toEpochMilliseconds()
            intake(source(RawLaunchReport(exit = ExitRecord(reason = ExitReason.CRASH_NATIVE, timestampMs = at, description = UNSAFE)))).runOnce()
            val oom = rawCrash() + mapOf("timestamp" to at + 1_000, "exceptionClass" to "java.lang.OutOfMemoryError")
            intake(source(RawLaunchReport(lastCrash = oom))).runOnce()
            intake(
                source(RawLaunchReport(exit = ExitRecord(reason = ExitReason.ANR, timestampMs = at + 2_000, importance = 100))),
            ).runOnce()
            intake(
                source(RawLaunchReport(exit = ExitRecord(reason = ExitReason.ANR, timestampMs = at + 3_000, importance = 400))),
            ).runOnce()
            intake(source(RawLaunchReport(exit = ExitRecord(reason = ExitReason.LOW_MEMORY, timestampMs = at + 4_000)))).runOnce()
            assertEquals(listOf(CrashCategory.Native to true, CrashCategory.OutOfMemory to true), signals.crashes)
            assertEquals(
                listOf(
                    Triple(FreezeKind.NotResponding, LaunchIntake.ANR_MS, true),
                    Triple(FreezeKind.NotResponding, LaunchIntake.ANR_MS, false),
                ),
                signals.freezes,
            )
            assertTrue(signals.errors.isEmpty())
            assertEquals(at + 4_000, ledger.lastSignaledMs())
            assertFalse("fake-secret-code" in diagnostics.reportText())
        }
}
