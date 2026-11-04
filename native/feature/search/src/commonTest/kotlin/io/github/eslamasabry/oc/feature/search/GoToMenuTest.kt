package io.github.eslamasabry.oc.feature.search

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.feature.search.presentation.GoToInput
import io.github.eslamasabry.oc.feature.search.presentation.GoToMenu
import io.github.eslamasabry.oc.feature.search.presentation.GoToPlace
import io.github.eslamasabry.oc.feature.search.presentation.GoToRow
import io.github.eslamasabry.oc.feature.search.presentation.GoToSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Dart `search_go_to_test`. */
class GoToMenuTest {
    private fun ref(id: String) = ConversationRef(ProfileId("p1"), SessionId(id))

    private val titles = listOf("Fix the login bug", "Add dark mode", "Explain the build", "Old one")

    private fun input(caps: Boolean = true, saved: Boolean = true) =
        GoToInput(
            fileBrowsing = caps, terminal = caps, projectManagement = caps, hasSavedServer = saved,
            waiting = 0, project = "shopfront", recent = titles.mapIndexed { i, t -> ref("s$i") to t },
        )

    /** The English words the screen gives each row. */
    private fun words(row: GoToRow): List<String> =
        when (row.place) {
            GoToPlace.NeedsYou -> listOf("Needs you", "Inbox")
            GoToPlace.Files -> listOfNotNull("Files", row.project)
            GoToPlace.Projects -> listOf("Projects")
            GoToPlace.Settings -> listOf("Settings")
            GoToPlace.Servers -> listOf("Servers")
            GoToPlace.AddServer -> listOf("Add a server")
            GoToPlace.Terminal -> listOf("Terminal")
            GoToPlace.Help -> listOf("Help")
            GoToPlace.NewConversation -> listOf("New conversation")
            null -> listOfNotNull(row.title)
        }

    private fun labels(rows: List<GoToRow>, section: GoToSection) = rows.filter { it.section == section }.map { words(it).first() }

    @Test
    fun `B-NAV-056 empty - Go to places first, then the last three conversations`() {
        val shown = GoToMenu.match(GoToMenu.rows(input()), "", ::words)
        assertEquals(listOf("Needs you", "Files", "Projects", "Settings", "Servers", "Terminal", "Help"), labels(shown, GoToSection.GoTo))
        assertEquals(GoToPlace.NeedsYou, shown.first().place)
        assertEquals(0, shown.first().waiting) // "Nothing waits"
        assertEquals("shopfront", shown.first { it.place == GoToPlace.Files }.project)
        assertEquals(listOf("Fix the login bug", "Add dark mode", "Explain the build"), labels(shown, GoToSection.Recent))
        assertTrue(shown.none { it.place == GoToPlace.NewConversation }, "not a place: found once typed")
    }

    @Test
    fun `B-NAV-056 rows follow what the connection supports, never its flavour`() {
        val shown = GoToMenu.match(GoToMenu.rows(input(caps = false, saved = false)), "", ::words)
        assertEquals(listOf("Needs you", "Settings", "Add a server", "Help"), labels(shown, GoToSection.GoTo))
    }

    @Test
    fun `B-NAV-056 typing filters the same rows`() {
        val rows = GoToMenu.rows(input())
        fun found(q: String) = GoToMenu.match(rows, q, ::words).map { words(it).first() }
        assertTrue("Terminal" in found("term"))
        assertTrue("Settings" in found("sett"))
        assertTrue("Needs you" in found("inbox"))
        assertTrue("Files" in found("shopfront"))
        assertTrue("New conversation" in found("new conv"))
        assertTrue(GoToMenu.match(rows, "login", ::words).none { it.section == GoToSection.Recent })
    }
}
