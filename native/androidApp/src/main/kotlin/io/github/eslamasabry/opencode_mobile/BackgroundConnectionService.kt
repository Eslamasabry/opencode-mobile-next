package io.github.eslamasabry.opencode_mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import io.github.eslamasabry.oc.android.widgets.WidgetActionExtras
import io.github.eslamasabry.oc.android.widgets.BackgroundConnectionService as Frozen
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.domain.platform.ForegroundKind
import io.github.eslamasabry.oc.core.domain.platform.ForegroundTicket
import io.github.eslamasabry.oc.core.domain.platform.ForegroundWork
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.opencode_mobile.graph.GraphOwner
import io.github.eslamasabry.opencode_mobile.graph.WidgetRunnerAccessors
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The frozen-name service shell the home-screen widget's buttons start (`PendingIntent.getForegroundService`, plan 11
 * §11.3 rule 7). It meets the foreground promise first, for every start including a malformed one, then hands the
 * identifier-only request to `WidgetActionAdmission` and stops as soon as the action settles. The admission lease
 * bounds one tap at 30 s; on Android 14+ the service runs as `shortService`, before that as `dataSync`, so it never
 * approaches the Android 15 dataSync time cap. Live mode and reply watch are not hosted here yet.
 */
class BackgroundConnectionService : Service() {
    private val log = AppLog.tagged("WidgetService")
    private val running = mutableMapOf<Int, Job>()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!promote()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val request = intent?.let(WidgetActionExtras::read)
        val runners = (application as? GraphOwner)?.appGraph as? WidgetRunnerAccessors
        if (request == null || runners == null) {
            settle(startId)
            return START_NOT_STICKY
        }
        val wiring = runners.widgetRunners
        val job =
            wiring.scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                try {
                    val result = wiring.admission.run(request)
                    log.i { "widget action ${request.action}: ${(result as? Outcome.Ok)?.value?.status ?: "failed"}" }
                } finally {
                    main.post { settle(startId) }
                }
            }
        synchronized(running) { running[startId] = job }
        job.start()
        return START_NOT_STICKY
    }

    /** Android 15+: the system's time limit for this type ended; stop at once (the lease reads uncertain). */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopAll()
    }

    @Deprecated("Android 14 overload")
    override fun onTimeout(startId: Int) {
        stopAll()
    }

    override fun onDestroy() {
        synchronized(running) {
            running.values.forEach { it.cancel() }
            running.clear()
        }
        super.onDestroy()
    }

    private fun stopAll() {
        synchronized(running) { running.values.forEach { it.cancel() } }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun settle(startId: Int) {
        val idle =
            synchronized(running) {
                running.remove(startId)
                running.isEmpty()
            }
        if (idle) stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    private fun promote(): Boolean =
        try {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, getString(R.string.widget_service_channel), NotificationManager.IMPORTANCE_LOW)
                )
            }
            val notification =
                Notification.Builder(this, CHANNEL)
                    .setSmallIcon(io.github.eslamasabry.oc.android.notifications.R.drawable.notify_ic_status)
                    .setContentTitle(applicationInfo.loadLabel(packageManager))
                    .setContentText(getString(R.string.widget_service_working))
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .setVisibility(Notification.VISIBILITY_SECRET)
                    .build()
            val id = Frozen.WIDGET_ACTION_NOTIFICATION_ID
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                    startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                else -> startForeground(id, notification)
            }
            true
        } catch (_: RuntimeException) {
            log.w { "widget action: foreground refused" }
            false
        }

    private companion object {
        /** The frozen live-connection channel (created by the platform hub at start). */
        const val CHANNEL = "opencode_live_connection"
    }
}

/**
 * The service already holds the foreground for a widget action, so admission never starts another promotion: the
 * shell meets the OS promise and ends it when the action settles.
 */
internal object WidgetServiceForeground : ForegroundWork {
    override val active: StateFlow<ImmutableSet<ForegroundKind>> =
        MutableStateFlow(persistentSetOf(ForegroundKind.WidgetAction))

    override suspend fun begin(kind: ForegroundKind): Outcome<ForegroundTicket> = Outcome.Ok(ForegroundTicket(kind, 0))

    override suspend fun end(ticket: ForegroundTicket) = Unit
}
