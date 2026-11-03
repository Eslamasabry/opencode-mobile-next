package io.github.eslamasabry.oc.engine.runtime.healing

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import io.github.eslamasabry.oc.engine.runtime.server.LaunchStartRules
import io.github.eslamasabry.oc.engine.runtime.server.LaunchStep
import io.github.eslamasabry.oc.engine.runtime.server.StartProblem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Port of lib/builtin/phone_server_healing.dart: the app-lifetime owner of the in-app server's health, so recovery
 * does not depend on opening This phone. It picks the runtime owner (the persisted `oc.builtinServerOwner`, else the
 * only readable in-app profile; several legacy profiles wait for an explicit Start), restores a native idle stop in
 * the foreground ([IdleResume]), runs the crash recovery check ([ServerRecovery]) only in the foreground, starts a
 * stopped server once per app process when the app opens on it, connects once per start with a back-off for automatic
 * reconnects, and keeps the local agents' CPU lease ([AgentWorkLease]) and native work observation in step with the
 * agent inventory. Streamed updates that change none of that cost no native call.
 *
 * The shell calls [onProfilesChanged] when the profile store changes, [onConnectionChanged] on every connection
 * notify, [setForeground] on lifecycle, [startForLaunch] when the app opens on this phone's server, and [heartbeat]
 * every 15 s in the background. Unawaited Dart work runs in [scope].
 */
