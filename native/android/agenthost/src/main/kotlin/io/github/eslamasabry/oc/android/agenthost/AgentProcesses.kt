package io.github.eslamasabry.oc.android.agenthost

import android.os.Build
import android.system.Os
import android.system.OsConstants
import io.github.eslamasabry.oc.core.common.AppLog
import org.json.JSONObject
import java.io.File
import java.util.IdentityHashMap
import java.util.concurrent.TimeUnit

/**
 * The agent block of `BuiltinLinux` (plan 11 §5.3 `AgentProcesses`: lines 731–1076 and 2096–2131 of the Flutter app's
 * `BuiltinLinux.kt` at the freeze tip), moved here behind [AgentGuestPorts] and serving the kept host Kotlin as its
 * [AgentLinux]. **Moves only**: every body keeps its order, checks and words; the edits are the seam calls (named on
 * each port member), `synchronized(locks.instance)` for `@Synchronized` (S11-2), `AppLog` for `Log`, and [FileModes]
 * for `Os.chmod` / `Os.lstat` so the JVM tests can run it.
 *
 * Two runtime reads go the other way (CR11-0 "registrations"): the runtime asks [anyAgentProcessAlive] before a cold
 * component recovery (`BuiltinLinux.kt:456`) and [isBlocked] before an agent chat work lease (`:3885`).
 */
