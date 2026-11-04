package io.github.eslamasabry.oc.feature.diagnostics

import io.github.eslamasabry.oc.engine.diagnostics.crashreport.CrashReportResult
import io.github.eslamasabry.oc.engine.diagnostics.crashreport.DiagnosticsError
import io.github.eslamasabry.oc.engine.diagnostics.device.DeviceExitHistory
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitHistoryError
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitKind
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptExits
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptNotice
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptPresenter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Everything this phone kept, over a fake host: the page-level halves of the crash report and exit history rows. */
@OptIn(ExperimentalCoroutinesApi::class)
class KeptPresenterTest {
    private fun TestScope.presenter(host: FakeKeptHost): KeptPresenter =
        KeptPresenter(host, backgroundScope).also {
            it.start {}
            runCurrent()
        }

    @Test
    fun `B-SEC-042 the page shows crash reports off by default and the switch opts in`() = runTest {
        val host = FakeKeptHost()
        val p = presenter(host)
        assertTrue(p.state.value.crashAvailable)
        assertFalse(p.state.value.crashOn)
        p.onCrashCapture(true)
        runCurrent()
        assertEquals(listOf(true), host.captureCalls)
        assertTrue(p.state.value.crashOn)
    }

    @Test
    fun `B-SEC-042 a refused consent change shows one fixed line and keeps the switch`() = runTest {
        val host = FakeKeptHost().apply { captureOk = false }
        val p = presenter(host)
        p.onCrashCapture(true)
        runCurrent()
        assertFalse(p.state.value.crashOn)
        assertEquals(KeptNotice.CrashUpdateFailed, p.state.value.notice)
    }

    @Test
    fun `B-SEC-042 an unopened crash store leaves the switch unavailable`() = runTest {
        val host = FakeKeptHost().apply { crash.value = null }
        val p = presenter(host)
        assertFalse(p.state.value.crashAvailable)
        assertFalse(p.state.value.crashOn)
    }

    @Test
    fun `B-SEC-050 Share hands the share sheet exactly the previewed text`() = runTest {
        val host = FakeKeptHost().apply { crash.value = crashState(enabled = true, records = 1) }
        val p = presenter(host)
        assertEquals(1, p.state.value.crashRows.size)
        p.onPreviewCrashReport()
        runCurrent()
        assertEquals("report-1", p.state.value.preview?.text)
        assertTrue(host.shared.isEmpty(), "previewing shares nothing")
        p.onShareCrashReport()
        runCurrent()
        assertEquals(listOf("report-1"), host.shared)
    }

    @Test
    fun `B-SEC-051 a stale preview is dropped and must be previewed again`() = runTest {
        val host = FakeKeptHost().apply {
            crash.value = crashState(enabled = true, records = 1)
            shareResult = { FakeKeptHost.stale }
        }
        val p = presenter(host)
        p.onPreviewCrashReport()
        runCurrent()
        p.onShareCrashReport()
        runCurrent()
        assertNull(p.state.value.preview)
        assertEquals(KeptNotice.Stale, p.state.value.notice)
        p.onShareCrashReport() // no preview: nothing to authorize
        runCurrent()
        assertTrue(host.shared.isEmpty())
    }

    @Test
    fun `B-SEC-053 a failed preview shows a fixed line and no report text`() = runTest {
        val host = FakeKeptHost().apply { nextPreview = CrashReportResult(error = DiagnosticsError.Unavailable) }
        val p = presenter(host)
        p.onPreviewCrashReport()
        runCurrent()
        assertNull(p.state.value.preview)
        assertEquals(KeptNotice.DetailsUnavailable, p.state.value.notice)
        host.nextPreview = CrashReportResult(error = DiagnosticsError.CaptureDisabled)
        p.onPreviewCrashReport()
        runCurrent()
        assertEquals(KeptNotice.CaptureOff, p.state.value.notice)
    }

    @Test
    fun `B-SEC-054 a second Share tap while one runs does not open the sheet again`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val host = FakeKeptHost().apply {
            crash.value = crashState(enabled = true, records = 1)
            shareGate = gate
        }
        val p = presenter(host)
        p.onPreviewCrashReport()
        runCurrent()
        p.onShareCrashReport()
        runCurrent()
        assertTrue(p.state.value.sharing)
        p.onShareCrashReport()
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, host.shared.size)
        assertFalse(p.state.value.sharing)
    }

    @Test
    fun `B-SEC-059 exit history keeps app exits and helper deaths apart and is read again on retry`() = runTest {
        val host = FakeKeptHost().apply { history = FakeKeptHost.exits() }
        val p = presenter(host)
        val ready = p.state.value.exits as KeptExits.Ready
        assertEquals(listOf(ExitKind.Crash, ExitKind.Update), ready.rows.map { it.kind })
        assertEquals(1, ready.helperDeaths)
        p.onRetryExits()
        runCurrent()
        assertEquals(2, host.historyReads)
    }

    @Test
    fun `B-SEC-059 unsupported and failed history read as fixed states`() = runTest {
        val host = FakeKeptHost().apply { history = DeviceExitHistory(supported = false) }
        val p = presenter(host)
        assertEquals(KeptExits.Unsupported, p.state.value.exits)
        host.history = DeviceExitHistory(supported = false, error = ExitHistoryError.Unavailable)
        p.onRetryExits()
        runCurrent()
        assertEquals(KeptExits.Failed, p.state.value.exits)
    }

    @Test
    fun `B-STATE-072 Clear asks first, then erases the kept errors`() = runTest {
        val host = FakeKeptHost().apply { errorsKept.value = 3 }
        val p = presenter(host)
        p.onClearErrors()
        assertTrue(p.state.value.confirmClear)
        assertEquals(0, host.clears)
        p.onConfirmClear()
        runCurrent()
        assertEquals(1, host.clears)
        assertEquals(0, p.state.value.errorsKept)
    }

    @Test
    fun `B-STATE-080 a failed clear is reported, not shown as success`() = runTest {
        val host = FakeKeptHost().apply {
            errorsKept.value = 2
            clearOk = false
        }
        val p = presenter(host)
        p.onClearErrors()
        p.onConfirmClear()
        runCurrent()
        assertEquals(2, p.state.value.errorsKept)
        assertEquals(KeptNotice.ClearFailed, p.state.value.notice)
    }
}
