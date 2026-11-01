package io.github.eslamasabry.oc.protocol.oc2.events

import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Delivery
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2FormFieldType
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ToolResultItem
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Wire
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ports the inline cases of test/api2_events_test.dart plus the event rows of test/server_data_widening_test.dart. */
class Oc2EventsTest {
    private fun env(json: String): Oc2Envelope = Oc2Envelope.decode(Oc2Wire.json.parseToJsonElement(json))

    @Test
    fun `B-PROTO-014 unknown event types fall back to Unknown without throwing and keep the envelope`() {
        val envelope = env(
            """
            {"id":"evt_1","created":5,"type":"session.quantum.entangled","data":{"sessionID":"ses_1","spin":"up"},
             "durable":{"aggregateID":"ses_1","seq":9,"version":1}}
            """,
        )
        assertEquals("session.quantum.entangled", envelope.type)
        val unknown = assertIs<Oc2Event.Unknown>(envelope.event)
        assertEquals(JsonPrimitive("up"), unknown.raw["spin"])
        assertEquals(9L, envelope.durable?.seq)
        assertTrue(envelope.isDurable)
        assertEquals(JsonPrimitive("ses_1"), envelope.data["sessionID"])

        val malformed = env("""{"type":"session.text.delta","data":{"ordinal":"not-a-number"}}""")
        assertEquals(0, assertIs<Oc2Event.SessionText>(malformed.event).ordinal)

        val empty = env("{}")
        assertEquals("", empty.type)
        assertIs<Oc2Event.Unknown>(empty.event)

        assertIs<Oc2Event.Unknown>(Oc2Envelope.decode(JsonPrimitive("not an object")).event)
        assertIs<Oc2Event.Unknown>(Oc2Envelope.parse("{not json").event)
    }

    @Test
    fun `B-PROTO-014 the log synced boundary marker parses bare without an envelope`() {
        val synced = assertIs<Oc2Event.LogSynced>(env("""{"type":"log.synced","aggregateID":"ses_fb53","seq":10}""").event)
        assertEquals("ses_fb53", synced.aggregateID)
        assertEquals(10L, synced.seq)
        val emptyLog = assertIs<Oc2Event.LogSynced>(env("""{"type":"log.synced","aggregateID":"ses_empty"}""").event)
        assertNull(emptyLog.seq)
    }

    @Test
    fun `B-PROTO-014 permission form status mcp vcs refresh and execution events parse`() {
        val asked = assertIs<Oc2Event.PermissionAsked>(
            env(
                """
                {"type":"permission.asked","data":{"id":"per_1","sessionID":"ses_1","action":"bash","resources":["rm -rf*"],
                 "source":{"type":"tool","messageID":"msg_1","id":"call_1"}}}
                """,
            ).event,
        )
        assertEquals("bash", asked.request.action)
        assertEquals(listOf("rm -rf*"), asked.request.resources)
        assertIs<Oc2Event.Unknown>(env("""{"type":"permission.asked","data":{"action":"bash"}}""").event)

        val replied = assertIs<Oc2Event.PermissionReplied>(
            env("""{"type":"permission.replied","data":{"sessionID":"ses_1","requestID":"per_1","reply":"once"}}""").event,
        )
        assertEquals("once", replied.reply)

        val formCreated = assertIs<Oc2Event.FormCreated>(
            env(
                """
                {"type":"form.created","data":{"form":{"id":"frm_1","sessionID":"ses_1","title":"Pick one",
                 "fields":[{"key":"a","type":"boolean"}]}}}
                """,
            ).event,
        )
        assertEquals(Oc2FormFieldType.Boolean, formCreated.form.fields.single().type)

        val formReplied = assertIs<Oc2Event.FormReplied>(
            env("""{"type":"form.replied","data":{"id":"frm_1","sessionID":"ses_1","answer":{"a":true}}}""").event,
        )
        assertEquals(JsonPrimitive(true), formReplied.answer["a"])

        val status = assertIs<Oc2Event.SessionStatus>(
            env(
                """
                {"type":"session.status","data":{"sessionID":"ses_1","status":{"type":"retry","attempt":2,
                 "message":"overloaded","next":1787961300000}}}
                """,
            ).event,
        )
        assertEquals(Oc2SessionRunStatus.Retry, status.status)
        assertEquals(2, status.attempt)
        assertEquals("overloaded", status.message)
        assertEquals(1787961300000, status.next)

        val idle = assertIs<Oc2Event.SessionStatus>(env("""{"type":"session.idle","data":{"sessionID":"ses_1"}}""").event)
        assertEquals(Oc2SessionRunStatus.Idle, idle.status)

        val future = assertIs<Oc2Event.SessionStatus>(
            env("""{"type":"session.status","data":{"sessionID":"ses_1","status":{"type":"hibernating"}}}""").event,
        )
        assertEquals(Oc2SessionRunStatus.Unknown, future.status)
        assertEquals(JsonPrimitive("hibernating"), future.raw["type"])

        val mcp = assertIs<Oc2Event.McpChanged>(env("""{"type":"mcp.status.changed","data":{"server":"github"}}""").event)
        assertEquals("status", mcp.kind)
        assertEquals("github", mcp.server)

        val vcs = assertIs<Oc2Event.VcsBranchUpdated>(
            env("""{"type":"vcs.branch.updated","data":{"branch":"port/api2-core"}}""").event,
        )
        assertEquals("port/api2-core", vcs.branch)

        assertEquals("agent.updated", assertIs<Oc2Event.RefreshHint>(env("""{"type":"agent.updated","data":{}}""").event).topic)

        val interrupted = assertIs<Oc2Event.SessionExecution>(
            env("""{"type":"session.execution.interrupted","data":{"sessionID":"ses_1","reason":"user"}}""").event,
        )
        assertEquals(Oc2Phase.Interrupted, interrupted.phase)
        assertEquals("user", interrupted.reason)
        assertTrue(interrupted.finished)

        val failed = assertIs<Oc2Event.SessionExecution>(
            env(
                """
                {"type":"session.execution.failed","data":{"sessionID":"ses_1","error":{"type":"overloaded_error",
                 "message":"try later","status":529}}}
                """,
            ).event,
        )
        assertEquals("overloaded_error", failed.error?.type)
        assertEquals(529, failed.error?.status)
    }

