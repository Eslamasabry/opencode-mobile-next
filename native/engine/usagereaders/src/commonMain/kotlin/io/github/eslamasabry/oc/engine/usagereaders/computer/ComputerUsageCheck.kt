package io.github.eslamasabry.oc.engine.usagereaders.computer

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.model.TerminalChannel
import io.github.eslamasabry.oc.core.model.TerminalProcess
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** What one start call gave: a terminal, or a plain reason it did not start. */
public sealed interface ComputerStart {
    public data class Started(val process: TerminalProcess) : ComputerStart

    public data class NotStarted(val problem: ComputerUsageProblem) : ComputerStart
}

/**
 * PORT. One short-lived way to run a command on a server and read its output. The production one is
 * [GatewayComputerTerminalPort] over the saved server's gateway; tests fake it.
 */
public interface ComputerTerminalPort {
    /** Starts [command] with [arguments] (never joined into a shell line) in a NEW terminal titled [title]. */
    public suspend fun start(command: String, arguments: List<String>, title: String): ComputerStart

    public suspend fun connect(id: String): Outcome<TerminalChannel>

    public suspend fun remove(id: String): Outcome<Unit>

    /** Releases the connection the port opened. */
    public fun close()
}

private const val MAX_SEEN = 256 * 1024
private const val CLOSE_WAIT_MS = 2_000L
private const val REMOVE_WAIT_MS = 5_000L

/** The default time one check may take. */
public const val COMPUTER_USAGE_TIMEOUT_MS: Long = 25_000L

/** The two ways the reader is run: `node` first, then a login shell (so an nvm PATH is found). */
public fun computerUsageAttempts(source: String): List<Pair<String, List<String>>> = listOf(
    "node" to listOf("--input-type=module", "-e", source),
    "bash" to listOf(
        "-lc",
        "command -v node >/dev/null 2>&1 || " +
            "{ echo \"node: command not found\"; sleep 1; exit 127; }; " +
            "node --input-type=module -e \"\$1\"; s=\$?; sleep 1; exit \$s",
        "_",
        source,
    ),
)

/**
 * Runs the reader on a computer through [port]: `node` first, then once more through a login shell. The terminal is
 * removed in every case, including a timeout, a removal that fails and a socket that never closes. [elapsedMs] is a
 * monotonic clock for the shared deadline.
 */
public suspend fun runComputerUsageCheck(
    port: ComputerTerminalPort,
    source: String,
    elapsedMs: () -> Long,
    timeoutMs: Long = COMPUTER_USAGE_TIMEOUT_MS,
): ComputerUsageResult {
    val deadline = elapsedMs() + timeoutMs
    fun left(): Long = (deadline - elapsedMs()).coerceAtLeast(0)

    var last = ComputerUsageProblem.NoAnswer
    for ((index, step) in computerUsageAttempts(source).withIndex()) {
        if (left() <= 0) break
        val run = runAttempt(port, step.first, step.second, left())
        run.result?.let { return it }
        last = if (index == 0) run.problem else worse(last, run.problem)
        // The server cannot do this at all, or does not answer: another shell would only repeat that.
        if (run.problem == ComputerUsageProblem.ServerTooOld || run.problem == ComputerUsageProblem.Unreachable) {
            return ComputerUsageResult(problem = run.problem)
        }
    }
    return ComputerUsageResult(problem = last)
}

private fun worse(first: ComputerUsageProblem, second: ComputerUsageProblem): ComputerUsageProblem =
    if (second == ComputerUsageProblem.NoAnswer) first else second

private class Attempt(val result: ComputerUsageResult?, val problem: ComputerUsageProblem)

private suspend fun runAttempt(
    port: ComputerTerminalPort,
    command: String,
    arguments: List<String>,
    timeoutMs: Long,
): Attempt {
    var id: String? = null
    var channel: TerminalChannel? = null
    val seen = StringBuilder()
    try {
        val started = withTimeoutOrNull(timeoutMs) { port.start(command, arguments, COMPUTER_USAGE_TITLE) }
            ?: return Attempt(null, ComputerUsageProblem.Unreachable)
        val process = when (started) {
            is ComputerStart.NotStarted -> return Attempt(null, started.problem)
            is ComputerStart.Started -> started.process
        }
        id = process.id
        // A connect that fails means it already ended (a missing command exits at once): no output to read.
        val connected: Outcome<TerminalChannel>? = withTimeoutOrNull(timeoutMs) {
            when (val call = attempt({ Failure.Unexpected() }) { port.connect(process.id) }) {
                is Outcome.Ok -> call.value
                is Outcome.Failed -> call
            }
        }
        val open = (connected as? Outcome.Ok<TerminalChannel>)?.value
            ?: return Attempt(null, ComputerUsageProblem.NodeMissing)
        channel = open
        val parsed = withTimeoutOrNull(timeoutMs) {
            open.output
                .catch { }
                .mapNotNull { chunk ->
                    if (seen.length < MAX_SEEN) seen.append(chunk)
                    parseComputerUsageOutput(seen.toString())
                }
                .firstOrNull()
        }
        return if (parsed != null) {
            Attempt(parsed, ComputerUsageProblem.NoAnswer)
        } else {
            Attempt(null, classifyComputerUsageOutput(seen.toString()))
        }
    } finally {
        seen.clear()
        // Teardown is bounded: a socket that does not close must not hold the removal, which stops the process.
        withContext(NonCancellable) {
            channel?.let { open ->
                withTimeoutOrNull(CLOSE_WAIT_MS) { attempt({ Failure.Unexpected() }) { open.close() } }
            }
            id?.let { terminal ->
                withTimeoutOrNull(REMOVE_WAIT_MS) { attempt({ Failure.Unexpected() }) { port.remove(terminal) } }
            }
        }
    }
}
