package io.github.eslamasabry.opencode_mobile.graph

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.runtime.paths.SharedStoragePath
import io.github.eslamasabry.oc.core.security.ApprovedLink
import io.github.eslamasabry.oc.core.security.ExternalLinkOpener
import io.github.eslamasabry.oc.core.security.ExternalLinkPolicy
import io.github.eslamasabry.oc.core.security.LinkDecision
import io.github.eslamasabry.oc.core.security.LinkOrigin
import io.github.eslamasabry.oc.feature.chat.presentation.PhoneFileOpen
import io.github.eslamasabry.oc.feature.chat.presentation.ChatOutside
import io.github.eslamasabry.oc.feature.chat.presentation.LinkCheck
import io.github.eslamasabry.oc.feature.chat.presentation.NoChatOutside
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.PreviewRoute
import java.io.File
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/*
 * P12-CHAT-FEEL: the Android half of the chat's clipboard, share sheet and links. Every reply link goes
 * ExternalLinkPolicy (origin Markdown: asks first) → ExternalLinkOpener (AGENTS.md invariant, R17); nothing here
 * launches a URL the policy did not approve. Nothing is logged (prompt and reply text never reach logs).
 */

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [NoChatOutside::class])
class AndroidChatOutside(
    private val application: Application,
    private val policy: ExternalLinkPolicy,
    // The app's one opener (PreviewQrWiring's AndroidExternalLinkOpener binding).
    private val opener: ExternalLinkOpener,
    private val navigator: AppNavigator,
    private val connections: ConnectionRegistry,
    private val activities: ResumedActivities,
    private val dispatchers: AppDispatchers,
) : ChatOutside {

    override fun fileLinkPath(url: String): String? =
        try {
            val uri = URI(url.trim())
            if (!uri.scheme.equals("file", ignoreCase = true) ||
                (!uri.rawAuthority.isNullOrEmpty() && !uri.rawAuthority.equals("localhost", ignoreCase = true))
            ) null else uri.path?.takeIf { it.startsWith('/') && '\u0000' !in it }
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: java.net.URISyntaxException) {
            null
        }

    override suspend fun openPhoneFile(ref: ConversationRef, path: String): Outcome<PhoneFileOpen> {
        val graph = connections.graphFor(ref) ?: return Outcome.Ok(PhoneFileOpen.Preview)
        if (!AddressRules.isLoopback(graph.profile.origin.host) || !SharedStoragePath.isShared(path)) return Outcome.Ok(PhoneFileOpen.Preview)
        val extension = path.substringAfterLast('/').substringAfterLast('.', "").lowercase()
        if (extension != "apk" && extension !in SHARED_BINARY_TYPES) return Outcome.Ok(PhoneFileOpen.Preview)
        return try {
            val requested = withContext(dispatchers.main) {
                if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                    application.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${application.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    true
                } else if (Build.VERSION.SDK_INT < 30 && application.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    val activity = activities.current ?: return@withContext false
                    activity.requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 0x46F1)
                    true
                } else false
            }
            if (requested) return Outcome.Ok(PhoneFileOpen.AccessRequested)
            val uri = withContext(dispatchers.io) {
                val file = File(path).canonicalFile
                val root = Environment.getExternalStorageDirectory().canonicalFile
                if (!file.path.startsWith(root.path + File.separator) || !file.isFile || !file.canRead()) {
                    throw IOException("unavailable")
                }
                FileProvider.getUriForFile(application, "${application.packageName}.files", file)
            }
            withContext(dispatchers.main) {
                // A deleted or replaced server cannot hand a late path to Android.
                if (connections.graphFor(ref) !== graph) return@withContext Outcome.Ok(PhoneFileOpen.Preview)
                val mime = if (extension == "apk") "application/vnd.android.package-archive"
                    else MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
                val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .apply { clipData = ClipData.newRawUri(null, uri) }
                val launch = if (extension == "apk") view else Intent.createChooser(view, null)
                application.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Outcome.Ok(PhoneFileOpen.Opened)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ActivityNotFoundException) {
            Outcome.Failed(Failure.Media(MediaProblem.NoAppToOpen))
        } catch (_: IOException) {
            Outcome.Failed(Failure.Media(MediaProblem.FileNotShowable))
        } catch (_: SecurityException) {
            Outcome.Failed(Failure.Media(MediaProblem.FileNotShowable))
        } catch (_: IllegalArgumentException) {
            Outcome.Failed(Failure.Media(MediaProblem.FileNotShowable))
        }
    }

    /** Links the policy approved and the person is being asked about (one at a time on the screen). */
    private val asked = LinkedHashMap<String, ApprovedLink>()

    override fun copy(text: String): Boolean =
        platformCall {
            val clipboard = application.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("OpenCode Mobile", text))
        }

    override fun share(text: String): Boolean =
        platformCall {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            application.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

    /** The platform's expected refusals (no app, a system policy) are a plain "it did not work". */
    private inline fun platformCall(block: () -> Unit): Boolean =
        try {
            block()
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalStateException) {
            false
        }

    override fun checkLink(url: String): LinkCheck {
        // P12-FIX-PREVIEWQR: `http://localhost:5173` in a reply is the conversation's dev server, not the phone's:
        // it opens in the in-app preview against that conversation's server (Flutter claims these links).
        PreviewRoute.Page.claim(url, (navigator.top.value as? ChatRoute)?.ref)?.let { page ->
            navigator.open(page)
            return LinkCheck.Opened
        }
        return when (val decision = policy.check(url, LinkOrigin.Markdown)) {
            is LinkDecision.Open -> if (opener.open(decision.link) is Outcome.Ok) LinkCheck.Opened else LinkCheck.Refused
            is LinkDecision.AskFirst -> {
                if (asked.size >= MAX_ASKED) asked.remove(asked.keys.first())
                asked[url] = decision.link
                LinkCheck.Ask(decision.shownHost)
            }
            is LinkDecision.Refuse -> LinkCheck.Refused
        }
    }

    override fun openLink(url: String): Boolean {
        val link = asked.remove(url) ?: return false
        return opener.open(link) is Outcome.Ok
    }

    private companion object {
        const val MAX_ASKED = 8
        val SHARED_BINARY_TYPES = setOf(
            "zip", "tar", "gz", "tgz", "bz2", "xz", "7z", "rar", "iso", "img", "bin", "apks", "xapk", "aab",
            "jar", "so", "db", "sqlite", "mp3", "wav", "flac", "ogg", "m4a", "mp4", "mkv", "mov", "avi", "webm",
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "epub",
        )
    }
}
