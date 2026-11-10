package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
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
        /**
         * [path] as a file only when it is a readable `.apk` directly inside
         * cacheDir/updates (canonical, so no `..` or link leads elsewhere);
         * the only place the updater downloads to and FileProvider exposes.
         */
        fun updateApk(cacheDir: File, path: String): File? = try {
            val updates = File(cacheDir, "updates").canonicalFile
            val file = File(path).canonicalFile
            file.takeIf {
                it.parentFile == updates && it.name.endsWith(".apk") && it.isFile && it.canRead()
            }
        } catch (_: java.io.IOException) {
            null
        }
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
            "getSupportedAbis" -> result.success(Build.SUPPORTED_ABIS.toList())
            "canRequestPackageInstalls" -> result.success(canRequestPackageInstalls())
            "openInstallPermissionSettings" -> openInstallPermissionSettings(result)
            "installApk" -> installApk(call.argument<String>("filePath"), result)
            else -> result.notImplemented()
        }
    }

    private fun canRequestPackageInstalls(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    private fun openInstallPermissionSettings(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            result.success(true)
            return
        }
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
    }

    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
        else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES

    private fun facts(info: PackageInfo?): UpdateApkCheck.Facts? {
        if (info == null) return null
        val signers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo
            when {
                signing == null -> emptyArray()
                signing.hasMultipleSigners() -> signing.apkContentsSigners
                else -> signing.signingCertificateHistory.takeLast(1).toTypedArray()
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures ?: emptyArray()
        }
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()
        return UpdateApkCheck.Facts(
            packageName = info.packageName,
            versionCode = code,
            signers = signers.map { UpdateApkCheck.sha256(it.toByteArray()) }.toSet(),
        )
    }

    private fun installedFacts() =
        facts(activity.packageManager.getPackageInfo(activity.packageName, signingFlags()))

    private fun archiveFacts(file: File) =
        facts(activity.packageManager.getPackageArchiveInfo(file.path, signingFlags()))

    private fun installApk(path: String?, result: MethodChannel.Result) {
        val file = path?.let { updateApk(activity.cacheDir, it) }
        if (file == null) {
            result.error("invalid_arg", "Only a downloaded update can be installed", null)
            return
        }
        val problem = UpdateApkCheck.problem(installedFacts(), archiveFacts(file))
        if (problem != null) result.error("not_this_app", problem, null) else openInstaller(file, result)
    }

    private fun openInstaller(file: File, result: MethodChannel.Result) {
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
}

/**
 * Whether a downloaded APK may replace this app: the same package, signed by
 * exactly the same certificates, and a newer build. The release checksum only
 * catches corruption; this catches a swapped file before the installer does.
 */
object UpdateApkCheck {
    data class Facts(val packageName: String, val versionCode: Long, val signers: Set<String>)

    fun sha256(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Null when [candidate] may be installed over [installed], else why not. */
    fun problem(installed: Facts?, candidate: Facts?): String? = when {
        installed == null -> "This app's own details are unreadable"
        candidate == null -> "Android cannot read the download as an app"
        candidate.packageName != installed.packageName -> "The download is a different app"
        candidate.signers.isEmpty() || candidate.signers != installed.signers ->
            "The download is not signed by this app's developer"
        candidate.versionCode <= installed.versionCode -> "The download is not newer than this app"
        else -> null
    }
}
