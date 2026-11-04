package io.github.eslamasabry.oc.android.background

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.eslamasabry.oc.core.domain.platform.AppPermission
import io.github.eslamasabry.oc.core.domain.platform.PermissionGate
import io.github.eslamasabry.oc.core.domain.platform.PermissionStatus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The process-wide facts the live service shell publishes and the port reads (the Flutter
 * `BackgroundConnectionService.active` / `lastStartFailure` companions). One instance per process,
 * held in the app graph and handed to both [LiveServiceBody] and [AndroidLiveServicePort].
 */
public class LiveServiceSignals {
    /** The service ran `startForeground` and saved a running receipt. */
    @Volatile public var active: Boolean = false

    /**
     * Why the last start did not run, in fixed words ([START_REFUSED], [START_RECEIPT_UNSAVED]);
     * null = none.
     */
    @Volatile public var lastStartFailure: String? = null

    /**
     * The last start request outlived its bounded wait and was reported as not started; a late
     * foreground promise for it must stop instead of running with live mode off and no owner.
     */
    @Volatile public var startAbandoned: Boolean = false

    public companion object {
        public const val START_REFUSED: String = "refused"
        public const val START_RECEIPT_UNSAVED: String = "receiptUnsaved"

        /** No answer within the bounded wait. */
        public const val START_ACK_TIMEOUT: String = "ackTimeout"
    }
}

/**
 * Starts and stops the one live service. The Android binding addresses the frozen shell by its
 * FQCN.
 */
public interface LiveServiceStarter {
    public fun start()

    public fun stop()
}

/**
 * `BackgroundConnectionService` in `:androidApp` at its frozen FQCN (the same service reply watch
 * and sign-in share, plan 11 §2.2); this module never declares a second service. A start may throw
 * on Android 12+ when the app is not allowed to start a foreground service (the port reports
 * `foreground_service_failed`).
 */
public class ComponentLiveServiceStarter(private val context: Context) : LiveServiceStarter {
    override fun start() {
        ContextCompat.startForegroundService(context, intent())
    }

    override fun stop() {
        context.stopService(intent())
    }

    private fun intent(): Intent = Intent().setClassName(context.packageName, SERVICE_FQCN)

    public companion object {
        public const val SERVICE_FQCN: String =
            "io.github.eslamasabry.opencode_mobile.BackgroundConnectionService"
    }
}

/**
 * The Android [LiveServicePort] (port of `MainActivityBackground` + the `oc/background` channel
 * handler): the notification permission first (API 33+), then a service start whose foreground
 * activation is awaited for at most [ackWait] and never retried. One start at a time: a second
 * while one is pending is `permission_in_progress`.
 */
public class AndroidLiveServicePort(
    private val context: Context,
    private val signals: LiveServiceSignals,
    private val receipts: PauseReceiptStore,
    private val permissions: PermissionGate,
    private val starter: LiveServiceStarter = ComponentLiveServiceStarter(context),
    private val ackWait: Duration = 5.seconds,
    private val ackPoll: Duration = 50.milliseconds,
) : LiveServicePort {
    @Volatile private var starting = false

    override suspend fun pause(): ServiceReply<PauseState> =
        ServiceReply.Ok(receipts.status(signals.active))

    override suspend fun enable(): ServiceReply<ServiceStatus> {
        if (starting) return refused(ServiceRefusal.PERMISSION_IN_PROGRESS)
        starting = true
        try {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    permissions.status(AppPermission.Notifications) != PermissionStatus.Granted &&
                    permissions.request(AppPermission.Notifications) != PermissionStatus.Granted
            ) {
                return refused(ServiceRefusal.NOTIFICATION_DENIED)
            }
            signals.lastStartFailure = null
            signals.startAbandoned = false
            try {
                starter.start()
            } catch (_: IllegalStateException) {
                return refused(ServiceRefusal.FOREGROUND_SERVICE_FAILED)
            } catch (_: SecurityException) {
                return refused(ServiceRefusal.FOREGROUND_SERVICE_FAILED)
            }
            // Bounded acknowledgement only: an accepted start request is not a foreground service.
            withTimeoutOrNull(ackWait) {
                while (!signals.active && signals.lastStartFailure == null) delay(ackPoll)
            }
            if (!signals.active) signals.startAbandoned = true
            val status = statusOf(signals.active)
            return ServiceReply.Ok(
                if (signals.active) status
                else
                    status.copy(
                        startFailure =
                            signals.lastStartFailure ?: LiveServiceSignals.START_ACK_TIMEOUT
                    )
            )
        } finally {
            starting = false
        }
    }

    override suspend fun disable(): ServiceReply<ServiceStatus> {
        receipts.clear()
        starter.stop()
        signals.active = false
        return ServiceReply.Ok(statusOf(enabled = false))
    }

    override suspend fun status(): ServiceReply<ServiceStatus> =
        ServiceReply.Ok(statusOf(signals.active))

    override suspend fun requestBatteryOptimizationExemption(): ServiceReply<ServiceStatus> {
        val power = context.getSystemService(PowerManager::class.java)
        if (power != null && !power.isIgnoringBatteryOptimizations(context.packageName)) {
            try {
                context.startActivity(
                    Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"),
                        )
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: RuntimeException) {
                return refused(ServiceRefusal.UNKNOWN)
            }
        }
        return status()
    }

    private suspend fun statusOf(enabled: Boolean): ServiceStatus {
        val power = context.getSystemService(PowerManager::class.java)
        return ServiceStatus(
            enabled = enabled,
            active = signals.active,
            notificationGranted = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            batteryOptimizationIgnored =
                power?.isIgnoringBatteryOptimizations(context.packageName) == true,
            pause = receipts.status(signals.active),
        )
    }

    private fun refused(code: String): ServiceReply<Nothing> =
        ServiceReply.Refused(ServiceRefusal(code))
}

