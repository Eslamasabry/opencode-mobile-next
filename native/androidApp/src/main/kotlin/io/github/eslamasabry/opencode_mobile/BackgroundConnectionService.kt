package io.github.eslamasabry.opencode_mobile

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.eslamasabry.oc.android.notifications.ChannelRegistry
import io.github.eslamasabry.oc.android.notifications.NotificationIds
import io.github.eslamasabry.oc.android.notifications.NotifyChannel
import io.github.eslamasabry.oc.android.notifications.NotifyWords
import io.github.eslamasabry.oc.android.notifications.R as NotifyR
import io.github.eslamasabry.opencode_mobile.graph.backgroundHub
import io.github.eslamasabry.opencode_mobile.graph.platformHub
import kotlinx.coroutines.launch

/**
 * The live background connection's foreground service, at the Flutter app's frozen FQCN (plan 11
 * §2.2), so the Flutter-era pause receipt and the OS's own records of it carry over. A thin shell:
 * the foreground promise is kept here (`dataSync`, the shared status row 4747), everything else is
 * `:android:background`'s [LiveServiceBody] (unit BUILD-ANDROID-BG-PLATFORM). Android 15+ caps
 * `dataSync` at 6 h per 24 h: [onTimeout] saves the pause first.
 */
class BackgroundConnectionService : Service() {
    private var widgetWork: kotlinx.coroutines.Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (
            intent?.action ==
                io.github.eslamasabry.oc.android.widgets.BackgroundConnectionService
                    .ACTION_WIDGET_ACTION
        ) {
            // The first tap owns this service lease; another intent cannot replace or stop its job.
            if (widgetWork?.isActive == true) return START_NOT_STICKY
            val raw =
                intent.getStringExtra("oc.widgetPayload")
                    ?: return START_NOT_STICKY.also { stopSelf(startId) }
            // A launcher tap gets a short bounded promise, without enabling the persistent live
            // connection.
            val hub = platformHub
            val promoted =
                try {
                    val type =
                        if (backgroundHub.signals.active && Build.VERSION.SDK_INT >= 29)
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        else if (Build.VERSION.SDK_INT >= 34)
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
                        else 0
                    ServiceCompat.startForeground(
                        this,
                        NotificationIds.STATUS,
                        row(widget = !backgroundHub.signals.active),
                        type,
                    )
                    true
                } catch (_: IllegalStateException) {
                    false
                } catch (_: SecurityException) {
                    false
                }
            widgetWork =
                hub.scope.launch {
                    try {
                        if (promoted) hub.runWidgetAction(raw)
                    } finally {
                        if (!backgroundHub.signals.active) {
                            ServiceCompat.stopForeground(
                                this@BackgroundConnectionService,
                                ServiceCompat.STOP_FOREGROUND_REMOVE,
                            )
                            stopSelf(startId)
                        }
                    }
                }
            return START_NOT_STICKY
        }
        val hub = backgroundHub
        val promised =
            try {
                val type =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    else 0
                ServiceCompat.startForeground(this, NotificationIds.STATUS, row(), type)
                true
            } catch (_: IllegalStateException) {
                false // ForegroundServiceStartNotAllowedException and the API 34+ type checks
            } catch (_: SecurityException) {
                false
            }
        hub.launchOnMain {
            if (!hub.serviceBody.onForegroundResult(promised)) {
                ServiceCompat.stopForeground(
                    this@BackgroundConnectionService,
                    ServiceCompat.STOP_FOREGROUND_REMOVE,
                )
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        if (
            Build.VERSION.SDK_INT >= 34 &&
                fgsType == ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
        ) {
            widgetWork?.cancel()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return
        }
        val hub = backgroundHub
        hub.launchOnMain {
            hub.serviceBody.onTimeout()
            ServiceCompat.stopForeground(
                this@BackgroundConnectionService,
                ServiceCompat.STOP_FOREGROUND_REMOVE,
            )
            stopSelf()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        backgroundHub.serviceBody.onTaskRemoved()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        widgetWork?.cancel()
        backgroundHub.serviceBody.onDestroy()
        super.onDestroy()
    }

    /**
     * The connected row: the app's name and that it stays live; a tap opens the app (no
     * trampoline).
     */
    private fun row(widget: Boolean = false): Notification {
        val words = NotifyWords(this) { platformHub.appLocaleTag }
        ChannelRegistry(getSystemService(android.app.NotificationManager::class.java), words)
            .ensure()
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return NotificationCompat.Builder(this, NotifyChannel.LiveConnection.id)
            .setSmallIcon(NotifyR.drawable.notify_ic_status)
            .setContentTitle(
                words.text(
                    if (widget) NotifyR.string.notify_widget_action
                    else NotifyR.string.notify_live_channel
                )
            )
            .setContentText(
                words.text(
                    if (widget) NotifyR.string.notify_widget_action_description
                    else NotifyR.string.notify_live_description
                )
            )
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
