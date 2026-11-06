package io.github.eslamasabry.oc.engine.composer

import io.github.eslamasabry.oc.engine.composer.input.AgentSuggestions
import io.github.eslamasabry.oc.engine.composer.input.MentionAgent
import io.github.eslamasabry.oc.engine.composer.input.Mentions
import kotlin.test.Test
import kotlin.test.assertEquals

/** E04-S01: the agents half of the `@` strip, from a fixture sub-agent list (Flutter `_inlineSuggestions`). */
class AgentSuggestionsTest {
    private val fixture =
        listOf(
            MentionAgent("reviewer", "Reads a diff and lists risks"),
            MentionAgent("explore", "Searches the code base"),
            MentionAgent("general"),
            MentionAgent("docs-writer", "Writes and reviews docs"),
            MentionAgent("explore", "a repeated id"),
            MentionAgent(""),
        )

    @Test
    fun `B-CHAT-087 a bare at lists every sub-agent by id`() {
        assertEquals(
            listOf("docs-writer", "explore", "general", "reviewer"),
            AgentSuggestions.rank("", fixture).map { it.id },
        )
    }

    @Test
    fun `B-CHAT-087 typing filters on id or description, id prefix first`() {
        // "rev" starts reviewer, and is inside docs-writer's description ("reviews").
        assertEquals(listOf("reviewer", "docs-writer"), AgentSuggestions.rank("rev", fixture).map { it.id })
        assertEquals(listOf("explore"), AgentSuggestions.rank("CODE", fixture).map { it.id })
        assertEquals(emptyList(), AgentSuggestions.rank("zzz", fixture))
    }

    @Test
    fun `B-CHAT-087 the strip holds five rows and files fill what agents leave`() {
        val many = (1..9).map { MentionAgent("agent$it") }
        assertEquals(5, AgentSuggestions.rank("", many).size)
        assertEquals(3, AgentSuggestions.fileRoom(2))
        assertEquals(0, AgentSuggestions.fileRoom(7))
    }

    @Test
    fun `B-PROTO-023 a picked agent writes the token the send reads back as an agent part`() {
        val picked = AgentSuggestions.rank("rev", fixture).first()
        val text = "please " + AgentSuggestions.token(picked) + "look"
        assertEquals("please @reviewer look", text)
        val mention = Mentions.agentMentions(text, fixture.map { it.id }).single()
        assertEquals("reviewer", mention.name)
        assertEquals("@reviewer", mention.value)
        assertEquals(7, mention.start)
    }
}
