package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File

/**
 * `oc/app_update`: the app installs its own new version. Dart downloads and
 * checks the file; this side reads the file's package, version and signer
 * from Android, and hands the file to a PackageInstaller session. Android
 * shows its own confirm dialog; the outcome comes back as `installState`.
 * Both halves are single-owner (docs/design/app-updates-2026-10-31.md).
 */
@Suppress("TooGenericExceptionCaught")
internal class AppUpdateChannel(private val activity: Activity) {
    private val handler = Handler(Looper.getMainLooper())
    private var channel: MethodChannel? = null

    fun register(messenger: BinaryMessenger) {
        channel = MethodChannel(messenger, CHANNEL).also { it.setMethodCallHandler(::handle) }
        sink = { state, message ->
            handler.post {
                channel?.invokeMethod(
                    "installState",
                    mapOf("state" to state, "message" to message),
                )
            }
        }
    }

    fun dispose() {
        channel?.setMethodCallHandler(null)
        channel = null
        sink = null
    }

    private fun handle(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {
                "canInstall" -> result.success(activity.packageManager.canRequestPackageInstalls())
                "openInstallPermission" -> {
                    activity.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${activity.packageName}"),
                        ),
                    )
                    result.success(null)
                }
                "ownSignerSha256" -> result.success(signerOf(activity.packageName, archive = false))
                "supportedAbis" -> result.success(Build.SUPPORTED_ABIS.toList())
                "inspectApk" -> inspect(call.argument<String>("path"), result)
                "install" -> install(call.argument<String>("path"), result)
                else -> result.notImplemented()
            }
        } catch (e: Exception) {
            result.error("app_update", e.message ?: e.javaClass.simpleName, null)
        }
    }

    private fun flags(): Int =
        if (Build.VERSION.SDK_INT >= ANDROID_P) PackageManager.GET_SIGNING_CERTIFICATES
        else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES

    private fun certificates(info: android.content.pm.PackageInfo): List<ByteArray> {
        val signers = if (Build.VERSION.SDK_INT >= ANDROID_P) {
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
        return signers.map { it.toByteArray() }
    }

    private fun signerOf(target: String, archive: Boolean): String {
        val pm = activity.packageManager
        val info = if (archive) pm.getPackageArchiveInfo(target, flags())
        else pm.getPackageInfo(target, flags())
        return AppUpdatePolicy.signerString(info?.let(::certificates) ?: emptyList())
    }

    private fun inspect(path: String?, result: MethodChannel.Result) {
        val info = path?.let { activity.packageManager.getPackageArchiveInfo(it, flags()) }
        if (info == null) {
            result.error("bad_apk", "Android cannot read this file as an app", null)
            return
        }
        val code = if (Build.VERSION.SDK_INT >= ANDROID_P) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()
        result.success(
            mapOf(
                "packageName" to info.packageName,
                "versionCode" to code,
                "signerSha256" to AppUpdatePolicy.signerString(certificates(info)),
            ),
        )
    }

    private fun install(path: String?, result: MethodChannel.Result) {
        val file = path?.let(::File)
        if (file == null || !file.isFile) {
            result.error("bad_apk", "The file is gone", null)
            return
        }
        val app = activity.applicationContext
        Thread {
            var sessionId = -1
            val installer = app.packageManager.packageInstaller
            try {
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                params.setAppPackageName(app.packageName)
                if (Build.VERSION.SDK_INT >= ANDROID_S) {
                    params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
                sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    file.inputStream().use { input ->
                        session.openWrite("opencode-mobile.apk", 0, file.length()).use { out ->
                            input.copyTo(out)
                            session.fsync(out)
                        }
                    }
                    val intent = Intent(app, AppUpdateStatusReceiver::class.java)
                    val pending = PendingIntent.getBroadcast(
                        app,
                        sessionId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                    session.commit(pending.intentSender)
                }
                handler.post { result.success(null) }
            } catch (e: Exception) {
                if (sessionId >= 0) {
                    try { installer.abandonSession(sessionId) } catch (_: Exception) { }
                }
                handler.post { result.error("install", e.message ?: e.javaClass.simpleName, null) }
            }
        }.start()
    }

    companion object {
        const val CHANNEL = "oc/app_update"
        private const val ANDROID_P = 28
        private const val ANDROID_S = 31

        /** Where the status receiver reports; set while the channel is registered. */
        @Volatile
        var sink: ((state: String, message: String?) -> Unit)? = null
    }
}

/** PackageInstaller's answer: Android's confirm dialog, then the outcome. */
class AppUpdateStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirm)
            }
        }
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        AppUpdateChannel.sink?.invoke(AppUpdatePolicy.stateFor(status), message)
    }
}
