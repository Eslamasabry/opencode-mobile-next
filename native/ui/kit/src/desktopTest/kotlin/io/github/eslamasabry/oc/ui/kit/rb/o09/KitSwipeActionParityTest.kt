package io.github.eslamasabry.oc.ui.kit.rb.o09

import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuGroup
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoOffer
import io.github.eslamasabry.oc.ui.kit.row.KitSwipeAction
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every rule of lib/ui/kit/kit_swipe_action.dart: one path from every door, undo only for an act that happened. */
class KitSwipeActionParityTest {
    private fun action(act: suspend () -> Boolean, commits: MutableList<String> = mutableListOf(), deferred: Boolean = false) =
        KitSwipeAction(
            id = "s1",
            label = "Archive",
            icon = KitIcons.archive,
            onAct = act,
            undoMessage = "Archived 'Fix login'",
            onUndo = {},
            onCommit = if (deferred) ({ commits += "commit" }) else null,
            group = KitMenuGroup("row"),
        )

    @Test fun `B-KIT-SWIPE-001 a done act offers the one undo line naming the thing`() = runTest {
        val shown = mutableListOf<KitUndoOffer>()
        assertTrue(action({ true }).run { shown += it })
        assertEquals(listOf(KitUndoOffer("kit-swipe-s1", "Archived 'Fix login'")), shown)
    }

    @Test fun `B-KIT-SWIPE-002 a failed act shows no undo line`() = runTest {
        var shown: KitUndoOffer? = null
        assertFalse(action({ false }).run { shown = it })
        assertNull(shown)
    }

    @Test fun `B-KIT-SWIPE-003 a throwing act counts as failed`() = runTest {
        var shown: KitUndoOffer? = null
        assertFalse(action({ error("boom") }).run { shown = it })
        assertNull(shown)
    }

    @Test fun `B-KIT-SWIPE-004 with no host left a deferred act commits at once`() = runTest {
        val commits = mutableListOf<String>()
        assertTrue(action({ true }, commits, deferred = true).run(null))
        assertEquals(listOf("commit"), commits)
    }

    @Test fun `B-KIT-SWIPE-005 a shown undo line leaves the deferred commit to the undo window`() = runTest {
        val commits = mutableListOf<String>()
        assertTrue(action({ true }, commits, deferred = true).run {})
        assertTrue(commits.isEmpty())
    }

    @Test fun `B-KIT-SWIPE-006 the menu twin keeps the label icon and group`() {
        var ran = 0
        val a = action({ true })
        val item = a.menuItem { ran++ }
        assertEquals("Archive", item.label)
        assertEquals(KitIcons.archive, item.icon)
        assertEquals(KitMenuGroup("row"), item.group)
        assertFalse(item.destructive) // never a destructive tint (kit_swipe_action.dart: "no destructive tint")
        item.onSelect()
        assertEquals(1, ran)
    }
}
