package io.github.eslamasabry.oc.engine.media.preview

/**
 * Strict UTF-8 (plan 10 §11, B-MEDIA-001): a preview shows text only when every byte decodes; it never shows U+FFFD
 * replacement characters. Save keeps the original bytes either way.
 */
public object StrictUtf8 {
    /** The text of [bytes], or null when they are not valid UTF-8 (overlong forms and surrogates included). */
    public fun decode(bytes: ByteArray): String? {
        if (!isValid(bytes)) return null
        return bytes.decodeToString()
    }

    public fun isValid(bytes: ByteArray): Boolean {
        var i = 0
        val n = bytes.size
        while (i < n) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b < 0x80 -> i++
                b in 0xC2..0xDF -> {
                    if (!cont(bytes, i + 1)) return false
                    i += 2
                }
                b in 0xE0..0xEF -> {
                    if (i + 2 >= n) return false
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    val ok =
                        when (b) {
                            0xE0 -> b1 in 0xA0..0xBF // no overlong
                            0xED -> b1 in 0x80..0x9F // no surrogates
                            else -> b1 in 0x80..0xBF
                        }
                    if (!ok || !cont(bytes, i + 2)) return false
                    i += 3
                }
                b in 0xF0..0xF4 -> {
                    if (i + 3 >= n) return false
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    val ok =
                        when (b) {
                            0xF0 -> b1 in 0x90..0xBF
                            0xF4 -> b1 in 0x80..0x8F
                            else -> b1 in 0x80..0xBF
                        }
                    if (!ok || !cont(bytes, i + 2) || !cont(bytes, i + 3)) return false
                    i += 4
                }
                else -> return false
            }
        }
        return true
    }

    /** The UTF-8 byte length of [text] without allocating it (lone surrogates count as the 3-byte U+FFFD). */
    public fun encodedLength(text: String): Long {
        var total = 0L
        var i = 0
        while (i < text.length) {
            val c = text[i]
            total +=
                when {
                    c.code < 0x80 -> 1
                    c.code < 0x800 -> 2
                    c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate() -> {
                        i++
                        4
                    }
                    else -> 3
                }
            i++
        }
        return total
    }

    private fun cont(bytes: ByteArray, i: Int): Boolean = i < bytes.size && (bytes[i].toInt() and 0xC0) == 0x80
}
