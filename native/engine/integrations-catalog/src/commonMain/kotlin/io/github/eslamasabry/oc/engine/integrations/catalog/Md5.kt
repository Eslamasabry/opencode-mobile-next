package io.github.eslamasabry.oc.engine.integrations.catalog

/**
 * RFC 1321 MD5 in common code (no java.security). Used only to recognise models.dev's default "sparkle" logo body;
 * it is not a security digest here.
 */
internal object Md5 {
    private val S =
        intArrayOf(
            7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
            5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
            4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
            6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
        )
    private val K = IntArray(64) { i -> (kotlin.math.abs(kotlin.math.sin((i + 1).toDouble())) * 4294967296.0).toLong().toInt() }

    fun hex(input: ByteArray): String {
        val bitLength = input.size.toLong() * 8
        val padded = ((input.size + 8) / 64 + 1) * 64
        val message = input.copyOf(padded)
        message[input.size] = 0x80.toByte()
        for (i in 0 until 8) message[padded - 8 + i] = (bitLength ushr (8 * i)).toByte()
        var a0 = 0x67452301
        var b0 = 0xefcdab89.toInt()
        var c0 = 0x98badcfe.toInt()
        var d0 = 0x10325476
        val m = IntArray(16)
        for (chunk in 0 until padded / 64) {
            for (j in 0 until 16) {
                val o = chunk * 64 + j * 4
                m[j] = (message[o].toInt() and 0xff) or ((message[o + 1].toInt() and 0xff) shl 8) or
                    ((message[o + 2].toInt() and 0xff) shl 16) or ((message[o + 3].toInt() and 0xff) shl 24)
            }
            var a = a0
            var b = b0
            var c = c0
            var d = d0
            for (i in 0 until 64) {
                val f: Int
                val g: Int
                when (i / 16) {
                    0 -> { f = (b and c) or (b.inv() and d); g = i }
                    1 -> { f = (d and b) or (d.inv() and c); g = (5 * i + 1) % 16 }
                    2 -> { f = b xor c xor d; g = (3 * i + 5) % 16 }
                    else -> { f = c xor (b or d.inv()); g = (7 * i) % 16 }
                }
                val temp = d
                d = c
                c = b
                b += (a + f + K[i] + m[g]).rotateLeft(S[i])
                a = temp
            }
            a0 += a
            b0 += b
            c0 += c
            d0 += d
        }
        return buildString {
            for (word in intArrayOf(a0, b0, c0, d0)) {
                for (i in 0 until 4) {
                    val byte = (word ushr (8 * i)) and 0xff
                    append(HEX[byte shr 4])
                    append(HEX[byte and 0xf])
                }
            }
        }
    }

    private const val HEX = "0123456789abcdef"
}
