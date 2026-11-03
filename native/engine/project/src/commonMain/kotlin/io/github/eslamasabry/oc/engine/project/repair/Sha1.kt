package io.github.eslamasabry.oc.engine.project.repair

/** FIPS 180-4 SHA-1, pure Kotlin: Git names objects and packs by it, and common code has no crypto dependency. */
internal object Sha1 {
    fun digest(input: ByteArray, length: Int = input.size): ByteArray {
        require(length in 0..input.size)
        var h0 = 0x67452301
        var h1 = 0xEFCDAB89.toInt()
        var h2 = 0x98BADCFE.toInt()
        var h3 = 0x10325476
        var h4 = 0xC3D2E1F0.toInt()
        val bitLength = length.toLong() * 8
        val padded = ((length + 9 + 63) / 64) * 64
        val w = IntArray(80)
        val block = ByteArray(64)
        var offset = 0
        while (offset < padded) {
            for (i in 0 until 64) {
                val at = offset + i
                block[i] =
                    when {
                        at < length -> input[at]
                        at == length -> 0x80.toByte()
                        at >= padded - 8 -> (bitLength ushr ((padded - 1 - at) * 8)).toByte()
                        else -> 0
                    }
            }
            for (t in 0 until 16) {
                w[t] = ((block[t * 4].toInt() and 0xff) shl 24) or ((block[t * 4 + 1].toInt() and 0xff) shl 16) or
                    ((block[t * 4 + 2].toInt() and 0xff) shl 8) or (block[t * 4 + 3].toInt() and 0xff)
            }
            for (t in 16 until 80) w[t] = (w[t - 3] xor w[t - 8] xor w[t - 14] xor w[t - 16]).rotateLeft(1)
            var a = h0
            var b = h1
            var c = h2
            var d = h3
            var e = h4
            for (t in 0 until 80) {
                val (f, k) =
                    when {
                        t < 20 -> ((b and c) or (b.inv() and d)) to 0x5A827999
                        t < 40 -> (b xor c xor d) to 0x6ED9EBA1
                        t < 60 -> ((b and c) or (b and d) or (c and d)) to 0x8F1BBCDC.toInt()
                        else -> (b xor c xor d) to 0xCA62C1D6.toInt()
                    }
                val temp = a.rotateLeft(5) + f + e + k + w[t]
                e = d
                d = c
                c = b.rotateLeft(30)
                b = a
                a = temp
            }
            h0 += a
            h1 += b
            h2 += c
            h3 += d
            h4 += e
            offset += 64
        }
        val out = ByteArray(20)
        intArrayOf(h0, h1, h2, h3, h4).forEachIndexed { i, h ->
            out[i * 4] = (h ushr 24).toByte()
            out[i * 4 + 1] = (h ushr 16).toByte()
            out[i * 4 + 2] = (h ushr 8).toByte()
            out[i * 4 + 3] = h.toByte()
        }
        return out
    }

    fun hex(input: ByteArray): String = digest(input).toHex()
}

internal fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
