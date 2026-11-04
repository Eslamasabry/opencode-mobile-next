package io.github.eslamasabry.oc.engine.diagnostics.report

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitRecord
import io.github.eslamasabry.oc.engine.diagnostics.perf.PerfTrace
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.Clock
import kotlin.time.Instant

/** The open persisted report with its capture (Flutter `ReportProblemStartup` instance). */
public class ReportProblemSession internal constructor(
    public val report: ReportProblem,
    public val capture: ReportProblemCapture,
) {
    private val closed = AtomicBoolean(false)
    private val lastThermal = AtomicReference<String?>(null)

    /**
     * Keeps Android's record of why the previous process ended, once: a typed exit already kept (same time) is not
     * added again, also after a restart (B-STATE-077). The diagnostics copy is skipped by the capture.
     */
    public fun recordAndroidExit(record: ExitRecord) {
        if (closed.load() || !record.hasTime) return
        val at = record.at
        if (report.entries.any { it.kind == ProblemEventKind.AndroidExit && it.at == at }) return
        report.recordAndroidExit(record)
    }

    /** Keeps a thermal status change; repeats of the same status and `unknown` add nothing. */
    public fun recordThermal(status: String, headroom: Double? = null, at: Instant? = null) {
        if (closed.load() || status == UNKNOWN_THERMAL) return
        if (lastThermal.exchange(status) == status) return
        if (at == null) report.recordThermal(status, headroom) else report.recordThermal(status, headroom, at)
    }

    /** Detaches capture; keeps the persisted history. Safe to repeat. */
    public fun close() {
        if (!closed.compareAndSet(expectedValue = false, newValue = true)) return
        capture.close()
    }

    internal companion object {
        const val UNKNOWN_THERMAL = "unknown"
    }
}

/**
 * The app's one persisted problem report, opened once per process (port of `ReportProblemStartup`; B-STATE-075/077).
 * In-memory capture ([Diagnostics], [PerfTrace]) exists from the first line, so errors and timings from before [start]
 * are imported on attach. [start] does blocking file work: call it off the main thread, once the first frame is up.
 *
 * Later [start] calls answer the first call's result. When the folder cannot be opened, [current] stays null and the
 * ring gets one fixed line ([OPEN_FAILED_MESSAGE], source [SOURCE]), never the file system's error.
 */
public class ReportProblemStartup(
    private val openFiles: () -> ReportFiles,
    private val diagnostics: Diagnostics,
    private val trace: PerfTrace,
    private val redactor: Redactor,
    private val clock: Clock,
    private val maxEntries: Int = ReportProblem.MAX_ENTRIES,
    private val maxBytes: Int = ReportProblem.MAX_BYTES,
) {
    private class Started(val session: ReportProblemSession?)

    private val lock = SpinLock()
    private var started: Started? = null

    /** The open report, or null before [start] finished or after it failed. */
    public val current: ReportProblemSession? get() = lock.withLock { started?.session }

    public fun start(): ReportProblemSession? =
        lock.withLock {
            started?.let { return@withLock it.session }
            val session = open()
            started = Started(session)
            session
        }

    private fun open(): ReportProblemSession? {
        val report =
            try {
                ReportProblem.open(openFiles(), redactor, clock, maxEntries, maxBytes)
            } catch (_: Exception) {
                null
            }
        if (report !is Outcome.Ok) {
            diagnostics.record(SOURCE, OPEN_FAILED_MESSAGE)
            return null
        }
        val capture = ReportProblemCapture(report.value, diagnostics, trace, typedAndroidExits = true)
        return ReportProblemSession(report.value, capture)
    }

    public companion object {
        public const val OPEN_FAILED_MESSAGE: String = "The saved problem report is unavailable on this device."
        public const val SOURCE: String = "report-problem"
    }
}
