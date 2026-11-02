package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.Tokens
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val CATALOG: (String, String) -> Long? = { provider, model ->
    when {
        provider != "p" -> null
        model == "m" -> 200_000L
        model == "nolimit" -> 0L
        else -> null
    }
}

private fun reply(id: String, tokens: Long, model: String = "m"): MessageWithParts =
    MessageWithParts(
        MessageInfo(
            id = id,
            sessionId = "s",
            role = Role.Assistant,
            providerId = "p",
            modelId = model,
            tokens = Tokens(input = tokens),
        ),
        persistentListOf(),
    )

private fun textPart(text: String, type: PartType = PartType.Text): Part =
    Part(id = "p-$text".take(8), messageId = "m", sessionId = "s", type = type, rawType = type.name.lowercase(), text = text)

private fun prompt(id: String): MessageWithParts =
    MessageWithParts(MessageInfo(id = id, sessionId = "s", role = Role.User), persistentListOf(textPart("hi")))

private fun compaction(id: String): MessageWithParts =
    MessageWithParts(
        MessageInfo(id = id, sessionId = "s", role = Role.User),
        persistentListOf(
            Part(id = "c", messageId = id, sessionId = "s", type = PartType.Compaction, rawType = "v2:compaction")
        ),
    )

private fun close(expected: Double, actual: Double) = assertTrue(kotlin.math.abs(expected - actual) < 1e-9, "$expected vs $actual")

class ContextUsageTest {
    @Test
    fun `B-CHAT-019 fraction is tokens over the window, unknown when either is missing`() {
        close(0.42, contextFraction(84_000, 200_000)!!)
        assertNull(contextFraction(0, 200_000))
        assertNull(contextFraction(5, 0))
    }

    @Test
    fun `B-CHAT-019 reads the newest reply against its own model`() {
        val reading =
            assertNotNull(
                contextReading(listOf(prompt("u1"), reply("a1", 40_000), prompt("u2"), reply("a2", 168_000)), CATALOG)
            )
        assertEquals(168_000L, reading.tokens)
        assertEquals(200_000L, reading.limit)
        close(0.84, reading.fraction)
        assertEquals(listOf(0.2, 0.84), reading.replies.map { it.fraction })
        assertEquals(listOf("a1", "a2"), reading.replies.map { it.messageId })
    }

    @Test
    fun `B-CHAT-019 a reply with no tokens is not a bar`() {
        val reading = assertNotNull(contextReading(listOf(reply("a1", 0), reply("a2", 20_000)), CATALOG))
        assertEquals(1, reading.replies.size)
    }

    @Test
    fun `B-CHAT-019 marks the reply after a compaction, the drop in the chart`() {
        val reading =
            assertNotNull(
                contextReading(
                    listOf(reply("a1", 150_000), compaction("c1"), reply("a2", 30_000), reply("a3", 50_000)),
                    CATALOG,
                )
            )
        assertEquals(listOf(false, true, false), reading.replies.map { it.afterCompaction })
        close(0.25, reading.fraction)
    }

    @Test
    fun `B-CHAT-019 a compaction before any reply marks nothing`() {
        val reading = assertNotNull(contextReading(listOf(compaction("c1"), reply("a1", 30_000)), CATALOG))
        assertFalse(reading.replies.single().afterCompaction)
    }

    @Test
    fun `B-CHAT-019 unknown when there is no catalog, no reply, or no window`() {
        assertNull(contextReading(listOf(reply("a", 5000)), null))
        assertNull(contextReading(listOf(prompt("u")), CATALOG))
        assertNull(contextReading(listOf(reply("a", 5000, model = "nolimit")), CATALOG))
        assertNull(contextReading(listOf(reply("a", 5000, model = "gone")), CATALOG))
    }

    @Test
    fun `B-CHAT-019 an unknown model on the newest reply hides the ring`() {
        assertNull(contextReading(listOf(reply("a1", 5000), reply("a2", 5000, model = "gone")), CATALOG))
    }

    @Test
    fun `B-CHAT-019 agent usage is used over max tokens from a usage report`() {
        val use =
            assertNotNull(
                agentContextUse(
                    buildJsonObject {
                        put("contextWindowUsedTokens", 50_000)
                        put("contextWindowMaxTokens", 200_000)
                        put("totalCostUsd", 0.4)
                    }
                )
            )
        assertEquals(50_000L, use.tokens)
        assertEquals(200_000L, use.limit)
        close(0.25, use.fraction)
    }

