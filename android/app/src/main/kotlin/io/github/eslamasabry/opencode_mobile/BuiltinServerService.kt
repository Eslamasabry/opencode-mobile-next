package io.github.eslamasabry.opencode_mobile

import android.app.ActivityManager
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

/**
 * Keeps the app alive while the OpenCode server (and, when it is on, AI Team)
 * runs inside it, together with the native Rust phone project engine.
 *
 * A PRoot child may survive Android reclaiming the app process. A qualified
 * canonical server can be restored only after durable exact ownership proves
 * its old children drained. While a child runs, this service keeps an ongoing notification that
 * says so and offers Stop, which stops them all. It starts with the first
 * service and ends with the last (BuiltinLinux.startService/stopService).
 */
class BuiltinServerService : Service() {
    @Volatile internal var runtimeQaLastStartId: Int = 0
        private set
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (BuildConfig.BUILTIN_RUNTIME_QA) runtimeQaLastStartId = startId
        try {
            if (intent?.action == ACTION_STOP) {
                stopRuntime(startId)
                return START_NOT_STICKY
            }
            createChannel()
            val notification = buildNotification(intent?.getStringExtra(EXTRA_TITLE))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            foregroundShown = true
            // A stop that arrived before Android ran this waited for the
            // notification (stopping a promised service first crashes the app).
            if (stopPending) {
                stopPending = false
                try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) { }
                try { stopSelf(startId) } catch (_: Throwable) { }
                return START_NOT_STICKY
            }
            val linux = BuiltinLinux.get(applicationContext)
            if (intent == null) {
                if (!linux.serverRestorationArmed) {
                    linux.rejectServerRestoration()
                    try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) { }
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                linux.restoreServerAfterProcessReclaim()
            }
            return if (linux.serverRestorationArmed) START_STICKY else START_NOT_STICKY
        } catch (_: Throwable) {
            // Android invokes this after startForegroundService returned. A
            // policy rejection cannot throw through its caller's channel guard.
            stopRuntime(startId)
        }
        return START_NOT_STICKY
    }

    // No foreground service has an unbounded lifetime, including specialUse.
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopRuntime(startId, "systemTimeout")
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) { }
        // Android gives only a few seconds: tree shutdown must not delay revocation.
        try { stopSelf(startId) } catch (_: Throwable) { }
    }

    private fun stopRuntime(startId: Int, reason: String = "stopped") {
        try {
            val linux = BuiltinLinux.get(applicationContext)
            var revision: Long? = null
            try { linux.requestServerStop(reason, includeOther = true, onRevoked = { revision = it }) }
            catch (_: Throwable) { /* Retain the exact invalidation revision even if durable storage failed. */ }
            val capturedRevision = revision
            Thread({
                try {
                    if (capturedRevision != null) linux.stopAllServices(capturedRevision)
                    else linux.drainRevokedRuntimeChildren()
                } catch (_: Throwable) { }
                finally { try { stopSelf(startId) } catch (_: Throwable) { } }
            }, "phone-service-policy-stop").start()
        } catch (_: Throwable) {
            try { stopSelf(startId) } catch (_: Throwable) { }
        }
    }

    override fun onDestroy() {
        // A destroyed service owes Android nothing; the next start decides.
        foregroundShown = false
        stopPending = false
        promised = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                NativeStrings.get(this, R.string.native_phone_server_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = NativeStrings.get(this@BuiltinServerService, R.string.native_phone_server_description)
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(title: String?): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, BuiltinServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title ?: NativeStrings.get(this, R.string.native_phone_server_title))
            .setContentText(NativeStrings.get(this, R.string.native_phone_server_body))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, NativeStrings.get(this, R.string.native_stop), stop).build())
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "opencode_builtin_server"
        private const val NOTIFICATION_ID = 4097
        private const val ACTION_STOP = "stop"
        private const val EXTRA_TITLE = "title"

        /** Starts the service, or updates its notification to [title]. */
        fun start(context: Context, title: String? = null) {
            val intent = Intent(context, BuiltinServerService::class.java)
                .putExtra(EXTRA_TITLE, title)
            stopPending = false
            // On screen, a plain start makes no promise to Android;
            // onStartCommand still moves the service to the foreground. Only a
            // background start promises a notification within seconds.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !appInForeground()) {
                promised = true
                context.startForegroundService(intent)
            } else {
                try {
                    context.startService(intent)
                } catch (_: IllegalStateException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        promised = true
                        context.startForegroundService(intent)
                    }
                }
            }
        }

        fun stop(context: Context) {
            if (promised && !foregroundShown) {
                stopPending = true
                return
            }
            foregroundShown = false
            promised = false
            context.stopService(Intent(context, BuiltinServerService::class.java))
        }

        // Written on the main thread (onStartCommand) and callers' threads.
        @Volatile private var foregroundShown = false
        @Volatile private var stopPending = false
        @Volatile private var promised = false

        private fun appInForeground(): Boolean = try {
            val info = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(info)
            info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        } catch (_: Throwable) {
            false
        }
    }
}
