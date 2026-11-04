package io.github.eslamasabry.opencode_mobile

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import io.github.eslamasabry.oc.android.notifications.NotificationIds
import io.github.eslamasabry.opencode_mobile.graph.attentionRuntime
import io.github.eslamasabry.opencode_mobile.graph.backgroundHub

/**
 * The live background connection's foreground service, at the Flutter app's frozen FQCN (plan 11
 * §2.2), so the Flutter-era pause receipt and the OS's own records of it carry over. A thin shell:
 * the foreground promise is kept here (`dataSync`, the shared status row 4747), everything else is
 * `:android:background`'s [LiveServiceBody] (unit BUILD-ANDROID-BG-PLATFORM). Android 15+ caps
 * `dataSync` at 6 h per 24 h: [onTimeout] saves the pause first.
 */
class BackgroundConnectionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        backgroundHub.serviceBody.onDestroy()
        super.onDestroy()
    }

    /** Uses the same status renderer as subsequent updates; no static row can replace live work. */
    private fun row(): Notification = attentionRuntime.notification()
}
