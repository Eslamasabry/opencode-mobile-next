package io.github.eslamasabry.oc.ui.kit.dock

import kotlin.test.Test
import kotlin.test.assertEquals

/** English test words (the host supplies real ones from its string resources). */
internal object TestDockWords : KitDockWords {
    override val dockLabel: String = "Running conversations"
    override fun working(): String = "Working"
    override fun workingTasks(done: Int, total: Int): String = "Working · $done of $total"
    override fun thinking(): String = "Thinking"
    override fun needsYou(): String = "Needs you"
    override fun finished(): String = "Done"
    override fun failed(): String = "Failed"
    override fun idle(): String = "Paused"
    override fun childNeedsYou(child: String): String = "$child needs you"
    override fun childFailed(child: String): String = "$child failed"
    override fun subAgents(count: Int): String = if (count == 1) "1 sub-agent" else "$count sub-agents"
    override fun onServer(server: String): String = "on $server"
    override val allow: String = "Allow"
    override val deny: String = "Deny"
    override val stop: String = "Stop"
    override val rename: String = "Rename"
    override val remove: String = "Remove"
    override val open: String = "Open"
    override val pin: String = "Pin"
    override val unpin: String = "Unpin"
    override val restore: String = "Show in dock"
    override val newConversation: String = "New conversation"
    override val nickname: String = "Nickname"
    override val pinnedLabel: String = "Pinned"
    override fun hidden(count: Int): String = "$count hidden"
    override val moveDock: String = "Move the dock"
    override val showDock: String = "Show the dock"
    override fun actionOn(action: String, target: String): String = "$action $target"
}

class DockWordsTest {
    private val bank =
        KitDockRing(
            key = "bank", name = "Bank API", markId = "claude", state = KitDockRingState.NeedsYou, causedBy = "explore",
            children = listOf(
                KitDockChild("explore", "explore", KitDockRingState.NeedsYou),
                KitDockChild("tests", "tests", KitDockRingState.WorkingWithTasks(2, 5)),
                KitDockChild("docs", "docs", KitDockRingState.Finished),
            ),
        )

    @Test
    fun `a sub-agent's alert speaks for its conversation`() {
        assertEquals("explore needs you · 3 sub-agents", TestDockWords.stateLine(bank))
        assertEquals("Bank API, Needs you, 3 sub-agents, explore needs you", TestDockWords.ringLabel(bank))
    }

    @Test
    fun `the server is named only when the host gives one`() {
        val r = KitDockRing("p", "Pong", null, KitDockRingState.WorkingWithTasks(3, 7), server = "Mac mini")
        assertEquals("Working · 3 of 7 · on Mac mini", TestDockWords.stateLine(r))
        assertEquals("Working · 3 of 7", TestDockWords.stateLine(r.copy(server = null)))
    }

    @Test
    fun `sub-agents past the model's cap are still counted`() {
        val r = bank.copy(childOverflow = 2, causedBy = null, state = KitDockRingState.Working)
        assertEquals(5, r.childTotal)
        assertEquals("Working · 5 sub-agents", TestDockWords.stateLine(r))
    }

    @Test
    fun `actions name their target`() {
        assertEquals("Allow", TestDockWords.targetWord(DockTargetKind.Allow))
        assertEquals("", TestDockWords.targetWord(DockTargetKind.Header))
    }
}
