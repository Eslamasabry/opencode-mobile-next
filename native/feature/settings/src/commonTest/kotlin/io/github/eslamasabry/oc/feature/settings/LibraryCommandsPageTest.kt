package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.core.model.CommandInfo
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryTab
import io.github.eslamasabry.oc.feature.settings.presentation.libraryInitialTab
import io.github.eslamasabry.oc.feature.settings.ui.libraryCommandWords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** P53, B-300: Commands and tools names its rows in words, and opens on its first tab, Commands. */
class LibraryCommandsPageTest {
    @Test
    fun `an argument template leaves the title and goes beside the slash word`() {
        val words =
            libraryCommandWords(
                CommandInfo("review", "review changes [commit|branch|pr], defaults to uncommitted", null, false)
            )
        assertEquals("review changes, defaults to uncommitted", words.title)
        assertEquals("/review", words.slash)
        assertEquals("[commit|branch|pr]", words.hint)
    }

    @Test
    fun `B-300 a bracket in the middle of a sentence stays in the title with the words after it`() {
        val words =
            libraryCommandWords(CommandInfo("fix", "Fix the failing [test] and explain why it broke", null, false))
        assertEquals("Fix the failing [test] and explain why it broke", words.title)
        assertNull(words.hint)
    }

    @Test
    fun `B-300 several template groups at the end all go beside the slash word`() {
        val words = libraryCommandWords(CommandInfo("pr", "open a pull request [base] [title]", null, false))
        assertEquals("open a pull request", words.title)
        assertEquals("[base] [title]", words.hint)
    }

    @Test
    fun `B-300 a Markdown link in a description is words, not a template`() {
        val words =
            libraryCommandWords(CommandInfo("docs", "Open the [guide](https://example.com) for setup", null, false))
        assertEquals("Open the [guide](https://example.com) for setup", words.title)
        assertNull(words.hint)
    }

    @Test
    fun `a plain description stays the whole title`() {
        val words = libraryCommandWords(CommandInfo("init", "guided AGENTS.md setup", null, false))
        assertEquals("guided AGENTS.md setup", words.title)
        assertNull(words.hint)
    }

    @Test
    fun `a description that is only a template or is missing falls back to whole text or the slash word`() {
        assertEquals("[a|b]", libraryCommandWords(CommandInfo("x", "[a|b]", null, false)).title)
        assertEquals("/x", libraryCommandWords(CommandInfo("x", null, null, false)).title)
    }

    @Test
    fun `B-300 no tab named opens the page's first tab, Commands`() {
        assertEquals(LibraryTab.Commands, libraryInitialTab(null))
        assertEquals(LibraryTab.Commands, libraryInitialTab("unknown"))
        assertEquals(LibraryTab.Mcp, libraryInitialTab("mcp"))
    }
}
