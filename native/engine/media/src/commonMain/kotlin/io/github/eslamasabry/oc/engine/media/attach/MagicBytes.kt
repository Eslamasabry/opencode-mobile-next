package io.github.eslamasabry.oc.engine.media.attach

/**
 * Picture types by their bytes (plan 10 §10.3 step 3): PNG, JPEG, GIF87a/89a, WebP. Anything else is not a prompt
 * photo, whatever its name says (B-MEDIA-008 "fake extensions are rejected").
 */
public object MagicBytes {
    public fun imageMime(bytes: ByteArray): String? {
        fun prefix(vararg v: Int) = bytes.size >= v.size && v.indices.all { (bytes[it].toInt() and 0xFF) == v[it] }
        fun ascii(at: Int, s: String) = bytes.size >= at + s.length && s.indices.all { bytes[at + it].toInt() == s[it].code }
        return when {
            prefix(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
            prefix(0xFF, 0xD8, 0xFF) -> "image/jpeg"
            ascii(0, "GIF87a") || ascii(0, "GIF89a") -> "image/gif"
            ascii(0, "RIFF") && ascii(8, "WEBP") -> "image/webp"
            else -> null
        }
    }

    /** The extension for a pasted picture's name (`pasted-image-<epochMs>.<ext>`, plan 10 §10.8). */
    public fun extensionFor(mime: String?): String =
        when (mime?.substringBefore(';')?.trim()?.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/bmp" -> "bmp"
            else -> "png"
        }
}
