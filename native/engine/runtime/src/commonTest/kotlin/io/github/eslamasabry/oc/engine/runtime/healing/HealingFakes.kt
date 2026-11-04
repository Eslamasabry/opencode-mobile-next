package io.github.eslamasabry.oc.engine.runtime.healing

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.runtime.host.RuntimePhase
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import io.github.eslamasabry.oc.engine.runtime.host.refused
import io.github.eslamasabry.oc.engine.runtime.server.LaunchMode
import io.github.eslamasabry.oc.engine.runtime.server.PhoneServerStarter
import io.github.eslamasabry.oc.engine.runtime.server.ProbeAnswer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The test's `_Linux` / `_NativeHealingLinux` / `_DelayedNativeBindingLinux` (test/phone_server_healing_test.dart). */
class FakeHealingHost : HealingHost {
    var running = false
    var healthy = true
    var wanted = true
    var dies = false
    var generation = 0L
    var rejects = false
    var answersAfterStart = true
    var uptime: Duration? = null
    var startGate: CompletableDeferred<Unit>? = null
    val startEntered = CompletableDeferred<Unit>()

    var idleSupported = false
    var idleValid = true
    var idleStopped = false
    var helperStopped = false
    var idleGeneration = 0L
    var idleResumes = 0
    var idleCompletes = 0
    var idleGate: CompletableDeferred<Unit>? = null
    val idleEntered = CompletableDeferred<Unit>()

    var authority = false
    var scheduled = false
    var bindingGate: CompletableDeferred<Unit>? = null
    val bindingRequested = CompletableDeferred<Unit>()
    var boundOwner: String? = null
    var restorationArmedOnStart = false

    val launches = mutableListOf<LaunchMode>()
    val observations = mutableListOf<Pair<String, Boolean?>>()
    val agentHolds = mutableListOf<Triple<String, String, Boolean>>()
    var statusCalls = 0

    /** Person or app-open starts (`startServer`). */
    val starts: Int get() = launches.count { it !is LaunchMode.Recovery }

    /** Automatic crash restarts (`restartServer`). */
    val restarts: Int get() = launches.count { it is LaunchMode.Recovery }

    fun status(): RuntimeStatus =
        RuntimeStatus(
            installed = true,
            phase = RuntimePhase.Ready,
            serverRunning = running,
            serverRestartWanted = wanted,
            serverRecoveryGeneration = generation,
            serverRecoveryAuthority = authority,
            serverRecoveryScheduled = scheduled,
            serverUptime = if (running) uptime else null,
            serverIdlePolicySupported = idleSupported,
            serverIdleEnabled = idleSupported,
            serverIdleMinutes = if (idleSupported) 5 else null,
            serverIdleStopped = idleStopped,
            serverIdleHelperStopped = helperStopped,
            serverIdleGeneration = if (idleSupported) idleGeneration else null,
            serverIdleReceiptValid = idleValid,
        )

    override suspend fun refreshStatus(): Outcome<RuntimeStatus> {
        statusCalls++
        return status().ok()
    }

    override suspend fun checkHistoryMove(): Outcome<Unit> = Unit.ok()

    override suspend fun writePassword(password: String): Outcome<Unit> = Unit.ok()

    override suspend fun launch(profile: ProfileId, mode: LaunchMode): Outcome<Unit> {
        when (mode) {
            is LaunchMode.Recovery -> {
                if (!wanted || generation != mode.expectedGeneration) return RuntimeProblem.Generic.refused()
                launches += mode
                running = !dies
                return Unit.ok()
            }
            is LaunchMode.ForAppOpen -> if (!wanted || generation != mode.expectedGeneration) return RuntimeProblem.Generic.refused()
            else -> Unit
        }
        launches += mode
        if (!startEntered.isCompleted) startEntered.complete(Unit)
        restorationArmedOnStart = boundOwner == profile.raw
        startGate?.await()
        wanted = true
        running = !dies
        healthy = answersAfterStart
        rejects = false
        uptime = Duration.ZERO
        return Unit.ok()
    }

    override suspend fun probe(password: String): ProbeAnswer =
        when {
            running && rejects -> ProbeAnswer.NeedsPassword
            running && healthy -> ProbeAnswer.Answered
            else -> ProbeAnswer.NoAnswer
        }

    override suspend fun confirmRecovery(expectedGeneration: Long): Outcome<Unit> =
        if (!wanted || !running || expectedGeneration != generation) RuntimeProblem.Generic.refused() else Unit.ok()

    override suspend fun cancelRecovery(): Outcome<Unit> {
        generation++
        return Unit.ok()
    }

    override suspend fun bindRecovery(profileId: String, enabled: Boolean): Outcome<Int> {
        if (!bindingRequested.isCompleted) bindingRequested.complete(Unit)
        bindingGate?.await()
        boundOwner = if (enabled) profileId else null
        return 3.ok()
    }

    override suspend fun resumeIdleStopped(profileId: String, expectedIdleGeneration: Long): Outcome<RuntimeStatus> {
        idleResumes++
        if (!idleEntered.isCompleted) idleEntered.complete(Unit)
        idleGate?.await()
        if (!wanted || profileId != "phone" || idleGeneration != expectedIdleGeneration) return RuntimeProblem.Generic.refused()
        running = true
        idleStopped = false
        return status().ok()
    }

