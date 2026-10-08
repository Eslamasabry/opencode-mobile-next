package io.github.eslamasabry.opencode_mobile
import android.content.Context
import java.io.File
class BuiltinLinux private constructor(context: Context) {
    val home: File = context.filesDir
    val installed = true
    enum class InstallStage { DOWNLOAD, UNPACK }
    interface InstallProgress {
        fun stage(which: InstallStage)
        fun bytes(done: Long, total: Long)
        fun log(line: String)
        val cancelled: Boolean
    }
    fun install(progress: InstallProgress) {}
    fun start(script: String, directory: String?, agentUser: Boolean = false): Process = ProcessBuilder("sh", "-c", script).start()
    internal fun startInstaller(script: String, targets: Set<InstallerTarget>, operation: InstallerOperation, agentUser: Boolean = false): Process {
        check(operation == InstallerOperation.INSTALL && targets.isNotEmpty())
        installerStarts++
        return ProcessBuilder("sh", "-c", script).start()
    }
    fun finishInstaller(process: Process) {
        check(process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS))
        installerFinishes++
    }
    fun stopInstaller(process: Process) { process.destroyForcibly() }
    companion object {
        const val TAG = "test"
        const val VERSION = "test"
        var installerStarts = 0
        var installerFinishes = 0
        fun get(context: Context) = BuiltinLinux(context)
        fun stopTree(process: Process, graceMs: Long = 0) { process.destroyForcibly() }
    }
}
object SetupService {
    var denyStart = false
    var denyFinish = false
    var denyUpdate = false
    var updateAttempts = 0
    var finishAttempts = 0
    fun start(context: Context, channel: String, title: String, text: String) {
        if (denyStart) throw SecurityException("notification denied")
    }
    fun stop(context: Context) {}
    fun finish(context: Context, channel: String, title: String, done: Boolean) {
        finishAttempts++
        if (denyFinish) throw SecurityException("notification denied")
    }
    fun update(context: Context, channel: String, title: String, text: String) {
        updateAttempts++
        if (denyUpdate) throw SecurityException("notification denied")
    }
}
