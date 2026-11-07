package io.github.eslamasabry.oc.ui.kit.content.log

import androidx.compose.runtime.snapshots.Snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The rules of the buffer-backed KitLogPanel (lib/ui/kit/kit_log_panel.dart at 8302de537). */
class KitLogPanelTest {
    @Test
    fun `B-KIT-021 a folded panel is 12 lines and its padding, never more than 45 percent of the window`() {
        // Dart: math.min(KitTokens.logFoldedLines * extent + tokens.space2 * 2, window * .45), floored.
        assertEquals(256f, KitLogRules.foldedHeight(extent = 20f, window = 915f, space2 = 8f))
        assertEquals(180f, KitLogRules.foldedHeight(extent = 40f, window = 400f, space2 = 8f))
    }

    @Test
    fun `B-KIT-021 the panel counts the buffer's dropped lines and those past its own cap`() {
        assertEquals(1240, KitLogRules.dropped(buffered = 1240, lines = 10, maxLines = 2000))
        assertEquals(5, KitLogRules.dropped(buffered = 0, lines = 2005, maxLines = 2000))
    }

    @Test
    fun `B-KIT-021 lines wrap on a phone and scroll sideways from medium, and the person's choice wins`() {
        assertTrue(KitLogRules.wraps(controlled = null, own = null, compact = true))
        assertFalse(KitLogRules.wraps(controlled = null, own = null, compact = false))
        assertTrue(KitLogRules.wraps(controlled = null, own = true, compact = false))
        assertFalse(KitLogRules.wraps(controlled = false, own = true, compact = true))
    }

    @Test
    fun `B-KIT-021 the jump pill shows only after the person left the newest line and new lines came`() {
        assertTrue(KitLogRules.showsJump(follow = true, following = false, unseen = 12))
        assertFalse(KitLogRules.showsJump(follow = true, following = true, unseen = 12))
        assertFalse(KitLogRules.showsJump(follow = true, following = false, unseen = 0))
        assertFalse(KitLogRules.showsJump(follow = false, following = false, unseen = 12))
    }

    @Test
    fun `B-KIT-021 a chunk is seen by a reader of the buffer's snapshot state`() {
        // Dart KitLogBuffer is a ValueNotifier; the native buffer publishes through snapshot state, so a panel
        // recomposes when output arrives.
        val buffer = KitLogBuffer()
        var seen = 0
        val handle = Snapshot.registerApplyObserver { changed, _ -> if (changed.isNotEmpty()) seen++ }
        try {
            Snapshot.observe(readObserver = {}) { buffer.lines }
            buffer.appendText("a\n")
            Snapshot.sendApplyNotifications()
            assertTrue(seen > 0, "appending a chunk changes the buffer's state")
            assertEquals(listOf("a"), buffer.lines.map { it.text })
        } finally {
            handle.dispose()
        }
    }
}
