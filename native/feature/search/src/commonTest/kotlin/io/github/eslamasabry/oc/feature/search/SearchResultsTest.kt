package io.github.eslamasabry.oc.feature.search

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.feature.search.presentation.ContentPhase
import io.github.eslamasabry.oc.feature.search.presentation.GoToPlace
import io.github.eslamasabry.oc.feature.search.presentation.GoToRow
import io.github.eslamasabry.oc.feature.search.presentation.GoToSection
import io.github.eslamasabry.oc.feature.search.presentation.SearchActivity
import io.github.eslamasabry.oc.feature.search.presentation.SearchHitUi
import io.github.eslamasabry.oc.feature.search.presentation.SearchProject
import io.github.eslamasabry.oc.feature.search.presentation.SearchRecentConversation
import io.github.eslamasabry.oc.feature.search.presentation.SearchResults
import io.github.eslamasabry.oc.feature.search.presentation.SearchRowStatus
import io.github.eslamasabry.oc.feature.search.presentation.SearchUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.collections.immutable.persistentListOf

/** Search B1's grouping rules, apart from the drawing. */
class SearchResultsTest {
    private val home = ProfileId("home")

    private fun ref(id: String) = ConversationRef(home, SessionId(id))

    @Test
    fun `SCREENS-NAV-34 B1 one row per conversation, titles first, the message only with Inside messages on`() {
        val titles = persistentListOf(SearchHitUi(ref("a"), "Debug build", "app"), SearchHitUi(ref("b"), "Deb", "app"))
        val inside =
            persistentListOf(
                SearchHitUi(ref("b"), "Deb", "app", snippet = "install the Debian", messageId = "m1"),
                SearchHitUi(ref("c"), "Hi", "box", snippet = "debug it", snippetFromYou = true, messageId = "m2"),
            )
        val working = SearchRecentConversation(ref("a"), "Debug build", "app", 5L, "claude", status = SearchRowStatus.Working)
        val live = SearchActivity(persistentListOf(working))
        val on =
            SearchUi(query = "deb", titleHits = titles, contentHits = inside, content = ContentPhase.Done, activity = live)
        val rows = SearchResults.conversations(on)
        assertEquals(listOf("a", "b", "c"), rows.map { it.hit.ref.sessionId.raw })
        assertEquals("install the Debian", rows[1].hit.snippet)
        assertEquals("claude", rows[0].agentMark)
        assertEquals(SearchRowStatus.Working, rows[0].status)
        assertEquals(5L, rows[0].updatedMs)
        val off = SearchResults.conversations(on.copy(content = ContentPhase.Off))
        assertEquals(listOf("a", "b"), off.map { it.hit.ref.sessionId.raw })
        assertNull(off[1].hit.snippet)
    }

    @Test
    fun `SCREENS-NAV-34 B1 projects match every typed word in their name, at most three`() {
        val all = listOf(
            SearchProject("debian-box", "/a", 3),
            SearchProject("web app", "/b", 1),
            SearchProject("Debian tools", "/c", 2),
            SearchProject("deb-extra", "/d", 1),
            SearchProject("debx", "/e", 1),
        )
        assertEquals(listOf("/a", "/c", "/d"), SearchResults.projects(all, "deb").map { it.directory })
        assertEquals(listOf("/c"), SearchResults.projects(all, "tools  DEB").map { it.directory })
        assertEquals(emptyList(), SearchResults.projects(all, "  "))
    }

    @Test
    fun `SCREENS-NAV-34 B1 the place shortcuts are Needs you, Projects where managed, Settings and Servers`() {
        fun rows(vararg places: GoToPlace) = places.map { GoToRow(GoToSection.GoTo, it) }
        val early = listOf(GoToPlace.NeedsYou, GoToPlace.Settings, GoToPlace.Servers)
        assertEquals(early, SearchResults.shortcuts(emptyList()))
        val all = listOf(GoToPlace.NeedsYou, GoToPlace.Projects, GoToPlace.Settings, GoToPlace.Servers)
        assertEquals(all, SearchResults.shortcuts(rows(*all.toTypedArray(), GoToPlace.Terminal)))
        val noServer = rows(GoToPlace.NeedsYou, GoToPlace.AddServer)
        assertEquals(GoToPlace.AddServer, SearchResults.shortcuts(noServer).last())
    }

    @Test
    fun `SCREENS-NAV-34 B1 recent conversations come from the live list, else from the Go to menu`() {
        val goTo = persistentListOf(GoToRow(GoToSection.Recent, ref = ref("g"), title = "From menu"))
        assertEquals(listOf("g"), SearchResults.recent(SearchUi(goTo = goTo)).map { it.ref.sessionId.raw })
        val live = SearchActivity(persistentListOf(SearchRecentConversation(ref("l"), "Live", null)))
        val both = SearchUi(goTo = goTo, activity = live)
        assertEquals(listOf("l"), SearchResults.recent(both).map { it.ref.sessionId.raw })
    }
}
