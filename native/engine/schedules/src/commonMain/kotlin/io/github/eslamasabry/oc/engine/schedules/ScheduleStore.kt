package io.github.eslamasabry.oc.engine.schedules

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.datetime.TimeZone
import kotlin.coroutines.cancellation.CancellationException

/**
 * Atomic read and compare-and-set of one string key (Flutter `ScheduledPromptBackend`). Implementations must compare
 * AND write atomically across every writer, and refuse writes for a profile that no longer exists.
 */
public interface ScheduleBackend {
    public suspend fun read(key: String): String?

    public suspend fun compareAndSet(key: String, expected: String?, value: String?): Boolean
}

/** The app's preference file (the adopted `FlutterSharedPreferences.xml`): the Flutter keys are read in place. */
public class PrefsScheduleBackend(
    private val prefs: KeyValueStore,
    private val lanes: WriterLanes,
) : ScheduleBackend {
    override suspend fun read(key: String): String? = prefs.string(key)

    override suspend fun compareAndSet(key: String, expected: String?, value: String?): Boolean {
        val profile = ScheduleKeys.profileOf(key) ?: return false
        return when (val result = prefs.compareAndSet(lanes.forProfile(ProfileId(profile)), key, expected, value)) {
            is Outcome.Ok -> result.value
            is Outcome.Failed -> throw ScheduleStoreException(ScheduleCode.StorageUnavailable)
        }
    }
}

public object ScheduleKeys {
    public const val STORE_PREFIX: String = "oc.scheduledPrompts."
    public const val CLAIMS_PREFIX: String = "oc.scheduleClaims."
    public const val INDEX_PREFIX: String = "oc.scheduleIndex."

    public fun store(profileId: String): String = STORE_PREFIX + checked(profileId)

    public fun claims(profileId: String): String = CLAIMS_PREFIX + checked(profileId)

    public fun index(profileId: String): String = INDEX_PREFIX + checked(profileId)

    internal fun profileOf(key: String): String? =
        listOf(STORE_PREFIX, CLAIMS_PREFIX, INDEX_PREFIX).firstOrNull { key.startsWith(it) }?.let { key.removePrefix(it) }
            ?.takeIf(ScheduleIds::valid)

    private fun checked(profileId: String): String {
        if (!ScheduleIds.valid(profileId)) throw ScheduleStoreException(ScheduleCode.InvalidProfile)
        return profileId
    }
}

/**
 * The fixed codes a schedule action can end with (Flutter `ScheduledPromptStoreException.code` and the runner's
 * error codes). Each has plain words in `:feature:schedules`; persistence failures never disclose prompts.
 */
public enum class ScheduleCode(public val wire: String) {
    InvalidProfile("invalid_profile"),
    ScheduleLimit("schedule_limit"),
    StaleSchedule("stale_schedule"),
    ScheduleMissing("schedule_missing"),
    ScheduleExpired("schedule_expired"),
    InvalidRunTransition("invalid_run_transition"),
    HistoryLimit("history_limit"),
    StorageLimit("storage_limit"),
    StorageBusy("storage_busy"),
    StorageCorrupt("storage_corrupt"),
    StorageUnavailable("storage_unavailable"),
    Unsupported("unsupported"),
    InvalidSchedule("invalid_schedule"),
    ;

    public fun asFailure(): Failure =
        when (this) {
            StorageCorrupt, StorageUnavailable, StorageBusy, StorageLimit -> Failure.StorageRefused()
            Unsupported -> Failure.Unavailable(io.github.eslamasabry.oc.core.common.FeatureId("scheduledPrompts"))
            else -> Failure.Refused(io.github.eslamasabry.oc.core.common.RefusalReason.StaleScope)
        }
}

public class ScheduleStoreException(public val code: ScheduleCode) : Exception(code.wire)

/**
 * Port of lib/scheduling/store.dart: one versioned envelope per profile avoids partial schedule/history/digest
 * writes. History is bounded for presentation and MUST NOT be used to deduplicate sends (claims do that).
 * Every operation throws [ScheduleStoreException] with a fixed code; nothing else escapes.
 */
