package io.github.eslamasabry.oc.protocol.paseo.mappers

import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.serialization.json.JsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** History mapping (test/paseo_gateway_test.dart:946), test/paseo_compaction_test.dart and test/paseo_images_test.dart. */
class PaseoTimelineMapperTest {
    private val dir = "/work/app"

    private fun at(ts: String) = kotlin.time.Instant.parse(ts).toEpochMilliseconds()

    private val history = jsonOf(
        "agentId" to "a1",
        "epoch" to "epoch-1",
        "endCursor" to mapOf("epoch" to "epoch-1", "seq" to 12),
        "entries" to listOf(
            mapOf("provider" to "claude", "item" to mapOf("type" to "user_message", "text" to "Read it", "messageId" to "u1"), "timestamp" to "2026-09-19T03:35:27.922Z", "seqStart" to 1),
            mapOf(
                "provider" to "claude",
                "item" to mapOf(
                    "type" to "tool_call",
                    "callId" to "toolu_1",
                    "name" to "Read",
                    "status" to "completed",
                    "detail" to mapOf("type" to "read", "filePath" to "$dir/README.md", "content" to "# spike"),
                ),
                "timestamp" to "2026-09-19T03:35:30.000Z",
                "seqStart" to 2,
            ),
            mapOf("provider" to "claude", "item" to mapOf("type" to "reasoning", "text" to "Thinking"), "timestamp" to "2026-09-19T03:35:31.000Z", "seqStart" to 5),
            mapOf("provider" to "claude", "item" to mapOf("type" to "assistant_message", "text" to "Done.", "messageId" to "msg_1"), "timestamp" to "2026-09-19T03:35:47.208Z", "seqStart" to 9),
        ),
    )

    @Test
    fun `B-CHAT-070 history maps the merged timeline with stable ids, roles and finished replies`() {
        val timeline = paseoTimeline("a1", history, busy = false)
        val messages = timeline.messages
        assertEquals(listOf("u1", "toolu_1", "r:5", "msg_1"), messages.map { it.info.id })
        assertEquals(listOf(Role.User, Role.Assistant, Role.Assistant, Role.Assistant), messages.map { it.info.role })
        assertEquals("read", messages[1].parts.single().toolName)
        assertEquals("# spike", messages[1].toolState.output)
        assertEquals("toolu_1", messages[1].parts.single().callId)
        assertEquals("toolu_1:0", messages[1].parts.single().id)
        assertEquals(PartType.Reasoning, messages[2].parts.single().type)
        assertEquals("Thinking", messages[2].parts.single().text)
        assertEquals(PartType.Text, messages[3].parts.single().type)
        assertEquals("Done.", messages[3].parts.single().text)
        assertEquals("claude", messages[3].info.providerId)
        assertNull(messages[0].info.providerId)
        assertEquals(at("2026-09-19T03:35:47.208Z"), messages.last().info.time!!.completedMs)
        assertEquals(setOf("toolu_1"), timeline.tools.keys)
        assertEquals("a1", messages[0].info.sessionId)
    }

    @Test
    fun `B-CHAT-070 only the newest item of a running agent can still be streaming`() {
        val busy = paseoTimeline("a1", history, busy = true).messages
        assertNull(busy.last().info.time!!.completedMs)
        assertNotNull(busy[2].info.time!!.completedMs)
    }

    @Test
    fun `B-CHAT-070 entries sharing an id collapse to the latest and a re-reported call merges in place`() {
        val timeline = paseoTimeline(
            "a1",
            jsonOf(
                "entries" to listOf(
                    entry(item("type" to "assistant_message", "text" to "Hel", "messageId" to "m1"), 1),
                    entry(item("type" to "tool_call", "callId" to "c1", "name" to "Bash", "status" to "running", "detail" to mapOf("type" to "shell", "command" to "ls")), 2),
                    entry(item("type" to "assistant_message", "text" to "Hello", "messageId" to "m1"), 3),
                    entry(item("type" to "tool_call", "callId" to "c1", "name" to "tool", "status" to "completed", "detail" to mapOf("type" to "unknown", "output" to "a")), 4),
                ),
            ),
            busy = false,
        )
        assertEquals(listOf("c1", "m1"), timeline.messages.map { it.info.id })
        val call = timeline.messages[0].parts.single()
        assertEquals("bash", call.toolName)
        assertEquals(ToolStatus.Completed, call.tool!!.status)
        // The call keeps the time it was first seen.
        assertEquals(at("2026-10-10T10:00:02.000Z"), timeline.messages[0].info.time!!.createdMs)
        assertEquals("Hello", timeline.messages[1].parts.single().text)
        // Items without an id of their own take one from their position.
        assertEquals("u:7", paseoItemId(item("type" to "user_message"), 7))
        assertEquals("i:0", paseoItemId(item("type" to "todo"), null))
        assertEquals("cm1", paseoItemId(item("type" to "user_message", "clientMessageId" to "cm1"), 2))
    }

