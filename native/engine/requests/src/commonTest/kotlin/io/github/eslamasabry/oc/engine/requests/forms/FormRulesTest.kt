package io.github.eslamasabry.oc.engine.requests.forms

import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The one form's rules (plan 09 §5.5, map CHAT-GENUI-48; Dart `form_renderer.dart` evaluate/validate). */
class FormRulesTest {
    private fun spec(vararg fields: String) = Oc2FormAdapter.adapt(wireForm(fields = arrayOf(*fields)))

    private val conditional =
        spec(
            """{"key":"kind","type":"string","required":true,"options":[{"value":"cloud"},{"value":"local"}]}""",
            """{"key":"region","type":"string","required":true,"maxLength":40,"when":[{"key":"kind","op":"eq","value":"cloud"}]}""",
            """{"key":"zone","type":"string","required":true,"maxLength":40,"when":[{"key":"region","op":"neq","value":"none"}]}""",
        )

    @Test
    fun `CHAT-GENUI-48 an inactive field is neither required nor sent, and its draft is kept`() {
        val draft = mapOf("kind" to FormValue.Text("local"), "region" to FormValue.Text("eu"), "zone" to FormValue.Text("b"))
        val e = FormRules.evaluate(conditional, draft)
        assertEquals(setOf("kind"), e.active, "region is off, and zone behind it falls with it (cascade)")
        assertTrue(e.canSend, "inactive required fields never block")
        assertEquals(setOf("kind"), FormRules.payload(conditional, draft).keys, "inactive drafts are not sent")
        // Switching back brings the kept draft into play.
        val back = FormRules.payload(conditional, draft + ("kind" to FormValue.Text("cloud")))
        assertEquals(setOf("kind", "region", "zone"), back.keys)
    }

    @Test
    fun `CHAT-GENUI-48 a condition on an unanswered field is false, and eq on a multi-select means includes`() {
        val s = spec(
            """{"key":"tags","type":"multiselect","options":[{"value":"db"},{"value":"web"}]}""",
            """{"key":"dbName","type":"string","maxLength":40,"when":[{"key":"tags","value":"db"}]}""",
        )
        assertEquals(setOf("tags"), FormRules.evaluate(s, emptyMap()).active)
        val chosen = mapOf("tags" to FormValue.Choices(persistentListOf("web", "db")))
        assertEquals(setOf("tags", "dbName"), FormRules.evaluate(s, chosen).active)
    }

    @Test
    fun `B-CHAT-072 required answers cannot be empty and the first problem is named`() {
        val e = FormRules.evaluate(conditional, mapOf("kind" to FormValue.Text("cloud"), "region" to FormValue.Text("   ")))
        assertFalse(e.canSend)
        assertEquals("region", e.firstProblemKey)
        assertEquals(FieldProblem.Required, e.problems["region"])
    }

    @Test
    fun `B-CHAT-072 a value the form does not offer never sends, unless the select takes own words`() {
        val closed = spec("""{"key":"k","type":"string","options":[{"value":"a"}]}""")
        val open = spec("""{"key":"k","type":"string","custom":true,"options":[{"value":"a"}]}""")
        val other = mapOf("k" to FormValue.Text("zzz"))
        assertEquals(FieldProblem.NotOffered, FormRules.evaluate(closed, other).problems["k"])
        assertTrue(FormRules.evaluate(open, other).canSend)
    }

