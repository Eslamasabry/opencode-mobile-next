package io.github.eslamasabry.oc.engine.voice.models

/** An incremental SHA-256 (plan 10 §4.2). Spent after [digestHex]. */
public interface Sha256Digest {
    public fun update(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size)

    /** Lowercase hex, 64 chars. */
    public fun digestHex(): String
}

/**
 * Makes digests. The default is pure Kotlin; Android binds `java.security.MessageDigest` (faster on 375 MB files).
 * TODO(core-bytes): use `:core:common`'s `Sha256` once the core owner adds it (C10-2, INTEGRATE-W1 §7).
 */
public fun interface DigestFactory {
    public fun sha256(): Sha256Digest

    public companion object {
        public val Pure: DigestFactory = DigestFactory { PureSha256() }
    }
}

/** FIPS 180-4 SHA-256 in plain Kotlin, so commonMain can verify a file on every target. */
public class PureSha256 : Sha256Digest {
    private val h =
        intArrayOf(
            0x6a09e667, -0x4498517b, 0x3c6ef372, -0x5ab00ac6, 0x510e527f, -0x64fa9774, 0x1f83d9ab, 0x5be0cd19,
        )
    private val block = ByteArray(64)
    private var blockLength = 0
    private var totalBytes = 0L
    private val w = IntArray(64)
    private var spent = false

    override fun update(bytes: ByteArray, offset: Int, length: Int) {
        check(!spent) { "digest already taken" }
        var i = offset
        val end = offset + length
        totalBytes += length
        if (blockLength > 0) {
            val take = minOf(64 - blockLength, end - i)
            bytes.copyInto(block, blockLength, i, i + take)
            blockLength += take
            i += take
            if (blockLength == 64) {
                compress(block, 0)
                blockLength = 0
            }
        }
        while (end - i >= 64) {
            compress(bytes, i)
            i += 64
        }
        if (i < end) {
            bytes.copyInto(block, 0, i, end)
            blockLength = end - i
        }
    }

    override fun digestHex(): String {
        check(!spent) { "digest already taken" }
        val bitLength = totalBytes * 8
        val padding = ByteArray(if (blockLength < 56) 56 - blockLength else 120 - blockLength)
        padding[0] = 0x80.toByte()
        val lengthBytes = ByteArray(8) { (bitLength ushr (56 - 8 * it)).toByte() }
        val savedTotal = totalBytes
        update(padding, 0, padding.size)
        update(lengthBytes, 0, 8)
        totalBytes = savedTotal
        spent = true
        val out = StringBuilder(64)
        for (word in h) for (shift in intArrayOf(24, 16, 8, 0)) {
            val b = (word ushr shift) and 0xff
            out.append(HEX[b ushr 4]).append(HEX[b and 0xf])
        }
        return out.toString()
    }

    private fun compress(data: ByteArray, at: Int) {
        for (t in 0 until 16) {
            val p = at + t * 4
            w[t] = ((data[p].toInt() and 0xff) shl 24) or ((data[p + 1].toInt() and 0xff) shl 16) or
                ((data[p + 2].toInt() and 0xff) shl 8) or (data[p + 3].toInt() and 0xff)
        }
        for (t in 16 until 64) {
            val s0 = w[t - 15].rotateRight(7) xor w[t - 15].rotateRight(18) xor (w[t - 15] ushr 3)
            val s1 = w[t - 2].rotateRight(17) xor w[t - 2].rotateRight(19) xor (w[t - 2] ushr 10)
            w[t] = w[t - 16] + s0 + w[t - 7] + s1
        }
        var a = h[0]
        var b = h[1]
        var c = h[2]
        var d = h[3]
        var e = h[4]
        var f = h[5]
        var g = h[6]
        var hh = h[7]
        for (t in 0 until 64) {
            val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val ch = (e and f) xor (e.inv() and g)
            val t1 = hh + s1 + ch + K[t] + w[t]
            val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val t2 = s0 + maj
            hh = g
            g = f
            f = e
            e = d + t1
            d = c
            c = b
            b = a
            a = t1 + t2
        }
        h[0] += a
        h[1] += b
        h[2] += c
        h[3] += d
        h[4] += e
        h[5] += f
        h[6] += g
        h[7] += hh
    }

    private companion object {
        const val HEX = "0123456789abcdef"
        val K =
            longArrayOf(
                0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
                0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
                0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
                0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
                0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
                0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
                0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
                0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
            ).let { longs -> IntArray(longs.size) { longs[it].toInt() } }
    }
}