    @Test
    fun `B-CHAT-019 absent, partial or unusable agent usage is unknown`() {
        assertNull(agentContextUse(null))
        assertNull(agentContextUse(buildJsonObject { put("totalCostUsd", 1) }))
        assertNull(agentContextUse(buildJsonObject { put("contextWindowUsedTokens", 5) }))
        assertNull(
            agentContextUse(
                buildJsonObject {
                    put("contextWindowUsedTokens", 0)
                    put("contextWindowMaxTokens", 100)
                }
            )
        )
        assertNull(
            agentContextUse(
                buildJsonObject {
                    put("contextWindowUsedTokens", 5)
                    put("contextWindowMaxTokens", 0)
                }
            )
        )
    }

    @Test
    fun `B-CHAT-057 thresholds, under 70 calm, 70 to 94 warning, 95 and over full`() {
        assertEquals(ContextLevel.Calm, contextLevel(0.0))
        assertEquals(ContextLevel.Calm, contextLevel(0.6999))
        assertEquals(ContextLevel.Warning, contextLevel(0.7))
        assertEquals(ContextLevel.Warning, contextLevel(0.9499))
        assertEquals(ContextLevel.Full, contextLevel(0.95))
        assertEquals(ContextLevel.Full, contextLevel(1.4))
    }

    @Test
    fun `B-CHAT-057 the percent is whole, floored, and held to 0 to 100`() {
        assertEquals(70, contextPercent(0.7))
        assertEquals(94, contextPercent(0.945))
        assertEquals(95, contextPercent(0.95))
        assertEquals(100, contextPercent(2.0))
        assertEquals(0, contextPercent(-1.0))
        assertEquals(72, contextPercent(0.72))
    }

    @Test
    fun `B-CHAT-057 the halo fires once on crossing 70 or 95 upward, never while above or on the way down`() {
        assertTrue(contextLevelCrossedUp(0.6, 0.75))
        assertTrue(contextLevelCrossedUp(0.9, 0.96))
        assertFalse(contextLevelCrossedUp(0.75, 0.85))
        assertFalse(contextLevelCrossedUp(0.96, 0.99))
        assertFalse(contextLevelCrossedUp(0.8, 0.5))
    }

    private fun screenMessages(): List<MessageWithParts> {
        val user =
            MessageWithParts(
                MessageInfo(id = "user-1", sessionId = "session-1", role = Role.User, time = MsgTime(1, 2)),
                persistentListOf(textPart("u".repeat(40))),
            )
        // Indented JSON of {"k": "1234567"} is 20 characters, like the Dart fixture's 20-character input.
        val tool =
            Part(
                id = "t",
                messageId = "assistant-1",
                sessionId = "session-1",
                type = PartType.Tool,
                rawType = "tool",
                toolName = "bash",
                tool =
                    ToolState(
                        status = ToolStatus.Completed,
                        input = buildJsonObject { put("k", "1234567") },
                        output = "o".repeat(20),
                    ),
            )
        val assistant =
            MessageWithParts(
                MessageInfo(
                    id = "assistant-1",
                    sessionId = "session-1",
                    role = Role.Assistant,
                    providerId = "openai",
                    modelId = "gpt-context",
                    tokens = Tokens(input = 100, output = 20, reasoning = 10, cacheRead = 50, cacheWrite = 20),
                    cost = 0.0123,
                    time = MsgTime(2, 3),
                ),
                listOf(textPart("a".repeat(20)), tool).toImmutableList(),
            )
        return listOf(user, assistant)
    }

    @Test
    fun `B-CHAT-102 token totals retain cache activity`() {
        val facts = contextSheetFacts(screenMessages(), 1000)
        assertEquals(50L, facts.tokens.cacheRead)
        assertEquals(20L, facts.tokens.cacheWrite)
        assertEquals(200L, facts.contextTokens)
    }

    @Test
    fun `B-CHAT-102 context metrics use the latest assistant truth and label estimates`() {
        val facts = contextSheetFacts(screenMessages(), 1000)
        assertEquals("assistant-1", facts.currentMessageId)
        assertEquals(200L, facts.contextTokens)
        assertEquals(1000L, facts.contextLimit)
        close(0.2, facts.usage!!)
        assertEquals(1, facts.userMessages)
        assertEquals(1, facts.assistantMessages)
        close(0.0123, facts.sessionCost)
        assertEquals(100L, facts.breakdown.sumOf { it.tokens })
        assertEquals(75L, facts.breakdown.single { it.kind == ContextBreakdownKind.Other }.tokens)
        assertTrue(facts.estimated)
    }

    @Test
    fun `B-CHAT-102 no window or no reporting reply leaves usage unknown and nothing estimated`() {
        val unknown = contextSheetFacts(screenMessages(), null)
        assertNull(unknown.contextLimit)
        assertNull(unknown.usage)
        val empty = contextSheetFacts(listOf(prompt("u")), 1000)
        assertNull(empty.currentMessageId)
        assertEquals(0L, empty.contextTokens)
        assertTrue(empty.breakdown.isEmpty())
        assertFalse(empty.estimated)
    }
}
