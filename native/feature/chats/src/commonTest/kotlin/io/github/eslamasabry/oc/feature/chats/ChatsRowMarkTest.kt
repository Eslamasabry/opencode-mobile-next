package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsState
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatsRowMarkTest {
    @Test
    fun `SCREENS-ROWS-01 a row carries its agent's mark and name`() {
        val claude = ChatsState.row(item("a").copy(agentId = "claude-code"))
        assertEquals("claude", claude.agentMark)
        assertEquals("Claude Code", claude.agentName)
        val plain = ChatsState.row(item("b"))
        assertEquals("opencode", plain.agentMark)
        assertEquals("OpenCode", plain.agentName)
    }

    @Test
    fun `SCREENS-ROWS-02 a row names its server only when the feed does`() {
        assertEquals(null, ChatsState.row(item("a")).server)
        assertEquals("Studio", ChatsState.row(item("a").copy(sourceLabel = "Studio")).server)
    }

    @Test
    fun `SCREENS-ROWS-01 an agent chat is named by its maker's name, else the name it was given, never its title`() {
        // A Paseo row carries the provider id; the daemon's own label does not replace the brand name.
        assertEquals("Codex", ChatsState.row(item("a").copy(agentId = "codex", agentLabel = "codex-acp")).agentName)
        assertEquals("Hermes", ChatsState.row(item("b").copy(agentId = "hermes", agentLabel = " Hermes ")).agentName)
        // No name at all: null, and the screen words it ("Another agent") in the person's language.
        assertEquals(null, ChatsState.row(item("c", title = "Fix the build").copy(agentId = "hermes")).agentName)
    }

    @Test
    fun `B-AGENT-164 Archive is offered on agent chats whose server archives, and never on one that cannot`() {
        val paseo = ProfileId("paseo")
        val codex = ProfileId("codex")
        val claudeOnPaseo = item("p1").copy(profileId = paseo, agentId = "claude", sourceId = "paseo")
        val codexChat = item("c1").copy(profileId = codex, agentId = "codex", sourceId = "codex")
        val subRun = item("p2", parent = "p1").copy(profileId = paseo, agentId = "claude", sourceId = "paseo")
        val openCode = item("o1")
        val archivable =
            ChatsState.archivable(listOf(claudeOnPaseo, codexChat, subRun, openCode), setOf(paseo, P1))
        assertEquals(setOf(claudeOnPaseo.identity, openCode.identity), archivable)
    }
}
