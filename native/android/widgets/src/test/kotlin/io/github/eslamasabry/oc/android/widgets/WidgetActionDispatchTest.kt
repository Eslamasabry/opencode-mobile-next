package io.github.eslamasabry.oc.android.widgets

import android.content.Context
import android.os.Looper
import io.github.eslamasabry.opencode_mobile.WidgetActionBridge
import io.github.eslamasabry.opencode_mobile.WidgetActionPolicy
import io.github.eslamasabry.opencode_mobile.WidgetV2Bridge
import java.time.Duration
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** The tap → admission → claim → live dispatch path of the copied bridge, over the native seams. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class WidgetActionDispatchTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val prefs =
        context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
    private val dispatched = mutableListOf<Map<String, Any?>>()
    private var answer: (Map<String, Any?>) -> Map<String, Any?>? = {
        mapOf("id" to it["id"], "action" to it["action"], "status" to "accepted")
    }

    private val session =
        JSONObject()
            .put("id", "session-a")
            .put("title", "Say hi")
            .put("requestID", "request-a")
            .put("busy", true)
            .put("startedAt", 100L)
    private val snapshot =
        JSONObject()
            .put("version", 2)
            .put("profileID", "p1")
            .put("scopeToken", "scope-a")
            .put("lastProjectID", "project-a")
            .put("connected", true)
            .put("sessions", org.json.JSONArray().put(session))

    @Before
    fun setUp() {
        prefs.edit().clear().putString("flutter.oc.widgetSessions", snapshot.toString()).commit()
        WidgetFeed.reset()
        bindWidgetTestPersistence(context)
        WidgetFeed.onConnection(context, true)
        WidgetFeed.liveDispatcher = WidgetLiveDispatcher { payload, complete ->
            dispatched.add(payload)
            complete(answer(payload) ?: emptyMap())
        }
    }

    private fun payload(action: String): Map<String, Any?> {
        val identity = WidgetV2Bridge.identity(snapshot, session).toMutableMap()
        identity["action"] = action
        identity["id"] = WidgetActionPolicy.token(identity)
        return identity
    }

    private fun submit(payload: Map<String, Any?>): Map<String, Any?>? {
        var result: Map<String, Any?>? = null
        WidgetActionBridge.submit(context, payload) { result = it }
        shadowOf(Looper.getMainLooper()).idle()
        return result
    }

    private fun stored(): JSONObject =
        JSONObject(prefs.getString("flutter.oc.widgetSessions", "{}")!!)

    @Test
    fun `widgets-MAJOR-5 disconnect before posted dispatch releases the claim`() {
        val tap = payload("allowOnce")
        var first: Map<String, Any?>? = null
        WidgetActionBridge.submit(context, tap) { first = it }
        WidgetFeed.onConnection(context, false)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("rejected", first?.get("status"))
        assertTrue(dispatched.isEmpty())
        WidgetFeed.onConnection(context, true)
        assertEquals("accepted", submit(tap)?.get("status"))
        assertEquals(1, dispatched.size)
    }

    @Test
    fun `B-ANDR-121 malformed actions never enter background dispatch`() {
        val bad = payload("allowOnce") + mapOf("action" to "deleteEverything")

        val result = submit(bad)

        assertEquals("rejected", result?.get("status"))
        assertEquals("invalidRequest", result?.get("error"))
        assertTrue(dispatched.isEmpty())
        assertFalse(prefs.contains("flutter.oc.widgetActions.p1"))
    }

    @Test
    fun `B-ANDR-121 the same action is dispatched once and its result lands in the snapshot`() {
        answer = {
            mapOf(
                "id" to it["id"],
                "action" to it["action"],
                "status" to "uncertain",
                "error" to "uncertainDelivery",
            )
        }
        val tap = payload("allowOnce")

        val first = submit(tap)
        assertEquals("uncertain", first?.get("status"))
        assertEquals("uncertain", stored().getJSONObject("actionResult").getString("status"))

        val second = submit(tap)

        assertEquals(1, dispatched.size)
        assertEquals("busy", second?.get("error"))
        // The latest outcome is the one the widget shows.
        assertEquals("busy", stored().getJSONObject("actionResult").getString("error"))
    }

    @Test
    fun `B-ANDR-121 an accepted action leaves no banner`() {
        val result = submit(payload("allowOnce"))

        assertEquals("accepted", result?.get("status"))
        assertFalse(stored().has("actionResult"))
    }

    @Test
    fun `B-ANDR-121 a stale profile scope never reaches the conversation`() {
        prefs
            .edit()
            .putString(
                "flutter.oc.widgetSessions",
                JSONObject(snapshot.toString()).put("scopeToken", "scope-b").toString(),
            )
            .commit()

        bindWidgetTestPersistence(context)
        val result = submit(payload("stop"))

        assertEquals("staleRequest", result?.get("error"))
        assertTrue(dispatched.isEmpty())
    }

    @Test
    fun `B-ANDR-107 an answer for another action or an unknown status is uncertain, never a retryable reject`() {
        answer = { mapOf("id" to "someone-else", "action" to it["action"], "status" to "accepted") }
        assertEquals("uncertain", submit(payload("allowOnce"))?.get("status"))

        val tap = payload("reject")
        answer = { mapOf("id" to it["id"], "action" to it["action"], "status" to "maybe") }
        val result = submit(tap)
        assertEquals("uncertain", result?.get("status"))
        assertEquals("uncertainDelivery", result?.get("error"))
        // The claim stays: a second tap cannot replay an uncertain delivery.
        assertEquals("busy", submit(tap)?.get("error"))
    }

    @Test
    fun `B-ANDR-107 a raw server error never crosses into the snapshot`() {
        answer = {
            mapOf(
                "id" to it["id"],
                "action" to it["action"],
                "status" to "rejected",
                "error" to "Authorization: private",
            )
        }

        val result = submit(payload("allowOnce"))

        assertEquals("rejected", result?.get("status"))
        assertNull(result?.get("error"))
        assertFalse(stored().toString().contains("Authorization"))
    }

    @Test
    fun `B-ANDR-107 no answer within the dispatch budget reads uncertain`() {
        WidgetFeed.liveDispatcher = WidgetLiveDispatcher { payload, _ -> dispatched.add(payload) }
        var result: Map<String, Any?>? = null
        WidgetActionBridge.submit(context, payload("allowOnce")) { result = it }
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(result)

        shadowOf(Looper.getMainLooper())
            .idleFor(Duration.ofMillis(LiveWidgetDispatch.WIDGET_DISPATCH_TIMEOUT_MS))

        assertEquals("uncertain", result?.get("status"))
        assertEquals("uncertainDelivery", result?.get("error"))
    }

    @Test
    fun `P11-11_3 without a live connection a tap is unavailable and nothing is claimed`() {
        WidgetFeed.onConnection(context, false)

        val result = submit(payload("allowOnce"))

        assertEquals("unavailable", result?.get("error"))
        assertTrue(dispatched.isEmpty())
        assertFalse(prefs.contains("flutter.oc.widgetActions.p1"))
    }
}
