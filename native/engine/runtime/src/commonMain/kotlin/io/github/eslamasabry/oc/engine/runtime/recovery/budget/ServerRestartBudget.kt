package io.github.eslamasabry.oc.engine.runtime.recovery.budget

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.runtime.bridge.BuiltinLinuxBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Where automatic restart of the phone server stands (port of `BuiltinRecoveryPhase`). */
public enum class RecoveryPhase {
    Idle, Paused, Stopped, Checking, Restarting, Waiting, Ready, Exhausted, Unconfirmed, StorageUnavailable,
}

/** One published reading (port of `BuiltinRecoveryState`). */
public data class RecoveryState(
    val phase: RecoveryPhase = RecoveryPhase.Idle,
    val profileId: ProfileId? = null,
    val attempts: Int = 0,
    val nextAttemptAt: Instant? = null,
) {
    val maxAttempts: Int get() = RecoveryBudget.MAX_ATTEMPTS
}

/** What the restart budget needs from the app around it. The Android binding implements it over the profile store. */
public interface RecoveryHost {
    /** The profile is still saved (not deleted or being deleted). */
    public fun profileExists(profile: ProfileId): Boolean

    /** Its address is the in-app server's ([BuiltinLinuxBridge.managesServerUrl]). */
    public fun isInAppServer(profile: ProfileId): Boolean

    /** Both automation behaviours `restartPhoneServer` and `pollRestartHealth` are on for it. */
    public fun restartAllowed(profile: ProfileId): Boolean

    /** One authenticated health probe of its server. */
    public suspend fun healthy(profile: ProfileId): Boolean

    /** Records one confirmed automatic restart in the act store; false when it could not be kept. */
    public suspend fun recordRestart(profile: ProfileId, eventId: String, at: Instant): Boolean

    /** The legacy budget or the migration marker under `oc.builtinRecovery.<id>`; null when absent. */
    public suspend fun readMarker(key: String): String?

    /** False when the durable write failed. */
    public suspend fun writeMarker(key: String, value: String): Boolean
}

/**
 * The shared start path for an automatic restart (the Android binding passes `PhoneServerStarter.start(…,
 * LaunchMode.Recovery(generation))`): true only when the server came back answering and native confirmed the attempt.
 */
public interface RecoveryRestarter {
    /** A start (manual or automatic) is in flight now. */
    public val starting: Boolean

    public suspend fun restart(profile: ProfileId, recoveryGeneration: Long, stillWanted: () -> Boolean): Boolean

    /** The automatic attempt answered and its receipt is being kept. */
    public fun recovered(profile: ProfileId) {}
}

/**
 * The foreground consumer of native crash recovery and its three-attempt budget (port of the native-authority half of
 * lib/builtin/builtin_server_recovery.dart; B-LINUX-032…034). The healing owner calls [check] on its foreground timer.
 *
 * Native owns migrated retry reservations: a legacy budget is staged once into native storage while disabled, then the
 * `{"version":2,"nativeAuthority":true}` marker is written before native is armed, so spent attempts never come back.
 * Direct and background restarts share the same three attempts across owners; a healthy server in an open app starts
 * the count again once per visit; only a confirmed manual Start ([manualStartConfirmed]) resets it otherwise. Stop,
 * a background app and a scheduled native restore never dispatch from here.
 *
 * Confined to one lane: call every member from the same coroutine context. [scope] runs the fire-and-forget native
 * cancellations, in order.
 */
