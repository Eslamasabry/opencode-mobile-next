package io.github.eslamasabry.oc.android.agenthost

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Port of the Flutter harness stub `test/native/phone_agent_host_stubs/linux.kt`: the phone's Linux as the kept host
 * code sees it, with real `/bin/sh` children standing in for the guest programs.
 */
class FakeAgentLinux(
    override val home: File,
    var script: String = "read password; exec sleep 10",
) : AgentLinux {
    override val rootfs = File(home, "ubuntu")
    override val installed = true
    override val primaryAbi = "x86_64"
    var holdStart = false
    val startEntered = CountDownLatch(1)
    val releaseStart = CountDownLatch(1)
    @Volatile var configWrites = 0
    @Volatile var launches = 0
    var tracked = 0
    var helperVersion = "0.9.2"
    var versionCalls = 0
    var daemonStops = 0
    var otherPrivateProcess = false
    var unowned = false
    var portAnswers = false
    private var trackedProcess: Process? = null

    override fun privateAgentDiagnostics(name: String): Map<String, Any?> = emptyMap()

    override fun writeAgentConfig(profile: String, config: String) {
        configWrites++
    }

    override fun clearStaleAgentLoginLock(profile: String) = Unit

    override fun agentPaseoHome(profile: String) = File(home, "profiles/$profile/paseo")

    override fun agentPortAnswers(port: Int) = portAnswers

    override fun startAgentProcess(profile: String, args: List<String>, foreground: Boolean, privateOutput: Boolean): Process {
        if (args == listOf("/home/oc/.local/bin/paseo", "--version")) {
            versionCalls++
            return ProcessBuilder("/bin/sh", "-c", "printf '%s' '$helperVersion'").start()
        }
        launches++
        startEntered.countDown()
        if (holdStart) check(releaseStart.await(5, TimeUnit.SECONDS))
        val shell = if (File("/system/bin/sh").canExecute()) "/system/bin/sh" else "/bin/sh"
        return ProcessBuilder(shell, "-c", script).start()
    }

    override fun startSignInProcess(profile: String, args: List<String>, foreground: Boolean): Process =
        startAgentProcess(profile, args, foreground)

    override fun trackPrivateAgentService(name: String, child: Process, port: Int?) {
        tracked++
        trackedProcess = child
    }

    override fun requireAgentHostUpdateIdle(profile: String, process: Process?) {
        if (unowned || (process != null && process !== trackedProcess)) throw PhoneAgentHost.Failure("agent_host_unowned")
        if (otherPrivateProcess) throw PhoneAgentHost.Failure("agent_host_busy")
    }

    override fun stopAgentProcess(child: Process) {
        if (child === trackedProcess && child.isAlive) daemonStops++
        child.destroyForcibly()
        child.waitFor()
    }

    override fun stopService(name: String) = Unit

    override fun blockAgentProfile(profile: String) = Unit

    override fun deleteAgentHome(profile: String) = Unit

    override val agentSignIn: PhoneAgentSignIn by lazy { PhoneAgentSignIn(this) }
}
