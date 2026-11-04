package io.github.eslamasabry.oc.engine.diagnostics.report

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitRecord
import io.github.eslamasabry.oc.engine.diagnostics.perf.PerfSpan
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.time.Clock
import kotlin.time.Instant

/** What a persisted problem event is (Flutter `ProblemEventKind`; [wire] is the stored name). */
public enum class ProblemEventKind(public val wire: String) {
    Error("error"),
    Timing("timing"),
    AndroidExit("androidExit"),
    Thermal("thermal"),
    ;

    public companion object {
        public fun ofWire(value: String): ProblemEventKind? = entries.firstOrNull { it.wire == value }
    }
}

/** One redacted, immutable event; events are ordered by capture, not wall time. */
public data class ProblemEvent(
    val kind: ProblemEventKind,
    val at: Instant,
    val source: String,
    val message: String,
    val stack: String = "",
) {
    public fun toJson(): JsonObject =
        buildJsonObject {
            put("kind", kind.wire)
            put("time", at.toString())
            put("source", source)
            put("message", message)
            if (stack.isNotEmpty()) put("stack", stack)
        }
}

/** What consumers read. [committed] advances only when a change reached disk (B-STATE-080). */
public data class ReportProblemState(
    val entries: ImmutableList<ProblemEvent>,
    val storageFailed: Boolean,
    val committed: Long,
)

/**
 * App-local problem report kept across crashes (port of Flutter `ReportProblem`; B-STATE-070..080). Never uploaded,
 * logged or notified automatically; "Report a problem" previews it before the person copies or shares it.
 *
 * Every write redacts before truncation and commits through [ReportFiles] (pending file, then an atomic rename), so a
 * record call that returned survives process death without a flush. An interrupted replacement leaves the previous
 * snapshot. Each file is at most [maxBytes] UTF-8 bytes; the oldest timing goes first when room is needed (a failure
 * outlives the timings after it), and an event too large for the budget alone is dropped.
 *
 * Failures are values: a write or delete that did not happen answers `Failure.StorageRefused` (no path, no text), sets
 * [ReportProblemState.storageFailed] and publishes nothing as committed. Thread-safe and non-suspending.
 */
