package io.github.eslamasabry.oc.android.tiles

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The Flutter `attention_tile_snapshot_test.dart` facts AttentionTileCacheTest does not assert yet. A write that
 * returns true is the one that asked the tile to redraw (the cache redraws on exactly the path that writes).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AttentionTileRedrawTest {
    private lateinit var context: Context
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        TilesComponents.prefs(context).edit().clear().commit()
    }

    private fun stored(): String? = TilesComponents.prefs(context).getString(AttentionTileCache.KEY, null)

    @Test
    fun `B-ANDR-010 a write redraws once, the same count does not, and a clear redraws again`() {
        assertTrue(AttentionTileCache.write(context, 2, "server-1", now))
        assertFalse(AttentionTileCache.write(context, 2, "server-1", now + 1))
        AttentionTileCache.clear(context)
        assertNull(stored())
        // Clearing what is already gone does nothing more.
        AttentionTileCache.clear(context)
        assertNull(stored())
    }

    @Test
    fun `B-ANDR-010 after a clear the same count is written again because the cache was really gone`() {
        AttentionTileCache.write(context, 2, "server-1", now)
        AttentionTileCache.clear(context)
        assertTrue(AttentionTileCache.write(context, 2, "server-1", now + 1))
        assertEquals(2, JSONObject(stored()!!).getInt("pendingCount"))
    }

    @Test
    fun `B-ANDR-010 a zero count and an owner change are both written`() {
        AttentionTileCache.write(context, 2, "server-1", now)
        assertTrue(AttentionTileCache.write(context, 0, "server-1", now + 1))
        assertEquals(0, JSONObject(stored()!!).getInt("pendingCount"))
        assertTrue(AttentionTileCache.write(context, 0, "server-2", now + 2))
        assertEquals("server-2", JSONObject(stored()!!).getString("owner"))
    }

    @Test
    fun `B-ANDR-010 deleting a profile with nothing cached reports nothing to clear`() {
        assertFalse(AttentionTileCache.clearOwner(context, "server-1"))
        assertNull(stored())
    }

    @Test
    fun `B-ANDR-010 deleting another profile keeps the stored count byte for byte`() {
        AttentionTileCache.write(context, 2, "server-1", now)
        val before = stored()
        assertFalse(AttentionTileCache.clearOwner(context, "server-2"))
        assertEquals(before, stored())
    }
}
