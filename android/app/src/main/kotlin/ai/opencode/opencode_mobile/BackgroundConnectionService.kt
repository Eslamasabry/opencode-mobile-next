package ai.opencode.opencode_mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

class BackgroundConnectionService : Service() {
    override fun onCreate() {
        super.onCreate()
        active = true
        createLiveNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
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

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

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

    private fun buildNotification(): Notification {
        val openApp = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("OpenCode session is live")
            .setContentText("Keeping server events and terminals connected")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "opencode_live_connection"
        private const val NOTIFICATION_ID = 4747
        private const val ACTION_CHANNEL_ID = "opencode_coding_action"
        private const val STATUS_CHANNEL_ID = "opencode_coding_status"
        private const val CODING_ALERT_ID_BASE = 6000
        private const val CODING_ALERT_GROUP = "opencode_coding_alerts"

        @Volatile
        var active: Boolean = false
            private set

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
            key: String
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
            val notification = builder
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(content.title)
                .setContentText(content.text)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(content.category)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setGroup(CODING_ALERT_GROUP)
                .build()
            manager.notify(notificationID, notification)
            return true
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
    }
}
