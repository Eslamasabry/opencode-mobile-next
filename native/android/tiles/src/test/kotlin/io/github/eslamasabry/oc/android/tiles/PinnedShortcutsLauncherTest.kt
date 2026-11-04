package io.github.eslamasabry.oc.android.tiles

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import org.robolectric.RuntimeEnvironment
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * `test/pinned_session_shortcuts_test.dart`, case by case, against the real launcher calls (Robolectric's
 * ShortcutManager). "Published" is what the launcher holds; the record is the `oc.pinnedShortcuts.<profileId>` entry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PinnedShortcutsLauncherTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val prefs get() = TilesComponents.prefs(context)

    private fun dynamic(): List<ShortcutInfoCompat> = ShortcutManagerCompat.getDynamicShortcuts(context)

    private fun byId(): Map<String, ShortcutInfoCompat> = dynamic().associateBy { it.id }

    private fun record(profile: String): String? = prefs.getString(PinnedShortcuts.recordKey(profile), null)

    private fun pins(vararg pairs: Pair<String, String>) = pairs.map { PinnedSession(it.first, it.second) }

    private val record1 = """{"profileID":"server-1","sessions":[{"id":"s1","title":"Fix auth"}]}"""

    @Test
    fun `B-ANDR-030 publishes at most four pinned sessions, titles and ids only, and remembers the set with its owner`() {
        assertTrue(
            PinnedShortcuts.publish(
                context,
                "server-1",
                pins("s1" to "  Fix auth  ", "s2" to "", "s3" to "Refactor", "s4" to "Docs", "s5" to "Never shown"),
            ),
        )
        val published = byId()
        assertEquals(
            listOf("s1", "s2", "s3", "s4").map { PinnedShortcuts.shortcutId("server-1", it) }.toSet(),
            published.keys,
        )
        val first = published.getValue("oc.session:server-1:s1")
        assertEquals("Fix auth", first.shortLabel.toString())
        assertEquals("Untitled conversation", published.getValue("oc.session:server-1:s2").shortLabel.toString())
        // Exactly the two ids leave the app: no prompt text, busy state, timestamps or paths in the tap intent.
        for (shortcut in published.values) {
            val intent = shortcut.intent
            assertEquals(TilesComponents.MAIN_ACTIVITY, intent.component?.className)
            assertEquals(setOf(LaunchActions.EXTRA_PROFILE, LaunchActions.EXTRA_SESSION), intent.extras!!.keySet())
            assertEquals("server-1", intent.getStringExtra(LaunchActions.EXTRA_PROFILE))
        }
        val stored = JSONObject(record("server-1")!!)
        assertEquals("server-1", stored.getString("profileID"))
        assertEquals(4, stored.getJSONArray("sessions").length())
        assertEquals(setOf("id", "title"), stored.getJSONArray("sessions").getJSONObject(0).keys().asSequence().toSet())
    }

    @Test
    fun `B-ANDR-030 long titles are cut before they leave the app`() {
        PinnedShortcuts.publish(context, "server-1", pins("s1" to "x".repeat(200)))
        val shortcut = dynamic().single()
        assertEquals(PinnedShortcuts.MAX_TITLE_LENGTH, shortcut.shortLabel.length)
        assertEquals(PinnedShortcuts.MAX_TITLE_LENGTH, shortcut.longLabel!!.length)
        assertEquals(80, PinnedShortcuts.MAX_TITLE_LENGTH)
        assertEquals(4, PinnedShortcuts.MAX_SHORTCUTS)
    }

    @Test
    fun `B-ANDR-053 unchanged payloads are not republished and a title edit or a new pin is`() {
        PinnedShortcuts.publish(context, "server-1", pins("s1" to "Fix auth"))
        // Clear the launcher behind the publisher's back: a republish would put the entry back.
        ShortcutManagerCompat.removeAllDynamicShortcuts(context)
        assertTrue(PinnedShortcuts.publish(context, "server-1", pins("s1" to "Fix auth")))
        assertTrue("an unchanged payload reached the launcher again", dynamic().isEmpty())

        PinnedShortcuts.publish(context, "server-1", pins("s1" to "Fix auth now"))
        assertEquals("Fix auth now", dynamic().single().shortLabel.toString())
        PinnedShortcuts.publish(context, "server-1", pins("s1" to "Fix auth now", "s2" to "Docs"))
        assertEquals(2, dynamic().size)
    }

    @Test
    fun `B-ANDR-053 no pins publishes an empty set and forgets the record`() {
        PinnedShortcuts.publish(context, "server-1", pins("s1" to "Fix auth"))
        assertNotNull(record("server-1"))
        assertTrue(PinnedShortcuts.publish(context, "server-1", emptyList()))
        assertTrue(dynamic().isEmpty())
        assertNull(record("server-1"))
    }

    @Test
    fun `B-ANDR-053 withdrawing all on disconnect empties the launcher and forgets the record, and twice is free`() {
        PinnedShortcuts.publish(context, "server-1", pins("s1" to "Fix auth"))
        assertTrue(PinnedShortcuts.withdrawAll(context))
        assertTrue(dynamic().isEmpty())
        assertNull(record("server-1"))
        assertTrue(PinnedShortcuts.withdrawAll(context))
        assertTrue(dynamic().isEmpty())
    }

    @Test
    fun `B-ANDR-054 deleting a profile withdraws its shortcuts`() {
        PinnedShortcuts.publish(context, "server-1", pins("s1" to "Fix auth"))
        assertEquals(ShortcutClear.Cleared, PinnedShortcuts.withdrawProfile(context, "server-1"))
        assertNull(record("server-1"))
        assertTrue(dynamic().isEmpty())
    }

    @Test
    fun `B-ANDR-054 deleting another profile leaves this profile's shortcuts alone`() {
        PinnedShortcuts.publish(context, "server-1", pins("s1" to "Fix auth"))
        val before = record("server-1")
        assertEquals(ShortcutClear.NothingToClear, PinnedShortcuts.withdrawProfile(context, "server-2"))
        assertEquals(before, record("server-1"))
        assertEquals(listOf("oc.session:server-1:s1"), dynamic().map { it.id })
    }

    @Test
    fun `B-ANDR-054 nothing to clear when nothing was published`() {
        assertEquals(ShortcutClear.NothingToClear, PinnedShortcuts.withdrawProfile(context, "server-1"))
        assertTrue(dynamic().isEmpty())
        assertEquals(ShortcutClear.NothingToClear, PinnedShortcuts.withdrawProfile(context, " "))
    }

    @Test
    fun `B-ANDR-054 an unreadable record is withdrawn rather than kept`() {
        prefs.edit().putString(PinnedShortcuts.recordKey("server-1"), "{not json").commit()
        assertEquals(ShortcutClear.Cleared, PinnedShortcuts.withdrawProfile(context, "server-1"))
        assertNull(record("server-1"))
    }

    @Test
    fun `B-ANDR-055 a record whose launcher entries are gone is still removed without a publish`() {
        prefs.edit().putString(PinnedShortcuts.recordKey("server-1"), record1).commit()
        assertTrue(dynamic().isEmpty())
        assertEquals(ShortcutClear.Cleared, PinnedShortcuts.withdrawProfile(context, "server-1"))
        assertNull(record("server-1"))
        assertTrue("withdrawing must not publish anything", dynamic().isEmpty())
    }

    @Test
    fun `B-ANDR-055 non-Android platforms publish nothing - no desktop module can reach the launcher publisher`() {
        val root = sequenceOf(File("../.."), File(".."), File(".")).first { File(it, "settings.gradle.kts").isFile }
        val desktop = File(root, "desktopApp/build.gradle.kts").readText()
        assertFalse(desktop.contains(":android:tiles"))
        assertFalse("a desktop build must not depend on any Android module", desktop.contains("\":android:"))
        assertTrue("the desktop module list was not found", desktop.contains("\":core:common\""))
        // A blank profile is ignored outright, the same as "not connected".
        assertFalse(PinnedShortcuts.publish(context, "", pins("s1" to "Fix auth")))
        assertTrue(dynamic().isEmpty())
    }
}
