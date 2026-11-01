package io.github.eslamasabry.oc.protocol.oc2.adapter

import io.github.eslamasabry.oc.protocol.oc2.events.Oc2Envelope
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Wire
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ports test/api2_gateway_events_test.dart onto the neutral GatewayEvent vocabulary (plan 04 §7.6, §11.2). */
class Oc2EventAdapterTest {
    private fun adapter() = Oc2EventAdapter(nowMs = { 1_000L })

    private fun Oc2EventAdapter.feed(json: String): List<GatewayEvent> =
        adapt(Oc2Envelope.decode(Oc2Wire.json.parseToJsonElement(json)))

    private fun Oc2EventAdapter.v2(type: String, data: String): List<GatewayEvent> {
        val body = Oc2Wire.json.parseToJsonElement(data).jsonObject
        val merged = JsonObject(mapOf("sessionID" to JsonPrimitive("ses_x")) + body)
        return feed("""{"type":"$type","data":$merged}""")
    }

    private fun Oc2EventAdapter.twoSearches() {
        v2("session.step.started", """{"assistantMessageID":"msg_a1","agent":"build"}""")
        for ((id, pattern) in listOf("call_1" to "Released|released", "call_2" to "focusFill|focusRing")) {
            v2("session.tool.input.started", """{"assistantMessageID":"msg_a1","id":"$id","name":"grep"}""")
            v2("session.tool.called", """{"assistantMessageID":"msg_a1","id":"$id","input":{"pattern":"$pattern"},"executed":false}""")
        }
    }

    private fun settled(out: List<GatewayEvent>): Map<String, ToolStatus> =
        out.filterIsInstance<GatewayEvent.PartUpserted>().mapNotNull { e -> e.part.tool?.let { e.part.partId to it.status } }
            .toMap()

    @Test
    fun `B-CHAT-005 a tool whose result was lost is settled when its step ends and the chat rereads`() {
        val adapter = adapter().apply { twoSearches() }
        val out = adapter.v2("session.step.ended", """{"assistantMessageID":"msg_a1","finish":"tool-calls"}""")
        assertEquals(mapOf("call_1" to ToolStatus.Completed, "call_2" to ToolStatus.Completed), settled(out))
        val first = assertIs<GatewayEvent.PartUpserted>(out.first()).part.tool!!
        assertEquals("grep", first.name)
        assertEquals(buildJsonObject { put("pattern", "Released|released") }, first.input)
        assertTrue(out.any { it is GatewayEvent.TranscriptStale })
        // The step end itself never ends the run (B-PROTO-040).
        assertTrue(out.none { it is GatewayEvent.RunEnded })
    }

    @Test
    fun `B-CHAT-005 a result for a call never seen makes the chat reread and one that was seen does not`() {
        val fresh = adapter().v2(
            "session.tool.success",
            """{"assistantMessageID":"msg_a1","id":"call_9","content":[{"type":"text","text":"hello.txt"}],"executed":true}""",
        )
        assertTrue(fresh.any { it is GatewayEvent.TranscriptStale })
        val seen = adapter().apply { twoSearches() }.v2(
            "session.tool.success",
            """{"assistantMessageID":"msg_a1","id":"call_1","content":[{"type":"text","text":"found"}],"executed":true}""",
        )
        assertTrue(seen.none { it is GatewayEvent.TranscriptStale })
        val tool = assertIs<GatewayEvent.PartUpserted>(seen.single()).part.tool!!
        assertEquals(ToolStatus.Completed, tool.status)
        assertEquals("found", tool.output)
        assertEquals(true, tool.executed)
    }

    @Test
    fun `B-CHAT-005 open calls settle when the next step starts and fail when the run is interrupted`() {
        val next = adapter().apply { twoSearches() }.v2("session.step.started", """{"assistantMessageID":"msg_a2","agent":"build"}""")
        assertEquals(mapOf("call_1" to ToolStatus.Completed, "call_2" to ToolStatus.Completed), settled(next))
        assertTrue(next.any { it is GatewayEvent.TranscriptStale })

        val interrupted = adapter().apply { twoSearches() }.v2("session.execution.interrupted", """{"reason":"user"}""")
        assertEquals(mapOf("call_1" to ToolStatus.Error, "call_2" to ToolStatus.Error), settled(interrupted))
        val ended = assertIs<GatewayEvent.RunEnded>(interrupted.last())
        assertEquals(RunOutcome.Interrupted("user"), ended.outcome)
    }

