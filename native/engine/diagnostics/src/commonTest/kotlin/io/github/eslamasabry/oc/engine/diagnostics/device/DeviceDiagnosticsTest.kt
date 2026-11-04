package io.github.eslamasabry.oc.engine.diagnostics.device

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashDiagnostics
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashSource
import io.github.eslamasabry.oc.engine.diagnostics.crashreport.CrashReportBuilder
import io.github.eslamasabry.oc.engine.diagnostics.crashreport.CrashShareSheet
import io.github.eslamasabry.oc.engine.diagnostics.crashreport.DiagnosticsError
import io.github.eslamasabry.oc.engine.diagnostics.crashreport.MemoryCrashStore
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitKind
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitReason
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitRecord
import io.github.eslamasabry.oc.engine.diagnostics.exit.LifecycleSource
import io.github.eslamasabry.oc.engine.diagnostics.exit.PolicyExitHistory
import io.github.eslamasabry.oc.engine.diagnostics.exit.RawLaunchReport
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceDiagnosticsTest {
    private val clock = TestClock()
    private val diagnostics = Diagnostics(Redactor(), clock)
    private val shared = mutableListOf<String>()
    private val calls = mutableListOf<String>()
    private var failPerformance = false

    private val lifecycle =
        object : LifecycleSource {
            override val exitHistorySupported = true
            override val mainProcessName = "app"

            override suspend fun retainedExits(): Outcome<List<ExitRecord>> {
                calls += "exitHistory"
                return Outcome.Ok(
                    listOf(
                        ExitRecord(
                            reason = ExitReason.ANR, timestampMs = 1_700_000_000_000, importance = 100, processName = "app",
                            description = "private-unregistered-provider-value",
                        ),
                    ),
                )
            }

            override suspend fun launchReport(): Outcome<RawLaunchReport> {
                calls += "launchReport"
                return Outcome.Ok(RawLaunchReport())
            }
        }

    private val helpers =
        HelperServiceSource {
            if (failPerformance) error("private native details")
            Outcome.Ok(
                listOf(
                    HelperServiceRecord(
                        key = "agent-host.private-profile", running = false, lastExitCode = 137,
                        lastExitAtMs = 1_700_000_001_000, lastStopRequested = false,
                        exitReason = HelperExitReason.MemoryOrPhantomKill,
                    ),
                    // A requested stop and a non-helper service are not helper deaths.
                    HelperServiceRecord("agent-host.other", false, 0, 1_700_000_002_000, lastStopRequested = true),
                    HelperServiceRecord("server", false, 1, 1_700_000_003_000, lastStopRequested = false),
                ),
            )
        }

    private suspend fun TestScope.gateway(): Pair<DeviceDiagnostics, CrashDiagnostics> {
        val captures = CrashDiagnostics.open(MemoryCrashStore(), diagnostics, clock, backgroundScope)
        val reports = CrashReportBuilder({ captures }, CrashShareSheet { shared += it; true })
        return DeviceDiagnostics(PolicyExitHistory(lifecycle), helpers, reports) to captures
    }

    @Test
    fun `B-SEC-059 device diagnostics separates helper deaths from Android app exits`() =
        runTest {
            val (gateway, _) = gateway()
            val history = gateway.exitHistory()
            assertEquals(1, history.entries.size)
            assertEquals(ExitKind.Crash, history.entries.single().kind)
            assertEquals(1, history.helperExits.size)
            assertEquals(137, history.helperExits.single().exitCode)
            assertTrue("Agent helper" in history.helperExits.single().description)
            assertFalse("private-profile" in history.helperExits.single().description)
            assertFalse("private-profile" in history.toString())
            failPerformance = true
            val fallback = gateway.exitHistory()
            assertEquals(1, fallback.entries.size)
            assertTrue(fallback.helperExits.isEmpty())
        }

    @Test
    fun `B-SEC-059 history is repeatable and never consumes launch recovery`() =
        runTest {
            val (gateway, _) = gateway()
            repeat(2) {
                val history = gateway.exitHistory(limit = 1)
                assertNull(history.error)
                assertEquals(ExitKind.Crash, history.entries.single().kind)
                assertTrue(history.entries.single().at.toString().endsWith("Z"))
                assertFalse("private-unregistered-provider-value" in history.toString())
            }
            assertEquals(listOf("exitHistory", "exitHistory"), calls)
        }

    @Test
    fun `B-SEC-059 the gateway preview stays off until consent and shares exact safe text`() =
        runTest {
            val (gateway, captures) = gateway()
            assertEquals(DiagnosticsError.CaptureDisabled, gateway.previewCrashReport().error)
            assertTrue(shared.isEmpty())
            assertFalse(captures.enabled)
            assertTrue(captures.setEnabled(true))
            captures.capture(IllegalStateException("private-unregistered-provider-value"), CrashSource.Flutter)
            val report = gateway.previewCrashReport()
            assertNull(report.error)
            val preview = assertNotNull(report.preview)
            assertEquals(1, preview.recordCount)
            assertFalse("private-unregistered-provider-value" in preview.text)
            assertTrue(shared.isEmpty())
            assertTrue(gateway.shareSupported)
            assertTrue(gateway.shareCrashReport(preview).opened)
            assertEquals(listOf(preview.text), shared)
            // Clearing captured evidence invalidates a gateway preview.
            val next = assertNotNull(gateway.previewCrashReport().preview)
            assertTrue(captures.clear())
            val stale = gateway.shareCrashReport(next)
            assertFalse(stale.opened)
            assertEquals(DiagnosticsError.StalePreview, stale.error)
            assertEquals(1, shared.size)
        }

    @Test
    fun `B-SEC-059 a failed helper read answers the app history alone`() =
        runTest {
            val gateway =
                DeviceDiagnostics(
                    PolicyExitHistory(lifecycle),
                    { Outcome.Failed(Failure.Device()) },
                    CrashReportBuilder({ null }, null),
                )
            val history = gateway.exitHistory()
            assertEquals(1, history.entries.size)
            assertTrue(history.helperExits.isEmpty())
        }
}
