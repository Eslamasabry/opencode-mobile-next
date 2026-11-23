package io.github.eslamasabry.oc.engine.diagnostics.report

import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import io.github.eslamasabry.oc.engine.diagnostics.perf.PerfSpan
import io.github.eslamasabry.oc.engine.diagnostics.perf.PerfTrace
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
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
 * capacity of timings). A repeated error is kept as a count (B-332): each change of a ring entry adds
 * the occurrences it gained, with the entry's first time, so repeats folded before attachment or
 * coalesced in one update keep their count; a long span that finishes after newer ones
 * is still kept. A finished timing reaches disk in the same call that finished it when no timing
 * was written in the last five seconds; timings that finish closer together are kept in memory
 * and written together in one report write, by the first timing five seconds or more after the
 * last write, before the next error, or on [close]. Clearing [Diagnostics] (Settings › Clear)
 * also erases the restored persisted evidence, even when the in-memory ring is empty, and never
 * replays old timings. With [typedAndroidExits] the diagnostics copy of Android's exit record
 * (source [ReportProblem.ANDROID_EXIT_SOURCE]) is skipped because the caller records the typed
 * exit itself.
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
    private val historicalTimingIds = mutableSetOf<Long>()
    private val closed = AtomicBoolean(false)
    private val pendingTimings = ArrayDeque<PerfSpan>()
    private var lastFlush: Instant? = null
    private val detachRecorded: () -> Unit
    private val detachCleared: () -> Unit
    private val detachTimings: () -> Unit
    private var detachThermal: () -> Unit = {}
    private var lastThermal: String? = null

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
        detachThermal = thermalReadings?.subscribe(::onThermal) ?: {}
    }

    public val isClosed: Boolean
        get() = closed.load()

    private fun onCleared() {
        lock.withLock {
            if (closed.load()) return@withLock
            seenErrors.clear()
            pendingTimings.clear()
            lastFlush = null
            report.clear()
        }
    }

    private fun captureErrors() {
        lock.withLock {
            if (closed.load()) return@withLock
            flushTimings()
            val list = diagnostics.errors.value
            val current = list.mapTo(HashSet()) { it.id }
            seenErrors.keys.retainAll(current)
            for (entry in list) {
                val revision = entry.at to entry.occurrences
                val seen = seenErrors[entry.id]
                if (seen == revision) continue
                seenErrors[entry.id] = revision
                if (typedAndroidExits && entry.source == ReportProblem.ANDROID_EXIT_SOURCE) continue
                // An entry seen for the first time brings all its repeats; a known one, what it gained.
                val added =
                    if (seen == null) entry.occurrences
                    else (entry.occurrences - seen.second).coerceAtLeast(1)
                report.recordError(
                    entry.message,
                    entry.stack.ifEmpty { null },
                    entry.source,
                    entry.at,
                    count = added,
                    firstAt = entry.firstAt,
                )
            }
        }
    }

    private fun onTiming(span: PerfSpan) {
        if (closed.load()) return
        lock.withLock {
            if (closed.load() || historicalTimingIds.remove(span.id)) return@withLock
            val now = report.clock.now()
            pendingTimings.addLast(span)
            while (pendingTimings.size > report.maxEntries) pendingTimings.removeFirst()
            val last = lastFlush
            if (last == null || now - last >= FLUSH_EVERY) flushTimings(now)
        }
    }

    /** Caller holds [lock]. Writes every pending timing in one report write; a refused write drops them too. */
    private fun flushTimings(now: Instant = report.clock.now()) {
        if (pendingTimings.isEmpty()) return
        val spans = pendingTimings.toList()
        pendingTimings.clear()
        report.recordTimings(spans)
        lastFlush = now
    }

    private fun onThermal(status: String, headroom: Double?) {
        lock.withLock {
            if (closed.load() || status == "unknown" || status == lastThermal) return@withLock
            report.recordThermal(status, headroom)
            lastThermal = status
        }
    }

    /**
     * Detaches the ring and the trace, after writing a timing that is still waiting. Safe to
     * repeat; keeps the evidence and does not close the sources.
     */
    public fun close() {
        val closing = lock.withLock {
            if (!closed.compareAndSet(expectedValue = false, newValue = true)) return@withLock false
            flushTimings()
            true
        }
        if (!closing) return
        detachRecorded()
        detachCleared()
        detachTimings()
        detachThermal()
    }

    private companion object {
        private val FLUSH_EVERY: Duration = 5.seconds
    }
}