    override suspend fun completeIdleResume(profileId: String, expectedIdleGeneration: Long): Outcome<RuntimeStatus> {
        if (!wanted || profileId != "phone" || idleGeneration != expectedIdleGeneration) return RuntimeProblem.Generic.refused()
        idleCompletes++
        if (helperStopped) idleGeneration = 0
        helperStopped = false
        return status().ok()
    }

    override suspend fun observeAgentWork(profileId: String, busy: Boolean?): Outcome<Unit> {
        observations += profileId to busy
        return Unit.ok()
    }

    override suspend fun setAgentWorkLease(profileId: String, leaseId: String, on: Boolean, hold: Duration): Outcome<LeaseAnswer> {
        agentHolds += Triple(profileId, leaseId, on)
        return LeaseAnswer(held = on).ok()
    }
}

/** The test's `_Connection` and `_Store`: profiles, the owner pointer, policy switches and connect attempts. */
class FakeHealingConnection : HealingConnection {
    val saved = mutableListOf<HealingProfile>()
    val blocked = mutableSetOf<String>()
    val prefs = mutableMapOf<String, String>()
    var activeId: String? = "phone"
    var shownStatus = LinkStatus.Connected
    val connections = mutableListOf<String>()
    var refuseConnects = 0
    val acts = mutableListOf<Pair<String, String>>()
    val policy = mutableMapOf<Pair<String, AutomationBehavior>, Boolean>()

    override fun inAppProfiles(): List<HealingProfile> = saved.toList()

    override fun isReadable(profileId: String): Boolean = profileId !in blocked && saved.any { it.id == profileId }

    override fun runtimeOwner(): String? = prefs[OWNER_KEY]

    override suspend fun claimRuntimeOwner(profileId: String): Boolean {
        prefs[OWNER_KEY] = profileId
        return true
    }

    override fun agentOwnerId(profileId: String): String = prefs["oc.phoneAgentOwner.$profileId"] ?: profileId

    override fun selectedProfileId(): String? = activeId

    override fun status(): LinkStatus = shownStatus

    override fun hasConnectedServer(): Boolean = false

    override suspend fun connect(profileId: String): Boolean {
        connections += profileId
        // A busy server right after boot: the connect times out.
        if (refuseConnects > 0) {
            refuseConnects--
            return true
        }
        return false
    }

    override suspend fun recordRestart(profileId: String, eventId: String, atEpochMillis: Long): Boolean {
        if (!isReadable(profileId)) return false
        if (acts.none { it.second == eventId }) acts += profileId to eventId
        return true
    }

    override fun allows(profileId: String, behavior: AutomationBehavior): Boolean = policy[profileId to behavior] ?: true

    companion object {
        const val OWNER_KEY = "oc.builtinServerOwner"
    }
}

class FakeBudgets : RecoveryBudgetStore {
    val saved = mutableMapOf<String, RecoveryBudget>()

    override fun read(profileId: String): RecoveryBudget? = saved[profileId] ?: RecoveryBudget()

    override suspend fun write(profileId: String, budget: RecoveryBudget): Boolean {
        saved[profileId] = budget
        return true
    }
}

/** Wall time that follows the test scheduler (so `delay` moves it), plus a manual [extra]. */
class SchedulerClock(private val scheduler: TestCoroutineScheduler) : Clock {
    var extra: Duration = Duration.ZERO

    override fun now(): Instant = Instant.fromEpochMilliseconds(1_700_000_000_000L + scheduler.currentTime) + extra
}

/** One test's world (the Dart `setUp` and `bind`). */
class HealingWorld(private val scope: CoroutineScope, scheduler: TestCoroutineScheduler) {
    val clock = SchedulerClock(scheduler)
    val host = FakeHealingHost()
    val connection = FakeHealingConnection()
    val budgets = FakeBudgets()
    val starter = HealingStarter(PhoneServerStarter(host, clock, readyTimeout = Duration.ZERO, pollInterval = Duration.ZERO))
    val phone = HealingProfile("phone", "synthetic-password")
    private var leases = 0

    init {
        connection.saved += phone
    }

    fun profile(id: String): HealingProfile = HealingProfile(id, "synthetic-password")

    fun bind(
        localAgentWorkBusy: ((String) -> Boolean?)? = null,
        resumeAgentsAfterIdle: (suspend (String, Long) -> Outcome<Unit>)? = null,
        reconnectBackoff: Duration = 10.seconds,
    ): PhoneServerHealing =
        PhoneServerHealing(
            connection = connection,
            host = host,
            starter = starter,
            recovery = ServerRecovery(host, connection, starter, budgets, clock, scope),
            scope = scope,
            clock = clock,
            localAgentWorkBusy = localAgentWorkBusy,
            resumeAgentsAfterIdle = resumeAgentsAfterIdle,
            newLeaseId = { "agent.lease${++leases}" },
            reconnectBackoff = reconnectBackoff,
        )

    /** `idleReceipt`: a native idle stop of generation 9 over an exhausted legacy budget. */
    fun idleReceipt(helper: Boolean = true) {
        host.idleSupported = true
        host.idleStopped = true
        host.helperStopped = helper
        host.idleGeneration = 9
        budgets.saved[phone.id] = RecoveryBudget(attempts = 3)
        connection.activeId = phone.id
    }
}
