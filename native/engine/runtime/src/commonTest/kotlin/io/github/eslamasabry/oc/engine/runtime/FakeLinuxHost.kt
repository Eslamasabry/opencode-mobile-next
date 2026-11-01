package io.github.eslamasabry.oc.engine.runtime

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.runtime.budget.BudgetReadings
import io.github.eslamasabry.oc.engine.runtime.heat.BatteryReading
import io.github.eslamasabry.oc.engine.runtime.heat.ThermalReading
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeCall
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeProblem
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import io.github.eslamasabry.oc.engine.runtime.host.done
import io.github.eslamasabry.oc.engine.runtime.host.refused
import io.github.eslamasabry.oc.engine.runtime.server.LaunchMode
import io.github.eslamasabry.oc.engine.runtime.server.PhoneServerPort
import io.github.eslamasabry.oc.engine.runtime.server.ProbeAnswer
import kotlin.time.Duration

/**
 * A phone's built-in Linux in memory: the process table count, the thermal and battery readings, and one OpenCode
 * server with the native intent (wanted, user-stopped, generation). Every call is recorded; nothing touches a device.
 */
class FakeLinuxHost : BudgetReadings, PhoneServerPort {
    // Budget readings.
    var processCount: Int? = 0
    var limitLifted = false
    var thermal = ThermalReading.Unknown
    var battery = BatteryReading.Unknown
    var runningAgents = 0
    var reads = 0
        private set

    override suspend fun processCount(): Int? = processCount.also { reads++ }

    override suspend fun limitLifted(): Boolean = limitLifted

    override suspend fun thermal(): ThermalReading = thermal

    override suspend fun battery(): BatteryReading = battery

    override suspend fun runningAgents(): Int = runningAgents

    // The phone server and its native intent.
    var installed = true
    var running = false
    var wanted = true
    var userStopped = false
    var generation = 1L
    var uptime: Duration? = null
    var recoveryAuthority = false
    var recoveryScheduled = false
    var idle = RuntimeStatus()

    /** The started server dies before it answers. */
    var dies = false

    /** The started server answers our password. */
    var answersAfterStart = true

    /** The running server rejects our password (another password, a stale process). */
    var rejectsPassword = false

    /** The process that runs now does not answer (hung, still booting); a fresh launch answers. */
    var silent = false
    var launchRefusal: RuntimeProblem? = null
    var passwordRefused = false
    var historyMoveRefused = false
    var duringPasswordWrite: (() -> Unit)? = null
    var duringProbe: (() -> Unit)? = null

    val calls = mutableListOf<String>()
    val launches = mutableListOf<LaunchMode>()
    val passwords = mutableListOf<String>()
    var probes = 0
        private set

    val status: RuntimeStatus
        get() =
            idle.copy(
                installed = installed,
                serverRunning = running,
                serverRestartWanted = wanted && !userStopped,
                serverRecoveryGeneration = generation,
                serverRecoveryAuthority = recoveryAuthority,
                serverRecoveryScheduled = recoveryScheduled,
                serverUptime = uptime,
            )

    /** What a person's Stop does natively (plan 11 §6.7): revisions move, intent cleared, process drained. */
    fun personStops() {
        generation++
        wanted = false
        userStopped = true
        running = false
    }

    override suspend fun checkHistoryMove(): RuntimeCall<Unit> {
        calls += "historyCheck"
        return if (historyMoveRefused) RuntimeProblem.Generic.refused() else Unit.done()
    }

    override suspend fun writePassword(password: String): RuntimeCall<Unit> {
        calls += "password"
        passwords += password
        duringPasswordWrite?.invoke()
        return if (passwordRefused) RuntimeProblem.Generic.refused() else Unit.done()
    }

    override suspend fun launch(profile: ProfileId, mode: LaunchMode): RuntimeCall<Unit> {
        calls += "launch"
        launches += mode
        launchRefusal?.let { return it.refused() }
        val expected =
            when (mode) {
                is LaunchMode.ForAppOpen -> mode.expectedGeneration
                is LaunchMode.Recovery -> mode.expectedGeneration
                is LaunchMode.AfterUpdate -> mode.expectedGeneration
                LaunchMode.Manual -> null
            }
        // The native side refuses a guarded launch once a Stop moved the generation (freeze tip).
        if (expected != null && expected != generation) return RuntimeProblem.Generic.refused()
        if (mode == LaunchMode.Manual) {
            wanted = true
            userStopped = false
        }
        running = !dies && !(binary == "new" && newBinaryBroken)
        rejectsPassword = false
        silent = false
        uptime = Duration.ZERO
        return Unit.done()
    }

    override suspend fun probe(password: String): ProbeAnswer {
        probes++
        duringProbe?.invoke()
        return when {
            !running -> ProbeAnswer.NoAnswer
            rejectsPassword -> ProbeAnswer.NeedsPassword
            silent -> ProbeAnswer.NoAnswer
            !answersAfterStart -> ProbeAnswer.NoAnswer
            else -> ProbeAnswer.Answered
        }
    }

    override suspend fun refreshStatus(): RuntimeCall<RuntimeStatus> = status.done()

    val confirmed = mutableListOf<Long>()

    override suspend fun confirmRecovery(expectedGeneration: Long): RuntimeCall<Unit> {
        confirmed += expectedGeneration
        return Unit.done()
    }

    val launchCount: Int get() = launches.size

    // The OpenCode component journal: `*.oc-pending` (staged), the committed binary, `*.oc-good` (the last good one).
    var binary = "old"
    var pendingVersion: String? = null
    var newBinaryBroken = false
    var activationRefused = false
    val journal = mutableListOf<String>()

    fun stage(version: String) {
        pendingVersion = version
    }
}

class FakeJournal(private val host: FakeLinuxHost) : io.github.eslamasabry.oc.engine.runtime.server.ComponentJournal {
    override suspend fun activate(): RuntimeCall<String> {
        host.journal += "activate"
        val version = host.pendingVersion ?: return RuntimeProblem.Generic.refused()
        if (host.activationRefused) return RuntimeProblem.Generic.refused()
        host.binary = "new"
        host.pendingVersion = null
        return version.done()
    }

    override suspend fun rollBack(): RuntimeCall<Unit> {
        host.journal += "rollBack"
        host.binary = "old"
        return Unit.done()
    }
}
