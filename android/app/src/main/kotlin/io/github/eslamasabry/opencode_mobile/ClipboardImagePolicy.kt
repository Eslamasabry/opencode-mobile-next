package io.github.eslamasabry.opencode_mobile

import java.io.IOException
import java.io.InputStream

/**
 * Pure rules for `oc/clipboard` (lib/platform/clipboard_image.dart): which
 * clip types count as a picture, and how many bytes may be read for one.
 * Kept free of Android types so plain JVM tests cover it.
 */
internal object ClipboardImagePolicy {
    /** Same per-attachment cap as the composer (chat_attachments.dart). */
    const val MAX_BYTES = 10 * 1024 * 1024

    /** Raised when the clip's image is larger than [MAX_BYTES]. */
    class TooLargeException : IOException("clipboard image exceeds $MAX_BYTES bytes")

    fun isImageMime(mime: String?): Boolean {
        val type = mime?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        return type.startsWith("image/") && type.length > "image/".length
    }

    /** The first image type among [mimes] (a clip's declared types), else null. */
    fun firstImageMime(mimes: List<String?>): String? =
        mimes.firstOrNull { isImageMime(it) }?.substringBefore(';')?.trim()?.lowercase()

    /**
     * Reads [stream] fully, refusing more than [maxBytes]. Reads one byte past
     * the cap so an oversize picture is refused instead of cut short.
     */
    fun readLimited(stream: InputStream, maxBytes: Int = MAX_BYTES): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw TooLargeException()
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
