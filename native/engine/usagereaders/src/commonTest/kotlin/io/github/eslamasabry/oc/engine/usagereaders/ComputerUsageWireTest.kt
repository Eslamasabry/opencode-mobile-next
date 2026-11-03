package io.github.eslamasabry.oc.engine.usagereaders

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.TerminalGateway
import io.github.eslamasabry.oc.core.model.TerminalProcess
import io.github.eslamasabry.oc.engine.usagereaders.computer.COMPUTER_USAGE_TITLE
import io.github.eslamasabry.oc.engine.usagereaders.computer.ComputerStart
import io.github.eslamasabry.oc.engine.usagereaders.computer.ComputerUsageProblem
import io.github.eslamasabry.oc.engine.usagereaders.computer.GatewayComputerTerminalPort
import io.github.eslamasabry.oc.engine.usagereaders.computer.OneShotCommandGateway
import io.github.eslamasabry.oc.engine.usagereaders.computer.OneShotPtyRequest
import io.github.eslamasabry.oc.engine.usagereaders.computer.visibleTerminals
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class ComputerUsageWireTest {
    private fun pty(id: String, title: String) =
        TerminalProcess(id, title, "node", persistentListOf("--input-type=module", "-e", "S"), "/project", true, 18, null)

    /** The OC2 adapter's side of the facet, recording the request body it would send. */
    private class FakeOneShot(val answer: () -> Outcome<TerminalProcess>) : OneShotCommandGateway {
        val bodies = mutableListOf<String>()

        override suspend fun createCommandTerminal(
            command: String,
            arguments: List<String>,
            title: String?,
        ): Outcome<TerminalProcess> {
            bodies += OneShotPtyRequest(command, arguments, title).toJson()
            return answer()
        }
    }

    private object Terminals : TerminalGateway

    @Test
    fun `B-AGENT-076 creates a PTY with the command and arguments apart`() = runTest {
        val args = listOf("--input-type=module", "-e", "SOURCE with \"quotes\" and \n lines")
        val facet = FakeOneShot { TerminalProcess("pty_1", COMPUTER_USAGE_TITLE, "node", args.toImmutableList(), "/", true, 1, null).ok() }
        val started = GatewayComputerTerminalPort(Terminals, facet).start("node", args, COMPUTER_USAGE_TITLE)
        assertEquals("pty_1", assertIs<ComputerStart.Started>(started).process.id)
        val body = DartJson.decode(facet.bodies.single()) as Map<*, *>
        assertEquals("node", body["command"])
        assertEquals(args, body["args"])
        assertEquals(COMPUTER_USAGE_TITLE, body["title"])
    }

    @Test
    fun `B-AGENT-076 leaves the usage check out of the terminal list`() {
        val list = listOf(pty("pty_mine", "Build"), pty("pty_check", COMPUTER_USAGE_TITLE))
        assertEquals(listOf("pty_mine"), visibleTerminals(list).map { it.id })
    }

    @OptIn(RedactorApi::class)
    @Test
    fun `B-AGENT-076 failures map to fixed reasons without server text`() = runTest {
        val detail = Redacted("fake-sensitive-server-detail")
        for ((failure, expected) in listOf(
            Failure.Rejected(404, details = detail) to ComputerUsageProblem.ServerTooOld,
            Failure.NotFound(details = detail) to ComputerUsageProblem.ServerTooOld,
            Failure.Rejected(405, details = detail) to ComputerUsageProblem.ServerTooOld,
            Failure.Server(501, details = detail) to ComputerUsageProblem.ServerTooOld,
            Failure.Server(500, details = detail) to ComputerUsageProblem.NodeMissing,
        )) {
            val port = GatewayComputerTerminalPort(Terminals, FakeOneShot { Outcome.Failed(failure) })
            val started = assertIs<ComputerStart.NotStarted>(port.start("node", emptyList(), COMPUTER_USAGE_TITLE))
            assertEquals(expected, started.problem)
            assertFalse("sensitive" in started.toString())
        }
    }

    @Test
    fun `B-AGENT-076 no answer at all is unreachable`() = runTest {
        for (failure in listOf(Failure.Network(NetworkWhy.Refused), Failure.TimedOut())) {
            val port = GatewayComputerTerminalPort(Terminals, FakeOneShot { Outcome.Failed(failure) })
            val started = assertIs<ComputerStart.NotStarted>(port.start("node", emptyList(), COMPUTER_USAGE_TITLE))
            assertEquals(ComputerUsageProblem.Unreachable, started.problem)
        }
        // An adapter that throws gave no answer either.
        val throwing = GatewayComputerTerminalPort(Terminals, FakeOneShot { error("socket closed") })
        val started = assertIs<ComputerStart.NotStarted>(throwing.start("node", emptyList(), COMPUTER_USAGE_TITLE))
        assertEquals(ComputerUsageProblem.Unreachable, started.problem)
    }

    @Test
    fun `B-AGENT-076 a gateway without the facet is too old, not a crash`() = runTest {
        val started = GatewayComputerTerminalPort(Terminals, oneShot = null).start("node", emptyList(), COMPUTER_USAGE_TITLE)
        assertEquals(ComputerUsageProblem.ServerTooOld, assertIs<ComputerStart.NotStarted>(started).problem)
    }
}
