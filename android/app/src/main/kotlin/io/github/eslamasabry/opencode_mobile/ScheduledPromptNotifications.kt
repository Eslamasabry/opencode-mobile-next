package io.github.eslamasabry.opencode_mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

internal object ScheduledPromptNotifications {
    private const val CHANNEL = "opencode_scheduled_prompts"
    private const val PENDING_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    private val quietSkips = setOf("stale_target", "not_due", "disabled")

    fun show(context: Context, entry: ScheduledPromptStore.Entry, response: Map<*, *>) {
        val status = response["status"] as? String ?: return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!worthShowing(entry, status, response) || !manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL, NativeStrings.get(context, R.string.native_schedule_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        runCatching { manager.notify(entry.workName, 0, build(context, entry, status, response)) }
    }

    private fun count(response: Map<*, *>, key: String): Int =
        (response[key] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0

    private fun worthShowing(entry: ScheduledPromptStore.Entry, status: String, response: Map<*, *>): Boolean {
        val digest = status == "digest"
        return when {
            entry.id == "__digest" && !digest -> false
            status == "skipped" && response["error"] in quietSkips -> false
            digest -> count(response, "ran") + count(response, "failed") + count(response, "skipped") > 0
            else -> true
        }
    }

    private fun body(context: Context, status: String, response: Map<*, *>): String = when (status) {
        "digest" -> NativeStrings.get(
            context, R.string.native_schedule_digest_body,
            count(response, "ran"), count(response, "failed"), count(response, "skipped"),
        )
        "ran" -> NativeStrings.get(context, R.string.native_schedule_ran)
        "skipped" -> NativeStrings.get(context, R.string.native_schedule_skipped)
        else -> NativeStrings.get(context, R.string.native_schedule_failed)
    }

    private fun openIntent(
        context: Context,
        entry: ScheduledPromptStore.Entry,
        digest: Boolean,
        response: Map<*, *>,
    ): Intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        data = Uri.Builder().scheme("oc-schedule-open").authority(entry.profileId)
            .appendPath(entry.occurrence).build()
        putExtra(ScheduledPromptChannel.OPEN_PROFILE, entry.profileId)
        putExtra(ScheduledPromptChannel.OPEN_SCHEDULE, if (digest) "" else entry.id)
        putExtra(ScheduledPromptChannel.OPEN_SESSION, response["sessionId"] as? String ?: "")
    }

    private fun build(
        context: Context,
        entry: ScheduledPromptStore.Entry,
        status: String,
        response: Map<*, *>,
    ): Notification {
        val digest = status == "digest"
        val title = NativeStrings.get(
            context,
            if (digest) R.string.native_schedule_digest_title else R.string.native_schedule_title,
        )
        val pending = PendingIntent.getActivity(context, 0, openIntent(context, entry, digest, response), PENDING_FLAGS)
        return Notification.Builder(context, CHANNEL).setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title).setContentText(body(context, status, response))
            .setContentIntent(pending).setAutoCancel(true)
            // Its own group, never Android's automatic one: an auto-grouped
            // row (for example next to the in-app server's ongoing notice)
            // collapses, and a tap on the collapsed row starts the app with
            // no scheduled-run extras, so the run's conversation never opened.
            .setGroup(entry.workName)
            .setVisibility(Notification.VISIBILITY_PRIVATE).build()
    }
}
