package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.content.pm.PackageManager
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel

/** Opens only the official package; no caller-provided intents or VPN controls. */
class TailscaleHandoff(private val activity: Activity, messenger: BinaryMessenger) {
    init {
        MethodChannel(messenger, "oc/tailscale").setMethodCallHandler { call, result ->
            when (call.method) {
                "check" -> result.success(check())
                "open" -> result.success(open())
                else -> result.notImplemented()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun check(): String = try {
        val info = activity.packageManager.getApplicationInfo(PACKAGE, 0)
        if (info.enabled) "installed" else "unavailable"
    } catch (_: PackageManager.NameNotFoundException) {
        "missing"
    } catch (_: Exception) {
        "unavailable"
    }

    private fun open(): Boolean = try {
        val intent = activity.packageManager.getLaunchIntentForPackage(PACKAGE)
        if (intent == null) false else {
            activity.startActivity(intent)
            true
        }
    } catch (_: Exception) {
        false
    }

    companion object {
        private const val PACKAGE = "com.tailscale.ipn"
    }
}
