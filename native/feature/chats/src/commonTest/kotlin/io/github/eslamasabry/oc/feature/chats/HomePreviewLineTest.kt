package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.feature.chats.presentation.HomePreviewRules
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A row's preview says things once and in words (review 2026-11-13, set-C C-004 / C-080): the status column says Done,
 * so the line does not start with it again; a path inside the line is project-relative and shortened from its start so
 * the file name survives; marks never show raw.
 */
class HomePreviewLineTest {
    private val dir = "/root/projects/oc_app"

    private fun shown(line: String, echoes: List<String> = listOf("Done"), directory: String? = dir) =
        HomePreviewRules.forRow(line, directory, echoes)

    @Test
    fun `RC1-HOME-008 a numbered list loses its numbers like a bullet loses its mark`() {
        assertEquals(
            "The River Called Rive It began as a rill",
            shown("# The River Called Rive\n\n1. It began as a rill", echoes = emptyList()),
        )
        assertEquals("first second", HomePreviewRules.oneLine("1) first\n2) second"))
        assertEquals("Version 2.1 is out", shown("Version 2.1 is out", echoes = emptyList()), "a number inside a line stays")
    }

    @Test
    fun `a leading Done is dropped when the status column already says Done`() {
        assertEquals("Created notes.txt with one line", shown("Done. Created notes.txt with one line"))
        assertEquals("Created t.py", shown("Done — created t.py"))
    }

    @Test
    fun `a leading Done stays when the column does not say it`() {
        assertEquals("Done. Created notes.txt", shown("Done. Created notes.txt", echoes = emptyList()))
    }

    @Test
    fun `a line that is only Done stays as it is`() {
        assertEquals("Done.", shown("Done."))
        assertEquals("Done", shown("Done"))
    }

    @Test
    fun `Done as the start of a sentence is not an echo`() {
        assertEquals("Done with the build, all green", shown("Done with the build, all green"))
    }

    @Test
    fun `a path inside the project is shown project-relative`() {
        assertEquals("Created notes/e2.txt", shown("Created /root/projects/oc_app/notes/e2.txt"))
    }

    @Test
    fun `a long path is cut from its start so the file name survives`() {
        val line = shown("Wrote /srv/data/deeply/nested/folder/structure/e2.txt", directory = null)
        assertEquals("Wrote …/structure/e2.txt", line)
        assertEquals("Saved …/hero-banner-final-version.png", shown("Saved /srv/a/b/hero-banner-final-version.png", directory = null))
    }

    @Test
    fun `a short path and a web address are left alone`() {
        assertEquals("Opened /etc/hosts", shown("Opened /etc/hosts", directory = null))
        assertEquals("See https://example.com/a/b/c/d/e/f/g/h", shown("See https://example.com/a/b/c/d/e/f/g/h", directory = null))
    }

    @Test
    fun `marks never show raw`() {
        assertEquals("Created a.txt", shown("**Done.** Created `a.txt`"))
    }
}