    @Test
    fun `B-CHAT-005 a result that arrived leaves nothing to settle and other conversations are left alone`() {
        val adapter = adapter().apply { twoSearches() }
        for (id in listOf("call_1", "call_2")) {
            adapter.v2("session.tool.success", """{"assistantMessageID":"msg_a1","id":"$id","content":[],"executed":false}""")
        }
        val out = adapter.v2("session.step.ended", """{"assistantMessageID":"msg_a1","finish":"tool-calls"}""")
        assertEquals(1, out.size)
        assertIs<GatewayEvent.MessageUpserted>(out.single())

        val other = adapter().apply { twoSearches() }.feed(
            """{"type":"session.step.started","data":{"sessionID":"ses_other","assistantMessageID":"msg_b1","agent":"build"}}""",
        )
        assertTrue(settled(other).isEmpty())
    }

    @Test
    fun `B-CHAT-005 the tracker keeps at most 256 calls and evicts the oldest`() {
        val adapter = adapter()
        for (i in 0 until 300) adapter.v2("session.tool.input.started", """{"assistantMessageID":"msg_a1","id":"c$i","name":"t"}""")
        val out = adapter.v2("session.step.ended", """{"assistantMessageID":"msg_a1"}""")
        val ids = settled(out).keys
        assertEquals(256, ids.size)
        assertTrue("c0" !in ids && "c299" in ids)
    }

    @Test
    fun `B-PROTO-015 execution failure ends the run with the type and never also emits idle`() {
        val out = adapter().v2("session.execution.failed", """{"error":{"type":"provider.auth","message":"   "}}""")
        val ended = assertIs<GatewayEvent.RunEnded>(out.single())
        val failed = assertIs<RunOutcome.Failed>(ended.outcome)
        assertEquals("provider.auth", failed.type)
        assertNull(failed.message, "a blank message is missing; the UI words it from the type")
        val succeeded = adapter().v2("session.execution.succeeded", "{}")
        assertEquals(RunOutcome.Succeeded, assertIs<GatewayEvent.RunEnded>(succeeded.single()).outcome)
        val noReason = adapter().v2("session.execution.interrupted", """{"reason":" "}""")
        assertEquals(RunOutcome.Interrupted("unknown"), assertIs<GatewayEvent.RunEnded>(noReason.single()).outcome)
    }

    @Test
    fun `B-PROTO-015 a failed step keeps the step model and carries the error type and status for Details`() {
        val adapter = adapter()
        adapter.v2("session.step.started", """{"assistantMessageID":"msg_1","agent":"build","model":{"id":"gpt","providerID":"openai"}}""")
        val out = adapter.v2("session.step.failed", """{"assistantMessageID":"msg_1","error":{"type":"api","message":"","status":529}}""")
        val info = assertIs<GatewayEvent.MessageUpserted>(out.single()).message
        assertEquals("build", info.agent)
        assertEquals("openai", info.providerId)
        assertEquals("gpt", info.modelId)
        assertEquals(1_000L, info.completedMs)
        assertEquals(StepError(message = null, type = "api", status = 529), info.error)
    }

    @Test
    fun `B-PROTO-015 the prompt round-trip maps steps text and the inbox user message`() {
        val adapter = adapter()
        val enqueued = adapter.feed(
            """
            {"type":"session.inbox.enqueued","created":5,"data":{"sessionID":"s","inboxID":"msg_in",
             "item":{"type":"user","payload":{"text":"hi"},"delivery":"steer"}}}
            """,
        )
        assertEquals(3, enqueued.size)
        assertIs<GatewayEvent.InboxChanged>(enqueued[0])
        val user = assertIs<GatewayEvent.MessageUpserted>(enqueued[1]).message
        assertEquals("msg_in", user.id)
        assertEquals(MessageRole.User, user.role)
        assertEquals(5L, user.createdMs)
        val text0 = assertIs<GatewayEvent.PartUpserted>(enqueued[2]).part
        assertEquals("text-0", text0.partId)
        assertEquals("hi", text0.text)

        val started = adapter.v2("session.step.started", """{"assistantMessageID":"msg_a"}""")
        assertIs<GatewayEvent.RunStarted>(started.first())
        assertIs<GatewayEvent.MessageUpserted>(started.last())
        val delta = assertIs<GatewayEvent.PartDelta>(
            adapter.v2("session.text.delta", """{"assistantMessageID":"msg_a","ordinal":1,"delta":"po"}""").single(),
        )
        assertEquals("text-1", delta.partId)
        assertEquals(DeltaField.Text, delta.field)
        val ended = assertIs<GatewayEvent.PartUpserted>(
            adapter.v2("session.reasoning.ended", """{"assistantMessageID":"msg_a","ordinal":0,"text":"think"}""").single(),
        )
        assertEquals("reasoning-0", ended.part.partId)
        assertEquals(1_000L, ended.part.endedAtMs)
        val raw = assertIs<GatewayEvent.PartDelta>(
            adapter.v2("session.tool.input.delta", """{"assistantMessageID":"msg_a","id":"call_1","delta":"{\"a"}""").single(),
        )
        assertEquals(DeltaField.ToolInputRaw, raw.field)
        assertEquals("call_1", raw.partId)
    }

