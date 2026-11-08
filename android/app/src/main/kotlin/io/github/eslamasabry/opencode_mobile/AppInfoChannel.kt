package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import java.security.MessageDigest

/** `oc/app`: facts about this app itself and its Android settings page. */
internal object AppInfoChannel {
    const val NAME = "oc/app"

    fun register(activity: Activity, messenger: BinaryMessenger) {
        MethodChannel(messenger, NAME).setMethodCallHandler { call, result ->
            when (call.method) {
                "getSigningCertificateSha256" -> result.success(signingCertificateSha256(activity))
                "openAppSettings" -> {
                    activity.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${activity.packageName}")
                        )
                    )
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun signingCertificateSha256(activity: Activity): String? {
        val info = activity.packageManager.getPackageInfo(
            activity.packageName,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
        )
        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.singleOrNull()
        } else {
            info.signatures?.singleOrNull()
        } ?: return null
        return MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString("") { byte -> "%02X".format(byte) }
    }
}
