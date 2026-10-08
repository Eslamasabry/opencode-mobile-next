package io.github.eslamasabry.opencode_mobile

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * Minimal oc/voice download bridge; all calls run on the Activity's main thread.
 * Fixed copy only: no Dart-provided text is posted or persisted. This does not
 * start a service or keep Dart downloads alive after Android kills the process.
 */
class VoiceDownloadNotifications(private val activity: Activity) {
    private val permissionResults = mutableListOf<MethodChannel.Result>()
    private val manager get() = activity.getSystemService(NotificationManager::class.java)

    fun handle(call: MethodCall, result: MethodChannel.Result): Boolean {
        when (call.method) {
            "getVoiceSetupNetwork" -> result.success(network())
            "requestVoiceDownloadNotificationPermission" -> requestPermission(result)
            "showVoiceDownloadNotification" -> result.success(show(call))
            "dismissVoiceDownloadNotification" -> {
                try { manager?.cancel(NOTIFICATION_ID) } catch (_: Exception) { }
                result.success(null)
            }
            else -> return false
        }
        return true
    }

    private fun network(): String = try {
        val connectivity = activity.getSystemService(ConnectivityManager::class.java)
        val network = connectivity?.activeNetwork
        if (connectivity == null) "unknown"
        else if (network == null) "offline"
        else {
            val capabilities = connectivity.getNetworkCapabilities(network)
            when {
                capabilities == null -> "unknown"
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> "offline"
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> "unknown"
                // Mobile data always requires explicit consent, including
                // plans Android happens to report as unmetered.
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "metered"
                connectivity.isActiveNetworkMetered -> "metered"
                // A VPN's underlying network cannot be proven here.
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "unknown"
                else -> "unmetered"
            }
        }
    } catch (_: Exception) { "unknown" }

    private fun available(): Boolean {
        val notifications = manager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return false
        if (!notifications.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifications.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    NativeStrings.get(activity, R.string.native_voice_setup),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            if (notifications.getNotificationChannel(CHANNEL_ID)?.importance ==
                NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    private fun requestPermission(result: MethodChannel.Result) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                permissionResults.add(result)
                if (permissionResults.size == 1) {
                    activity.requestPermissions(
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERMISSION_REQUEST
                    )
                }
            } else result.success(available())
        } catch (_: Exception) {
            if (permissionResults.isEmpty()) result.success(false)
            else finishPermission(false)
        }
    }

    fun onPermissionResult(requestCode: Int, grantResults: IntArray): Boolean {
        if (requestCode != PERMISSION_REQUEST) return false
        val granted = try {
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && available()
        } catch (_: Exception) { false }
        finishPermission(granted)
        return true
    }

    private fun finishPermission(granted: Boolean) {
        val results = permissionResults.toList()
        permissionResults.clear()
        results.forEach { it.success(granted) }
    }

    fun dispose() {
        finishPermission(false)
    }

    private fun show(call: MethodCall): Boolean {
        try {
            if (!available()) return false
            val phase = call.argument<String>("phase")
            val text = when (phase) {
                "downloading" -> NativeStrings.get(activity, R.string.native_voice_downloading)
                "verifying" -> NativeStrings.get(activity, R.string.native_voice_preparing)
                "complete" -> NativeStrings.get(activity, R.string.native_voice_ready)
                "failed" -> NativeStrings.get(activity, R.string.native_voice_failed)
                else -> return false
            }
            val total = (call.argument<Number>("totalBytes")?.toLong() ?: 0L).coerceAtLeast(0L)
            val received = (call.argument<Number>("receivedBytes")?.toLong() ?: 0L).coerceIn(0L, total)
            val active = phase == "downloading" || phase == "verifying"
            val intent = Intent(activity, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val open = PendingIntent.getActivity(
                activity, NOTIFICATION_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(activity, CHANNEL_ID)
            } else Notification.Builder(activity)
            builder.setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(NativeStrings.get(activity, R.string.native_voice_setup))
                .setContentText(text)
                .setContentIntent(open)
                .setOnlyAlertOnce(true)
                .setOngoing(active)
                .setAutoCancel(!active)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setCategory(Notification.CATEGORY_PROGRESS)
            if (active) {
                val progress = if (total > 0) ((received.toDouble() / total) * 100).toInt() else 0
                builder.setProgress(100, progress, phase == "verifying" || total == 0L)
                // Each progress update renews this bound. A killed Dart process
                // must not leave an ongoing "downloading" notification forever.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    builder.setTimeoutAfter(15 * 60 * 1000L)
                }
            }
            manager?.notify(NOTIFICATION_ID, builder.build()) ?: return false
            return true
        } catch (_: Exception) { return false }
    }

    companion object {
        private const val CHANNEL_ID = "opencode_voice_setup"
        private const val NOTIFICATION_ID = 0x701CE
        private const val PERMISSION_REQUEST = 4710
    }
}
