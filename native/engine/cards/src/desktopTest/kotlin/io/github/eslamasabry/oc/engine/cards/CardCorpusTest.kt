package io.github.eslamasabry.oc.engine.cards

import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * The shared corpus (`test/fixtures/genui/validation.json`, copied verbatim) and the expectations its JavaScript twin
 * gives (`validation.expected.json`, written by the helper's own validator under Node from the same file; the Flutter
 * suite proves Dart and that twin agree on acceptance and normalised output).
 */
class CardCorpusTest {
    private fun resource(name: String): String =
        CardCorpusTest::class.java.getResourceAsStream("/genui/$name")?.readBytes()?.decodeToString()
            ?: fail("missing fixture $name")

    private val scope = CardScope("profile", "source", "/project")

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `B-CHAT-029 shared corpus gives the same accept, reject and normalised output as the JS twin`() {
        val cases = CardJson.parse(resource("validation.json")) as List<Map<String, Any?>>
        val expected = CardJson.parse(resource("validation.expected.json")) as List<Map<String, Any?>>
        assertEquals(65, cases.size)
        assertEquals(cases.size, expected.size)
        for ((c, js) in cases.zip(expected)) {
            val label = c["name"] as String
            assertEquals(label, js["name"])
            val result = CardCodec.normalized(c["input"])
            val error = c["error"] as String?
            if (error != null) {
                val problem = result.exceptionOrNull()?.let(CardCodec::problemOf) ?: fail("$label: accepted, expected $error")
                assertEquals(error, problem.name.replaceFirstChar { it.lowercase() }, label)
                assertEquals(error, js["error"], label)
            } else {
                val value = result.getOrElse { fail("$label: refused (${CardCodec.problemOf(it)})") }
                assertNull(js["error"], label)
                assertEquals(numbersAsDoubles(js["value"]), numbersAsDoubles(value), label)
                // Normalising the twin's output gives the same canonical text and revision.
                val again = CardCodec.normalized(js["value"]).getOrThrow()
                assertEquals(CardJson.encode(value), CardJson.encode(again), label)
                assertEquals(CardCodec.revision(value), CardCodec.revision(again), label)
                assertIs<CardParse.Parsed>(CardCodec.fromInput(c["input"], scope, "s", "m", "c"), label)
            }
        }
    }

    @Test
    fun `B-CHAT-029 every directional control is stripped from text and refused in links`() {
        val codes = listOf(0x061c, 0x200e, 0x200f) + (0x202a..0x202e) + (0x2066..0x2069)
        val title = "Before" + codes.joinToString("") { it.toChar().toString() } + "After"
        val parsed = CardCodec.fromInput(CardFixtures.input(title = title), scope, "s", "m", "c")
        assertEquals("BeforeAfter", (parsed as CardParse.Parsed).card.title)
        for (code in codes) {
            val link = mapOf("type" to "link", "label" to "Link", "url" to "https://example.com/a${code.toChar()}b")
            assertEquals(
                CardParse.Unreadable(CardProblem.BadValue),
                CardCodec.fromInput(CardFixtures.input() + ("body" to listOf(link)), scope, "s", "m", "c"),
                code.toString(16),
            )
        }
    }

    @Test
    fun `B-CHAT-029 the canonical text keeps schema order and Dart numbers`() {
        val value =
            CardCodec.normalized(
                linkedMapOf(
                    "body" to listOf(mapOf("value" to 0.5, "label" to "Half", "type" to "progress")),
                    "title" to "T",
                    "id" to "x",
                    "v" to 1.0,
                )
            ).getOrThrow()
        assertEquals("""{"v":1,"id":"x","title":"T","body":[{"type":"progress","label":"Half","value":0.5}]}""", CardJson.encode(value))
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `B-CHAT-045 a captured Paseo Claude tool becomes a scoped confirm card and its running form stays a tool`() {
        val capture = CardJson.parse(resource("runtime_paseo_claude_tool.json")) as Map<String, Any?>
        val item = ((capture["entries"] as List<*>).single() as Map<String, Any?>)["item"] as Map<String, Any?>
        val detail = item["detail"] as Map<String, Any?>
        val fixtureScope = CardScope("fixture-profile", "fixture-paseo-source", "/root/projects/fixture")
        fun message(status: ToolStatus): MessageWithParts {
            val p =
                CardFixtures.part(
                    input = detail["input"] as Map<String, Any?>,
                    call = item["callId"] as String,
                    message = "paseo-1",
                    name = item["name"] as String,
                    status = status,
                    session = "fixture-session",
                )
            return MessageWithParts(MessageInfo("paseo-1", "fixture-session", Role.Assistant), persistentListOf(p))
        }
        val done = message(ToolStatus.Completed)
        val card = (CardCodec.fromPart(done.parts.single(), fixtureScope, "fixture-session", "paseo-1") as CardParse.Parsed).card
        assertEquals(fixtureScope, card.scope)
        assertEquals("call_runtime_probe", card.callId)
        assertEquals("runtime-probe", card.id)
        assertEquals("Runtime probe", card.title)
        assertIs<CardAsk.Confirm>(card.ask)
        assertEquals(CardState.Waiting, CardCodec.stateFor(card, listOf(done), true))
        assertEquals(CardState.Unknown, CardCodec.stateFor(card, listOf(done), false))
        val running = message(ToolStatus.Running)
        assertNull(CardCodec.fromPart(running.parts.single(), fixtureScope, "fixture-session", "paseo-1"))
    }

    /** JSON numbers compare by value (the twin prints integers and doubles alike). */
    private fun numbersAsDoubles(v: Any?): Any? =
        when (v) {
            is Long -> v.toDouble()
            is Int -> v.toDouble()
            is Map<*, *> -> v.entries.associate { (k, x) -> k to numbersAsDoubles(x) }
            is List<*> -> v.map(::numbersAsDoubles)
            else -> v
        }
}
