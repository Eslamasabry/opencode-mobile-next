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
import java.util.concurrent.atomic.AtomicReference

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
        return try {
            startOrStopRuntime(intent, startId)
        } catch (_: Throwable) {
            refuseStart(intent, startId)
            START_NOT_STICKY
        }
    }

    private fun refuseStart(intent: Intent?, startId: Int) {
        // Event dispatch denial must not turn saved wanted intent into user Stop.
        if (intent?.action == ACTION_RESTORE) {
            cancelPendingRestore(applicationContext)
            try { BuiltinLinux.get(applicationContext).settleServerEventService() } catch (_: Throwable) { }
            if (!foregroundShown) try { stopSelf(startId) } catch (_: Throwable) { }
        } else {
            // Policy rejection cannot throw through the caller's channel guard.
            stopRuntime(startId)
        }
    }

    private fun startOrStopRuntime(intent: Intent?, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRuntime(startId)
            return START_NOT_STICKY
        }
        // One shared status row: the live connection service uses the same ID,
        // so there is never a second "always on" notification.
        val notification = StatusNotification.begin(
            this,
            StatusNotification.OWNER_SERVER,
            intent?.getStringExtra(EXTRA_TITLE),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                StatusNotification.ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(StatusNotification.ID, notification)
        }
        foregroundShown = true
        // A promised foreground service must show its notification before stopping.
        return if (stopPending) {
            stopPending = false
            releaseStatus()
            try { stopSelf(startId) } catch (_: Throwable) { }
            START_NOT_STICKY
        } else restoreRuntimeForStart(intent, startId)
    }

    private fun restoreRuntimeForStart(intent: Intent?, startId: Int): Int {
        val linux = BuiltinLinux.get(applicationContext)
        if (intent?.action == ACTION_RESTORE) {
            val ticket = pendingRestore.getAndSet(null)
            val accepted = ticket != null && linux.restoreServerAfterSystemEvent(ticket)
            if (!accepted) {
                if (ticket != null) linux.cancelServerEventRestore(ticket)
                linux.settleServerEventService()
            }
            // A later null-intent reclaim still passes the strict saved-recipe gates.
            // START_STICKY does not redeliver this single-use event Intent.
            return if (accepted) START_STICKY else START_NOT_STICKY
        }
        return if (intent == null && !linux.serverRestorationArmed) {
            linux.rejectServerRestoration()
            releaseStatus()
            stopSelf(startId)
            START_NOT_STICKY
        } else {
            if (intent == null) linux.restoreServerAfterProcessReclaim()
            if (linux.serverRestorationArmed) START_STICKY else START_NOT_STICKY
        }
    }

    // No foreground service has an unbounded lifetime, including specialUse.
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopRuntime(startId, "systemTimeout")
        releaseStatus()
        // Android gives only a few seconds: tree shutdown must not delay revocation.
        try { stopSelf(startId) } catch (_: Throwable) { }
    }

    private fun stopRuntime(startId: Int, reason: String = "stopped") {
        cancelPendingRestore(applicationContext)
        try {
            val linux = BuiltinLinux.get(applicationContext)
            linux.revokeForegroundWork()
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

    /**
     * Gives up this service's share of the one status row: only the last
     * owner removes it, otherwise it stays for the live connection.
     */
    private fun releaseStatus() {
        try {
            val last = StatusNotification.end(this, StatusNotification.OWNER_SERVER)
            stopForeground(if (last) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
        } catch (_: Throwable) { }
    }

    override fun onDestroy() {
        StatusNotification.end(this, StatusNotification.OWNER_SERVER)
        cancelPendingRestore(applicationContext)
        try { BuiltinLinux.get(applicationContext).revokeForegroundWork() } catch (_: Throwable) { }
        // A destroyed service owes Android nothing; the next start decides.
        foregroundShown = false
        stopPending = false
        promised = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ACTION_STOP = "stop"
        private const val ACTION_RESTORE = "io.github.eslamasabry.opencode_mobile.RESTORE_SERVER_EVENT"
        private val pendingRestore = AtomicReference<NativeServerRestoreTicket?>()

        private fun cancelPendingRestore(context: Context) {
            val ticket = pendingRestore.getAndSet(null) ?: return
            try { BuiltinLinux.get(context).cancelServerEventRestore(ticket) } catch (_: Throwable) { }
        }
        private const val EXTRA_TITLE = "title"

        /** Native system-event entry point; the capability never leaves this process. */
        internal fun startForRestore(context: Context, ticket: NativeServerRestoreTicket): Boolean {
            if (!pendingRestore.compareAndSet(null, ticket)) return false
            stopPending = false
            promised = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            return try {
                val intent = Intent(context, BuiltinServerService::class.java).setAction(ACTION_RESTORE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
                true
            } catch (_: Throwable) {
                pendingRestore.compareAndSet(ticket, null)
                if (!foregroundShown) promised = false
                false
            }
        }

        /** The status row's "Stop OpenCode" button. */
        internal fun stopIntent(context: Context): PendingIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, BuiltinServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

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
            cancelPendingRestore(context)
            if (promised && !foregroundShown) {
                stopPending = true
                return
            }
            foregroundShown = false
            promised = false
            context.stopService(Intent(context, BuiltinServerService::class.java))
        }

        // Written on the main thread (onStartCommand) and callers' threads.
        internal val isForegroundRunning: Boolean get() = foregroundShown
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
