package io.github.eslamasabry.oc.android.schedules

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.eslamasabry.oc.engine.schedules.ArmedEntry
import io.github.eslamasabry.oc.engine.schedules.RunReport
import io.github.eslamasabry.oc.engine.schedules.ScheduleNotices

/**
 * The run's notices (Flutter ScheduledPromptNotifications.kt, same frozen channel id). A run shows "Running…" when it
 * starts; the finished notice REPLACES it in the same place (same tag and id), led by the reply's first line. The
 * lock screen shows only the generic title. Nothing here reads prompts, URLs or credentials.
 */
public class ScheduleNotifications(
    context: Context,
    /** The not-exported trampoline activity that routes a tap (B-NAV-054). */
    private val openActivity: Class<*>,
) : ScheduleNotices {
    private val app = context.applicationContext
    private val manager = app.getSystemService(NotificationManager::class.java)

    override fun started(entry: ArmedEntry) {
        if (!allowed()) return
        val text = app.getString(R.string.oc_schedule_running)
        notify(entry, base(entry, null).setContentTitle(title(null)).setContentText(text).setOngoing(true).setOnlyAlertOnce(true))
    }

    override fun finished(entry: ArmedEntry, report: RunReport) {
        if (!allowed()) return
        val builder =
            base(entry, report)
                .setContentTitle(title(report))
                .setContentText(body(report))
                .setStyle(Notification.BigTextStyle().bigText(body(report)))
                .setAutoCancel(true)
                .setOngoing(false)
        if (report.status == "failed") {
            builder.addAction(Notification.Action.Builder(null, app.getString(R.string.oc_schedule_open), open(entry, report)).build())
        }
        notify(entry, builder)
    }

    override fun clear(entry: ArmedEntry) {
        manager.cancel(entry.workName, ID)
    }

    private fun allowed(): Boolean = manager.areNotificationsEnabled()

    private fun notify(entry: ArmedEntry, builder: Notification.Builder) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, app.getString(R.string.oc_schedule_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        try {
            manager.notify(entry.workName, ID, builder.build())
        } catch (e: SecurityException) {
            // Notification permission revoked between the check and the post: nothing to show.
        }
    }

    private fun base(entry: ArmedEntry, report: RunReport?): Notification.Builder =
        Notification.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.oc_schedule_ic)
            .setContentIntent(open(entry, report))
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(
                Notification.Builder(app, CHANNEL).setSmallIcon(R.drawable.oc_schedule_ic)
                    .setContentTitle(app.getString(if (report?.digest == true) R.string.oc_schedule_digest_title else R.string.oc_schedule_title))
                    .build(),
            )
            // Its own group: an auto-grouped row collapses and a tap on it carries no routing extras.
            .setGroup(entry.workName)

    private fun title(report: RunReport?): String =
        when {
            report?.digest == true -> app.getString(R.string.oc_schedule_digest_title)
            report?.status == "ran" -> NoticeText.line(report.title, TITLE_MAX) ?: app.getString(R.string.oc_schedule_title)
            else -> app.getString(R.string.oc_schedule_title)
        }

    private fun body(report: RunReport): String =
        when (report.status) {
            "digest" -> app.getString(R.string.oc_schedule_digest_body, report.ran, report.failed, report.skipped)
            "ran" ->
                NoticeText.line(report.summary, REPLY_MAX)
                    ?: app.getString(if (report.sessionId != null && report.title != null) R.string.oc_schedule_finished else R.string.oc_schedule_ran)
            "skipped" -> app.getString(R.string.oc_schedule_skipped)
            else ->
                app.getString(
                    when (report.errorCode) {
                        "needs_you", "permission_required" -> R.string.oc_schedule_why_needs_you
                        "auth_required" -> R.string.oc_schedule_why_sign_in
                        "agent_failed" -> R.string.oc_schedule_why_agent
                        "timeout", "uncertain_delivery", "interrupted" -> R.string.oc_schedule_why_timeout
                        else -> R.string.oc_schedule_why_unavailable
                    },
                )
        }

    private fun open(entry: ArmedEntry, report: RunReport?): PendingIntent {
        val intent =
            Intent(app, openActivity).apply {
                // A per-occurrence data URI so extras of two notices never merge.
                data = Uri.Builder().scheme("oc-schedule-open").authority(entry.profileId).appendPath(entry.occurrence).build()
                putExtra(EXTRA_PROFILE, entry.profileId)
                putExtra(EXTRA_SCHEDULE, if (report?.digest == true) "" else entry.scheduleId)
                putExtra(EXTRA_SESSION, report?.sessionId.orEmpty())
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        return PendingIntent.getActivity(app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    public companion object {
        /** Frozen: the Flutter app's channel, so a person's channel settings survive the update. */
        public const val CHANNEL: String = "opencode_scheduled_prompts"
        public const val EXTRA_PROFILE: String = "oc.schedule.profile"
        public const val EXTRA_SCHEDULE: String = "oc.schedule.id"
        public const val EXTRA_SESSION: String = "oc.schedule.session"
        private const val ID = 0
        private const val TITLE_MAX = 80
        private const val REPLY_MAX = 100
    }
}

/** One line for the shade: first non-empty line, key-like tokens hidden, capped. */
public object NoticeText {
    private val secretLike = Regex("""\b(?:sk|pk|ghp|gho|xox[a-z]|AIza)[-_A-Za-z0-9]{12,}""")

    public fun line(value: String?, max: Int): String? {
        val line = value?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }?.replace(secretLike, "…") ?: return null
        return if (line.length <= max) line else line.take(max - 1).trimEnd() + "…"
    }
}
