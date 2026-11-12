package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsFilters
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.toImmutableList

/** Flutter's list (connection/sessions.dart sortedSessions): a pinned conversation comes first, then the newest. */
class HomePinnedFirstTest {
    private val feed =
        ChatFeedSnapshot(
            listOf(
                item("new", dir = "/work/api", at = 9_000),
                item("mid", dir = "/work/api", at = 5_000),
                item("old", dir = "/work/web", at = 1_000),
            ).toImmutableList()
        )

    private fun order(pinned: Set<String>, filters: ChatsFilters = ChatsFilters()) =
        ChatsState.build(feed, ReturnBrief.None, filters, 10_000, settingsAvailable = true, pinned = pinned)
            .recent.map { it.key.substringAfterLast('/') }

    private fun key(id: String) = feed.items.first { it.sessionId.raw == id }.identity

    @Test
    fun `a pinned row moves to the top of Recent, unpinning restores the order`() {
        assertEquals(listOf(key("old"), key("new"), key("mid")).map { it.substringAfterLast('/') }, order(setOf(key("old"))))
        assertEquals(listOf(key("new"), key("mid"), key("old")).map { it.substringAfterLast('/') }, order(emptySet()))
    }

    @Test
    fun `pinned first holds inside a project filter`() {
        val onlyApi = ChatsFilters(projectDirectory = "/work/api")
        assertEquals(listOf(key("mid"), key("new")).map { it.substringAfterLast('/') }, order(setOf(key("mid")), onlyApi))
    }
}
