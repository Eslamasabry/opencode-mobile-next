package io.github.eslamasabry.oc.android.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/** What posting an alert did (plan 11 §10, `NotificationSurface.showAlert`). */
public sealed interface AlertShown {
    public data object Shown : AlertShown

    public data class Blocked(val why: BlockedWhy) : AlertShown

    public data object Invalid : AlertShown
}

public enum class BlockedWhy { Permission, ChannelOff }

/** The alert half of the notification surface, as the shade-action body sees it. */
public interface AlertShade {
    public fun show(alert: CodingAlert, route: AlertRoute.Post): AlertShown

    /**
     * Reposts the alert for [tap]'s key in plain words ("Open OpenCode to answer") with **no** buttons; its tap opens
     * the app at that exact request (plan 11 §10.6). Used after a failed, stale, timed-out or locked-phone answer.
     */
    public fun showOpenOnly(tap: ShadeTap): AlertShown

    /** Dismisses one alert and refreshes both group summaries. */
    public fun dismiss(key: String)

    /** Compare and mutate atomically; an older request must never change its replacement. */
    public fun dismissIfCurrent(tap: ShadeTap): Boolean

    public fun showOpenOnlyIfCurrent(tap: ShadeTap): AlertShown

    public fun isShowing(key: String): Boolean
}

/**
 * Posts alerts with `NotificationCompat` (androidx.core 1.19.1). Port of the Flutter app's
 * `BackgroundConnectionService.showCodingAlert` family, with the plan 11 §10.4/§10.6 changes: no RemoteInput reply or
 * option buttons are ever offered, every `PendingIntent` is immutable, and nothing here starts an activity.
 */
