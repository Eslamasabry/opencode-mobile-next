package io.github.eslamasabry.oc.core.common.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationTitleTest {
    @Test
    fun `a short first line is the title as it is`() {
        assertEquals("say hi", ConversationTitle.from("say hi\nmore"))
        assertNull(ConversationTitle.from("  \n "))
    }

    @Test
    fun `a long first line ends in an ellipsis at a word boundary, never inside a word`() {
        val text = "Explain this folder in detail: a heading, a numbered list of five items, a bulleted list and a table"
        val title = ConversationTitle.from(text)!!
        assertTrue(title.endsWith("…"), title)
        assertTrue(title.length <= ConversationTitle.MAX, title)
        assertEquals("Explain this folder in detail: a heading, a numbered list of five items, a…", title)
        val last = title.removeSuffix("…").substringAfterLast(' ')
        assertTrue(text.split(' ').contains(last), "$last is a whole word of the message")
    }

    @Test
    fun `one word wider than the title is cut at the limit with the ellipsis`() {
        val title = ConversationTitle.from("x".repeat(200))!!
        assertEquals(ConversationTitle.MAX, title.length)
        assertTrue(title.endsWith("…"))
    }

    @Test
    fun `B-LAMP-FIX-001 shown - nothing readable is untitled, the dated placeholder is new, and display is never empty`() {
        for (raw in listOf(null, "", "  ", "<tool_call>{", "<function=bash>")) {
            assertEquals(ConversationTitle.Shown.Untitled, ConversationTitle.shown(raw), "raw $raw")
            assertEquals("U", ConversationTitle.display(raw, "U", "N"))
        }
        assertEquals(ConversationTitle.Shown.New, ConversationTitle.shown("New session - 2026-11-14T10:00:00.000Z"))
        assertEquals(ConversationTitle.Shown.Text("Fix login"), ConversationTitle.shown(" Fix login <tool_use>x"))
        assertEquals("Fix login", ConversationTitle.display("Fix login", "U", "N"))
    }
}