public class ServerRestartBudget(
    private val linux: BuiltinLinuxBridge,
    private val host: RecoveryHost,
    private val restarter: RecoveryRestarter,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val state = MutableStateFlow(RecoveryState())
    public val value: StateFlow<RecoveryState> = state.asStateFlow()

    private var foreground = false
    private var presenceResetThisVisit = false
    private var disposed = false
    private var checking = false
    private var storageFailed = false
    private var nativeAuthority = false
    private var activeCheck: CompletableDeferred<Unit>? = null
    private var nativeChain: Job? = null
    private var generation = 0
    private val manualResets = mutableSetOf<ProfileId>()
    private var profileId: ProfileId? = null
    private var profileBound = false

    /** Selects the saved phone profile; a change unbinds the old one natively and invalidates its admission. */
    public fun setProfile(profile: ProfileId?) {
        profileBound = true
        if (profile == profileId) return
        val previous = profileId
        if (nativeAuthority && previous != null) enqueueNative { linux.unbindServerRecovery(previous) }
        profileId = profile
        generation++
        cancelNative()
    }

    public fun setForeground(value: Boolean) {
        if (foreground == value) return
        foreground = value
        generation++
        if (value) presenceResetThisVisit = false
        if (!value) {
            cancelNative()
            publish(RecoveryPhase.Paused)
        }
    }

    /** The automation policy changed: native rereads it before every launch, and pending admission ends here. */
    public fun policyChanged() {
        val id = profileId
        if (nativeAuthority && id != null) enqueueNative { linux.unbindServerRecovery(id) }
        generation++
        cancelNative()
    }

    /**
     * Closes admission at once, drains the native queue and any active check, then erases the native record. Await it
     * before the profile deletion sweep removes `oc.builtinRecovery.<id>`.
     */
    public suspend fun suspendProfile(profile: ProfileId) {
        if (profileId == profile) setProfile(null)
        manualResets.remove(profile)
        nativeChain?.join()
        activeCheck?.await()
        if (nativeAuthority) linux.unbindServerRecovery(profile, delete = true)
    }

    /** The shared starter confirmed a healthy manual Start of [profile]: the only reset outside a healthy visit. */
    public suspend fun manualStartConfirmed(profile: ProfileId) {
        manualResets += profile
        if (!profileBound || profileId == profile) check(profile)
    }

    public fun dispose() {
        disposed = true
        generation++
        cancelNative()
    }

    private fun enqueueNative(block: suspend () -> Unit) {
        val previous = nativeChain
        nativeChain = scope.launch {
            previous?.join()
            block()
        }
    }

    private fun cancelNative() = enqueueNative { linux.cancelServerRecovery() }

    private fun eligible(profile: ProfileId, at: Int): Boolean =
        !disposed && foreground && at == generation && host.profileExists(profile) && host.restartAllowed(profile)

    private fun admitted(profile: ProfileId, at: Int): Boolean = eligible(profile, at) || profile in manualResets

    private fun publish(phase: RecoveryPhase, budget: RecoveryBudget? = null) {
        if (disposed) return
        state.value =
            RecoveryState(
                phase = phase,
                profileId = profileId,
                attempts = budget?.attempts ?: state.value.attempts,
                nextAttemptAt = budget?.nextAt,
            )
    }

    /** Persists [budget]; natively only receipts with the same counter. Null when it could not be kept. */
    private suspend fun save(profile: ProfileId, budget: RecoveryBudget): RecoveryBudget? {
        if (!host.profileExists(profile)) return null
        val saved =
            if (nativeAuthority) {
                RecoveryBudget.readMap(linux.updateServerRecoveryReceipt(profile, budget.toMap()).getOrNull())
                    ?.let { budget.copy(revision = it.revision) }
            } else {
                budget.takeIf { host.writeMarker(keyFor(profile), it.toJson()) }
            }
        // Never admit another attempt from a value that was not kept.
        if (saved == null) storageFailed = true
        return saved
    }

    /** Safe to call concurrently: one check at a time; a profile change invalidates the old one at once. */
    public suspend fun check(profile: ProfileId?) {
        if (profileBound && profile != profileId) return
        if (profileId != profile) {
            profileId = profile
            generation++
        }
        if (disposed) return
        if (checking) {
            activeCheck?.await()
            // A confirmed manual start may arrive after the active check passed its reset branch.
            if (profile != null && profile in manualResets) check(profile)
            return
        }
        if (profile == null || !host.isInAppServer(profile)) {
            publish(RecoveryPhase.Idle)
            return
        }
        val at = generation
        if (!admitted(profile, at)) {
            publish(RecoveryPhase.Paused)
            return
        }
        checking = true
        val done = CompletableDeferred<Unit>()
        activeCheck = done
        try {
            if (!checkOnce(profile, at) && eligible(profile, at)) publish(RecoveryPhase.Unconfirmed)
        } finally {
            checking = false
            done.complete(Unit)
            activeCheck = null
        }
    }

    /** False where the Dart check threw: a native refusal or a malformed receipt. */
    private suspend fun checkOnce(profile: ProfileId, at: Int): Boolean {
        nativeChain?.join()
        if (!admitted(profile, at)) return true
        val initial = linux.status().getOrNull() ?: return false
        if (!admitted(profile, at)) return true
        var budget: RecoveryBudget?
        if (initial.serverRecoveryAuthority) {
            budget = migrate(profile, at) ?: run {
                publish(RecoveryPhase.StorageUnavailable)
                return true
            }
        } else {
            budget = RecoveryBudget.readJson(host.readMarker(keyFor(profile)))
        }
        if (initial.idleBlocks) {
            publish(RecoveryPhase.Stopped, budget)
            return true
        }
        if (nativeAuthority) {
            val receipts = linux.serverRecoveryReceipts(profile).getOrNull() ?: return false
            for (value in receipts) {
                val receipt = RecoveryBudget.readMap(value)
                val eventId = receipt?.eventId
                val confirmedAt = receipt?.confirmedAt
                if (eventId == null || confirmedAt == null) return false
                if (!host.recordRestart(profile, eventId, confirmedAt)) {
                    publish(RecoveryPhase.StorageUnavailable, budget)
                    return true
                }
                if (linux.ackServerRecoveryReceipt(profile, eventId) is Outcome.Failed) return false
            }
        }
        if (budget?.confirmedAt != null) {
            // A receipt is historical health evidence, independent of a process that may since have stopped.
            budget = recordConfirmation(profile, budget) ?: run {
                publish(RecoveryPhase.StorageUnavailable, budget)
                return true
            }
        }
        if (profile in manualResets) {
            budget =
                if (nativeAuthority) {
                    val reset = linux.confirmManualServerStart(profile)
                    if (reset is Outcome.Failed) return false
                    RecoveryBudget.readMap(reset.getOrNull())
                } else {
                    save(profile, RecoveryBudget())
                }
            if (budget == null) {
                publish(RecoveryPhase.StorageUnavailable)
                return true
            }
            manualResets.remove(profile)
            storageFailed = false
        }
        if (budget == null || storageFailed) {
            publish(RecoveryPhase.StorageUnavailable)
            return true
        }
        if (!eligible(profile, at)) return true
        publish(RecoveryPhase.Checking, budget)
        val status = linux.status().getOrNull() ?: return false
        if (!eligible(profile, at)) return true
        if (status.idleBlocks || !status.installed || !status.serverRestartWanted || status.serverRecoveryGeneration == null) {
            publish(RecoveryPhase.Stopped, budget)
            return true
        }
        if (restarter.starting) return true
        if (status.serverRunning) return confirmRunning(profile, budget, at)
        if (nativeAuthority && status.serverRecoveryScheduled) {
            publish(RecoveryPhase.Waiting, budget)
            return true
        }
        // A pending dispatch with no process is a confirmed failed attempt.
        if (budget.pending) {
            budget = save(profile, budget.finished()) ?: run {
                publish(RecoveryPhase.StorageUnavailable, budget.finished())
                return true
            }
        }
        if (budget.attempts >= RecoveryBudget.MAX_ATTEMPTS) {
            publish(RecoveryPhase.Exhausted, budget)
            return true
        }
        val nextAt = budget.nextAt
        if (nextAt != null && clock.now() < nextAt) {
            publish(RecoveryPhase.Waiting, budget)
            return true
        }
        val now = clock.now()
        val recoveryGeneration = status.serverRecoveryGeneration
        if (!nativeAuthority) {
            val next = budget.attempts + 1
            val reserved =
                RecoveryBudget(
                    attempts = next,
                    nextAt = now + RETRY_DELAY * next,
                    pending = true,
                    recoveryGeneration = recoveryGeneration,
                    eventId = "builtin-restart:${now.toEpochMilliseconds() * 1000}:$next",
                )
            budget = save(profile, reserved) ?: run {
                publish(RecoveryPhase.StorageUnavailable, reserved)
                return true
            }
        }
        if (!eligible(profile, at)) return true
        publish(RecoveryPhase.Restarting, budget)
        val answered = restarter.restart(profile, recoveryGeneration) { eligible(profile, at) }
        if (!eligible(profile, at)) return true
        if (nativeAuthority) {
            val snapshot = linux.serverRecoveryBudget(profile)
            if (snapshot is Outcome.Failed) return false
            budget = RecoveryBudget.readMap(snapshot.getOrNull()) ?: run {
                publish(RecoveryPhase.StorageUnavailable)
                return true
            }
        }
        if (answered) {
            val confirmed = linux.status().getOrNull() ?: return false
            if (!eligible(profile, at)) return true
            if (!confirmed.idleBlocks && confirmed.serverRunning && confirmed.serverRestartWanted) {
                confirm(profile, budget, at)
                return true
            }
        }
        // Do not dispatch again until a later status proves the process absent.
        publish(RecoveryPhase.Unconfirmed, budget)
        return true
    }

    /** Stages a legacy budget once, writes the marker before arming native, then binds. Null: fail closed. */
    private suspend fun migrate(profile: ProfileId, at: Int): RecoveryBudget? {
        val raw = host.readMarker(keyFor(profile))
        val migrated = nativeMarker(raw)
        if (!migrated) {
            val legacy = RecoveryBudget.readJson(raw) ?: return null
            // Stage while disabled: a failed marker write leaves an inactive native record the next check reuses.
            if (linux.stageServerRecovery(profile, legacy.toMap()) is Outcome.Failed) return null
            if (!host.writeMarker(keyFor(profile), MARKER)) return null
        }
        nativeAuthority = true
        return RecoveryBudget.readMap(linux.bindServerRecovery(profile, enabled = eligible(profile, at)).getOrNull())
    }

    private suspend fun confirmRunning(profile: ProfileId, start: RecoveryBudget, at: Int): Boolean {
        var budget = start
        val healthy = host.healthy(profile)
        if (!eligible(profile, at)) return true
        if (!healthy) {
            publish(RecoveryPhase.Unconfirmed, budget)
            return true
        }
        // Recheck stop intent after the asynchronous probe: Stop wins.
        val confirmed = linux.status().getOrNull() ?: return false
        if (!eligible(profile, at)) return true
        if (confirmed.idleBlocks || !confirmed.serverRunning || !confirmed.serverRestartWanted) {
            publish(RecoveryPhase.Stopped, budget)
            return true
        }
        if (budget.pending) {
            if (confirmed.serverRecoveryGeneration != budget.recoveryGeneration) {
                // Another owner or an explicit start replaced this attempt: never attribute it to an old act.
                budget = save(profile, budget.finished()) ?: run {
                    publish(RecoveryPhase.StorageUnavailable, budget.finished())
                    return true
                }
            } else {
                val generation = budget.recoveryGeneration ?: return false
                if (linux.confirmServerRecovery(generation) is Outcome.Failed) return false
                if (!eligible(profile, at)) return true
            }
        }
        if (confirm(profile, budget, at)) resetForPresence(profile, budget, at)
        return true
    }

    private suspend fun recordConfirmation(profile: ProfileId, budget: RecoveryBudget): RecoveryBudget? {
        val eventId = budget.eventId ?: return null
        val at = budget.confirmedAt ?: return null
        if (!host.recordRestart(profile, eventId, at)) return null
        return save(profile, budget.finished())
    }

    /** False when the receipt could not be kept. */
    private suspend fun confirm(profile: ProfileId, budget: RecoveryBudget, at: Int): Boolean {
        if (budget.pending && budget.eventId != null) {
            restarter.recovered(profile)
            // Persist the health-confirmed receipt before the act store records it: a pause cannot erase it.
            val receipt = save(profile, budget.confirmed(clock.now())) ?: run {
                publish(RecoveryPhase.StorageUnavailable, budget)
                return false
            }
            if (recordConfirmation(profile, receipt) == null) {
                publish(RecoveryPhase.StorageUnavailable, receipt)
                return false
            }
        }
        if (eligible(profile, at)) publish(RecoveryPhase.Ready, budget)
        return true
    }

    /**
     * The person is here and the server answers: the count starts again, once per visit, so a server that keeps
     * crashing while the app is open still runs out of tries. Native refuses while a restore is reserved.
     */
    private suspend fun resetForPresence(profile: ProfileId, budget: RecoveryBudget, at: Int) {
        if (!nativeAuthority || presenceResetThisVisit || budget.attempts == 0) return
        val reset = RecoveryBudget.readMap(linux.confirmServerPresence(profile).getOrNull()) ?: return
        if (reset.attempts != 0) return
        presenceResetThisVisit = true
        if (eligible(profile, at)) publish(RecoveryPhase.Ready, reset)
    }

    public companion object {
        public val RETRY_DELAY: kotlin.time.Duration = 15.seconds

        /** The native authority marker written before native is armed. */
        public const val MARKER: String = """{"version":2,"nativeAuthority":true}"""

        /** Swept with the profile (`oc.<what>.<profileId>`). */
        public fun keyFor(profile: ProfileId): String = "oc.builtinRecovery.${profile.raw}"

        /** Exactly version 2 with native authority; anything else (corrupt, false) is not migrated. */
        public fun nativeMarker(raw: String?): Boolean {
            val map = raw?.let(RecoveryBudget::parse) as? Map<*, *> ?: return false
            return RecoveryBudget.integer(map["version"]) == 2L && map["nativeAuthority"] == true
        }
    }
}

