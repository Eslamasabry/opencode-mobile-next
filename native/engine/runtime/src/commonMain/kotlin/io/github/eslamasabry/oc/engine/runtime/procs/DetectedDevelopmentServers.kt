package io.github.eslamasabry.oc.engine.runtime.procs

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.engine.runtime.services.ManagedShell
import io.github.eslamasabry.oc.engine.runtime.services.ManagedShellGateway
import io.github.eslamasabry.oc.engine.runtime.services.TerminalText
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public enum class DetectedServerSource {
    Process,
    Shell,
}

/** A discovered server is not an ownership receipt for a saved development command. */
public data class DetectedDevelopmentServer(
    val id: String,
    val source: DetectedServerSource,
    val name: String,
    val command: String,
    val ports: List<Int>,
    val pid: Int = 0,
    val shellId: String? = null,
    val cwd: String? = null,
    val logPath: String? = null,
    val loopbackOnly: Boolean = true,
    val startedAt: Long? = null,
) {
    public val hasLogs: Boolean
        get() = source == DetectedServerSource.Shell || logPath != null

    public val canStop: Boolean
        get() = source == DetectedServerSource.Shell || (pid > 1 && command.isNotBlank())

    public val firstPort: Int?
        get() = ports.firstOrNull()
}

public data class DetectedDevelopmentServersState(
    val servers: List<DetectedDevelopmentServer> = emptyList(),
    val scanned: Boolean = false,
    val scanning: Boolean = false,
    val stopping: String? = null,
    val failure: Failure? = null,
)

/**
 * B-LINUX-054..059. Each command takes one serial lane; every result is bound to the panel's
 * generation.
 */
public interface DetectedDevelopmentServers {
    public val state: StateFlow<DetectedDevelopmentServersState>
    public val canScanProcesses: Boolean

    public fun portsForShell(id: String): List<Int>

    public fun invalidate()

    public suspend fun refresh(): Outcome<Unit>

    public suspend fun readLog(id: String): Outcome<String>

    public suspend fun stop(id: String): Outcome<Unit>
}

/** [runOnPhone] is absent for a remote profile or a platform without the phone's Linux. */
public fun detectedDevelopmentServers(
    directory: String,
    resolveGateway: suspend () -> ManagedShellGateway?,
    isCurrent: () -> Boolean,
    ownedShellIds: () -> Set<String>,
    runOnPhone: (suspend (String) -> Outcome<String>)?,
    clock: Clock,
): DetectedDevelopmentServers =
    DefaultDetectedDevelopmentServers(
        directory,
        resolveGateway,
        isCurrent,
        ownedShellIds,
        runOnPhone,
        clock,
    )

