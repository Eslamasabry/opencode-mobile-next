package io.github.eslamasabry.oc.engine.runtime.healing

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.runtime.server.ProbeAnswer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/** Where foreground crash recovery stands (Dart `BuiltinRecoveryPhase`). */
public enum class RecoveryPhase { Idle, Paused, Stopped, Checking, Restarting, Waiting, Ready, Exhausted, Unconfirmed, StorageUnavailable }

/**
 * Port of lib/builtin/builtin_server_recovery.dart (`check`): the foreground health check of the in-app server that
 * restarts a dead one within a durable budget (3 attempts, 15/30/45 s apart), confirms the restart once the server
 * answers our password, and records each confirmed restart once in the while-away log. It acts only in the foreground,
 * for the bound owner profile, while the shared `restartPhoneServer` and `pollRestartHealth` switches allow it and the
 * person has not stopped the server. An explicit Start gives the budget back.
 *
 * With native recovery authority the profile is bound natively and unattended restarts belong to the native owner:
 * this check then only confirms a running server (it never dispatches a restart itself). The periodic tick of the
 * Dart class is the shell's timer calling [check].
 */
public class ServerRecovery(
    private val host: HealingHost,
    private val connection: HealingConnection,
    private val starter: HealingStarter,
    private val budgets: RecoveryBudgetStore,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    public var phase: RecoveryPhase = RecoveryPhase.Idle
        private set

    /** The profile the last published phase belongs to. */
    public var phaseProfileId: String? = null
        private set

    /** Called with every published phase (healing reconnects on [RecoveryPhase.Ready]). */
    public var onPhase: (suspend (RecoveryPhase, HealingProfile?) -> Unit)? = null

    private var foreground = false
    private var disposed = false
    private var checking = false
    private var activeCheck: CompletableDeferred<Unit>? = null
    private var cancellation: Job? = null
    private var generation = 0
    private var profile: HealingProfile? = null
    private var profileBound = false
    private var nativeAuthority = false
    private val manualResets = mutableSetOf<String>()

    init {
        starter.onManualReady = { started ->
            manualResets += started.id
            if (!profileBound || profile?.id == started.id) check(started)
        }
    }

    public val profileId: String? get() = profile?.id

    public fun setProfile(next: HealingProfile?) {
        profileBound = true
        if (next?.id == profile?.id) return
        profile = next
        generation++
        cancelNative()
    }

    public fun setForeground(value: Boolean) {
        if (foreground == value) return
        foreground = value
        generation++
        if (!value) {
            cancelNative()
            phase = RecoveryPhase.Paused
            phaseProfileId = profile?.id
        }
    }

    /** The automation policy of the bound profile changed: anything in flight is withdrawn. */
    public fun policyChanged() {
        generation++
        cancelNative()
    }

    /** Waits for every native cancellation asked so far. */
    public suspend fun drainCancellation() {
        cancellation?.join()
    }

    private fun cancelNative() {
        val previous = cancellation
        cancellation = scope.launch {
            previous?.join()
            host.cancelRecovery()
        }
    }

    private fun eligible(phone: HealingProfile, gen: Int): Boolean =
        !disposed && foreground && gen == generation &&
            connection.inAppProfiles().any { it.id == phone.id } &&
            connection.allows(phone.id, AutomationBehavior.RestartPhoneServer) &&
            connection.allows(phone.id, AutomationBehavior.PollRestartHealth)

    private suspend fun publish(value: RecoveryPhase, phone: HealingProfile?) {
        if (disposed) return
        phase = value
        phaseProfileId = profile?.id ?: phone?.id
        onPhase?.invoke(value, phone)
    }

    private suspend fun save(phone: HealingProfile, budget: RecoveryBudget): Boolean {
        if (connection.inAppProfiles().none { it.id == phone.id }) return false
        return budgets.write(phone.id, budget)
    }

    public suspend fun check(phone: HealingProfile?) {
        if (profileBound && phone?.id != profile?.id) return
        if (disposed) return
        if (checking) {
            activeCheck?.await()
            if (phone != null && phone.id in manualResets) check(phone)
            return
        }
        if (phone == null) {
            publish(RecoveryPhase.Idle, null)
            return
        }
        val gen = generation
        val manual = { phone.id in manualResets }
        if (!eligible(phone, gen) && !manual()) {
            publish(RecoveryPhase.Paused, phone)
            return
        }
        checking = true
        val done = CompletableDeferred<Unit>()
        activeCheck = done
        try {
            run(phone, gen, manual)
        } finally {
            checking = false
            activeCheck = null
            done.complete(Unit)
        }
    }

    private suspend fun run(phone: HealingProfile, gen: Int, manual: () -> Boolean) {
        drainCancellation()
        if (!eligible(phone, gen) && !manual()) return
        val initial = host.statusOrNull() ?: return publishIfEligible(RecoveryPhase.Unconfirmed, phone, gen)
        if (!eligible(phone, gen) && !manual()) return
        var budget: RecoveryBudget?
        if (initial.serverRecoveryAuthority) {
            nativeAuthority = true
            val bound = host.bindRecovery(phone.id, enabled = eligible(phone, gen))
            budget = (bound as? Outcome.Ok)?.value?.let { RecoveryBudget(attempts = it) }
            if (budget == null) return publish(RecoveryPhase.StorageUnavailable, phone)
        } else {
            budget = budgets.read(phone.id) ?: return publish(RecoveryPhase.StorageUnavailable, phone)
        }
        if (initial.idleBlocks) return publish(RecoveryPhase.Stopped, phone)
        if (budget.confirmedAt != null) {
            if (!recordConfirmation(phone, budget)) return publish(RecoveryPhase.StorageUnavailable, phone)
            budget = budget.finished()
        }
        if (manual()) {
            budget = RecoveryBudget()
            if (!nativeAuthority && !save(phone, budget)) return publish(RecoveryPhase.StorageUnavailable, phone)
            manualResets -= phone.id
        }
        if (!eligible(phone, gen)) return
        publish(RecoveryPhase.Checking, phone)
        val status = host.statusOrNull() ?: return publishIfEligible(RecoveryPhase.Unconfirmed, phone, gen)
        if (!eligible(phone, gen)) return
        if (status.idleBlocks || !status.installed || !status.serverRestartWanted || status.serverRecoveryGeneration == null) {
            return publish(RecoveryPhase.Stopped, phone)
        }
        if (starter.starting) return
        if (status.serverRunning) {
            val answer = host.probe(phone.password)
            if (!eligible(phone, gen)) return
            if (answer != ProbeAnswer.Answered) return publish(RecoveryPhase.Unconfirmed, phone)
            val confirmed = host.statusOrNull() ?: return publishIfEligible(RecoveryPhase.Unconfirmed, phone, gen)
            if (!eligible(phone, gen)) return
            if (confirmed.idleBlocks || !confirmed.serverRunning || !confirmed.serverRestartWanted) {
                return publish(RecoveryPhase.Stopped, phone)
            }
            if (budget.pending) {
                if (confirmed.serverRecoveryGeneration != budget.recoveryGeneration) {
                    budget = budget.finished()
                    if (!save(phone, budget)) return publish(RecoveryPhase.StorageUnavailable, phone)
                } else {
                    if (host.confirmRecovery(budget.recoveryGeneration!!) is Outcome.Failed) {
                        return publishIfEligible(RecoveryPhase.Unconfirmed, phone, gen)
                    }
                    if (!eligible(phone, gen)) return
                }
            }
            confirm(phone, budget, gen)
            return
        }
        // Native authority owns every unattended dispatch and its budget.
        if (nativeAuthority) return publish(RecoveryPhase.Waiting, phone)
        if (budget.pending) {
            budget = budget.finished()
            if (!save(phone, budget)) return publish(RecoveryPhase.StorageUnavailable, phone)
        }
        if (budget.attempts >= MAX_ATTEMPTS) return publish(RecoveryPhase.Exhausted, phone)
        val now = clock.now().toEpochMilliseconds()
        if (budget.nextAt != null && now < budget.nextAt) return publish(RecoveryPhase.Waiting, phone)
        val attempt = budget.attempts + 1
        budget =
            RecoveryBudget(
                attempts = attempt,
                nextAt = now + RETRY_DELAY.inWholeMilliseconds * attempt,
                pending = true,
                recoveryGeneration = status.serverRecoveryGeneration,
                eventId = "builtin-restart:${now * 1000}:$attempt",
            )
        if (!save(phone, budget)) return publish(RecoveryPhase.StorageUnavailable, phone)
        if (!eligible(phone, gen)) return
        publish(RecoveryPhase.Restarting, phone)
        val failure = starter.restart(phone, status.serverRecoveryGeneration) { eligible(phone, gen) }
        if (!eligible(phone, gen)) return
        if (failure == null) {
            val confirmed = host.statusOrNull()
            if (!eligible(phone, gen)) return
            if (confirmed != null && !confirmed.idleBlocks && confirmed.serverRunning && confirmed.serverRestartWanted) {
                confirm(phone, budget, gen)
                return
            }
        }
        publish(RecoveryPhase.Unconfirmed, phone)
    }

    private suspend fun publishIfEligible(value: RecoveryPhase, phone: HealingProfile, gen: Int) {
        if (eligible(phone, gen)) publish(value, phone)
    }

    private suspend fun recordConfirmation(phone: HealingProfile, budget: RecoveryBudget): Boolean {
        if (!connection.recordRestart(phone.id, budget.eventId!!, budget.confirmedAt!!)) return false
        return save(phone, budget.finished())
    }

    private suspend fun confirm(phone: HealingProfile, budget: RecoveryBudget, gen: Int): Boolean {
        if (budget.pending && budget.eventId != null) {
            starter.confirmRecovered(phone.id)
            val receipt = budget.confirmed(clock.now().toEpochMilliseconds())
            if (!save(phone, receipt) || !recordConfirmation(phone, receipt)) {
                publish(RecoveryPhase.StorageUnavailable, phone)
                return false
            }
        }
        if (eligible(phone, gen)) publish(RecoveryPhase.Ready, phone)
        return true
    }

    public fun dispose() {
        disposed = true
        generation++
        starter.onManualReady = null
        cancelNative()
    }

    public companion object {
        public const val MAX_ATTEMPTS: Int = 3
        public val RETRY_DELAY: kotlin.time.Duration = 15.seconds
    }
}
