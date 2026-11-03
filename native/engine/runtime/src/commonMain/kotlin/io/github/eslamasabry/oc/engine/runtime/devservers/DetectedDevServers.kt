package io.github.eslamasabry.oc.engine.runtime.devservers

import io.github.eslamasabry.oc.core.common.BusyKind
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.runtime.services.ManagedShellPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Where a detected dev server was found. */
public enum class DetectedSource {
    /** A program listening on a port in the in-app Linux (any starter). */
    Process,

    /** A command the connected server runs as a managed shell (the agent started it, or another app). */
    Shell,
}

/** A dev server this project did not save as a command: found running (port of the Dart `DetectedServer`). */
public data class DetectedServer(
    val id: String,
    val source: DetectedSource,
    val name: String,
    val command: String,
    val ports: List<Int>,
    val pid: Int = 0,
    val shellId: String? = null,
    val cwd: String? = null,
    val logPath: String? = null,
    val loopbackOnly: Boolean = true,
) {
    val hasLogs: Boolean get() = source == DetectedSource.Shell || logPath != null
    val canStop: Boolean get() = source == DetectedSource.Shell || pid > 0
    val canSave: Boolean get() = command.isNotBlank()
    val firstPort: Int? get() = ports.firstOrNull()

    public companion object {
        private val runners = setOf("npm", "yarn", "pnpm", "bun", "npx", "uv", "cargo", "go")
        private val interpreters =
            setOf("node", "python", "python3", "ruby", "bash", "sh", "php", "java", "deno", "dart", "flutter")
        private val whitespace = Regex("""\s+""")

        /** A short title from a command line: `node …/vite --port 5173` is "vite", `npm run dev` stays whole. */
        public fun nameFor(command: String): String {
            val tokens = command.trim().split(whitespace).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return ""
            fun base(t: String) = t.substringAfterLast('/')
            if (base(tokens.first()) in runners) return tokens.take(3).joinToString(" ")
            val head = base(tokens.first())
            if (head.startsWith("python") && tokens.size > 2 && tokens[1] == "-m") return tokens[2]
            var i = 0
            while (i < tokens.size - 1 && (base(tokens[i]) in interpreters || tokens[i].startsWith("-"))) i++
            val name = base(tokens[i])
            if (name.isEmpty()) return tokens.take(3).joinToString(" ")
            return if (name.length > 40) name.substring(0, 40) else name
        }
    }
}

/** What one bash script in the in-app Linux answered. */
public data class PhoneScriptResult(val exitCode: Int, val output: String)

/** Runs a bash script in the in-app Linux (the binding gives it a 25 s timeout, as in Flutter). */
public fun interface PhoneScriptRunner {
    public suspend fun run(script: String): Outcome<PhoneScriptResult>
}

/** Port of lib/state/dev_server_scan.dart `phoneScriptRunner` (B-LINUX-059). */
public object PhoneScan {
    /**
     * [linux] when the connected server is the phone itself (a loopback address) and this build has the in-app
     * Linux; null otherwise: listening ports can only be read where the script runs. A runtime-only private transport
     * ([transientTransport]) is never the phone.
     */
    public fun runnerFor(
        serverAddress: String?,
        linuxSupported: Boolean,
        linux: PhoneScriptRunner,
        transientTransport: Boolean = false,
    ): PhoneScriptRunner? {
        if (serverAddress == null || transientTransport || !linuxSupported) return null
        val host = hostOf(serverAddress.trim())
        if (host.isEmpty() || !isLoopbackHost(host)) return null
        return linux
    }

    public fun isLoopbackHost(host: String): Boolean {
        val h = host.lowercase()
        return h == "localhost" || h == "::1" || h == "[::1]" || h == "0.0.0.0" || h.startsWith("127.")
    }

    private fun hostOf(address: String): String {
        val scheme = address.indexOf("://")
        if (scheme <= 0) return ""
        val authority = address.substring(scheme + 3).takeWhile { it != '/' && it != '?' && it != '#' }
        val hostPort = authority.substringAfterLast('@')
        if (hostPort.startsWith("[")) {
            val end = hostPort.indexOf(']')
            return if (end < 0) "" else hostPort.substring(1, end)
        }
        return hostPort.substringBefore(':')
    }
}

/** What the detected-servers list shows. */
public data class DetectedDevServersState(
    val servers: List<DetectedServer> = emptyList(),
    /** True once a scan finished, even an empty one. */
    val scanned: Boolean = false,
    val scanning: Boolean = false,
    val error: Failure? = null,
)

