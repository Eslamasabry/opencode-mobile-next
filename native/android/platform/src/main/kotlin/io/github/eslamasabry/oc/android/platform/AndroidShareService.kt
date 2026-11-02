package io.github.eslamasabry.oc.android.platform

import android.app.Activity
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.platform.ShareService
import io.github.eslamasabry.oc.core.domain.platform.SharedFile

/** The visible activity, or null. [AndroidActivityResults] implements it for `MainActivity`. */
public fun interface CurrentActivity {
    public fun get(): Activity?
}

/**
 * Share out (plan 11 §14.2, 07 §7.7, ANDROID-PLATFORM-33). Port of `LaunchShareChannel.shareTextOut` (Flutter app at
 * bd0a13d6) without the channel: the system chooser with `EXTRA_EXCLUDE_COMPONENTS = [MainActivity]`, so the app never
 * offers itself (a diagnostics report must not start a session with itself). Files go only as this app's own
 * `FileProvider` URIs (`${applicationId}.localfiles`), always with an explicit read grant and `ClipData` (Android 18
 * stops implicit grants for `SEND`). Expected failures are values; nothing is logged.
 */
public class AndroidShareService(
    private val context: Context,
    private val activities: CurrentActivity,
) : ShareService {
    override fun shareText(text: String, subject: String?): Outcome<Unit> {
        if (text.isEmpty()) return Outcome.Failed(Failure.Unexpected())
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                if (!subject.isNullOrEmpty()) putExtra(Intent.EXTRA_SUBJECT, subject)
            }
        return startChooser(send)
    }

    override fun shareFile(file: SharedFile): Outcome<Unit> {
        val uri = ownFileProviderUri(file.uri) ?: return Outcome.Failed(Failure.Unexpected())
        if (file.mime.isBlank()) return Outcome.Failed(Failure.Unexpected())
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = file.mime
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(file.displayName.orEmpty(), uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        return startChooser(send)
    }

    /** Only `content://<our package>.localfiles/…`; anything else (file://, another app's provider, http) is refused. */
    private fun ownFileProviderUri(raw: String): Uri? {
        val uri = raw.toUri()
        val ours = "${context.packageName}$FILE_PROVIDER_SUFFIX"
        return uri.takeIf { it.scheme == "content" && it.authority == ours && !it.path.isNullOrEmpty() }
    }

    private fun startChooser(send: Intent): Outcome<Unit> {
        val chooser =
            Intent.createChooser(send, null).apply {
                putExtra(
                    Intent.EXTRA_EXCLUDE_COMPONENTS,
                    arrayOf(ComponentName(context.packageName, StaticShortcuts.MAIN_ACTIVITY)),
                )
                // createChooser copies the target's ClipData and grant flags; set them explicitly so a platform
                // change cannot silently drop the grant (07 §7.7).
                if (send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
                    clipData = send.clipData
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        return startFromVisibleActivity(context, activities, chooser)
    }

    private companion object {
        const val FILE_PROVIDER_SUFFIX = ".localfiles"
    }
}

/**
 * Starts [intent] from the visible activity, or from the application in a new task when none is visible. Any
 * refusal (no app can handle it, a too-large transaction, a security exception) is `Failure.Device`, never thrown.
 */
internal fun startFromVisibleActivity(context: Context, activities: CurrentActivity, intent: Intent): Outcome<Unit> =
    try {
        val activity = activities.get()
        if (activity != null) {
            activity.startActivity(intent)
        } else {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        Outcome.Ok(Unit)
    } catch (_: RuntimeException) {
        // ActivityNotFoundException, SecurityException, and TransactionTooLargeException wrapped in RuntimeException.
        Outcome.Failed(Failure.Device())
    }
