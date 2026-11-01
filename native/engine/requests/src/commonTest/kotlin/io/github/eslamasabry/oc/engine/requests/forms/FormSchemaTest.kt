package io.github.eslamasabry.oc.engine.requests.forms

import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The adapter and the schema revision (plan 09 §5.5; Dart `form_request.dart`, `Api2FormField.fromJson`). */
class FormSchemaTest {
    @Test
    fun `B-CHAT-028 equal schemas in a different key order have the same revision`() {
        val a = wireForm(metadata = """{"kind":"form","nested":{"a":1,"b":[1,2]}}""",
            fields = arrayOf("""{"key":"name","type":"string","required":true,"minLength":2}"""))
        val b = wireForm(metadata = """{"nested":{"b":[1,2],"a":1},"kind":"form"}""",
            fields = arrayOf("""{"minLength":2,"required":true,"type":"string","key":"name"}"""))
        assertEquals(FormRevision.of(a), FormRevision.of(b))
    }

    @Test
    fun `B-CHAT-028 any constraint, nested metadata or value type change gives a different revision`() {
        val base = FormRevision.of(wireForm(metadata = """{"n":{"a":1}}""",
            fields = arrayOf("""{"key":"n","type":"integer","maximum":10}""")))
        val changedLimit = FormRevision.of(wireForm(metadata = """{"n":{"a":1}}""",
            fields = arrayOf("""{"key":"n","type":"integer","maximum":11}""")))
        val changedNested = FormRevision.of(wireForm(metadata = """{"n":{"a":2}}""",
            fields = arrayOf("""{"key":"n","type":"integer","maximum":10}""")))
        val changedType = FormRevision.of(wireForm(metadata = """{"n":{"a":"1"}}""",
            fields = arrayOf("""{"key":"n","type":"integer","maximum":10}""")))
        val changedCondition = FormRevision.of(wireForm(metadata = """{"n":{"a":1}}""",
            fields = arrayOf("""{"key":"n","type":"integer","maximum":10,"when":[{"key":"x","op":"eq","value":true}]}""")))
        assertEquals(5, setOf(base, changedLimit, changedNested, changedType, changedCondition).size)
    }

    @Test
    fun `B-CHAT-028 field order is identity`() {
        val ab = wireForm(fields = arrayOf("""{"key":"a","type":"string"}""", """{"key":"b","type":"string"}"""))
        val ba = wireForm(fields = arrayOf("""{"key":"b","type":"string"}""", """{"key":"a","type":"string"}"""))
        assertNotEquals(FormRevision.of(ab), FormRevision.of(ba))
    }

    @Test
    fun `B-CHAT-028 the identity key separates every scope part without delimiter collisions`() {
        val one = FormRevision.identityKey("p|1", "/w", null, "s", "f", "r")
        val two = FormRevision.identityKey("p", "1|/w", null, "s", "f", "r")
        val noWorkspace = FormRevision.identityKey("p", "/w", null, "s", "f", "r")
        val emptyWorkspace = FormRevision.identityKey("p", "/w", "", "s", "f", "r")
        val otherRevision = FormRevision.identityKey("p", "/w", null, "s", "f", "r2")
        assertEquals(5, setOf(one, two, noWorkspace, emptyWorkspace, otherRevision).size)
    }

    @Test
    fun `B-PROTO-022 hidden OpenCode 2 fields become preset answers and visible ones become prompts`() {
        // OpenCode 2.1.1's real Console sign-in form (Dart api2_integration_form_test.dart).
        val spec = Oc2FormAdapter.adapt(
            wireForm(fields = arrayOf(
                """{"key":"server","hidden":true,"type":"string","format":"uri","default":"https://opencode.ai/console"}""",
                """{"key":"location","title":"Location","required":true,"type":"string","default":"global",
                   "options":[{"value":"global","label":"Global"},{"value":"us-east5"}]}""",
            )),
        )
        assertEquals(listOf("location"), FormRules.visible(spec).map { it.key }, "the hidden field is never shown")
        val payload = FormRules.payload(spec, FormRules.initialDraft(spec))
        assertEquals("\"https://opencode.ai/console\"", payload["server"].toString(), "the hidden default is sent")
        assertEquals("\"global\"", payload["location"].toString())
        assertTrue(FormRules.evaluate(spec, FormRules.initialDraft(spec)).canSend, "no 'Select an option' refusal")
    }

