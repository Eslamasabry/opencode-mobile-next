package io.github.eslamasabry.oc.engine.media.attach

import kotlin.io.encoding.Base64

/** `data:` URLs, the representation attachments keep everywhere (plan 10 §10.1, 05 §5). */
public object DataUrl {
    public class Decoded(public val mime: String?, public val bytes: ByteArray)

    public fun encode(mime: String, bytes: ByteArray): String = "data:$mime;base64,${Base64.encode(bytes)}"

    /** Decoded bytes of a `data:` URL without decoding it (Flutter `_attachmentByteLength`); 0 for anything else. */
    public fun decodedLength(url: String): Long {
        if (!url.startsWith("data:")) return 0
        val comma = url.indexOf(',')
        if (comma < 0) return 0
        val header = url.substring(0, comma)
        val payloadLength = url.length - comma - 1
        if (!header.endsWith(";base64")) return url.substring(comma + 1).encodeToByteArray().size.toLong()
        val padding = if (url.endsWith("==")) 2 else if (url.endsWith("=")) 1 else 0
        return (payloadLength.toLong() * 3 / 4) - padding
    }

    /** The bytes of a `data:` URL, or null when it is not one or does not decode. */
    public fun decode(url: String): Decoded? {
        if (!url.startsWith("data:")) return null
        val comma = url.indexOf(',')
        if (comma < 0) return null
        val header = url.substring(5, comma)
        val parts = header.split(';')
        val mime = parts.first().trim().lowercase().ifEmpty { null }
        val payload = url.substring(comma + 1)
        return if (parts.drop(1).any { it.trim().equals("base64", ignoreCase = true) }) {
            try {
                Decoded(mime, Base64.decode(payload))
            } catch (_: IllegalArgumentException) {
                null
            }
        } else {
            percentDecode(payload)?.let { Decoded(mime, it) }
        }
    }

    private fun percentDecode(text: String): ByteArray? {
        val out = ArrayList<Byte>(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '%') {
                if (i + 2 >= text.length) return null
                val v = text.substring(i + 1, i + 3).toIntOrNull(16) ?: return null
                out += v.toByte()
                i += 3
            } else {
                c.toString().encodeToByteArray().forEach { out += it }
                i++
            }
        }
        return out.toByteArray()
    }
}
