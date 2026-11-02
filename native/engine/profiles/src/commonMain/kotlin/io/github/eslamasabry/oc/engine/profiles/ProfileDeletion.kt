package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import io.github.eslamasabry.oc.core.data.scope.ProfileKeyRule
import io.github.eslamasabry.oc.core.data.scope.ProfileScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * An engine that holds per-profile writers or caches (plan 03 §13.5 `Set<ProfileCloser>`): connection, runtime,
 * agents, schedule, composer, delivery, attention, and the `:engine:settings` stores (their `closeProfile`).
 * Profiles never imports them; they register this.
 */
public interface ProfileCloser {
    /** Module path, for the result. */
    public val owner: String

    /** Step 1, synchronous: refuse new work for [profile] from now on. Must not suspend or throw. */
    public fun stopAdmission(profile: ProfileId)

    /** Step 1: wait for admitted work to finish (drain pending writes, stop the profile's connections). */
    public suspend fun drain(profile: ProfileId): Outcome<Unit>

    /** Step 8: the deletion failed and the profile is kept: admit work again. */
    public fun reopen(profile: ProfileId)
}

/**
 * Data the generic key sweep does not reach (plan 05 §3.3): embedded records inside app-wide values, files, Keystore
 * aliases, widget/tile/shortcut state, the retained Flutter-era copies. [order] places it in the transaction
 * ([DeletionStep]); lower runs first. Contract: idempotent, removes only [profileId]'s data, never throws.
 */
public interface ProfileResourceCleaner {
    public val owner: String
    public val order: Int

    /**
     * True for a cleaner whose failure must stop the generic sweep (the stash: the sweep would erase the owner marker
     * that finds orphan files on the next launch, plan 05 §3.5 step 6).
     */
    public val guardsSweep: Boolean get() = false

    /** Clears [profileId]'s data; Ok(records removed, 0 = nothing to clear). */
    public suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int>
}

/** Positions in plan 05 §3.5's order. Cleaners pick one; the transaction owns [SWEEP] and [ROW]. */
public object DeletionStep {
    public const val QUEUE: Int = 300
    public const val DRAFTS: Int = 400
    public const val DEVICE_SURFACES: Int = 500
    public const val STASH: Int = 600

    /** Step 7, the generic sweep (reserved). */
    public const val SWEEP: Int = 700

    /** After the sweep, before the row: the retained Flutter-era copies (Astra M1). */
    public const val RETAINED_COPIES: Int = 800

    /** Step 9, the profile row and secrets (reserved). */
    public const val ROW: Int = 900

    /** Step 10 and later: data that may only go once the row is gone (plugin secrets, retained owner keys). */
    public const val AFTER_ROW: Int = 1000
}

/** Keys kept through the sweep (plan 05 §3.5 step 2). */
public fun interface RetainedKeys {
    /** Keys of [profileId] the sweep must leave; [untilRow] = they go after the row commit, else they stay for good. */
    public fun retained(profileId: ProfileId, untilRow: Boolean): Set<String>

    public companion object {
        /** Restore authority and tombstones: removed only once the row is gone (Flutter `retainedKeys`). */
        public val Default: RetainedKeys = RetainedKeys { id, untilRow ->
            if (!untilRow) {
                emptySet()
            } else {
                setOf(
                    "oc.automaticActivity.${id.raw}",
                    "oc.automation.${id.raw}",
                    "oc.teamEngineDeleted.${id.raw}",
                    "oc.scheduleDeleted.${id.raw}",
                )
            }
        }
    }
}

/**
 * [keepQueuedPromptsAsDrafts] moves the profile's queued prompts to Saved prompts; [confirmedQueue] is the person's
 * confirmed `QueuedPromptRemovalPlan` (opaque here; the delivery cleaner refuses when the live queue differs);
 * [linuxGone] = the in-app Linux was erased, so cleaners of what lived inside it have nothing to wait for.
 */
public data class DeletionOptions(
    val keepQueuedPromptsAsDrafts: Boolean = false,
    val confirmedQueue: QueuedPromptRemovalPlan? = null,
    val linuxGone: Boolean = false,
) {
    val structuralPlan: StructuralPlan get() = StructuralPlan(keepQueuedPromptsAsDrafts)
}

/** One owner that could not finish. [failure] never carries a value or a secret. */
public data class OwnerFailure(val owner: String, val failure: Failure)

/**
 * The truthful report (Flutter `DeleteProfileResult`). [removedProfile] false = the server was kept and
 * [failures] lists EVERY owner that refused, in transaction order (B-STATE-063).
 */