public class AndroidAlertShade(
    private val context: Context,
    private val words: NotifyWords,
    private val channels: ChannelRegistry,
) : AlertShade {
    private val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)

    override fun show(alert: CodingAlert, route: AlertRoute.Post): AlertShown {
        synchronized(shadeLock) {
        if (!alert.isValid) return AlertShown.Invalid
        blocked(route.channel)?.let { return it }
        val id = NotificationIds.alert(alert.key)
        val publicWords = publicWords(alert)
        val (title, text) = privateWords(alert, publicWords)
        val builder =
            base(route.channel, title, text, openIntent(id, alert.kind, alert.sessionId, alert.profileId, alert.requestId, alert.monitorToken))
                .setOnlyAlertOnce(!alert.kind.isQuota)
                .setCategory(category(alert.kind))
        if (!alert.kind.isQuota) builder.setPublicVersion(publicVersion(route.channel, publicWords))
        route.group?.let { builder.setGroup(it).setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN) }
        if (alert.kind == AlertKind.Complete) builder.setTimeoutAfter(FINISHED_TIMEOUT_MS)
        val subtext = alert.subtext.trim()
        if (subtext.isNotEmpty()) builder.setSubText(subtext.take(MAX_SUBTEXT))
        if (text.isNotEmpty()) builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        builder.extras.putString(FrozenExtras.EXTRA_SUMMARY_LINE, AlertCopy.summaryLine(alert, title))
        AlertButtons.plan(alert).forEachIndexed { index, button -> builder.addAction(action(alert, button, id, index)) }
        builder.extras.apply {
            putString(FrozenExtras.EXTRA_CODING_ALERT_KIND, alert.kind.wire)
            putString(FrozenExtras.EXTRA_CODING_ALERT_KEY, alert.key)
            putString(FrozenExtras.EXTRA_CODING_ALERT_PROFILE_ID, alert.profileId)
            putString(FrozenExtras.EXTRA_CODING_ALERT_SESSION_ID, alert.sessionId)
            putString(FrozenExtras.EXTRA_CODING_ALERT_REQUEST_ID, alert.requestId)
        }
        if (!post(id, builder.build())) return AlertShown.Blocked(BlockedWhy.Permission)
        route.group?.let { refreshSummary(it) }
        return AlertShown.Shown
            }
    }

    override fun showOpenOnly(tap: ShadeTap): AlertShown {
        synchronized(shadeLock) {
        if (tap.key.isBlank()) return AlertShown.Invalid
        val channel = NotifyChannel.CodingAction
        blocked(channel)?.let { return it }
        val id = NotificationIds.alert(tap.key)
        val title = words.text(R.string.notify_public_needs_you)
        val text = words.text(R.string.notify_answer_in_app)
        val builder =
            base(channel, title, text, openIntent(id, tap.kind, tap.sessionId, tap.profileId, tap.requestId, ""))
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setPublicVersion(publicVersion(channel, title))
        AlertPolicy.groupOf(tap.kind)?.let { builder.setGroup(it) }
        builder.extras.putString(FrozenExtras.EXTRA_SUMMARY_LINE, title)
        builder.extras.apply {
            putString(FrozenExtras.EXTRA_CODING_ALERT_KIND, tap.kind.wire)
            putString(FrozenExtras.EXTRA_CODING_ALERT_KEY, tap.key)
            putString(FrozenExtras.EXTRA_CODING_ALERT_PROFILE_ID, tap.profileId)
            putString(FrozenExtras.EXTRA_CODING_ALERT_SESSION_ID, tap.sessionId)
            putString(FrozenExtras.EXTRA_CODING_ALERT_REQUEST_ID, tap.requestId)
        }
        if (!post(id, builder.build())) return AlertShown.Blocked(BlockedWhy.Permission)
        return AlertShown.Shown
            }
    }

    override fun dismiss(key: String) {
        synchronized(shadeLock) {
        if (key.isBlank()) return
        manager.cancel(NotificationIds.alert(key))
        // The last alert of a group takes its summary with it.
        refreshSummary(AlertGroups.NEEDS_YOU)
        refreshSummary(AlertGroups.FINISHED)
            }
    }

    override fun dismissIfCurrent(tap: ShadeTap): Boolean = synchronized(shadeLock) {
        if (!isCurrent(tap)) return false
        dismiss(tap.key)
        true
    }

    override fun showOpenOnlyIfCurrent(tap: ShadeTap): AlertShown = synchronized(shadeLock) {
        if (!isCurrent(tap)) return AlertShown.Invalid
        showOpenOnly(tap)
    }

    private fun isCurrent(tap: ShadeTap): Boolean {
        val row = manager.activeNotifications.firstOrNull {
            it.id == NotificationIds.alert(tap.key) && it.packageName == context.packageName
        }?.notification ?: return false
        val extras = row.extras
        return extras.getString(FrozenExtras.EXTRA_CODING_ALERT_KIND) == tap.kind.wire &&
            extras.getString(FrozenExtras.EXTRA_CODING_ALERT_KEY) == tap.key &&
            extras.getString(FrozenExtras.EXTRA_CODING_ALERT_PROFILE_ID) == tap.profileId &&
            extras.getString(FrozenExtras.EXTRA_CODING_ALERT_SESSION_ID) == tap.sessionId &&
            extras.getString(FrozenExtras.EXTRA_CODING_ALERT_REQUEST_ID) == tap.requestId
    }

    override fun isShowing(key: String): Boolean {
        val id = NotificationIds.alert(key)
        return manager.activeNotifications.any { it.id == id && it.packageName == context.packageName }
    }

    /** The common shape: private visibility, the localized context, auto-cancel, the content intent. */
    private fun base(channel: NotifyChannel, title: String, text: String, content: PendingIntent): NotificationCompat.Builder =
        NotificationCompat.Builder(words.localized(), channel.id)
            .setSmallIcon(R.drawable.notify_ic_status)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(content)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(if (channel == NotifyChannel.CodingAction) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)

    private fun blocked(channel: NotifyChannel): AlertShown.Blocked? {
        if (!manager.areNotificationsEnabled()) return AlertShown.Blocked(BlockedWhy.Permission)
        if (manager.getNotificationChannel(channel.id) == null) channels.ensure()
        if (channels.isOff(channel)) return AlertShown.Blocked(BlockedWhy.ChannelOff)
        return null
    }

    private fun post(id: Int, notification: Notification): Boolean =
        try {
            manager.notify(id, notification)
            true
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check and the post.
            false
        }

    /** The lock screen's words: fixed, count-free and nameless (map T3, B-SEC-105). */
    private fun publicWords(alert: CodingAlert): String =
        when (alert.kind) {
            AlertKind.Permission, AlertKind.Question -> words.text(R.string.notify_public_needs_you)
            AlertKind.Complete -> words.text(R.string.notify_public_finished)
            AlertKind.Error -> words.text(R.string.notify_status_public_stopped)
            AlertKind.Quota -> words.text(R.string.notify_alert_quota_title)
            AlertKind.QuotaReset -> words.text(R.string.notify_alert_quota_reset_generic_title)
            AlertKind.CheckIn -> words.text(R.string.notify_alert_checkin_title, "OpenCode")
            AlertKind.Test -> words.text(R.string.notify_test_title)
        }

    /** The shade's words (title, text). Fixed copy for quota, check-in and test; [AlertCopy] for the rest. */
    private fun privateWords(alert: CodingAlert, publicTitle: String): Pair<String, String> {
        when (alert.kind) {
            AlertKind.Quota -> return words.text(R.string.notify_alert_quota_title) to words.text(R.string.notify_alert_quota_body)
            AlertKind.QuotaReset -> {
                val title =
                    if (CodingAlert.AGENT_NAME.matches(alert.agentName)) {
                        words.text(R.string.notify_alert_quota_reset_title, alert.agentName)
                    } else {
                        words.text(R.string.notify_alert_quota_reset_generic_title)
                    }
                return title to words.text(R.string.notify_alert_quota_reset_body)
            }
            AlertKind.CheckIn ->
                return words.text(R.string.notify_alert_checkin_title, alert.safeAgentName) to
                    words.text(R.string.notify_alert_checkin_body)
            AlertKind.Test -> return words.text(R.string.notify_test_title) to words.text(R.string.notify_test_body)
            else -> Unit
        }
        val copy = AlertCopy.of(alert) ?: return publicTitle to ""
        val title =
            when (copy.title) {
                AlertCopy.Title.Literal -> copy.titleArg
                AlertCopy.Title.Run -> words.text(R.string.notify_ask_run, copy.titleArg)
                AlertCopy.Title.Edit -> words.text(R.string.notify_ask_edit, copy.titleArg)
                AlertCopy.Title.Read -> words.text(R.string.notify_ask_read, copy.titleArg)
                AlertCopy.Title.Folder -> words.text(R.string.notify_ask_folder, copy.titleArg)
                AlertCopy.Title.Ask -> words.text(R.string.notify_alert_permission_title, alert.safeAgentName)
                AlertCopy.Title.Question -> words.text(R.string.notify_alert_question_title, alert.safeAgentName)
                AlertCopy.Title.Finished -> words.text(R.string.notify_alert_complete_title, copy.titleArg)
                AlertCopy.Title.FinishedAny -> words.text(R.string.notify_public_finished)
                AlertCopy.Title.Stopped -> words.text(R.string.notify_alert_error_title, copy.titleArg)
                AlertCopy.Title.StoppedAny -> words.text(R.string.notify_alert_stopped_any)
            }
        // A stopped chat says why in a plain sentence from a reason token, never the server's text (B-SEC-104).
        val text = if (alert.kind == AlertKind.Error) words.text(reasonRes(alert.reason)) else copy.body
        return title to text
    }

    private fun publicVersion(channel: NotifyChannel, title: String): Notification =
        NotificationCompat.Builder(words.localized(), channel.id)
            .setSmallIcon(R.drawable.notify_ic_status)
            .setContentTitle(title)
            .build()

    private fun category(kind: AlertKind): String =
        when (kind) {
            AlertKind.Permission, AlertKind.Question -> NotificationCompat.CATEGORY_RECOMMENDATION
            AlertKind.Error -> NotificationCompat.CATEGORY_ERROR
            else -> NotificationCompat.CATEGORY_STATUS
        }

    /**
     * Opens the app at the exact request or chat: the body tap and the Open button. An activity `PendingIntent`, so
     * the person's own tap starts it (no trampoline, plan 11 §10.6).
     */
    private fun openIntent(
        requestCode: Int,
        kind: AlertKind,
        session: String,
        profile: String,
        request: String,
        monitorToken: String,
    ): PendingIntent {
        val open =
            Intent().apply {
                component = FrozenComponents.of(context, FrozenComponents.MAIN_ACTIVITY)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(FrozenExtras.EXTRA_CODING_ALERT_KIND, kind.wire)
                putExtra(FrozenExtras.EXTRA_CODING_ALERT_SESSION_ID, session)
                putExtra(FrozenExtras.EXTRA_CODING_ALERT_PROFILE_ID, profile)
                if (request.isNotEmpty()) putExtra(FrozenExtras.EXTRA_CODING_ALERT_REQUEST_ID, request)
                if (monitorToken.isNotEmpty()) putExtra(FrozenExtras.EXTRA_MONITOR_TOKEN, monitorToken)
            }
        return PendingIntent.getActivity(context, requestCode, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Fixed-copy buttons; the intents carry only routing identifiers, never request content. */
    private fun action(alert: CodingAlert, button: AlertButton, notificationId: Int, index: Int): NotificationCompat.Action {
        val label =
            when (button.label) {
                AlertButton.Label.AllowOnce -> words.text(R.string.notify_allow_once)
                AlertButton.Label.Reject -> words.text(R.string.notify_reject)
                AlertButton.Label.Open -> words.text(R.string.notify_open)
            }
        val pending =
            if (button.decision == ShadeDecision.Open) {
                openIntent(notificationId, alert.kind, alert.sessionId, alert.profileId, alert.requestId, alert.monitorToken)
            } else {
                PendingIntent.getBroadcast(
                    context,
                    NotificationIds.actionRequestCode(notificationId, index),
                    decisionIntent(alert, button.decision),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
        return NotificationCompat.Action.Builder(R.drawable.notify_ic_status, label, pending)
            // A lock-screen tap can authorize code execution on the server host: Android 12+ asks to unlock first.
            // On API 26–30 the body checks the lock itself (CR16-7).
            .setAuthenticationRequired(button.authRequired)
            .build()
    }

    private fun decisionIntent(alert: CodingAlert, decision: ShadeDecision): Intent =
        Intent().apply {
            component = FrozenComponents.of(context, FrozenComponents.CODING_ACTION_RECEIVER)
            action = "${FrozenExtras.ACTION_CODING_ALERT}.${decision.wire}"
            data = FrozenExtras.codingAlertUri(alert.key, decision.wire)
            putExtra(FrozenExtras.EXTRA_CODING_ALERT_KIND, alert.kind.wire)
            putExtra(FrozenExtras.EXTRA_CODING_ALERT_SESSION_ID, alert.sessionId)
            putExtra(FrozenExtras.EXTRA_CODING_ALERT_KEY, alert.key)
            putExtra(FrozenExtras.EXTRA_CODING_ALERT_PROFILE_ID, alert.profileId)
            putExtra(FrozenExtras.EXTRA_CODING_ALERT_DECISION, decision.wire)
            // The exact pending request this notification represents; no other request is ever answered with it.
            putExtra(FrozenExtras.EXTRA_CODING_ALERT_REQUEST_ID, alert.requestId)
        }

    /**
     * One summary per group ("3 need you", "3 conversations finished") while two or more of its alerts show; none for a
     * single alert. Its lines are the children's own (≤ 5). A summary is cosmetic: a failure leaves the alerts alone.
     */
    private fun refreshSummary(group: String) {
        val needs = group == AlertGroups.NEEDS_YOU
        val summaryId = if (needs) NotificationIds.NEEDS_YOU_SUMMARY else NotificationIds.FINISHED_SUMMARY
        val children =
            manager.activeNotifications.filter {
                it.packageName == context.packageName && it.notification.group == group &&
                    (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
            }
        if (children.size < 2) {
            manager.cancel(summaryId)
            return
        }
        val title =
            words.plural(if (needs) R.plurals.notify_group_need_you else R.plurals.notify_group_finished, children.size, children.size)
        val channel = if (needs) NotifyChannel.CodingAction else NotifyChannel.CodingStatus
        val inbox = NotificationCompat.InboxStyle().setBigContentTitle(title)
        children.take(MAX_SUMMARY_LINES).forEach { child ->
            val extras = child.notification.extras
            val line = extras.getString(FrozenExtras.EXTRA_SUMMARY_LINE) ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            if (line.isNotBlank()) inbox.addLine(line)
        }
        val open =
            PendingIntent.getActivity(
                context,
                summaryId,
                Intent().apply {
                    component = FrozenComponents.of(context, FrozenComponents.MAIN_ACTIVITY)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val summary =
            base(channel, title, "", open)
                .setOnlyAlertOnce(true)
                .setGroup(group)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setStyle(inbox)
                .setPublicVersion(publicVersion(channel, "OpenCode · $title"))
                .build()
        post(summaryId, summary)
    }

    internal companion object {
        private val shadeLock = Any()
        const val FINISHED_TIMEOUT_MS = 60 * 60 * 1000L
        const val MAX_SUBTEXT = 120
        const val MAX_SUMMARY_LINES = 5

        fun reasonRes(reason: StopReason): Int =
            when (reason) {
                StopReason.Auth -> R.string.notify_alert_reason_auth
                StopReason.Context -> R.string.notify_alert_reason_context
                StopReason.Length -> R.string.notify_alert_reason_length
                StopReason.Filter -> R.string.notify_alert_reason_filter
                StopReason.Model -> R.string.notify_alert_reason_model
                StopReason.Other -> R.string.notify_alert_reason_other
            }
    }
}
