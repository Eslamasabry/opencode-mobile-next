package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.media.attach.AttachmentLimits
import io.github.eslamasabry.oc.feature.chat.composer.ComposerInsertedPictures
import io.github.eslamasabry.oc.feature.chat.composer.InsertedPicture
import io.github.eslamasabry.oc.ui.kitchat.composer.KitInsertedPicture
import java.io.IOException
import kotlinx.coroutines.withContext

/*
 * S24-CHAT story 5: reads a picture the keyboard committed into the composer (Gboard GIFs and
 * stickers, the clipboard-image chip). The keyboard hands a content URI with a read grant; it is read
 * here, at most 10 MiB, off the main thread, and only its bytes and type go on. Nothing is logged.
 */
@ContributesIntoSet(AppScope::class, binding = binding<ComposerInsertedPictures>())
@Inject
class AndroidComposerInsertedPictures(
    private val application: Application,
    private val dispatchers: AppDispatchers,
) : ComposerInsertedPictures {
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    override suspend fun read(picture: KitInsertedPicture): Outcome<List<InsertedPicture>> {
        val clip = picture.content.clipEntry.clipData
        val described = clip.description.takeIf { it.mimeTypeCount > 0 }?.getMimeType(0)
        val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        if (uris.isEmpty()) return Outcome.Ok(emptyList())
        return withContext(dispatchers.io) {
            val read = ArrayList<InsertedPicture>()
            for (uri in uris) {
                when (val one = readOne(uri, described)) {
                    is Outcome.Ok -> read += one.value
                    is Outcome.Failed -> if (read.isEmpty()) return@withContext one
                }
            }
            Outcome.Ok(read)
        }
    }

    private fun readOne(uri: Uri, described: String?): Outcome<InsertedPicture> {
        val resolver = application.contentResolver
        val mime = resolver.getType(uri) ?: described
        if (mime != null && !mime.startsWith("image/")) return Outcome.Failed(Failure.Media(MediaProblem.AttachmentUnsupported))
        val max = AttachmentLimits.CLIPBOARD_MAX_BYTES
        return try {
            val stream = resolver.openInputStream(uri) ?: return Outcome.Failed(Failure.Device())
            stream.use { input ->
                // minSdk 26: no readNBytes; read in chunks and stop one byte past the limit.
                val out = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(64 * 1024)
                while (out.size() <= max) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    out.write(chunk, 0, n)
                }
                if (out.size() > max) Outcome.Failed(Failure.Media(MediaProblem.ClipboardImageTooLarge))
                else Outcome.Ok(InsertedPicture(mime, out.toByteArray()))
            }
        } catch (_: IOException) {
            Outcome.Failed(Failure.Device())
        } catch (_: SecurityException) {
            // The keyboard's read grant lapsed or was never given.
            Outcome.Failed(Failure.Device())
        }
    }
}
