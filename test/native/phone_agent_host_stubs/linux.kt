package io.github.eslamasabry.opencode_mobile
import java.io.File
class BuiltinLinux(val home: File, var script: String = "read password; sleep 10") {
    var configWrites = 0
    var launches = 0
    var tracked = 0
    val installed = true
    fun writeAgentConfig(profile: String, config: String) { configWrites++ }
    fun clearStaleAgentLoginLock(profile: String) {}
    fun startAgentProcess(profile: String, args: List<String>): Process {
        launches++
        return ProcessBuilder("/bin/sh", "-c", script).start()
    }
    fun trackPrivateAgentService(name: String, child: Process, port: Int) { tracked++ }
    fun stopAgentProcess(child: Process) { child.destroyForcibly(); child.waitFor() }
    fun stopService(name: String) {}
    fun blockAgentProfile(profile: String) {}
    fun deleteAgentHome(profile: String) {}
    val agentSignIn = SignIn()
    class SignIn { fun deleteProfile(profile: String) {} }
}
