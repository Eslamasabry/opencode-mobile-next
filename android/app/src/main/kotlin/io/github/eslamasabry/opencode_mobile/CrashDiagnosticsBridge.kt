package io.github.eslamasabry.opencode_mobile

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel

/** No ANR trace stream, exit description, exception value or log is read. */
class CrashDiagnosticsBridge(context: Context, messenger: BinaryMessenger) {
    private companion object {
        const val MAX_EXIT_RECORDS = 16
    }
    private val channel = MethodChannel(messenger, "oc/crash_diagnostics")
    private val app = context.applicationContext

    init {
        channel.setMethodCallHandler { call, result ->
            if (call.method != "open") {
                result.notImplemented()
            } else {
                try {
                    result.success(mapOf("directory" to app.filesDir.absolutePath,
                        "nativeCrash" to NativeCrashStore(app.filesDir).read(),
                        "anrTimestamp" to previousAnr()))
                } catch (_: Throwable) {
                    result.error(
                        "unavailable",
                        "Saved crash details are unavailable. Try again after restarting.",
                        null,
                    )
                }
            }
        }
    }

    private fun previousAnr(): Long? {
        val consent = NativeCrashStore(app.filesDir).enabledSince()
        if (consent == 0L || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val manager = app.getSystemService(ActivityManager::class.java)
        return try {
            manager?.getHistoricalProcessExitReasons(app.packageName, 0, MAX_EXIT_RECORDS)
                ?.filter { it.reason == ApplicationExitInfo.REASON_ANR && it.timestamp > consent &&
                    it.processName == app.packageName }
                ?.maxOfOrNull { it.timestamp }
        } catch (_: Throwable) { null }
    }

    fun dispose() {
        channel.setMethodCallHandler(null)
    }
}