    @Test
    fun `B-PROTO-044 the adapter reads every field kind and drops what cannot be answered`() {
        val spec = Oc2FormAdapter.adapt(
            wireForm(fields = arrayOf(
                """{"key":"short","type":"string","maxLength":40}""",
                """{"key":"long","type":"string"}""",
                """{"key":"pick","type":"string","options":[{"value":"a"}],"custom":true}""",
                """{"key":"day","type":"string","format":"date"}""",
                """{"key":"at","type":"string","format":"date-time"}""",
                """{"key":"n","type":"number","minimum":0,"maximum":"9"}""",
                """{"key":"i","type":"integer"}""",
                """{"key":"ok","type":"boolean","default":true}""",
                """{"key":"many","type":"multiselect","options":[{"value":"x"},{"value":"y"}],"minItems":1,"maxItems":"2"}""",
                """{"key":"link","type":"external","url":"https://example.com/auth"}""",
                """{"key":"mystery","type":"blob"}""",
                """{"type":"string"}""",
            )),
        )
        assertEquals(
            listOf(FieldKind.Text, FieldKind.Multiline, FieldKind.Select, FieldKind.Date, FieldKind.DateTime,
                FieldKind.Number, FieldKind.Integer, FieldKind.Toggle, FieldKind.MultiSelect, FieldKind.ExternalLink),
            spec.fields.map { it.kind },
        )
        val n = spec.fields.first { it.key == "n" }
        assertEquals(0.0, n.min)
        assertNull(n.max, "a wrong-typed limit reads as absent")
        val many = spec.fields.first { it.key == "many" }
        assertEquals(1, many.minItems)
        assertNull(many.maxItems)
        assertTrue(spec.fields.first { it.key == "pick" }.custom)
        assertEquals("a", spec.fields.first { it.key == "pick" }.options.single().label, "a label defaults to its value")
        assertEquals("long", spec.fields.first { it.key == "long" }.label, "a label defaults to the key")
        assertEquals("https://example.com/auth", spec.fields.first { it.key == "link" }.url)
    }

    @Test
    fun `B-PROTO-044 a global form belongs to the connection, a session form to its conversation, and the question tool is a question`() {
        val global = Oc2FormAdapter.adapt(wireForm(session = "global"))
        val session = Oc2FormAdapter.adapt(wireForm(session = "ses_9"))
        val question = Oc2FormAdapter.adapt(wireForm(metadata = """{"kind":"question","description":"Pick one"}"""))
        assertEquals(FormOwner.Connection, global.owner)
        assertEquals(FormOwner.Session(SessionId("ses_9")), session.owner)
        assertTrue(question.isQuestion)
        assertEquals("Pick one", question.description)
        assertFalse(session.isQuestion)
    }

    @Test
    fun `B-PROTO-044 conditions read eq and neq, and default to eq`() {
        val f = Oc2FormAdapter.field(
            kotlinx.serialization.json.Json.parseToJsonElement(
                """{"key":"k","type":"string","when":[{"key":"a","value":"x"},{"key":"b","op":"neq","value":true},{"op":"eq"}]}""",
            ) as kotlinx.serialization.json.JsonObject,
        )!!
        assertEquals(
            persistentListOf(
                FieldCondition("a", ConditionOp.Eq, FormValue.Text("x")),
                FieldCondition("b", ConditionOp.Neq, FormValue.Toggle(true)),
            ),
            f.whenAll,
        )
    }
}
