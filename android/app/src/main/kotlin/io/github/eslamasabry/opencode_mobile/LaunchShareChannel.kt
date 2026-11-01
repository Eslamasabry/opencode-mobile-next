package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel

internal class LaunchShareChannel(private val activity: Activity) {
    private var pending: String? = null
    private var channel: MethodChannel? = null
    private var dartReady = false

    fun configure(messenger: BinaryMessenger) {
        dartReady = false
        channel = MethodChannel(messenger, "oc/share").also { shareChannel ->
            shareChannel.setMethodCallHandler { call, result ->
                when (call.method) {
                    "consumeSharedText" -> {
                        // Dart installs its inbound handler before acknowledging readiness.
                        dartReady = true
                        val text = pending
                        pending = null
                        result.success(text)
                    }
                    "shareText" -> result.success(
                        shareTextOut(call.argument<String>("text"), call.argument<String>("subject"))
                    )
                    "supportsOpenLocalFile" -> result.success(true)
                    "openLocalFile" -> result.success(
                        openLocalFile(call.argument<String>("path"), call.argument<Boolean>("install") == true)
                    )
                    else -> result.notImplemented()
                }
            }
        }
    }

    fun reset() {
        channel = null
        dartReady = false
    }

    fun onNewIntent(intent: Intent) {
        if (capture(intent)) {
            val currentChannel = channel
            val text = pending
            if (dartReady && currentChannel != null && text != null) {
                pending = null
                currentChannel.invokeMethod("shared", text)
            }
        }
    }

    /** Accept text shares and keep the subject as the first line when needed. */
    fun capture(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("text/") != true) return false
        val body = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        val accepted = body.isNotEmpty() || subject.isNotEmpty()
        if (accepted) {
            pending = sharedText(body, subject)
            // Consume the share so a configuration change does not replay it.
            intent.action = Intent.ACTION_MAIN
            intent.removeExtra(Intent.EXTRA_TEXT)
            intent.removeExtra(Intent.EXTRA_SUBJECT)
        }
        return accepted
    }

    private fun sharedText(body: String, subject: String): String = when {
        subject.isEmpty() -> body
        body.isEmpty() -> subject
        body.startsWith(subject) -> body
        else -> "$subject\n$body"
    }

    /** Exclude this app from the chooser to avoid starting a session with its own report. */
    private fun shareTextOut(text: String?, subject: String?): Boolean {
        if (text.isNullOrEmpty()) return false
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (!subject.isNullOrEmpty()) putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        val chooser = Intent.createChooser(send, null).apply {
            putExtra(
                Intent.EXTRA_EXCLUDE_COMPONENTS,
                arrayOf(ComponentName(activity, MainActivity::class.java)),
            )
        }
        return try {
            activity.startActivity(chooser)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /**
     * Hands a file in shared storage to Android: the app chooser, or for an .apk the
     * package installer, whose own screen is the confirmation. Never installs silently.
     */
    private fun openLocalFile(path: String?, install: Boolean): Boolean {
        if (path.isNullOrEmpty()) return false
        val file = File(path)
        if (!file.isFile) return false
        return try {
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.localfiles", file)
            val type = if (install) {
                "application/vnd.android.package-archive"
            } else {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
            }
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, type)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(if (install) view else Intent.createChooser(view, null))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
