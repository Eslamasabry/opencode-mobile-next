package io.github.eslamasabry.oc.ui.kit.content.qr

/**
 * A self-contained QR Code Model 2 encoder: byte mode (UTF-8), error correction M, the smallest version 1…40 that holds
 * the data, and the mask chosen by [QrRules.bestMask] (plan 10 §14.1, M13).
 *
 * Native: plan 10 pins `qrose-encoder-matrix` 1.3.1 for the matrix; the catalog has no entry for it yet, so this unit
 * carries the encoder (about 250 lines, verified by decoding with ZXing core in tests). Swapping to qrose later changes
 * only [QrEncoder.encode]; the mask rule and the drawing stay. Pure: no Compose, no platform.
 */
public object QrEncoder {
    /** The longest byte payload error correction M can hold (version 40). */
    public const val MAX_BYTES: Int = 2331

    /** The symbol for [data], or `null` when it is empty or too long for any version at error correction M. */
    public fun encode(data: String): QrMatrix? {
        if (data.isEmpty()) return null
        val bytes = data.encodeToByteArray()
        val version = (1..40).firstOrNull { v -> fitsIn(bytes.size, v) } ?: return null
        val codewords = addErrorCorrection(dataCodewords(bytes, version), version)
        val mask = QrRules.bestMask { m -> Builder(version).apply { place(codewords, m, test = true) }.toMatrix(m) }
        return Builder(version).apply { place(codewords, mask, test = false) }.toMatrix(mask)
    }

    /** Builds the symbol for one fixed [mask] (tests use it to compare the penalty of every mask). */
    internal fun encodeWithMask(data: String, mask: Int, test: Boolean = false): QrMatrix? {
        val bytes = data.encodeToByteArray()
        val version = (1..40).firstOrNull { v -> fitsIn(bytes.size, v) } ?: return null
        val codewords = addErrorCorrection(dataCodewords(bytes, version), version)
        return Builder(version).apply { place(codewords, mask, test) }.toMatrix(mask)
    }

    // --- capacity (ISO 18004 table 9, error correction M)

    private val ECC_PER_BLOCK_M =
        intArrayOf(
            10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26,
            26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28,
        )
    private val BLOCKS_M =
        intArrayOf(
            1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16,
            17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49,
        )

    internal fun sizeOf(version: Int): Int = version * 4 + 17

    private fun rawDataModules(version: Int): Int {
        var result = (16 * version + 128) * version + 64
        if (version >= 2) {
            val align = version / 7 + 2
            result -= (25 * align - 10) * align - 55
            if (version >= 7) result -= 36
        }
        return result
    }

    private fun dataCodewordCount(version: Int): Int =
        rawDataModules(version) / 8 - ECC_PER_BLOCK_M[version - 1] * BLOCKS_M[version - 1]

    private fun countBits(version: Int): Int = if (version <= 9) 8 else 16

    private fun fitsIn(byteCount: Int, version: Int): Boolean =
        byteCount < (1 shl countBits(version)) && 4 + countBits(version) + 8 * byteCount <= dataCodewordCount(version) * 8

    // --- data codewords (byte mode, terminator, pad bytes 0xEC 0x11)

    private fun dataCodewords(bytes: ByteArray, version: Int): ByteArray {
        val capacityBits = dataCodewordCount(version) * 8
        val bits = BitBuffer()
        bits.append(0b0100, 4)
        bits.append(bytes.size, countBits(version))
        for (b in bytes) bits.append(b.toInt() and 0xFF, 8)
        bits.append(0, minOf(4, capacityBits - bits.length))
        bits.append(0, (8 - bits.length % 8) % 8)
        var pad = 0xEC
        while (bits.length < capacityBits) {
            bits.append(pad, 8)
            pad = pad xor 0xEC xor 0x11
        }
        return bits.toBytes()
    }

    private class BitBuffer {
        private val bits = ArrayList<Boolean>()
        val length: Int get() = bits.size

        fun append(value: Int, count: Int) {
            for (i in count - 1 downTo 0) bits.add((value ushr i) and 1 == 1)
        }

        fun toBytes(): ByteArray {
            val out = ByteArray(bits.size / 8)
            for (i in bits.indices) if (bits[i]) out[i / 8] = (out[i / 8].toInt() or (1 shl (7 - i % 8))).toByte()
            return out
        }
    }

    // --- Reed–Solomon over GF(256), polynomial 0x11D, and block interleaving

    private fun addErrorCorrection(data: ByteArray, version: Int): ByteArray {
        val blocks = BLOCKS_M[version - 1]
        val eccLen = ECC_PER_BLOCK_M[version - 1]
        val raw = rawDataModules(version) / 8
        val shortBlocks = blocks - raw % blocks
        val shortLen = raw / blocks
        val divisor = rsDivisor(eccLen)
        val dataBlocks = ArrayList<ByteArray>(blocks)
        val eccBlocks = ArrayList<ByteArray>(blocks)
        var k = 0
        for (i in 0 until blocks) {
            val len = shortLen - eccLen + if (i < shortBlocks) 0 else 1
            val dat = data.copyOfRange(k, k + len)
            k += len
            dataBlocks += dat
            eccBlocks += rsRemainder(dat, divisor)
        }
        val out = ArrayList<Byte>(raw)
        val longest = shortLen - eccLen + 1
        for (i in 0 until longest) for (b in dataBlocks) if (i < b.size) out += b[i]
        for (i in 0 until eccLen) for (b in eccBlocks) out += b[i]
        return out.toByteArray()
    }

