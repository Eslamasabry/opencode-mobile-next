package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File

/**
 * Native bridge for in-app APK installations (`oc/app_update`).
 *
 * Uses FileProvider to expose downloaded APKs securely to the Android
 * package installer without world-readable file permissions.
 */
@Suppress("TooGenericExceptionCaught")
class AppUpdateInstaller(
    private val activity: Activity,
    messenger: BinaryMessenger,
) {
    companion object {
        const val CHANNEL_NAME = "oc/app_update"
    }

    private val channel = MethodChannel(messenger, CHANNEL_NAME)

    init {
        channel.setMethodCallHandler(::handleMethodCall)
    }

    fun dispose() {
        channel.setMethodCallHandler(null)
    }

    private fun handleMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getSupportedAbis" -> {
                result.success(Build.SUPPORTED_ABIS.toList())
            }
            "canRequestPackageInstalls" -> {
                val canInstall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    activity.packageManager.canRequestPackageInstalls()
                } else {
                    true
                }
                result.success(canInstall)
            }
            "openInstallPermissionSettings" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${activity.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        activity.startActivity(intent)
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("permission_error", e.message, null)
                    }
                } else {
                    result.success(true)
                }
            }
            "installApk" -> {
                val path = call.argument<String>("filePath")
                if (path == null) {
                    result.error("invalid_arg", "filePath must not be null", null)
                    return
                }
                val file = File(path)
                if (!file.exists() || !file.canRead()) {
                    result.error("file_not_found", "APK file does not exist or is unreadable: $path", null)
                    return
                }

                try {
                    val contentUri = FileProvider.getUriForFile(
                        activity,
                        "${activity.packageName}.fileprovider",
                        file,
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(contentUri, "application/vnd.android.package-archive")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    activity.startActivity(intent)
                    result.success(true)
                } catch (e: ActivityNotFoundException) {
                    result.error("no_installer", "No activity found to install package: ${e.message}", null)
                } catch (e: Exception) {
                    result.error("install_failed", e.message, null)
                }
            }
            else -> result.notImplemented()
        }
    }
}
