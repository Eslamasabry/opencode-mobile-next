package io.github.eslamasabry.opencode_mobile

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel

/**
 * Access to files in the phone's shared storage (`oc/storage`, both halves:
 * this file and lib/platform/storage_access.dart).
 *
 * Without it Android 11+ lets the app see other apps' folders but not their
 * non-media files (a project opened from /sdcard lists only dot-folders).
 * It is asked for only when the person opens or creates a project in shared
 * storage; never at start-up and never for the app's own project space.
 *
 *  - API 30+: "All files access" (MANAGE_EXTERNAL_STORAGE) is a Settings
 *    page, not a dialog: [open] launches it and answers `settings`; Dart
 *    re-reads [status] when the app comes back to the front.
 *  - API 29 and older: the READ/WRITE_EXTERNAL_STORAGE runtime dialog.
 */
object StorageAccess {
    private const val TAG = "OcStorage"
    private const val CHANNEL = "oc/storage"
    const val REQUEST = 4711

    private var pending: MethodChannel.Result? = null

    fun register(activity: Activity, messenger: BinaryMessenger) {
        MethodChannel(messenger, CHANNEL).setMethodCallHandler { call, result ->
            try {
                when (call.method) {
                    "status" -> result.success(status(activity))
                    "open" -> open(activity, result)
                    else -> result.notImplemented()
                }
            } catch (error: Exception) {
                Log.w(TAG, "storage call ${call.method} failed", error)
                result.error("storage", error.message ?: error.javaClass.simpleName, null)
            }
        }
    }

    /** `granted` or `notGranted`. */
    fun status(activity: Activity): String {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED &&
                (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q ||
                    activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED)
        }
        return if (granted) "granted" else "notGranted"
    }

    private fun open(activity: Activity, result: MethodChannel.Result) {
        if (status(activity) == "granted") {
            result.success("granted")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uri = Uri.parse("package:${activity.packageName}")
            try {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri))
            } catch (_: ActivityNotFoundException) {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
            result.success("settings")
            return
        }
        if (pending != null) {
            result.error("permission_in_progress", "A permission request is already open.", null)
            return
        }
        pending = result
        activity.requestPermissions(
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
            REQUEST,
        )
    }

    /** True when [requestCode] was this request (API 29 and older). */
    fun onPermissionResult(activity: Activity, requestCode: Int): Boolean {
        if (requestCode != REQUEST) return false
        val result = pending ?: return true
        pending = null
        result.success(status(activity))
        return true
    }
}
