package io.github.eslamasabry.oc.protocol.oc2.adapter

import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.PartId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.DeltaField
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.HintKind
import io.github.eslamasabry.oc.core.domain.events.InterruptReason
import io.github.eslamasabry.oc.core.domain.events.NoticeKind
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.PermissionTool
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.protocol.oc2.events.Oc2Envelope
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Wire
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The adapter's output reaches the rest of the app only as core's GatewayEvent (INTEGRATE-W1, plan 04 §11.2). */
class Oc2CoreProjectionTest {
    private val redactor = Redactor()
    private val projection = Oc2CoreProjection(redactor::details)
    private val adapter = Oc2EventAdapter(nowMs = { 1_000L })

    private fun feed(json: String): List<GatewayEvent> =
        adapter.adapt(Oc2Envelope.decode(Oc2Wire.json.parseToJsonElement(json))).map(projection::toCore)

    private fun v2(type: String, data: String): List<GatewayEvent> {
        val body = Oc2Wire.json.parseToJsonElement(data).jsonObject
        val merged = JsonObject(mapOf("sessionID" to JsonPrimitive("ses_x")) + body)
        return feed("""{"type":"$type","data":$merged}""")
    }

    @Test
    fun `B-PROTO-015 the prompt round-trip reaches core as typed ids, transcript parts and deltas`() {
        val started = v2("session.step.started", """{"assistantMessageID":"msg_a","agent":"build","model":{"id":"gpt","providerID":"openai"}}""")
        assertEquals(GatewayEvent.RunStarted(SessionId("ses_x")), started.first())
        val info = assertIs<GatewayEvent.MessageUpserted>(started.last()).message
        assertEquals(Role.Assistant, info.role)
        assertEquals("openai", info.providerId)
        assertEquals(1_000L, info.time?.createdMs)

        val delta = assertIs<GatewayEvent.PartDelta>(v2("session.text.delta", """{"assistantMessageID":"msg_a","ordinal":1,"delta":"po"}""").single())
        assertEquals(MessageId("msg_a"), delta.messageId)
        assertEquals(PartId("text-1"), delta.partId)
        assertEquals(DeltaField.Text, delta.field)

        val ended = assertIs<GatewayEvent.PartUpserted>(v2("session.reasoning.ended", """{"assistantMessageID":"msg_a","ordinal":0,"text":"think"}""").single()).part
        assertEquals(PartType.Reasoning, ended.type)
        assertEquals("reasoning", ended.rawType)
        assertEquals("think", ended.text)
        assertTrue(ended.ended)
    }

    @Test
    fun `B-CHAT-005 a settled lost result reaches core as a ran tool and the chat rereads`() {
        v2("session.step.started", """{"assistantMessageID":"msg_a1"}""")
        v2("session.tool.input.started", """{"assistantMessageID":"msg_a1","id":"call_1","name":"grep"}""")
        v2("session.tool.called", """{"assistantMessageID":"msg_a1","id":"call_1","input":{"pattern":"x"},"executed":false}""")
        val out = v2("session.step.ended", """{"assistantMessageID":"msg_a1","finish":"tool-calls"}""")
        val part = out.filterIsInstance<GatewayEvent.PartUpserted>().first().part
        assertEquals(PartType.Tool, part.type)
        assertEquals("call_1", part.callId)
        assertEquals("grep", part.toolName)
        val tool = part.tool!!
        assertEquals(ToolStatus.Completed, tool.status)
        assertTrue(tool.ran, "outcome wins over the executed:false stamp (plan 09 trap T2)")
        assertTrue(out.any { it is GatewayEvent.TranscriptStale })
    }

    @Test
    fun `B-PROTO-015 a failed run carries its type as a tag and server words only as redacted details`() {
        val secret = "sk-ant-api03-FAKEFAKEFAKEFAKEFAKEFAKE"
        redactor.registerSecret(secret)
        val ended = assertIs<GatewayEvent.RunEnded>(
            v2("session.execution.failed", """{"error":{"type":"provider.auth","message":"bad key $secret"}}""").single(),
        )
        val failed = assertIs<RunOutcome.Failed>(ended.outcome)
        assertEquals(WireTag("provider.auth"), failed.tag)
        assertTrue(secret !in failed.details!!.text, "server prose is redacted before it reaches Details")

        val interrupted = assertIs<GatewayEvent.RunEnded>(v2("session.execution.interrupted", """{"reason":"superseded"}""").single())
        assertEquals(RunOutcome.Interrupted(InterruptReason.Superseded), interrupted.outcome)
        val unknown = assertIs<GatewayEvent.RunEnded>(v2("session.execution.interrupted", """{"reason":" "}""").single())
        assertEquals(RunOutcome.Interrupted(InterruptReason.Unknown), unknown.outcome)
    }

    @Test
    fun `B-PROTO-015 session idle is a run end with Idle, which the reducer never lets replace a failure`() {
        val idle = v2("session.status", """{"status":{"type":"idle"}}""")
        assertEquals(listOf<GatewayEvent>(GatewayEvent.RunEnded(SessionId("ses_x"), RunOutcome.Idle)), idle)
    }

    @Test
    fun `B-PROTO-019 permission asked maps like Dart mapApi2PermissionRequest`() {
        val asked = assertIs<GatewayEvent.PermissionAsked>(
            v2("permission.asked", """{"id":"per_1","action":"bash","resources":["ls"],"save":["ls *"],"source":{"type":"tool","messageID":"m","id":"call_1"}}""")
                .single(),
        ).request
        assertEquals(RequestId("per_1"), asked.id)
        assertEquals(SessionId("ses_x"), asked.sessionId)
        assertEquals("bash", asked.permission)
        assertEquals(listOf("ls"), asked.patterns)
        assertEquals(listOf("ls *"), asked.always)
        assertEquals(PermissionTool(MessageId("m"), "call_1"), asked.tool)
        assertTrue(asked.canAlwaysAllow)
    }

    @Test
    fun `B-PROTO-015 hints keep their wire family and session-level notices need no message`() {
        assertEquals(listOf<GatewayEvent>(GatewayEvent.RefreshHint(HintKind("catalog"))), feed("""{"type":"models-dev.refreshed","data":{}}"""))
        val notice = assertIs<GatewayEvent.Notice>(v2("session.shell.started", "{}").single())
        assertEquals(NoticeKind.ShellChanged, notice.kind)
        assertNull(notice.messageId)
    }
}
