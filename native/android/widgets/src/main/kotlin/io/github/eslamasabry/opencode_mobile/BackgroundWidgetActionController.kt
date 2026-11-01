package io.github.eslamasabry.opencode_mobile

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject
import io.github.eslamasabry.oc.android.widgets.R
import io.github.eslamasabry.oc.android.widgets.MainActivity
import io.github.eslamasabry.oc.android.widgets.BackgroundConnectionService
import io.github.eslamasabry.oc.android.widgets.StatusNotification
import io.github.eslamasabry.oc.android.widgets.WidgetActionRuntime
import io.github.eslamasabry.oc.android.widgets.NativeStrings

/** Owns the bounded, transient widget action without changing live-service ownership. */
internal class BackgroundWidgetActionController(
    private val service: Service,
    private val ownsLive: () -> Boolean,
    private val updatePending: (Boolean) -> Unit,
) {
    private val widgetHandler = Handler(Looper.getMainLooper())
    private var widgetLease: WidgetActionLease? = null
    private var widgetRuntime: WidgetActionRuntime? = null
    private var widgetPayload: Map<String, Any?>? = null
    private val widgetTimeout = Runnable { finish(null) }
    val hasLease: Boolean get() = widgetLease != null

    fun start(intent: Intent, startId: Int) {
        val payload = WidgetV2Bridge.parsePayload(intent.getStringExtra(WidgetV2Bridge.EXTRA_PAYLOAD))
        // Always meet the foreground contract, including malformed taps.
        if (promote(startId, payload)) admit(payload)
    }

    private fun promote(startId: Int, payload: Map<String, Any?>?): Boolean {
        // A live owner must never be demoted to shortService.
        if (ownsLive() || hasLease) return true
        return try {
            val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(service, StatusNotification.CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(service)
            }.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(service.applicationInfo.loadLabel(service.packageManager))
                .setContentText(NativeStrings.get(service, R.string.native_team_working))
                .setOnlyAlertOnce(true).setOngoing(true)
                .setVisibility(Notification.VISIBILITY_SECRET).build()
            val id = BackgroundConnectionService.WIDGET_ACTION_NOTIFICATION_ID
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                    service.startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    service.startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                else -> service.startForeground(id, notification)
            }
            true
        } catch (_: RuntimeException) {
            publishFailure(payload, "unsupported")
            service.stopSelf(startId)
            false
        }
    }

    private fun admit(payload: Map<String, Any?>?) {
        val error = if (payload == null) "invalidRequest" else WidgetActionBridge.admissionError(service, payload)
        when {
            error != null -> reject(payload, error)
            payload == null -> Unit // The invalid-request branch above already handled this.
            ownsLive() && MainActivity.backgroundChannel != null -> WidgetActionBridge.submit(service, payload) { }
            hasLease -> reject(payload, "busy")
            else -> claim(payload)
        }
    }

    private fun claim(payload: Map<String, Any?>) {
        val snapshot = WidgetV2Bridge.snapshot(service)
        val rows = snapshot?.optJSONArray("sessions")
        val row = rows?.let {
            (0 until it.length()).mapNotNull { index -> it.optJSONObject(index) }
                .firstOrNull { it.optString("id") == payload["sessionID"] }
        }
        val advertised = row?.optJSONArray("onDemandActions")
        val supported = !snapshot?.optJSONObject("onDemand")?.optString("fingerprint").isNullOrBlank() &&
            advertised != null && (0 until advertised.length()).any { advertised.optString(it) == payload["action"] }
        if (supported && snapshot != null) {
            val error = WidgetActionBridge.claim(service, payload)
            when (error) {
                // A remaining claim cannot prove prior delivery failed. Never replay.
                "busy" -> reject(payload, "uncertainDelivery", "uncertain")
                null -> execute(payload, snapshot)
                else -> reject(payload, error)
            }
        } else {
            reject(payload, "unsupported")
        }
    }

    private fun publishFailure(payload: Map<String, Any?>?, error: String, status: String = "rejected") {
        if (payload != null) runCatching {
            WidgetActionBridge.publishResult(service, payload, WidgetActionBridge.result(payload, status, error))
        }
    }

    private fun reject(payload: Map<String, Any?>?, error: String, status: String = "rejected") {
        publishFailure(payload, error, status)
        if (!ownsLive() && !hasLease) {
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
            service.stopSelf()
        }
    }

    private fun execute(payload: Map<String, Any?>, snapshot: JSONObject) {
        val lease = WidgetActionLease(payload["id"].toString(), SystemClock.elapsedRealtime())
        widgetLease = lease
        widgetPayload = payload
        updatePending(true)
        // Monotonic native watchdog includes engine startup. No tap can extend it.
        widgetHandler.postDelayed(widgetTimeout, lease.remaining(SystemClock.elapsedRealtime()))
        runCatching { SessionsWidgetProvider.refreshAll(service) }
        val deadline = System.currentTimeMillis() + lease.remaining(SystemClock.elapsedRealtime())
        widgetRuntime = WidgetActionRuntime(service, payload, snapshot, deadline) {
            finish(it)
        }
        try { widgetRuntime?.start() } catch (_: RuntimeException) { finish(null) }
    }

    fun finish(response: Map<*, *>?) {
        val lease = widgetLease ?: return
        if (lease.settle()) {
            widgetHandler.removeCallbacks(widgetTimeout)
            val payload = widgetPayload ?: return
            val result = WidgetActionLease.safeResult(payload, response)
            if (result["status"] == "rejected") {
                runCatching { WidgetActionBridge.releaseClaim(service, payload) }
            }
            if (result["status"] == "accepted") {
                runCatching {
                    val snapshot = response?.get("snapshot") as? Map<*, *>
                    if (WidgetActionBridge.applyActionSnapshot(service, payload, snapshot)) {
                        WidgetStatusScheduler.afterAction(service, payload)
                    }
                }
            }
            // Dart closes its transport before replying. Destroy also closes the
            // isolate on deadline/error, including sockets still awaiting a response.
            runCatching { widgetRuntime?.destroy() }
            widgetRuntime = null
            widgetLease = null
            widgetPayload = null
            updatePending(false)
            runCatching { WidgetActionBridge.publishResult(service, payload, result) }
            // Even if profile deletion/switch rejects the old receipt, remove the
            // process-local Sending state from whichever snapshot is current.
            runCatching { SessionsWidgetProvider.refreshAll(service) }
            if (!ownsLive()) {
                service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
                service.stopSelf()
            }
        }
    }

    fun destroy() {
        widgetHandler.removeCallbacks(widgetTimeout)
        runCatching { widgetRuntime?.destroy() }
        widgetRuntime = null
        widgetLease = null
        updatePending(false)
        widgetPayload?.let { publishFailure(it, "uncertainDelivery", "uncertain") }
        widgetPayload = null
    }
}
