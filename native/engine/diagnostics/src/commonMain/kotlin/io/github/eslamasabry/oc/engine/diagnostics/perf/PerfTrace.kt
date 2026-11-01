package io.github.eslamasabry.oc.engine.diagnostics.perf

import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ceil
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Instant
import kotlin.time.TimeSource

/** Whether a finished span completed or threw. */
public enum class PerfOutcome { Ok, Error }

/** One finished span, or an instant ([isMark]), in the trace ring. */
public data class PerfSpan(
    val id: Long,
    val name: String,
    /** Monotonic microseconds since the tracer started (≈ process start). */
    val startMicros: Long,
    val durationMicros: Long,
    val wallStart: Instant,
    /** The span that was open around this one when it started. */
    val parent: String? = null,
    val attrs: ImmutableMap<String, String> = emptyMap<String, String>().toImmutableMap(),
    val outcome: PerfOutcome = PerfOutcome.Ok,
    val isMark: Boolean = false,
) {
    val durationMs: Double get() = durationMicros / 1000.0
    val failed: Boolean get() = outcome == PerfOutcome.Error

    /** The device-log line: `OCTRACE 123ms name key=value … parent=name` (the analysis scripts parse it). */
    public fun toLogLine(): String =
        buildString {
            append(PerfTrace.LOG_TAG).append(' ')
            if (isMark) append("mark $name at=${formatMs(startMicros / 1000.0)}") else append("${formatMs(durationMs)} $name")
            for ((key, value) in attrs) append(" $key=${logValue(value)}")
            if (failed) append(" outcome=error")
            if (parent != null) append(" parent=${logValue(parent)}")
        }

    private fun logValue(value: String) = if (' ' in value) "\"${value.replace('"', '\'')}\"" else value
}

/** Integer milliseconds, with one decimal below 10 ms so fast work reads as something other than zero. */
public fun formatMs(ms: Double): String =
    if (ms < 10) {
        val tenths = (ms * 10).roundToLong()
        "${tenths / 10}.${tenths % 10}ms"
    } else {
        "${ms.roundToLong()}ms"
    }

/** Per-name statistics over the spans still in the ring (nearest-rank percentiles). */
public data class PerfStat(
    val name: String,
    val count: Int,
    val errors: Int,
    val p50Ms: Double,
    val p95Ms: Double,
    val maxMs: Double,
    val totalMs: Double,
)

/** A started span. Finishing twice is a no-op; tracing never breaks the work it measures. */
public class PerfSpanHandle internal constructor(
    private val trace: PerfTrace,
    public val id: Long,
    public val name: String,
    public val startMicros: Long,
    public val wallStart: Instant,
    public val parent: PerfSpanHandle?,
    private val logMinMicros: Long,
    attrs: Map<String, String>,
) {
    private val attrs = AtomicReference(attrs)
    private val done = AtomicBoolean(false)

    public val isOpen: Boolean get() = !done.load()

    /** Adds or replaces an attribute before the span finishes. */
    public fun set(key: String, value: Any?) {
        if (done.load()) return
        while (true) {
            val current = attrs.load()
            if (attrs.compareAndSet(current, trace.withAttr(current, key, value))) return
        }
    }

    public fun finish(error: Boolean = false, attrs: Map<String, Any?> = emptyMap()) {
        if (done.exchange(true)) return
        var all = this.attrs.load()
        for ((key, value) in attrs) all = trace.withAttr(all, key, value)
        trace.add(
            PerfSpan(
                id = id,
                name = name,
                startMicros = startMicros,
                durationMicros = (trace.nowMicros - startMicros).coerceAtLeast(0),
                wallStart = wallStart,
                parent = parent?.name,
                attrs = all.toImmutableMap(),
                outcome = if (error) PerfOutcome.Error else PerfOutcome.Ok,
            ),
            logMinMicros,
        )
    }
}

/**
 * A tiny, always-on tracer (port of Dart `PerfTrace`; B-BUILD-025, B-SEC-117): where did the time go, top to bottom.
 *
 * [span] nests through the coroutine context, so work started inside "location.select" records it as its parent;
 * work that outlives a finished parent is not attributed to it. Everything lands in a [capacity] ring; each finished
 * span also goes to [logSink] as one `OCTRACE …` line (the app wires it to AppLog). Names and attribute values are
 * scrubbed on the way in: secret-named keys and token-like values never reach the ring or the log.
 */
