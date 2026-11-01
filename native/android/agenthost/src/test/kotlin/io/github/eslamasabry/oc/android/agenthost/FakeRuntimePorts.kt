package io.github.eslamasabry.oc.android.agenthost

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.util.IdentityHashMap

/** The runtime as [AgentProcesses] sees it, in memory, with real `/bin/sh` children for the guest programs. */
class FakeRuntimePorts(val files: File) {
    val locks = AgentLocks(Any(), Any())
    var installed = true
    val launched = mutableListOf<Pair<ProcessOwner, List<String>>>()
    val rootOwners = mutableListOf<ProcessOwner>()
    val rootPids = mutableMapOf<ProcessOwner, () -> Collection<Int>>()
    /** The guest program each launch runs, by its last argv word. */
    var guestScript: (List<String>) -> String = { "exec sleep 10" }

    val launcher = object : GuestLauncher {
        override fun prootCommand(program: List<String>, agentUser: Boolean): List<String> =
            listOf("proot", "--change-id=1000:1000".takeIf { agentUser } ?: "--root-id") + program

        override fun environment(): Map<String, String> = mapOf("PROOT_TMP_DIR" to files.path)

        override fun startRegistered(owner: ProcessOwner, command: List<String>, privateOutput: Boolean): Process {
            launched += owner to command
            return ProcessBuilder("/bin/sh", "-c", guestScript(command)).apply {
                if (privateOutput) redirectError(File("/dev/null")) else redirectErrorStream(true)
            }.start()
        }
    }

    val registry = object : GuestProcessRegistry {
        override fun register(owner: ProcessOwner, process: Process) = Unit

        override fun unregister(process: Process) = Unit

        override fun registerRootPids(owner: ProcessOwner, pids: () -> Collection<Int>) {
            rootOwners += owner
            rootPids[owner] = pids
        }
    }

    val serviceMap = mutableMapOf<String, Process>()
    val stopRequested: MutableSet<Process> = java.util.Collections.newSetFromMap(IdentityHashMap())
    val exits = mutableListOf<String>()
    var foregroundRefuses = false
    var forgotten = mutableListOf<Set<String>>()
    var forgetCommits = true

    val services = object : AgentServiceTable {
        override fun process(name: String): Process? = synchronized(serviceMap) { serviceMap[name] }

        override fun names(): Set<String> = synchronized(serviceMap) { serviceMap.keys.toSet() }

        override fun start(name: String, process: Process, port: Int?) {
            synchronized(serviceMap) { serviceMap[name] = process }
            if (foregroundRefuses) error("foreground service refused")
        }

        override fun exited(name: String, process: Process) {
            synchronized(serviceMap) {
                if (serviceMap[name] === process) {
                    serviceMap.remove(name)
                    exits += name
                }
            }
        }

        override fun removeStopped(name: String) {
            synchronized(serviceMap) { serviceMap.remove(name) }
        }

        override fun requestStop(process: Process) {
            stopRequested += process
        }

        override fun stop(name: String) {
            synchronized(serviceMap) { serviceMap.remove(name) }?.destroyForcibly()
        }

        override fun diagnostics(name: String): Map<String, Any?> = mapOf("running" to (process(name)?.isAlive == true))

        override fun forgetDiagnostics(names: Set<String>): Boolean {
            forgotten += names
            return forgetCommits
        }
    }

    @Volatile var state = AgentRuntimeState(
        idleAvailable = true, activityResumed = true, activityEpoch = 1, wantedRevision = 1,
        supervisionProfile = "owner", restartWanted = true, userStopped = false, supervisionEnabled = true,
        idleServerLive = true, serverRunning = true, idle = AgentIdleFacts(),
    )
    var helperOwnerOf: (String) -> String? = { null }
    val clearedGates = mutableListOf<Pair<String, Long>>()

    val runtime = object : AgentRuntimeFacts {
        override fun state(): AgentRuntimeState = state

        override fun migrationMarkerValid(owner: String) = true

        override fun policyPermits(owner: String) = true

        override fun helperOwner(owner: String): String? = helperOwnerOf(owner)

        override fun clearCompletedHelperGate(owner: String, generation: Long): Boolean {
            clearedGates += owner to generation
            return true
        }
    }

