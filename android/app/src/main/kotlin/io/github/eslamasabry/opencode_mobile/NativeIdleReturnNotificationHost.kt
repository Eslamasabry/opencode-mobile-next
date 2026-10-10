package io.github.eslamasabry.opencode_mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** A return shortcut only: posting never starts a service or acquires a work lease. */
internal object NativeIdleReturnNotificationHost {
    internal const val ID = 4098
    private const val CHANNEL = "opencode_builtin_server"

    fun show(context: Context): Boolean = try { post(context) } catch (_: Throwable) { false }

    private fun post(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.areNotificationsEnabled() || !channelOpen(manager, context)) return false
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val open = PendingIntent.getActivity(context, ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(ID, Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(NativeStrings.get(context, R.string.native_phone_server_title))
            .setContentText(NativeStrings.get(context, R.string.native_phone_server_idle_body))
            .setContentIntent(open).setOngoing(false).setAutoCancel(true).build())
        return true
    }

    private fun channelOpen(manager: NotificationManager, context: Context): Boolean {
        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            NativeStrings.get(context, R.string.native_phone_server_channel),
            NotificationManager.IMPORTANCE_LOW).apply {
            description = NativeStrings.get(context, R.string.native_phone_server_description)
            setShowBadge(false)
        })
        return manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun clear(context: Context) {
        try { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(ID) }
        catch (_: Throwable) { }
    }
}
