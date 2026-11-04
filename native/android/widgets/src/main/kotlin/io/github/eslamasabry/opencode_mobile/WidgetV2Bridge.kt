package io.github.eslamasabry.opencode_mobile

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.eslamasabry.oc.android.widgets.BackgroundConnectionService
import io.github.eslamasabry.oc.android.widgets.WidgetFeed
import io.github.eslamasabry.oc.android.widgets.WidgetOverlayActivity
import io.github.eslamasabry.oc.android.widgets.WidgetRouteActivity
import io.github.eslamasabry.oc.core.common.getOrNull
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Private widget intents carry identifiers only. Typed/dictated text exists only during dispatch.
 */
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
        val payload =
            identity(snapshot, session).toMutableMap().apply {
                put("action", action)
                put("id", WidgetActionPolicy.token(this))
            }
        val intent =
            Intent(context, WidgetActionReceiver::class.java).apply {
                this.action = BackgroundConnectionService.ACTION_WIDGET_ACTION
                data = Uri.parse("opencode-widget://action/$widgetId/${payload["id"]}")
                putExtra(EXTRA_PAYLOAD, JSONObject(payload).toString())
            }
        // A live tap has at most the bridge's 6.5 s deadline. The private receiver's goAsync keeps that bounded
        // dispatch alive without creating a second dataSync service or renewing the live service's promise.
        return PendingIntent.getBroadcast(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun routeIntent(
        context: Context,
        widgetId: Int,
        snapshot: JSONObject,
        session: JSONObject?,
        mode: String,
    ): PendingIntent {
        require(mode in setOf("configure", "steer", "voice"))
        val payload =
            identity(snapshot, session).toMutableMap().apply {
                put("steerMode", session?.optString("steerMode").orEmpty())
            }
        val intent =
            Intent().setClassName(context, WidgetRouteActivity.COMPONENT).apply {
                data =
                    Uri.parse(
                        "opencode-widget://$mode/$widgetId/${WidgetActionPolicy.token(payload)}"
                    )
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                putExtra(EXTRA_PAYLOAD, JSONObject(payload).toString())
                putExtra(EXTRA_MODE, mode)
            }
        return PendingIntent.getActivity(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun overlayReady(context: Context): Boolean = runCatching {
        context.packageManager
            .getApplicationInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_META_DATA,
            )
            .metaData
            ?.getBoolean(WidgetOverlayActivity.READY_META, false) == true
    }
        .getOrDefault(false)

    fun deleteWidgets(context: Context, ids: IntArray) {
        val storage = WidgetFeed.persistence ?: return
        storage.scope.launch {
            for (key in storage.store.keys("oc.widgetStyle.")) {
                val before = storage.string(key) ?: continue
                val styles = runCatching { JSONObject(before) }.getOrNull() ?: continue
                ids.forEach { styles.remove(it.toString()) }
                val profile = key.removePrefix("oc.widgetStyle.")
                val next = styles.takeIf { it.length() > 0 }?.toString()
                if (profile == DEVICE_STYLE_KEY) {
                    storage.store.compareAndSet(storage.lanes.app, key, before, next)
                } else storage.cas(profile, key, before, next)
            }
        }
    }

    internal suspend fun invalidateConnection(
        context: Context,
        persistence: io.github.eslamasabry.oc.android.widgets.WidgetPersistence? =
            WidgetFeed.persistence,
    ) {
        val storage = persistence ?: return
        val before = storage.string("oc.widgetSessions") ?: return
        val current = runCatching { JSONObject(before) }.getOrNull() ?: return
        val profile = current.optString("profileID")
        if (profile.isBlank() || current.optString("scopeToken").isBlank()) return
        current.put("connected", false)
        current.put("lastSeenAt", storage.clock.now().toEpochMilliseconds())
        if (storage.cas(profile, "oc.widgetSessions", before, current.toString()))
            SessionsWidgetProvider.refreshAll(context)
    }

    internal suspend fun clearProfile(
        context: Context,
        profileID: String,
        persistence: io.github.eslamasabry.oc.android.widgets.WidgetPersistence? =
            WidgetFeed.persistence,
    ): Boolean {
        if (profileID.isBlank()) return false
        val storage = persistence ?: return false
        WidgetStatusScheduler.clearProfile(context, profileID)
        val before = storage.string("oc.widgetSessions")
        if (
            before != null &&
                runCatching { JSONObject(before).optString("profileID") }.getOrNull() == profileID
        ) {
            // Deletion runs after the profile lane closes, so embedded cleanup uses the app lane.
            val removed =
                storage.store
                    .compareAndSet(storage.lanes.app, "oc.widgetSessions", before, null)
                    .getOrNull()
            if (removed != true) return false
        }
        if (!storage.removeProfileKeys(profileID)) return false
        SessionsWidgetProvider.refreshAll(context)
        UsageWidgetProvider.refreshAll(context)
        return true
    }

    /** Style owner for widgets placed before the first snapshot. */
    internal const val DEVICE_STYLE_KEY = "_device"

    internal fun snapshot(context: Context): JSONObject? = runCatching {
        JSONObject(WidgetFeed.persistence?.string("oc.widgetSessions") ?: "").takeIf {
            it.optInt("version") == 2
        }
    }
        .getOrNull()

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
    }
        .getOrNull()

    internal fun currentIdentity(context: Context, payload: Map<String, Any?>): Map<String, Any?>? {
        val snapshot = snapshot(context) ?: return null
        val rows = snapshot.optJSONArray("sessions") ?: JSONArray()
        val session =
            (0 until rows.length())
                .mapNotNull { rows.optJSONObject(it) }
                .firstOrNull { it.optString("id") == payload["sessionID"] }
        return if (payload["action"] != "voice" && session == null) null
        else
            identity(snapshot, session) +
                mapOf(
                    "busy" to (session?.optBoolean("busy") == true),
                    "teamAction" to session?.optJSONObject("team")?.optString("action"),
                )
    }
}

class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val payload =
            WidgetV2Bridge.parsePayload(intent.getStringExtra(WidgetV2Bridge.EXTRA_PAYLOAD))
                ?: return
        val pending = goAsync()
        WidgetActionBridge.submit(context, payload) { pending.finish() }
    }
}
