package io.github.eslamasabry.oc.feature.search

import io.github.eslamasabry.oc.feature.search.presentation.SearchMatch
import io.github.eslamasabry.oc.feature.search.presentation.SearchMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchMatchTest {
    @Test
    fun `B-NAV-049 conversations match by title case-insensitively and untitled ones never match a query`() {
        assertTrue(SearchMatch.titleMatches("Fix the Login bug", "login"))
        assertTrue(SearchMatch.titleMatches("Fix the Login bug", "  the   LOGIN "))
        assertFalse(SearchMatch.titleMatches("Fix the Login bug", "logout"))
        assertFalse(SearchMatch.titleMatches(null, "login"))
        assertTrue(SearchMatch.titleMatches(null, "  "))
    }

    @Test
    fun `SCREENS-NAV-34 a content match becomes a one-line excerpt cut with ellipses on the cut sides only`() {
        val long = "a".repeat(80) + " the deploy\nscript failed " + "b".repeat(80)
        val excerpt = SearchMatch.snippet(long, "deploy script")!!
        assertTrue(excerpt.startsWith("…"))
        assertTrue(excerpt.endsWith("…"))
        assertTrue(excerpt.contains("deploy script"), excerpt)
        assertFalse(excerpt.contains("\n"))
        assertEquals("the deploy script failed", SearchMatch.snippet("the deploy script failed", "DEPLOY"))
        assertNull(SearchMatch.snippet("nothing here", "deploy"))
        assertNull(SearchMatch.snippet("anything", "   "))
    }

    @Test
    fun `SCREENS-NAV-34 the newest matching message wins`() {
        val messages =
            listOf(
                SearchMessage("m1", fromYou = true, text = "please deploy it"),
                SearchMessage("m2", fromYou = false, text = "unrelated"),
                SearchMessage("m3", fromYou = false, text = "Deploy finished"),
            )
        val (message, excerpt) = SearchMatch.newestMatch(messages, "deploy")!!
        assertEquals("m3", message.id)
        assertEquals("Deploy finished", excerpt)
        assertNull(SearchMatch.newestMatch(messages, "rollback"))
    }

    @Test
    fun `SCREENS-NAV-34 recent searches put the newest first, drop duplicates ignoring case and keep eight`() {
        val start = (1..8).map { "q$it" }
        val after = SearchMatch.remember(start, " Q3 ")
        assertEquals("Q3", after.first())
        assertEquals(8, after.size)
        assertEquals(1, after.count { it.equals("q3", ignoreCase = true) })
        assertEquals(start, SearchMatch.remember(start, "   "))
        assertEquals(listOf("new") + start.take(7), SearchMatch.remember(start, "new"))
    }

    @Test
    fun `SCREENS-NAV-34 a row names the project by its last folder`() {
        assertEquals("app", SearchMatch.projectName("/home/me/work/app/"))
        assertEquals("proj", SearchMatch.projectName("C:\\code\\proj"))
        assertNull(SearchMatch.projectName(null))
        assertNull(SearchMatch.projectName("/"))
    }
}
