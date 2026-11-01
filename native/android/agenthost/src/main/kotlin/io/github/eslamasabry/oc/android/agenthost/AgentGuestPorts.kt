package io.github.eslamasabry.oc.android.agenthost

/*
 * What the agent host needs from the phone's Linux runtime (`:android:linux`), as ports. Plan 11 CR11-0 gives this
 * module exactly two seams into the runtime, [GuestLauncher] and [GuestProcessRegistry]; their shapes below are the
 * plan's (11 §5.4) verbatim, declared in this package only because `ModuleRules` does not allow the
 * `:android:agenthost → :android:linux` edge yet. When it does, these two declarations are deleted and the imports
 * point at `io.github.eslamasabry.oc.android.linux`.
 *
 * The other ports ([AgentLocks], [AgentServiceTable], [AgentRuntimeFacts], [AgentWorkLeases], [AgentProcessControl])
 * are the rest of what the `AgentProcesses` block of `BuiltinLinux` (11 §5.3, lines 745–1079 and 2093–2131) touched:
 * the runtime's two monitors, its supervised-services map, its recovery and idle facts, its work leases and its
 * process-tree helpers. S3-RTSPLIT binds them from the split classes (`ServiceRegistry`, `ServerRecovery`,
 * `IdleController`, `WorkLeaseBridge`, `ProcessInventory`). Each member names the `BuiltinLinux` code it stands for.
 */

/** How :android:agenthost and :android:terminal start guest programs (plan 11 §5.4, CR11-0). */
interface GuestLauncher {
    /** `BuiltinLinux.prootCommand(program, agentUser)`: the full proot argv for [program]. */
    fun prootCommand(program: List<String>, agentUser: Boolean): List<String>

    /** PROOT_LOADER, PROOT_TMP_DIR, LD_LIBRARY_PATH only (`prootEnvironment()`). */
    fun environment(): Map<String, String>

    /**
     * Starts [command] (a [prootCommand] result) with exactly [environment], stderr to `/dev/null` when
     * [privateOutput] else merged into stdout, and registers it in the runtime's process inventory (`processes`,
     * `processConfinement`, orphan reaper), as `startPrivateAgentProcess` + `registerAgentProcess` did.
     */
    fun startRegistered(owner: ProcessOwner, command: List<String>, privateOutput: Boolean): Process
}

/** Inverts today's BuiltinLinux → LocalTerminal / PhoneAgent* calls (they become registrations; plan 11 §5.4). */
interface GuestProcessRegistry {
    fun register(owner: ProcessOwner, process: Process)

    fun unregister(process: Process)

    /** Terminals, agent hosts: the runtime asks for the root pids when it counts or slows agent trees. */
    fun registerRootPids(owner: ProcessOwner, pids: () -> Collection<Int>)
}

enum class ProcessOwner { App, OpenCode, Paseo, AgentSignIn, Engine, Helper, Task, Terminal, Orphan, Other }

/**
 * The runtime's one lock family (S11-2, `RuntimeLocks`): [instance] is the old `BuiltinLinux` `this` (every
 * `@Synchronized` member), [recovery] is `recoveryLock`. The agent host takes them in the original order: instance, then
 * recovery, never the reverse.
 */
class AgentLocks(val instance: Any, val recovery: Any)

/** The supervised services map (`services`, `serviceStarted`, `handleServiceExit`, diagnostics records). */
interface AgentServiceTable {
    /** The live or exited process of service [name], or null (`services[name]?.process`). */
    fun process(name: String): Process?

    /** Every service name now in the map (`services.keys`). */
    fun names(): Set<String>

    /**
     * `removeService(name)`, `services[name] = serviceStarted(name, process, port, "Agents are working on this phone")`,
     * `recordRunning()`, then `BuiltinServerService.start(context, currentNotice())`. Throws when the foreground
     * service refuses; the caller then stops the process and calls [removeStopped].
     */
    fun start(name: String, process: Process, port: Int?)

    /** `handleServiceExit(name, process)`: the exit is recorded and the service set changes. */
    fun exited(name: String, process: Process)

    /** `removeStoppedService(name)`. */
    fun removeStopped(name: String)

    /** Marks every service whose process is [process] as stop-requested (so its exit is not a crash). */
    fun requestStop(process: Process)

    /** `BuiltinLinux.stopService(name)`. */
    fun stop(name: String)

    /** `diagnosticRecord(name).snapshot(running, uptime)`, recording a seen exit first. */
    fun diagnostics(name: String): Map<String, Any?>

    /** Drops [names]' diagnostics keys and their `names` entries; false when the commit failed. */
    fun forgetDiagnostics(names: Set<String>): Boolean
}

/** One read of the runtime's recovery and idle state, taken while [AgentLocks.recovery] is held. */
data class AgentRuntimeState(
    /** `idleState.available`: the durable idle receipt could be read. */
    val idleAvailable: Boolean,
    val activityResumed: Boolean,
    val activityEpoch: Long,
    val wantedRevision: Long,
    /** `supervisionProfile`: the profile whose phone server the runtime supervises. */
    val supervisionProfile: String?,
    val restartWanted: Boolean,
    val userStopped: Boolean,
    val supervisionEnabled: Boolean,
    val idleServerLive: Boolean,
    val serverRunning: Boolean,
    val idle: AgentIdleFacts,
)

/** The fields of `NativeIdleState.Snapshot` the admission reads. */
data class AgentIdleFacts(
    val generation: Long = 0,
    val stopped: Boolean = false,
    val helperStopped: Boolean = false,
    val owner: String? = null,
    val helper: String? = null,
)

/** The recovery-side facts of `agentHostAdmissionCurrent` and `commitHostAdmission`. Called under [AgentLocks.recovery]. */
interface AgentRuntimeFacts {
    fun state(): AgentRuntimeState

    fun migrationMarkerValid(owner: String): Boolean

    fun policyPermits(owner: String): Boolean

    /** `helperOwner(owner)`: the helper profile the owner's completed idle token grants, or null. */
    fun helperOwner(owner: String): String?

    /** `idleState.clearCompletedHelperGate(owner, generation)`. */
    fun clearCompletedHelperGate(owner: String, generation: Long): Boolean
}

/** `WorkLeases` as the agent host uses it. */
interface AgentWorkLeases {
    /** `workLeases.foregroundGeneration()`, read only while the activity is resumed. */
    fun foregroundGeneration(): Long

    fun authorizeForegroundWork(generation: Long)

    /** `trackWork(process, WorkLeases.Kind.SIGN_IN)`. */
    fun trackSignIn(process: Process)
}

/** The runtime's process-tree helpers (`BuiltinLinux` companion). */
interface AgentProcessControl {
    /** Android's `ProcessImpl.pid`, or null. */
    fun pidOf(process: Process): Int?

    /** Every process below [root] (children by parent, plus orphans its proot traces). */
    fun descendants(root: Int): Collection<Int>

    /** SIGTERM to the tree, then SIGKILL after the grace (`stopTree(process)`). */
    fun stopTree(process: Process)

    /** SIGKILL to one pid; an exit that raced it is fine. */
    fun kill(pid: Int)

    /** The kernel start time of [pid] (`/proc/<pid>/stat` field 22), or null when it is gone or unreadable. */
    fun startToken(pid: Int): String?
}

/** The private auth probe as the host calls it ([PhoneAgentAuthRuntime] on a phone). */
interface PrivateAgentAuth {
    fun run(arguments: Map<*, *>): Map<String, Any?>

    fun blockProfile(profileId: String): Boolean
}
