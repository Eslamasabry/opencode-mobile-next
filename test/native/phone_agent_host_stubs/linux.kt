package io.github.eslamasabry.opencode_mobile
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
class BuiltinLinux(val home: File, var script: String = "read password; exec sleep 10") {
    var holdStart = false
    val startEntered = CountDownLatch(1)
    val releaseStart = CountDownLatch(1)
    @Volatile var configWrites = 0
    @Volatile var launches = 0
    var tracked = 0
    val installed = true
    fun writeAgentConfig(profile: String, config: String) { configWrites++ }
    fun clearStaleAgentLoginLock(profile: String) {}
    fun startAgentProcess(profile: String, args: List<String>): Process {
        launches++
        startEntered.countDown()
        if (holdStart) check(releaseStart.await(5, TimeUnit.SECONDS))
        val shell = if (File("/system/bin/sh").canExecute()) "/system/bin/sh" else "/bin/sh"
        return ProcessBuilder(shell, "-c", script).start()
    }
    fun trackPrivateAgentService(name: String, child: Process, port: Int) { tracked++ }
    fun stopAgentProcess(child: Process) { child.destroyForcibly(); child.waitFor() }
    fun stopService(name: String) {}
    fun blockAgentProfile(profile: String) {}
    fun deleteAgentHome(profile: String) {}
    val agentSignIn = SignIn()
    class SignIn { fun deleteProfile(profile: String) {} }
}
