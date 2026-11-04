package io.github.eslamasabry.oc.engine.runtime.procs

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.engine.runtime.services.TerminalText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The phone-only part of detected development servers; server-owned shells use a separate
 * capability.
 */
public data class DetectedPhoneServersState(
    val servers: List<LocalServerProcess> = emptyList(),
    val scanned: Boolean = false,
    val scanning: Boolean = false,
    val stopping: Int? = null,
    val failure: Failure? = null,
)

/**
 * B-LINUX-054..059. One serial command lane for the phone inventory, bounded logs and exact-PID
 * stop. A failed scan retains the last successful inventory. Leaving the page invalidates every
 * pending answer without erasing a newer page's state. The runner is supplied only for the phone's
 * own Linux, never for a remote server's process table.
 */
public interface DetectedPhoneServers {
    public val state: StateFlow<DetectedPhoneServersState>

    public fun invalidate()

    public suspend fun refresh(): Outcome<Unit>

    public suspend fun readLog(server: LocalServerProcess): Outcome<String>

    public suspend fun stop(server: LocalServerProcess): Outcome<Unit>
}

/**
 * A runner is bound to one real phone runtime; the controller never fabricates data for an absent
 * platform.
 */
public fun detectedPhoneServers(run: suspend (String) -> Outcome<String>): DetectedPhoneServers =
    DefaultDetectedPhoneServers(run)

internal class DefaultDetectedPhoneServers(private val run: suspend (String) -> Outcome<String>) :
    DetectedPhoneServers {
    private val lane = Mutex()
    private var generation = 0L
    private val mutable = MutableStateFlow(DetectedPhoneServersState())
    override val state: StateFlow<DetectedPhoneServersState> = mutable.asStateFlow()

    override fun invalidate() {
        generation++
        mutable.value = DetectedPhoneServersState()
    }

    override suspend fun refresh(): Outcome<Unit> {
        // A scan overlapping our stop neither replaces its inventory nor invents a phone-scan
        // error.
        if (mutable.value.stopping != null || mutable.value.scanning) return Outcome.Ok(Unit)
        val admitted = generation
        return lane.withLock {
            if (admitted != generation) return@withLock stale()
            mutable.value = mutable.value.copy(scanning = true, failure = null)
            when (val answer = run(ListeningPorts.script())) {
                is Outcome.Failed -> {
                    if (admitted == generation)
                        mutable.value =
                            mutable.value.copy(scanning = false, failure = answer.failure)
                    answer
                }
                is Outcome.Ok -> {
                    if (admitted != generation) return@withLock stale()
                    mutable.value =
                        mutable.value.copy(
                            servers = ListeningPorts.parse(answer.value),
                            scanned = true,
                            scanning = false,
                        )
                    Outcome.Ok(Unit)
                }
            }
        }
    }

    /**
     * Uses only the current row's stdout file; paths from an obsolete page cannot trigger a read.
     */
    override suspend fun readLog(server: LocalServerProcess): Outcome<String> {
        val admitted = generation
        return lane.withLock {
            if (
                admitted != generation || server !in mutable.value.servers || server.logPath == null
            )
                return@withLock stale()
            when (val answer = run(ListeningPorts.tailScript(server.logPath))) {
                is Outcome.Failed -> answer
                is Outcome.Ok -> {
                    if (admitted != generation || server !in mutable.value.servers)
                        return@withLock stale()
                    Outcome.Ok(TerminalText.withoutTerminalCodes(answer.value).takeLast(65536))
                }
            }
        }
    }

    /**
     * The script compares the displayed command before signaling this exact pid; no process-name
     * kill exists.
     */
    override suspend fun stop(server: LocalServerProcess): Outcome<Unit> {
        val admitted = generation
        return lane.withLock {
            if (
                admitted != generation ||
                    server !in mutable.value.servers ||
                    server.pid <= 1 ||
                    server.command.isBlank()
            )
                return@withLock stale()
            mutable.value = mutable.value.copy(stopping = server.pid, failure = null)
            when (val answer = run(ListeningPorts.stopScript(server.pid, server.command))) {
                is Outcome.Failed -> {
                    if (admitted == generation)
                        mutable.value =
                            mutable.value.copy(stopping = null, failure = answer.failure)
                    answer
                }
                is Outcome.Ok -> {
                    if (admitted != generation) return@withLock stale()
                    mutable.value =
                        mutable.value.copy(
                            servers = mutable.value.servers.filterNot { it == server },
                            stopping = null,
                        )
                    Outcome.Ok(Unit)
                }
            }
        }
    }

    private fun stale(): Outcome.Failed =
        Outcome.Failed(Failure.Runtime(RuntimeProblem.OwnershipUnknown))
}
