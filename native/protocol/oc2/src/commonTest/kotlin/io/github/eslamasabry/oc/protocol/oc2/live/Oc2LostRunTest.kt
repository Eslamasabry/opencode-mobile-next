package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Wire
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B8: what OpenCode 2.2.6 gives back after its process was killed mid-run and `serve` started again (recorded
 * 2026-11-12 on a local 2.2.6: prompt sent, SIGKILL 5 s and 25 s in, restart; nothing resumes, `/session/active` is
 * empty). The step's assistant message keeps its model and creation time, has no completion and no error, and holds
 * one empty reasoning part: streamed words were never durable. Beside it, the same server's message after the person
 * declined a write: completed, error `aborted`. The chat tells the two apart by these messages
 * (feature/chat B8CutOffTest); the session row cannot (both leave `time.busy` with no newer `time.idle`).
 */
class Oc2LostRunTest {
    private fun message(text: String) =
        assertNotNull(Oc2History(Redactor()).message("ses_e339b8957ffef0fXsE3aM3fWzz", Oc2Wire.parse(text)!!))

    @Test
    fun `B8 the lost step's message comes back unfinished, with its model and no words`() {
        val lost =
            message(
                """{"id":"msg_1cc64bb7f0011AyNtEsD0F3AH7","time":{"created":1794430516098},"type":"assistant",""" +
                    """"agent":"build","model":{"id":"big-pickle","providerID":"opencode"},"content":[{"type":"reasoning",""" +
                    """"text":"","native":{"reasoningField":"reasoning_content"},"time":{"created":1794430516924}}]}"""
            )
        assertEquals(Role.Assistant, lost.info.role)
        assertEquals("big-pickle", lost.info.modelId)
        assertEquals(1794430516098, lost.info.time?.createdMs)
        assertNull(lost.info.time?.completedMs)
        assertNull(lost.info.errorText)
        assertNull(lost.info.errorKind)
        assertEquals(listOf(PartType.Reasoning), lost.parts.map { it.type })
        assertEquals("", lost.parts.single().text)
    }

    @Test
    fun `B8 a declined step's message comes back ended, aborted, with the refusal on its tool`() {
        val declined =
            message(
                """{"id":"msg_d","time":{"created":1794431225133,"streamed":1794431227069,"completed":1794431242618},""" +
                    """"type":"assistant","agent":"build","model":{"id":"big-pickle","providerID":"opencode"},""" +
                    """"finish":"error","rawFinish":"tool_calls","error":{"type":"aborted","message":"Step interrupted"},""" +
                    """"content":[{"type":"tool","id":"call_1","name":"write","executed":false,"state":{"status":"error",""" +
                    """"input":{"path":"/work/project/hello.txt","content":"hi\n"},""" +
                    """"error":{"type":"aborted","message":"The user declined this tool call"}},""" +
                    """"time":{"created":1794431226208,"ran":1794431227068,"completed":1794431242589}}]}"""
            )
        assertEquals(1794431242618, declined.info.time?.completedMs)
        assertEquals(MessageErrorKind.Aborted, declined.info.errorKind)
        val tool = assertNotNull(declined.parts.single { it.type == PartType.Tool }.tool)
        assertEquals(ToolStatus.Error, tool.status)
    }

    private fun refusedWrite(error: String) =
        message(
            """{"id":"msg_r","time":{"created":1,"completed":3},"type":"assistant","agent":"build",""" +
                """"model":{"id":"big-pickle","providerID":"opencode"},"finish":"error",""" +
                """"content":[{"type":"tool","id":"call_1","name":"write","executed":false,""" +
                """"state":{"status":"error","input":{"path":"/work/project/a.txt"},"error":$error},""" +
                """"time":{"created":2,"completed":3}}]}"""
        ).parts.single { it.type == PartType.Tool }.tool!!

    @Test
    fun `a write refused with a note reads as the person's refusal and keeps the note`() {
        val tool = refusedWrite("""{"type":"permission.rejected","message":"use the other folder"}""")
        assertEquals(ToolStatus.Error, tool.status)
        // Before: the output was only "use the other folder", which the transcript read as a failure.
        assertEquals("The user declined this tool call\nuse the other folder", tool.output)
        assertTrue(tool.output!!.startsWith("The user declined")) // what the transcript reads as declined
    }

    @Test
    fun `RC1-DENY-002 the note sent as the user's words reads back as the person's note on the declined step`() {
        val sent = io.github.eslamasabry.oc.protocol.oc2.adapter.Oc2DenyNote.message("  use rc2.txt ")
        // What the model reads: who wrote it and that it is how to go on, then the person's own words.
        assertTrue(sent.startsWith("The user declined this tool call, so it did not run."))
        assertTrue(sent.endsWith("the user said:\nuse rc2.txt"))
        val json = kotlinx.serialization.json.JsonPrimitive(sent).toString()
        val tool = refusedWrite("""{"type":"permission.rejected","message":$json}""")
        assertEquals("The user declined this tool call\nuse rc2.txt", tool.output)
    }

    @Test
    fun `a refusal without a note is the decline sentence once, another error type keeps its own text`() {
        assertEquals(
            "The user declined this tool call",
            refusedWrite("""{"type":"permission.rejected","message":"The user declined this tool call"}""").output,
        )
        assertEquals("The user declined this tool call", refusedWrite("""{"type":"permission.rejected"}""").output)
        val failed = refusedWrite("""{"type":"tool.execution","message":"EACCES: permission denied"}""")
        assertEquals("EACCES: permission denied", failed.output)
    }
}
