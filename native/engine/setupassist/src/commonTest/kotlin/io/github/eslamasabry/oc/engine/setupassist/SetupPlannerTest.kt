package io.github.eslamasabry.oc.engine.setupassist

import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Port of test/setup_planner_test.dart (rows B-SEC-167). */
class SetupPlannerTest {
    private val redactor = Redactor()
    private val planner = SetupPlanner(redactor)

    private fun s(value: String): JsonElement = JsonPrimitive(value)

    @Test
    fun `B-SEC-167 credential-shaped URLs and cleartext external URLs never propose`() {
        for (url in listOf(
            "https://user:fake-password@mcp.example.test",
            "https://mcp.example.test?token=fake-value",
            "https://mcp.example.test#fake-value",
            "http://mcp.example.test",
            "file:///srv/mcp",
        )) {
            val reply = planner.start(SetupIntent.ConnectRemoteMcp, inputs = mapOf("name" to s("docs"), "url" to s(url)))
            assertTrue(reply.edits.isEmpty(), url)
            assertFalse(reply.guidance.contains(url))
        }
        val local = planner.start(SetupIntent.ConnectRemoteMcp, inputs = mapOf("name" to s("docs"), "url" to s("http://localhost:9000/mcp")))
        assertEquals(SetupPlannerState.Proposed, local.state)
        assertEquals(listOf("mcp", "docs"), local.edits.single().path)
    }

    @Test
    fun `B-SEC-167 secret or unknown inputs are rejected and never retained`() {
        val fake = listOf("synthetic", "credential", "value").joinToString("-")
        redactor.registerSecret(fake)
        val first = planner.start(SetupIntent.ChooseModel, question = fake, inputs = mapOf("model" to s("example/$fake")))
        assertEquals(SetupPlannerState.NeedsInput, first.state)
        assertFalse(first.guidance.contains(fake))
        assertTrue(first.edits.isEmpty())
        val second = planner.continueSession(first.sessionId)
        assertEquals(listOf("model"), second.requiredInputs)
        val unsupportedField = planner.continueSession(first.sessionId, inputs = mapOf("password" to s(fake), "model" to s("example/safe")))
        assertTrue(unsupportedField.edits.isEmpty())
        assertEquals(listOf("model"), planner.continueSession(first.sessionId).requiredInputs)
    }

    @Test
    fun `B-SEC-167 split secret CLI arguments and invalid command shapes are rejected`() {
        for (command in listOf<JsonElement>(
            JsonArray(listOf(s("node"), s("--api-key"), s("fake-value"))),
            JsonArray(listOf(s("node"), s("PASSWORD=fake-value"))),
            JsonArray(listOf(s("node"), s("--authorization=Bearer fake-value"))),
            JsonArray(listOf(s("node"), s("line\nbreak"))),
            JsonArray(listOf(s("node"), JsonPrimitive(2))),
            JsonArray(emptyList()),
            s("node /srv/mcp.js"),
        )) {
            val reply = planner.start(SetupIntent.AddLocalMcp, inputs = mapOf("name" to s("local"), "command" to command))
            assertTrue(reply.edits.isEmpty(), command.toString())
            assertEquals(SetupPlannerState.NeedsInput, reply.state)
        }
        val ok = planner.start(SetupIntent.AddLocalMcp, inputs = mapOf("name" to s("local"), "command" to JsonArray(listOf(s("node"), s("/srv/mcp.js")))))
        assertEquals(SetupPlannerState.Proposed, ok.state)
        assertEquals(obj("""{"type":"local","command":["node","/srv/mcp.js"],"enabled":false}"""), ok.edits.single().value)
    }

    @Test
    fun `B-SEC-167 unsupported sensitive or unimplemented workflows state the gap`() {
        for (intent in listOf(SetupIntent.ConfigureProvider, SetupIntent.ConfigureAgent, SetupIntent.ConfigureCommand)) {
            val reply = planner.start(intent)
            assertEquals(SetupPlannerState.Unsupported, reply.state)
            assertTrue(reply.edits.isEmpty())
            assertTrue(reply.guidance.isNotEmpty())
        }
    }

    @Test
    fun `B-SEC-167 closed, disposed and evicted sessions cannot resume`() {
        val closed = planner.start(SetupIntent.ChooseModel)
        planner.closeSession(closed.sessionId)
        assertEquals(SetupPlannerState.Unsupported, planner.continueSession(closed.sessionId).state)
        val oldest = planner.start(SetupIntent.ChooseModel)
        repeat(8) { planner.start(SetupIntent.ChooseModel) }
        assertEquals(SetupPlannerState.Unsupported, planner.continueSession(oldest.sessionId).state)
        val last = planner.start(SetupIntent.ChooseModel)
        planner.dispose()
        assertEquals(SetupPlannerState.Unsupported, planner.continueSession(last.sessionId).state)
        assertTrue(planner.continueSession("unknown-user-text").sessionId.isEmpty())
    }
}
