package io.github.eslamasabry.oc.engine.factsbook

/**
 * SHA-512 (FIPS 180-4), for Ed25519 only. Pure Kotlin like `:core:common`'s SHA-256, so every target signs the same
 * way; checked against the FIPS vectors and the JDK's own digest in the tests.
 */
internal object Sha512 {
    private val K: LongArray =
        (
            "428a2f98d728ae22 7137449123ef65cd b5c0fbcfec4d3b2f e9b5dba58189dbbc 3956c25bf348b538 59f111f1b605d019 " +
                "923f82a4af194f9b ab1c5ed5da6d8118 d807aa98a3030242 12835b0145706fbe 243185be4ee4b28c 550c7dc3d5ffb4e2 " +
                "72be5d74f27b896f 80deb1fe3b1696b1 9bdc06a725c71235 c19bf174cf692694 e49b69c19ef14ad2 efbe4786384f25e3 " +
                "0fc19dc68b8cd5b5 240ca1cc77ac9c65 2de92c6f592b0275 4a7484aa6ea6e483 5cb0a9dcbd41fbd4 76f988da831153b5 " +
                "983e5152ee66dfab a831c66d2db43210 b00327c898fb213f bf597fc7beef0ee4 c6e00bf33da88fc2 d5a79147930aa725 " +
                "06ca6351e003826f 142929670a0e6e70 27b70a8546d22ffc 2e1b21385c26c926 4d2c6dfc5ac42aed 53380d139d95b3df " +
                "650a73548baf63de 766a0abb3c77b2a8 81c2c92e47edaee6 92722c851482353b a2bfe8a14cf10364 a81a664bbc423001 " +
                "c24b8b70d0f89791 c76c51a30654be30 d192e819d6ef5218 d69906245565a910 f40e35855771202a 106aa07032bbd1b8 " +
                "19a4c116b8d2d0c8 1e376c085141ab53 2748774cdf8eeb99 34b0bcb5e19b48a8 391c0cb3c5c95a63 4ed8aa4ae3418acb " +
                "5b9cca4f7763e373 682e6ff3d6b2b8a3 748f82ee5defb2fc 78a5636f43172f60 84c87814a1f0ab72 8cc702081a6439ec " +
                "90befffa23631e28 a4506cebde82bde9 bef9a3f7b2c67915 c67178f2e372532b ca273eceea26619c d186b8c721c0c207 " +
                "eada7dd6cde0eb1e f57d4f7fee6ed178 06f067aa72176fba 0a637dc5a2c898a6 113f9804bef90dae 1b710b35131c471b " +
                "28db77f523047d84 32caab7b40c72493 3c9ebe0a15c9bebc 431d67c49c100d4c 4cc5d4becb3e42b6 597f299cfc657e2a " +
                "5fcb6fab3ad6faec 6c44198c4a475817"
            )
            .split(' ')
            .map { it.toULong(16).toLong() }
            .toLongArray()

    private val IV: LongArray =
        "6a09e667f3bcc908 bb67ae8584caa73b 3c6ef372fe94f82b a54ff53a5f1d36f1 510e527fade682d1 9b05688c2b3e6c1f 1f83d9abfb41bd6b 5be0cd19137e2179"
            .split(' ')
            .map { it.toULong(16).toLong() }
            .toLongArray()

    private fun rotr(x: Long, n: Int): Long = (x ushr n) or (x shl (64 - n))

    fun digest(vararg parts: ByteArray): ByteArray {
        val length = parts.sumOf { it.size }
        val message = ByteArray(((length + 17 + 127) / 128) * 128)
        var at = 0
        for (part in parts) {
            part.copyInto(message, at)
            at += part.size
        }
        message[length] = 0x80.toByte()
        val bits = length.toLong() * 8
        for (i in 0 until 8) message[message.size - 1 - i] = (bits ushr (8 * i)).toByte()

        val h = IV.copyOf()
        val w = LongArray(80)
        for (block in 0 until message.size / 128) {
            for (t in 0 until 16) {
                var v = 0L
                for (b in 0 until 8) v = (v shl 8) or (message[block * 128 + t * 8 + b].toLong() and 0xff)
                w[t] = v
            }
            for (t in 16 until 80) {
                val s0 = rotr(w[t - 15], 1) xor rotr(w[t - 15], 8) xor (w[t - 15] ushr 7)
                val s1 = rotr(w[t - 2], 19) xor rotr(w[t - 2], 61) xor (w[t - 2] ushr 6)
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
            for (t in 0 until 80) {
                val t1 = hh + (rotr(e, 14) xor rotr(e, 18) xor rotr(e, 41)) + ((e and f) xor (e.inv() and g)) + K[t] + w[t]
                val t2 = (rotr(a, 28) xor rotr(a, 34) xor rotr(a, 39)) + ((a and b) xor (a and c) xor (b and c))
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
        val out = ByteArray(64)
        for (i in 0 until 8) for (b in 0 until 8) out[i * 8 + b] = (h[i] ushr (56 - 8 * b)).toByte()
        return out
    }
}
