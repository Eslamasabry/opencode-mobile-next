package io.github.eslamasabry.oc.feature.diagnostics

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashDiagnosticsState
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashRecord
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashRecordCategory
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashSource
import io.github.eslamasabry.oc.engine.diagnostics.crashreport.CrashReportPreview
import io.github.eslamasabry.oc.engine.diagnostics.crashreport.CrashReportResult
import io.github.eslamasabry.oc.engine.diagnostics.crashreport.CrashShareResult
import io.github.eslamasabry.oc.engine.diagnostics.crashreport.DiagnosticsError
import io.github.eslamasabry.oc.engine.diagnostics.device.AgentHelperExit
import io.github.eslamasabry.oc.engine.diagnostics.device.DeviceExitHistory
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitEntry
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitKind
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptDiagnosticsHost
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Instant

internal val AT = Instant.fromEpochMilliseconds(1_767_225_600_000)

internal fun crashState(enabled: Boolean, records: Int = 0, available: Boolean = true) =
    CrashDiagnosticsState(
        available = available,
        enabled = enabled,
        storageFailed = false,
        consentEpoch = 1,
        revision = records,
        records = List(records) { CrashRecord(CrashSource.Platform, CrashRecordCategory.InvalidState, AT) }.toImmutableList(),
    )

internal class FakeKeptHost(
    override val crash: MutableStateFlow<CrashDiagnosticsState?> = MutableStateFlow(crashState(enabled = false)),
    override val errorsKept: MutableStateFlow<Int> = MutableStateFlow(0),
) : KeptDiagnosticsHost {
    var history = DeviceExitHistory(supported = true)
    var historyReads = 0
    var captureOk = true
    val captureCalls = mutableListOf<Boolean>()
    var nextPreview: CrashReportResult = CrashReportResult(preview = CrashReportPreview("report-1", 8, 1))
    val shared = mutableListOf<String>()
    var shareGate: CompletableDeferred<Unit>? = null
    var shareResult: (CrashReportPreview) -> CrashShareResult = { CrashShareResult(opened = true) }
    var clears = 0
    var clearOk = true
    var deletes = 0

    override suspend fun setCrashCapture(on: Boolean): Boolean {
        captureCalls += on
        if (captureOk) crash.value = crashState(enabled = on, records = if (on) crash.value?.records?.size ?: 0 else 0)
        return captureOk
    }

    override suspend fun deleteCrashReports(): Boolean {
        deletes++
        crash.value = crashState(enabled = true)
        return true
    }

    override suspend fun exitHistory(): DeviceExitHistory {
        historyReads++
        return history
    }

    override suspend fun previewCrashReport(): CrashReportResult = nextPreview

    override suspend fun shareCrashReport(preview: CrashReportPreview): CrashShareResult {
        shareGate?.await()
        val result = shareResult(preview)
        if (result.opened) shared += preview.text
        return result
    }

    override suspend fun clearErrors(): Outcome<Unit> {
        clears++
        if (!clearOk) return Outcome.Failed(Failure.StorageRefused())
        errorsKept.value = 0
        return Outcome.Ok(Unit)
    }

    override fun timeLabel(at: Instant): String = "Today 12:00"

    companion object {
        fun exits() =
            DeviceExitHistory(
                supported = true,
                entries = persistentListOf(ExitEntry(4, 0, 100, AT, ExitKind.Crash), ExitEntry(10, 0, 100, AT, ExitKind.Update)),
                helperExits = persistentListOf(AgentHelperExit(AT, 137, possibleResourceKill = true)),
            )

        val stale = CrashShareResult(opened = false, error = DiagnosticsError.StalePreview)
    }
}
