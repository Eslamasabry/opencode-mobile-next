package io.github.eslamasabry.oc.engine.project.terminal

/**
 * Decodes UTF-8 that arrives in chunks (a PTY cuts output anywhere): a character split across two chunks comes out
 * whole with the second, never as U+FFFD. Malformed bytes still become U+FFFD (Flutter `Utf8Decoder(allowMalformed)`).
 */
public class Utf8StreamDecoder {
    private var pending = ByteArray(0)

    public fun decode(chunk: ByteArray): String {
        val bytes = if (pending.isEmpty()) chunk else pending + chunk
        val keep = incompleteTail(bytes)
        pending = bytes.copyOfRange(bytes.size - keep, bytes.size)
        return bytes.decodeToString(0, bytes.size - keep)
    }

    /** What is still held back: the end of the stream turns it into U+FFFD. */
    public fun finish(): String {
        val rest = pending
        pending = ByteArray(0)
        return rest.decodeToString()
    }

    /** How many bytes at the end start a sequence the next chunk may complete. */
    private fun incompleteTail(bytes: ByteArray): Int {
        val n = bytes.size
        for (k in 1..minOf(3, n)) {
            val b = bytes[n - k].toInt() and 0xff
            if (b and 0xC0 == 0x80) continue // continuation: look further back
            val need =
                when {
                    b and 0xE0 == 0xC0 -> 2
                    b and 0xF0 == 0xE0 -> 3
                    b and 0xF8 == 0xF0 -> 4
                    else -> return 0
                }
            return if (need > k) k else 0
        }
        return 0
    }
}