class AgentProcesses(
    /** `context.filesDir`. */
    private val files: File,
    private val installedNow: () -> Boolean,
    private val locks: AgentLocks,
    private val launcher: GuestLauncher,
    private val registry: GuestProcessRegistry,
    private val services: AgentServiceTable,
    private val runtime: AgentRuntimeFacts,
    private val work: AgentWorkLeases,
    private val control: AgentProcessControl,
    privateAuth: (AgentProcesses) -> PrivateAgentAuth,
    private val modes: FileModes = FileModes.Android,
    /** `Build.SUPPORTED_ABIS[0]` on a phone. */
    private val abi: () -> String = { Build.SUPPORTED_ABIS.firstOrNull() ?: "" },
) : AgentLinux {
    override val primaryAbi: String get() = abi()
    override val home: File = File(files, "linux")
    override val rootfs: File = File(home, "ubuntu")
    override val installed: Boolean get() = installedNow()

    private val blockedAgentProfiles = mutableSetOf<String>()
    private val agentProcessProfiles = mutableMapOf<Process, String>()
    private val agentWorkAdmissions = IdentityHashMap<Process, Long>()
    private val authOtherOwners = PhoneAgentAuthOtherOwners { process ->
        control.pidOf(process)?.let { pid -> control.startToken(pid)?.let { pid to it } }
    }

    init {
        // Process budget hooks (plan 11 §7.2): the runtime counts and slows agent trees by these roots.
        registry.registerRootPids(ProcessOwner.Paseo) { helperRootPids() }
        registry.registerRootPids(ProcessOwner.AgentSignIn) { signInRootPids() }
    }

    /**
     * An agent's own sign-in (`claude auth login`, `codex login`, …) for [profileId]'s agent account, to run on a
     * terminal: the person signs in through the agent's prompts and its provider's page, and the app never reads a code
     * or key. The page the agent asks the system to open is written to the returned file by a stand-in `xdg-open`, and
     * the terminal opens it in the browser. Only the catalog's agent programs and plain arguments run.
     */
    fun agentSignInCommand(profileId: String, program: List<String>): Pair<List<String>, File> =
        synchronized(locks.instance) {
            check(Regex("^[A-Za-z0-9_-]{1,80}$").matches(profileId)) { "The agent host is unavailable." }
            check(installed && profileId !in blockedAgentProfiles) { "The agent host is unavailable." }
            check(program.isNotEmpty() && program.size <= 5 && program.first() in SIGN_IN_PROGRAMS &&
                program.drop(1).all { Regex("^[A-Za-z0-9-]{1,32}$").matches(it) }) { "That sign-in can't run here." }
            val profileHome = prepareAgentProfileHome(profileId)
            shareClaudeSignIn(profileId)
            val guestHome = "/home/oc/.oc-profiles/$profileId"
            val bin = File(profileHome, ".oc-bin").apply { mkdirs() }
            val opener = "#!/bin/sh\n" +
                "printf '%s\\n' \"${'$'}1\" > \"${'$'}HOME/.oc-open-url.tmp\" && " +
                "mv \"${'$'}HOME/.oc-open-url.tmp\" \"${'$'}HOME/.oc-open-url\"\n"
            for (name in listOf("xdg-open", "open-url")) {
                File(bin, name).apply {
                    writeText(opener)
                    setReadable(true, false)
                    setExecutable(true, false)
                }
            }
            val request = File(profileHome, ".oc-open-url").apply { delete() }
            // CLAUDE_HOME: Paseo's usage reader looks for Claude's sign-in there
            // (or ~/.claude), not in CLAUDE_CONFIG_DIR.
            val command = signInEnvironment(guestHome) + program
            launcher.prootCommand(command, agentUser = true) to request
        }

    /**
     * [profileId]'s private agent home, with the folders its agents are pointed at by environment. Codex refuses to run
     * at all, sign-in and `app-server` included, when `CODEX_HOME` names a folder that does not exist (issue #95: Codex
     * never worked on the phone).
     */
    private fun prepareAgentProfileHome(profileId: String): File {
        val profileHome = PhoneAgentPaths.prepare(files, "linux/ubuntu/home/oc/.oc-profiles/$profileId")
        modes.chmod(profileHome.absolutePath, 448)
        val codexHome = PhoneAgentPaths.prepare(files, "linux/ubuntu/home/oc/.oc-profiles/$profileId/codex")
        modes.chmod(codexHome.absolutePath, 448)
        return profileHome
    }

    /**
     * One Claude sign-in for the app and the Terminal: `/home/oc/.claude` becomes a link to [profileId]'s Claude folder,
     * and a sign-in made in the Terminal before is adopted (PhoneAgentClaudeHome). Best effort: a failure leaves the
     * profile's own folder as it was and never blocks the launch.
     */
    private fun shareClaudeSignIn(profileId: String) {
        try {
            val steps = PhoneAgentClaudeHome.prepare(File(rootfs, "home/oc"), profileId, File(rootfs, "root"))
            if (steps.isNotEmpty()) log.i { "Claude sign-in folder: ${steps.joinToString()}" }
        } catch (error: Exception) {
            log.w { "Claude sign-in folder not shared: ${error.javaClass.simpleName}" }
        }
    }

    /**
     * Removes Claude's OAuth refresh lock (`<config dir>/.oauth_refresh.lock`, an empty folder) for [profile] when none
     * of that profile's agent processes runs: one Android stopped mid-refresh never releases it.
     */
    override fun clearStaleAgentLoginLock(profile: String): Unit = synchronized(locks.instance) {
        check(Regex("^[A-Za-z0-9_-]{1,80}$").matches(profile)) { "The agent host is unavailable." }
        val busy = agentProcessProfiles.any { (process, owner) -> owner == profile && process.isAlive }
        if (busy) return
        val lock = File(files, "linux/ubuntu/home/oc/.oc-profiles/$profile/claude/.oauth_refresh.lock")
        if (lock.isDirectory && lock.canonicalFile.parentFile?.name == "claude") {
            lock.listFiles()?.forEach { it.delete() }
            if (lock.delete()) log.i { "cleared a stale agent login lock" }
        }
    }

    /** [profile]'s private Paseo home, where the helper keeps its config and supervisor lock. */
    override fun agentPaseoHome(profile: String): File = synchronized(locks.instance) {
        check(Regex("^[A-Za-z0-9_-]{1,80}$").matches(profile) && profile !in blockedAgentProfiles)
        PhoneAgentPaths.prepare(files, "linux/ubuntu/home/oc/.oc-profiles/$profile/paseo")
    }

    /** Whether a helper (ours from an earlier app process, or anything else) answers on [port]. */
    override fun agentPortAnswers(port: Int): Boolean = PaseoStaleLock.loopbackAnswers(port)

    /** Private agent process: fixed uid, private host home, no transcript/log. */
    override fun startAgentProcess(profile: String, args: List<String>, foreground: Boolean, privateOutput: Boolean): Process =
        synchronized(locks.instance) { startPrivateAgentProcess(profile, args, foreground, privateOutput, signIn = false) }

    override fun startSignInProcess(profile: String, args: List<String>, foreground: Boolean): Process =
        synchronized(locks.instance) { startPrivateAgentProcess(profile, args, foreground, false, signIn = true) }

    private fun startPrivateAgentProcess(profileId: String, argv: List<String>, foreground: Boolean,
        privateOutput: Boolean, signIn: Boolean): Process {
        check(Regex("^[A-Za-z0-9_-]{1,80}$").matches(profileId)) { "The agent host is unavailable." }
        check(installed && argv.isNotEmpty() && profileId !in blockedAgentProfiles) { "The agent host is unavailable." }
        val hostTicket = agentHostTicket.get()
        if (hostTicket != null) synchronized(locks.recovery) {
            check(hostTicket.profile == profileId && agentHostAdmissionCurrent(hostTicket))
        }
        val workGeneration = if (synchronized(locks.recovery) { runtime.state().activityResumed })
            work.foregroundGeneration() else null
        prepareAgentProfileHome(profileId)
        shareClaudeSignIn(profileId)
        val guestHome = "/home/oc/.oc-profiles/$profileId"
        // CLAUDE_HOME: Paseo's usage reader looks for Claude's sign-in there
        // (or ~/.claude), not in CLAUDE_CONFIG_DIR; without it the Usage
        // widget never shows Claude.
        val command = agentEnvironment(guestHome) + argv
        val proot = launcher.prootCommand(command, agentUser = true)
        val owner = if (signIn) ProcessOwner.AgentSignIn else ProcessOwner.Paseo
        val process = if (hostTicket == null) launcher.startRegistered(owner, proot, privateOutput)
        else synchronized(locks.recovery) {
            check(agentHostAdmissionCurrent(hostTicket))
            if (hostTicket.idleGeneration != null) check(runtime.state().serverRunning)
            launcher.startRegistered(owner, proot, privateOutput)
        }
        registerAgentProcess(process, profileId, hostTicket, workGeneration, privateOutput)
        try {
            if (foreground) trackPrivateAgentService("agent-auth.$profileId", process, null)
            if (signIn) work.trackSignIn(process)
            return process
        } catch (_: Throwable) {
            try { stopAgentProcess(process) } catch (_: Throwable) { }
            agentProcessProfiles.remove(process)
            throw IllegalStateException("The agent host could not start.")
        }
    }

    private fun registerAgentProcess(process: Process, profileId: String, hostTicket: AgentHostTicket?,
        workGeneration: Long?, privateOutput: Boolean) {
        agentHostAdmissions.entries.removeAll { !it.key.isAlive }
        hostTicket?.let { agentHostAdmissions[process] = it }
        agentProcessProfiles.entries.removeAll { !it.key.isAlive }
        agentProcessProfiles[process] = profileId
        agentWorkAdmissions.entries.removeAll { !it.key.isAlive }
        workGeneration?.let { agentWorkAdmissions[process] = it }
        authOtherOwners.register(process, privateOutput)
    }

    /** Uses the existing Android foreground-service deadline and exact PID stop. */
    override fun trackPrivateAgentService(name: String, child: Process, port: Int?): Unit = synchronized(locks.instance) {
        check(name.startsWith("agent-auth.") || name.startsWith("agent-host."))
        val hostTicket = if (name.startsWith("agent-host.")) admittedHostTicket(name, child) else null
        try {
            services.start(name, child, port)
            if (hostTicket != null) commitHostAdmission(hostTicket, child)
            agentWorkAdmissions.remove(child)?.let { work.authorizeForegroundWork(it) }
        } catch (_: Throwable) {
            try { stopAgentProcess(child) } catch (_: Throwable) { }
            services.removeStopped(name)
            throw IllegalStateException("The agent host could not start.")
        }
        try {
            Thread({
                try { child.waitFor() }
                catch (_: Throwable) {
                    try { stopAgentProcess(child) } catch (_: Throwable) { }
                } finally {
                    try { services.exited(name, child) }
                    catch (_: Throwable) { /* Supervision must not crash the app. */ }
                }
            }, "phone-agent-service").start()
        } catch (_: Throwable) {
            try { stopAgentProcess(child) } catch (_: Throwable) { }
            services.removeStopped(name)
            throw IllegalStateException("The agent host could not start.")
        }
    }

    private fun admittedHostTicket(name: String, process: Process): AgentHostTicket =
        agentHostAdmissions[process]?.also { ticket ->
            synchronized(locks.recovery) {
                check(name == "agent-host.${ticket.profile}" && agentHostAdmissionCurrent(ticket))
                if (ticket.idleGeneration != null) check(runtime.state().serverRunning)
            }
        } ?: error("idle_resume_stale")

    private fun commitHostAdmission(hostTicket: AgentHostTicket, process: Process) {
        synchronized(locks.recovery) {
            check(agentHostAdmissionCurrent(hostTicket))
            val state = runtime.state().idle
            if (hostTicket.idleGeneration == null && state.generation > 0) {
                check(state.owner != null && runtime.clearCompletedHelperGate(state.owner, state.generation))
            }
            agentHostAdmissions.remove(process)
        }
    }

    /** Update only the exact profile-owned helper; the agents engine separately proves its agents idle. */
    override fun requireAgentHostUpdateIdle(profile: String, process: Process?): Unit = synchronized(locks.instance) {
        val unowned = if (process == null) {
            services.names().any { name -> name.startsWith("agent-host.") && services.process(name)?.isAlive == true }
        } else {
            services.process("agent-host.$profile") !== process || agentProcessProfiles[process] != profile
        }
        if (unowned) throw PhoneAgentHost.Failure("agent_host_unowned")
        if (agentProcessProfiles.any { (other, _) -> other !== process && other.isAlive })
            throw PhoneAgentHost.Failure("agent_host_busy")
    }

    /** A cancelled private flow cannot report drained while captured children survive. */
    override fun stopAgentProcess(child: Process) {
        if (!child.isAlive) return
        synchronized(locks.instance) { services.requestStop(child) }
        val root = control.pidOf(child)
        val children = if (root == null) emptyMap() else control.descendants(root).associateWith { control.startToken(it) }
        control.stopTree(child)
        children.filter { (pid, identity) -> identity != null && control.startToken(pid) == identity }
            .forEach { (pid, _) -> control.kill(pid) }
        if (!child.waitFor(2, TimeUnit.SECONDS)) throw IllegalStateException("The agent did not stop.")
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (children.any { (pid, identity) -> identity != null && control.startToken(pid) == identity } &&
            System.nanoTime() < deadline) Thread.sleep(20)
        if (children.any { (pid, identity) -> identity != null && control.startToken(pid) == identity })
            throw IllegalStateException("The agent did not stop.")
    }

    private val privateAgentAuth: PrivateAgentAuth by lazy { privateAuth(this) }

    /** Private auth output bypasses neither the generic receipt filter nor owner checks. */
    fun agentAuthProbe(arguments: Map<*, *>): Map<String, Any?> = privateAgentAuth.run(arguments)

    /** The launcher of [PhoneAgentAuthRuntime]: private output, the profile's agent home. */
    internal fun authLaunch(profile: String, argv: List<String>): Process =
        startAgentProcess(profile, argv, privateOutput = true)

    /** The lock cleaner of [PhoneAgentAuthRuntime]: only while [quiet] holds, under the instance monitor. */
    internal fun authClearLock(profile: String, quiet: () -> Boolean): Unit = synchronized(locks.instance) {
        if (quiet()) PhoneAgentAuthLock.clear(files, profile)
    }

    /** The other agent owners [PhoneAgentAuthRuntime] must never signal. */
    internal fun authOtherOwnerSnapshot(): Set<Pair<Int, String>> = authOtherOwners.snapshot()

    override val agentSignIn: PhoneAgentSignIn by lazy { PhoneAgentSignIn(this) }
    val agentHost: PhoneAgentHost by lazy { PhoneAgentHost(this) }

    override fun blockAgentProfile(profile: String) {
        val targets = synchronized(locks.instance) {
            check(Regex("^[A-Za-z0-9_-]{1,80}$").matches(profile))
            blockedAgentProfiles.add(profile)
            agentProcessProfiles.filterValues { it == profile }.keys.toList()
        }
        check(privateAgentAuth.blockProfile(profile)) { "The agent did not stop." }
        targets.forEach { stopAgentProcess(it) }
    }

    override fun writeAgentConfig(profile: String, config: String): Unit = synchronized(locks.instance) {
        check(Regex("^[A-Za-z0-9_-]{1,80}$").matches(profile) && config.length < 65536 && profile !in blockedAgentProfiles)
        val data = JSONObject(config)
        check(data.optInt("version") == 1)
        val dir = PhoneAgentPaths.prepare(files, "linux/ubuntu/home/oc/.oc-profiles/$profile/paseo")
        modes.chmod(dir.absolutePath, 448)
        val temp = File(dir, "config.json.tmp")
        check(temp.canonicalFile == temp.absoluteFile && !File(dir, "config.json").let { it.canonicalFile != it.absoluteFile })
        temp.writeText(config)
        modes.chmod(temp.absolutePath, 384)
        check(temp.renameTo(File(dir, "config.json")))
    }

    override fun deleteAgentHome(profile: String): Unit = synchronized(locks.instance) {
        check(Regex("^[A-Za-z0-9_-]{1,80}$").matches(profile))
        val base = PhoneAgentPaths.resolve(files, "linux/ubuntu/home/oc/.oc-profiles")
        val target = PhoneAgentPaths.resolve(base, profile)
        fun erase(file: File) {
            val directory = modes.realDirectory(file) ?: return
            if (directory) file.listFiles()?.forEach { erase(it) }
            check(file.delete()) { "The agent sign-in could not be removed." }
        }
        check(target.parentFile?.canonicalFile == base)
        val names = setOf("agent-auth.$profile", "agent-host.$profile")
        check(names.none { services.process(it)?.isAlive == true }) {
            "Stop the agent before removing its sign-in."
        }
        // The Terminal's ~/.claude link to this profile goes with it.
        PhoneAgentClaudeHome.forget(File(rootfs, "home/oc"), profile)
        erase(target)
        names.forEach { services.removeStopped(it) }
        check(services.forgetDiagnostics(names)) { "The agent sign-in could not be removed." }
    }

    override fun privateAgentDiagnostics(name: String): Map<String, Any?> = synchronized(locks.instance) {
        check(name.startsWith("agent-host."))
        services.diagnostics(name)
    }

    override fun stopService(name: String) = services.stop(name)

    // ---- Ticketed helper admission (BuiltinLinux.kt 2096–2131)

    internal data class AgentHostTicket(val profile: String, val owner: String?, val revision: Long,
        val epoch: Long, val idleGeneration: Long?)
    private val agentHostTicket = ThreadLocal<AgentHostTicket?>()
    private val agentHostAdmissions = IdentityHashMap<Process, AgentHostTicket>()

    internal fun captureAgentHostStart(profile: String, idleResume: Boolean, generation: Long?): AgentHostTicket? =
        synchronized(locks.recovery) {
            val state = runtime.state()
            if (!state.activityResumed || !Regex("[A-Za-z0-9_-]{1,80}").matches(profile)) null else {
                val owner = state.supervisionProfile
                val ticket = AgentHostTicket(profile, owner, state.wantedRevision, state.activityEpoch,
                    if (idleResume) generation else null)
                if (idleResume && (generation == null || generation <= 0)) null
                else ticket.takeIf { agentHostAdmissionCurrent(it) }
            }
        }

    private fun agentHostAdmissionCurrent(ticket: AgentHostTicket): Boolean {
        val current = runtime.state()
        val state = current.idle
        val authorityOwner = ticket.owner.takeIf { ticket.idleGeneration != null }
        val helper = if (ticket.idleGeneration == null && state.generation > 0 && ticket.owner != null)
            runtime.helperOwner(ticket.owner) else null
        return NativeAgentHostAdmission.admitted(
            NativeAgentHostAdmission.Ticket(ticket.profile, ticket.owner, ticket.revision,
                ticket.epoch, ticket.idleGeneration),
            NativeAgentHostAdmission.Snapshot(current.idleAvailable, current.activityResumed, current.activityEpoch,
                current.wantedRevision, current.supervisionProfile, current.restartWanted, current.userStopped,
                current.supervisionEnabled, current.idleServerLive,
                authorityOwner?.let(runtime::migrationMarkerValid) == true,
                authorityOwner?.let(runtime::policyPermits) == true, helper,
                NativeIdleState.Snapshot(generation = state.generation, stopped = state.stopped,
                    helperStopped = state.helperStopped, owner = state.owner, helper = state.helper)))
    }

    internal fun <T> withAgentHostStart(ticket: AgentHostTicket, work: () -> T): T {
        check(agentHostTicket.get() == null)
        agentHostTicket.set(ticket)
        try {
            synchronized(locks.recovery) { check(agentHostAdmissionCurrent(ticket)) { "idle_resume_stale" } }
            return work()
        } finally { agentHostTicket.remove() }
    }

    // ---- Runtime reads (CR11-0 registrations) and the process budget hooks

    /** `agentProcessProfiles.keys.none { it.isAlive }` inverted, for the runtime's cold component recovery. */
    fun anyAgentProcessAlive(): Boolean = synchronized(locks.instance) { agentProcessProfiles.keys.any { it.isAlive } }

    /** Whether [profile] is blocked (profile deletion): the runtime refuses its agent chat work lease. */
    fun isBlocked(profile: String): Boolean = synchronized(locks.instance) { profile in blockedAgentProfiles }

    /** Live private agent processes (helpers, auth checks, sign-in pipes): what the agent host adds to the 32. */
    fun liveAgentProcesses(): Int = synchronized(locks.instance) { agentProcessProfiles.keys.count { it.isAlive } }

    /** Root pids of the running Paseo helpers (`agent-host.*` services), for `setAgentPriority` and the census. */
    fun helperRootPids(): Set<Int> = synchronized(locks.instance) {
        services.names().filter { it.startsWith("agent-host.") }
            .mapNotNull { services.process(it)?.takeIf { p -> p.isAlive } }
            .mapNotNull(control::pidOf).toSet()
    }

    /** Root pids of the other live agent processes (auth checks and sign-in pipes). */
    private fun signInRootPids(): Set<Int> = synchronized(locks.instance) {
        val helpers = services.names().filter { it.startsWith("agent-host.") }.mapNotNull { services.process(it) }
        agentProcessProfiles.keys.filter { it.isAlive && helpers.none { h -> h === it } }
            .mapNotNull(control::pidOf).toSet()
    }

    companion object {
        /** The sign-in programs a terminal may run (`BuiltinLinux.SIGN_IN_PROGRAMS`, issue #95). */
        val SIGN_IN_PROGRAMS = setOf("claude", "codex", "gemini", "qwen", "goose", "omp", "fx", "kilo", "cline")

        private val log = AppLog.tagged("AgentHost")

        /** The env prefix of every private agent launch (`startPrivateAgentProcess`). */
        internal fun agentEnvironment(guestHome: String): List<String> =
            listOf("/usr/bin/env", "HOME=$guestHome", "CLAUDE_CONFIG_DIR=$guestHome/claude",
                "CLAUDE_HOME=$guestHome/claude",
                "CODEX_HOME=$guestHome/codex", "DISABLE_AUTOUPDATER=1", "CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1")

        /** The env prefix of the sign-in terminal (`agentSignInCommand`). */
        internal fun signInEnvironment(guestHome: String): List<String> =
            listOf("/usr/bin/env", "HOME=$guestHome", "CLAUDE_CONFIG_DIR=$guestHome/claude",
                "CLAUDE_HOME=$guestHome/claude",
                "BROWSER=$guestHome/.oc-bin/open-url",
                "PATH=$guestHome/.oc-bin:/home/oc/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                "CODEX_HOME=$guestHome/codex",
                "TERM=xterm-256color", "LANG=C.UTF-8",
                "DISABLE_AUTOUPDATER=1", "CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1")
    }
}

/** `Os.chmod` and `Os.lstat` as the agent host uses them; the JVM tests bind [FileModes.Posix]. */
interface FileModes {
    fun chmod(path: String, mode: Int)

    /** True for a real directory, false for anything else (a link included), null when lstat fails. */
    fun realDirectory(file: File): Boolean?

    object Android : FileModes {
        override fun chmod(path: String, mode: Int) = Os.chmod(path, mode)

        override fun realDirectory(file: File): Boolean? {
            val stat = try { Os.lstat(file.absolutePath) } catch (_: Exception) { return null }
            return OsConstants.S_ISDIR(stat.st_mode) && !OsConstants.S_ISLNK(stat.st_mode)
        }
    }
}
