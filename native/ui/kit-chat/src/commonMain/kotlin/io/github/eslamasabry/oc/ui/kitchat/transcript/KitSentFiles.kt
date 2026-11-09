package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import io.github.eslamasabry.oc.ui.kitchat.LocalKitChatWork
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/** One file a sent prompt carried: its name, kind and address (a `data:` address for a picture from this phone). */
@Immutable
public data class KitSentFile(val name: String, val mime: String?, val url: String?)

/** What the prompt bubble shows for [KitSentFile]s: pictures as thumbnails, everything else as a chip. */
@Immutable
public class KitSentFiles(public val photos: List<KitPromptPhoto>, public val attachments: List<KitPromptAttachment>)

/**
 * Splits a sent prompt's files for [KitPromptBubble]. A picture whose bytes ride in its `data:` address is decoded off
 * the main thread and shows as a thumbnail (a quiet tile until then); a picture whose bytes are not here (a server or
 * web address) and any other file show as a read-only chip with the file name. Addresses are never fetched or opened.
 */
@Composable
public fun rememberKitSentFiles(files: List<KitSentFile>): KitSentFiles {
    if (files.isEmpty()) return remember { KitSentFiles(emptyList(), emptyList()) }
    val work = LocalKitChatWork.current
    val inline = remember(files) { files.filter { it.isInlineImage() } }
    // A picture already decoded (by the sending bubble) is there on the first frame: the echoed message keeps the
    // very same bitmap, so the swap from bubble to message does not blink.
    val decoded by
        produceState<Map<KitSentFile, ImageBitmap?>>(
            inline.filter { SentPictureCache[it] != null }.associateWith { SentPictureCache[it] },
            inline,
        ) {
            value =
                withContext(work) { inline.associateWith { SentPictureCache[it] ?: decode(it) } }
                    .also { got -> got.forEach { (file, bitmap) -> SentPictureCache[file] = bitmap } }
        }
    return remember(files, decoded) {
        val photos = ArrayList<KitPromptPhoto>()
        val chips = ArrayList<KitPromptAttachment>()
        for (file in files) {
            val label = file.name.ifBlank { file.mime ?: "" }
            when {
                file.isInlineImage() -> photos += KitPromptPhoto(label, decoded[file])
                file.mime?.startsWith("image/") == true ->
                    chips += KitPromptAttachment(label, KitPromptAttachmentKind.Image)
                else -> chips += KitPromptAttachment(label, KitPromptAttachmentKind.File)
            }
        }
        KitSentFiles(photos, chips)
    }
}

private fun KitSentFile.isInlineImage(): Boolean =
    mime?.startsWith("image/") == true && url?.startsWith("data:") == true

/** The bytes inside a base64 `data:` address, or null when it is not one. */
@OptIn(ExperimentalEncodingApi::class)
public fun dataUrlBytes(url: String): ByteArray? {
    if (!url.startsWith("data:")) return null
    val comma = url.indexOf(',')
    if (comma < 0 || !url.substring(5, comma).endsWith(";base64")) return null
    return try {
        Base64.Mime.decode(url, comma + 1, url.length)
    } catch (_: IllegalArgumentException) {
        null
    }
}

@Suppress("TooGenericExceptionCaught") // Refused bytes stay a named chip; decoder details never reach the UI.
private fun decode(file: KitSentFile): ImageBitmap? {
    val bytes = file.url?.let(::dataUrlBytes) ?: return null
    return try {
        if (bytes.isEmpty()) null else bytes.decodeToImageBitmap()
    } catch (_: Exception) {
        null
    }
}

/** The last few decoded sent pictures by address; touched only from composition. */
internal object SentPictureCache {
    private const val LIMIT = 16
    private val map = object : LinkedHashMap<String, ImageBitmap>(LIMIT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean = size > LIMIT
    }

    operator fun get(file: KitSentFile): ImageBitmap? = file.url?.let { map[it] }

    operator fun set(file: KitSentFile, bitmap: ImageBitmap?) {
        val url = file.url ?: return
        if (bitmap != null) map[url] = bitmap
    }
}
