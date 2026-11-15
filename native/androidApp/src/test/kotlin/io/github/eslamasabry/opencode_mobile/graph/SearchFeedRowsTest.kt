package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.feature.search.presentation.SearchConversation
import io.github.eslamasabry.oc.feature.search.presentation.SearchConversationPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** B-NAV-SEARCH-147: a search finds every conversation the home lists for the connection. */
class SearchFeedRowsTest {
    private val phone = ProfileId("phone")

    private fun item(id: String, title: String?, profile: ProfileId = phone, parent: String? = null) =
        ChatFeedItem(
            sourceId = "s",
            profileId = profile,
            sessionId = SessionId(id),
            title = title,
            directory = "/work/oc-test",
            projectName = "oc-test",
            status = ChatStatus.Idle,
            lastActivityMs = 10,
            parentId = parent?.let(::SessionId),
        )

    private fun page(vararg own: SearchConversation, matched: Boolean = false) =
        SearchConversationPage(own.toList().toImmutableList(), null, titlesMatched = matched, allProjects = false)

    @Test
    fun `B-NAV-SEARCH-147 a conversation the home lists but the gateway page lacks is searchable`() {
        val out =
            withFeedRows(page(), listOf(item("a", "Reply with OK"), item("b", "Run uname -a")), phone, "uname")
        assertEquals(listOf("a", "b"), out.items.map { it.id.raw }.sorted())
    }

    @Test
    fun `B-NAV-SEARCH-147 rows are not repeated, other connections and sub-agents stay out`() {
        val have = SearchConversation(SessionId("a"), "Reply with OK", "/work/oc-test", 5)
        val out =
            withFeedRows(
                page(have),
                listOf(item("a", "Reply with OK"), item("x", "other", ProfileId("far")), item("k", "kid", parent = "a")),
                phone,
                "",
            )
        assertEquals(listOf("a"), out.items.map { it.id.raw })
    }

    @Test
    fun `B-NAV-SEARCH-147 when the page is already title-filtered only matching feed rows join`() {
        val out =
            withFeedRows(page(matched = true), listOf(item("a", "Reply with OK"), item("b", "Run uname -a")), phone, "uname")
        assertEquals(listOf("b"), out.items.map { it.id.raw })
        assertEquals(persistentListOf<SearchConversation>().size, 0)
    }
}
