package io.github.eslamasabry.oc.android.tiles

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The pure publish policy (the launcher calls themselves are thin ports of the Flutter object). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PinnedShortcutsTest {

    @Test
    fun `B-ANDR-030 pinned sessions are capped at four in pin order`() {
        val pins = (1..6).map { PinnedSession("s$it", "Title $it") }
        val out = PinnedShortcuts.normalize(pins)
        assertEquals(listOf("s1", "s2", "s3", "s4"), out.map { it.sessionId })
    }

    @Test
    fun `B-ANDR-030 blank ids and duplicates are dropped and long titles are cut`() {
        val long = "x".repeat(200)
        val out = PinnedShortcuts.normalize(
            listOf(
                PinnedSession(" ", "blank"),
                PinnedSession("a", "  $long  "),
                PinnedSession("a", "dup"),
                PinnedSession("b", "   "),
            )
        )
        assertEquals(listOf("a", "b"), out.map { it.sessionId })
        assertEquals(PinnedShortcuts.MAX_TITLE_LENGTH, out[0].title.length)
        assertEquals("", out[1].title)
    }

    @Test
    fun `B-ANDR-030 only ids and titles leave the app`() {
        val payload = PinnedShortcuts.payload("p1", listOf(PinnedSession("s1", "T")))
        val json = JSONObject(payload)
        assertEquals(setOf("profileID", "sessions"), json.keys().asSequence().toSet())
        val entry = json.getJSONArray("sessions").getJSONObject(0)
        assertEquals(setOf("id", "title"), entry.keys().asSequence().toSet())
    }

    @Test
    fun `B-ANDR-053 an unchanged payload is not republished`() {
        val pins = PinnedShortcuts.normalize(listOf(PinnedSession("s1", "T")))
        val first = PinnedShortcuts.payload("p1", pins)
        assertTrue(PinnedShortcuts.shouldPublish(null, first))
        assertFalse(PinnedShortcuts.shouldPublish(first, PinnedShortcuts.payload("p1", pins)))
        val renamed = PinnedShortcuts.payload("p1", PinnedShortcuts.normalize(listOf(PinnedSession("s1", "U"))))
        assertTrue(PinnedShortcuts.shouldPublish(first, renamed))
    }

    @Test
    fun `B-ANDR-031 shortcut ids keep the Flutter scheme and stale ones are withdrawn before publishing`() {
        assertEquals("oc.session:p1:s1", PinnedShortcuts.shortcutId("p1", "s1"))
        val existing = listOf("oc.session:p1:s1", "oc.session:p1:s2", "oc.session:p2:s7", "connect")
        assertEquals(
            listOf("oc.session:p1:s2", "oc.session:p2:s7"),
            PinnedShortcuts.staleIds(existing, keep = setOf("oc.session:p1:s1"), profileId = null),
        )
    }

    @Test
    fun `B-ANDR-054 deleting a profile withdraws only its shortcuts`() {
        val existing = listOf("oc.session:p1:s1", "oc.session:p10:s1", "oc.session:p2:s7", "new_task")
        assertEquals(
            listOf("oc.session:p1:s1"),
            PinnedShortcuts.staleIds(existing, keep = emptySet(), profileId = "p1"),
        )
        assertEquals(emptyList<String>(), PinnedShortcuts.staleIds(listOf("new_task"), emptySet(), "p1"))
        assertEquals("oc.pinnedShortcuts.p1", PinnedShortcuts.recordKey("p1"))
    }
}
