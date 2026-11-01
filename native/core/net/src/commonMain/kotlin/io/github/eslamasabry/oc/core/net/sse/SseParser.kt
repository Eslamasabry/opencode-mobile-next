package io.github.eslamasabry.oc.core.net.sse

/** One complete server-sent event. Only frames with non-empty data are ever produced. */
public data class SseFrame(public val event: String?, public val id: String?, public val data: String)

/**
 * Incremental SSE parser: raw bytes in, complete frames out (plan 04 §6.1; port of Flutter `Sse2Parser`, row
 * B-PROTO-033).
 *
 * 1. A line ends at LF only; one trailing CR is stripped. A lone CR is not a line end (Flutter parity).
 * 2. Each completed line is decoded as UTF-8 with malformed sequences replaced, so characters split across chunks
 *    decode whole and bad bytes never throw.
 * 3. `:` lines are comments (heartbeats): ignored here. They still count as bytes for the loop's stall rule, which
 *    times each read, not each frame.
 * 4. `field:value` drops one leading space; a line without a colon is a field with an empty value. `data` lines join
 *    with `\n`; `event` and `id` are kept; `retry` and unknown fields are ignored.
 * 5. A blank line delivers the frame when its data is non-empty.
 * 6. A raw line longer than [maxFrameBytes], or data adding up to more, drops the whole frame up to the next blank line;
 *    the next frame parses normally. Unlike Flutter, a line exactly one byte over the cap cannot end the discard early.
 * 7. [flush] delivers a trailing frame without its blank line; call it only when the body ended cleanly. After an IO
 *    error call [discardPartial] instead.
 * 8. Allocation-light: bytes go into one growable line buffer; only completed lines are copied out.
 *
 * Not thread-safe; one reader feeds it.
 */
public class SseParser(
    private val maxFrameBytes: Int = MAX_FRAME_BYTES,
    private val onFrame: (SseFrame) -> Unit,
) {
    private var line = ByteArray(256)
    private var lineLength = 0
    private var lineOverflow = false

    private val data = ArrayList<String>()
    private var event: String? = null
    private var id: String? = null
    private var frameBytes = 0L
    private var discarding = false

    public fun feed(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) {
        require(offset >= 0 && length >= 0 && offset + length <= bytes.size) { "bad range" }
        var start = offset
        val end = offset + length
        while (start < end) {
            val lf = indexOfLf(bytes, start, end)
            if (lf < 0) {
                append(bytes, start, end - start)
                return
            }
            append(bytes, start, lf - start)
            completeLine()
            start = lf + 1
        }
    }

    /** Delivers a trailing frame not followed by a blank line. Only after a clean end of body. */
    public fun flush() {
        if (lineLength > 0 || lineOverflow) completeLine()
        if (!discarding) dispatch() else resetFrame()
        discarding = false
    }

    /** Drops a partial line and frame: the body ended with an error, so the frame was cut mid-write. */
    public fun discardPartial() {
        lineLength = 0
        lineOverflow = false
        discarding = false
        resetFrame()
    }

    private fun indexOfLf(bytes: ByteArray, from: Int, to: Int): Int {
        for (i in from until to) if (bytes[i] == LF) return i
        return -1
    }

    private fun append(bytes: ByteArray, from: Int, count: Int) {
        if (count == 0 || lineOverflow) return
        if (lineLength.toLong() + count > maxFrameBytes) {
            // Over the cap: forget the line and drop the rest of it up to its LF; the frame is discarded.
            lineOverflow = true
            lineLength = 0
            shrink()
            return
        }
        val needed = lineLength + count
        if (needed > line.size) line = line.copyOf(maxOf(needed, minOf(line.size.toLong() * 2, maxFrameBytes.toLong()).toInt()))
        bytes.copyInto(line, lineLength, from, from + count)
        lineLength = needed
    }

    private fun completeLine() {
        if (lineOverflow) {
            lineOverflow = false
            lineLength = 0
            discarding = true
            return
        }
        var length = lineLength
        lineLength = 0
        if (length > 0 && line[length - 1] == CR) length -= 1
        processLine(length)
        shrink()
    }

    /** A 16 MiB line must not pin 16 MiB for the life of the stream. */
    private fun shrink() {
        if (line.size > SHRINK_ABOVE) line = ByteArray(256)
    }

    private fun processLine(length: Int) {
        if (length == 0) {
            if (discarding) {
                discarding = false
                resetFrame()
            } else {
                dispatch()
            }
            return
        }
        if (discarding) return
        if (line[0] == COLON) return // comment / heartbeat
        val colon = indexOf(COLON, length)
        val fieldEnd = if (colon < 0) length else colon
        var valueStart = if (colon < 0) length else colon + 1
        if (valueStart < length && line[valueStart] == SPACE) valueStart += 1
        when {
            isField(DATA, fieldEnd) -> {
                frameBytes += (length - valueStart)
                if (frameBytes > maxFrameBytes) {
                    discarding = true
                    resetFrame()
                    return
                }
                data += line.decodeToString(valueStart, length)
            }
            isField(EVENT, fieldEnd) -> event = line.decodeToString(valueStart, length)
            isField(ID, fieldEnd) -> id = line.decodeToString(valueStart, length)
            else -> Unit // retry and unknown fields
        }
    }

    private fun indexOf(byte: Byte, length: Int): Int {
        for (i in 0 until length) if (line[i] == byte) return i
        return -1
    }

    private fun isField(name: ByteArray, fieldEnd: Int): Boolean {
        if (fieldEnd != name.size) return false
        for (i in name.indices) if (line[i] != name[i]) return false
        return true
    }

    private fun dispatch() {
        if (data.isEmpty()) {
            resetFrame()
            return
        }
        val frame = SseFrame(event, id, if (data.size == 1) data[0] else data.joinToString("\n"))
        resetFrame()
        if (frame.data.isNotEmpty()) onFrame(frame)
    }

    private fun resetFrame() {
        data.clear()
        event = null
        id = null
        frameBytes = 0
    }

    public companion object {
        /** 16 MiB: the cap for one line and for one frame's data (plan 04 §6.1 rule 6). */
        public const val MAX_FRAME_BYTES: Int = 16 shl 20

        private const val SHRINK_ABOVE = 64 * 1024
        private const val LF: Byte = 10
        private const val CR: Byte = 13
        private const val COLON: Byte = 58
        private const val SPACE: Byte = 32
        private val DATA = "data".encodeToByteArray()
        private val EVENT = "event".encodeToByteArray()
        private val ID = "id".encodeToByteArray()
    }
}