/**
 * The live-mode half of the `BackgroundConnectionService` shell (the Flutter `beginLive`,
 * `onTimeout(startId, type)`, `onTaskRemoved` and `LivePauseReceiver.pause` bodies). The shell
 * keeps the foreground promise itself (`ServiceCompat.startForeground(…, dataSync)` with the
 * `:android:notifications` row, owner `live`) and forwards the outcome here; [onStopped] reaches
 * the [LiveModeController] (`onServiceStopped`) when the app is alive.
 */
public class LiveServiceBody(
    private val signals: LiveServiceSignals,
    private val receipts: PauseReceiptStore,
    private val onStopped: (reason: String, pause: PauseState?) -> Unit,
) {
    /**
     * After the shell's `startForeground`: [promised] false
     * (ForegroundServiceStartNotAllowedException, the API 34+ type checks) is a refused start.
     * Accepted is not enough either: the pause clears only once the running receipt is saved. False
     * tells the shell to release its row and `stopSelf`.
     */
    public suspend fun onForegroundResult(promised: Boolean): Boolean {
        if (!promised) {
            signals.lastStartFailure = LiveServiceSignals.START_REFUSED
            return false
        }
        if (signals.startAbandoned) {
            signals.startAbandoned = false
            return false
        }
        if (!receipts.confirmedStarted(persistOptIn = false)) {
            signals.lastStartFailure = LiveServiceSignals.START_RECEIPT_UNSAVED
            return false
        }
        signals.lastStartFailure = null
        signals.active = true
        return true
    }

    /**
     * `onTimeout(startId, fgsType)` (API 35+, the 6 h `dataSync` budget): save the pause before
     * stopping (neither the engine nor onDestroy is guaranteed afterwards), so an old opt-in never
     * restarts it, then tell the app.
     */
    public suspend fun onTimeout() {
        receipts.timeout()
        signals.active = false
        onStopped(LiveModeController.REASON_SYSTEM_TIMEOUT, receipts.status(active = false))
    }

    /**
     * The notification's (or the tile's) "Pause background": intentional, so the receipt is
     * cleared, not paused.
     */
    public suspend fun onUserPause() {
        receipts.clear()
        signals.active = false
        onStopped(LiveModeController.REASON_USER_PAUSE, null)
    }

    /** A hint only: never replaces a running receipt or classifies a swipe. */
    public fun onTaskRemoved() {
        if (signals.active) receipts.recordTaskRemoved()
    }

    public fun onDestroy() {
        signals.active = false
    }
}

/**
 * "Open notification settings" (P0.6): only where the platform has them, called once, and a failure
 * is swallowed (the person is already looking at the page around it).
 */
public object NotificationSettingsShortcut {
    public fun open(supported: Boolean, open: () -> Unit) {
        if (!supported) return
        try {
            open()
        } catch (_: RuntimeException) {
            // Never let a settings shortcut break the page around it.
        }
    }

    /** This app's Android notification settings. */
    public fun intent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/**
 * QA main-thread work always returns to the instrumentation framework (port of the Flutter
 * `Bb8MainCallback`).
 */
public object MainCallback {
    /**
     * Runs [action] inside [sync]'s Runnable; a throw is caught there and reported as false, never
     * escaping.
     */
    public fun run(sync: (Runnable) -> Unit, action: () -> Unit): Boolean {
        var succeeded = true
        sync(
            Runnable {
                try {
                    action()
                } catch (_: Throwable) {
                    succeeded = false
                }
            }
        )
        return succeeded
    }
}