internal class DefaultDetectedDevelopmentServers(
    private val directory: String,
    private val resolveGateway: suspend () -> ManagedShellGateway?,
    private val isCurrent: () -> Boolean,
    private val ownedShellIds: () -> Set<String>,
    private val runOnPhone: (suspend (String) -> Outcome<String>)?,
    private val clock: Clock,
) : DetectedDevelopmentServers {
    private val lane = Mutex()
    private val mutable = MutableStateFlow(DetectedDevelopmentServersState())
    override val state: StateFlow<DetectedDevelopmentServersState> = mutable.asStateFlow()
    override val canScanProcesses: Boolean
        get() = runOnPhone != null

    private var generation = 0L

    private data class PortReading(val shell: ManagedShell, val ports: List<Int>, val at: Instant)

    private val shellPorts = mutableMapOf<String, PortReading>()

    override fun portsForShell(id: String): List<Int> = shellPorts[id]?.ports.orEmpty()

    override fun invalidate() {
        generation++
        shellPorts.clear()
        mutable.value = DetectedDevelopmentServersState()
    }

    private fun current(admitted: Long): Boolean = admitted == generation && isCurrent()

    private fun stale(): Outcome.Failed = Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))

    private fun refused(): Outcome.Failed = Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))

    override suspend fun refresh(): Outcome<Unit> {
        // A scan overlapping this panel's own stop keeps the last inventory and never reports the
        // stop's busy error.
        if (mutable.value.stopping != null || mutable.value.scanning) return Outcome.Ok(Unit)
        val admitted = generation
        return lane.withLock {
            if (!current(admitted)) return@withLock stale()
            mutable.value = mutable.value.copy(scanning = true, failure = null)
            try {
                val before = mutable.value.servers
                val found = mutableListOf<DetectedDevelopmentServer>()
                val shellPortSet = mutableSetOf<Int>()
                var failure: Failure? = null
                val gateway = resolveGateway()
                if (!current(admitted)) return@withLock stale()
                if (gateway != null) {
                    when (val inventory = gateway.loadRunningShells()) {
                        is Outcome.Failed -> {
                            failure = inventory.failure
                            found += before.filter { it.source == DetectedServerSource.Shell }
                            shellPortSet += shellPorts.values.flatMap { it.ports }
                        }
                        is Outcome.Ok -> {
                            val live = mutableSetOf<String>()
                            val owned = ownedShellIds()
                            var scanned = 0
                            for (shell in inventory.value.filter { it.running }) {
                                if (!current(admitted)) return@withLock stale()
                                live += shell.id
                                val cached =
                                    shellPorts[shell.id]?.takeIf { sameShell(it.shell, shell) }
                                var ports = cached?.ports
                                val needsRead =
                                    cached == null ||
                                        (cached.ports.isEmpty() &&
                                            clock.now() - cached.at > 20.seconds)
                                if (needsRead && scanned < 6) {
                                    scanned++
                                    val tail = shellTail(gateway, shell.id, 16384, admitted)
                                    if (!current(admitted)) return@withLock stale()
                                    ports =
                                        (tail as? Outcome.Ok)
                                            ?.value
                                            ?.let(ListeningPorts::portsInOutput)
                                            .orEmpty()
                                    shellPorts[shell.id] = PortReading(shell, ports, clock.now())
                                }
                                val known = ports.orEmpty()
                                shellPortSet += known
                                if (shell.id in owned) continue
                                found +=
                                    DetectedDevelopmentServer(
                                        id = "shell:${shell.id}",
                                        source = DetectedServerSource.Shell,
                                        name = nameFor(shell.command),
                                        command = shell.command,
                                        ports = known,
                                        shellId = shell.id,
                                        cwd = shell.directory,
                                        startedAt = shell.startedAt,
                                    )
                            }
                            if (!current(admitted)) return@withLock stale()
                            shellPorts.keys.retainAll(live)
                        }
                    }
                }
                if (!current(admitted)) return@withLock stale()
                val runner = runOnPhone
                if (runner != null) {
                    when (val result = runner(ListeningPorts.script())) {
                        is Outcome.Failed -> {
                            failure = failure ?: result.failure
                            found += before.filter {
                                it.source == DetectedServerSource.Process &&
                                    it.ports.none(shellPortSet::contains)
                            }
                        }
                        is Outcome.Ok -> {
                            if (!current(admitted)) return@withLock stale()
                            for (process in ListeningPorts.parse(result.value)) {
                                if (process.ports.any(shellPortSet::contains)) continue
                                found +=
                                    DetectedDevelopmentServer(
                                        id =
                                            if (process.hasProcess) "pid:${process.pid}"
                                            else "port:${process.ports.first()}",
                                        source = DetectedServerSource.Process,
                                        name = nameFor(process.command),
                                        command = process.command,
                                        ports = process.ports,
                                        pid = process.pid,
                                        cwd = process.cwd,
                                        logPath = process.logPath,
                                        loopbackOnly = process.loopbackOnly,
                                    )
                            }
                        }
                    }
                }
                if (!current(admitted)) return@withLock stale()
                mutable.value =
                    mutable.value.copy(
                        servers =
                            found.sortedWith(
                                compareBy<DetectedDevelopmentServer> { if (inProject(it)) 0 else 1 }
                                    .thenBy { it.firstPort ?: 0 }
                            ),
                        scanned = true,
                        failure = failure,
                    )
                if (failure == null) Outcome.Ok(Unit) else Outcome.Failed(failure)
            } finally {
                if (current(admitted)) mutable.value = mutable.value.copy(scanning = false)
            }
        }
    }

    private fun inProject(server: DetectedDevelopmentServer): Boolean =
        directory.isNotEmpty() &&
            server.cwd?.let { it == directory || it.startsWith("$directory/") } == true

    private suspend fun shellTail(
        gateway: ManagedShellGateway,
        id: String,
        limit: Int,
        admitted: Long,
    ): Outcome<String> {
        val head = gateway.readManagedShellOutput(id, 0, 1)
        if (!current(admitted)) return stale()
        if (head is Outcome.Failed) return head
        val size = (head as Outcome.Ok).value.size
        if (size < 0) return refused()
        val start = (size - limit).coerceAtLeast(0)
        val tail = gateway.readManagedShellOutput(id, start, limit)
        if (!current(admitted)) return stale()
        if (tail is Outcome.Failed) return tail
        val output = (tail as Outcome.Ok).value
        if (output.size < 0 || output.cursor < start || output.cursor > output.size)
            return refused()
        return Outcome.Ok(TerminalText.withoutTerminalCodes(output.text).takeLast(limit))
    }

    override suspend fun readLog(id: String): Outcome<String> {
        val admitted = generation
        return lane.withLock {
            if (!current(admitted)) return@withLock stale()
            val server =
                mutable.value.servers.firstOrNull { it.id == id && it.hasLogs }
                    ?: return@withLock refused()
            val result =
                when (server.source) {
                    DetectedServerSource.Shell -> {
                        val gateway = resolveGateway()
                        if (!current(admitted)) return@withLock stale()
                        if (gateway == null || server.shellId == null) return@withLock refused()
                        shellTail(gateway, server.shellId, 65536, admitted)
                    }
                    DetectedServerSource.Process -> {
                        val runner = runOnPhone ?: return@withLock refused()
                        val path = server.logPath ?: return@withLock refused()
                        when (val read = runner(ListeningPorts.tailScript(path))) {
                            is Outcome.Failed -> read
                            is Outcome.Ok ->
                                Outcome.Ok(
                                    TerminalText.withoutTerminalCodes(read.value).takeLast(65536)
                                )
                        }
                    }
                }
            if (!current(admitted) || server !in mutable.value.servers) stale() else result
        }
    }

    override suspend fun stop(id: String): Outcome<Unit> {
        val admitted = generation
        val result = lane.withLock {
            if (!current(admitted)) return@withLock stale()
            val server =
                mutable.value.servers.firstOrNull { it.id == id && it.canStop }
                    ?: return@withLock refused()
            mutable.value = mutable.value.copy(stopping = id, failure = null)
            try {
                val stopped =
                    when (server.source) {
                        DetectedServerSource.Shell -> stopShell(server, admitted)
                        DetectedServerSource.Process -> {
                            val runner = runOnPhone ?: return@withLock refused()
                            if (!current(admitted)) return@withLock stale()
                            when (
                                val stop =
                                    runner(ListeningPorts.stopScript(server.pid, server.command))
                            ) {
                                is Outcome.Failed -> stop
                                is Outcome.Ok -> Outcome.Ok(Unit)
                            }
                        }
                    }
                if (!current(admitted)) return@withLock stale()
                if (stopped is Outcome.Failed) {
                    mutable.value = mutable.value.copy(failure = stopped.failure)
                } else {
                    mutable.value =
                        mutable.value.copy(
                            servers = mutable.value.servers.filterNot { it.id == id }
                        )
                    server.shellId?.let(shellPorts::remove)
                }
                stopped
            } finally {
                if (current(admitted)) mutable.value = mutable.value.copy(stopping = null)
            }
        }
        if (result is Outcome.Ok && current(admitted)) refresh()
        return result
    }

    private suspend fun stopShell(
        server: DetectedDevelopmentServer,
        admitted: Long,
    ): Outcome<Unit> {
        val gateway = resolveGateway()
        if (!current(admitted)) return stale()
        val id = server.shellId ?: return refused()
        if (gateway == null) return refused()
        val fetched = gateway.getManagedShell(id)
        if (!current(admitted)) return stale()
        if (fetched is Outcome.Failed) return fetched
        val shell = (fetched as Outcome.Ok).value
        if (
            shell != null &&
                (shell.command != server.command ||
                    shell.directory != server.cwd ||
                    shell.startedAt != server.startedAt)
        )
            return refused()
        if (shell == null) return Outcome.Ok(Unit)
        val stopped = gateway.stopManagedShell(id)
        if (!current(admitted)) return stale()
        if (stopped is Outcome.Failed) return stopped
        val remaining = gateway.getManagedShell(id)
        if (!current(admitted)) return stale()
        return when (remaining) {
            is Outcome.Failed -> remaining
            is Outcome.Ok -> if (remaining.value == null) Outcome.Ok(Unit) else refused()
        }
    }

    private fun sameShell(first: ManagedShell, second: ManagedShell): Boolean =
        first.id == second.id &&
            first.command == second.command &&
            first.directory == second.directory &&
            first.startedAt == second.startedAt

    private fun nameFor(command: String): String {
        val tokens = command.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        if (tokens.isEmpty()) return ""
        fun base(token: String): String = token.substringAfterLast('/')
        if (base(tokens.first()) in setOf("npm", "yarn", "pnpm", "bun", "npx", "uv", "cargo", "go"))
            return tokens.take(3).joinToString(" ")
        if (base(tokens.first()).startsWith("python") && tokens.size > 2 && tokens[1] == "-m")
            return tokens[2]
        val interpreters =
            setOf(
                "node",
                "python",
                "python3",
                "ruby",
                "bash",
                "sh",
                "php",
                "java",
                "deno",
                "dart",
                "flutter",
            )
        var index = 0
        while (
            index < tokens.lastIndex &&
                (base(tokens[index]) in interpreters || tokens[index].startsWith('-'))
        ) index++
        return base(tokens[index]).take(40)
    }
}