public class ReportProblem private constructor(
    private val files: ReportFiles,
    private val redactor: Redactor,
    private val clock: Clock,
    public val maxEntries: Int,
    public val maxBytes: Int,
) {
    private val lock = SpinLock()
    private val mutableState = MutableStateFlow(ReportProblemState(persistentListOf(), storageFailed = false, committed = 0))

    public val state: StateFlow<ReportProblemState> = mutableState.asStateFlow()

    /** Read-only, oldest first. */
    public val entries: ImmutableList<ProblemEvent> get() = mutableState.value.entries

    public val storageFailed: Boolean get() = mutableState.value.storageFailed

    public fun recordError(message: String, stack: String? = null, source: String = "app", at: Instant = clock.now()): Outcome<Unit> =
        record(ProblemEvent(ProblemEventKind.Error, at, source, message, stack.orEmpty()))

    public fun recordTiming(span: PerfSpan): Outcome<Unit> = record(timing(span))

    /**
     * Imports already-buffered timings with one durable commit; consumers see the final imported snapshot once.
     * Order, redaction, eviction and byte limits are those of single records.
     */
    public fun recordTimings(spans: Iterable<PerfSpan>): Outcome<Unit> =
        lock.withLock {
            val next = entries.toMutableList()
            var added = false
            for (span in spans) {
                added = true
                next += timing(span)
                if (next.size > maxEntries) evictOne(next)
            }
            if (!added) Outcome.Ok(Unit) else commit(next)
        }

    /** Android's record of why the previous process ended: numbers and the kind only, never the OS description. */
    public fun recordAndroidExit(record: ExitRecord): Outcome<Unit> =
        record(
            ProblemEvent(
                ProblemEventKind.AndroidExit,
                record.at,
                ANDROID_EXIT_SOURCE,
                "${record.kind.name} reason=${record.reason} subReason=${record.subReason} " +
                    "status=${record.status} importance=${record.importance}",
            ),
        )

    /** A thermal status change ([status] is the platform's status name, e.g. `severe`). */
    public fun recordThermal(status: String, headroom: Double? = null, at: Instant = clock.now()): Outcome<Unit> =
        record(
            ProblemEvent(
                ProblemEventKind.Thermal,
                at,
                THERMAL_SOURCE,
                "status=$status headroom=${headroom?.takeIf { it.isFinite() } ?: "unknown"}",
            ),
        )

    /** Deletes the committed and the interrupted snapshot. On failure keeps the in-memory history and says so. */
    public fun clear(): Outcome<Unit> =
        lock.withLock {
            try {
                files.delete(ReportFile.Pending)
                files.delete(ReportFile.Snapshot)
            } catch (_: Exception) {
                return@withLock storageFailure()
            }
            val current = mutableState.value
            mutableState.value = ReportProblemState(persistentListOf(), storageFailed = false, committed = current.committed + 1)
            Outcome.Ok(Unit)
        }

    /** Text for a diagnostic notification boundary; this posts nothing. */
    public fun notificationText(text: String): String = safeText(text, NOTIFICATION_LIMIT)

    /** Text for a diagnostic log boundary; this prints nothing. */
    public fun logText(text: String): String = safeText(text, MESSAGE_LIMIT)

    /** This exact snapshot is what a preview shows before Copy or Share; it has no network destination. */
    public fun reportJson(): JsonObject {
        val list = entries
        return buildJsonObject {
            put("app", "opencode-mobile")
            put("privacy", "redacted-app-local")
            put("entryCount", list.size)
            putJsonArray("entries") { for (event in list) add(redacted(event).toJson()) }
        }
    }

    public fun reportText(): String = PRETTY.encodeToString(JsonObject.serializer(), reportJson())

    private fun record(event: ProblemEvent): Outcome<Unit> = lock.withLock { commit(entries + event) }

    private fun timing(span: PerfSpan) = ProblemEvent(ProblemEventKind.Timing, span.wallStart, TIMING_SOURCE, span.toLogLine())

    private fun redacted(event: ProblemEvent) =
        event.copy(
            source = safeText(event.source, SOURCE_LIMIT),
            message = safeText(event.message, MESSAGE_LIMIT),
            stack = safeText(event.stack, STACK_LIMIT),
        )

    private fun safeText(text: String, limit: Int): String {
        val safe = redactor.opaqueTokens(redactor.redact(text))
        if (safe.length <= limit) return safe
        // Never leave half of a surrogate pair at the cut.
        val cut = if (safe[limit - 1].isHighSurrogate()) limit - 1 else limit
        return safe.substring(0, cut)
    }

    private fun encode(events: List<ProblemEvent>): ByteArray =
        JSON.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("version", VERSION)
                putJsonArray("entries") { for (event in events) add(event.toJson()) }
            },
        ).encodeToByteArray()

    /** Caller holds [lock]. */
    private fun commit(candidates: List<ProblemEvent>): Outcome<Unit> {
        val next = candidates.map(::redacted).toMutableList()
        while (next.size > maxEntries) evictOne(next)
        var bytes = encode(next)
        while (bytes.size > maxBytes && next.isNotEmpty()) {
            evictOne(next)
            bytes = encode(next)
        }
        try {
            files.writePending(bytes)
            files.commitPending()
        } catch (_: Exception) {
            return storageFailure()
        }
        val current = mutableState.value
        mutableState.value = ReportProblemState(next.toImmutableList(), storageFailed = false, committed = current.committed + 1)
        return Outcome.Ok(Unit)
    }

    private fun storageFailure(): Outcome<Unit> {
        mutableState.value = mutableState.value.copy(storageFailed = true)
        return Outcome.Failed(Failure.StorageRefused())
    }

    /** Caller holds [lock]. Never replays an uncommitted write; drops what is oversized, corrupt or incompatible. */
    private fun restore(): Outcome<Unit> {
        try {
            files.delete(ReportFile.Pending)
            val size = files.size(ReportFile.Snapshot) ?: return Outcome.Ok(Unit)
            if (size > maxBytes) {
                files.delete(ReportFile.Snapshot)
                return Outcome.Ok(Unit)
            }
            val restored = decode(files.read(ReportFile.Snapshot).decodeToString())
            if (restored == null) {
                files.delete(ReportFile.Snapshot)
                return Outcome.Ok(Unit)
            }
            // Today's redaction and limits are applied again before anything restored is shown.
            return commit(restored)
        } catch (_: Exception) {
            return Outcome.Failed(Failure.StorageRefused())
        }
    }

    public companion object {
        public const val MAX_ENTRIES: Int = 128
        public const val MAX_BYTES: Int = 262_144
        public const val MIN_BYTES: Int = 1_024
        public const val SOURCE_LIMIT: Int = 128
        public const val MESSAGE_LIMIT: Int = 2_048
        public const val STACK_LIMIT: Int = 8_192
        public const val NOTIFICATION_LIMIT: Int = 300

        /** The source of Android's exit record, in the report and in the diagnostics ring. */
        public const val ANDROID_EXIT_SOURCE: String = "android.exit"
        public const val THERMAL_SOURCE: String = "android.thermal"
        public const val TIMING_SOURCE: String = "OCTRACE"
        private const val VERSION = 1
        private val JSON = Json
        private val PRETTY = Json { prettyPrint = true }

        /**
         * Opens the report in [files]: the pending file is removed, an unsupported, corrupt or oversized snapshot is
         * discarded. `Failure.StorageRefused` when the folder cannot be read or written. Invalid limits are a bug.
         */
        public fun open(
            files: ReportFiles,
            redactor: Redactor,
            clock: Clock,
            maxEntries: Int = MAX_ENTRIES,
            maxBytes: Int = MAX_BYTES,
        ): Outcome<ReportProblem> {
            require(maxEntries >= 1) { "maxEntries must be at least 1" }
            require(maxBytes >= MIN_BYTES) { "maxBytes must be at least $MIN_BYTES" }
            val report = ReportProblem(files, redactor, clock, maxEntries, maxBytes)
            return when (val restored = report.lock.withLock { report.restore() }) {
                is Outcome.Ok -> Outcome.Ok(report)
                is Outcome.Failed -> restored
            }
        }

        /** Drops the oldest timing; an error, exit or thermal event goes only when no timing is left. */
        internal fun evictOne(events: MutableList<ProblemEvent>) {
            val timing = events.indexOfFirst { it.kind == ProblemEventKind.Timing }
            events.removeAt(if (timing < 0) 0 else timing)
        }

        /** Null when the snapshot is not version 1 with a list of valid events. */
        internal fun decode(text: String): List<ProblemEvent>? =
            try {
                val root = JSON.parseToJsonElement(text) as? JsonObject
                val version = (root?.get("version") as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
                val list = root?.get("entries") as? JsonArray
                if (version != VERSION || list == null) null else list.map { decodeEvent(it) ?: return null }
            } catch (_: Exception) {
                null
            }

        private fun decodeEvent(value: Any?): ProblemEvent? {
            val obj = value as? JsonObject ?: return null
            fun string(key: String): String? = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val kind = string("kind")?.let(ProblemEventKind::ofWire) ?: return null
            val time = string("time") ?: return null
            val source = string("source") ?: return null
            val message = string("message") ?: return null
            if (obj.containsKey("stack") && string("stack") == null) return null
            return ProblemEvent(kind, Instant.parse(time), source, message, string("stack").orEmpty())
        }
    }
}