    @Test
    fun `B-PROTO-014 inbox events merge the inbox id and session into the item and read delivery from either place`() {
        val enqueued = assertIs<Oc2Event.SessionInbox>(
            env(
                """
                {"type":"session.inbox.enqueued","data":{"sessionID":"ses_1","inboxID":"msg_1",
                 "item":{"type":"user","payload":{"text":"hello"},"delivery":"queue"}}}
                """,
            ).event,
        )
        assertEquals(Oc2Phase.Enqueued, enqueued.phase)
        assertEquals("msg_1", enqueued.item?.id)
        assertEquals("ses_1", enqueued.item?.sessionID)
        assertEquals("hello", enqueued.item?.promptText)
        assertEquals(Oc2Delivery.Queue, enqueued.delivery)
        val changed = assertIs<Oc2Event.SessionInbox>(
            env(
                """{"type":"session.inbox.delivery.changed","data":{"sessionID":"ses_1","inboxID":"msg_1","delivery":"steer"}}""",
            ).event,
        )
        assertEquals(Oc2Phase.DeliveryChanged, changed.phase)
        assertNull(changed.item)
        assertEquals(Oc2Delivery.Steer, changed.delivery)
    }

    @Test
    fun `B-PROTO-047 installation events pass through with the exact available and installed versions`() {
        val available = assertIs<Oc2Event.PassThrough>(
            env("""{"type":"installation.update-available","data":{"version":"2.2.5-rc.1+build.7"}}""").event,
        )
        assertEquals("installation.update-available", available.type)
        assertEquals(JsonPrimitive("2.2.5-rc.1+build.7"), available.raw["version"])
        val installed = assertIs<Oc2Event.PassThrough>(
            env("""{"type":"installation.updated","data":{"version":"2.1.1"}}""").event,
        )
        assertEquals(JsonPrimitive("2.1.1"), installed.raw["version"])
    }

    @Test
    fun `B-PROTO-090 retry state parses from status and retry scheduled events with attempt message and next time`() {
        val scheduled = assertIs<Oc2Event.SessionRetryScheduled>(
            env(
                """
                {"type":"session.retry.scheduled","data":{"sessionID":"ses_1","assistantMessageID":"msg_2","attempt":3,
                 "at":1788960000500,"error":{"type":"api","message":"Overloaded","status":529}}}
                """,
            ).event,
        )
        assertEquals(3, scheduled.attempt)
        assertEquals(1788960000500, scheduled.at)
        assertEquals("Overloaded", scheduled.error?.message)
        assertEquals("msg_2", scheduled.assistantMessageID)
    }

