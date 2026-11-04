package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashDiagnosticsState
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashReportError
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashReportPreview
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashReportResult
import kotlinx.coroutines.flow.StateFlow

public interface CrashSettings {
    public val state: StateFlow<CrashDiagnosticsState?>

    public suspend fun setEnabled(on: Boolean): Boolean

    public suspend fun clear(): Boolean

    public suspend fun history(): DeviceExitHistory

    public suspend fun preview(): CrashReportResult

    public suspend fun share(preview: CrashReportPreview): CrashReportError?
}

public data class CrashUi(
    val history: DeviceExitHistory? = null,
    val available: Boolean = false,
    val enabled: Boolean = false,
    val count: Int = 0,
    val preview: CrashReportPreview? = null,
    val error: CrashReportError? = null,
)

public data class DeviceExit(val at: String, val code: Int)

public data class DeviceExitHistory(
    val app: List<DeviceExit> = emptyList(),
    val helpers: List<DeviceExit> = emptyList(),
    val unavailable: Boolean = false,
)
