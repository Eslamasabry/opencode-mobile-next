package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsFilters
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsState
import io.github.eslamasabry.oc.feature.chats.presentation.HomeCachedRows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

class HomeCachedRowsTest {
    private val saved =
        listOf(
            item("a", at = 3_000, title = "Fix login").copy(preview = "I fixed the form", agentId = "claude-code"),
            item("b", at = 2_000, title = "Plan release"),
        )

    private fun ui(snapshot: ChatFeedSnapshot) =
        ChatsState.build(snapshot, io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief.None, ChatsFilters(), 0, true, 10_000)

    @Test
    fun `B-HOME-CACHE-001 connecting draws the connected home from saved rows, same rows in the same places`() {
        val opening = ChatsOpeningUi(ChatsOpeningPhase.Connecting, "This phone", saved.toImmutableList(), profile = P1)
        val connecting = ui(HomeCachedRows.merge(ChatFeedSnapshot(loading = true), opening))
        val connected = ui(ChatFeedSnapshot(items = saved.toImmutableList()))
        assertEquals(ChatsPhase.List, connecting.phase)
        assertEquals(connected.rightNow.map { it.key }, connecting.rightNow.map { it.key })
        assertEquals(connected.recent.map { it.key }, connecting.recent.map { it.key })
        assertEquals(connected.allCount, connecting.allCount)
    }

    @Test
    fun `B-HOME-CACHE-002 a saved row shows its agent mark, preview and time`() {
        val opening = ChatsOpeningUi(ChatsOpeningPhase.Connecting, "This phone", saved.toImmutableList(), profile = P1)
        val row = ui(HomeCachedRows.merge(ChatFeedSnapshot(loading = true), opening)).recent.first { it.ref.sessionId.raw == "a" }
        assertEquals("claude", row.agentMark)
        assertEquals("I fixed the form", row.preview)
        assertNotNull(row.ago)
    }

    @Test
    fun `B-HOME-CACHE-003 once the live feed holds the server's rows the saved ones step aside`() {
        val opening = ChatsOpeningUi(ChatsOpeningPhase.Connecting, "This phone", saved.toImmutableList(), profile = P1)
        val live = ChatFeedSnapshot(items = persistentListOf(item("a", at = 5_000, title = "Fix login")))
        assertEquals(live, HomeCachedRows.merge(live, opening))
    }
}
