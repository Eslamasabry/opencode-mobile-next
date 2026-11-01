package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.content.pm.PackageManager
import io.flutter.plugin.common.MethodChannel

internal class TermuxActivityPermissions(
    private val activity: Activity,
    private val environment: TermuxActivityEnvironment
) {
    private var permissionResult: MethodChannel.Result? = null
    private var runCommandAccessResult: MethodChannel.Result? = null

    fun requestPermission(result: MethodChannel.Result) {
        when {
            !environment.isInstalled() -> result.error("termux_missing", "Termux is not installed.", null)
            environment.hasPermission() -> result.success(true)
            permissionResult != null || runCommandAccessResult != null ->
                result.error("permission_in_progress", "A permission request is already open.", null)
            else -> {
                permissionResult = result
                activity.requestPermissions(
                    arrayOf(TermuxActivityEnvironment.PERMISSION), TermuxActivityBridge.PERMISSION_REQUEST
                )
            }
        }
    }

    /** Same request as [requestPermission], with a word describing the permission state. */
    fun requestAccess(result: MethodChannel.Result) {
        when {
            !environment.isInstalled() -> result.success("missing")
            environment.hasPermission() -> result.success("granted")
            runCommandAccessResult != null || permissionResult != null ->
                result.error("permission_in_progress", "A permission request is already open.", null)
            else -> {
                runCommandAccessResult = result
                activity.requestPermissions(
                    arrayOf(TermuxActivityEnvironment.PERMISSION), TermuxActivityBridge.PERMISSION_REQUEST
                )
            }
        }
    }

    fun onPermissionResult(grantResults: IntArray) {
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        val access = runCommandAccessResult
        runCommandAccessResult = null
        if (access != null) {
            // After repeated denial Android no longer shows a dialog; only Settings can grant access.
            access.success(
                when {
                    granted -> "granted"
                    // Android may cancel a request with empty arrays; that is
                    // not a permanent permission refusal.
                    grantResults.isEmpty() -> "denied"
                    !activity.shouldShowRequestPermissionRationale(TermuxActivityEnvironment.PERMISSION) ->
                        "permanentlyDenied"
                    else -> "denied"
                }
            )
        }
        val result = permissionResult ?: return
        permissionResult = null
        result.success(granted)
    }

    fun reset() {
        permissionResult = null
        runCommandAccessResult = null
    }
}
