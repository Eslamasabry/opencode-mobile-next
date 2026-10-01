package io.github.eslamasabry.opencode_mobile

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject

/**
 * Home-screen widget listing the newest OpenCode sessions. It renders only
 * the snapshot the Flutter side persists (`flutter.oc.widgetSessions`) —
 * session id, title, busy flag, updated time — and never polls the server
 * itself. Taps deep-link into the exact chat through the same intent extras
 * the notification body-tap path already consumes.
 */
class SessionsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    companion object {
        private const val SNAPSHOT_KEY = "flutter.oc.widgetSessions"
        private const val ATTENTION_KEY = "flutter.oc.attentionTile"
        private const val ATTENTION_MAX_AGE_MS = 24L * 60 * 60 * 1000
        private const val FLUTTER_PREFS = "FlutterSharedPreferences"

        private val rowIDs = intArrayOf(
            R.id.widget_row_0,
            R.id.widget_row_1,
            R.id.widget_row_2,
            R.id.widget_row_3
        )
        private val dotIDs = intArrayOf(
            R.id.widget_dot_0,
            R.id.widget_dot_1,
            R.id.widget_dot_2,
            R.id.widget_dot_3
        )
        private val titleIDs = intArrayOf(
            R.id.widget_title_0,
            R.id.widget_title_1,
            R.id.widget_title_2,
            R.id.widget_title_3
        )
        private val timeIDs = intArrayOf(
            R.id.widget_time_0,
            R.id.widget_time_1,
            R.id.widget_time_2,
            R.id.widget_time_3
        )

        /** Redraws every placed widget; called from the app after a snapshot write. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, SessionsWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            for (id in ids) manager.updateAppWidget(id, buildViews(context))
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_sessions)

            // Header and empty-state taps open the app normally.
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header, open)
            views.setOnClickPendingIntent(R.id.widget_empty, open)
            views.setOnClickPendingIntent(R.id.widget_new, open)

            // The "N need you" badge reads the Quick Settings tile's snapshot
            // (same key, same one-day freshness) and opens Activity.
            val attention = readAttentionCount(context)
            if (attention != null && attention > 0) {
                views.setTextViewText(
                    R.id.widget_attention,
                    context.resources.getQuantityString(
                        R.plurals.tile_need_you,
                        attention,
                        attention
                    )
                )
                views.setViewVisibility(R.id.widget_attention, View.VISIBLE)
                views.setOnClickPendingIntent(
                    R.id.widget_attention,
                    PendingIntent.getActivity(
                        context,
                        99,
                        Intent(context, MainActivity::class.java).apply {
                            action = Intent.ACTION_MAIN
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra(
                                MainActivity.EXTRA_LAUNCH_ACTION,
                                AttentionTileService.LAUNCH_ACTION_ACTIVITY
                            )
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                    )
                )
            } else {
                views.setViewVisibility(R.id.widget_attention, View.GONE)
            }

            val snapshot = readSnapshot(context)
            val sessions = snapshot?.optJSONArray("sessions")
            val profileID = snapshot?.optString("profileID").orEmpty()
            val count = sessions?.length() ?: 0

            views.setViewVisibility(
                R.id.widget_empty,
                if (count == 0) View.VISIBLE else View.GONE
            )

            for (index in rowIDs.indices) {
                if (sessions == null || index >= count) {
                    views.setViewVisibility(rowIDs[index], View.GONE)
                    continue
                }
                val session = sessions.optJSONObject(index) ?: continue
                val sessionID = session.optString("id")
                if (sessionID.isBlank()) {
                    views.setViewVisibility(rowIDs[index], View.GONE)
                    continue
                }
                views.setViewVisibility(rowIDs[index], View.VISIBLE)
                views.setTextViewText(
                    titleIDs[index],
                    session.optString("title", "Untitled conversation")
                )
                views.setTextViewText(
                    timeIDs[index],
                    relativeLabel(session.optLong("updatedAt", 0L))
                )
                views.setImageViewResource(
                    dotIDs[index],
                    if (session.optBoolean("busy")) {
                        R.drawable.widget_dot_busy
                    } else {
                        R.drawable.widget_dot_idle
                    }
                )
                // Deep-link into the exact chat via the alert-open extras the
                // app already consumes; 'complete' routes to the transcript.
                val chat = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    data = android.net.Uri.parse("opencode://widget/$sessionID")
                    putExtra(
                        BackgroundConnectionService.EXTRA_CODING_ALERT_KIND,
                        "complete"
                    )
                    putExtra(
                        BackgroundConnectionService.EXTRA_CODING_ALERT_SESSION_ID,
                        sessionID
                    )
                    // Which server profile wrote the snapshot; Dart opens the
                    // app normally when it no longer matches the active one.
                    if (profileID.isNotBlank()) {
                        putExtra(
                            BackgroundConnectionService.EXTRA_CODING_ALERT_PROFILE_ID,
                            profileID
                        )
                    }
                }
                views.setOnClickPendingIntent(
                    rowIDs[index],
                    PendingIntent.getActivity(
                        context,
                        100 + index,
                        chat,
                        PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
            return views
        }

        private fun readAttentionCount(context: Context): Int? {
            val raw = context
                .getSharedPreferences(FLUTTER_PREFS, Context.MODE_PRIVATE)
                .getString(ATTENTION_KEY, null)
                ?: return null
            return try {
                val snapshot = JSONObject(raw)
                val updatedAt = snapshot.optLong("updatedAt", 0L)
                if (updatedAt <= 0L ||
                    System.currentTimeMillis() - updatedAt > ATTENTION_MAX_AGE_MS
                ) {
                    null
                } else {
                    snapshot.optInt("pendingCount", 0).coerceAtLeast(0)
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun readSnapshot(context: Context): JSONObject? {
            val raw = context
                .getSharedPreferences(FLUTTER_PREFS, Context.MODE_PRIVATE)
                .getString(SNAPSHOT_KEY, null)
                ?: return null
            return try {
                JSONObject(raw)
            } catch (_: Exception) {
                null
            }
        }

        private fun relativeLabel(updatedAt: Long): String {
            if (updatedAt <= 0L) return ""
            val delta = System.currentTimeMillis() - updatedAt
            val minutes = delta / 60_000
            return when {
                minutes < 1 -> "now"
                minutes < 60 -> "${minutes}m"
                minutes < 60 * 24 -> "${minutes / 60}h"
                else -> "${minutes / (60 * 24)}d"
            }
        }
    }
}
