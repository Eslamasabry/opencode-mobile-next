package io.github.eslamasabry.opencode_mobile

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

/**
 * How hot Android says the phone is (`oc/thermal` and `oc/thermal/events`,
 * both halves: this file and lib/platform/thermal.dart).
 *
 * Android never closes an app for heat: it throttles the CPU and lets the
 * heat build. The app's thermal guard pauses the AI Team instead, from
 * these readings:
 * - `status`: PowerManager's thermal status (API 29+), as a word:
 *   none, light, moderate, severe, critical, emergency, shutdown;
 *   "unknown" before Android 10.
 * - `headroom`: `getThermalHeadroom(30)` (API 30+), the forecast in 30 s
 *   where 1.0 means severe throttling; -1 when Android has none. Android
 *   answers NaN when asked more than about once a second, so it is read
 *   at most every [HEADROOM_POLL_MS] while Dart listens.
 *
 * The status listener lives as long as the process: the built-in server's
 * foreground service keeps the process alive while the team runs.
 */
object ThermalMonitor {
    private const val TAG = "OcThermal"
    private const val CHANNEL = "oc/thermal"
    private const val EVENTS = "oc/thermal/events"
    private const val HEADROOM_POLL_MS = 30_000L

    // The team's existing status channel (BackgroundConnectionService). The
    // guard posts only when it already exists and is not silenced: no new
    // channel, no new kind of noise.
    private const val TEAM_STATUS_CHANNEL_ID = "opencode_coding_status"
    private const val NOTIFICATION_ID = 0x7E4A1

    private val main = Handler(Looper.getMainLooper())
    private var sink: EventChannel.EventSink? = null
    private var appContext: Context? = null
    private var listening = false
    private var lastStatus = -1

    // Made only on Android 10+: the listener type does not exist before.
    private var statusListener: Any? = null

    private val poll = object : Runnable {
        override fun run() {
            emit()
            main.postDelayed(this, HEADROOM_POLL_MS)
        }
    }

    /** Status codes to words; unknown codes read as "unknown". */
    fun statusName(code: Int): String = when (code) {
        0 -> "none"
        1 -> "light"
        2 -> "moderate"
        3 -> "severe"
        4 -> "critical"
        5 -> "emergency"
        6 -> "shutdown"
        else -> "unknown"
    }

    fun register(context: Context, messenger: BinaryMessenger) {
        appContext = context.applicationContext
        startListening()
        MethodChannel(messenger, CHANNEL).setMethodCallHandler { call, result ->
            try {
                when (call.method) {
                    "current" -> result.success(reading())
                    "notify" -> result.success(
                        notify(
                            call.argument<String>("title") ?: "",
                            call.argument<String>("text") ?: "",
                        ),
                    )
                    else -> result.notImplemented()
                }
            } catch (error: Exception) {
                Log.w(TAG, "thermal call ${call.method} failed", error)
                result.error("thermal", error.message ?: error.javaClass.simpleName, null)
            }
        }
        EventChannel(messenger, EVENTS).setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                sink = events
                main.removeCallbacks(poll)
                main.post(poll)
            }

            override fun onCancel(arguments: Any?) {
                sink = null
                main.removeCallbacks(poll)
            }
        })
    }

    @Synchronized
    private fun startListening() {
        if (listening || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val power = appContext?.getSystemService(PowerManager::class.java) ?: return
        try {
            val listener = PowerManager.OnThermalStatusChangedListener { status ->
                lastStatus = status
                emit()
            }
            power.addThermalStatusListener(appContext!!.mainExecutor, listener)
            statusListener = listener
            lastStatus = power.currentThermalStatus
            listening = true
        } catch (error: Exception) {
            Log.w(TAG, "thermal listener unavailable", error)
        }
    }

    private fun emit() {
        val events = sink ?: return
        main.post {
            try {
                events.success(reading())
            } catch (error: Exception) {
                Log.w(TAG, "thermal event failed", error)
            }
        }
    }

    private fun reading(): Map<String, Any?> {
        val context = appContext
        val power = context?.getSystemService(PowerManager::class.java)
        var status = lastStatus
        if (power != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            status = power.currentThermalStatus
            lastStatus = status
        }
        var headroom = -1.0
        if (power != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val value = try {
                power.getThermalHeadroom(30)
            } catch (error: Exception) {
                Float.NaN
            }
            if (!value.isNaN()) headroom = value.toDouble()
        }
        return mapOf(
            "status" to statusName(status),
            "headroom" to headroom,
            "sdk" to Build.VERSION.SDK_INT,
        )
    }

    /**
     * One notification on the team's existing status channel, replacing
     * the previous one: the guard calls it once per pause and once per
     * resume, only while the app is in the background. False when that
     * channel does not exist yet or the person silenced it.
     */
    private fun notify(title: String, text: String): Boolean {
        val context = appContext ?: return false
        if (title.isBlank()) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !manager.areNotificationsEnabled()) {
            return false
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = manager.getNotificationChannel(TEAM_STATUS_CHANNEL_ID) ?: return false
            if (channel.importance == NotificationManager.IMPORTANCE_NONE) return false
            Notification.Builder(context, TEAM_STATUS_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
        if (text.isNotBlank()) builder.setContentText(text)
        manager.notify(NOTIFICATION_ID, builder.build())
        return true
    }
}
