package io.github.eslamasabry.oc.feature.chats

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
}