    @Test
    fun `CHAT-GENUI-48 text, number and multi-select limits follow the schema`() {
        val s = spec(
            """{"key":"name","type":"string","minLength":3,"maxLength":5,"pattern":"^[a-z]+$"}""",
            """{"key":"n","type":"number","minimum":1,"maximum":5}""",
            """{"key":"i","type":"integer"}""",
            """{"key":"m","type":"multiselect","options":[{"value":"a"},{"value":"b"},{"value":"c"}],"minItems":2,"maxItems":2}""",
            """{"key":"bad","type":"string","maxLength":40,"pattern":"(["}""",
        )
        fun problems(vararg pairs: Pair<String, FormValue>) = FormRules.evaluate(s, mapOf(*pairs)).problems
        assertEquals(FieldProblem.TooShort(3), problems("name" to FormValue.Text("ab"))["name"])
        assertEquals(FieldProblem.TooLong(5), problems("name" to FormValue.Text("abcdef"))["name"])
        assertEquals(FieldProblem.NoMatch, problems("name" to FormValue.Text("ABC"))["name"])
        assertEquals(FieldProblem.NotANumber, problems("n" to FormValue.Text("2x"))["n"])
        assertEquals(FieldProblem.OutOfRange(1.0, 5.0), problems("n" to FormValue.Text("9"))["n"])
        assertNull(problems("n" to FormValue.Text("٣"))["n"], "Arabic-Indic digits read as numbers")
        assertEquals(FieldProblem.NotAWholeNumber, problems("i" to FormValue.Text("2.5"))["i"])
        assertEquals(FieldProblem.TooFewItems(2), problems("m" to FormValue.Choices(persistentListOf("a")))["m"])
        assertEquals(
            FieldProblem.TooManyItems(2),
            problems("m" to FormValue.Choices(persistentListOf("a", "b", "c")))["m"],
        )
        assertNull(problems("bad" to FormValue.Text("x"))["bad"], "an invalid server pattern never locks the form")
    }

    @Test
    fun `CHAT-GENUI-48 the payload keeps types - whole numbers without a fraction, toggles, string lists`() {
        val s = spec(
            """{"key":"n","type":"number"}""",
            """{"key":"i","type":"integer"}""",
            """{"key":"on","type":"boolean"}""",
            """{"key":"m","type":"multiselect","options":[{"value":"a"},{"value":"b"}]}""",
            """{"key":"link","type":"external","url":"https://example.com"}""",
        )
        val payload = FormRules.payload(
            s,
            mapOf(
                "n" to FormValue.Text("2.5"),
                "i" to FormValue.Text("7"),
                "m" to FormValue.Choices(persistentListOf("b")),
            ),
        )
        assertEquals("""{n=2.5, i=7, on=false, m=["b"]}""", payload.toString(), "an external link never answers")
    }

    @Test
    fun `B-SEC-204 credential-like fields use the sensitive field`() {
        val s = spec(
            """{"key":"apiKey","type":"string"}""",
            """{"key":"x","title":"Private key","type":"string"}""",
            """{"key":"user","title":"User name","type":"string"}""",
            """{"key":"tokens","type":"integer"}""",
        )
        assertEquals(listOf(true, true, false, false), s.fields.map(FormRules::isSensitive))
    }

    @Test
    fun `CHAT-GENUI-48 the sheet is full height for five declared fields or a long description, and ignores when`() {
        val four = spec(*Array(4) { """{"key":"f$it","type":"string","when":[{"key":"nope","value":true}]}""" })
        val five = spec(*Array(5) { """{"key":"f$it","type":"string","when":[{"key":"nope","value":true}]}""" })
        val long = spec("""{"key":"a","type":"string","description":"${"x".repeat(141)}"}""")
        val hiddenFifth = spec(*Array(4) { """{"key":"f$it","type":"string"}""" }, """{"key":"h","hidden":true,"type":"string"}""")
        assertFalse(FormRules.prefersFullHeight(four))
        assertTrue(FormRules.prefersFullHeight(five), "inactive fields still count, so the sheet never jumps")
        assertTrue(FormRules.prefersFullHeight(long))
        assertFalse(FormRules.prefersFullHeight(hiddenFifth), "hidden fields are never shown, so they never count")
    }

    @Test
    fun `B-STATE-024 drafts are kept per form identity and stored under oc draft target profile`() {
        val drafts = FormDrafts()
        val s = spec("""{"key":"note","type":"string","default":"hi"}""")
        assertEquals(mapOf<String, FormValue>("note" to FormValue.Text("hi")), drafts.open("id-1", s), "a fresh sheet starts from defaults")
        drafts.put("id-1", mapOf("note" to FormValue.Text("typed")))
        assertEquals(FormValue.Text("typed"), drafts.open("id-1", s)["note"], "closing and reopening restores")
        assertEquals(FormValue.Text("hi"), drafts.open("id-2", s)["note"], "another revision never inherits it")
        drafts.clear("id-1")
        assertNull(drafts.get("id-1"))
        val key = FormDrafts.storageKey("abc", "my.note", "prof1")
        assertTrue(key.startsWith("oc.draft.form-abc-") && key.endsWith(".prof1"), key)
        assertEquals(3, key.count { it == '.' }, "the target holds no dots, so the profile sweep can parse it: $key")
    }
}