    private fun gfMultiply(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }

    private fun rsDivisor(degree: Int): IntArray {
        val result = IntArray(degree)
        result[degree - 1] = 1
        var root = 1
        repeat(degree) {
            for (j in 0 until degree) {
                result[j] = gfMultiply(result[j], root)
                if (j + 1 < degree) result[j] = result[j] xor result[j + 1]
            }
            root = gfMultiply(root, 0x02)
        }
        return result
    }

    private fun rsRemainder(data: ByteArray, divisor: IntArray): ByteArray {
        val result = IntArray(divisor.size)
        for (b in data) {
            val factor = (b.toInt() and 0xFF) xor result[0]
            for (i in 0 until result.size - 1) result[i] = result[i + 1]
            result[result.size - 1] = 0
            for (i in result.indices) result[i] = result[i] xor gfMultiply(divisor[i], factor)
        }
        return ByteArray(result.size) { result[it].toByte() }
    }

    // --- module placement

    /**
     * One symbol under construction. `test = true` is the mask trial of Dart `qr` (`makeImpl(true, …)`, Kazuhiko
     * Arase's original): format and version bits and the fixed dark module stay light, so every mask is scored on the
     * same function area and the choice matches the Flutter app's.
     */
    private class Builder(val version: Int) {
        val size = sizeOf(version)
        val dark = BooleanArray(size * size)
        val function = BooleanArray(size * size)

        fun set(col: Int, row: Int, value: Boolean) {
            dark[row * size + col] = value
            function[row * size + col] = true
        }

        fun place(codewords: ByteArray, mask: Int, test: Boolean) {
            drawFunctionPatterns()
            drawFormat(mask, test)
            if (version >= 7) drawVersion(test)
            drawCodewords(codewords)
            applyMask(mask)
        }

        fun toMatrix(mask: Int): QrMatrix = QrMatrix(size, version, mask, dark.copyOf())

        private fun drawFunctionPatterns() {
            for (i in 0 until size) {
                set(6, i, i % 2 == 0)
                set(i, 6, i % 2 == 0)
            }
            finder(3, 3)
            finder(size - 4, 3)
            finder(3, size - 4)
            val align = alignmentPositions()
            val n = align.size
            for (i in 0 until n) for (j in 0 until n) {
                if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) continue
                alignment(align[i], align[j])
            }
        }

        private fun finder(x: Int, y: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                val xx = x + dx
                val yy = y + dy
                if (xx in 0 until size && yy in 0 until size) set(xx, yy, dist != 2 && dist != 4)
            }
        }

        private fun alignment(x: Int, y: Int) {
            for (dy in -2..2) for (dx in -2..2) set(x + dx, y + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
        }

        private fun alignmentPositions(): IntArray {
            if (version == 1) return IntArray(0)
            val n = version / 7 + 2
            val step = (version * 8 + n * 3 + 5) / (n * 4 - 4) * 2
            val result = IntArray(n)
            result[0] = 6
            var pos = size - 7
            for (i in n - 1 downTo 1) {
                result[i] = pos
                pos -= step
            }
            return result
        }

        private fun drawFormat(mask: Int, test: Boolean) {
            val data = (0 shl 3) or mask // error correction M = 0b00
            var rem = data
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            val bits = ((data shl 10) or rem) xor 0x5412
            fun bit(i: Int) = !test && (bits ushr i) and 1 == 1
            for (i in 0..5) set(8, i, bit(i))
            set(8, 7, bit(6))
            set(8, 8, bit(7))
            set(7, 8, bit(8))
            for (i in 9 until 15) set(14 - i, 8, bit(i))
            for (i in 0 until 8) set(size - 1 - i, 8, bit(i))
            for (i in 8 until 15) set(8, size - 15 + i, bit(i))
            set(8, size - 8, !test)
        }

        private fun drawVersion(test: Boolean) {
            var rem = version
            repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
            val bits = (version shl 12) or rem
            for (i in 0 until 18) {
                val b = !test && (bits ushr i) and 1 == 1
                val a = size - 11 + i % 3
                val c = i / 3
                set(a, c, b)
                set(c, a, b)
            }
        }

        private fun drawCodewords(data: ByteArray) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) {
                    for (j in 0..1) {
                        val x = right - j
                        val upward = ((right + 1) and 2) == 0
                        val y = if (upward) size - 1 - vert else vert
                        if (!function[y * size + x] && i < data.size * 8) {
                            dark[y * size + x] = ((data[i ushr 3].toInt() ushr (7 - (i and 7))) and 1) == 1
                            i++
                        }
                    }
                }
                right -= 2
            }
        }

        private fun applyMask(mask: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                if (function[y * size + x]) continue
                val invert =
                    when (mask) {
                        0 -> (x + y) % 2 == 0
                        1 -> y % 2 == 0
                        2 -> x % 3 == 0
                        3 -> (x + y) % 3 == 0
                        4 -> (x / 3 + y / 2) % 2 == 0
                        5 -> x * y % 2 + x * y % 3 == 0
                        6 -> (x * y % 2 + x * y % 3) % 2 == 0
                        else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                    }
                if (invert) dark[y * size + x] = !dark[y * size + x]
            }
        }
    }
}
