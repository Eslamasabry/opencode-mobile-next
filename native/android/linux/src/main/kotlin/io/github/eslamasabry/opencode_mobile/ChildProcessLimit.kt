package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * Reads whether Android's child process restrictions ("phantom process" limit of 32) are lifted by
 * the Developer options toggle "Disable child process restrictions". NEEDS A RELEASE (native); an
 * older APK answers notImplemented and the Dart side falls back to instructions only.
 */
object ChildProcessLimit {
    const val FLAG = "settings_enable_monitor_phantom_procs"
    private const val PROPERTY = "persist.sys.fflag.override.$FLAG"

    /**
     * The flag's value as text -> restrictionsOff. The flag is "monitor phantom procs": false =
     * restrictions off.
     */
    fun restrictionsOffFrom(value: String?): Boolean? {
        val v = value?.trim()?.lowercase().orEmpty()
        return when (v) {
            "" -> null
            "false" -> true
            "true" -> false
            else -> null
        }
    }

    fun developerOptionsEnabled(context: Context): Boolean? =
        try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0,
            ) == 1
        } catch (_: Exception) {
            null
        }

    fun restrictionsOff(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val fromGlobal =
            try {
                restrictionsOffFrom(Settings.Global.getString(context.contentResolver, FLAG))
            } catch (_: Exception) {
                null
            }
        return fromGlobal ?: restrictionsOffFrom(readProperty())
    }

    private fun readProperty(): String? {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("/system/bin/getprop", PROPERTY))
            val done = process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)
            if (done) process.inputStream.bufferedReader().use { it.readText() } else null
        } catch (_: Exception) {
            null
        } finally {
            runCatching { process?.destroy() }
        }
    }

    fun status(context: Context): Map<String, Any?> =
        mapOf(
            "sdkInt" to Build.VERSION.SDK_INT,
            "developerOptionsEnabled" to developerOptionsEnabled(context),
            "restrictionsOff" to restrictionsOff(context),
        )
}
