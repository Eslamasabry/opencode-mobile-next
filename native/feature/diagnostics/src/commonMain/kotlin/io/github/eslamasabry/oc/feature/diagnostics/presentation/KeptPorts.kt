package io.github.eslamasabry.oc.feature.diagnostics.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashDiagnosticsState
import io.github.eslamasabry.oc.engine.diagnostics.crashreport.CrashReportPreview
import io.github.eslamasabry.oc.engine.diagnostics.crashreport.CrashReportResult
import io.github.eslamasabry.oc.engine.diagnostics.crashreport.CrashShareResult
import io.github.eslamasabry.oc.engine.diagnostics.device.DeviceExitHistory
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

/**
 * What "Everything this phone kept" reads and does (port of `AppDiagnosticsKeptScreen`'s controllers). :androidApp
 * binds it over the real engines: CrashStartup's controller, DeviceDiagnostics, the persisted ReportProblem and the
 * Diagnostics ring. Nothing here sends anything; Share opens the share sheet with the previewed text only.
 */
public interface KeptDiagnosticsHost {
    /** Null while the crash store is opening or when it could not open. */
    public val crash: StateFlow<CrashDiagnosticsState?>

    /** The errors kept on this phone (persisted report, else this run's ring). */
    public val errorsKept: StateFlow<Int>

    /** Opt in or out of saved crash reports. False when storage could not honour the choice. */
    public suspend fun setCrashCapture(on: Boolean): Boolean

    /** Deletes saved crash reports; consent stays. */
    public suspend fun deleteCrashReports(): Boolean

    public suspend fun exitHistory(): DeviceExitHistory

    public suspend fun previewCrashReport(): CrashReportResult

    public suspend fun shareCrashReport(preview: CrashReportPreview): CrashShareResult

    /** Clears the kept errors (memory, the saved report and any pending write) and the restored crash evidence. */
    public suspend fun clearErrors(): Outcome<Unit>

    /** "Today 14:05" style words come from the app's locale; the feature never formats a date itself. */
    public fun timeLabel(at: Instant): String
}
