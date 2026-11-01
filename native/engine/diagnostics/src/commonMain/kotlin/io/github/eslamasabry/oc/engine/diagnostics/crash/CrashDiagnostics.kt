package io.github.eslamasabry.oc.engine.diagnostics.crash

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.StorageProblem
import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitReason
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitRecord
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.time.Clock
import kotlin.time.Instant

/** What the consent UI and the problem report read; never paths or raw errors. */
public data class CrashDiagnosticsState(
    val available: Boolean,
    val enabled: Boolean,
    val storageFailed: Boolean,
    val consentEpoch: Int,
    val revision: Int,
    /** Newest first; empty unless enabled, open and storage healthy. */
    val records: ImmutableList<CrashRecord>,
)

/**
 * Explicit opt-in, app-private crash evidence (port of `CrashDiagnosticsController`; plan 16 §1.10, B-SEC-042/043/045).
 * Only fixed sources, fixed categories and times are kept; no throwable text or stack has a slot. Off by default:
 * without consent every evidence file is erased at open. Commands run on one serial lane.
 */
public class CrashDiagnostics private constructor(
    private val store: CrashStore,
    private val diagnostics: Diagnostics,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val lane = Mutex()
    private var records: List<CrashRecord> = emptyList()
    private var enabledSince = 0L
    private var storageFailed = false
    private var consentEpoch = 0
    private var revision = 0
    private val closed = AtomicBoolean(false)
    private val clearing = AtomicBoolean(false)
    private var detach: () -> Unit = {}

    private val mutableState = MutableStateFlow(snapshot())
    public val state: StateFlow<CrashDiagnosticsState> = mutableState.asStateFlow()

    public val enabled: Boolean get() = enabledSince > 0
    public val savedCount: Int get() = records.size
    internal val isClosed: Boolean get() = closed.load()

    /** Records oldest first, as stored (for the startup replay). */
    internal fun storedRecords(): List<CrashRecord> = records

    /** Returns false when storage cannot honour the choice. A successful disable removes all evidence at once. */
    public suspend fun setEnabled(value: Boolean): Boolean =
        lane.withLock {
            if (closed.load()) return@withLock false
            if (value && enabled && !storageFailed) return@withLock true
            replaceConsent(value)
        }

    /** Erases evidence but keeps consent; the new consent epoch stops an old ANR from coming back. */
    public suspend fun clear(): Boolean =
        lane.withLock {
            if (closed.load() || clearing.load()) return@withLock false
            replaceConsent(enabled)
        }

    /** Safe in crash paths: only the error's TYPE is read, never its message or stack. */
    public suspend fun capture(error: Throwable, source: CrashSource = CrashSource.Platform) {
        lane.withLock {
            if (!enabled || closed.load()) return@withLock
            save(source, CrashRecordCategory.of(error), clock.now(), intoRing = true)
        }
    }

    public fun close() {
        if (closed.exchange(true)) return
        detach()
        publish()
    }

    /**
     * ExitImport (plan 16 §1.10): the JVM handler's last crash, native crashes and ANRs since consent become
     * category-only records, once. Nothing goes to the diagnostics ring here; the startup replays after readiness.
     */
    internal suspend fun importAtStart(platformCrash: NativeCrashRecord?, exits: List<ExitRecord>) {
        lane.withLock {
            if (!enabled || closed.load()) return@withLock
            if (platformCrash != null) {
                importSince(CrashSource.Platform, CrashRecordCategory.ofClassName(platformCrash.exceptionClass), platformCrash.timestamp)
            }
            for (exit in exits.sortedBy { it.timestampMs }) {
                when (exit.reason) {
                    ExitReason.CRASH_NATIVE -> importSince(CrashSource.Native, CrashRecordCategory.NativeApplicationError, exit.at)
                    ExitReason.ANR -> importSince(CrashSource.Anr, CrashRecordCategory.NotResponding, exit.at)
                    else -> Unit
                }
            }
        }
    }

    private suspend fun importSince(source: CrashSource, category: CrashRecordCategory, at: Instant) {
        val millis = at.toEpochMilliseconds()
        if (millis <= 0 || millis < enabledSince || millis > clock.now().toEpochMilliseconds()) return
        save(source, category, at, intoRing = false)
    }

    // ---- internals (called with the lane held)

    private class StorageBroken : Exception()

    private fun <T> need(outcome: Outcome<T>): T =
        when (outcome) {
            is Outcome.Ok -> outcome.value
            is Outcome.Failed -> throw StorageBroken()
        }

    private suspend fun openLocked() {
        try {
            val consent = store.read(CrashFile.Consent, MAX_CONSENT_BYTES)
            enabledSince =
                when {
                    consent is Outcome.Ok -> parseConsent(consent.value)
                    consent is Outcome.Failed && consent.failure.isOversize() -> 0L
                    else -> throw StorageBroken()
                }
            if (enabled) {
                restore()
            } else {
                need(store.delete(CrashFile.Consent))
                eraseEvidence()
            }
        } catch (_: StorageBroken) {
            enabledSince = 0L
            storageFailed = true
        }
    }

    private fun parseConsent(text: String?): Long {
        val value = text?.trim()?.toLongOrNull() ?: return 0L
        return if (value > 0 && value <= clock.now().toEpochMilliseconds()) value else 0L
    }

    private suspend fun restore() {
        need(store.delete(CrashFile.Pending))
        val read = store.read(CrashFile.Records, MAX_BYTES)
        val text =
            when {
                read is Outcome.Ok -> read.value ?: return
                read is Outcome.Failed && read.failure.isOversize() -> {
                    need(store.delete(CrashFile.Records))
                    return
                }
                else -> throw StorageBroken()
            }
        val parsed = parseRecords(text)
        if (parsed == null) {
            records = emptyList()
            need(store.delete(CrashFile.Records))
        } else {
            records = parsed
        }
    }

    private fun parseRecords(text: String): List<CrashRecord>? {
        val root =
            try {
                Json.parseToJsonElement(text)
            } catch (_: IllegalArgumentException) {
                return null // SerializationException is a subclass
            }
        if (root !is JsonArray || root.size > MAX_ENTRIES) return null
        val now = clock.now().toEpochMilliseconds()
        return root.map { entry ->
            if (entry !is JsonObject || entry.size != 3) return null
            val source = entry.string("source")?.let(CrashSource::ofWire) ?: return null
            val category = entry.string("category")?.let(CrashRecordCategory::ofWire) ?: return null
            val time = (entry["time"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull ?: return null
            if (time < enabledSince || time > now) return null
            CrashRecord(source, category, Instant.fromEpochMilliseconds(time))
        }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private suspend fun replaceConsent(value: Boolean): Boolean {
        consentEpoch++
        clearing.store(true)
        try {
            enabledSince = 0L
            need(store.delete(CrashFile.Consent))
            eraseEvidence()
            diagnostics.clear()
            if (value) {
                val now = clock.now().toEpochMilliseconds()
                need(store.write(CrashFile.Consent, now.toString()))
                enabledSince = now
            }
            storageFailed = false
            publish()
            return true
        } catch (_: StorageBroken) {
            enabledSince = 0L
            storageFailed = true
            publish()
            return false
        } finally {
            clearing.store(false)
        }
    }

    private suspend fun eraseEvidence() {
        records = emptyList()
        revision++
        for (file in listOf(CrashFile.Records, CrashFile.Pending, CrashFile.NativeLastCrash)) need(store.delete(file))
    }

    private suspend fun save(source: CrashSource, category: CrashRecordCategory, at: Instant, intoRing: Boolean) {
        val millis = at.toEpochMilliseconds()
        if (records.any { it.source == source && it.at.toEpochMilliseconds() == millis }) return
        val next = (records + CrashRecord(source, category, Instant.fromEpochMilliseconds(millis))).toMutableList()
        while (next.size > MAX_ENTRIES || encode(next).encodeToByteArray().size > MAX_BYTES) next.removeAt(0)
        when (store.write(CrashFile.Records, encode(next))) {
            is Outcome.Failed -> {
                storageFailed = true
                publish()
                return
            }
            is Outcome.Ok -> Unit
        }
        records = next
        revision++
        storageFailed = false
        if (intoRing) diagnostics.record("crash.${source.wire}", category.wire, at = at)
        publish()
    }

    private fun encode(list: List<CrashRecord>): String =
        Json.encodeToString(
            JsonElement.serializer(),
            buildJsonArray {
                for (record in list) {
                    addJsonObject {
                        put("source", record.source.wire)
                        put("category", record.category.wire)
                        put("time", record.at.toEpochMilliseconds())
                    }
                }
            },
        )

    private fun snapshot(): CrashDiagnosticsState {
        val open = !closed.load()
        val visible =
            if (enabled && open && !storageFailed) {
                val now = clock.now()
                records.filter { it.at.toEpochMilliseconds() >= enabledSince && it.at <= now }.asReversed()
            } else {
                emptyList()
            }
        return CrashDiagnosticsState(
            available = open,
            enabled = enabled,
            storageFailed = storageFailed,
            consentEpoch = consentEpoch,
            revision = revision,
            records = visible.toImmutableList(),
        )
    }

    private fun publish() {
        mutableState.value = snapshot()
    }

    public companion object {
        public const val MAX_ENTRIES: Int = 20
        public const val MAX_BYTES: Int = 8_192
        public const val MAX_CONSENT_BYTES: Int = 32

        /**
         * Opens the store (consent, restore or erase). Never throws: a broken store answers a closed-to-capture
         * instance with `storageFailed`. A diagnostics [Diagnostics.clear] from elsewhere erases the evidence too.
         */
        public suspend fun open(store: CrashStore, diagnostics: Diagnostics, clock: Clock, scope: CoroutineScope): CrashDiagnostics {
            val controller = CrashDiagnostics(store, diagnostics, clock, scope)
            controller.lane.withLock { controller.openLocked() }
            controller.detach =
                diagnostics.onCleared {
                    // Synchronous check: a clear this controller started itself must not loop back.
                    if (!controller.clearing.load() && !controller.closed.load()) scope.launch { controller.clear() }
                }
            controller.publish()
            return controller
        }

        private fun Failure.isOversize(): Boolean = this is Failure.Storage && problem == StorageProblem.InvalidValue
    }
}
