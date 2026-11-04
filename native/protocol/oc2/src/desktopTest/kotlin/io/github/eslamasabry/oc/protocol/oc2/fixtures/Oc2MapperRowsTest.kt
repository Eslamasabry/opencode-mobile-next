package io.github.eslamasabry.oc.protocol.oc2.fixtures

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.transcript.*
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.protocol.oc2.live.Oc2History
import io.github.eslamasabry.oc.protocol.oc2.live.RowServer
import io.github.eslamasabry.oc.protocol.oc2.live.json
import java.io.File
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/** Flutter test/api2_gateway_mappers_test.dart over the same captured fixtures (B-PROTO-017), plus the B-PROTO-037 source guard. */
class Oc2MapperRowsTest {
    private val session = "ses_fb534b6a0ffeJ5pNlwiesRdWV2"
    private val history = Oc2History(Redactor())

    private fun map(json: JsonObject): MessageWithParts = assertNotNull(history.message(session, json))

    private fun map(text: String): MessageWithParts = map(Json.parseToJsonElement(text) as JsonObject)

    private fun captured(): List<MessageWithParts> = Fixtures.dataList("api2/messages.json").map(::map)

    /** One assistant message whose content is a single tool entry with [state]. */
    private fun tool(state: String, name: String = "screenshot"): ToolState =
        assertNotNull(
            map("""{"id":"msg_t","type":"assistant","time":{"created":1,"completed":2},"content":[{"type":"tool","id":"call_1","name":"$name","state":$state}]}""")
                .parts.single().tool
        )

    @Test
    fun `B-PROTO-017 the captured session page maps onto the app session`() = runTest {
        val server = RowServer { json(Fixtures.text("api2/sessions_page.json")) }
        val page = assertIs<Outcome.Ok<ServerPage<Session>>>(server.gateway.sessionPage()).value
        val first = page.items.first()
        assertTrue(first.id.raw.startsWith("ses_"))
        assertEquals("Repo test strategy overview", first.title)
        assertEquals("/home/dev/projects/oc_app", first.directory)
        assertFalse(first.projectId?.raw.isNullOrEmpty())
        assertNull(first.time?.archivedMs)
        assertNotNull(first.time?.createdMs)
        server.gateway.close()
    }

    @Test
    fun `B-PROTO-017 a user message carries text and file attachment parts`() {
        val user = captured().first()
        assertEquals(Role.User, user.info.role)
        assertEquals(session, user.info.sessionId)
        val text = user.parts.first { it.type == PartType.Text }
        assertTrue("Reply with exactly the word: pong" in text.text)
        assertTrue(text.isRenderable)
        val file = user.parts.first { it.type == PartType.File }
        assertEquals("text/plain", file.mime)
        assertEquals("att.txt", file.filename)
        assertTrue(file.url!!.startsWith("data:text/plain;base64,"))
    }

    @Test
    fun `B-PROTO-017 the assistant capture maps reasoning text and tool content`() {
        val assistant = captured()[1]
        assertEquals(Role.Assistant, assistant.info.role)
        assertEquals("build", assistant.info.agent)
        assertEquals("openai", assistant.info.providerId)
        assertEquals("gpt-5.6-sol", assistant.info.modelId)
        assertNotNull(assistant.info.time?.completedMs)
        assertNull(assistant.info.errorText)
        assertEquals(3, assistant.parts.size)
        assertEquals(PartType.Reasoning, assistant.parts[0].type)
        assertEquals("reasoning-0", assistant.parts[0].id)
        assertEquals(PartType.Text, assistant.parts[1].type)
        assertEquals("text-0", assistant.parts[1].id)
        assertEquals("pong.", assistant.parts[1].text)
        val tool = assistant.parts[2]
        assertEquals(PartType.Tool, tool.type)
        assertEquals("read", tool.toolName)
        assertFalse(tool.callId.isNullOrEmpty())
        assertEquals(tool.callId, tool.id)
        assertEquals(ToolStatus.Completed, tool.tool!!.status)
        assertEquals("README.md", tool.tool!!.input["path"]!!.jsonPrimitive.content)
        assertTrue("OpenCode for Android" in tool.tool!!.output!!)
    }

    @Test
    fun `B-PROTO-017 the single assistant fixture parses tokens and cost`() {
        val mapped = map(Fixtures.data("api2/message_assistant.json"))
        assertEquals(Role.Assistant, mapped.info.role)
        assertTrue(mapped.info.tokens.input > 0)
        assertNotNull(mapped.info.time?.completedMs)
    }

