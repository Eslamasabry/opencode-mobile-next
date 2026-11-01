package io.github.eslamasabry.oc.android.media.clipboard

import android.content.ClipboardManager
import android.content.Context
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.media.attach.ClipboardImage
import io.github.eslamasabry.oc.engine.media.attach.ClipboardImages
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The clipboard picture (port of the Flutter app's `ClipboardImageBridge.kt`, plan 10 §10.8). [hasImage] looks only at
 * the clip description (no content access, no Android 12+ toast); [read] opens the first item's URI and is the call
 * Android 12+ announces with its own toast. `SecurityException`/`IOException` mean "no picture".
 */
public class AndroidClipboardImages(
    context: Context,
    private val dispatchers: AppDispatchers,
) : ClipboardImages {
    private val app = context.applicationContext

    private fun manager(): ClipboardManager? = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    override suspend fun hasImage(): Boolean =
        withContext(dispatchers.main) {
            val description = manager()?.primaryClipDescription ?: return@withContext false
            (0 until description.mimeTypeCount).any { ClipboardImagePolicy.isImageMime(description.getMimeType(it)) }
        }

    override suspend fun read(maxBytes: Int): Outcome<ClipboardImage?> {
        val clip =
            withContext(dispatchers.main) {
                try {
                    manager()?.primaryClip
                } catch (_: SecurityException) {
                    null
                }
            } ?: return Outcome.Ok(null)
        return withContext(dispatchers.io) {
            try {
                val uri = clip.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri ?: return@withContext Outcome.Ok(null)
                val declared = (0 until clip.description.mimeTypeCount).map { clip.description.getMimeType(it) }
                val mime =
                    ClipboardImagePolicy.firstImageMime(declared + app.contentResolver.getType(uri))
                        ?: return@withContext Outcome.Ok(null)
                val bytes = app.contentResolver.openInputStream(uri)?.use { ClipboardImagePolicy.readLimited(it, maxBytes) }
                if (bytes == null || bytes.isEmpty()) Outcome.Ok(null) else Outcome.Ok(ClipboardImage(mime, bytes))
            } catch (_: ClipboardImagePolicy.TooLargeException) {
                Outcome.Failed(Failure.Media(MediaProblem.ClipboardImageTooLarge))
            } catch (_: IOException) {
                Outcome.Ok(null)
            } catch (_: SecurityException) {
                Outcome.Ok(null)
            }
        }
    }
}
