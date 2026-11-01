package io.github.eslamasabry.oc.android.media.clipboard

import io.github.eslamasabry.oc.engine.media.attach.AttachmentLimits
import java.io.IOException
import java.io.InputStream

/**
 * Pure rules for the clipboard picture (copied from the Flutter app's `ClipboardImagePolicy.kt`, plan 10 §10.8): which
 * clip types count as a picture, and how many bytes may be read for one. Free of Android types for plain JVM tests.
 */
internal object ClipboardImagePolicy {
    /** Same per-attachment cap as the composer. */
    const val MAX_BYTES: Int = AttachmentLimits.CLIPBOARD_MAX_BYTES

    /** Raised when the clip's image is larger than the cap. */
    class TooLargeException(maxBytes: Int) : IOException("clipboard image exceeds $maxBytes bytes")

    fun isImageMime(mime: String?): Boolean {
        val type = mime?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        return type.startsWith("image/") && type.length > "image/".length
    }

    /** The first image type among [mimes] (a clip's declared types), else null. */
    fun firstImageMime(mimes: List<String?>): String? =
        mimes.firstOrNull { isImageMime(it) }?.substringBefore(';')?.trim()?.lowercase()

    /** Reads [stream] fully, refusing more than [maxBytes]; reads one byte past the cap so oversize is refused, not cut. */
    fun readLimited(stream: InputStream, maxBytes: Int = MAX_BYTES): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val want = minOf(buffer.size, maxBytes + 1 - total)
            val read = stream.read(buffer, 0, want)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw TooLargeException(maxBytes)
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
