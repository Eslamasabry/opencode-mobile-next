package io.github.eslamasabry.oc.feature.chat.composer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** S30 story 2: the "/" menu ranks the app's actions and the server's commands as Flutter's `scoreFor` does. */
class SlashMenuTest {
    private fun cmd(slash: String, title: String = slash, aliases: List<String> = emptyList(), server: Boolean = false) =
        ComposerSlashCommand(slash, slash, title, null, aliases, inserts = server, onRun = {})

    private val all =
        listOf(
            cmd("new", "New session", listOf("clear")),
            cmd("agents", "Agent", listOf("agent")),
            cmd("approvals", "Approvals"),
            cmd("connect", "Connect provider"),
            cmd("compact", "Shorten conversation", listOf("summarize")),
            cmd("init", "/init", server = true),
            cmd("review", "/review", server = true),
        )

    @Test
    fun `S30-CHAT-002 a bare slash lists the app's own actions with the server's commands`() {
        val shown = SlashMenu.rank(all, "").map { it.slash }
        assertEquals(all.size, shown.size)
        assertTrue("agents" in shown && "approvals" in shown && "connect" in shown && "init" in shown, "$shown")
        assertTrue(shown.size > SlashMenu.VISIBLE, "more than fit, so Show all is offered")
    }

    @Test
    fun `S30-CHAT-002 exact slash beats prefix beats alias beats title beats contains`() {
        val rows =
            listOf(
                cmd("rev-notes", "Notes"),
                cmd("x", "Review the diff"),
                cmd("y", "Other", listOf("review")),
                cmd("review"),
                cmd("pre-review"),
            )
        // Two slash prefixes tie and read by name, ahead of every alias, title or contains match.
        assertEquals(listOf("rev-notes", "review", "y", "x", "pre-review"), SlashMenu.rank(rows, "rev").map { it.slash })
        assertEquals(
            listOf("review", "y", "x", "pre-review"),
            SlashMenu.rank(rows, "/review").map { it.slash },
        )
    }

    @Test
    fun `S30-CHAT-002 a word matches a slash, an alias, a title or a description, nothing else`() {
        assertEquals(listOf("compact"), SlashMenu.rank(all, "summ").map { it.slash })
        assertEquals(listOf("connect"), SlashMenu.rank(all, "provider").map { it.slash })
        assertEquals(emptyList(), SlashMenu.rank(all, "zzz"))
    }
}