    @Test
    fun `B-PROTO-017 paginated fixture pages map without loss of order`() {
        val raw = Fixtures.dataList("api2/messages_page1.json") + Fixtures.dataList("api2/messages_page2.json")
        val mapped = raw.map(::map)
        assertEquals(raw.size, mapped.size)
        assertEquals(raw.map { it["id"]!!.jsonPrimitive.content }, mapped.map { it.info.id })
        for (message in mapped) {
            assertTrue(message.info.id.startsWith("msg_"))
            assertEquals(session, message.info.sessionId)
        }
    }

    @Test
    fun `B-PROTO-017 a shell message maps to a shell tool part and a failed shell to a tool error`() {
        val ok = map("""{"id":"msg_shell","type":"shell","time":{"created":1,"completed":2},"shellID":"sh_1","command":"ls -la","status":"exited","exit":0,"output":{"output":"README.md\nlib\n","truncated":false}}""")
        assertEquals(Role.Assistant, ok.info.role)
        val part = ok.parts.single()
        assertEquals(PartType.Tool, part.type)
        assertEquals("shell", part.toolName)
        assertEquals(ToolStatus.Completed, part.tool!!.status)
        assertEquals("ls -la", part.tool!!.input["command"]!!.jsonPrimitive.content)
        assertTrue("README.md" in part.tool!!.output!!)
        val failed = map("""{"id":"msg_shell2","type":"shell","time":{"created":1,"completed":2},"command":"false","status":"exited","exit":1,"output":{"output":"boom","truncated":false}}""").parts.single()
        assertEquals(ToolStatus.Error, failed.tool!!.status)
        assertTrue("boom" in failed.tool!!.output!!)
    }

    @Test
    fun `B-PROTO-017 shell metadata preserves the raw v2 status and shell id`() {
        val part = map("""{"id":"msg_shell3","type":"shell","time":{"created":1,"completed":2},"shellID":"sh_9","command":"sleep 900","status":"timeout","output":{"output":"","truncated":true}}""").parts.single()
        val metadata = part.tool!!.metadata!!
        assertEquals(ToolStatus.Error, part.tool!!.status)
        assertEquals(JsonPrimitive("timeout"), metadata["shellStatus"])
        assertEquals(JsonPrimitive("sh_9"), metadata["shellID"])
        assertEquals(JsonPrimitive(true), metadata["truncated"])
    }

    @Test
    fun `B-PROTO-017 a tool error carries the structured message`() {
        val state = tool("""{"status":"error","input":{"path":"x"},"error":{"type":"ToolError","message":"file missing"}}""", "read")
        assertEquals(ToolStatus.Error, state.status)
        assertTrue("file missing" in state.output.orEmpty(), state.toString())
    }

    @Test
    fun `B-PROTO-017 tool file results surface as output files`() {
        val state = tool("""{"status":"completed","input":{},"content":[{"type":"text","text":"wrote image"},{"type":"file","uri":"file:///tmp/shot.png","mime":"image/png","name":"shot.png"}]}""")
        assertEquals(ToolStatus.Completed, state.status)
        assertEquals("wrote image", state.output)
        val file = state.outputFiles.single()
        assertEquals("image/png", file.mime)
        assertEquals("shot.png", file.filename)
    }

    @Test
    fun `B-PROTO-017 interleaved tool content survives as ordered segments`() {
        val state = tool("""{"status":"completed","input":{},"content":[{"type":"text","text":"first render:"},{"type":"file","uri":"file:///tmp/before.png","mime":"image/png","name":"before.png"},{"type":"text","text":"after the fix:"},{"type":"file","uri":"file:///tmp/after.png","mime":"image/png","name":"after.png"}]}""")
        assertEquals(4, state.segments.size)
        assertEquals(ToolSegment.Text("first render:"), state.segments[0])
        assertEquals("before.png", assertIs<ToolSegment.File>(state.segments[1]).file.filename)
        assertEquals(ToolSegment.Text("after the fix:"), state.segments[2])
        assertEquals("after.png", assertIs<ToolSegment.File>(state.segments[3]).file.filename)
        assertEquals("first render:\nafter the fix:", state.output)
        assertEquals(2, state.outputFiles.size)
    }

    @Test
    fun `B-PROTO-017 streaming tool input maps to the pending preview`() {
        val state = tool("""{"status":"streaming","input":"{\"pa"}""")
        assertEquals(ToolStatus.Pending, state.status)
        assertEquals("{\"pa", state.inputRaw)
    }

    @Test
    fun `B-PROTO-037 the transport source offers no way to put credentials in a URL`() {
        val sources =
            File(Fixtures.repoRoot, "native/protocol/oc2/src/commonMain/kotlin")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .toList()
        assertTrue(sources.isNotEmpty())
        for (file in sources) {
            val code = file.readLines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }.joinToString("\n")
            for (banned in listOf("auth_token", "withAuthToken", "authTokenUri")) assertFalse(banned in code, "${file.name}: $banned")
        }
    }
}
