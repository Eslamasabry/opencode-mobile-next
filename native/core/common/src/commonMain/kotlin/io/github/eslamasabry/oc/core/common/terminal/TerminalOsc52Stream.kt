package io.github.eslamasabry.oc.core.common.terminal

/** Incremental, bounded OSC 52 filter before the VT emulator. Never forwards a clipboard query. */
public class TerminalOsc52Stream(private val onCopy: (String) -> Unit) {
    private val prefix = byteArrayOf(27, 93, 53, 50, 59)
    private val pending = ArrayList<Byte>()
    private var clipboard = false
    private var overflow = false
    private var escaped = false

    /**
     * Other terminal bytes are returned unchanged, including escape sequences split across reads.
     */
    public fun feed(bytes: ByteArray, count: Int): ByteArray {
        require(count in 0..bytes.size)
        val output = ArrayList<Byte>(count)
        for (i in 0 until count) {
            val byte = bytes[i]
            if (clipboard) {
                if (byte == 7.toByte() || (escaped && byte == 92.toByte())) {
                    if (!overflow) {
                        if (escaped && pending.lastOrNull() == 27.toByte())
                            pending.removeAt(pending.lastIndex)
                        val body = pending.toByteArray().decodeToString()
                        TerminalOsc52.parse(body.split(';'))?.let(onCopy)
                    }
                    pending.clear()
                    clipboard = false
                    overflow = false
                    escaped = false
                } else {
                    if (pending.size < TerminalOsc52.MAX_BYTES * 4 / 3 + 64) pending.add(byte)
                    else overflow = true
                    escaped = byte == 27.toByte()
                }
                continue
            }
            if (byte == prefix[pending.size]) {
                pending.add(byte)
                if (pending.size == prefix.size) {
                    clipboard = true
                    pending.clear()
                }
            } else {
                output.addAll(pending)
                pending.clear()
                if (byte == prefix[0]) pending.add(byte) else output.add(byte)
            }
        }
        return output.toByteArray()
    }
}
