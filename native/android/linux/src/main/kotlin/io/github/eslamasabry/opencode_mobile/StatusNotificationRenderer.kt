package io.github.eslamasabry.opencode_mobile

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build

internal object StatusNotificationRenderer {
    private const val OPEN_REQUEST = 10
    private const val START_REQUEST = 11
    private const val NEW_CHAT_REQUEST = 12
    private const val STOP_REPLY_REQUEST = 13

    private fun openIntent(context: Context, requestCode: Int, launch: String? = null): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (launch != null) putExtra(MainActivity.EXTRA_LAUNCH_ACTION, launch)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    internal fun build(context: Context, content: StatusContent, ongoing: Boolean): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, StatusNotification.CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(content.title)
            .setContentText(content.text.ifEmpty { content.lines.firstOrNull().orEmpty() })
            .setContentIntent(openIntent(context, 0))
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            // Chat titles and task lines are the person's own words: hidden on
            // the lock screen, which shows only counts.
            .setVisibility(Notification.VISIBILITY_PRIVATE)
        if (content.subText.isNotEmpty()) builder.setSubText(content.subText)
        if (content.lines.isNotEmpty()) {
            builder.setStyle(
                Notification.InboxStyle().apply {
                    setBigContentTitle(content.title)
                    content.lines.forEach { addLine(it) }
                },
            )
        }
        // A bar only when a real fraction is known: never an endless spinner.
        content.progress?.let { builder.setProgress(StatusContent.MAX_PROGRESS, it, false) }
        for (action in content.actions) {
            actionFor(context, action, content)?.let { builder.addAction(it) }
        }
        publicVersion(context, content.publicTitle)?.let { builder.setPublicVersion(it) }
        configurePromotion(context, content, builder)
        return builder.build()
    }

    private fun publicVersion(context: Context, title: String): Notification? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return Notification.Builder(context, StatusNotification.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .build()
    }

    private fun actionFor(context: Context, action: StatusAction, content: StatusContent): Notification.Action? {
        val icon = Icon.createWithResource(context, R.mipmap.ic_launcher)
        fun label(id: Int) = NativeStrings.get(context, id)
        return when (action) {
            StatusAction.OPEN -> Notification.Action.Builder(
                icon, label(R.string.native_status_action_open), openIntent(context, OPEN_REQUEST),
            ).build()
            StatusAction.START -> Notification.Action.Builder(
                icon, label(R.string.native_status_action_start), openIntent(context, START_REQUEST),
            ).build()
            StatusAction.NEW_CHAT -> Notification.Action.Builder(
                icon, label(R.string.native_status_action_new_chat), openIntent(context, NEW_CHAT_REQUEST, "new_task"),
            ).build()
            StatusAction.STOP_REPLY -> Notification.Action.Builder(
                icon, label(R.string.native_status_action_stop_reply),
                PendingIntent.getBroadcast(
                    context,
                    STOP_REPLY_REQUEST,
                    Intent(context, StatusActionReceiver::class.java).apply {
                        this.action = StatusNotification.ACTION_STATUS
                        data = android.net.Uri.parse("opencode://status/stop_reply")
                        putExtra(StatusNotification.EXTRA_STATUS_ACTION, StatusAction.STOP_REPLY.wire)
                        putExtra(StatusNotification.EXTRA_STATUS_SESSION, content.sessionId)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            ).build()
            StatusAction.STOP_SERVER -> Notification.Action.Builder(
                icon, label(R.string.native_status_action_stop_opencode),
                BuiltinServerService.stopIntent(context),
            ).build()
        }
    }

    private fun configurePromotion(context: Context, content: StatusContent, builder: Notification.Builder) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
            Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1
        ) {
            // Android 16 live updates: the status bar chip while chats work.
            val count = Regex("^\\d+").find(content.title)?.value?.toIntOrNull()
            val chip = when (content.kind) {
                StatusKind.WORKING ->
                    NativeStrings.quantity(context, R.plurals.native_chip_running, 1, 1)
                StatusKind.SEVERAL_WORKING -> count?.let {
                    NativeStrings.quantity(context, R.plurals.native_chip_running, it, it)
                }
                StatusKind.NEEDS_YOU -> count?.let {
                    NativeStrings.quantity(context, R.plurals.native_chip_pending, it, it)
                }
                else -> null
            }
            builder.setRequestPromotedOngoing(chip != null)
            builder.setShortCriticalText(chip)
        }
    }
}