    @Test
    fun `B-PROTO-091 tool called and result events forward the executed flag and result items`() {
        val called = assertIs<Oc2Event.SessionToolCalled>(
            env(
                """
                {"type":"session.tool.called","data":{"sessionID":"s","assistantMessageID":"m","id":"call_1",
                 "input":{"path":"README.md"},"executed":false}}
                """,
            ).event,
        )
        assertEquals("call_1", called.callID)
        assertEquals(false, called.executed)
        val success = assertIs<Oc2Event.SessionToolResult>(
            env(
                """
                {"type":"session.tool.success","data":{"sessionID":"s","assistantMessageID":"m","id":"call_1","executed":true,
                 "content":[{"type":"text","text":"ok"},{"type":"file","uri":"file:///a.png","mime":"image/png"},{"type":"audio"},7]}}
                """,
            ).event,
        )
        assertTrue(success.succeeded)
        assertEquals(true, success.executed)
        assertEquals(3, success.content.size)
        assertEquals("ok", assertIs<Oc2ToolResultItem.Text>(success.content[0]).text)
        assertEquals("image/png", assertIs<Oc2ToolResultItem.File>(success.content[1]).mime)
        assertIs<Oc2ToolResultItem.Unknown>(success.content[2])
        val failed = assertIs<Oc2Event.SessionToolResult>(
            env("""{"type":"session.tool.failed","data":{"id":"call_2","error":{"message":"boom"}}}""").event,
        )
        assertFalse(failed.succeeded)
        assertNull(failed.executed)
    }

    @Test
    fun `B-PROTO-092 step failures keep the error type message and status and blank messages read as missing`() {
        val failed = assertIs<Oc2Event.SessionStep>(
            env(
                """
                {"type":"session.step.failed","data":{"sessionID":"s","assistantMessageID":"m",
                 "error":{"type":"provider.auth","message":"","status":401},"finish":"content-filter"}}
                """,
            ).event,
        )
        assertEquals(Oc2Phase.Failed, failed.phase)
        assertEquals("provider.auth", failed.error?.type)
        assertNull(failed.error?.message)
        assertEquals(401, failed.error?.status)
        assertEquals("content-filter", failed.finish)
    }

    @Test
    fun `B-PROTO-014 the registry lists typed pass-through and ignored types exactly once each`() {
        assertEquals(83, Oc2EventRegistry.typed.size)
        assertEquals(10, Oc2EventRegistry.passThrough.size)
        assertTrue(Oc2EventRegistry.typed.intersect(Oc2EventRegistry.passThrough).isEmpty())
        assertTrue(Oc2EventRegistry.typed.intersect(Oc2EventRegistry.ignored).isEmpty())
        assertTrue(Oc2EventRegistry.passThrough.intersect(Oc2EventRegistry.ignored).isEmpty())
        for (type in Oc2EventRegistry.ignored) {
            val event = assertIs<Oc2Event.Unknown>(env("""{"type":"$type","data":{}}""").event)
            assertTrue(event.ignored, type)
        }
        assertFalse(assertIs<Oc2Event.Unknown>(env("""{"type":"brand.new","data":{}}""").event).ignored)
        for (type in Oc2EventRegistry.typed) {
            val event = env("""{"type":"$type","data":{"id":"x","form":{"id":"f"}}}""").event
            assertFalse(event is Oc2Event.Unknown || event is Oc2Event.PassThrough, type)
        }
    }

    @Test
    fun `B-PROTO-034 durable markers carry the session log cursor and deltas carry none`() {
        val durable = env(
            """{"type":"session.text.ended","durable":{"aggregateID":"ses_1","seq":42,"version":1},"data":{"text":"x"}}""",
        )
        assertEquals(42L, durable.durable?.seq)
        assertEquals("ses_1", durable.durable?.aggregateID)
        assertFalse(env("""{"type":"session.text.delta","data":{"delta":"x"}}""").isDurable)
        assertEquals("/w", env("""{"type":"server.connected","location":{"directory":"/w","workspace":"wrk"}}""").location?.directory)
        assertEquals("wrk", env("""{"type":"server.connected","location":{"workspace":"wrk"}}""").location?.workspaceID)
        val json = Oc2Wire.json.parseToJsonElement("""{"type":"server.connected"}""").jsonObject
        assertIs<Oc2Event.ServerConnected>(Oc2Envelope.decode(json).event)
    }
}
