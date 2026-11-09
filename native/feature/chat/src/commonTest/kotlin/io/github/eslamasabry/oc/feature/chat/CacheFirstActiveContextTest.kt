package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.engine.agents.context.ActiveContextMessage
import io.github.eslamasabry.oc.engine.agents.context.ContextContent
import io.github.eslamasabry.oc.engine.agents.context.ContextContentKind
import io.github.eslamasabry.oc.feature.chat.context.ActiveContextUi
import io.github.eslamasabry.oc.feature.chat.context.KeptActiveContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json

class CacheFirstActiveContextTest {
    private val live =
        listOf(
            ActiveContextMessage("m1", "user", listOf(ContextContent(ContextContentKind.Text, "fix the build"))),
            ActiveContextMessage(
                "m2",
                "assistant",
                listOf(
                    ContextContent(ContextContentKind.Text, "x".repeat(2000)),
                    ContextContent(ContextContentKind.ToolOutput, "API_KEY=abc123", "bash"),
                ),
            ),
        )

    private fun kept() =
        Json.decodeFromString(KeptActiveContext.serializer(), Json.encodeToString(KeptActiveContext.serializer(), KeptActiveContext.of(live)))

    @Test
    fun `CF-84 the first frame with kept messages lists the same ids and types, refreshing quietly`() {
        val first = ActiveContextUi.Ready(kept().toMessages().toImmutableList(), refreshing = true)
        assertEquals(live.map { it.id to it.type }, first.messages.map { it.id to it.type })
        assertEquals("fix the build", first.messages[0].previewFor(""))
    }

    @Test
    fun `CF-85 kept messages cut long text and never keep tool output`() {
        val m = kept().toMessages()[1]
        assertEquals(KeptActiveContext.MAX_TEXT, m.content[0].text.length)
        assertEquals("", m.content[1].text)
        assertEquals(KeptActiveContext.MAX_MESSAGES, KeptActiveContext.of(List(200) { ActiveContextMessage("$it", "user", emptyList()) }).messages.size)
    }

    @Test
    fun `CF-86 with nothing kept the first-ever read is the loading state`() {
        assertEquals(emptyList(), KeptActiveContext().toMessages())
    }
}
