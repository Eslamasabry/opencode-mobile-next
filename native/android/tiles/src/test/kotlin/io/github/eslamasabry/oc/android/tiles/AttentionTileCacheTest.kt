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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AttentionTileCacheTest {
    private lateinit var context: Context
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        TilesComponents.prefs(context).edit().clear().commit()
    }

    private fun stored(): String? = TilesComponents.prefs(context).getString(AttentionTileCache.KEY, null)

    @Test
    fun `B-ANDR-010 cache stores only count owner and time and clamps negatives`() {
        assertTrue(AttentionTileCache.write(context, -3, "p1", now))
        val json = JSONObject(stored()!!)
        assertEquals(setOf("pendingCount", "owner", "updatedAt"), json.keys().asSequence().toSet())
        assertEquals(0, json.getInt("pendingCount"))
        assertEquals("p1", json.getString("owner"))
        assertEquals(now, json.getLong("updatedAt"))
        assertEquals(0, AttentionTileCache.readCached(context, now))
    }

    @Test
    fun `B-ANDR-010 rewrites only when count or owner changes`() {
        assertTrue(AttentionTileCache.write(context, 2, "p1", now))
        assertFalse(AttentionTileCache.write(context, 2, "p1", now + 1_000))
        assertEquals(now, JSONObject(stored()!!).getLong("updatedAt"))
        assertTrue(AttentionTileCache.write(context, 3, "p1", now + 2_000))
        assertTrue(AttentionTileCache.write(context, 3, "p2", now + 3_000))
        assertEquals(3, AttentionTileCache.readCached(context, now + 3_000))
    }

    @Test
    fun `B-ANDR-010 a confirmed unchanged count is restamped before it can go stale`() {
        AttentionTileCache.write(context, 1, "p1", now)
        assertTrue(AttentionTileCache.write(context, 1, "p1", now + AttentionTileCache.RESTAMP_AFTER_MS))
        assertEquals(now + AttentionTileCache.RESTAMP_AFTER_MS, JSONObject(stored()!!).getLong("updatedAt"))
    }

    @Test
    fun `B-ANDR-010 stale missing or unreadable snapshots show no count`() {
        val fresh = """{"pendingCount":4,"owner":"p1","updatedAt":$now}"""
        assertEquals(4, AttentionTileCache.read(fresh, now + AttentionTileCache.MAX_AGE_MS))
        assertNull(AttentionTileCache.read(fresh, now + AttentionTileCache.MAX_AGE_MS + 1))
        assertNull(AttentionTileCache.read(null, now))
        assertNull(AttentionTileCache.read("not json", now))
        assertNull(AttentionTileCache.read("""{"pendingCount":4}""", now))
        assertEquals(0, AttentionTileCache.read("""{"pendingCount":-2,"updatedAt":$now}""", now))
    }

    @Test
    fun `B-ANDR-010 clearing for a deleted profile drops only its count`() {
        AttentionTileCache.write(context, 5, "keep", now)
        assertFalse(AttentionTileCache.clearOwner(context, "gone"))
        assertEquals(5, AttentionTileCache.readCached(context, now))
        assertTrue(AttentionTileCache.clearOwner(context, "keep"))
        assertNull(stored())
        assertFalse(AttentionTileCache.clearOwner(context, "keep"))
    }

    @Test
    fun `B-ANDR-010 an ownerless or unreadable snapshot is dropped for any deleted profile`() {
        TilesComponents.prefs(context).edit().putString(AttentionTileCache.KEY, "garbage").commit()
        assertTrue(AttentionTileCache.clearOwner(context, "any"))
        assertNull(stored())
    }

    @Test
    fun `B-ANDR-010 disconnect clears the snapshot`() {
        AttentionTileCache.write(context, 1, "p1", now)
        AttentionTileCache.clear(context)
        assertNull(stored())
        assertNull(AttentionTileCache.readCached(context, now))
    }
}
