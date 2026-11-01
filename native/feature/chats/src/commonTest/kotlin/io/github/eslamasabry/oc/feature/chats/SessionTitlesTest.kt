package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.feature.chats.presentation.RowTitle
import io.github.eslamasabry.oc.feature.chats.presentation.SessionTitles
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionTitlesTest {
    private val leaked = "I'll run the startup sequence to check for existing work and prime the merge queue.<tool_call><fu…"
    private val clean = "I'll run the startup sequence to check for existing work and prime the merge queue."

    @Test
    fun `B-NAV-066 the server placeholder loses its stamp, real titles pass, blanks fall back`() {
        assertEquals(RowTitle.New, SessionTitles.present("New session - 2026-09-02T14:47:06.902Z"))
        assertEquals(RowTitle.New, SessionTitles.present("New session - 2026-09-02T14:47:06Z"))
        assertEquals(RowTitle.Text("Fix the login bug"), SessionTitles.present("Fix the login bug"))
        assertEquals(
            RowTitle.Text("New session - notes from 2026-09-02"),
            SessionTitles.present("New session - notes from 2026-09-02"),
        )
        assertEquals(RowTitle.Text("Fix login"), SessionTitles.present("Fix login 2026-09-28T10:00:00.000Z"))
        assertEquals(RowTitle.Untitled, SessionTitles.present(null))
        assertEquals(RowTitle.Untitled, SessionTitles.present("  "))
    }

    @Test
    fun `B-NAV-067 leaked model markup is cut from titles and ordinary titles stay`() {
        assertEquals(clean, SessionTitles.text(leaked))
        assertEquals("Fix login", SessionTitles.text("Fix login <function=bash>{\"cmd\""))
        assertEquals("Fix login", SessionTitles.text("Fix login<function_calls>"))
        assertEquals("Plan the release", SessionTitles.text("Plan the release <|im_start|>assistant"))
        assertEquals("Check tests", SessionTitles.text("Check tests</tool_call>"))
        assertEquals("Check tests", SessionTitles.text("Check tests <TOOL_CALL>{"))
        assertEquals("Review", SessionTitles.text("Review <tool_use>x"))
        assertEquals("prime the merge queue.", SessionTitles.text("prime the merge queue.<tool_ca…"))
        assertEquals("prime the merge queue.", SessionTitles.text("prime the merge queue. <fu..."))
        assertEquals("prime the merge queue.", SessionTitles.text("prime the merge queue.<…"))
        assertEquals("Refactor the parser", SessionTitles.text("Refactor the parser …<tool_call>"))
        listOf(
            "Refinery merge queue patrol", "Polecat startup: claim work and execute", "Compare a < b and c > d",
            "Explain the <details> element", "Use <f", "Summarise the long discussion…", "Why does 1 < 2…",
        ).forEach { assertEquals(it, SessionTitles.text(it), it) }
        assertEquals("Padded", SessionTitles.text("  Padded  "))
        assertEquals("", SessionTitles.text("<tool_call><function=read>"))
        assertEquals("", SessionTitles.text(null))
        // Every list uses the cleaned title; markup-only falls back to the untitled wording.
        assertEquals(RowTitle.Text(clean), SessionTitles.present(leaked))
        assertEquals(RowTitle.Untitled, SessionTitles.present("<tool_call><fu…"))
    }
}
