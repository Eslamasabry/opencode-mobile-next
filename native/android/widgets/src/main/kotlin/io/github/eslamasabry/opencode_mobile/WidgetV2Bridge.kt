package io.github.eslamasabry.opencode_mobile

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import io.github.eslamasabry.oc.android.widgets.BackgroundConnectionService
import io.github.eslamasabry.oc.android.widgets.WidgetOverlayActivity
import io.github.eslamasabry.oc.android.widgets.WidgetRouteActivity

/** Private widget intents carry identifiers only. Typed/dictated text exists only during dispatch. */
object WidgetV2Bridge {
    internal const val EXTRA_PAYLOAD = "oc.widgetPayload"
    internal const val EXTRA_MODE = "oc.widgetMode"
    internal const val SNAPSHOT_KEY = "flutter.oc.widgetSessions"
    internal const val PREFS = "FlutterSharedPreferences"

    fun actionIntent(
        context: Context,
        widgetId: Int,
        snapshot: JSONObject,
        session: JSONObject?,
        action: String,
    ): PendingIntent {
        val payload = identity(snapshot, session).toMutableMap().apply {
            put("action", action)
            put("id", WidgetActionPolicy.token(this))
        }
        val intent = Intent().setClassName(context, BackgroundConnectionService.COMPONENT).apply {
            this.action = BackgroundConnectionService.ACTION_WIDGET_ACTION
            data = Uri.parse("opencode-widget://action/$widgetId/${payload["id"]}")
            putExtra(EXTRA_PAYLOAD, JSONObject(payload).toString())
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                context, widgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            PendingIntent.getService(
                context, widgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }

    fun routeIntent(
        context: Context,
        widgetId: Int,
        snapshot: JSONObject,
        session: JSONObject?,
        mode: String,
    ): PendingIntent {
        require(mode in setOf("configure", "steer", "voice"))
        val payload = identity(snapshot, session).toMutableMap().apply {
            put("steerMode", session?.optString("steerMode").orEmpty())
        }
        val intent = Intent().setClassName(context, WidgetRouteActivity.COMPONENT).apply {
            data = Uri.parse("opencode-widget://$mode/$widgetId/${WidgetActionPolicy.token(payload)}")
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(EXTRA_PAYLOAD, JSONObject(payload).toString())
            putExtra(EXTRA_MODE, mode)
        }
        return PendingIntent.getActivity(
            context, widgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun overlayReady(context: Context): Boolean = runCatching {
        context.packageManager.getApplicationInfo(context.packageName, android.content.pm.PackageManager.GET_META_DATA)
            .metaData?.getBoolean(WidgetOverlayActivity.READY_META, false) == true
    }.getOrDefault(false)

    fun deleteWidgets(context: Context, ids: IntArray) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for ((key, value) in prefs.all) {
            val styles = if (key.startsWith("flutter.oc.widgetStyle.") && value is String) {
                runCatching { JSONObject(value) }.getOrNull()
            } else null
            if (styles == null) continue
            ids.forEach { styles.remove(it.toString()) }
            if (styles.length() == 0) editor.remove(key) else editor.putString(key, styles.toString())
        }
        editor.apply()
    }

    /** Known service stops invalidate live presentation without erasing chat titles.
     * A later Dart event publishes the authoritative connection state; starting
     * the service alone must never mark an old snapshot connected again. */
    internal fun invalidateConnection(context: Context) {
        val current = snapshot(context) ?: return
        if (current.optString("profileID").isBlank() || current.optString("scopeToken").isBlank()) return
        current.put("connected", false)
        // "Offline · last seen ..." counts from the moment the live link ended.
        current.put("lastSeenAt", System.currentTimeMillis())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(SNAPSHOT_KEY, current.toString()).commit()
        SessionsWidgetProvider.refreshAll(context)
    }

    internal fun clearProfile(context: Context, profileID: String): Boolean {
        if (profileID.isBlank()) return false
        WidgetStatusScheduler.clearProfile(context, profileID)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .remove("flutter.oc.widgetStyle.$profileID")
            .remove("flutter.oc.widgetActions.$profileID")
            .remove("flutter.oc.widgetTarget.$profileID")
        if (snapshot(context)?.optString("profileID") == profileID) editor.remove(SNAPSHOT_KEY)
        val cleared = editor.commit()
        SessionsWidgetProvider.refreshAll(context)
        UsageWidgetProvider.refreshAll(context)
        return cleared
    }

    /** Style owner for widgets placed before the first snapshot. */
    internal const val DEVICE_STYLE_KEY = "_device"

    internal fun snapshot(context: Context): JSONObject? = runCatching {
        JSONObject(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(SNAPSHOT_KEY, "") ?: "")
            .takeIf { it.optInt("version") == 2 }
    }.getOrNull()

    internal fun identity(snapshot: JSONObject, session: JSONObject?): Map<String, Any?> {
        val team = session?.optJSONObject("team")
        return mapOf(
            "profileID" to snapshot.optString("profileID"),
            "scopeToken" to snapshot.optString("scopeToken"),
            "sessionID" to session?.optString("id").orEmpty(),
            "requestID" to session?.optString("requestID").orEmpty(),
            "runID" to team?.optString("runID").orEmpty(),
            "gateID" to team?.optString("gateID").orEmpty(),
            "lastProjectID" to snapshot.optString("lastProjectID"),
            "expectedStartedAt" to (session?.optLong("startedAt") ?: 0L),
        )
    }

    internal fun parsePayload(raw: String?): Map<String, Any?>? = runCatching {
        val json = JSONObject(raw ?: "")
        (WidgetActionPolicy.identityFields + listOf("id", "action", "steerMode")).associateWith {
            if (it == "expectedStartedAt") json.optLong(it) else json.optString(it)
        }
    }.getOrNull()

    internal fun currentIdentity(context: Context, payload: Map<String, Any?>): Map<String, Any?>? {
        val snapshot = snapshot(context) ?: return null
        val rows = snapshot.optJSONArray("sessions") ?: JSONArray()
        val session = (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
            .firstOrNull { it.optString("id") == payload["sessionID"] }
        return if (payload["action"] != "voice" && session == null) null else identity(snapshot, session) + mapOf(
            "busy" to (session?.optBoolean("busy") == true),
            "teamAction" to session?.optJSONObject("team")?.optString("action"),
        )
    }
}

class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val payload = WidgetV2Bridge.parsePayload(intent.getStringExtra(WidgetV2Bridge.EXTRA_PAYLOAD)) ?: return
        val pending = goAsync()
        WidgetActionBridge.submit(context, payload) { pending.finish() }
    }
}
