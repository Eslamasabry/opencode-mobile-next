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
    fun `SCREENS-NAV-34 B1 one row per conversation, titles first, and the matching message only with Inside messages on`() {
        val titles = persistentListOf(SearchHitUi(ref("a"), "Debug build", "app"), SearchHitUi(ref("b"), "Deb notes", "app"))
        val inside =
            persistentListOf(
                SearchHitUi(ref("b"), "Deb notes", "app", snippet = "install the Debian image", messageId = "m1"),
                SearchHitUi(ref("c"), "Hi", "box", snippet = "the debug build won't run", snippetFromYou = true, messageId = "m2"),
            )
        val live = SearchActivity(persistentListOf(SearchRecentConversation(ref("a"), "Debug build", "app", 5L, "claude", "Claude Code", SearchRowStatus.Working)))
        val on = SearchUi(query = "deb", titleHits = titles, contentHits = inside, content = ContentPhase.Done, activity = live)
        val rows = SearchResults.conversations(on)
        assertEquals(listOf("a", "b", "c"), rows.map { it.hit.ref.sessionId.raw })
        assertEquals("install the Debian image", rows[1].hit.snippet)
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
        assertEquals(listOf(GoToPlace.NeedsYou, GoToPlace.Settings, GoToPlace.Servers), SearchResults.shortcuts(emptyList()))
        val rows =
            listOf(GoToPlace.NeedsYou, GoToPlace.Projects, GoToPlace.Settings, GoToPlace.Servers, GoToPlace.Terminal)
                .map { GoToRow(GoToSection.GoTo, it) }
        assertEquals(listOf(GoToPlace.NeedsYou, GoToPlace.Projects, GoToPlace.Settings, GoToPlace.Servers), SearchResults.shortcuts(rows))
        val noServer = listOf(GoToRow(GoToSection.GoTo, GoToPlace.NeedsYou), GoToRow(GoToSection.GoTo, GoToPlace.AddServer))
        assertEquals(GoToPlace.AddServer, SearchResults.shortcuts(noServer).last())
    }

    @Test
    fun `SCREENS-NAV-34 B1 recent conversations come from the live list, else from the Go to menu`() {
        val goTo = persistentListOf(GoToRow(GoToSection.Recent, ref = ref("g"), title = "From menu"))
        assertEquals(listOf("g"), SearchResults.recent(SearchUi(goTo = goTo)).map { it.ref.sessionId.raw })
        val live = SearchActivity(persistentListOf(SearchRecentConversation(ref("l"), "Live", null)))
        assertEquals(listOf("l"), SearchResults.recent(SearchUi(goTo = goTo, activity = live)).map { it.ref.sessionId.raw })
    }
}
