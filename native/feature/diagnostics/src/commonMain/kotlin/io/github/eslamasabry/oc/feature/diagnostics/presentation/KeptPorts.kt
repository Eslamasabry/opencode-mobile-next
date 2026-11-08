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
 * What the retired "Everything this phone kept" page read (port of `AppDiagnosticsKeptScreen`'s controllers). R1
 * (2026-11-09) removed the page and its door from Report a problem; :androidApp still binds this host
 * (AppKeptDiagnosticsHost in DiagnosticsAttentionWiring.kt), which goes with it once native/prompt-editor-crash, which
 * edits that file, has merged. Nothing here sends anything.
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

    /** The redacted text of the errors kept on this phone, for Copy. Empty when there are none. */
    public fun errorLogText(): String = ""

    /** The kept errors, newest first, redacted, with their times worded. Empty when the host lists none. */
    public fun errorEntries(): List<KeptErrorRow> = emptyList()

    /** Where time went in this run, grouped by step, slowest first. Times are already worded. */
    public fun performanceSteps(): List<KeptPerfRow> = emptyList()

    /** The plain-text timing report: names and timings only. */
    public fun performanceReport(): String = ""

    public fun clearPerformance() {}

    /** "Today 14:05" style words come from the app's locale; the feature never formats a date itself. */
    public fun timeLabel(at: Instant): String
}