public class PerfTrace(
    private val redactor: Redactor,
    private val clock: Clock,
    timeSource: TimeSource = TimeSource.Monotonic,
    public val capacity: Int = CAPACITY,
    private val logSink: ((String) -> Unit)? = null,
) {
    private val started = timeSource.markNow()
    private val ring = AtomicReference<PersistentList<PerfSpan>>(persistentListOf())
    private val onceMarks = AtomicReference<PersistentSet<String>>(persistentSetOf())
    private val nextId = AtomicLong(1L)

    /** Monotonic microseconds since this tracer started. */
    public val nowMicros: Long get() = started.elapsedNow().inWholeMicroseconds

    private class SpanElement(val handle: PerfSpanHandle) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<SpanElement>
    }

    /** The innermost span still open around the caller; finished spans are skipped. */
    public suspend fun current(): PerfSpanHandle? = openFrom(currentCoroutineContext()[SpanElement]?.handle)

    private fun openFrom(handle: PerfSpanHandle?): PerfSpanHandle? {
        var open = handle
        while (open != null && !open.isOpen) open = open.parent
        return open
    }

    /** Starts a span to finish by hand (work with no single block around it). Shorter than [logMinMs]: ring only. */
    public fun begin(
        name: String,
        attrs: Map<String, Any?> = emptyMap(),
        logMinMs: Long = 0,
        parent: PerfSpanHandle? = null,
    ): PerfSpanHandle =
        PerfSpanHandle(
            trace = this,
            id = nextId.fetchAndAdd(1L),
            name = safeName(name),
            startMicros = nowMicros,
            wallStart = clock.now(),
            parent = openFrom(parent),
            logMinMicros = logMinMs * 1000,
            attrs = scrubAttrs(attrs),
        )

    /** Times [body]; spans started inside record this one as their parent. Result and error pass through. */
    public suspend fun <T> span(
        name: String,
        attrs: Map<String, Any?> = emptyMap(),
        logMinMs: Long = 0,
        body: suspend () -> T,
    ): T {
        val handle = begin(name, attrs, logMinMs, current())
        val result =
            try {
                withContext(SpanElement(handle)) { body() }
            } catch (e: CancellationException) {
                handle.finish()
                throw e
            } catch (e: Throwable) {
                handle.finish(error = true)
                throw e
            }
        handle.finish()
        return result
    }

    /** The non-suspending [span]; pass [parent] (from [current]) to nest it. */
    public fun <T> measure(
        name: String,
        attrs: Map<String, Any?> = emptyMap(),
        logMinMs: Long = 0,
        parent: PerfSpanHandle? = null,
        body: () -> T,
    ): T {
        val handle = begin(name, attrs, logMinMs, parent)
        val result =
            try {
                body()
            } catch (e: Throwable) {
                handle.finish(error = e !is CancellationException)
                throw e
            }
        handle.finish()
        return result
    }

    /** An instant: "this happened now". Always logged. */
    public fun mark(name: String, attrs: Map<String, Any?> = emptyMap(), parent: PerfSpanHandle? = null) {
        guarded {
            add(
                PerfSpan(
                    id = nextId.fetchAndAdd(1L),
                    name = safeName(name),
                    startMicros = nowMicros,
                    durationMicros = 0,
                    wallStart = clock.now(),
                    parent = openFrom(parent)?.name,
                    attrs = scrubAttrs(attrs).toImmutableMap(),
                    isMark = true,
                ),
                0,
            )
        }
    }

    /** [mark], once per tracer (process): first frame, first connected. */
    public fun markOnce(name: String, attrs: Map<String, Any?> = emptyMap()) {
        while (true) {
            val current = onceMarks.load()
            if (name in current) return
            if (onceMarks.compareAndSet(current, current.adding(name))) break
        }
        mark(name, attrs)
    }

    /** A span that started at [startMicros] (a [nowMicros] reading) and ends now. */
    public fun recordSince(
        name: String,
        startMicros: Long,
        attrs: Map<String, Any?> = emptyMap(),
        error: Boolean = false,
        logMinMs: Long = 0,
        parent: PerfSpanHandle? = null,
    ) {
        guarded {
            val elapsed = (nowMicros - startMicros).coerceAtLeast(0)
            recordMicros(name, elapsed, startMicros, attrs, error, logMinMs, parent)
        }
    }

    /** A span measured elsewhere, ending now. */
    public fun recordDuration(
        name: String,
        duration: Duration,
        attrs: Map<String, Any?> = emptyMap(),
        error: Boolean = false,
        parent: PerfSpanHandle? = null,
    ) {
        guarded {
            val micros = duration.inWholeMicroseconds.coerceAtLeast(0)
            recordMicros(name, micros, nowMicros - micros, attrs, error, 0, parent)
        }
    }

    private fun recordMicros(
        name: String,
        micros: Long,
        startMicros: Long,
        attrs: Map<String, Any?>,
        error: Boolean,
        logMinMs: Long,
        parent: PerfSpanHandle?,
    ) {
        add(
            PerfSpan(
                id = nextId.fetchAndAdd(1L),
                name = safeName(name),
                startMicros = startMicros,
                durationMicros = micros,
                wallStart = clock.now() - micros.microseconds,
                parent = openFrom(parent)?.name,
                attrs = scrubAttrs(attrs).toImmutableMap(),
                outcome = if (error) PerfOutcome.Error else PerfOutcome.Ok,
            ),
            logMinMs * 1000,
        )
    }

    internal fun add(span: PerfSpan, logMinMicros: Long) {
        if (capacity <= 0) return
        guarded {
            val clean = span.copy(name = redactor.redact(span.name), parent = span.parent?.let(redactor::redact))
            while (true) {
                val current = ring.load()
                var next = current.adding(clean)
                // Amortised trim: the ring may hold up to twice the capacity; readers take the newest [capacity].
                if (next.size > capacity * 2) next = next.subList(next.size - capacity, next.size).toPersistentList()
                if (ring.compareAndSet(current, next)) break
            }
            val sink = logSink
            if (sink != null && (clean.isMark || clean.failed || clean.durationMicros >= logMinMicros)) {
                guarded { sink(redactor.redact(clean.toLogLine())) }
            }
        }
    }

    // ---- reading

    /** Every span still in the ring, oldest first. */
    public fun spans(): ImmutableList<PerfSpan> {
        val all = ring.load()
        return (if (all.size > capacity) all.subList(all.size - capacity, all.size) else all).toImmutableList()
    }

    /** The last [count] spans and marks, newest first. */
    public fun recent(count: Int = 50): ImmutableList<PerfSpan> = spans().takeLast(count).asReversed().toImmutableList()

    /** Spans (not marks) grouped by name, slowest p95 first. */
    public fun stats(): ImmutableList<PerfStat> =
        spans().filterNot { it.isMark }.groupBy { it.name }.map { (name, list) -> statFor(name, list) }
            .sortedWith(compareByDescending<PerfStat> { it.p95Ms }.thenByDescending { it.maxMs })
            .toImmutableList()

    private fun statFor(name: String, spans: List<PerfSpan>): PerfStat {
        val durations = spans.map { it.durationMs }.sorted()
        // Nearest-rank: the smallest value with at least p of the samples at or below it.
        fun percentile(p: Double): Double = durations[ceil(p * durations.size).toInt().coerceIn(1, durations.size) - 1]
        return PerfStat(
            name = name,
            count = durations.size,
            errors = spans.count { it.failed },
            p50Ms = percentile(0.5),
            p95Ms = percentile(0.95),
            maxMs = durations.last(),
            totalMs = durations.sum(),
        )
    }

    public fun clear() {
        ring.store(persistentListOf())
    }

    /** Plain text, safe to share: names, durations and scrubbed attributes. */
    public fun reportText(slowest: Int = 30, recentCount: Int = 50): String {
        val all = spans()
        val out =
            buildString {
                appendLine("OpenCode Mobile performance trace")
                appendLine("spans: ${all.size} · clock: ${formatMs(nowMicros / 1000.0)} since start")
                appendLine()
                appendLine("Slowest (by p95): name — count, p50, p95, max, errors")
                for (stat in stats().take(slowest)) {
                    append("  ${stat.name} — ${stat.count}×, p50 ${formatMs(stat.p50Ms)}, p95 ${formatMs(stat.p95Ms)}, ")
                    append("max ${formatMs(stat.maxMs)}")
                    if (stat.errors > 0) append(", ${stat.errors} failed")
                    appendLine()
                }
                appendLine()
                appendLine("Recent (newest first):")
                for (span in recent(recentCount)) appendLine("  ${span.toLogLine().substring(LOG_TAG.length + 1)}")
            }
        return redactor.redact(out)
    }

    // ---- scrubbing (B-SEC-117)

    /** A name as it may be recorded: registered secrets, credential patterns and long opaque tokens removed. */
    private fun safeName(name: String): String = redactor.opaqueTokens(redactor.redact(name))

    private fun scrubAttrs(attrs: Map<String, Any?>): Map<String, String> {
        var out: Map<String, String> = emptyMap()
        for ((key, value) in attrs) out = withAttr(out, key, value)
        return out
    }

    internal fun withAttr(into: Map<String, String>, key: String, value: Any?): Map<String, String> {
        if (value == null) return into
        if (SECRET_KEY.containsMatchIn(key)) return into + (scrub(key) to REDACTED)
        return into + (scrub(key) to scrub(value.toString()))
    }

    /** A value as it may be recorded: one line, bounded, tokens and URL credentials replaced. */
    public fun scrub(value: String, limit: Int = VALUE_LIMIT): String {
        var safe = redactor.redact(value).replace(WHITESPACE, " ").trim()
        safe = safe.replace(URL_USER, "//$REDACTED@")
        safe = safe.replace(TOKEN_LIKE, REDACTED)
        if (safe.length > limit) safe = safe.substring(0, limit - 1) + "…"
        return safe
    }

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
            // Tracing must never break the work it measures.
        }
    }

    public companion object {
        public const val LOG_TAG: String = "OCTRACE"
        public const val CAPACITY: Int = 2_000
        public const val VALUE_LIMIT: Int = 80
        public const val REDACTED: String = "[redacted]"
        private val SECRET_KEY = Regex("auth|token|password|passwd|secret|cookie|api[-_]?key|credential", RegexOption.IGNORE_CASE)
        private val TOKEN_LIKE = Regex("[A-Za-z0-9_+/=.-]{32,}")
        private val WHITESPACE = Regex("\\s+")
        private val URL_USER = Regex("//[^/@\\s]*@")
    }
}