    val authorized = mutableListOf<Long>()
    val signInLeases = mutableListOf<Process>()

    val work = object : AgentWorkLeases {
        override fun foregroundGeneration(): Long = 42

        override fun authorizeForegroundWork(generation: Long) {
            authorized += generation
        }

        override fun trackSignIn(process: Process) {
            signInLeases += process
        }
    }

    val killed = mutableListOf<Int>()

    val control = object : AgentProcessControl {
        override fun pidOf(process: Process): Int? = GuestProc.pid(process)

        override fun descendants(root: Int): Collection<Int> = GuestProc.descendants(root)

        override fun stopTree(process: Process) {
            GuestProc.pid(process)?.let { root -> GuestProc.descendants(root).forEach(GuestProc::kill) }
            process.destroyForcibly()
            process.waitFor()
        }

        override fun kill(pid: Int) {
            killed += pid
            GuestProc.kill(pid)
        }

        override fun startToken(pid: Int): String? = GuestProc.startToken(pid)
    }

    val authRuns = mutableListOf<Map<*, *>>()
    var authAnswer: Map<String, Any?> = mapOf("state" to "signedOut")
    val blockedAuth = mutableListOf<String>()

    val modes = RecordingModes()

    class RecordingModes : FileModes {
        val modesSet = mutableMapOf<String, Int>()

        override fun chmod(path: String, mode: Int) {
            modesSet[path] = mode
            val perms = mutableSetOf<PosixFilePermission>()
            if (mode and 256 != 0) perms += PosixFilePermission.OWNER_READ
            if (mode and 128 != 0) perms += PosixFilePermission.OWNER_WRITE
            if (mode and 64 != 0) perms += PosixFilePermission.OWNER_EXECUTE
            Files.setPosixFilePermissions(File(path).toPath(), perms)
        }

        override fun realDirectory(file: File): Boolean? = try {
            val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            attributes.isDirectory && !attributes.isSymbolicLink
        } catch (_: Exception) {
            null
        }
    }

    fun processes(): AgentProcesses = AgentProcesses(
        files = files,
        installedNow = { installed },
        locks = locks,
        launcher = launcher,
        registry = registry,
        services = services,
        runtime = runtime,
        work = work,
        control = control,
        privateAuth = {
            object : PrivateAgentAuth {
                override fun run(arguments: Map<*, *>): Map<String, Any?> {
                    authRuns += arguments
                    return authAnswer
                }

                override fun blockProfile(profileId: String): Boolean {
                    blockedAuth += profileId
                    return true
                }
            }
        },
        modes = modes,
        abi = { "x86_64" },
    )
}

/** Linux /proc reads for the JVM tests (the android.jar Process has no pid()). */
object GuestProc {
    fun pid(process: Process): Int? = try {
        (Process::class.java.getMethod("pid").invoke(process) as Long).toInt()
    } catch (_: Exception) {
        null
    }

    private fun stat(pid: Int): List<String>? = try {
        File("/proc/$pid/stat").readText().substringAfterLast(") ").split(' ')
    } catch (_: Exception) {
        null
    }

    /** Field 22 (start time), as `BuiltinLinux.stopAgentProcess` read it; null for a gone or zombie process. */
    fun startToken(pid: Int): String? = stat(pid)?.takeIf { it.getOrNull(0) != "Z" }?.getOrNull(19)

    fun descendants(root: Int): List<Int> {
        val parents = File("/proc").listFiles().orEmpty().mapNotNull { dir ->
            val pid = dir.name.toIntOrNull() ?: return@mapNotNull null
            stat(pid)?.getOrNull(1)?.toIntOrNull()?.let { pid to it }
        }
        val below = mutableListOf<Int>()
        var frontier = listOf(root)
        while (frontier.isNotEmpty()) {
            frontier = parents.filter { (_, parent) -> parent in frontier }.map { it.first }
            below += frontier
        }
        return below
    }

    fun alive(pid: Int): Boolean = startToken(pid) != null

    fun kill(pid: Int) {
        ProcessBuilder("kill", "-9", pid.toString()).start().waitFor()
    }
}
