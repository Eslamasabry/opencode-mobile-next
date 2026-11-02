package io.github.eslamasabry.oc.android.widgets

import android.content.Context
import io.github.eslamasabry.oc.engine.runtime.host.RestorePhase
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import io.github.eslamasabry.opencode_mobile.WidgetProviders
import io.github.eslamasabry.opencode_mobile.WidgetV2Bridge
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
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
import org.w3c.dom.Element

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetFeedTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Before fun reset() = WidgetFeed.reset()

    @Test
    fun `P11-4_4 E10 the Linux line follows the runtime engine's status`() {
        assertEquals(PhoneServerLine.Off, PhoneServerLine.from(RuntimeStatus()))
        assertEquals(
            PhoneServerLine.Off,
            PhoneServerLine.from(RuntimeStatus(installed = false, serverRunning = true)),
        )
        assertEquals(
            PhoneServerLine.Running,
            PhoneServerLine.from(RuntimeStatus(installed = true, serverRunning = true)),
        )
        assertEquals(
            PhoneServerLine.Restoring,
            PhoneServerLine.from(
                RuntimeStatus(installed = true, restorePhase = RestorePhase.Restoring)
            ),
        )
        assertEquals(
            PhoneServerLine.Restoring,
            PhoneServerLine.from(
                RuntimeStatus(installed = true, restorePhase = RestorePhase.Waiting)
            ),
        )
        assertEquals(
            PhoneServerLine.Off,
            PhoneServerLine.from(
                RuntimeStatus(installed = true, restorePhase = RestorePhase.Unavailable)
            ),
        )
    }

    @Test
    fun `P11-4_4 E10 the Status widget redraws only when its line changes`() {
        val running = RuntimeStatus(installed = true, serverRunning = true)

        assertTrue(WidgetFeed.onRuntimeStatus(context, running))
        assertTrue(BuiltinLinux.serverRunningIfStarted())
        assertFalse(WidgetFeed.onRuntimeStatus(context, running.copy(bytesUsed = 42)))
        assertTrue(WidgetFeed.onRuntimeStatus(context, RuntimeStatus(installed = true)))
        assertFalse(BuiltinLinux.serverRunningIfStarted())
    }

    @Test
    fun `P11-11_3 the end of the live link marks the snapshot offline, its start never marks it connected`() {
        val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val offline =
            JSONObject()
                .put("version", 2)
                .put("profileID", "p1")
                .put("scopeToken", "s")
                .put("connected", false)
        prefs.edit().putString("flutter.oc.widgetSessions", offline.toString()).commit()

        bindWidgetTestPersistence(context)
        assertTrue(WidgetFeed.onConnection(context, true))
        assertFalse(WidgetV2Bridge.snapshot(context)!!.getBoolean("connected"))
        assertTrue(BackgroundConnectionService.active)

        prefs
            .edit()
            .putString("flutter.oc.widgetSessions", offline.put("connected", true).toString())
            .commit()
        bindWidgetTestPersistence(context)
        assertTrue(WidgetFeed.onConnection(context, false))
        assertFalse(WidgetV2Bridge.snapshot(context)!!.getBoolean("connected"))
        assertFalse(WidgetFeed.onConnection(context, false))
    }

    @Test
    fun `P11-3 provider class names survive the in-place update`() {
        val frozen =
            setOf(
                    "SessionsWidgetProvider",
                    "SessionsWideWidgetProvider",
                    "SessionsListWidgetProvider",
                    "SessionsStripWidgetProvider",
                    "UsageWidgetProvider",
                    "UsageWideWidgetProvider",
                    "StatusWidgetProvider",
                )
                .map { "io.github.eslamasabry.opencode_mobile.$it" }
                .toSet()
        assertEquals(frozen, WidgetProviders.allNames)

        val ns = "http://schemas.android.com/apk/res/android"
        val manifest =
            DocumentBuilderFactory.newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(File("src/main/AndroidManifest.xml"))
                .documentElement
        val receivers = manifest.getElementsByTagName("receiver")
        val declared =
            (0 until receivers.length)
                .map { (receivers.item(it) as Element).getAttributeNS(ns, "name") }
                .toSet()
        assertEquals(
            frozen + "io.github.eslamasabry.opencode_mobile.WidgetActionReceiver",
            declared,
        )
    }

    @Test
    fun `P11-3 widget taps target the frozen component names`() {
        val snapshot = JSONObject().put("version", 2).put("profileID", "p1").put("scopeToken", "s")
        val session = JSONObject().put("id", "a")

        val action =
            shadowOf(WidgetV2Bridge.actionIntent(context, 7, snapshot, session, "allowOnce"))
        assertEquals(BackgroundConnectionService.COMPONENT, action.savedIntent.component?.className)
        assertEquals(BackgroundConnectionService.ACTION_WIDGET_ACTION, action.savedIntent.action)
        assertTrue(action.isForegroundService)

        val route = shadowOf(WidgetV2Bridge.routeIntent(context, 7, snapshot, session, "steer"))
        assertEquals(WidgetRouteActivity.COMPONENT, route.savedIntent.component?.className)
        assertEquals(context.packageName, route.savedIntent.component?.packageName)
        assertTrue(route.isActivityIntent)
    }

    @Test
    fun `P11-4_2 with no runner bound a background run settles once with nothing`() {
        val results = mutableListOf<Map<*, *>?>()
        WidgetActionRuntime(context, emptyMap(), JSONObject(), 0L) { results.add(it) }.start()
        assertEquals(listOf<Map<*, *>?>(null), results)

        var cancelled = false
        var late: ((Map<*, *>?) -> Unit)? = null
        WidgetFeed.backgroundRunner =
            object : WidgetBackgroundRunner {
                override fun start(
                    context: Context,
                    payload: Map<String, Any?>,
                    snapshotJson: String,
                    deadlineEpochMs: Long,
                    operation: String,
                    complete: (Map<*, *>?) -> Unit,
                ): WidgetBackgroundRunner.Handle {
                    late = complete
                    return WidgetBackgroundRunner.Handle { cancelled = true }
                }
            }
        val after = mutableListOf<Map<*, *>?>()
        val runtime =
            WidgetActionRuntime(context, emptyMap(), JSONObject(), 0L, "checkStatus") {
                after.add(it)
            }
        runtime.start()
        runtime.destroy()
        late!!(mapOf("status" to "idle"))
        assertTrue(cancelled)
        assertTrue(after.isEmpty())
        assertNull(WidgetFeed.liveDispatcher)
    }
}
