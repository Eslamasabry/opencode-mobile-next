package io.github.eslamasabry.oc.engine.diagnostics.report

import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import io.github.eslamasabry.oc.engine.diagnostics.perf.PerfSpan
import io.github.eslamasabry.oc.engine.diagnostics.perf.PerfTrace
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.time.Instant

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
) {
    private val lock = SpinLock()
    private val seenErrors = mutableMapOf<Long, Pair<Instant, Int>>()
    private val historicalTimingIds = mutableSetOf<Long>()
    private val closed = AtomicBoolean(false)
    private val detachRecorded: () -> Unit
    private val detachCleared: () -> Unit
    private val detachTimings: () -> Unit

    init {
        detachRecorded = diagnostics.onRecorded(::captureErrors)
        detachCleared = diagnostics.onCleared(::onCleared)
        // Subscribe before taking the snapshot: a span finishing during startup cannot fall between
        // the two.
        // A completion already in the snapshot may still be delivering its listener callback, so
        // skip that ID once.
        detachTimings = lock.withLock {
            val detach = trace.onRecorded(::onTiming)
            val buffered = trace.spans().takeLast(report.maxEntries)
            historicalTimingIds.addAll(buffered.map { it.id })
            report.recordTimings(buffered)
            detach
        }
        captureErrors()
    }

    public val isClosed: Boolean
        get() = closed.load()

    private fun onCleared() {
        if (closed.load()) return
        lock.withLock { seenErrors.clear() }
        report.clear()
    }

    private fun captureErrors() {
        if (closed.load()) return
        lock.withLock {
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
        if (closed.load()) return
        lock.withLock {
            if (!historicalTimingIds.remove(span.id)) report.recordTiming(span)
        }
    }

    /**
     * Detaches the ring and the trace. Safe to repeat; keeps the evidence and does not close the
     * sources.
     */
    public fun close() {
        if (!closed.compareAndSet(expectedValue = false, newValue = true)) return
        detachRecorded()
        detachCleared()
        detachTimings()
    }
}
