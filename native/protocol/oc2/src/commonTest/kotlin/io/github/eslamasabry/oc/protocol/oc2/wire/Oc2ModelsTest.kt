package io.github.eslamasabry.oc.protocol.oc2.wire

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ports the inline cases of test/api2_models_test.dart (the fixture cases live in desktopTest). */
class Oc2ModelsTest {
    private fun obj(json: String): JsonObject = Oc2Wire.json.parseToJsonElement(json).jsonObject

    @Test
    fun `B-PROTO-027 unknown message types content items tool states and enum values never throw`() {
        val future = Oc2Message.fromJson(
            obj("""{"id":"msg_future","type":"hologram","time":{"created":1},"brandNewField":{"nested":true}}"""),
        )
        val unknown = assertIs<Oc2Message.Unknown>(future)
        assertEquals("hologram", unknown.type)
        assertTrue("brandNewField" in unknown.raw)

        val odd = Oc2Message.fromJson(
            obj(
                """
                {"id":"msg_a","type":"assistant","finish":"brand-new-reason","content":[
                  {"type":"text","text":"hi"},
                  {"type":"video","uri":"x"},
                  "not-a-map",
                  {"type":"tool","id":"c1","name":"t","state":{"status":"paused"}}
                ]}
                """,
            ),
        )
        val assistant = assertIs<Oc2Message.Assistant>(odd)
        assertEquals("brand-new-reason", assistant.finish)
        assertEquals(3, assistant.content.size)
        assertIs<Oc2AssistantContent.Unknown>(assistant.content[1])
        val tool = assertIs<Oc2AssistantContent.Tool>(assistant.content[2])
        assertIs<Oc2ToolState.Unknown>(tool.state)

        assertEquals(Oc2SessionOutcome.Unknown, Oc2SessionOutcome.parse(JsonPrimitive("vanished")))
        assertEquals(Oc2Delivery.Unknown, Oc2Delivery.parse(JsonPrimitive("teleport")))
        assertEquals(Oc2FormFieldType.Unknown, Oc2FormFieldType.parse(JsonPrimitive("hologram")))
        assertEquals(Oc2FormStatus.Unknown, Oc2FormStatus.parse(JsonPrimitive(42)))
        assertNull(Oc2Session.fromJson(obj("""{"title":"no id"}""")))
        assertNull(Oc2Message.fromJson(obj("""{"type":"user"}""")))
    }

    @Test
    fun `B-PROTO-027 wrong-typed fields read as absent instead of failing the whole object`() {
        val session = Oc2Session.fromJson(
            obj("""{"id":"ses_1","title":42,"cost":"free","tokens":{"input":"x","output":7.9},"time":{"created":1.5}}"""),
        )!!
        assertNull(session.title)
        assertEquals(0.0, session.cost)
        assertEquals(0L, session.tokens.input)
        assertEquals(7L, session.tokens.output) // Dart num.toInt() truncates
        assertEquals(1L, session.time.created)
        assertFalse(session.reverted)

        val user = assertIs<Oc2Message.User>(
            Oc2Message.fromJson(obj("""{"id":"m","type":"user","text":null,"files":[1,{"mime":"text/plain"}]}""")),
        )
        assertEquals("", user.text)
        assertEquals(listOf("text/plain"), user.files.map { it.mime })
    }

    @Test
    fun `B-PROTO-027 composite model ids lose only their own provider prefix and default variants stay as sent`() {
        val ref = Oc2ModelRef.fromJson(obj("""{"id":"openai/gpt-5.6-sol","providerID":"openai","variant":"high"}"""))!!
        assertEquals("gpt-5.6-sol", ref.id)
        assertEquals("openai/gpt-5.6-sol#high", ref.toString())
        val foreign = Oc2ModelRef.fromJson(obj("""{"id":"anthropic/claude","providerID":"openrouter"}"""))!!
        assertEquals("anthropic/claude", foreign.id)
        assertNull(Oc2ModelRef.fromJson(obj("""{"id":"x"}""")))
    }

    @Test
    fun `B-PROTO-027 blank structured error text is no text at all and name stands in for type`() {
        val error = Oc2StructuredError.fromJson(obj("""{"name":"ProviderAuthError","message":"  ","status":401}"""))!!
        assertEquals("ProviderAuthError", error.type)
        assertNull(error.message)
        assertEquals(401, error.status)
    }

