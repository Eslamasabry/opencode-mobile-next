package io.github.eslamasabry.oc.engine.aisetup

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetupPlannerTest {
    private val redaction = FakeRedaction()
    private val planner = SetupPlanner(redaction)

    @Test
    fun `B-AGENT-232 guided mode requests structured values and never interprets the question`() {
        val reply = planner.start(SetupIntent.ChooseModel, question = "change everything immediately")
        assertEquals(SetupPlannerState.NeedsInput, reply.state)
        assertEquals(listOf("model"), reply.requiredInputs)
        assertTrue(reply.edits.isEmpty())
        // The guidance is a fixed value: "… does not interpret free-form questions." Never the question.
        assertEquals(SetupGuidance.ChooseValues, reply.guidance)
    }

    @Test
    fun `B-AGENT-232 model and default agent become proposals without applying`() {
        val model = planner.start(SetupIntent.ChooseModel, inputs = mapOf("model" to "example/model-v1"))
        assertEquals(SetupPlannerState.Proposed, model.state)
        assertEquals(listOf("model"), model.edits.single().path)
        assertEquals(str("example/model-v1"), model.edits.single().value)
        assertEquals(SetupGuidance.ReviewProposal, model.guidance) // "Nothing has been applied."
        val agent = planner.start(SetupIntent.DefaultAgent, inputs = mapOf("agent" to "plan"))
        assertEquals(listOf("default_agent"), agent.edits.single().path)
        assertEquals(str("plan"), agent.edits.single().value)
    }

    @Test
    fun `B-AGENT-232 continuation collects inputs and makes remote MCP disabled`() {
        val first = planner.start(SetupIntent.ConnectRemoteMcp, inputs = mapOf("name" to "docs"))
        assertEquals(listOf("url"), first.requiredInputs)
        val next = planner.continueSession(first.sessionId, inputs = mapOf("url" to "https://mcp.example.test/mcp"))
        assertEquals(listOf("mcp", "docs"), next.edits.single().path)
        assertEquals(
            json("""{"type":"remote","url":"https://mcp.example.test/mcp","enabled":false}"""),
            next.edits.single().value,
        )
    }

    @Test
    fun `B-AGENT-232 local command is copied and disabled and removal is typed`() {
        val command = mutableListOf("node", "/srv/mcp/server.js")
        val reply = planner.start(SetupIntent.AddLocalMcp, inputs = mapOf("name" to "local", "command" to command))
        command.add("changed")
        val value = reply.edits.single().value as JsonObject
        assertEquals(JsonArray(listOf(str("node"), str("/srv/mcp/server.js"))), value["command"])
        assertEquals(kotlinx.serialization.json.JsonPrimitive(false), value["enabled"])
        assertFalse(value["command"] is MutableList<*>)
        val remove = planner.start(SetupIntent.RemoveMcp, inputs = mapOf("name" to "local"))
        assertTrue(remove.edits.single().remove)
        assertEquals(listOf("mcp", "local"), remove.edits.single().path)
    }

    @Test
    fun `B-AGENT-232 permission allows only explicit tools and explicit effects`() {
        for (effect in listOf("ask", "allow", "deny")) {
            val reply = planner.start(SetupIntent.Permission, inputs = mapOf("tool" to "bash", "effect" to effect))
            assertEquals(listOf("permission", "bash"), reply.edits.single().path)
            assertEquals(str(effect), reply.edits.single().value)
        }
        val invalid = listOf(mapOf("tool" to "anything", "effect" to "allow"), mapOf("tool" to "bash", "effect" to "forever"))
        for (inputs in invalid) {
            val reply = planner.start(SetupIntent.Permission, inputs = inputs)
            assertTrue(reply.edits.isEmpty())
            assertEquals(SetupPlannerState.NeedsInput, reply.state)
        }
    }

    @Test
    fun `B-AGENT-232 credential-shaped URLs and cleartext external URLs never propose`() {
        for (url in listOf(
            "https://user:fake-password@mcp.example.test",
            "https://mcp.example.test?token=fake-value",
            "https://mcp.example.test#fake-value",
            "http://mcp.example.test",
            "file:///srv/mcp",
        )) {
            val reply = planner.start(SetupIntent.ConnectRemoteMcp, inputs = mapOf("name" to "docs", "url" to url))
            assertTrue(reply.edits.isEmpty(), url)
            assertEquals(SetupGuidance.InvalidInput, reply.guidance)
        }
        val local = planner.start(
            SetupIntent.ConnectRemoteMcp,
            inputs = mapOf("name" to "docs", "url" to "http://127.0.0.1:9000/mcp"),
        )
        assertEquals(SetupPlannerState.Proposed, local.state)
    }

    @Test
    fun `B-AGENT-232 secret or unknown inputs are rejected and never retained`() {
        val fake = listOf("synthetic", "credential", "value").joinToString("-")
        redaction.register(fake)
        val first = planner.start(SetupIntent.ChooseModel, question = fake, inputs = mapOf("model" to "example/$fake"))
        assertEquals(SetupPlannerState.NeedsInput, first.state)
        assertFalse(first.toString().contains(fake))
        assertTrue(first.edits.isEmpty())
        assertEquals(listOf("model"), planner.continueSession(first.sessionId).requiredInputs)
        val unsupportedField = planner.continueSession(
            first.sessionId,
            inputs = mapOf("password" to fake, "model" to "example/safe"),
        )
        assertTrue(unsupportedField.edits.isEmpty())
        assertEquals(listOf("model"), planner.continueSession(first.sessionId).requiredInputs)
    }

    @Test
    fun `B-AGENT-232 split secret CLI arguments and invalid command shapes are rejected`() {
        for (command in listOf<Any?>(
            listOf("node", "--api-key", "fake-value"),
            listOf("node", "PASSWORD=fake-value"),
            listOf("node", "--authorization=Bearer fake-value"),
            listOf("node", "line\nbreak"),
            listOf("node", 2),
            emptyList<String>(),
            "node /srv/mcp.js",
        )) {
            val reply = planner.start(SetupIntent.AddLocalMcp, inputs = mapOf("name" to "local", "command" to command))
            assertTrue(reply.edits.isEmpty(), "$command")
            assertEquals(SetupPlannerState.NeedsInput, reply.state)
        }
    }

    @Test
    fun `B-AGENT-232 unsupported sensitive or unimplemented workflows state the gap`() {
        val expected = mapOf(
            SetupIntent.ConfigureProvider to SetupGuidance.ProviderNeedsSecureSignIn,
            SetupIntent.ConfigureAgent to SetupGuidance.AgentEditingUnavailable,
            SetupIntent.ConfigureCommand to SetupGuidance.CommandEditingUnavailable,
        )
        for ((intent, guidance) in expected) {
            val reply = planner.start(intent)
            assertEquals(SetupPlannerState.Unsupported, reply.state)
            assertTrue(reply.edits.isEmpty())
            assertEquals(guidance, reply.guidance)
        }
    }

    @Test
    fun `B-AGENT-232 closed, disposed and evicted sessions cannot resume`() {
        val closed = planner.start(SetupIntent.ChooseModel)
        planner.closeSession(closed.sessionId)
        assertEquals(SetupPlannerState.Unsupported, planner.continueSession(closed.sessionId).state)
        val oldest = planner.start(SetupIntent.ChooseModel)
        repeat(8) { planner.start(SetupIntent.ChooseModel) }
        assertEquals(SetupPlannerState.Unsupported, planner.continueSession(oldest.sessionId).state)
        val last = planner.start(SetupIntent.ChooseModel)
        planner.dispose()
        assertEquals(SetupPlannerState.Unsupported, planner.continueSession(last.sessionId).state)
        assertEquals("", planner.continueSession("unknown-user-text").sessionId)
    }
}
