package io.github.eslamasabry.oc.engine.agents.handoff

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ports the identity half of test/paseo_agent_handoff_test.dart (B-AGENT-147). */
class HandoffAgentStartTest {
    private class Daemon : HandoffAgentDaemon {
        val providerCalls = mutableListOf<Unit>()
        val creates = mutableListOf<HandoffAgentCreate>()

        /** Agents the daemon knows, by the daemon's own id, with their first user message. */
        val agents = mutableMapOf<String, String>()

        override suspend fun providers(): Outcome<List<HandoffProvider>> {
            providerCalls += Unit
            return listOf(HandoffProvider("claude", listOf("opus"))).ok()
        }

        override suspend fun createAgent(request: HandoffAgentCreate): Outcome<String> {
            creates += request
            agents["durable-agent"] = request.initialPrompt
            return "durable-agent".ok()
        }

        /** What a fresh gateway on the same daemon can open: only daemon ids (`agent: null` otherwise). */
        fun open(id: String): String? = agents[id]
    }

    private val directory = "/work/app"
    private val text = "Read the handoff file and continue."

    @Test
    fun `B-AGENT-147 handoff first prompt preserves live and durable navigation identities`() = runTest {
        val daemon = Daemon()
        val starter = HandoffAgentStarter(daemon, directory)
        starter.draft("draft-1")
        var fenced = false
        val start = starter.promptHandoff("draft-1", text, HandoffModelRef("claude", "opus"), null, null) {
            assertTrue(daemon.creates.isEmpty())
            fenced = true
            true
        }
        assertEquals(HandoffAgentStart.Started("durable-agent"), (start as Outcome.Ok).value)
        assertTrue(fenced)
        val request = daemon.creates.single()
        assertEquals(text, request.initialPrompt)
        assertEquals("opus", request.model)
        assertEquals(directory, request.cwd)
        assertEquals("durable-agent", starter.durableSessionId("draft-1"))
        assertEquals("durable-agent", starter.durableSessionId("durable-agent"))
        assertEquals("durable-agent", starter.navigationSessionId("durable-agent"))
        assertEquals("durable-agent", starter.navigationSessionId("draft-1"))
        assertEquals("unseen-durable-id", starter.navigationSessionId("unseen-durable-id"))
        assertEquals("draft-1", starter.appSessionId("durable-agent"))
    }

    @Test
    fun `B-AGENT-147 the continued conversation opens on another gateway of the same daemon`() = runTest {
        val daemon = Daemon()
        val starter = HandoffAgentStarter(daemon, directory)
        starter.draft("draft-1")
        starter.promptHandoff("draft-1", text, HandoffModelRef("claude", "opus"), null, null) { true }
        // What the engine's send returns for the sheet to open.
        val open = starter.navigationSessionId(starter.durableSessionId("draft-1"))
        // The chat's own gateway is a fresh one that knows only the daemon's ids.
        assertEquals(text, daemon.open(open))
        assertNull(daemon.open("draft-1"))
    }

    @Test
    fun `B-AGENT-147 handoff dispatch fence can refuse creation after provider preflight`() = runTest {
        val daemon = Daemon()
        val starter = HandoffAgentStarter(daemon, directory)
        starter.draft("draft-1")
        val start = starter.promptHandoff("draft-1", "Do not send after scope deletion.", HandoffModelRef("claude", "opus"), null, null) {
            false
        }
        assertEquals(HandoffAgentRefusal.Fenced, assertIs<HandoffAgentStart.Refused>((start as Outcome.Ok).value).reason)
        assertEquals(1, daemon.providerCalls.size)
        assertTrue(daemon.creates.isEmpty())
        assertEquals("draft-1", starter.durableSessionId("draft-1"))
    }
}
