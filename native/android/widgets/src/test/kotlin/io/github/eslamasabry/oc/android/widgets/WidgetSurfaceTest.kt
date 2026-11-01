package io.github.eslamasabry.oc.android.widgets

import android.content.Context
import android.content.SharedPreferences
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
class WidgetSurfaceTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val prefs: SharedPreferences = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
    private var redraws = 0
    private val surface = AndroidWidgetSurface(context) { redraws++ }
    private val snapshot = """{"version":2,"profileID":"p1","scopeToken":"s1","connected":true,"sessions":[]}"""
    private val target = """{"profileID":"p1","fingerprint":"f1"}"""

    @Before fun clean() {
        // WidgetStatusScheduler cancels the profile's follow-up checks through WorkManager.
        runCatching {
            androidx.work.WorkManager.initialize(
                context, androidx.work.Configuration.Builder().setExecutor { it.run() }.build(),
            )
        }
        prefs.edit().clear().commit()
        WidgetFeed.reset()
    }

    @Test fun `B-ANDR-022 the snapshot is written, the target first, and every widget redraws`() {
        val order = mutableListOf<String?>()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> order.add(key) }
        prefs.registerOnSharedPreferenceChangeListener(listener)

        assertEquals(PublishResult.Written, surface.publish(snapshot, "p1", target))

        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        assertEquals(listOf("flutter.oc.widgetTarget.p1", "flutter.oc.widgetSessions"), order)
        assertEquals(snapshot, prefs.getString("flutter.oc.widgetSessions", null))
        assertEquals(target, prefs.getString("flutter.oc.widgetTarget.p1", null))
        assertEquals(1, redraws)
    }

    @Test fun `B-ANDR-117 unchanged widget payloads are neither rewritten nor redrawn`() {
        surface.publish(snapshot, "p1", target)
        var writes = 0
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> writes++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)

        assertEquals(PublishResult.Unchanged, surface.publish(snapshot, "p1", target))
        assertEquals(PublishResult.Unchanged, surface.publish(snapshot, "p1", null))

        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        assertEquals(0, writes)
        assertEquals(1, redraws)
        // A changed byte is a change.
        assertEquals(PublishResult.Written, surface.publish(snapshot.replace("true", "false"), "p1", target))
        assertEquals(2, redraws)
    }

    @Test fun `B-ANDR-117 a publish with no profile writes nothing`() {
        assertEquals(PublishResult.Refused, surface.publish(snapshot, "", target))
        assertNull(prefs.getString("flutter.oc.widgetSessions", null))
        assertEquals(0, redraws)
    }

    @Test fun `P11-11_3 invalidating the connection keeps titles and marks the snapshot offline`() {
        val withTitle = """{"version":2,"profileID":"p1","scopeToken":"s1","connected":true,""" +
            """"sessions":[{"id":"a","title":"Fix the focus ring"}]}"""
        surface.publish(withTitle, "p1", null)

        surface.invalidateConnection()

        val stored = org.json.JSONObject(prefs.getString("flutter.oc.widgetSessions", null)!!)
        assertFalse(stored.getBoolean("connected"))
        assertTrue(stored.getLong("lastSeenAt") > 0)
        assertEquals("Fix the focus ring", stored.getJSONArray("sessions").getJSONObject(0).getString("title"))
    }

    @Test fun `P11-11_3 clearing a profile drops only that profile's widget keys`() {
        surface.publish(snapshot, "p1", target)
        prefs.edit()
            .putString("flutter.oc.widgetStyle.p1", "{}")
            .putString("flutter.oc.widgetActions.p1", "[]")
            .putString("flutter.oc.widgetTarget.p2", "{}")
            .putString("flutter.oc.widgetStyle.p2", "{}")
            .commit()

        assertEquals(ClearOutcome.Cleared, surface.clearProfile("p1"))

        for (key in listOf("widgetSessions", "widgetTarget.p1", "widgetStyle.p1", "widgetActions.p1")) {
            assertFalse(key, prefs.contains("flutter.oc.$key"))
        }
        assertTrue(prefs.contains("flutter.oc.widgetTarget.p2"))
        assertTrue(prefs.contains("flutter.oc.widgetStyle.p2"))
        assertEquals(ClearOutcome.NothingToClear, surface.clearProfile("p1"))
        assertEquals(ClearOutcome.NothingToClear, surface.clearProfile(""))
    }
}
