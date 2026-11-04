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
import io.github.eslamasabry.oc.android.widgets.NativeStrings
import io.github.eslamasabry.oc.android.widgets.R as WidgetR
import io.github.eslamasabry.oc.android.widgets.WidgetActionAdmission
import io.github.eslamasabry.oc.android.widgets.WidgetActionExtras
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.platform.ForegroundKind
import io.github.eslamasabry.oc.core.domain.platform.ForegroundTicket
import io.github.eslamasabry.oc.core.domain.platform.ForegroundWork
import io.github.eslamasabry.opencode_mobile.graph.backgroundHub
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The live background connection's foreground service, at the Flutter app's frozen FQCN (plan 11
 * §2.2), so the Flutter-era pause receipt and the OS's own records of it carry over. A thin shell:
 * the foreground promise is kept here (`dataSync`, the shared status row 4747), everything else is
 * `:android:background`'s [LiveServiceBody] (unit BUILD-ANDROID-BG-PLATFORM). Android 15+ caps
 * `dataSync` at 6 h per 24 h: [onTimeout] saves the pause first.
 */
class BackgroundConnectionService : Service() {
    private var livePromised = false
    private val widgetJobs = mutableSetOf<Job>()
    private var widgetRuns = 0
    private var widgetStartId = 0
    private val widgetForeground = WidgetForeground()
    private val widgetAdmission by lazy { WidgetActionAdmission(this, widgetForeground) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (
            intent?.action ==
                io.github.eslamasabry.oc.android.widgets.BackgroundConnectionService
                    .ACTION_WIDGET_ACTION
        ) {
            val request = WidgetActionExtras.read(intent)
            if (request == null) {
                // Started through getForegroundService: keep the start-foreground promise even for
                // a malformed request, then drop the row at once.
                if (!livePromised) {
                    widgetForeground.promote()
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                }
                if (!hasLiveOwner()) stopSelfResult(startId)
                return START_NOT_STICKY
            }
            widgetStartId = startId
            widgetRuns++
            backgroundHub.launchOnMain {
                val job = currentCoroutineContext()[Job]!!
                widgetJobs += job
                widgetForeground.syncOwners()
                try {
                    widgetAdmission.run(request)
                } finally {
                    widgetJobs -= job
                    widgetRuns--
                    if (widgetRuns == 0 && !hasLiveOwner()) {
                        ServiceCompat.stopForeground(
                            this@BackgroundConnectionService,
                            ServiceCompat.STOP_FOREGROUND_REMOVE,
                        )
                        stopSelfResult(widgetStartId)
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
                livePromised = true
                true
            } catch (_: IllegalStateException) {
                false // ForegroundServiceStartNotAllowedException and the API 34+ type checks
            } catch (_: SecurityException) {
                false
            }
        hub.launchOnMain {
            if (!hub.serviceBody.onForegroundResult(promised)) {
                livePromised = false
                if (widgetRuns == 0) {
                    ServiceCompat.stopForeground(
                        this@BackgroundConnectionService,
                        ServiceCompat.STOP_FOREGROUND_REMOVE,
                    )
                    stopSelf()
                } else widgetForeground.promote()
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        if (
            Build.VERSION.SDK_INT >= 34 &&
                fgsType == ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
        ) {
            expireWidget(startId)
            return
        }
        val hub = backgroundHub
        hub.launchOnMain {
            hub.serviceBody.onTimeout()
            livePromised = false
            ServiceCompat.stopForeground(
                this@BackgroundConnectionService,
                ServiceCompat.STOP_FOREGROUND_REMOVE,
            )
            stopSelf()
        }
    }

    override fun onTimeout(startId: Int) {
        expireWidget(startId)
    }

    private fun expireWidget(startId: Int) {
        widgetJobs.toList().forEach { it.cancel() }
        if (!hasLiveOwner()) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        backgroundHub.serviceBody.onTaskRemoved()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        widgetJobs.toList().forEach { it.cancel() }
        livePromised = false
        backgroundHub.serviceBody.onDestroy()
        super.onDestroy()
    }

    /**
     * The connected row: the app's name and that it stays live; a tap opens the app (no
     * trampoline).
     */
    private fun row(widget: Boolean = false): Notification {
        val words = NotifyWords(this) { null }
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
                if (widget) NativeStrings.get(this, WidgetR.string.widget_label)
                else words.text(NotifyR.string.notify_live_channel)
            )
            .setContentText(
                if (widget) NativeStrings.get(this, WidgetR.string.widget_v2_pending)
                else words.text(NotifyR.string.notify_live_description)
            )
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun hasLiveOwner(): Boolean = livePromised || backgroundHub.signals.active

    /**
     * The widget borrows an existing live promise; otherwise its exact ticket owns only a
     * shortService.
     */
    private inner class WidgetForeground : ForegroundWork {
        private val mutable = MutableStateFlow<ImmutableSet<ForegroundKind>>(persistentSetOf())
        override val active: StateFlow<ImmutableSet<ForegroundKind>> = mutable
        private var next = 0L
        private val tickets = mutableSetOf<Long>()

        fun syncOwners() {
            mutable.value =
                when {
                    // Only this instance's own live promise counts: the live signal alone has not
                    // called startForeground here.
                    livePromised -> persistentSetOf(ForegroundKind.LiveMode)
                    tickets.isNotEmpty() -> persistentSetOf(ForegroundKind.WidgetAction)
                    else -> persistentSetOf()
                }
        }

        fun promote(): Boolean =
            try {
                if (!livePromised) {
                    val type =
                        when {
                            Build.VERSION.SDK_INT >= 34 ->
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
                            Build.VERSION.SDK_INT >= 29 ->
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                            else -> 0
                        }
                    ServiceCompat.startForeground(
                        this@BackgroundConnectionService,
                        4748,
                        row(widget = true),
                        type,
                    )
                }
                true
            } catch (e: IllegalStateException) {
                false
            } catch (e: SecurityException) {
                false
            }

        override suspend fun begin(kind: ForegroundKind): Outcome<ForegroundTicket> {
            if (kind != ForegroundKind.WidgetAction || !promote())
                return Outcome.Failed(Failure.Device())
            val ticket = ForegroundTicket(kind, ++next)
            tickets += ticket.id
            syncOwners()
            return Outcome.Ok(ticket)
        }

        override suspend fun end(ticket: ForegroundTicket) {
            if (ticket.kind != ForegroundKind.WidgetAction || !tickets.remove(ticket.id)) return
            syncOwners()
            if (tickets.isEmpty() && !livePromised) {
                ServiceCompat.stopForeground(
                    this@BackgroundConnectionService,
                    ServiceCompat.STOP_FOREGROUND_REMOVE,
                )
            }
        }
    }
}
