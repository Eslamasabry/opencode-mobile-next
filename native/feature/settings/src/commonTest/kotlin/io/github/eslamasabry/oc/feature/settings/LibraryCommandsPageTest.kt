package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.core.model.CommandInfo
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryTab
import io.github.eslamasabry.oc.feature.settings.presentation.libraryInitialTab
import io.github.eslamasabry.oc.feature.settings.ui.libraryCommandWords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** P53: Commands and tools names its rows in words, and opens on its first tab as Flutter's Tools page does. */
class LibraryCommandsPageTest {
    @Test
    fun `an argument template leaves the title and goes beside the slash word`() {
        val words =
            libraryCommandWords(
                CommandInfo("review", "review changes [commit|branch|pr], defaults to uncommitted", null, false)
            )
        assertEquals("review changes", words.title)
        assertEquals("/review", words.slash)
        assertEquals("[commit|branch|pr], defaults to uncommitted", words.hint)
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
    fun `no tab named opens the first tab, MCP servers`() {
        assertEquals(LibraryTab.Mcp, libraryInitialTab(null))
        assertEquals(LibraryTab.Mcp, libraryInitialTab("unknown"))
        assertEquals(LibraryTab.Commands, libraryInitialTab("commands"))
    }
}
