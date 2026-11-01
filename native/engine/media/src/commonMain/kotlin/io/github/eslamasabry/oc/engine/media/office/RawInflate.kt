package io.github.eslamasabry.oc.engine.media.office

/**
 * Raw DEFLATE (RFC 1951, ZIP method 8) in pure common Kotlin.
 *
 * Plan 10 §4.2 puts `expect object RawInflate` in `:core:common` (C10-2); it does not exist yet, so this module carries a
 * portable decoder with the same contract (a core request in the S3-MEDIAATT report). Pure Kotlin also covers the iOS
 * fallback the plan names. Returns null when the stream is corrupt, ends early, or would exceed [maxOutput] bytes
 * (the zip-bomb guard); never throws for bad input.
 */
internal object RawInflate {
    fun inflate(input: ByteArray, offset: Int, length: Int, maxOutput: Int): ByteArray? =
        try {
            Inflater(input, offset, offset + length, maxOutput).run()
        } catch (_: InflateStop) {
            null
        }

    private class InflateStop : Exception()

    private class Huffman(lengths: IntArray, count: Int) {
        val counts = IntArray(16)
        val symbols = IntArray(count)

        init {
            for (i in 0 until count) counts[lengths[i]]++
            counts[0] = 0
            var left = 1
            for (len in 1..15) {
                left = (left shl 1) - counts[len]
                if (left < 0) throw InflateStop() // over-subscribed
            }
            val offs = IntArray(16)
            for (len in 1 until 15) offs[len + 1] = offs[len] + counts[len]
            for (sym in 0 until count) if (lengths[sym] != 0) symbols[offs[lengths[sym]]++] = sym
        }
    }

    private class Inflater(val src: ByteArray, var pos: Int, val end: Int, val max: Int) {
        var out = ByteArray(minOf(max, maxOf(1024, (end - pos) * 4)).coerceAtLeast(1))
        var size = 0
        var bitBuf = 0
        var bitCnt = 0

        fun bits(need: Int): Int {
            var v = bitBuf
            while (bitCnt < need) {
                if (pos >= end) throw InflateStop()
                v = v or ((src[pos++].toInt() and 0xFF) shl bitCnt)
                bitCnt += 8
            }
            bitBuf = v ushr need
            bitCnt -= need
            return v and ((1 shl need) - 1)
        }

        fun put(b: Int) {
            if (size >= max) throw InflateStop()
            if (size == out.size) out = out.copyOf(minOf(max, out.size * 2).coerceAtLeast(size + 1))
            out[size++] = b.toByte()
        }

        fun decode(h: Huffman): Int {
            var code = 0
            var first = 0
            var index = 0
            for (len in 1..15) {
                code = code or bits(1)
                val count = h.counts[len]
                if (code - count < first) return h.symbols[index + (code - first)]
                index += count
                first += count
                first = first shl 1
                code = code shl 1
            }
            throw InflateStop()
        }

        fun run(): ByteArray {
            do {
                val last = bits(1)
                when (bits(2)) {
                    0 -> stored()
                    1 -> codes(FIXED_LEN, FIXED_DIST)
                    2 -> dynamic()
                    else -> throw InflateStop()
                }
            } while (last == 0)
            return out.copyOf(size)
        }

        fun stored() {
            bitBuf = 0
            bitCnt = 0
            if (pos + 4 > end) throw InflateStop()
            val len = (src[pos].toInt() and 0xFF) or ((src[pos + 1].toInt() and 0xFF) shl 8)
            val nlen = (src[pos + 2].toInt() and 0xFF) or ((src[pos + 3].toInt() and 0xFF) shl 8)
            pos += 4
            if (len != (nlen.inv() and 0xFFFF)) throw InflateStop()
            if (pos + len > end) throw InflateStop()
            repeat(len) { put(src[pos++].toInt()) }
        }

        fun codes(lencode: Huffman, distcode: Huffman) {
            while (true) {
                var symbol = decode(lencode)
                if (symbol < 256) {
                    put(symbol)
                } else if (symbol == 256) {
                    return
                } else {
                    symbol -= 257
                    if (symbol >= 29) throw InflateStop()
                    val len = LBASE[symbol] + bits(LEXT[symbol])
                    val ds = decode(distcode)
                    if (ds >= 30) throw InflateStop()
                    val dist = DBASE[ds] + bits(DEXT[ds])
                    if (dist > size) throw InflateStop()
                    repeat(len) { put(out[size - dist].toInt()) }
                }
            }
        }

        fun dynamic() {
            val nlen = bits(5) + 257
            val ndist = bits(5) + 1
            val ncode = bits(4) + 4
            if (nlen > 286 || ndist > 30) throw InflateStop()
            val lengths = IntArray(320)
            for (i in 0 until ncode) lengths[ORDER[i]] = bits(3)
            val lencode = Huffman(lengths, 19)
            var index = 0
            while (index < nlen + ndist) {
                var symbol = decode(lencode)
                if (symbol < 16) {
                    lengths[index++] = symbol
                } else {
                    var len = 0
                    when (symbol) {
                        16 -> {
                            if (index == 0) throw InflateStop()
                            len = lengths[index - 1]
                            symbol = 3 + bits(2)
                        }
                        17 -> symbol = 3 + bits(3)
                        else -> symbol = 11 + bits(7)
                    }
                    if (index + symbol > nlen + ndist) throw InflateStop()
                    repeat(symbol) { lengths[index++] = len }
                }
            }
            if (lengths[256] == 0) throw InflateStop()
            val lit = Huffman(lengths, nlen)
            val dist = Huffman(lengths.copyOfRange(nlen, nlen + ndist).copyOf(320), ndist)
            codes(lit, dist)
        }
    }

    private val ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)
    private val LBASE =
        intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
    private val LEXT = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
    private val DBASE =
        intArrayOf(
            1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097,
            6145, 8193, 12289, 16385, 24577,
        )
    private val DEXT = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)

    private val FIXED_LEN: Huffman by lazy {
        val l = IntArray(320)
        for (i in 0 until 144) l[i] = 8
        for (i in 144 until 256) l[i] = 9
        for (i in 256 until 280) l[i] = 7
        for (i in 280 until 288) l[i] = 8
        Huffman(l, 288)
    }
    private val FIXED_DIST: Huffman by lazy { Huffman(IntArray(320) { if (it < 30) 5 else 0 }, 30) }
}
