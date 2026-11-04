package io.github.eslamasabry.oc.android.tiles

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** `test/launch_surfaces_controller_test.dart`: both launch surfaces follow connection truth. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LaunchSurfacesTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val prefs get() = TilesComponents.prefs(context)
    private var now = 1_767_225_600_000L
    private val surfaces = LaunchSurfaces(context) { now }

    private fun ids() = ShortcutManagerCompat.getDynamicShortcuts(context).map { it.id }.toSet()

    private fun tile(): JSONObject? = prefs.getString(AttentionTileCache.KEY, null)?.let(::JSONObject)

    private fun state(pins: List<String>, pending: Int = 0, connected: Boolean = true) =
        LaunchSurfaceState(
            profileId = "server-1",
            connected = connected,
            pins = pins.map { PinnedSession(it, if (it == "ses-2") "" else "Session $it") },
            pendingCount = pending,
        )

    @Test
    fun `B-ANDR-030 pinned sessions become launcher shortcuts capped at four and unpinning republishes the smaller set`() {
        assertTrue(surfaces.onState(state(listOf("ses-5", "ses-4", "ses-3", "ses-2", "ses-1"))))
        assertEquals(listOf("ses-5", "ses-4", "ses-3", "ses-2").map { "oc.session:server-1:$it" }.toSet(), ids())
        val untitled = ShortcutManagerCompat.getDynamicShortcuts(context).single { it.id.endsWith("ses-2") }
        assertEquals("Untitled conversation", untitled.shortLabel.toString())
        assertNotNull(prefs.getString(PinnedShortcuts.recordKey("server-1"), null))

        surfaces.onState(state(listOf("ses-4", "ses-3", "ses-2", "ses-1")))
        assertEquals(listOf("ses-4", "ses-3", "ses-2", "ses-1").map { "oc.session:server-1:$it" }.toSet(), ids())
    }

    @Test
    fun `B-ANDR-030 unchanged pins do not republish on every notification`() {
        surfaces.onState(state(listOf("ses-1")))
        ShortcutManagerCompat.removeAllDynamicShortcuts(context)
        surfaces.onState(state(listOf("ses-1")))
        surfaces.onState(state(listOf("ses-1")))
        assertTrue(ids().isEmpty())
    }

    @Test
    fun `B-ANDR-030 the tile cache carries the Activity count and nothing else`() {
        surfaces.onState(state(listOf("ses-1"), pending = 2))
        val cached = tile()!!
        assertEquals(2, cached.getInt("pendingCount"))
        assertEquals("server-1", cached.getString("owner"))
        assertEquals(setOf("pendingCount", "owner", "updatedAt"), cached.keys().asSequence().toSet())
        assertFalse(prefs.getString(AttentionTileCache.KEY, null)!!.contains("Session"))

        surfaces.onState(state(listOf("ses-1"), pending = 1))
        assertEquals(1, tile()!!.getInt("pendingCount"))
    }

    @Test
    fun `B-ANDR-030 nothing is published while not connected`() {
        assertFalse(surfaces.onState(state(listOf("ses-1"), pending = 3, connected = false)))
        assertFalse(surfaces.onState(state(listOf("ses-1")).copy(profileId = null)))
        assertTrue(ids().isEmpty())
        assertNull(prefs.getString(PinnedShortcuts.recordKey("server-1"), null))
        assertNull(tile())
    }

    @Test
    fun `B-ANDR-030 an explicit disconnect withdraws both surfaces`() {
        surfaces.onState(state(listOf("ses-1"), pending = 1))
        assertTrue(ids().isNotEmpty())
        assertNotNull(tile())

        surfaces.onExplicitDisconnect()

        assertTrue(ids().isEmpty())
        assertNull(prefs.getString(PinnedShortcuts.recordKey("server-1"), null))
        assertNull(tile())
    }

    @Test
    fun `B-ANDR-030 a lifecycle suspension keeps the last published state`() {
        surfaces.onState(state(listOf("ses-1"), pending = 1))
        val before = ids()
        // Suspension parks the link; the surfaces are told "not connected" and must not withdraw anything.
        surfaces.onState(state(listOf("ses-1"), pending = 0, connected = false))
        assertEquals(before, ids())
        assertNotNull(prefs.getString(PinnedShortcuts.recordKey("server-1"), null))
        assertEquals(1, tile()!!.getInt("pendingCount"))
    }
}
