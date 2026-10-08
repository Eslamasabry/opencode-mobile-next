package io.github.eslamasabry.opencode_mobile

import android.app.Notification
import android.app.NotificationManager
import android.content.Context

/** Refreshes existing native surfaces without restarting a service or changing permissions. */
object NativeNotificationLocale {
    private const val PHONE_NOTIFICATION_ID = 4097
    private val channels = listOf(
        Channel("opencode_live_connection", R.string.native_live_channel, R.string.native_live_description),
        Channel("opencode_coding_action", R.string.native_coding_requests_channel,
            R.string.native_coding_requests_description),
        Channel("opencode_coding_status", R.string.native_coding_status_channel,
            R.string.native_coding_status_description),
        Channel("opencode_team_progress", R.string.native_team_progress_channel,
            R.string.native_team_progress_description),
        Channel("opencode_builtin_server", R.string.native_phone_server_channel,
            R.string.native_phone_server_description),
        Channel("opencode_voice_setup", R.string.native_voice_setup),
    )

    fun refresh(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        for (copy in channels) {
            val channel = manager.getNotificationChannel(copy.id) ?: continue
            channel.name = NativeStrings.get(context, copy.name)
            copy.description?.let { channel.description = NativeStrings.get(context, it) }
            // Reuse the existing channel object: Android retains the user's
            // importance/sound choices and this only changes app-authored copy.
            manager.createNotificationChannel(channel)
        }
        BackgroundConnectionService.refreshLocale(context)
        refreshPhone(context, manager)
        SessionsWidgetProvider.refreshAll(context)
    }

    private fun refreshPhone(context: Context, manager: NotificationManager) {
        val existing = manager.activeNotifications.firstOrNull { it.id == PHONE_NOTIFICATION_ID } ?: return
        val notification = existing.notification
        val builder = Notification.Builder.recoverBuilder(context, notification)
        if (NativeStrings.matches(context, R.string.native_phone_server_title,
                notification.extras.getCharSequence(Notification.EXTRA_TITLE))) {
            builder.setContentTitle(NativeStrings.get(context, R.string.native_phone_server_title))
        }
        if (NativeStrings.matches(context, R.string.native_phone_server_body,
                notification.extras.getCharSequence(Notification.EXTRA_TEXT))) {
            builder.setContentText(NativeStrings.get(context, R.string.native_phone_server_body))
        }
        val actions = notification.actions?.map { action ->
            Notification.Action.Builder(action).build().apply {
                if (NativeStrings.matches(context, R.string.native_stop, title)) {
                    title = NativeStrings.get(context, R.string.native_stop)
                }
            }
        }
        val refreshed = builder.build()
        if (actions != null) refreshed.actions = actions.toTypedArray()
        // Supplied task/server titles are preserved; no restart, logout or
        // service control operation is involved in updating this notification.
        manager.notify(PHONE_NOTIFICATION_ID, refreshed)
    }

    private data class Channel(val id: String, val name: Int, val description: Int? = null)
}
