package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.PaseoAgentSignInTag
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Issue 147 (owner's phone, build 10045): New conversation › Claude Code answered within 3 s "Claude Code didn't
 * confirm this message" and no `claude` ran. The helper had answered the create with `agent_create_failed` (its
 * create handler's only failure answer, Paseo 0.15.3 `session.js handleCreateAgentRequest`); the gateway read that
 * answer as "the computer did not answer" after the bytes left, which delivery keeps as a send of unknown fate.
 */
class PaseoCreateRefusedTest {
    private fun TestScope.refusing(error: String): GatewayHarness =
        GatewayHarness(
            this,
            FakeDaemon().seedDefaults().apply {
                handlers["create_agent_request"] = {
                    "status" to jsonOf("status" to "agent_create_failed", "error" to error, "errorCode" to "unknown")
                }
            },
        ).also {
            it.gateway.startListening()
            it.pump()
        }

    private suspend fun GatewayHarness.start(): Outcome<*> {
        val draft = gateway.createSession(NewSession())
        val id = assertIs<Outcome.Ok<io.github.eslamasabry.oc.core.model.Session>>(draft).value.id
        return gateway.promptNewSessionWithMessageId(id, MessageId("m1"), OutgoingPrompt("Reply with OK")) { true }
    }

    @Test
    fun `I147-AGY-6 a create the helper refuses for a lapsed sign-in is a sign-in refusal, never unconfirmed`() = runTest {
        val h = refusing("Failed to authenticate: OAuth session expired and could not be refreshed")
        val failed = assertIs<Outcome.Failed>(h.start())
        val rejected = assertIs<Failure.Rejected>(failed.failure, "an answer is a refusal: ${failed.failure}")
        assertEquals(null, rejected.status)
        assertEquals(PaseoAgentSignInTag, rejected.tag)
        assertEquals(1, h.daemon.of("create_agent_request").size)
    }

    @Test
    fun `I147-AGY-6b any other refused create is a refusal with the helper's words under Details`() = runTest {
        val h = refusing("Working directory does not exist or is not a directory")
        val failed = assertIs<Outcome.Failed>(h.start())
        val rejected = assertIs<Failure.Rejected>(failed.failure)
        assertEquals(WireTag("Paseo AgentRefused"), rejected.tag)
        assertEquals(true, rejected.details != null)
    }
}