public class PhoneServerHealing(
    private val connection: HealingConnection,
    private val host: HealingHost,
    public val starter: HealingStarter,
    public val recovery: ServerRecovery,
    private val scope: CoroutineScope,
    private val clock: Clock,
    /** BA's current inventory truth for the readable alias; null is unknown and acquires nothing. */
    private val localAgentWorkBusy: ((String) -> Boolean?)? = null,
    private val resumeAgentsAfterIdle: (suspend (profileId: String, expectedIdleGeneration: Long) -> Outcome<Unit>)? = null,
    newLeaseId: () -> String = AgentWorkLease::randomLeaseId,
    /** How long the launch start waits before its one retry. */
    public val launchRetryDelay: Duration = 2.seconds,
    /** How often the launch asks a booting server whether it answers yet. */
    public val launchPollInterval: Duration = 2.seconds,
    /** Delay before an automatic reconnect after a failed connect; doubles per failure up to [MAX_RECONNECT_BACKOFF]. */
    public val reconnectBackoff: Duration = 10.seconds,
) {
    public val idle: IdleResume =
        IdleResume(
            host = host,
            isForeground = { !disposed && foreground },
            ownerId = { owner?.id },
            isReadable = connection::isReadable,
            resumeAgents = { id, generation -> resumeAgentsAfterIdle?.invoke(id, generation) ?: IDLE_RESUME_ABSENT },
            scope = scope,
        )
    private val agentWork = AgentWorkLease(host, clock, scope, newLeaseId)

    private var owner: HealingProfile? = null
    private var foreground = false
    private var disposed = false
    private var ownerStorageFailed = false
    private var manualClaiming = false
    private var lastObservation: String? = null
    private var lastStreamStatus: LinkStatus? = null
    private var observations: Job? = null
    private var observationRevision = 0
    private var idleEpoch = 0
    // A manual claim cancels an old idle restore, but its own launch must still finish.
    private var launchEpoch = 0
    private var lastConnectionAttempt: String? = null
    private var connecting = false
    private var connectFailedAt: Instant? = null
    private var connectFailures = 0
    private var launchStartUsed = false
    private var foregroundWaiter: CompletableDeferred<Unit>? = null

    public val ownerId: String? get() = owner?.id
    public val idleResuming: Boolean get() = idle.running
    public val idleResumeFailure: IdleFailure? get() = idle.failure

    init {
        recovery.onPhase = { phase, profile -> recoveryChanged(phase, profile) }
        starter.beforeManualStart = ::claimProfile
        syncProfile()
        lastStreamStatus = connection.status()
    }

    private suspend fun claimProfile(profile: HealingProfile): Boolean {
        if (disposed || !connection.isReadable(profile.id)) return false
        manualClaiming = true
        try {
            invalidateIdle(revokeLaunch = false)
            recovery.setProfile(null)
            recovery.drainCancellation()
            ownerStorageFailed = true
            if (!connection.claimRuntimeOwner(profile.id)) return false
            ownerStorageFailed = false
            syncProfile()
            recovery.setForeground(foreground)
            // The native launch snapshots its binding once: drain migration and binding before the recipe goes.
            recovery.check(profile)
            if (recovery.phaseProfileId != profile.id) recovery.check(profile)
            return !disposed &&
                connection.isReadable(profile.id) &&
                owner?.id == profile.id &&
                connection.runtimeOwner() == profile.id
        } finally {
            manualClaiming = false
        }
    }

    /** The profile store changed. */
    public fun onProfilesChanged(): Unit = syncProfile()

    /** Any connection notify (most are streamed message updates: they do no bookkeeping and no checks). */
    public fun onConnectionChanged() {
        syncProfile()
        connectionChanged()
    }

    private fun syncProfile() {
        if (disposed) return
        val profiles = connection.inAppProfiles().filter { connection.isReadable(it.id) }
        val pointer = connection.runtimeOwner()
        // A legacy installation with several local profiles has no reliable owner: an explicit Start selects one.
        val selected =
            when {
                ownerStorageFailed -> null
                pointer == null -> profiles.singleOrNull()
                else -> profiles.firstOrNull { it.id == pointer }
            }
        val changed = selected?.id != owner?.id
        if (changed) {
            invalidateIdle()
            owner = selected
            lastObservation = null
        }
        recovery.setProfile(selected)
        observeAgentWork()
        if (changed && foreground && !manualClaiming && selected != null) scope.launch { check(selected) }
    }

    private fun connectionChanged() {
        if (disposed) return
        val status = connection.status()
        val previous = lastStreamStatus
        lastStreamStatus = status
        // The health pipeline runs on the way down, not on every update.
        val current = owner
        if (status != previous &&
            (status == LinkStatus.Disconnected || status == LinkStatus.Reconnecting) &&
            current != null &&
            foreground &&
            !manualClaiming &&
            connection.selectedProfileId() == current.id
        ) {
            scope.launch { check(current) }
        }
        observeAgentWork()
    }

    /** Native reads this evidence only in the background and drops it after 45 s: renewed every 15 s there. */
    public fun heartbeat() {
        if (!disposed && !foreground) observeAgentWork(force = true)
    }

    private fun observeAgentWork(force: Boolean = false) {
        if (disposed) return
        val current = owner
        if (current == null || !connection.isReadable(current.id)) {
            if (lastObservation == NONE && !force) return
            lastObservation = NONE
            observationRevision++
            agentWork.observe(null, null)
            return
        }
        val agentOwner = connection.agentOwnerId(current.id)
        val busy =
            try {
                localAgentWorkBusy?.invoke(current.id)
            } catch (_: Exception) {
                null
            }
        val key = "${current.id}|$agentOwner|$busy"
        if (!force && key == lastObservation) return
        lastObservation = key
        val revision = ++observationRevision
        agentWork.observe(agentOwner, busy)
        // Native idle truth uses the readable server alias; neither alias invents an idle answer.
        val previous = observations
        observations =
            scope.launch {
                previous?.join()
                if (disposed || revision != observationRevision || owner?.id != current.id || !connection.isReadable(current.id)) {
                    return@launch
                }
                host.observeAgentWork(current.id, busy) // A failed observation expires natively into unknown.
            }
    }

    public fun setForeground(value: Boolean) {
        val was = foreground
        foreground = value
        // Backgrounding hands native a fresh sample at once.
        if (!value && was && !disposed) observeAgentWork(force = true)
        if (value) {
            foregroundWaiter?.complete(Unit)
            foregroundWaiter = null
            val current = owner
            if (current != null) scope.launch { check(current) }
        } else {
            invalidateIdle()
            recovery.setForeground(false)
        }
    }

    private suspend fun recoveryChanged(phase: RecoveryPhase, profile: HealingProfile?) {
        if (phase != RecoveryPhase.Ready) return
        val current = owner ?: return
        if (!foreground || profile?.id != current.id) return
        connectIfNeeded(current)
    }

    private fun invalidateIdle(revokeLaunch: Boolean = true) {
        idleEpoch++
        if (revokeLaunch) launchEpoch++
        idle.invalidate()
        recovery.setForeground(false)
    }

    private fun mine(profile: HealingProfile): Boolean =
        !disposed && !manualClaiming && foreground && owner?.id == profile.id && connection.isReadable(profile.id)

    private fun idleCurrent(profile: HealingProfile, epoch: Int): Boolean = mine(profile) && epoch == idleEpoch

    private fun launchCurrent(profile: HealingProfile, epoch: Int): Boolean = mine(profile) && epoch == launchEpoch

    /** The idle restore, then (unless the idle owner holds the server) the recovery check. Foreground only. */
    public suspend fun check(profile: HealingProfile) {
        val epoch = idleEpoch
        if (!idleCurrent(profile, epoch)) return
        idle.check()
        if (!idleCurrent(profile, epoch)) return
        val status = host.statusOrNull()
        if (!idleCurrent(profile, epoch)) return
        if (status == null || idle.failure != null || status.idleBlocks) {
            recovery.setForeground(false)
            return
        }
        recovery.setForeground(true)
        recovery.check(profile)
    }

    /**
     * The app opened on this phone's own server: a stopped server is started for the person once per app process (one
     * more try after a fast failure), then connected. Explicit Stop, the restart policy being off, a running server, a
     * scheduled native retry or another start in flight leave it alone. [retry] is the person's Try again: it connects
     * now instead of waiting out the automatic reconnect back-off.
     */
    public suspend fun startForLaunch(profile: HealingProfile, retry: Boolean = false) {
        if (!foreground) {
            // Android can report the first resume after the first frame.
            (foregroundWaiter ?: CompletableDeferred<Unit>().also { foregroundWaiter = it }).await()
        }
        if (!idleCurrent(profile, idleEpoch)) return
        val epoch = idleEpoch
        val launch = launchEpoch
        val readyBefore = starter.readyCount
        check(profile)
        if (!idleCurrent(profile, epoch)) return
        // Once per app process. A healing restart in that check that brought it up is not a launch.
        if (!launchStartUsed) {
            launchStartUsed = true
            if (starter.readyCount == readyBefore) launchStart(profile)
        }
        if (launchCurrent(profile, launch)) connectIfNeeded(profile, automatic = !retry)
    }

    private suspend fun launchStart(profile: HealingProfile) {
        if (starter.failureFor(profile.id)?.problem == StartProblem.ComponentRecovery) return
        val epoch = launchEpoch
        // The recovery owner may have just tried and failed: then the launch start is the one more try.
        var tries = if (starter.failureFor(profile.id) == null) 2 else 1
        while (tries > 0 && launchStartWanted(profile)) {
            if (!launchCurrent(profile, epoch)) return
            tries--
            val failure =
                starter.startForLaunch(profile, admission = { launchAdmission(profile) }, stillWanted = { launchCurrent(profile, epoch) })
            if (!launchCurrent(profile, epoch)) return
            if (failure == null) {
                recovery.check(profile)
                break
            }
            if (!failure.retryable || tries == 0) break
            delay(launchRetryDelay)
        }
    }

    /** The facts every launch step repeats (Dart `mayAct` / `_mayLaunch`). */
    private fun mayLaunch(profile: HealingProfile): Boolean =
        !disposed &&
            foreground &&
            connection.isReadable(profile.id) &&
            owner?.id == profile.id &&
            idle.failure == null &&
            connection.selectedProfileId() == profile.id &&
            !connection.hasConnectedServer() &&
            connection.allows(profile.id, AutomationBehavior.RestartPhoneServer)

    private suspend fun launchStartWanted(profile: HealingProfile): Boolean {
        val epoch = idleEpoch
        val mayAct = { epoch == idleEpoch && !starter.starting && mayLaunch(profile) }
        if (!mayAct()) return false
        val first = host.statusOrNull() ?: return false
        if (!mayAct()) return false
        var step = LaunchStartRules.firstLook(first, clock.now())
        while (true) {
            when (val s = step) {
                is LaunchStep.Leave -> return false
                LaunchStep.Start -> return true
                is LaunchStep.Probe -> {
                    val answer = host.probe(profile.password)
                    if (!mayAct()) return false
                    step = LaunchStartRules.afterProbe(answer, host.statusOrNull(), clock.now(), s.until)
                    if (step is LaunchStep.Start && !mayAct()) return false
                    if (step is LaunchStep.Probe) delay(launchPollInterval)
                }
            }
        }
    }

    /** The native intent generation an opening start may act on, read just before it; null when Stop came first. */
    private suspend fun launchAdmission(profile: HealingProfile): Long? {
        if (!mayLaunch(profile)) return null
        val admission: RuntimeStatus = host.statusOrNull() ?: return null
        if (!mayLaunch(profile) || !admission.serverRestartWanted || admission.idleBlocksLaunch) return null
        return admission.serverRecoveryGeneration
    }

    /** Opening and confirmed recovery share one reconnect attempt per start; a failed one backs off when automatic. */
    public suspend fun connectIfNeeded(profile: HealingProfile, automatic: Boolean = true) {
        if (disposed ||
            !foreground ||
            connection.selectedProfileId() != profile.id ||
            connection.hasConnectedServer() ||
            starter.failureFor(profile.id) != null ||
            (automatic && !connection.allows(profile.id, AutomationBehavior.Reconnect))
        ) {
            return
        }
        if (connecting) return
        val attempt = "${profile.id}:${starter.readyCount}"
        if (lastConnectionAttempt == attempt) {
            val failedAt = connectFailedAt ?: return
            if (automatic) {
                val wait = reconnectBackoff * (1 shl (connectFailures - 1).coerceIn(0, 6))
                val capped = if (wait > MAX_RECONNECT_BACKOFF) MAX_RECONNECT_BACKOFF else wait
                if (clock.now() - failedAt < capped) return
            }
        } else {
            connectFailedAt = null
            connectFailures = 0
        }
        lastConnectionAttempt = attempt
        connecting = true
        val errored =
            try {
                connection.connect(profile.id)
            } finally {
                connecting = false
            }
        val failed = !disposed && connection.selectedProfileId() == profile.id && !connection.hasConnectedServer() && errored
        if (failed) {
            connectFailedAt = clock.now()
            connectFailures++
        } else {
            connectFailedAt = null
            connectFailures = 0
        }
    }

    public fun dispose() {
        disposed = true
        idleEpoch++
        launchEpoch++
        observationRevision++
        idle.dispose()
        foregroundWaiter?.complete(Unit)
        foregroundWaiter = null
        starter.beforeManualStart = null
        recovery.onPhase = null
        agentWork.dispose()
        recovery.dispose()
    }

    public companion object {
        public val MAX_RECONNECT_BACKOFF: Duration = 60.seconds
        private const val NONE = "none"
        private val IDLE_RESUME_ABSENT: Outcome<Unit> =
            Outcome.Failed(io.github.eslamasabry.oc.core.common.Failure.Runtime(io.github.eslamasabry.oc.core.common.RuntimeProblem.IdleResumeUnavailable))
    }
}