    @Test
    fun `B-PROTO-015 permission asked copies the source id to the call id and renames and hints map`() {
        val asked = assertIs<GatewayEvent.PermissionAsked>(
            adapter().v2("permission.asked", """{"id":"per_1","action":"bash","source":{"type":"tool","messageID":"m","id":"call_1"}}""").single(),
        )
        assertEquals("call_1", asked.callId)
        assertEquals("per_1", asked.request.id)
        val renamed = assertIs<GatewayEvent.SessionChanged>(adapter().v2("session.renamed", """{"title":"New"}""").single())
        assertEquals("New", renamed.title)
        assertEquals(false, renamed.replacesLocation)
        val moved = assertIs<GatewayEvent.SessionChanged>(adapter().v2("session.moved", """{"location":{"directory":"/a"}}""").single())
        assertTrue(moved.replacesLocation)
        assertNull(moved.workspaceId, "a move back to a directory clears the workspace")
        assertEquals(
            listOf(GatewayEvent.RefreshHint(HintKind.Catalog)),
            adapter().feed("""{"type":"models-dev.refreshed","data":{}}"""),
        )
        assertTrue(adapter().v2("session.model.selected", "{}").isEmpty(), "empty selections are ignored")
        assertTrue(adapter().v2("session.agent.selected", """{"agent":""}""").isEmpty())
    }

    @Test
    fun `B-PROTO-015 credential switches pass only when well formed and never carry a credential payload`() {
        val ok = adapter().feed("""{"type":"credential.switched","data":{"integrationID":"gh","credentialID":null,"secret":"x"}}""")
        assertEquals(GatewayEvent.CredentialSwitched("gh", null), ok.first())
        assertEquals(GatewayEvent.RefreshHint(HintKind.Integrations), ok.last())
        val missing = adapter().feed("""{"type":"credential.switched","data":{"integrationID":"gh"}}""")
        assertEquals(listOf<GatewayEvent>(GatewayEvent.RefreshHint(HintKind.Integrations)), missing)
    }

    @Test
    fun `B-PROTO-047 installation events keep the exact available and installed versions`() {
        assertEquals(
            listOf(GatewayEvent.Installation(InstallationKind.Available, "2.2.5-rc.1+build.7")),
            adapter().feed("""{"type":"installation.update-available","data":{"version":"2.2.5-rc.1+build.7"}}"""),
        )
        assertEquals(
            listOf(GatewayEvent.Installation(InstallationKind.Installed, "2.1.1")),
            adapter().feed("""{"type":"installation.updated","data":{"version":"2.1.1"}}"""),
        )
        assertTrue(adapter().feed("""{"type":"installation.updated","data":{}}""").isEmpty())
    }

    @Test
    fun `B-PROTO-090 retry scheduled and retry status map to run retrying with attempt and next time`() {
        val scheduled = assertIs<GatewayEvent.RunRetrying>(
            adapter().v2("session.retry.scheduled", """{"attempt":2,"at":99,"error":{"type":"api","message":"Overloaded"}}""").single(),
        )
        assertEquals(2, scheduled.attempt)
        assertEquals(99L, scheduled.nextAtMs)
        assertEquals("Overloaded", scheduled.message)
        val status = assertIs<GatewayEvent.RunRetrying>(
            adapter().v2("session.status", """{"status":{"type":"retry","attempt":3,"next":7}}""").single(),
        )
        assertEquals(3, status.attempt)
        assertIs<GatewayEvent.RunIdle>(adapter().v2("session.idle", "{}").single())
        assertIs<GatewayEvent.RunStarted>(adapter().v2("session.status", """{"status":{"type":"busy"}}""").single())
    }
}
