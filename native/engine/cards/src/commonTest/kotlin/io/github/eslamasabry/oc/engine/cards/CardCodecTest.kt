package io.github.eslamasabry.oc.engine.cards

import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.cards.CardFixtures.assistant
import io.github.eslamasabry.oc.engine.cards.CardFixtures.card
import io.github.eslamasabry.oc.engine.cards.CardFixtures.input
import io.github.eslamasabry.oc.engine.cards.CardFixtures.parse
import io.github.eslamasabry.oc.engine.cards.CardFixtures.part
import io.github.eslamasabry.oc.engine.cards.CardFixtures.scope
import io.github.eslamasabry.oc.engine.cards.CardFixtures.user
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class CardCodecTest {
    @Test
    fun `B-CHAT-029 rejects cyclic, non-JSON, nonfinite and unpaired surrogate data safely`() {
        val cyclic = input().toMutableMap()
        val body = mutableListOf<Any?>()
        cyclic["body"] = body
        body.add(cyclic)
        val cases =
            listOf<Any?>(
                cyclic,
                input() + ("title" to Any()),
                input() + ("title" to "\ud800"),
                input() + ("body" to listOf(mapOf("type" to "progress", "label" to "X", "value" to Double.NaN))),
                input() + ("body" to listOf(mapOf("type" to "progress", "label" to "X", "value" to Double.POSITIVE_INFINITY))),
            )
        for (c in cases) {
            assertIs<CardParse.Unreadable>(CardCodec.fromInput(c, scope, "session", "assistant", "call"))
        }
    }

    @Test
    fun `B-CHAT-029 a very deep payload is refused as too large, never a crash`() {
        var deep: Any? = "leaf"
        repeat(10_000) { deep = listOf(deep) }
        val parsed = CardCodec.fromInput(input() + ("body" to listOf(deep)), scope, "session", "assistant", "call")
        assertEquals(CardParse.Unreadable(CardProblem.TooLarge), parsed)
    }

    @Test
    fun `B-CHAT-030 input previews, missing identity, synthetic and nonexecuted parts are not admitted`() {
        val refused =
            listOf(
                part(name = "other_oc-ui_show"),
                part(name = "show"),
                part(status = ToolStatus.Pending),
                part(status = ToolStatus.Running),
                part(status = ToolStatus.Error),
                part(executed = false),
                part(synthetic = true),
                part(call = ""),
                part(message = "other"),
                part(input = emptyMap()),
            )
        for (p in refused) assertNull(CardCodec.fromPart(p, scope, "session", "assistant"), p.toString())
        assertIs<CardParse.Parsed>(CardCodec.fromPart(part(name = "mcp__oc-ui__show"), scope, "session", "assistant"))
        assertIs<CardParse.Parsed>(CardCodec.fromPart(part(), scope, "session", "assistant"))
    }

    @Test
    fun `B-CHAT-031 revision is normalized and independent of key order, scope and call stay separate`() {
        val base = card()
        val reordered =
            linkedMapOf<String, Any?>(
                "body" to emptyList<Any?>(),
                "title" to " Example ",
                "id" to "example",
                "v" to 1L,
                "ask" to linkedMapOf("tone" to "normal", "kind" to "confirm"),
            )
        assertEquals(base.revision, card(reordered).revision)
        assertNotEquals(base.revision, card(input(title = "Changed")).revision)
        val other = (parse(input(), call = "other", scope = scope.copy(profileId = "other")) as CardParse.Parsed).card
        assertEquals(base.revision, other.revision)
        assertNotEquals(base.identity, other.identity)
    }

    @Test
    fun `B-CHAT-031 state requires matching current call and revision in a complete authoritative tail`() {
        val c = card()
        assertEquals(CardState.Waiting, CardCodec.stateFor(c, listOf(assistant()), true))
        assertEquals(CardState.Unknown, CardCodec.stateFor(c, listOf(assistant()), false))
        assertEquals(CardState.Unknown, CardCodec.stateFor(c, emptyList(), true))
        assertEquals(CardState.Unknown, CardCodec.stateFor(c, listOf(assistant(input = input(title = "Changed"))), true))
        assertEquals(CardState.Unknown, CardCodec.stateFor(c, listOf(assistant(call = "other")), true))
    }

    @Test
    fun `B-CHAT-031 only the newest eligible ask wins and reports do not supersede asks`() {
        val c = card()
        assertEquals(CardState.PassedOver, CardCodec.stateFor(c, listOf(assistant(), assistant(id = "second", call = "second")), true))
        assertEquals(
            CardState.Waiting,
            CardCodec.stateFor(c, listOf(assistant(), assistant(id = "report", call = "report", input = input(ask = null))), true),
        )
        val report = card(input(ask = null))
        assertEquals(CardState.Report, CardCodec.stateFor(report, listOf(assistant(input = input(ask = null))), true))
    }

    @Test
    fun `B-CHAT-031 only the first later authoritative user message settles a card`() {
        val c = card()
        val text = CardCodec.answerText(c, CardAnswer.Confirm(true))
        assertEquals(CardState.Answered, CardCodec.stateFor(c, listOf(assistant(), user(text)), true))
        assertEquals(
            CardState.PassedOver,
            CardCodec.stateFor(c, listOf(assistant(), user("Typed answer", id = "u1"), user(text, id = "u2")), true),
        )
        assertEquals(CardState.Waiting, CardCodec.stateFor(c, listOf(assistant(), user(text, synthetic = true)), true))
        assertEquals(CardState.Waiting, CardCodec.stateFor(c, listOf(assistant(), user(text, role = Role.Assistant)), true))
        assertEquals(CardState.Unknown, CardCodec.stateFor(c, listOf(assistant(), user(text, session = "other")), true))
        assertEquals(
            CardState.PassedOver,
            CardCodec.stateFor(c, listOf(assistant(), assistant(id = "second", call = "second"), user(text)), true),
        )
    }

    @Test
    fun `B-CHAT-032 answer encoder and decoder keep typed values and validate the exact envelope`() {
        val c = card()
        val text = CardCodec.answerText(c, CardAnswer.Confirm(false))
        assertEquals("[oc-ui answer example] Declined\n{\"v\":1,\"cardId\":\"example\",\"callId\":\"call\",\"value\":{\"confirm\":false}}", text)
        val answer = CardCodec.answerIn(user(text), c)
        assertEquals(mapOf("confirm" to false), answer?.second)
        assertEquals("Declined", answer?.first)
        assertEquals("example" to "Declined", CardCodec.answerEnvelope(user(text)))
        for (invalid in
            listOf(
                text.replaceFirst("\"call\"", "\"other\""),
                text.replaceFirst("\"example\"", "\"other\""),
                "$text\nextra",
                text.replaceFirst("\"confirm\":false", "\"confirm\":\"false\""),
                text.replaceFirst("\"v\":1", "\"v\":2"),
                text.replaceFirst("\"value\":", "\"extra\":true,\"value\":"),
            )) {
            assertNull(CardCodec.answerIn(user(invalid), c), invalid)
        }
        assertNull(CardCodec.answerIn(user(text, synthetic = true), c))
        assertNull(CardCodec.answerIn(user(text, role = Role.Assistant), c))
        assertNull(CardCodec.answerIn(user(text, session = "other"), c))
        assertFailsWith<CardAnswerInvalid> { CardCodec.answerText(c, CardAnswer.Photo(1)) }
    }

    @Test
    fun `B-CHAT-032 choice answers need known distinct ids within cardinality`() {
        val c =
            card(
                input(
                    ask =
                        mapOf(
                            "kind" to "choice",
                            "options" to listOf(mapOf("id" to "a", "label" to "Alpha"), mapOf("id" to "b", "label" to "Beta")),
                        )
                )
            )
        val text = CardCodec.answerText(c, CardAnswer.Choice(listOf("b")))
        assertEquals(mapOf("choice" to listOf("b")), CardCodec.answerIn(user(text), c)?.second)
        assertEquals("Beta", CardCodec.answerEnvelope(user(text))?.second)
        for (ids in listOf(emptyList(), listOf("unknown"), listOf("a", "b"), listOf("a", "a"))) {
            assertFailsWith<CardAnswerInvalid>(ids.toString()) { CardCodec.answerText(c, CardAnswer.Choice(ids)) }
        }
    }

    @Test
    fun `B-CHAT-032 form answers enforce required fields, real dates, ranges and absence rather than null`() {
        val c =
            card(
                input(
                    ask =
                        mapOf(
                            "kind" to "form",
                            "fields" to
                                listOf(
                                    mapOf("id" to "agree", "label" to "Agree", "type" to "toggle", "required" to true),
                                    mapOf("id" to "name", "label" to "Name", "type" to "text", "required" to true),
                                    mapOf("id" to "number", "label" to "Number", "type" to "number", "min" to 0L, "max" to 5L),
                                    mapOf("id" to "date", "label" to "Date", "type" to "date"),
                                ),
                        )
                )
            )
        val valid = linkedMapOf<String, Any>("agree" to false, "name" to "Ada", "number" to 5L, "date" to "2024-02-29")
        val text = CardCodec.answerText(c, CardAnswer.Form(valid))
        assertEquals(mapOf("form" to valid), CardCodec.answerIn(user(text), c)?.second)
        assertEquals("Submitted 4 fields", CardCodec.answerEnvelope(user(text))?.second)
        for (invalid in
            listOf<Map<String, Any>>(
                mapOf("name" to "Ada"),
                mapOf("agree" to false, "name" to " "),
                valid + ("number" to 6L),
                valid + ("date" to "2023-02-29"),
                valid + ("extra" to true),
                valid + ("name" to emptyList<String>()),
                valid + ("name" to "\ud800"),
            )) {
            assertFailsWith<CardAnswerInvalid>(invalid.toString()) { CardCodec.answerText(c, CardAnswer.Form(invalid)) }
        }
        CardCodec.answerText(c, CardAnswer.Form(mapOf("agree" to false, "name" to "Ada")))
    }

    @Test
    fun `B-CHAT-032 a photo receipt records the submitted count without claiming attachments are kept`() {
        val c = card(input(ask = mapOf("kind" to "photo", "purpose" to "Show result", "max" to 2L)))
        val text = CardCodec.answerText(c, CardAnswer.Photo(2))
        assertEquals(mapOf("photo" to 2L), CardCodec.answerIn(user(text), c)?.second)
        assertEquals("Submitted 2 photos", CardCodec.answerEnvelope(user(text))?.second)
        for (n in listOf(0, 3)) assertFailsWith<CardAnswerInvalid> { CardCodec.answerText(c, CardAnswer.Photo(n)) }
    }

    @Test
    fun `B-CHAT-033 a connector suggestion keeps identity and a plain reason, and refuses actionable payloads`() {
        val valid = input(ask = null) + ("connector" to mapOf("catalogId" to "acme/postgres", "reason" to "  Reads your database  "))
        val c = card(valid)
        assertEquals(ConnectorSuggestion("acme/postgres", "Reads your database"), c.connector)
        assertNull(c.ask)
        val refused =
            listOf(
                input() + ("connector" to mapOf("catalogId" to "acme/postgres", "reason" to "x")), // with an ask
                input(ask = null) + ("connector" to mapOf("catalogId" to "postgres", "reason" to "x")),
                input(ask = null) + ("connector" to mapOf("catalogId" to "acme/postgres", "reason" to " ")),
                input(ask = null) + ("connector" to mapOf("catalogId" to "acme/postgres", "reason" to "x", "command" to "npx")),
                input(ask = null) + ("connector" to mapOf("catalogId" to "acme/postgres", "reason" to "x".repeat(501))),
            )
        for (r in refused) assertIs<CardParse.Unreadable>(parse(r), r.toString())
        // Provenance: a connector in a running or synthetic call is not a card.
        assertNull(CardCodec.fromPart(part(valid, status = ToolStatus.Running), scope, "session", "assistant"))
        assertNull(CardCodec.fromPart(part(valid, synthetic = true), scope, "session", "assistant"))
    }

    @Test
    fun `B-CHAT-004 held-answer words agree with the verb the button names`() {
        val confirm = card(input(ask = mapOf("kind" to "confirm", "confirmLabel" to "Run it", "cancelLabel" to "Not now")))
        assertEquals("Run it", CardCodec.heldWords(confirm, CardAnswer.Confirm(true)))
        assertEquals("Not now", CardCodec.heldWords(confirm, CardAnswer.Confirm(false)))
        val choice =
            card(
                input(
                    ask =
                        mapOf(
                            "kind" to "choice",
                            "multi" to true,
                            "options" to listOf(mapOf("id" to "pg", "label" to "Postgres"), mapOf("id" to "sq", "label" to "SQLite")),
                        )
                )
            )
        assertEquals("Postgres, SQLite", CardCodec.heldWords(choice, CardAnswer.Choice(listOf("pg", "sq"))))
    }

    @Test
    fun `B-CHAT-029 Dart number and string printing feeds the revision`() {
        assertEquals("1.7976931348623157e+308", CardJson.dartDouble(Double.MAX_VALUE))
        assertEquals("0.25", CardJson.dartDouble(0.25))
        assertEquals("100000000000000000000.0", CardJson.dartDouble(1e20))
        assertEquals("1e+21", CardJson.dartDouble(1e21))
        assertEquals("1e-7", CardJson.dartDouble(1e-7))
        assertEquals("0.000001", CardJson.dartDouble(0.000001))
        assertEquals("-1.5", CardJson.dartDouble(-1.5))
        assertEquals("\"a\\\"b\\\\c\\n\\u0001é\"", CardJson.encode("a\"b\\c\n\u0001é"))
    }
}
