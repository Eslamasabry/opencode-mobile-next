package io.github.eslamasabry.oc.engine.usagereaders.computer

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.domain.TerminalGateway
import io.github.eslamasabry.oc.core.model.TerminalChannel
import io.github.eslamasabry.oc.core.model.TerminalProcess

/**
 * PORT (optional gateway facet, Flutter `OneShotCommandGateway`): runs one command in a NEW PTY on the server and
 * hands back the terminal so a caller can read its output and remove it. `:core:domain` has no such call yet, so the
 * OpenCode 2 adapter implements this beside its [TerminalGateway]: `POST /api/pty` with [OneShotPtyRequest] as the
 * body (command and arguments as separate fields, never joined into a shell line). Its failures are ordinary
 * [Failure] values; nothing is persisted or logged, and no failure carries command text.
 */
public interface OneShotCommandGateway {
    public suspend fun createCommandTerminal(command: String, arguments: List<String>, title: String?): Outcome<TerminalProcess>
}

/** The OpenCode 2 PTY create body for a one-shot command (wire: `{command, args, title}`). */
public data class OneShotPtyRequest(val command: String, val arguments: List<String>, val title: String?) {
    /** The JSON body. Arguments stay one array element each, whatever quotes or newlines they hold. */
    public fun toJson(): String = DartJson.encode(
        LinkedHashMap<String, Any?>().apply {
            put("command", command)
            put("args", arguments)
            if (title != null) put("title", title)
        },
    )

    override fun toString(): String = "OneShotPtyRequest($command, ${arguments.size} args)"
}

/** The person's terminals: the usage check's own terminal is left out of every list. */
public fun visibleTerminals(terminals: List<TerminalProcess>): List<TerminalProcess> =
    terminals.filter { it.title != COMPUTER_USAGE_TITLE }

/**
 * Why a PTY create failed, as a fixed reason. 404/405/501 (and a gateway's own "unavailable") mean this OpenCode has
 * no terminal API; no HTTP answer at all means the server did not answer; anything else (a refused command) is "the
 * command did not run" so the login-shell fallback can try. Never reads [Failure.details].
 */
public fun classifyComputerStartFailure(failure: Failure): ComputerUsageProblem = when (failure) {
    is Failure.Unavailable, is Failure.NotFound -> ComputerUsageProblem.ServerTooOld
    is Failure.Rejected -> if (failure.status in TOO_OLD) ComputerUsageProblem.ServerTooOld else ComputerUsageProblem.NodeMissing
    is Failure.Server -> if (failure.status in TOO_OLD) ComputerUsageProblem.ServerTooOld else ComputerUsageProblem.NodeMissing
    is Failure.Network, is Failure.TimedOut, is Failure.Unknown, is Failure.ComputerDidNotAnswer ->
        ComputerUsageProblem.Unreachable
    else -> ComputerUsageProblem.NodeMissing
}

private val TOO_OLD = setOf(404, 405, 501)

/**
 * The terminal port over a saved server's gateway. [oneShot] is null when the gateway lacks the facet: the check then
 * says "too old" instead of failing. [onClose] releases whatever the caller opened for this check.
 */
public class GatewayComputerTerminalPort(
    private val terminals: TerminalGateway,
    private val oneShot: OneShotCommandGateway?,
    private val onClose: () -> Unit = {},
) : ComputerTerminalPort {
    override suspend fun start(command: String, arguments: List<String>, title: String): ComputerStart {
        val facet = oneShot ?: return ComputerStart.NotStarted(ComputerUsageProblem.ServerTooOld)
        // A throwing adapter is a transport that gave no answer.
        val created: Outcome<TerminalProcess> =
            when (val call = attempt({ Failure.Unknown() }) { facet.createCommandTerminal(command, arguments, title) }) {
                is Outcome.Ok -> call.value
                is Outcome.Failed -> call
            }
        return when (created) {
            is Outcome.Ok -> ComputerStart.Started(created.value)
            is Outcome.Failed -> ComputerStart.NotStarted(classifyComputerStartFailure(created.failure))
        }
    }

    override suspend fun connect(id: String): Outcome<TerminalChannel> = terminals.connectTerminal(id)

    override suspend fun remove(id: String): Outcome<Unit> = terminals.removeTerminal(id)

    override fun close(): Unit = onClose()
}