/**
 * Finds, reads and stops dev servers the saved commands do not own (port of lib/state/detected_dev_servers.dart,
 * B-LINUX-054..059). Two sources, either may be missing: the in-app Linux ([runOnPhone] is set only when the connected
 * server is the phone itself, see [PhoneScan]) and the connected server's managed shells ([resolveShells], null when
 * the server has no development-services capability or cannot be reached).
 *
 * The caller confines the object to one serial lane. Failures: a project change `Refused(StaleScope)`; an unreachable
 * server `Unknown`; a program without a log file or that cannot be stopped from here `Unavailable(devServerLog)` /
 * `Unavailable(devServerStop)`; a log read that failed `Runtime(Generic)`; a stop that did not work `Runtime(Generic)`;
 * a shell stop the server has not confirmed `Busy(Stopping)`.
 */
public class DetectedDevServers(
    public val directory: String,
    private val resolveShells: suspend () -> ManagedShellPort?,
    private val isCurrent: () -> Boolean,
    private val runOnPhone: PhoneScriptRunner? = null,
    /** Shell ids that saved commands own; they are not listed but their ports are still found ([portsForShell]). */
    private val ownedShellIds: () -> Set<String> = { emptySet() },
    private val clock: Clock,
) {
    private val mutableState = MutableStateFlow(DetectedDevServersState())
    public val state: StateFlow<DetectedDevServersState> = mutableState.asStateFlow()

    public val servers: List<DetectedServer> get() = state.value.servers

    private var closed = false
    private var generation = 0

    /*
     * Stops in progress, and a count that moves whenever one starts or ends. A look at the phone that overlaps its own
     * stop can fail (the phone is busy ending the program): that failure is the stop's doing, so the last list stays
     * and no failure is reported for it.
     */
    private var stopping = 0
    private var stopEpoch = 0

    /** Ports found in each shell's own output, and when that was last read. */
    private val shellPorts = mutableMapOf<String, List<Int>>()
    private val shellScanned = mutableMapOf<String, Instant>()

    public val canScanProcesses: Boolean get() = runOnPhone != null

    public fun portsForShell(id: String): List<Int> = shellPorts[id].orEmpty()

    public fun forPort(port: Int): DetectedServer? = servers.firstOrNull { port in it.ports }

    /** Every port of every detected server, for the conversation menu. */
    public val allPorts: List<Int> get() = servers.flatMap { it.ports }.toSortedSet().toList()

    public fun invalidate() {
        generation++
        shellPorts.clear()
        shellScanned.clear()
        mutableState.value = DetectedDevServersState()
    }

    public fun close() {
        closed = true
        generation++
    }

    private fun ok(at: Int): Boolean = !closed && isCurrent() && at == generation

    public suspend fun refresh() {
        if (state.value.scanning || closed || !isCurrent()) return
        val at = ++generation
        val epoch = stopEpoch
        mutableState.value = state.value.copy(scanning = true)
        try {
            scan(at, epoch)
        } finally {
            if (at == generation && !closed) mutableState.value = state.value.copy(scanning = false)
        }
    }

    private suspend fun scan(at: Int, epoch: Int) {
        var failure: Failure? = null
        val found = mutableListOf<DetectedServer>()
        val owned = ownedShellIds()
        val shellPortSet = mutableSetOf<Int>()
        val shells = resolveShells()
        if (!ok(at)) return
        if (shells != null) {
            when (val listed = shells.running()) {
                is Outcome.Failed -> failure = listed.failure
                is Outcome.Ok -> {
                    if (!ok(at)) return
                    var scans = 0
                    val live = mutableSetOf<String>()
                    for (shell in listed.value.shells.filter { it.running }) {
                        live += shell.id
                        var ports = shellPorts[shell.id]
                        val last = shellScanned[shell.id]
                        val stale = ports == null || (ports.isEmpty() && last != null && clock.now() - last > 20.seconds)
                        if (stale && scans < MAX_SHELL_SCANS) {
                            scans++
                            ports = portsInShell(shells, shell.id)
                            if (!ok(at)) return
                            shellPorts[shell.id] = ports
                            shellScanned[shell.id] = clock.now()
                        }
                        val known = ports.orEmpty()
                        shellPortSet += known
                        if (shell.id in owned) continue
                        found +=
                            DetectedServer(
                                id = "shell:${shell.id}",
                                source = DetectedSource.Shell,
                                name = DetectedServer.nameFor(shell.command),
                                command = shell.command,
                                ports = known,
                                shellId = shell.id,
                                cwd = shell.directory,
                            )
                    }
                    shellPorts.keys.retainAll(live)
                    shellScanned.keys.retainAll(live)
                }
            }
        }
        val run = runOnPhone
        if (run != null) {
            when (val result = run.run(ListeningPorts.script())) {
                is Outcome.Failed -> if (failure == null) failure = result.failure
                is Outcome.Ok -> {
                    if (!ok(at)) return
                    for (p in ListeningPorts.parse(result.value.output)) {
                        // The same program is already listed as the shell that runs it, or as a saved command.
                        if (p.ports.any { it in shellPortSet }) continue
                        found +=
                            DetectedServer(
                                id = if (p.hasProcess) "pid:${p.pid}" else "port:${p.ports.first()}",
                                source = DetectedSource.Process,
                                name = if (p.hasProcess) DetectedServer.nameFor(p.command) else "",
                                command = p.command,
                                ports = p.ports,
                                pid = p.pid,
                                cwd = p.cwd,
                                logPath = p.logPath,
                                loopbackOnly = p.loopbackOnly,
                            )
                    }
                }
            }
        }
        if (!ok(at)) return
        // Keep what was shown; the stop ends with a fresh look.
        if (failure != null && (stopping > 0 || epoch != stopEpoch)) return
        val sorted = found.sortedWith(compareBy<DetectedServer> { if (inProject(it)) 0 else 1 }.thenBy { it.firstPort ?: 0 })
        mutableState.value = state.value.copy(servers = sorted, error = failure, scanned = true)
    }

    private fun inProject(s: DetectedServer): Boolean {
        val cwd = s.cwd ?: return false
        if (directory.isEmpty()) return false
        return cwd == directory || cwd.startsWith("$directory/")
    }

    private suspend fun tail(shells: ManagedShellPort, id: String, bytes: Int): Outcome<String> {
        val head = when (val r = shells.output(id, cursor = 0, limit = 1)) {
            is Outcome.Failed -> return r
            is Outcome.Ok -> r.value
        }
        val start = maxOf(0L, head.size - bytes)
        return when (val r = shells.output(id, cursor = start, limit = bytes)) {
            is Outcome.Failed -> r
            is Outcome.Ok -> TerminalText.plain(r.value.text).ok()
        }
    }

    private suspend fun portsInShell(shells: ManagedShellPort, id: String): List<Int> =
        (tail(shells, id, SHELL_PORT_SCAN) as? Outcome.Ok)?.value?.let(ListeningPorts::portsInOutput).orEmpty()

    /** The end of [server]'s output as plain text (at most 64 KB). */
    public suspend fun readLog(server: DetectedServer): Outcome<String> {
        if (server.source == DetectedSource.Shell) {
            val shells = resolveShells()
            val id = server.shellId
            if (shells == null || id == null) return Failure.Unknown().failed()
            return when (val text = tail(shells, id, LOG_TAIL)) {
                is Outcome.Failed -> text
                is Outcome.Ok -> text.value.let { if (it.length > LOG_TAIL) it.substring(it.length - LOG_TAIL) else it }.ok()
            }
        }
        val run = runOnPhone
        val path = server.logPath
        if (run == null || path == null) return Failure.Unavailable(LOG_FEATURE).failed()
        val result = when (val r = run.run(ListeningPorts.tailScript(path))) {
            is Outcome.Failed -> return r
            is Outcome.Ok -> r.value
        }
        if (result.exitCode != 0) return Failure.Runtime(RuntimeProblem.Generic).failed()
        return TerminalText.plain(result.output).ok()
    }

    /**
     * Stops [server]. A shell asks the connected server to end it; a program is sent TERM (then KILL) only while its
     * command line is unchanged. A stop that did not work keeps the row; one that worked drops it and looks again.
     */
    public suspend fun stop(server: DetectedServer): Outcome<Unit> {
        if (!isCurrent()) return Failure.Refused(RefusalReason.StaleScope).failed()
        stopping++
        stopEpoch++
        val result =
            try {
                stopIn(server)
            } finally {
                stopping--
                stopEpoch++
            }
        if (result is Outcome.Failed) return result
        mutableState.value = state.value.copy(servers = servers.filter { it.id != server.id })
        server.shellId?.let { shellPorts.remove(it) }
        refresh()
        return Unit.ok()
    }

    private suspend fun stopIn(server: DetectedServer): Outcome<Unit> {
        if (server.source == DetectedSource.Shell) {
            val shells = resolveShells()
            val id = server.shellId
            if (shells == null || id == null) return Failure.Unknown().failed()
            shells.stop(id).let { if (it is Outcome.Failed) return it }
            return when (val left = shells.get(id)) {
                is Outcome.Failed -> left
                is Outcome.Ok -> if (left.value != null) Failure.Busy(BusyKind.Stopping).failed() else Unit.ok()
            }
        }
        val run = runOnPhone
        if (run == null || server.pid <= 0) return Failure.Unavailable(STOP_FEATURE).failed()
        val result = when (val r = run.run(ListeningPorts.stopScript(server.pid, server.command))) {
            is Outcome.Failed -> return r
            is Outcome.Ok -> r.value
        }
        return if (result.exitCode != 0) Failure.Runtime(RuntimeProblem.Generic).failed() else Unit.ok()
    }

    public companion object {
        public const val LOG_TAIL: Int = 65_536
        private const val SHELL_PORT_SCAN = 16_384
        private const val MAX_SHELL_SCANS = 6
        public val LOG_FEATURE: FeatureId = FeatureId("devServerLog")
        public val STOP_FEATURE: FeatureId = FeatureId("devServerStop")
    }
}
