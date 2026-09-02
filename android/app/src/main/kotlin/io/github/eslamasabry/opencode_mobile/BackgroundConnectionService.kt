package io.github.eslamasabry.opencode_mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class BackgroundConnectionService : Service() {
    override fun onCreate() {
        super.onCreate()
        active = true
        createLiveNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildLiveNotification(this, liveStatus)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        active = false
        super.onDestroy()
    }

    /**
     * Android 15+ stops a dataSync foreground service once it has used its
     * daily budget. Stopping is required; staying silent is not. Dart's
     * "keep live in background" preference would otherwise stay true over a
     * service the system has already killed, so the user believes live mode
     * is running while events and terminals are disconnected.
     *
     * The notification goes first, then the push: whatever happens to the
     * channel, the ongoing "session is live" notification must not outlive
     * the session it claims is live.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifyDartOfTimeout()
        stopSelf(startId)
    }

    private fun notifyDartOfTimeout() = notifyDartStopped(REASON_SYSTEM_TIMEOUT)

    private fun createLiveNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Live coding connection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while OpenCode keeps a server session live in the background"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "opencode_live_connection"
        private const val NOTIFICATION_ID = 4747
        private const val ACTION_CHANNEL_ID = "opencode_coding_action"
        private const val STATUS_CHANNEL_ID = "opencode_coding_status"
        private const val CODING_ALERT_ID_BASE = 6000
        private const val CODING_ALERT_GROUP = "opencode_coding_alerts"

        /// Pushed to Dart when Android's foreground-service time limit stops
        /// this service, and when the user taps "Pause background" on the
        /// ongoing notification. Handled in lib/background/live_background.dart.
        const val METHOD_TIMEOUT = "backgroundServiceTimeout"
        const val REASON_SYSTEM_TIMEOUT = "systemTimeout"

        /// Must match `BackgroundLiveController.pauseReason`.
        const val REASON_USER_PAUSE = "userPause"

        const val ACTION_PAUSE_LIVE =
            "io.github.eslamasabry.opencode_mobile.action.PAUSE_LIVE"

        @Volatile
        var active: Boolean = false
            private set

        /** What the ongoing notification currently says; survives restarts
         *  of the service so a re-enabled connection shows the last truth
         *  until Dart pushes a fresh one. */
        @Volatile
        var liveStatus: LiveStatus = LiveStatus()
            private set

        data class LiveStatus(
            val runningCount: Int = 0,
            val pendingCount: Int = 0,
            val title: String? = null,
            val detail: String? = null
        )

        /**
         * Rebuilds the ongoing notification from Dart's session truth. Returns
         * whether a notification was actually refreshed; when the service is
         * not running the status is only remembered for its next start.
         */
        fun updateLiveStatus(
            context: Context,
            runningCount: Int,
            pendingCount: Int,
            title: String?,
            detail: String?
        ): Boolean {
            val status = LiveStatus(
                runningCount = runningCount.coerceAtLeast(0),
                pendingCount = pendingCount.coerceAtLeast(0),
                title = title?.trim()?.takeIf { it.isNotEmpty() },
                detail = detail?.trim()?.takeIf { it.isNotEmpty() }
            )
            if (status == liveStatus && active) return true
            liveStatus = status
            if (!active) return false
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, buildLiveNotification(context, status))
            return true
        }

        /** Tells Dart the service stopped so the persisted preference and the
         *  settings switch follow the service rather than outliving it. */
        fun notifyDartStopped(reason: String) {
            val channel = MainActivity.backgroundChannel ?: return
            // invokeMethod is main-thread only; onTimeout and broadcasts are
            // not guaranteed to arrive there.
            Handler(Looper.getMainLooper()).post {
                channel.invokeMethod(
                    METHOD_TIMEOUT,
                    mapOf(
                        "enabled" to false,
                        "active" to false,
                        "reason" to reason
                    )
                )
            }
        }

        private fun buildLiveNotification(context: Context, status: LiveStatus): Notification {
            val openApp = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val pauseIntent = PendingIntent.getBroadcast(
                context,
                1,
                Intent(context, LivePauseReceiver::class.java).apply {
                    action = ACTION_PAUSE_LIVE
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val running = status.runningCount
            val pending = status.pendingCount
            val title = status.title?.let { "OpenCode · $it" } ?: "OpenCode is connected"
            val text = status.detail ?: when {
                running > 0 && pending > 0 ->
                    "${plural(running, "session")} running · ${plural(pending, "need")} you"
                running > 0 -> "${plural(running, "session")} running"
                pending > 0 -> "${plural(pending, "request")} ${if (pending == 1) "needs" else "need"} you"
                else -> "Connected, nothing running"
            }
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
            builder
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                // Session titles are the user's own words; keep them off the
                // lock screen like every other coding alert.
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, R.mipmap.ic_launcher),
                        "Pause background",
                        pauseIntent
                    ).build()
                )
            if (running > 0) builder.setProgress(0, 0, true)
            if (Build.VERSION.SDK_INT >= 36) {
                // Android 16 live updates: promoted to the status bar chip and
                // pinned atop the shade while something is actually running.
                builder.setRequestPromotedOngoing(true)
                val chip = when {
                    running > 0 -> "$running running"
                    pending > 0 -> "$pending need you"
                    else -> null
                }
                builder.setShortCriticalText(chip)
            }
            return builder.build()
        }

        private fun plural(count: Int, noun: String): String =
            if (noun == "need") count.toString()
            else "$count $noun${if (count == 1) "" else "s"}"

        fun start(context: Context) {
            val intent = Intent(context, BackgroundConnectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BackgroundConnectionService::class.java))
        }

        @Suppress("DEPRECATION")
        fun showCodingAlert(
            context: Context,
            kind: String,
            sessionID: String,
            key: String,
            quickReply: Boolean = false,
            requestID: String = ""
        ): Boolean {
            if (sessionID.isBlank() || key.isBlank()) return false
            val manager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                !manager.areNotificationsEnabled()
            ) {
                return false
            }

            val content = when (kind) {
                "permission" -> CodingAlertContent(
                    channelID = ACTION_CHANNEL_ID,
                    title = "OpenCode needs permission",
                    text = "Tap to review the pending request.",
                    category = Notification.CATEGORY_RECOMMENDATION,
                    priority = Notification.PRIORITY_HIGH
                )
                "question" -> CodingAlertContent(
                    channelID = ACTION_CHANNEL_ID,
                    title = "OpenCode needs your input",
                    text = "Tap to answer the pending question.",
                    category = Notification.CATEGORY_RECOMMENDATION,
                    priority = Notification.PRIORITY_HIGH
                )
                "complete" -> CodingAlertContent(
                    channelID = STATUS_CHANNEL_ID,
                    title = "OpenCode finished",
                    text = "Tap to review the latest result.",
                    category = Notification.CATEGORY_STATUS,
                    priority = Notification.PRIORITY_DEFAULT
                )
                "error" -> CodingAlertContent(
                    channelID = ACTION_CHANNEL_ID,
                    title = "OpenCode session needs attention",
                    text = "Tap to review the session error.",
                    category = Notification.CATEGORY_ERROR,
                    priority = Notification.PRIORITY_HIGH
                )
                else -> return false
            }

            createCodingAlertChannels(manager)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                manager.getNotificationChannel(content.channelID)?.importance ==
                NotificationManager.IMPORTANCE_NONE
            ) {
                return false
            }

            val notificationID = codingAlertNotificationID(key)
            val openApp = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_CODING_ALERT_KIND, kind)
                putExtra(EXTRA_CODING_ALERT_SESSION_ID, sessionID)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationID,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, content.channelID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context).setPriority(content.priority)
            }
            builder
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(content.title)
                .setContentText(content.text)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(content.category)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setGroup(CODING_ALERT_GROUP)
            for (action in codingAlertActions(
                context, kind, sessionID, key, quickReply, requestID, notificationID
            )) {
                builder.addAction(action)
            }
            manager.notify(notificationID, builder.build())
            return true
        }

        // Fixed-copy notification actions; the intents carry only routing
        // identifiers, never request content.
        private fun codingAlertActions(
            context: Context,
            kind: String,
            sessionID: String,
            key: String,
            quickReply: Boolean,
            requestID: String,
            notificationID: Int
        ): List<Notification.Action> {
            fun actionIntent(decision: String): Intent =
                Intent(context, CodingActionReceiver::class.java).apply {
                    action = "$ACTION_CODING_ALERT.$decision"
                    data = android.net.Uri.parse("opencode://coding-alert/$key/$decision")
                    putExtra(EXTRA_CODING_ALERT_KIND, kind)
                    putExtra(EXTRA_CODING_ALERT_SESSION_ID, sessionID)
                    putExtra(EXTRA_CODING_ALERT_KEY, key)
                    putExtra(EXTRA_CODING_ALERT_DECISION, decision)
                    // The exact pending request this notification represents;
                    // Dart refuses to resolve any other request with it.
                    putExtra(EXTRA_CODING_ALERT_REQUEST_ID, requestID)
                }

            val icon = Icon.createWithResource(context, R.mipmap.ic_launcher)
            return when {
                kind == "permission" -> listOf(
                    Notification.Action.Builder(
                        icon,
                        "Allow once",
                        PendingIntent.getBroadcast(
                            context,
                            notificationID * 4 + 1,
                            actionIntent("allow"),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    ).build(),
                    Notification.Action.Builder(
                        icon,
                        "Deny",
                        PendingIntent.getBroadcast(
                            context,
                            notificationID * 4 + 2,
                            actionIntent("deny"),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    ).build()
                )
                kind == "question" && quickReply -> listOf(
                    Notification.Action.Builder(
                        icon,
                        "Reply",
                        PendingIntent.getBroadcast(
                            context,
                            notificationID * 4 + 3,
                            actionIntent("reply"),
                            // RemoteInput requires a mutable PendingIntent.
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        )
                    )
                        .addRemoteInput(
                            RemoteInput.Builder(REMOTE_INPUT_REPLY)
                                .setLabel("Reply")
                                .build()
                        )
                        .build()
                )
                else -> emptyList()
            }
        }

        fun dismissCodingAlert(context: Context, key: String): Boolean {
            if (key.isBlank()) return false
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.cancel(codingAlertNotificationID(key))
            return true
        }

        private fun createCodingAlertChannels(manager: NotificationManager) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val action = NotificationChannel(
                ACTION_CHANNEL_ID,
                "Coding requests",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when OpenCode needs permission or input"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            val status = NotificationChannel(
                STATUS_CHANNEL_ID,
                "Coding session updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when a background OpenCode session finishes"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            manager.createNotificationChannels(listOf(action, status))
        }

        private fun codingAlertNotificationID(key: String): Int =
            CODING_ALERT_ID_BASE + (key.hashCode() and 0x0fffffff)

        private data class CodingAlertContent(
            val channelID: String,
            val title: String,
            val text: String,
            val category: String,
            val priority: Int
        )

        const val EXTRA_CODING_ALERT_KIND =
            "io.github.eslamasabry.opencode_mobile.extra.CODING_ALERT_KIND"
        const val EXTRA_CODING_ALERT_SESSION_ID =
            "io.github.eslamasabry.opencode_mobile.extra.CODING_ALERT_SESSION_ID"
        const val EXTRA_CODING_ALERT_PROFILE_ID =
            "io.github.eslamasabry.opencode_mobile.extra.CODING_ALERT_PROFILE_ID"
        const val EXTRA_CODING_ALERT_KEY =
            "io.github.eslamasabry.opencode_mobile.extra.CODING_ALERT_KEY"
        const val EXTRA_CODING_ALERT_DECISION =
            "io.github.eslamasabry.opencode_mobile.extra.CODING_ALERT_DECISION"
        const val EXTRA_CODING_ALERT_REQUEST_ID =
            "io.github.eslamasabry.opencode_mobile.CODING_ALERT_REQUEST_ID"
        const val ACTION_CODING_ALERT =
            "io.github.eslamasabry.opencode_mobile.action.CODING_ALERT"
        const val REMOTE_INPUT_REPLY = "oc.codingAlertReply"
    }
}