public data class DeleteProfileResult(
    val removedProfile: Boolean,
    val failures: List<OwnerFailure>,
    /** Records or keys removed, per owner (counts only). */
    val removedByOwner: Map<String, Int>,
    val sweptKeys: Int,
) {
    val failedOwners: List<String> get() = failures.map { it.owner }
    val complete: Boolean get() = removedProfile && failures.isEmpty()
}

/**
 * `ProfileDeletion.delete(id, options)` (plan 03 §10.5, plan 05 §3.5): the backwards transaction ported from
 * `connection/deletion.dart`.
 *
 * 1. Close admission synchronously on the profile's lanes (`<id>` and `<id>.agents`) and every [ProfileCloser], then
 *    drain them. 2. Retained keys. 3–6. [ProfileResourceCleaner]s below [DeletionStep.SWEEP] in order. 7. The generic
 *    sweep minus retained keys (skipped when a sweep-guarding cleaner failed). Cleaners up to [DeletionStep.ROW].
 *    8. Any failure: keep the profile, reopen everything, report every failed owner. 9. Row and secrets as one unit
 *    (BYO identity first; a refusal restores the previous `oc.profiles`). 10. Retained keys, then cleaners after the
 *    row; their failures are reported, the server is already gone.
 *
 * One deletion runs at a time; a second call for the same id while it runs gets the same result.
 */