public class ScheduleStore(
    private val backend: ScheduleBackend,
    private val nowMs: () -> Long,
    private val zone: () -> TimeZone,
) {
    public companion object {
        public const val MAX_SCHEDULES: Int = 100
        public const val MAX_HISTORY: Int = 500
        public const val MAX_BYTES: Int = 4 * 1024 * 1024
        private const val CAS_ATTEMPTS = 20
    }

    public suspend fun load(profileId: String): List<ScheduledPrompt> = read(profileId).schedules

    /** Newest occurrence first; one schedule's when [scheduleId] is set. */
    public suspend fun history(profileId: String, scheduleId: String? = null): List<ScheduledRun> =
        read(profileId).runs.filter { scheduleId == null || it.scheduleId == scheduleId }.sortedByDescending { it.scheduledAtMs }

    public suspend fun digest(profileId: String): ScheduleDigest = read(profileId).digest

    /** A new schedule, or an edit whose revision is exactly one more than the stored one. */
    public suspend fun save(schedule: ScheduledPrompt) {
        if (!schedule.valid) throw ScheduleStoreException(ScheduleCode.InvalidSchedule)
        mutate(schedule.profileId) { state ->
            val index = state.schedules.indexOfFirst { it.id == schedule.id }
            if (index < 0) {
                if (state.schedules.size >= MAX_SCHEDULES) throw ScheduleStoreException(ScheduleCode.ScheduleLimit)
                state.copy(schedules = state.schedules + schedule)
            } else {
                if (schedule.revision != state.schedules[index].revision + 1) {
                    throw ScheduleStoreException(ScheduleCode.StaleSchedule)
                }
                state.copy(schedules = state.schedules.replaced(index, schedule))
            }
        }
    }

    /** Removes its visible history too. Claims stay until profile deletion, so a delayed run cannot repeat. */
    public suspend fun delete(profileId: String, id: String) {
        mutate(profileId) { state ->
            state.copy(schedules = state.schedules.filterNot { it.id == id }, runs = state.runs.filterNot { it.scheduleId == id })
        }
    }

    /** Resuming skips the paused occurrences; a once schedule already past needs editing (`schedule_expired`). */
    public suspend fun setEnabled(profileId: String, id: String, enabled: Boolean) {
        mutate(profileId) { state ->
            val index = state.schedules.indexOfFirst { it.id == id }
            if (index < 0) throw ScheduleStoreException(ScheduleCode.ScheduleMissing)
            val current = state.schedules[index]
            if (current.enabled == enabled) return@mutate state
            val next = if (enabled) Recurrences.next(current.recurrence, nowMs(), zone()) else null
            if (enabled && next == null) throw ScheduleStoreException(ScheduleCode.ScheduleExpired)
            state.copy(
                schedules =
                    state.schedules.replaced(index, current.copy(enabled = enabled, nextRunAtMs = next, revision = current.revision + 1)),
            )
        }
    }

    /**
     * Advances only the exact occurrence that was claimed. A stale run never revives a removed schedule or
     * overwrites an edit or pause made while it ran.
     */
    public suspend fun advanceOccurrence(expected: ScheduledPrompt, nextMs: Long?): Boolean {
        var advanced = false
        mutate(expected.profileId) { state ->
            advanced = false
            val index = state.schedules.indexOfFirst { it.id == expected.id }
            if (index < 0) return@mutate state
            val current = state.schedules[index]
            if (current.revision != expected.revision || current.nextRunAtMs != expected.nextRunAtMs || !current.enabled) {
                return@mutate state
            }
            advanced = true
            state.copy(schedules = state.schedules.replaced(index, current.copy(nextRunAtMs = nextMs, enabled = nextMs != null)))
        }
        return advanced
    }

    /** Re-stamps a schedule's server fingerprint without a new revision (the Flutter-to-native migration only). */
    public suspend fun restamp(profileId: String, legacy: (String) -> Boolean, fingerprint: String): Int {
        var changed = 0
        mutate(profileId) { state ->
            changed = 0
            state.copy(
                schedules =
                    state.schedules.map {
                        if (legacy(it.profileFingerprint)) {
                            changed++
                            it.copy(profileFingerprint = fingerprint)
                        } else {
                            it
                        }
                    },
            )
        }
        return changed
    }

    /** Claims the digest cutoff before delivery; a concurrent toggle or an older cutoff suppresses it. */
    public suspend fun claimDigest(profileId: String, expected: ScheduleDigest, cutoffMs: Long): Boolean {
        var claimed = false
        mutate(profileId) { state ->
            claimed = false
            val current = state.digest
            val last = current.lastDeliveredAtMs
            if (!current.enabled || current != expected || (last != null && cutoffMs <= last)) return@mutate state
            claimed = true
            state.copy(digest = current.copy(lastDeliveredAtMs = cutoffMs))
        }
        return claimed
    }

    public suspend fun setDigest(profileId: String, digest: ScheduleDigest) {
        if (!digest.valid) throw ScheduleStoreException(ScheduleCode.InvalidSchedule)
        mutate(profileId) { it.copy(digest = digest) }
    }

    /** One record per occurrence: a running record may become terminal once; a terminal record never changes. */
    public suspend fun recordRun(profileId: String, run: ScheduledRun) {
        val stored = run.stored()
        if (!stored.valid) throw ScheduleStoreException(ScheduleCode.InvalidRunTransition)
        mutate(profileId) { state ->
            if (state.schedules.none { it.id == stored.scheduleId }) throw ScheduleStoreException(ScheduleCode.ScheduleMissing)
            val index = state.runs.indexOfFirst { it.id == stored.id }
            var runs =
                if (index >= 0) {
                    val previous = state.runs[index]
                    if (previous.scheduleId != stored.scheduleId || previous.scheduledAtMs != stored.scheduledAtMs ||
                        previous.startedAtMs != stored.startedAtMs ||
                        (previous.status != RunStatus.Running && previous != stored)
                    ) {
                        throw ScheduleStoreException(ScheduleCode.InvalidRunTransition)
                    }
                    state.runs.replaced(index, stored)
                } else {
                    state.runs + stored
                }
            runs = runs.sortedByDescending { it.scheduledAtMs }
            // Never discard an in-flight run merely to make room for display history.
            while (runs.size > MAX_HISTORY) {
                val oldestTerminal = runs.indexOfLast { it.status != RunStatus.Running }
                if (oldestTerminal < 0) throw ScheduleStoreException(ScheduleCode.HistoryLimit)
                runs = runs.filterIndexed { i, _ -> i != oldestTerminal }
            }
            state.copy(runs = runs)
        }
    }

    private suspend fun read(profileId: String): ScheduleCodec.Envelope {
        val key = ScheduleKeys.store(profileId)
        val raw = guarded { backend.read(key) }
        return decode(raw, profileId)
    }

    private fun decode(raw: String?, profileId: String): ScheduleCodec.Envelope =
        try {
            ScheduleCodec.decodeEnvelope(raw, profileId, MAX_SCHEDULES, MAX_HISTORY, MAX_BYTES)
        } catch (e: ScheduleFormatException) {
            throw ScheduleStoreException(ScheduleCode.StorageCorrupt)
        } catch (e: IllegalArgumentException) {
            throw ScheduleStoreException(ScheduleCode.StorageCorrupt)
        }

    private suspend fun mutate(profileId: String, change: (ScheduleCodec.Envelope) -> ScheduleCodec.Envelope) {
        val key = ScheduleKeys.store(profileId)
        repeat(CAS_ATTEMPTS) {
            val raw = guarded { backend.read(key) }
            val next = change(decode(raw, profileId))
            val encoded = ScheduleCodec.encodeEnvelope(next)
            if (encoded.encodeToByteArray().size > MAX_BYTES) throw ScheduleStoreException(ScheduleCode.StorageLimit)
            if (guarded { backend.compareAndSet(key, raw, encoded) }) return
        }
        throw ScheduleStoreException(ScheduleCode.StorageBusy)
    }

    private suspend fun <T> guarded(block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ScheduleStoreException) {
            throw e
        } catch (e: Exception) {
            throw ScheduleStoreException(ScheduleCode.StorageUnavailable)
        }
}

internal fun <T> List<T>.replaced(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }
