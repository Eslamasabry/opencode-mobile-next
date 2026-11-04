package io.github.eslamasabry.oc.engine.diagnostics.device

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.engine.diagnostics.crashreport.CrashReportBuilder
import io.github.eslamasabry.oc.engine.diagnostics.crashreport.CrashReportPreview
import io.github.eslamasabry.oc.engine.diagnostics.crashreport.CrashReportResult
import io.github.eslamasabry.oc.engine.diagnostics.crashreport.CrashShareResult
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitEntry
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitHistory
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitHistoryError
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Instant

/** Why the built-in Linux saw a helper service end (port of Dart `BuiltinServiceExitReason`, the part that matters). */
public enum class HelperExitReason { MemoryOrPhantomKill, Other }

/** One built-in Linux service as the runtime reports it; [key] may name a private profile and is never shown. */
public data class HelperServiceRecord(
    val key: String,
    val running: Boolean,
    val lastExitCode: Int?,
    val lastExitAtMs: Long?,
    /** Null when the runtime did not say: not a confirmed unexpected stop. */
    val lastStopRequested: Boolean?,
    val exitReason: HelperExitReason = HelperExitReason.Other,
)

/** The built-in Linux runtime's service table (Android: the phone engine's performance read). May fail or throw. */
public fun interface HelperServiceSource {
    public suspend fun services(): Outcome<List<HelperServiceRecord>>
}

/** An agent helper that stopped by itself: a time, its exit code and whether a resource kill is likely. */
public data class AgentHelperExit(val at: Instant, val exitCode: Int?, val possibleResourceKill: Boolean) {
    /** Fixed words; never the service key, which identifies a private profile. */
    public val description: String
        get() =
            if (possibleResourceKill) "Agent helper stopped (possible memory or process limit)"
            else "Agent helper stopped unexpectedly"
}

/** Android's app exits beside the agent helper deaths (port of Dart `AppExitHistory`), both newest first. */
public data class DeviceExitHistory(
    val supported: Boolean,
    val entries: ImmutableList<ExitEntry> = persistentListOf(),
    val helperExits: ImmutableList<AgentHelperExit> = persistentListOf(),
    val error: ExitHistoryError? = null,
)

/**
 * Port of `DeviceDiagnosticsGateway` (B-SEC-059): the device-diagnostics page's one door. Exit history is read fresh
 * every time (reads never consume the launch-recovery report), helper deaths come from the built-in Linux and a
 * missing or failing runtime never hides Android's history. Crash-report preview and share delegate to [reports].
 */
public class DeviceDiagnostics(
    private val exits: ExitHistory,
    private val helpers: HelperServiceSource?,
    private val reports: CrashReportBuilder,
) {
    public val shareSupported: Boolean get() = reports.shareSupported

    public suspend fun exitHistory(limit: Int = ExitHistory.DEFAULT_LIMIT): DeviceExitHistory {
        val history = exits.history(limit)
        val base = DeviceExitHistory(history.supported, history.entries, error = history.error)
        val source = helpers ?: return base
        val services =
            when (val read = attempt(::failed) { source.services() }) {
                is Outcome.Ok -> (read.value as? Outcome.Ok)?.value ?: return base
                is Outcome.Failed -> return base
            }
        return base.copy(helperExits = helperExits(services, limit))
    }

    public suspend fun previewCrashReport(): CrashReportResult = reports.preview()

    public suspend fun shareCrashReport(preview: CrashReportPreview): CrashShareResult = reports.share(preview)

    private fun failed(@Suppress("UNUSED_PARAMETER") error: Throwable): Failure = Failure.Device()

    public companion object {
        /** Port of `withAgentHelperExits`: the latest observed unexpected exit per helper, newest first. */
        public fun helperExits(services: List<HelperServiceRecord>, limit: Int): ImmutableList<AgentHelperExit> =
            services
                .filter { it.key.startsWith(HELPER_PREFIX) && it.lastExitAtMs != null && it.lastStopRequested == false }
                .map {
                    AgentHelperExit(
                        at = Instant.fromEpochMilliseconds(it.lastExitAtMs!!),
                        exitCode = it.lastExitCode,
                        possibleResourceKill = it.exitReason == HelperExitReason.MemoryOrPhantomKill,
                    )
                }
                .sortedByDescending { it.at }
                .take(limit.coerceIn(0, 100))
                .toImmutableList()

        public const val HELPER_PREFIX: String = "agent-host."
    }
}
