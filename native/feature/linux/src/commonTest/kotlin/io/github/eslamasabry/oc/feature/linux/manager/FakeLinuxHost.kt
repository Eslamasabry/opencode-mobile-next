package io.github.eslamasabry.oc.feature.linux.manager

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.feature.linux.host.LinuxHost
import io.github.eslamasabry.oc.feature.linux.host.LinuxPresence
import io.github.eslamasabry.oc.feature.linux.host.ShellRun
import kotlinx.coroutines.delay

/** The app graph's retry pause, on the test's virtual clock. */
val VirtualRetryWait: LinuxRetryWait = LinuxRetryWait { delay(LinuxManager.RETRY_DELAY) }

/** A scripted Linux: answers each run from [answer], records every script, and keeps one service table. */
class FakeLinuxHost(
    var presence: LinuxPresence = LinuxPresence.Ready,
    var answer: (String) -> Outcome<ShellRun> = { Outcome.Ok(ShellRun(0, "")) },
) : LinuxHost {
    val scripts = mutableListOf<String>()
    val failures = ArrayDeque<Failure>()
    var setup = false
    val running = mutableSetOf<String>()
    val logs = mutableMapOf<String, String>()
    val started = mutableListOf<Pair<String, String>>()
    var startFailure: Failure? = null

    override suspend fun presence(): LinuxPresence = presence

    override suspend fun run(script: String, timeoutSeconds: Long): Outcome<ShellRun> {
        scripts += script
        failures.removeFirstOrNull()?.let { return Outcome.Failed(it) }
        return answer(script)
    }

    override suspend fun setupRunning(): Boolean = setup

    override suspend fun serviceRunning(name: String): Boolean = name in running

    override suspend fun startService(name: String, script: String, notice: String): Outcome<Unit> {
        startFailure?.let { return Outcome.Failed(it) }
        started += name to notice
        running += name
        logs[name] = script.lineSequence().firstOrNull { it.startsWith("printf '::oc job ") }.orEmpty()
        return Outcome.Ok(Unit)
    }

    override suspend fun stopService(name: String): Outcome<Unit> {
        running -= name
        return Outcome.Ok(Unit)
    }

    override suspend fun serviceLog(name: String, tailBytes: Int): String = logs[name].orEmpty()

    companion object {
        val ownership: Failure = Failure.Runtime(RuntimeProblem.OwnershipUnknown)
        val busySlot: Failure = Failure.Runtime(RuntimeProblem.SlotBusy)
        val notInstalled: Failure = Failure.Runtime(RuntimeProblem.NotInstalled)

        fun out(text: String): Outcome<ShellRun> = Outcome.Ok(ShellRun(0, text))
    }
}
