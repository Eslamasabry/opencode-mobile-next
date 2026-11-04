package io.github.eslamasabry.oc.android.platform

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * Chooser or Android's confirming APK installer, for an actual readable file in shared storage
 * only.
 */
public class AndroidLocalFileOpener(context: Context, private val dispatchers: AppDispatchers) {
    private val context = context.applicationContext

    public suspend fun supported(path: String): Boolean =
        withContext(dispatchers.io) { readable(path) != null }

    private fun readable(path: String): File? =
        try {
            val file = File(path).canonicalFile
            val root = Environment.getExternalStorageDirectory().canonicalFile
            file.takeIf {
                it.path.startsWith(root.path + File.separator) && it.isFile && it.canRead()
            }
        } catch (_: Exception) {
            null
        }

    public suspend fun open(
        path: String,
        install: Boolean,
        beforeOpen: () -> Boolean = { true },
    ): Outcome<Unit> {
        val file =
            withContext(dispatchers.io) { readable(path) }
                ?: return Outcome.Failed(Failure.Media(MediaProblem.NoAppToOpen))
        if (install && !file.extension.equals("apk", true))
            return Outcome.Failed(Failure.Media(MediaProblem.NoAppToOpen))
        return withContext(dispatchers.main) {
            try {
                val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                val type =
                    if (install) "application/vnd.android.package-archive"
                    else
                        MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
                val view =
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, type)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                view.clipData = ClipData.newRawUri(file.name, uri)
                val action = if (install) view else Intent.createChooser(view, null)
                action.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                if (!beforeOpen())
                    return@withContext Outcome.Failed(Failure.Media(MediaProblem.NoAppToOpen))
                context.startActivity(action)
                Outcome.Ok(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (_: ActivityNotFoundException) {
                Outcome.Failed(Failure.Media(MediaProblem.NoAppToOpen))
            } catch (_: IllegalArgumentException) {
                Outcome.Failed(Failure.Media(MediaProblem.NoAppToOpen))
            } catch (_: SecurityException) {
                Outcome.Failed(Failure.Media(MediaProblem.NoAppToOpen))
            }
        }
    }
}
