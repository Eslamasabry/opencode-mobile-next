package io.github.eslamasabry.oc.engine.cards

import io.github.eslamasabry.oc.engine.cards.CardFixtures.assistant
import io.github.eslamasabry.oc.engine.cards.CardFixtures.card
import io.github.eslamasabry.oc.engine.cards.CardFixtures.input
import io.github.eslamasabry.oc.engine.cards.CardFixtures.json
import io.github.eslamasabry.oc.engine.cards.CardFixtures.scope
import io.github.eslamasabry.oc.engine.cards.CardFixtures.user
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * B-CHAT-029 (P12-CHAT-GAPS): a hostile nesting from the wire or in an answer line is refused the way Flutter's
 * `_bounded` refuses it (too large past depth 8, bad value for whatever fails first in walk order), never a
 * StackOverflowError while the input is still being copied into the validator's tree.
 */
class CardDepthTest {
    private fun wirePart(input: JsonObject): Part =
        Part(
            id = "part-call",
            messageId = "assistant",
            sessionId = "session",
            type = PartType.Tool,
            rawType = "tool",
            callId = "call",
            toolName = "oc-ui_show",
            tool = ToolState(status = ToolStatus.Completed, input = input, executedFlag = true),
        )

    /** [levels] arrays around a string, built without recursion so the test itself never overflows. */
    private fun nested(levels: Int): JsonElement {
        var deep: JsonElement = JsonPrimitive("leaf")
        repeat(levels) { deep = JsonArray(listOf(deep)) }
        return deep
    }

    private fun withBody(vararg body: JsonElement): JsonObject {
        val base = json(input()) as JsonObject
        return JsonObject(base + ("body" to JsonArray(body.toList())))
    }

    @Test
    fun `B-CHAT-029 a very deep wire payload is refused as too large, never a crash`() {
        val parsed = CardCodec.fromPart(wirePart(withBody(nested(100_000))), scope, "session", "assistant")
        assertEquals(CardParse.Unreadable(CardProblem.TooLarge), parsed)
    }

    @Test
    fun `B-CHAT-029 the refusal is the one Flutter gives - the first failure in walk order wins`() {
        // Dart walks depth first: the broken text comes before the deep branch, so the reason is a bad value.
        val broken = json(mapOf("type" to "text", "text" to "\ud800"))
        val parsed = CardCodec.fromPart(wirePart(withBody(broken, nested(100_000))), scope, "session", "assistant")
        assertEquals(CardParse.Unreadable(CardProblem.BadValue), parsed)
        // The same tree, built shallow enough to reach the validator unchanged, gives the same answer.
        var plain: Any? = "leaf"
        repeat(20) { plain = listOf(plain) }
        val direct =
            CardCodec.fromInput(
                input() + ("body" to listOf(mapOf("type" to "text", "text" to "\ud800"), plain)),
                scope, "session", "assistant", "call",
            )
        assertEquals(direct, parsed)
    }

    @Test
    fun `B-CHAT-029 depth 8 is the last level the validator reads, from the wire and as a tree`() {
        // input(1) → body(2) → arrays(3..8) → leaf(8 or 9): six arrays put the leaf at 9, five at 8.
        val atNine = CardCodec.fromPart(wirePart(withBody(nested(6))), scope, "session", "assistant")
        assertEquals(CardParse.Unreadable(CardProblem.TooLarge), atNine)
        val atEight = CardCodec.fromPart(wirePart(withBody(nested(5))), scope, "session", "assistant")
        // Within the depth limit the node itself is wrong (an array is not a card node): a bad value, as in Dart.
        assertEquals(CardParse.Unreadable(CardProblem.BadValue), atEight)
    }

    @Test
    fun `B-CHAT-029 a cyclic tree is a bad value, a normal payload still parses`() {
        val cyclic = input().toMutableMap()
        val body = mutableListOf<Any?>()
        cyclic["body"] = body
        body.add(body)
        assertEquals(CardParse.Unreadable(CardProblem.BadValue), CardCodec.fromInput(cyclic, scope, "s", "m", "c"))

        val normal = input() + ("body" to listOf(mapOf("type" to "text", "text" to "Hello")))
        val fromWire = CardCodec.fromPart(wirePart(json(normal) as JsonObject), scope, "session", "assistant")
        val parsed = assertIs<CardParse.Parsed>(fromWire)
        assertEquals("Example", parsed.card.title)
        assertEquals(1, parsed.card.body.size)
        val fromTree = assertIs<CardParse.Parsed>(CardCodec.fromInput(normal, scope, "session", "assistant", "call"))
        assertEquals(fromTree.card.revision, parsed.card.revision)
    }

    @Test
    fun `B-CHAT-029 a deep answer line is not a receipt and never crashes the card state`() {
        val c = card()
        val deep = "[".repeat(16_000) + "]".repeat(16_000)
        val message = user("[oc-ui answer example] Yes\n$deep")
        assertNull(CardCodec.answerIn(message, c))
        assertNull(CardCodec.answerEnvelope(message))
        assertEquals(CardState.PassedOver, CardCodec.stateFor(c, listOf(assistant(), message), true))
    }

    @Test
    fun `B-CHAT-029 general JSON deeper than the tree limit is refused plainly, not with a stack overflow`() {
        val text = "[".repeat(100_000) + "]".repeat(100_000)
        assertFailsWith<IllegalArgumentException> { CardJson.parse(text) }
        assertFailsWith<IllegalArgumentException> { CardJson.tree(nested(100_000)) }
        assertEquals(listOf(listOf("leaf")), CardJson.tree(nested(2)))
    }
}
