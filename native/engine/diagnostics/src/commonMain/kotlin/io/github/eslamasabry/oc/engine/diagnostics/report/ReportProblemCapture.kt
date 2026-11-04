package io.github.eslamasabry.oc.engine.diagnostics.report

import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import io.github.eslamasabry.oc.engine.diagnostics.perf.PerfSpan
import io.github.eslamasabry.oc.engine.diagnostics.perf.PerfTrace
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.time.Instant

/** A platform subscription to thermal changes; closing the capture detaches it too. */
public fun interface ProblemThermalReadings {
    public fun subscribe(onReading: (status: String, headroom: Double?) -> Unit): () -> Unit
}

/**
 * Connects the process diagnostics (the handled-error ring and the trace) to a durable
 * [ReportProblem] (port of `ReportProblemCapture`; B-SEC-148, B-STATE-070..073). Create one after
 * opening the report and keep it for the app's life; [close] before letting go of its sources.
 *
 * Errors and finished timings buffered before attachment are captured once (only the report's
 * capacity of timings). A repeated error is kept again; a long span that finishes after newer ones
 * is still kept. A finished timing reaches disk in the same call that finished it. Clearing
 * [Diagnostics] (Settings › Clear) also erases the restored persisted evidence, even when the
 * in-memory ring is empty, and never replays old timings. With [typedAndroidExits] the diagnostics
 * copy of Android's exit record (source [ReportProblem.ANDROID_EXIT_SOURCE]) is skipped because the
 * caller records the typed exit itself.
 *
 * Storage problems never throw into the app being diagnosed: [ReportProblem.storageFailed] says so
 * instead.
 */
public class ReportProblemCapture(
    private val report: ReportProblem,
    private val diagnostics: Diagnostics,
    trace: PerfTrace,
    public val typedAndroidExits: Boolean = false,
    thermalReadings: ProblemThermalReadings? = null,
) {
    private val lock = SpinLock()
    private val seenErrors = mutableMapOf<Long, Pair<Instant, Int>>()
    private val closed = AtomicBoolean(false)
    private val detachRecorded: () -> Unit
    private val detachCleared: () -> Unit
    private val detachTimings: () -> Unit
    private var detachThermal: () -> Unit = {}
    private var lastThermal: String? = null

    init {
        val buffered = trace.spans()
        val first = (buffered.size - report.maxEntries).coerceAtLeast(0)
        detachRecorded = diagnostics.onRecorded(::captureErrors)
        detachCleared = diagnostics.onCleared(::onCleared)
        detachTimings = trace.onRecorded(::onTiming)
        captureErrors()
        report.recordTimings(buffered.subList(first, buffered.size))
        detachThermal = thermalReadings?.subscribe(::onThermal) ?: {}
    }

    public val isClosed: Boolean
        get() = closed.load()

    private fun onCleared() {
        lock.withLock {
            if (closed.load()) return@withLock
            seenErrors.clear()
            report.clear()
        }
    }

    private fun captureErrors() {
        lock.withLock {
            if (closed.load()) return@withLock
            val list = diagnostics.errors.value
            val current = list.mapTo(HashSet()) { it.id }
            seenErrors.keys.retainAll(current)
            for (entry in list) {
                val revision = entry.at to entry.occurrences
                if (seenErrors[entry.id] == revision) continue
                seenErrors[entry.id] = revision
                if (typedAndroidExits && entry.source == ReportProblem.ANDROID_EXIT_SOURCE) continue
                report.recordError(
                    entry.message,
                    entry.stack.ifEmpty { null },
                    entry.source,
                    entry.at,
                )
            }
        }
    }

    private fun onTiming(span: PerfSpan) {
        lock.withLock {
            if (!closed.load()) report.recordTiming(span)
        }
    }

    private fun onThermal(status: String, headroom: Double?) {
        lock.withLock {
            if (closed.load() || status == "unknown" || status == lastThermal) return@withLock
            report.recordThermal(status, headroom)
            lastThermal = status
        }
    }

    /**
     * Detaches the ring and the trace. Safe to repeat; keeps the evidence and does not close the
     * sources.
     */
    public fun close() {
        val closing = lock.withLock { closed.compareAndSet(expectedValue = false, newValue = true) }
        if (!closing) return
        detachRecorded()
        detachCleared()
        detachTimings()
        detachThermal()
    }
}