    @Test
    fun `B-PROTO-027 permission requests parse and replies use wire names`() {
        val request = Oc2PermissionRequest.fromJson(
            obj(
                """
                {"id":"per_1","sessionID":"ses_1","action":"bash","resources":["git push*"],"save":["git push*"],
                 "metadata":{"command":"git push"},"source":{"type":"tool","messageID":"msg_1","id":"call_1"},
                 "message":"wants to push"}
                """,
            ),
        )!!
        assertEquals("bash", request.action)
        assertEquals(listOf("git push*"), request.resources)
        assertEquals("msg_1", request.source?.messageID)
        assertEquals("always", Oc2PermissionReply.Always.wire)
        assertEquals("reject", Oc2PermissionReply.Reject.wire)
        assertEquals(Oc2PermissionEffect.Deny, Oc2PermissionEffect.parse(JsonPrimitive("deny")))
        assertEquals(Oc2PermissionEffect.Unknown, Oc2PermissionEffect.parse(JsonPrimitive("later")))
    }

    @Test
    fun `B-PROTO-027 forms parse the six field types and when conditions`() {
        val form = Oc2FormInfo.fromJson(
            obj(
                """
                {"id":"frm_1","sessionID":"global","title":"Connect","fields":[
                  {"key":"env","type":"string","required":true,"options":[{"value":"prod","label":"Production"},{"value":"dev"}],"custom":true},
                  {"key":"retries","type":"integer","minimum":0,"maximum":5},
                  {"key":"ratio","type":"number","default":0.5},
                  {"key":"confirm","type":"boolean","default":false},
                  {"key":"tags","type":"multiselect","options":[{"value":"a"},{"value":"b"}],"minItems":1,
                   "when":[{"key":"confirm","op":"eq","value":true}]},
                  {"key":"oauth","type":"external","url":"https://example.com"},
                  {"key":"mystery","type":"quantum"}
                ]}
                """,
            ),
        )!!
        assertEquals("global", form.sessionID)
        assertEquals(7, form.fields.size)
        assertEquals(Oc2FormFieldType.String, form.fields[0].type)
        assertTrue(form.fields[0].required)
        assertEquals(2, form.fields[0].options.size)
        assertTrue(form.fields[0].custom)
        assertEquals(Oc2FormFieldType.Integer, form.fields[1].type)
        assertEquals(5.0, form.fields[1].maximum)
        assertEquals(JsonPrimitive(0.5), form.fields[2].defaultValue)
        assertEquals(Oc2FormFieldType.Boolean, form.fields[3].type)
        assertEquals(Oc2FormFieldType.Multiselect, form.fields[4].type)
        assertEquals(Oc2FormFieldType.External, form.fields[5].type)
        assertEquals("https://example.com", form.fields[5].url)
        assertEquals(Oc2FormFieldType.Unknown, form.fields[6].type)

        val tags = form.fields[4]
        assertFalse(tags.activeFor(JsonObject(emptyMap())))
        assertFalse(tags.activeFor(buildJsonObject { put("confirm", false) }))
        assertTrue(tags.activeFor(buildJsonObject { put("confirm", true) }))

        val includes = Oc2FormCondition(key = "tags", op = "eq", value = JsonPrimitive("a"))
        assertTrue(includes.holds(obj("""{"tags":["a","b"]}""")))
        assertFalse(includes.holds(obj("""{"tags":["b"]}""")))
        val excludes = Oc2FormCondition(key = "tags", op = "neq", value = JsonPrimitive("a"))
        assertTrue(excludes.holds(obj("""{"tags":["b"]}""")))

        val state = Oc2FormState.fromJson(obj("""{"status":"answered","answer":{"env":"prod"}}"""))
        assertEquals(Oc2FormStatus.Answered, state.status)
        assertEquals(JsonPrimitive("prod"), state.answer?.get("env"))
    }