    @Test
    fun `B-CHAT-070 the agent's task list is a Tasks step`() {
        val todo = paseoItemMessage(
            "a1",
            item("type" to "todo", "items" to listOf(mapOf("text" to "Write tests", "status" to "in_progress"), mapOf("text" to "Ship", "completed" to true), mapOf("text" to " "))),
            id = "i:3",
            provider = "claude",
            createdMs = 1,
            completedMs = 2,
        )!!
        val part = todo.parts.single()
        assertEquals("todowrite", part.toolName)
        assertEquals(ToolStatus.Completed, part.tool!!.status)
        val todos = assertIs<JsonArray>(part.tool!!.input["todos"])
        assertEquals(
            listOf(jsonOf("content" to "Write tests", "status" to "in_progress"), jsonOf("content" to "Ship", "status" to "completed")),
            todos.toList(),
        )
    }

    @Test
    fun `B-CHAT-070 a compaction is a notice in the transcript, not a reply`() {
        val message = paseoItemMessage("agent-1", item("type" to "compaction", "status" to "completed", "trigger" to "manual", "preTokens" to 1234.4), "i:7", "claude", null, null)!!
        assertEquals(Role.User, message.info.role)
        val part = message.parts.single()
        assertEquals(PartType.Compaction, part.type)
        assertEquals("v2:compaction", part.rawType)
        assertEquals("completed", part.toolName)
        assertEquals("Conversation shortened.", part.text)
        assertEquals(jsonOf("trigger" to "manual", "preTokens" to 1234), part.tool!!.metadata)
        val running = paseoItemMessage("agent-1", item("type" to "compaction", "trigger" to "weird"), "i:8", "claude", null, null)!!.parts.single()
        assertEquals("running", running.toolName)
        assertEquals(jsonOf(), running.tool!!.metadata)
    }

    @Test
    fun `B-CHAT-070 a notification is a notice line with its level, an empty one shows nothing`() {
        val warn = paseoItemMessage("a1", item("type" to "notification", "level" to "warning", "message" to "Low disk"), "i:1", "claude", 5, null)!!
        val part = warn.parts.single()
        assertEquals(PartType.Notice, part.type)
        assertEquals("v2:notice", part.rawType)
        assertEquals("agent-warning", part.toolName)
        assertEquals("Low disk", part.text)
        assertEquals(5L, warn.info.time!!.completedMs)
        assertEquals("agent-info", paseoItemMessage("a1", item("type" to "notification", "message" to "x"), "i:2", "claude", null, null)!!.parts.single().toolName)
        assertNull(paseoItemMessage("a1", item("type" to "notification", "message" to "  "), "i:3", "claude", null, null))
        assertNull(paseoItemMessage("a1", item("type" to "something_new"), "i:4", "claude", null, null))
        assertNull(paseoItemMessage("a1", item("text" to "no type"), "i:5", "claude", null, null))
    }

    @Test
    fun `B-SEC-110 an agent error is an error row whose text is Details only, never reply text`() {
        val error = paseoItemMessage("a1", item("type" to "error", "message" to "Traceback: secret at /home/x"), "i:9", "claude", 10, null)!!
        assertTrue(error.parts.isEmpty())
        assertEquals(Role.Assistant, error.info.role)
        assertEquals("Traceback: secret at /home/x", error.info.errorText)
        assertEquals("The agent could not finish this reply.", paseoItemMessage("a1", item("type" to "error"), "i:9", "claude", null, null)!!.info.errorText)
        val helper = paseoItemMessage("agent-1", item("type" to "assistant_message", "text" to "[System Error] Internal error: model overloaded\n\ncode: -32603"), "a:5", "kilo", null, null)!!
        assertTrue(helper.parts.isEmpty())
        assertNotEquals(MessageErrorKind.ProviderAuth, helper.info.errorKind)
        assertTrue("model overloaded" in helper.info.errorText!!)
        val api = paseoItemMessage("a1", item("type" to "assistant_message", "text" to "API Error: 529 {\"type\":\"overloaded_error\"}"), "a:6", "claude", null, null)!!
        assertTrue(api.parts.isEmpty())
        assertTrue(api.info.errorText!!.startsWith("API Error"))
    }

