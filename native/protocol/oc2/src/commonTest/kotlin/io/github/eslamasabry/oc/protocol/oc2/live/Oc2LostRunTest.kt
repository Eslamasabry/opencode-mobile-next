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
}
