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
 * Keeps the app alive while a phone setup job runs (SetupRunner.kt).
 *
 * The job is a thread and child processes of the app's process; without a
 * foreground service Android may reclaim the process as soon as the person
 * switches apps, minutes into a download. The ongoing notification mirrors
 * the overall percent and, when tapped, opens the app on the setup progress
 * screen (the launch action [LAUNCH_ACTION_PROGRESS], which MainActivity
 * hands to Dart like a launcher shortcut). When the job ends the service
 * stops and leaves one ordinary notification saying how it went: a stopped
 * job's opens the progress screen too (it offers Continue), a finished
 * job's opens the app on the phone server ([LAUNCH_ACTION_DONE]).
 *
 * Every text comes from the app, already in the person's language.
 */
class SetupService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val channel = intent?.getStringExtra(EXTRA_CHANNEL)
                ?: NativeStrings.get(this, R.string.native_setup_channel)
            val title = intent?.getStringExtra(EXTRA_TITLE) ?: ""
            val text = intent?.getStringExtra(EXTRA_TEXT) ?: ""
            createChannel(this, channel)
            val notification = build(this, title, text, ongoing = true, LAUNCH_ACTION_PROGRESS)
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
            // The job may have ended before Android ran this method. Its stop
            // waited for the notification (a promised foreground service that
            // stops first crashes the app); stop now that the promise is kept.
            if (stopPending) {
                stopPending = false
                try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) { }
                try { stopSelf() } catch (_: Throwable) { }
            }
        } catch (_: Throwable) {
            // Foreground-service/notification policy failures happen here on
            // Android's main thread, after the channel's worker has replied.
            // End setup instead of running it without its promised lifetime.
            try {
                Thread({
                    try { SetupRunner.get(applicationContext).cancel() }
                    catch (_: Throwable) { }
                }, "oc-setup-policy-stop").start()
            } catch (_: Throwable) { }
            try { stopSelf() } catch (_: Throwable) { }
        }
        // Not sticky: a restarted service without the process's job thread
        // would only show a stale percent.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // A destroyed service owes Android nothing; the next start decides.
        foregroundShown = false
        stopPending = false
        promised = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "opencode_phone_setup"
        private const val NOTIFICATION_ID = 4098
        // Its own id: the service's notification goes away when the service
        // stops, which could otherwise take this one with it.
        private const val RESULT_NOTIFICATION_ID = 4099
        private const val EXTRA_CHANNEL = "channel"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"

        // Whitelisted by MainActivity.LAUNCH_ACTIONS; Dart routes them
        // (LaunchAction.phoneSetup / phoneSetupDone).
        const val LAUNCH_ACTION_PROGRESS = "phone_setup"
        const val LAUNCH_ACTION_DONE = "phone_setup_done"

        fun start(context: Context, channel: String, title: String, text: String) {
            context.getSystemService(NotificationManager::class.java)?.cancel(RESULT_NOTIFICATION_ID)
            val intent = Intent(context, SetupService::class.java)
                .putExtra(EXTRA_CHANNEL, channel)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TEXT, text)
            foregroundShown = false
            stopPending = false
            promised = false
            // While the app is on screen a plain start is allowed and makes no
            // promise; onStartCommand still moves the service to the
            // foreground. Only a start from the background promises Android a
            // notification within seconds, which [stop] must then respect.
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

        // Written on the main thread (onStartCommand) and the job thread.
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

        /** Replaces the ongoing notification's text; the service keeps running. */
        fun update(context: Context, channel: String, title: String, text: String) {
            try {
                createChannel(context, channel)
                context.getSystemService(NotificationManager::class.java)
                    ?.notify(NOTIFICATION_ID, build(context, title, text, ongoing = true, LAUNCH_ACTION_PROGRESS))
            } catch (_: Throwable) { /* Notifications are best effort. */ }
        }

        fun stop(context: Context) {
            // A promised start that hasn't shown its notification yet must not
            // be stopped: Android crashes the app for that. onStartCommand
            // stops it right after the notification appears.
            if (promised && !foregroundShown) {
                stopPending = true
                return
            }
            try { context.stopService(Intent(context, SetupService::class.java)) }
            catch (_: Throwable) { }
        }

        /**
         * Stops the service and leaves [text] as a plain, dismissable
         * notification; [done] says whether the job finished or stopped.
         */
        fun finish(context: Context, channel: String, text: String, done: Boolean) {
            try {
                stop(context)
                createChannel(context, channel)
                val action = if (done) LAUNCH_ACTION_DONE else LAUNCH_ACTION_PROGRESS
                context.getSystemService(NotificationManager::class.java)
                    ?.notify(RESULT_NOTIFICATION_ID, build(context, text, null, ongoing = false, action))
            } catch (_: Throwable) { /* Notifications are best effort. */ }
        }

        private fun createChannel(context: Context, name: String) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
        }

        private fun build(
            context: Context,
            title: String,
            text: String?,
            ongoing: Boolean,
            action: String,
        ): Notification {
            // One request code per action: PendingIntents that differ only
            // in extras are the same PendingIntent to Android, so sharing a
            // code would let one notification carry the other's action.
            // UPDATE_CURRENT keeps an older build's intent from lingering.
            val open = PendingIntent.getActivity(
                context,
                if (action == LAUNCH_ACTION_DONE) 3 else 2,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(MainActivity.EXTRA_LAUNCH_ACTION, action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
            return builder
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .apply { if (!text.isNullOrEmpty()) setContentText(text) }
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
                .setAutoCancel(!ongoing)
                .setContentIntent(open)
                .build()
        }
    }
}