    @Test
    fun `B-PROTO-012 tagged error envelopes keep the tag the message and the extra fields`() {
        val envelope = Oc2ErrorEnvelope.fromJson(
            obj("""{"_tag":"SessionNotFoundError","sessionID":"ses_doesnotexist","message":"Session not found"}"""),
        )
        assertEquals("SessionNotFoundError", envelope.tag)
        assertEquals("Session not found", envelope.message)
        assertEquals("ses_doesnotexist", envelope.detail("sessionID"))
        assertEquals("boot", Oc2ErrorEnvelope.fromJson(obj("""{"code":"boot"}""")).tag)
    }

    @Test
    fun `B-PROTO-089 session usage reads cost tokens agent and model and archived from time`() {
        val session = Oc2Session.fromJson(
            obj(
                """
                {"id":"ses_1","agent":"build","model":{"id":"gpt","providerID":"openai"},"cost":0.25,
                 "tokens":{"input":10,"output":2,"reasoning":1,"cache":{"read":5,"write":3}},
                 "time":{"created":1,"archived":9},"revert":{"messageID":"msg_9","files":[{"path":"a"}]}}
                """,
            ),
        )!!
        assertEquals("build", session.agent)
        assertEquals("openai/gpt", session.model.toString())
        assertEquals(0.25, session.cost)
        assertEquals(21L, session.tokens.total)
        assertEquals(8L, session.tokens.cache)
        assertTrue(session.archived)
        assertTrue(session.reverted)
        assertEquals("msg_9", session.revert?.messageID)
        assertEquals(1, session.revert?.files?.size)
        val emptyRevert = Oc2Session.fromJson(obj("""{"id":"ses_2","revert":{"messageID":""}}"""))!!
        assertTrue(emptyRevert.reverted)
        assertNull(emptyRevert.revert)
    }

    @Test
    fun `B-PROTO-091 tool content keeps executed and run timing and a streaming tool keeps raw input text`() {
        val message = assertIs<Oc2Message.Assistant>(
            Oc2Message.fromJson(
                obj(
                    """
                    {"id":"m","type":"assistant","content":[
                      {"type":"tool","id":"c1","name":"bash","executed":false,"state":{"status":"streaming","input":"{\"cmd"},"time":{"created":5}},
                      {"type":"tool","id":"c2","name":"bash","executed":true,"state":{"status":"running","input":{"cmd":"ls"}},"time":{"created":5,"ran":6}}
                    ]}
                    """,
                ),
            ),
        )
        val streaming = message.content[0] as Oc2AssistantContent.Tool
        assertEquals(false, streaming.executed)
        assertEquals("{\"cmd", assertIs<Oc2ToolState.Streaming>(streaming.state).rawInput)
        assertNull(streaming.time.ran)
        val running = message.content[1] as Oc2AssistantContent.Tool
        assertEquals(6L, running.time.ran)
        assertEquals(JsonPrimitive("ls"), assertIs<Oc2ToolState.Running>(running.state).input["cmd"])
    }

    @Test
    fun `B-PROTO-093 model cost prefers the base entry over context tiers and agents expose colour and model`() {
        val model = Oc2ModelInfo.fromJson(
            obj(
                """
                {"id":"openai/gpt","providerID":"openai","cost":[
                  {"input":5,"output":10,"tier":{"type":"context","size":200000}},
                  {"input":2,"output":8,"cache":{"read":0.5,"write":1}}],
                 "time":{"released":1700000000000},"status":"beta","variants":[{"id":"low"},{"name":"x"},{"id":"high"}]}
                """,
            ),
        )!!
        assertEquals("gpt", model.modelID)
        assertEquals(2.0, model.baseCost?.input)
        assertEquals(0.5, model.baseCost?.cacheRead)
        assertEquals(200000L, model.cost[0].tierSize)
        assertEquals(1700000000000, model.released)
        assertEquals(listOf("low", "high"), model.variants)
        val single = Oc2ModelInfo.fromJson(obj("""{"id":"m","cost":{"input":1,"output":2}}"""))!!
        assertEquals(1, single.cost.size)

        val agent = Oc2AgentInfo.fromJson(
            obj("""{"name":"review","mode":"subagent","color":"#ff0000","model":{"id":"m","providerID":"p"}}"""),
        )!!
        assertEquals("review", agent.id)
        assertEquals("#ff0000", agent.color)
        assertFalse(agent.selectable)
        assertNull(Oc2AgentInfo.fromJson(obj("""{"mode":"primary"}""")))
    }
}
