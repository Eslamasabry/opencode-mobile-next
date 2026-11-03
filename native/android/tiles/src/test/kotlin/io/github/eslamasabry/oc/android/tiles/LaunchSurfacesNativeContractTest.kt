package io.github.eslamasabry.oc.android.tiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * `test/launch_surfaces_native_contract_test.dart`: the parts of the shortcut and tile contract that can only be read
 * from the sources (call order, what the tile service never touches), plus the compiled resources.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LaunchSurfacesNativeContractTest {
    private fun source(path: String): String =
        sequenceOf(File("src/main/$path"), File("tiles/src/main/$path"), File("android/tiles/src/main/$path"))
            .first { it.isFile }
            .readText()

    private val kotlin = "kotlin/io/github/eslamasabry"
    private val shortcuts by lazy { source("$kotlin/oc/android/tiles/PinnedShortcuts.kt") }
    private val tile by lazy { source("$kotlin/opencode_mobile/AttentionTileService.kt") }
    private val cache by lazy { source("$kotlin/oc/android/tiles/AttentionTileCache.kt") }
    private val actions by lazy { source("$kotlin/oc/android/tiles/LaunchActions.kt") }

    @Test
    fun `B-ANDR-031 stale entries are disabled before the new set is published and pinned copies are matched too`() {
        val publish = shortcuts.substring(shortcuts.indexOf("fun publish("))
        val disable = publish.indexOf("disable(context, staleIds(")
        val set = publish.indexOf("setDynamicShortcuts(")
        assertTrue(disable >= 0)
        assertTrue("disable must come before the publish", set > disable)
        assertTrue(shortcuts.contains("FLAG_MATCH_PINNED"))
        assertTrue(shortcuts.contains("ShortcutManagerCompat.disableShortcuts("))
        assertTrue(shortcuts.contains("R.string.shortcut_session_unavailable"))
        assertTrue(shortcuts.contains("R.string.shortcut_session_untitled"))
    }

    @Test
    fun `B-ANDR-031 the publisher targets MainActivity with the two id extras only`() {
        val intent = shortcuts.substring(shortcuts.indexOf("Intent(Intent.ACTION_MAIN)"), shortcuts.indexOf(".build()"))
        assertTrue(intent.contains("setClassName(context, TilesComponents.MAIN_ACTIVITY)"))
        assertEquals(2, Regex("""putExtra\(""").findAll(intent).count())
        assertTrue(intent.contains("putExtra(LaunchActions.EXTRA_PROFILE, profileId)"))
        assertTrue(intent.contains("putExtra(LaunchActions.EXTRA_SESSION, pin.sessionId)"))
        assertEquals("oc.shortcut.profile", LaunchActions.EXTRA_PROFILE)
        assertEquals("oc.shortcut.session", LaunchActions.EXTRA_SESSION)
    }

    @Test
    fun `B-ANDR-031 the session launch is drained once - both extras are removed before the tap is returned`() {
        val consume = actions.substring(actions.indexOf("fun consumeSessionShortcut"))
        val removeSession = consume.indexOf("removeExtra(EXTRA_SESSION)")
        val removeProfile = consume.indexOf("removeExtra(EXTRA_PROFILE)")
        val tap = consume.indexOf("SessionShortcutTap(profileId")
        assertTrue(removeSession >= 0 && removeProfile >= 0)
        assertTrue(tap > removeSession && tap > removeProfile)
    }

    @Test
    fun `B-ANDR-031 the shortcut icon exists and the compat library is declared`() {
        val res = RuntimeEnvironment.getApplication().resources
        assertTrue(R.drawable.ic_shortcut_session != 0)
        assertNotNull(res.getDrawable(R.drawable.ic_shortcut_session, null))
        val gradle = sequenceOf(File("build.gradle.kts"), File("android/tiles/build.gradle.kts")).first { it.isFile }.readText()
        assertTrue(gradle.contains("libs.androidx.core.ktx"))
    }

    @Test
    fun `B-ANDR-031 the tile reads the Activity-count key and never connects`() {
        assertEquals("oc.attentionTile", AttentionTileCache.KEY)
        assertTrue(tile.contains("AttentionTileCache.readCached("))
        assertTrue(cache.contains("getString(KEY, null)"))
        assertTrue(cache.contains("optInt(\"pendingCount\""))
        assertTrue(cache.contains("optLong(\"updatedAt\""))
        for (network in listOf("HttpURLConnection", "OkHttp", "Socket", "java.net.")) {
            assertFalse(network, tile.contains(network))
            assertFalse(network, cache.contains(network))
        }
    }

    @Test
    fun `B-ANDR-031 a tile tap sends only the whitelisted Activity action`() {
        assertTrue(tile.contains("LaunchActions.mainActivityIntent(packageName, LaunchAction.Activity)"))
        assertFalse(tile.contains("EXTRA_SESSION"))
        assertFalse(tile.contains("EXTRA_PROFILE"))
        assertTrue(tile.contains("startActivityAndCollapse"))
        assertTrue(tile.contains("unlockAndRun"))
        assertEquals("activity", LaunchAction.Activity.wire)
        assertEquals(LaunchAction.Activity, LaunchActions.parse("activity"))
    }

    @Test
    fun `B-ANDR-031 the tile degrades to its bare label and its copy is localized`() {
        assertTrue(tile.contains("R.string.tile_label"))
        assertTrue(tile.contains("R.plurals.tile_need_you"))
        assertTrue(tile.contains("R.string.tile_all_clear"))
        // With nothing cached there is no count, so only the bare label is drawn.
        assertTrue(tile.contains("count == null -> null"))
        assertEquals(null, AttentionTileCache.read(null, 0L))
        for (file in listOf("res/values/strings_tiles.xml", "res/values-ar/strings_tiles.xml")) {
            val strings = source(file)
            assertTrue(file, strings.contains("name=\"tile_label\""))
            assertTrue(file, strings.contains("name=\"tile_label_with_count\""))
            assertTrue(file, strings.contains("name=\"tile_all_clear\""))
            assertTrue(file, strings.contains("<plurals name=\"tile_need_you\">"))
        }
        val res = RuntimeEnvironment.getApplication().resources
        assertEquals("OpenCode", res.getString(R.string.tile_label))
        assertEquals("2 need you", res.getQuantityString(R.plurals.tile_need_you, 2, 2))
    }

    @Test
    @Config(qualifiers = "ar")
    fun `B-ANDR-031 Arabic tile copy is used under an Arabic locale`() {
        val res = RuntimeEnvironment.getApplication().resources
        val en = "Nothing needs you"
        assertTrue(res.getString(R.string.tile_all_clear).isNotBlank())
        assertFalse(res.getString(R.string.tile_all_clear) == en)
    }
}