    @Test
    fun `B-CHAT-070 an expired sign-in is a sign-in error, its text kept for Details`() {
        val message = paseoItemMessage(
            "agent-1",
            item("type" to "assistant_message", "text" to "Failed to authenticate: OAuth session expired and could not be refreshed"),
            "a:3",
            "claude",
            null,
            null,
        )!!
        assertTrue(message.parts.isEmpty())
        assertEquals(MessageErrorKind.ProviderAuth, message.info.errorKind)
        assertTrue("OAuth session expired" in message.info.errorText!!)
    }

    @Test
    fun `B-CHAT-070 an agent asking to sign in inside the bridge's error report is a sign-in error`() {
        val kiloSignedOut = "[System Error] Internal error: You need to sign in to use this model.\n\ncode: -32603\n\n" +
            "Internal error: You need to sign in to use this model. | code=-32603 | data={\"service\":\"session\",\"errorName\":\"APIError\"}"
        val message = paseoItemMessage("agent-1", item("type" to "assistant_message", "text" to kiloSignedOut), "a:4", "kilo", null, null)!!
        assertTrue(message.parts.isEmpty())
        assertEquals(MessageErrorKind.ProviderAuth, message.info.errorKind)
        assertTrue("code: -32603" in message.info.errorText!!)
        // In an ordinary reply the same words are advice, not a failure.
        val advice = paseoItemMessage("a1", item("type" to "assistant_message", "text" to "You can sign in later."), "a:5", "claude", null, null)!!
        assertEquals("You can sign in later.", advice.parts.single().text)
    }

    @Test
    fun `B-CHAT-070 a busy refresh elsewhere is not a sign-in failure`() {
        assertFalse(paseoSignInFailure("Failed to refresh OAuth token: another Claude Code process is refreshing it or exited mid-refresh."))
        assertTrue(paseoSignInFailure("Invalid API key · Please run /login"))
    }

    @Test
    fun `B-CHAT-070 a history that does not map names the reply, never its content`() {
        val failure = assertFailsWith<PaseoException> { paseoTimeline("a1", jsonOf("entries" to "nope"), busy = false) }
        assertEquals(PaseoFailureKind.InvalidResponse, failure.kind)
        assertEquals("fetch_agent_timeline_response", failure.reason)
        // Malformed entries are skipped, not fatal.
        assertEquals(1, paseoTimeline("a1", jsonOf("entries" to listOf(1, mapOf("item" to "x"), entry(item("type" to "reasoning", "text" to "t"), 1))), false).messages.size)
    }

    // ---- pictures

    private fun attachment(mime: String, url: String) = PromptAttachment(mime = mime, filename = "f", url = url)

    @Test
    fun `B-AGENT-170 pictures go to the agent as base64 with their type`() {
        val ok = assertIs<PaseoImages.Ok>(paseoImages(listOf(attachment("image/png", "data:image/png;base64,iVBORw0KGgo="))))
        assertEquals(listOf(jsonOf("data" to "iVBORw0KGgo=", "mimeType" to "image/png")), ok.images)
    }

    @Test
    fun `B-AGENT-170 other files are refused in words`() {
        val refused = assertIs<PaseoImages.Refused>(paseoImages(listOf(attachment("application/pdf", "data:application/pdf;base64,JVBERi0="))))
        assertEquals(PaseoImageProblem.NotPicture, refused.problem)
        assertEquals(PaseoImageProblem.TooMany, (paseoImages(List(9) { attachment("image/png", "data:image/png;base64,iVBORw0KGgo=") }) as PaseoImages.Refused).problem)
    }

    @Test
    fun `B-AGENT-170 the type sent is the one the bytes are, not the file name`() {
        val jpegData = "/9j/4AAQSkZJRgABAQ=="
        val ok = assertIs<PaseoImages.Ok>(paseoImages(listOf(attachment("image/png", "data:image/png;base64,$jpegData"))))
        assertEquals("image/jpeg", ok.images.single().str("mimeType"))
        assertEquals(jpegData, ok.images.single().str("data"))
    }

    @Test
    fun `B-AGENT-170 a picture type Claude does not take is refused in words`() {
        val refused = assertIs<PaseoImages.Refused>(paseoImages(listOf(attachment("image/heic", "data:image/heic;base64,AAAAGGZ0eXBoZWlj"))))
        assertEquals(PaseoImageProblem.UnsupportedType, refused.problem)
    }
}
