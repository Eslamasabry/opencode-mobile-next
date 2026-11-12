package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.FakeSockets
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fault 2 of the Paseo 0.15.3 device pass: with the daemon stopped, a message ended in "We couldn't confirm your
 * message arrived." although no frame left the phone. The link cannot be opened, so the request fails before the
 * prompt's fence: that is "not sent, Try again" (Failure.Unavailable, the delivery engine's nothing-sent answer),
 * never Uncertain, and it must not block the conversation's next send once the computer answers again.
 */
class PaseoUnreachableSendTest {
    private fun <T> Outcome<T>.ok(): T = assertIs<Outcome.Ok<T>>(this, "expected Ok, got $this").value

    private fun TestScope.daemon0153(sockets: FakeSockets, daemon: FakeDaemon): GatewayHarness {
        daemon.version = "0.15.3"
        daemon.handlers["get_providers_snapshot_request"] = {
            "get_providers_snapshot_response" to Paseo0153Frames.providersSnapshot
        }
        daemon.handlers["fetch_agents_request"] = {
            "fetch_agents_response" to
                jsonOf(
                    "entries" to listOf(mapOf("agent" to Paseo0153Frames.fetchedAgent["agent"])),
                    "pageInfo" to mapOf("hasMore" to false),
                )
        }
        daemon.handlers["fetch_agent_request"] = { "fetch_agent_response" to Paseo0153Frames.fetchedAgent }
        return GatewayHarness(this, daemon, sockets, directory = Paseo0153Frames.PASEO_0153_DIR)
    }

    private val agentId = "e88a5aea-63aa-4dc6-b81f-f42f0ceded13"

    @Test
    fun `fault 2 a first message while the computer is unreachable is not sent, and nothing is written`() = runTest {
        val daemon = FakeDaemon()
        val sockets = FakeSockets(daemon).apply { refuse = PaseoException(PaseoFailureKind.Disconnected) }
        val h = daemon0153(sockets, daemon)
        val draft = h.gateway.createSession(NewSession()).ok().id
        val failed = assertIs<Outcome.Failed>(h.gateway.prompt(draft, OutgoingPrompt("say hi")))
        // Before: ComputerDidNotAnswer, which the delivery engine reads as "may have arrived".
        assertIs<Failure.Unavailable>(failed.failure)
        assertTrue(daemon.sent.isEmpty())

        // The computer is back: the same draft starts, with nothing left over that blocks it.
        sockets.refuse = null
        daemon.handlers["create_agent_request"] = { "status" to Paseo0153Frames.agentCreated }
        assertEquals(PromptAdmission.Confirmed(null), h.gateway.prompt(draft, OutgoingPrompt("say hi")).ok())
        assertEquals(1, daemon.of("create_agent_request").size)
    }

    @Test
    fun `fault 2 a message to an existing conversation after the computer stopped is not sent and writes nothing`() =
        runTest {
            val daemon = FakeDaemon()
            val sockets = FakeSockets(daemon)
            val h = daemon0153(sockets, daemon)
            h.gateway.sessionPage(null, 50).ok()
            // The daemon stops: the socket drops and every reconnect is refused.
            sockets.refuse = PaseoException(PaseoFailureKind.Disconnected)
            daemon.drop()
            h.pump()
            val failed = assertIs<Outcome.Failed>(h.gateway.prompt(SessionId(agentId), OutgoingPrompt("say done")))
            assertIs<Failure.Unavailable>(failed.failure)
            assertTrue(daemon.of("send_agent_message_request").isEmpty())
        }
}
