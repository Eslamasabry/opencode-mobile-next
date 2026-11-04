package io.github.eslamasabry.oc.android.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri

/**
 * Draws the one status row (plan 11 §10.3, §10.5). Port of `StatusNotificationRenderer` with the
 * native changes:
 *
 * - **Live Updates**: the Compat calls are made unconditionally and androidx.core decides per SDK
 *   (no `SDK_INT_FULL >= BAKLAVA_1` gate). A promoted row is ongoing, has a title, no custom views,
 *   is not colorized and not a group summary, and uses the standard or `BigTextStyle` — never
 *   `InboxStyle`, which Android does not promote: lines are joined by newlines in the same order
 *   (same ≤ 4 lines).
 * - Lock screen: `VISIBILITY_PRIVATE` with a count-only public version.
 * - Dismissal: `setDeleteIntent` tells `StatusActionReceiver` the person swiped the row away
 *   (S11-9).
 */
public class StatusRenderer(private val context: Context, private val words: NotifyWords) {
    public fun build(content: StatusContent, ongoing: Boolean, chip: LiveChip?): Notification {
        val localized = words.localized()
        val builder =
            NotificationCompat.Builder(localized, NotifyChannel.LiveConnection.id)
                .setSmallIcon(R.drawable.notify_ic_status)
                .setContentTitle(content.title)
                .setContentText(content.text.ifEmpty { content.lines.firstOrNull().orEmpty() })
                .setContentIntent(openIntent(CONTENT_REQUEST, null, content))
                .setOngoing(ongoing)
                .setAutoCancel(!ongoing)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(
                    NotificationCompat.Builder(localized, NotifyChannel.LiveConnection.id)
                        .setSmallIcon(R.drawable.notify_ic_status)
                        .setContentTitle(content.publicTitle)
                        .build()
                )
                .setDeleteIntent(dismissIntent(content))
        if (content.subText.isNotEmpty()) builder.setSubText(content.subText)
        if (content.lines.isNotEmpty()) {
            if (chip != null) {
                builder.setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(content.title)
                        .bigText(content.lines.joinToString("\n"))
                )
            } else {
                builder.setStyle(
                    NotificationCompat.InboxStyle().setBigContentTitle(content.title).also { inbox
                        ->
                        content.lines.forEach(inbox::addLine)
                    }
                )
            }
        }
        // A bar only when a real fraction is known: never an endless spinner (B-ANDR-042).
        content.progress?.let { builder.setProgress(StatusContent.MAX_PROGRESS, it, false) }
        content.actions.forEach { builder.addAction(action(it, content)) }
        if (chip != null && ongoing) {
            builder.setRequestPromotedOngoing(true)
            builder.setShortCriticalText(words.plural(chip.pluralRes, chip.count, chip.count))
        }
        return builder.build()
    }

    private fun openIntent(
        requestCode: Int,
        launch: String?,
        content: StatusContent,
    ): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent().apply {
                component =
                    if (requestCode == START_REQUEST)
                        android.content.ComponentName(
                            context.packageName,
                            "io.github.eslamasabry.opencode_mobile.IdleStartActivity",
                        )
                    else FrozenComponents.of(context, FrozenComponents.MAIN_ACTIVITY)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                data = targetUri("opencode://status/open", content)
                putStatusConversation(content.conversation)
                // EntryRouter already understands the frozen conversation routing extras.
                content.conversation?.let {
                    putExtra(FrozenExtras.EXTRA_CODING_ALERT_PROFILE_ID, it.profileId.raw)
                    putExtra(FrozenExtras.EXTRA_CODING_ALERT_SESSION_ID, it.sessionId.raw)
                }
                if (launch != null) putExtra(FrozenExtras.EXTRA_LAUNCH_ACTION, launch)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * Extras do not participate in PendingIntent identity; keep older delivered actions on their
     * own chat.
     */
    private fun targetUri(base: String, content: StatusContent): android.net.Uri =
        base
            .toUri()
            .buildUpon()
            .appendQueryParameter("profile", content.conversation?.profileId?.raw.orEmpty())
            .appendQueryParameter("session", content.conversation?.sessionId?.raw.orEmpty())
            .build()

    private fun dismissIntent(content: StatusContent): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            DISMISS_REQUEST,
            Intent(FrozenExtras.ACTION_STATUS).apply {
                component = FrozenComponents.of(context, FrozenComponents.STATUS_ACTION_RECEIVER)
                // A previously delivered delete intent keeps its own turn when this row is
                // replaced.
                data =
                    FrozenExtras.STATUS_DISMISSED_URI.toUri()
                        .buildUpon()
                        .appendPath(content.conversation?.profileId?.raw.orEmpty())
                        .appendPath(content.conversation?.sessionId?.raw.orEmpty())
                        .appendPath(content.turnId)
                        .build()
                putExtra(FrozenExtras.EXTRA_STATUS_ACTION, DISMISSED_ACTION)
                putStatusConversation(content.conversation)
                putExtra(FrozenExtras.EXTRA_STATUS_TURN, content.turnId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun action(action: StatusAction, content: StatusContent): NotificationCompat.Action {
        val icon = R.drawable.notify_ic_status
        return when (action) {
            StatusAction.Open ->
                NotificationCompat.Action(
                    icon,
                    words.text(R.string.notify_status_action_open),
                    openIntent(OPEN_REQUEST, null, content),
                )
            StatusAction.Start ->
                NotificationCompat.Action(
                    icon,
                    words.text(R.string.notify_status_action_start),
                    openIntent(START_REQUEST, null, content),
                )
            StatusAction.NewChat ->
                NotificationCompat.Action(
                    icon,
                    words.text(R.string.notify_status_action_new_chat),
                    openIntent(NEW_CHAT_REQUEST, "new_task", content),
                )
            StatusAction.StopReply ->
                NotificationCompat.Action(
                    icon,
                    words.text(R.string.notify_status_action_stop_reply),
                    PendingIntent.getBroadcast(
                        context,
                        STOP_REPLY_REQUEST,
                        Intent(FrozenExtras.ACTION_STATUS).apply {
                            component =
                                FrozenComponents.of(
                                    context,
                                    FrozenComponents.STATUS_ACTION_RECEIVER,
                                )
                            data = targetUri(FrozenExtras.STOP_REPLY_URI, content)
                            putExtra(FrozenExtras.EXTRA_STATUS_ACTION, StatusAction.StopReply.wire)
                            putStatusConversation(content.conversation)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            StatusAction.StopServer ->
                NotificationCompat.Action(
                    icon,
                    words.text(R.string.notify_status_action_stop_opencode),
                    PendingIntent.getService(
                        context,
                        STOP_SERVER_REQUEST,
                        Intent(FrozenExtras.ACTION_STOP_SERVER).apply {
                            component =
                                FrozenComponents.of(context, FrozenComponents.SERVER_SERVICE)
                        },
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
        }
    }

    public companion object {
        /**
         * Request codes copied from the Flutter renderer (content 0, Open 10, Start 11, New chat
         * 12, Stop reply 13).
         */
        public const val CONTENT_REQUEST: Int = 0
        public const val OPEN_REQUEST: Int = 10
        public const val START_REQUEST: Int = 11
        public const val NEW_CHAT_REQUEST: Int = 12
        public const val STOP_REPLY_REQUEST: Int = 13
        public const val DISMISS_REQUEST: Int = 14

        /** `BuiltinServerService.stopIntent` request code. */
        public const val STOP_SERVER_REQUEST: Int = 1
        public const val DISMISSED_ACTION: String = "dismissed"
    }
}