public class ProfileDeletion(
    private val store: ProfileStore,
    private val scope: ProfileScope,
    private val lanes: WriterLanes,
    private val closers: Set<ProfileCloser> = emptySet(),
    private val cleaners: Set<ProfileResourceCleaner> = emptySet(),
    private val retainedKeys: Set<RetainedKeys> = setOf(RetainedKeys.Default),
) {
    private val log = AppLog.tagged("profiles.delete")
    private val serial = Mutex()
    private val pendingLock = Mutex()
    private val pending = HashMap<String, CompletableDeferred<Outcome<DeleteProfileResult>>>()

    public suspend fun delete(id: ProfileId, options: DeletionOptions = DeletionOptions()): Outcome<DeleteProfileResult> {
        val (deferred, mine) = pendingLock.withLock {
            pending[id.raw]?.let { it to false } ?: CompletableDeferred<Outcome<DeleteProfileResult>>().also { pending[id.raw] = it } to true
        }
        if (!mine) return deferred.await()
        try {
            val result = serial.withLock {
                // B-STATE-098: finish an admitted deletion, including its final row/secret receipt.
                withContext(NonCancellable) { run(id, options) }
            }
            deferred.complete(result)
            return result
        } catch (e: Throwable) {
            // Cancelled or a bug mid-transaction: a server still saved must not stay half-closed.
            withContext(NonCancellable) {
                abortReopen(id)
            }
            deferred.completeExceptionally(e)
            throw e
        } finally {
            withContext(NonCancellable) { pendingLock.withLock { pending.remove(id.raw) } }
        }
    }

    private suspend fun run(id: ProfileId, options: DeletionOptions): Outcome<DeleteProfileResult> {
        if (id.raw.isEmpty()) return Outcome.Failed(Failure.Refused(RefusalReason.ProfileDeleted))
        val laneIds = listOf(id, ProfileId(id.raw + ProfileKeyRule.AGENT_BACKEND_SUFFIX))
        val failures = ArrayList<OwnerFailure>()
        val removed = LinkedHashMap<String, Int>()

        // 1. Admission closes synchronously, before anything can yield.
        store.beginDeletion(id)
        laneIds.forEach { lanes.closeProfile(it) }
        closers.forEach { it.stopAdmission(id) }
        when (val drained = store.drainMutations()) {
            is Outcome.Failed -> failures += OwnerFailure(ProfileKeys.OWNER, drained.failure)
            is Outcome.Ok -> Unit
        }
        if (failures.isNotEmpty()) return reopen(id, laneIds, failures, removed, 0)
        laneIds.forEach { lanes.drainProfile(it) }
        for (closer in closers) {
            val drained = attempt(::unexpected) { closer.drain(id) }.flatten()
            if (drained is Outcome.Failed) failures += OwnerFailure(closer.owner, drained.failure)
        }

        // 2. Retained through the row commit, and for good.
        val keptForGood = retainedKeys.flatMapTo(LinkedHashSet()) { it.retained(id, untilRow = false) }
        val keptUntilRow = retainedKeys.flatMapTo(LinkedHashSet(keptForGood)) { it.retained(id, untilRow = true) }

        // 3–6. Cleaners before the sweep; every one runs and every refusal is reported.
        val ordered = cleaners.sortedBy { it.order }
        var sweepGuardFailed = false
        for (cleaner in ordered.filter { it.order < DeletionStep.SWEEP }) {
            if (!runCleaner(cleaner, id, options, failures, removed) && cleaner.guardsSweep) sweepGuardFailed = true
        }

        // 7. The generic sweep, computed late (after the lanes drained).
        var swept = 0
        if (!sweepGuardFailed) {
            when (val sweep = scope.sweep(id, keptUntilRow)) {
                is Outcome.Ok -> {
                    swept += sweep.value.removedKeys
                    sweep.value.removedByOwner.forEach { (owner, n) -> removed[owner] = (removed[owner] ?: 0) + n }
                }
                is Outcome.Failed -> failures += OwnerFailure(SWEEP_OWNER, sweep.failure)
            }
        }
        for (cleaner in ordered.filter { it.order > DeletionStep.SWEEP && it.order < DeletionStep.ROW }) {
            runCleaner(cleaner, id, options, failures, removed)
        }

        // 8. Any failure keeps the server.
        if (failures.isNotEmpty()) return reopen(id, laneIds, failures, removed, swept)

        // 9. Row and secrets as one unit.
        var secretCount = 0
        val row = store.removeRowAndSecrets(id) {
            scope.ownedData(id).map { owned -> orderedSecretNames(owned.secretNames).also { secretCount = it.size } }
        }
        if (row is Outcome.Failed) {
            failures += OwnerFailure(ProfileKeys.OWNER, row.failure)
            return reopen(id, laneIds, failures, removed, swept)
        }
        removed[ProfileKeys.OWNER] = (removed[ProfileKeys.OWNER] ?: 0) + 1 + secretCount

        // 10. Retained keys last (everything but the keys kept for good), then cleaners that need the row gone.
        when (val last = scope.sweep(id, keptForGood)) {
            is Outcome.Ok -> {
                swept += last.value.removedKeys
                last.value.removedByOwner.forEach { (owner, n) -> removed[owner] = (removed[owner] ?: 0) + n }
            }
            is Outcome.Failed -> failures += OwnerFailure(SWEEP_OWNER, last.failure)
        }
        for (cleaner in ordered.filter { it.order > DeletionStep.ROW }) runCleaner(cleaner, id, options, failures, removed)
        store.endDeletion(id)
        if (failures.isNotEmpty()) log.w { "server removed; ${failures.size} owners could not finish after the row" }
        return DeleteProfileResult(removedProfile = true, failures = failures.toList(), removedByOwner = removed, sweptKeys = swept).ok()
    }

    private suspend fun runCleaner(
        cleaner: ProfileResourceCleaner,
        id: ProfileId,
        options: DeletionOptions,
        failures: MutableList<OwnerFailure>,
        removed: MutableMap<String, Int>,
    ): Boolean = when (val result = attempt(::unexpected) { cleaner.clear(id, options) }.flatten()) {
        is Outcome.Ok -> {
            if (result.value > 0) removed[cleaner.owner] = (removed[cleaner.owner] ?: 0) + result.value
            true
        }
        is Outcome.Failed -> {
            failures += OwnerFailure(cleaner.owner, result.failure)
            false
        }
    }

    private suspend fun reopen(
        id: ProfileId,
        laneIds: List<ProfileId>,
        failures: List<OwnerFailure>,
        removed: Map<String, Int>,
        swept: Int,
    ): Outcome<DeleteProfileResult> {
        laneIds.forEach { lanes.forProfile(it).resumeAdmission() }
        closers.forEach { it.reopen(id) }
        store.cancelDeletion(id)
        log.w { "server kept: ${failures.size} owners refused" }
        return DeleteProfileResult(removedProfile = false, failures = failures.toList(), removedByOwner = removed, sweptKeys = swept).ok()
    }

    private suspend fun abortReopen(id: ProfileId) {
        if (store.profile(id) == null) return
        listOf(id, ProfileId(id.raw + ProfileKeyRule.AGENT_BACKEND_SUFFIX)).forEach { lanes.forProfile(it).resumeAdmission() }
        closers.forEach { it.reopen(id) }
        store.cancelDeletion(id)
    }

    /** BYO identity first (its refusal keeps metadata for a retry), then the order of [ProfileKeys.SECRET_PREFIXES]. */
    private fun orderedSecretNames(names: Set<String>): List<String> {
        val prefixes = ProfileKeys.SECRET_PREFIXES.keys.toList()
        return names.sortedBy { name -> prefixes.indexOfFirst { name.startsWith(it) }.let { if (it < 0) prefixes.size else it } }
    }

    public companion object {
        /** The owner name of the generic key sweep in [DeleteProfileResult.failures]. */
        public const val SWEEP_OWNER: String = ":core:data sweep"
    }
}

private fun unexpected(error: Throwable): Failure = Failure.Unexpected(Redacted.shape(error))

private fun <T> Outcome<Outcome<T>>.flatten(): Outcome<T> = when (this) {
    is Outcome.Ok -> value
    is Outcome.Failed -> this
}
