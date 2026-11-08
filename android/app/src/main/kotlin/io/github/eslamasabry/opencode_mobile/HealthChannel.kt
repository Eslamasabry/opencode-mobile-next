package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.webkit.WebView
import androidx.core.app.NotificationManagerCompat
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel

/**
 * `oc/health`: the numbers behind the opt-in health telemetry
 * (docs/telemetry/health-and-diagnostics.md), both halves: this file and
 * lib/telemetry/health_probe.dart.
 *
 * It answers only numbers and fixed words: memory and storage as byte counts,
 * the CPU family, the WebView's major version, whether notifications are
 * allowed, and how long the process has run. It never reads or returns a
 * name, a path or any text from the phone. Dart asks only while the person
 * has agreed to share usage.
 *
 * Memory pressure goes the other way: Android's trim callbacks are passed to
 * Dart as a level word, and Dart sends them on only with the same consent.
 */
internal object HealthChannel {
    const val NAME = "oc/health"

    private var channel: MethodChannel? = null
    private var callbacks: ComponentCallbacks2? = null
    private var callbacksContext: Context? = null

    fun register(activity: Activity, messenger: BinaryMessenger) {
        dispose()
        val context = activity.applicationContext
        val methods = MethodChannel(messenger, NAME)
        channel = methods
        methods.setMethodCallHandler { call, result ->
            when (call.method) {
                "deviceContext" -> result.success(deviceContext(context))
                else -> result.notImplemented()
            }
        }
        val listener = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                levelWord(level)?.let { methods.invokeMethod("memoryPressure", mapOf("level" to it)) }
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Replaced by onTrimMemory")
            override fun onLowMemory() {
                methods.invokeMethod("memoryPressure", mapOf("level" to "critical"))
            }
        }
        context.registerComponentCallbacks(listener)
        callbacks = listener
        callbacksContext = context
    }

    fun dispose() {
        callbacks?.let { callbacksContext?.unregisterComponentCallbacks(it) }
        callbacks = null
        callbacksContext = null
        channel?.setMethodCallHandler(null)
        channel = null
    }

    /** The fixed word for a running-process trim level; null for levels about cached processes. */
    internal fun levelWord(level: Int): String? = when (level) {
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "moderate"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "low"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "critical"
        else -> null
    }

    /** The first CPU family as the contract names it, or null for any other. */
    internal fun abiWord(abi: String?): String? = when (abi) {
        "arm64-v8a" -> "arm64"
        "armeabi-v7a" -> "arm32"
        "x86_64" -> "x86_64"
        else -> null
    }

    private fun deviceContext(context: Context): Map<String, Any?> {
        val memory = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory)
        return mapOf(
            "ramBytes" to memory.totalMem,
            "freeBytes" to freeBytes(context),
            "abi" to abiWord(Build.SUPPORTED_ABIS.firstOrNull()),
            "webviewMajor" to webViewMajor(),
            "notificationsAllowed" to NotificationManagerCompat.from(context).areNotificationsEnabled(),
            "processUptimeMs" to (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()),
            "fromStore" to installedFromStore(context),
        )
    }

    private fun freeBytes(context: Context): Long = try {
        StatFs(context.filesDir.path).availableBytes
    } catch (_: IllegalArgumentException) {
        -1L
    }

    private fun webViewMajor(): Int? = try {
        WebView.getCurrentWebViewPackage()?.versionName
            ?.substringBefore('.')?.toIntOrNull()
    } catch (_: RuntimeException) {
        null
    }

    private fun installedFromStore(context: Context): Boolean = try {
        val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }
        source == "com.android.vending"
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
