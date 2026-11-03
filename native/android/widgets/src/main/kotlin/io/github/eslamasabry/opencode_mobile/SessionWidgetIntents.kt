package io.github.eslamasabry.opencode_mobile

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import io.github.eslamasabry.oc.android.widgets.MainActivity
import io.github.eslamasabry.oc.android.widgets.BackgroundConnectionService

internal object SessionWidgetIntents {
    fun backgroundSettingsIntent(context: Context, id: Int): PendingIntent =
        PendingIntent.getActivity(context, id, Intent().setClassName(context, MainActivity.COMPONENT).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = Uri.parse("opencode-widget://background-settings/$id")
            putExtra(MainActivity.EXTRA_LAUNCH_ACTION, WidgetV2Policy.BACKGROUND_SETTINGS_ACTION)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Marks a widget tap that opens the conversation to answer it: the composer takes focus. */
    const val EXTRA_REPLY = "oc.widgetReply"

    /**
     * The server a row belongs to: its own `profileID` when it has one (the merged feed carries several servers'
     * conversations), else the snapshot's. Opening B's row while A is active must open B's conversation (B-ANDR-116).
     */
    fun rowProfile(snapshot: JSONObject, session: JSONObject?): String =
        session?.optString("profileID")?.takeIf { it.isNotBlank() } ?: snapshot.optString("profileID")

    /** Opens [session]'s conversation; [reply] lands with the composer focused and the keyboard up. */
    fun chatIntent(context: Context, id: Int, snapshot: JSONObject, session: JSONObject?,
        reply: Boolean = false): PendingIntent {
        val profile = rowProfile(snapshot, session)
        return PendingIntent.getActivity(context, id, Intent().setClassName(context, MainActivity.COMPONENT).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            // The data keeps Reply's PendingIntent apart from Open's (extras do not).
            data = Uri.Builder().scheme("opencode").authority("widget").appendPath(profile)
                .appendPath(session?.optString("id").orEmpty())
                .apply { if (reply) appendPath("reply") }.build()
            if (reply) putExtra(EXTRA_REPLY, true)
            // Team rows have synthetic IDs, not chat session IDs. Open the app until
            // an explicit project deep-link contract exists.
            if (session != null && session.optJSONObject("team") == null) {
                putExtra(BackgroundConnectionService.EXTRA_CODING_ALERT_KIND, "complete")
                putExtra(BackgroundConnectionService.EXTRA_CODING_ALERT_SESSION_ID, session.optString("id"))
                putExtra(BackgroundConnectionService.EXTRA_CODING_ALERT_PROFILE_ID, profile)
            }
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
