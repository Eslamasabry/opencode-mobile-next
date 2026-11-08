package io.github.eslamasabry.oc.engine.factsbook

/**
 * Ed25519 (RFC 8032) for the owner key FactsBook approvals are signed with, and for checking the node's signed control
 * history. A line-for-line port of TweetNaCl's public-domain `crypto_sign` (16 limbs of 16 bits), so it needs nothing
 * but [Sha512] and runs the same on every target. The Flutter app used the `cryptography` package; this file is held
 * to the same RFC 8032 vectors and, on desktop, to the JDK's own Ed25519 (`Ed25519Test`).
 *
 * The private key is a 32-byte seed kept in the secret store and never logged; only the public key is shown.
 */
internal object Ed25519 {
    private fun gf(vararg init: Long): LongArray = LongArray(16).also { init.copyInto(it) }

    private val GF0 = gf()
    private val GF1 = gf(1)
    private val D =
        gf(0x78a3, 0x1359, 0x4dca, 0x75eb, 0xd8ab, 0x4141, 0x0a4d, 0x0070, 0xe898, 0x7779, 0x4079, 0x8cc7, 0xfe73, 0x2b6f, 0x6cee, 0x5203)
    private val D2 =
        gf(0xf159, 0x26b2, 0x9b94, 0xebd6, 0xb156, 0x8283, 0x149a, 0x00e0, 0xd130, 0xeef3, 0x80f2, 0x198e, 0xfce7, 0x56df, 0xd9dc, 0x2406)
    private val X =
        gf(0xd51a, 0x8f25, 0x2d60, 0xc956, 0xa7b2, 0x9525, 0xc760, 0x692c, 0xdc5c, 0xfdd6, 0xe231, 0xc0a4, 0x53fe, 0xcd6e, 0x36d3, 0x2169)
    private val Y =
        gf(0x6658, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666)
    private val I =
        gf(0xa0b0, 0x4a0e, 0x1b27, 0xc4ee, 0xe478, 0xad2f, 0x1806, 0x2f43, 0xd7a7, 0x3dfb, 0x0099, 0x2b4d, 0xdf0b, 0x4fc1, 0x2480, 0x2b83)
    private val L =
        longArrayOf(
            0xed, 0xd3, 0xf5, 0x5c, 0x1a, 0x63, 0x12, 0x58, 0xd6, 0x9c, 0xf7, 0xa2, 0xde, 0xf9, 0xde, 0x14,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x10,
        )

    // ---- field arithmetic mod 2^255 - 19 ---------------------------------------------------------------------------

    private fun car(o: LongArray) {
        for (i in 0 until 16) {
            o[i] += 1L shl 16
            val c = o[i] shr 16
            if (i < 15) o[i + 1] += c - 1 else o[0] += 38 * (c - 1)
            o[i] -= c shl 16
        }
    }

    private fun sel(p: LongArray, q: LongArray, b: Int) {
        val c = (b - 1).toLong().inv()
        for (i in 0 until 16) {
            val t = c and (p[i] xor q[i])
            p[i] = p[i] xor t
            q[i] = q[i] xor t
        }
    }

    private fun pack25519(o: ByteArray, n: LongArray) {
        val m = LongArray(16)
        val t = n.copyOf()
        car(t)
        car(t)
        car(t)
        repeat(2) {
            m[0] = t[0] - 0xffed
            for (i in 1 until 15) {
                m[i] = t[i] - 0xffff - ((m[i - 1] shr 16) and 1)
                m[i - 1] = m[i - 1] and 0xffff
            }
            m[15] = t[15] - 0x7fff - ((m[14] shr 16) and 1)
            val b = ((m[15] shr 16) and 1).toInt()
            m[14] = m[14] and 0xffff
            sel(t, m, 1 - b)
        }
        for (i in 0 until 16) {
            o[2 * i] = (t[i] and 0xff).toByte()
            o[2 * i + 1] = (t[i] shr 8).toByte()
        }
    }

    private fun neq(a: LongArray, b: LongArray): Boolean {
        val c = ByteArray(32)
        val d = ByteArray(32)
        pack25519(c, a)
        pack25519(d, b)
        var diff = 0
        for (i in 0 until 32) diff = diff or (c[i].toInt() xor d[i].toInt())
        return diff != 0
    }

    private fun par(a: LongArray): Int {
        val d = ByteArray(32)
        pack25519(d, a)
        return d[0].toInt() and 1
    }

    private fun unpack25519(o: LongArray, n: ByteArray) {
        for (i in 0 until 16) o[i] = (n[2 * i].toLong() and 0xff) + ((n[2 * i + 1].toLong() and 0xff) shl 8)
        o[15] = o[15] and 0x7fff
    }

    private fun add(o: LongArray, a: LongArray, b: LongArray) {
        for (i in 0 until 16) o[i] = a[i] + b[i]
    }

    private fun sub(o: LongArray, a: LongArray, b: LongArray) {
        for (i in 0 until 16) o[i] = a[i] - b[i]
    }

    private fun mul(o: LongArray, a: LongArray, b: LongArray) {
        val t = LongArray(31)
        for (i in 0 until 16) for (j in 0 until 16) t[i + j] += a[i] * b[j]
        for (i in 0 until 15) t[i] += 38 * t[i + 16]
        for (i in 0 until 16) o[i] = t[i]
        car(o)
        car(o)
    }

    private fun sq(o: LongArray, a: LongArray) = mul(o, a, a)

    private fun inv(o: LongArray, i: LongArray) {
        val c = i.copyOf()
        for (a in 253 downTo 0) {
            sq(c, c)
            if (a != 2 && a != 4) mul(c, c, i)
        }
        c.copyInto(o)
    }

    private fun pow2523(o: LongArray, i: LongArray) {
        val c = i.copyOf()
        for (a in 250 downTo 0) {
            sq(c, c)
            if (a != 1) mul(c, c, i)
        }
        c.copyInto(o)
    }

    // ---- points ----------------------------------------------------------------------------------------------------

    private fun point(): Array<LongArray> = Array(4) { LongArray(16) }

    private fun addPoints(p: Array<LongArray>, q: Array<LongArray>) {
        val a = LongArray(16)
        val b = LongArray(16)
        val c = LongArray(16)
        val d = LongArray(16)
        val t = LongArray(16)
        val e = LongArray(16)
        val f = LongArray(16)
        val g = LongArray(16)
        val h = LongArray(16)
        sub(a, p[1], p[0])
        sub(t, q[1], q[0])
        mul(a, a, t)
        add(b, p[0], p[1])
        add(t, q[0], q[1])
        mul(b, b, t)
        mul(c, p[3], q[3])
        mul(c, c, D2)
        mul(d, p[2], q[2])
        add(d, d, d)
        sub(e, b, a)
        sub(f, d, c)
        add(g, d, c)
        add(h, b, a)
        mul(p[0], e, f)
        mul(p[1], h, g)
        mul(p[2], g, f)
        mul(p[3], e, h)
    }

    private fun cswap(p: Array<LongArray>, q: Array<LongArray>, b: Int) {
        for (i in 0 until 4) sel(p[i], q[i], b)
    }

    private fun pack(r: ByteArray, p: Array<LongArray>) {
        val tx = LongArray(16)
        val ty = LongArray(16)
        val zi = LongArray(16)
        inv(zi, p[2])
        mul(tx, p[0], zi)
        mul(ty, p[1], zi)
        pack25519(r, ty)
        r[31] = (r[31].toInt() xor (par(tx) shl 7)).toByte()
    }

    private fun scalarmult(p: Array<LongArray>, q: Array<LongArray>, s: ByteArray) {
        GF0.copyInto(p[0])
        GF1.copyInto(p[1])
        GF1.copyInto(p[2])
        GF0.copyInto(p[3])
        for (i in 255 downTo 0) {
            val b = (s[i / 8].toInt() shr (i and 7)) and 1
            cswap(p, q, b)
            addPoints(q, p)
            addPoints(p, p)
            cswap(p, q, b)
        }
    }

    private fun scalarbase(p: Array<LongArray>, s: ByteArray) {
        val q = point()
        X.copyInto(q[0])
        Y.copyInto(q[1])
        GF1.copyInto(q[2])
        mul(q[3], X, Y)
        scalarmult(p, q, s)
    }

    // ---- scalars mod L ---------------------------------------------------------------------------------------------

    private fun modL(r: ByteArray, offset: Int, x: LongArray) {
        for (i in 63 downTo 32) {
            var carry = 0L
            var j = i - 32
            while (j < i - 12) {
                x[j] += carry - 16 * x[i] * L[j - (i - 32)]
                carry = (x[j] + 128) shr 8
                x[j] -= carry shl 8
                j++
            }
            x[j] += carry
            x[i] = 0
        }
        var carry = 0L
        for (j in 0 until 32) {
            x[j] += carry - (x[31] shr 4) * L[j]
            carry = x[j] shr 8
            x[j] = x[j] and 255
        }
        for (j in 0 until 32) x[j] -= carry * L[j]
        for (i in 0 until 32) {
            x[i + 1] += x[i] shr 8
            r[offset + i] = (x[i] and 255).toByte()
        }
    }

    private fun reduce(r: ByteArray) {
        val x = LongArray(64) { r[it].toLong() and 0xff }
        r.fill(0)
        modL(r, 0, x)
    }

    private fun clamped(seed: ByteArray): ByteArray =
        Sha512.digest(seed).also {
            it[0] = (it[0].toInt() and 248).toByte()
            it[31] = (it[31].toInt() and 127).toByte()
            it[31] = (it[31].toInt() or 64).toByte()
        }

    private fun check(seed: ByteArray) = require(seed.size == 32) { "An Ed25519 seed is 32 bytes" }

    // ---- the API ---------------------------------------------------------------------------------------------------

    /** The 32-byte public key of [seed]. */
    fun publicKey(seed: ByteArray): ByteArray {
        check(seed)
        val d = clamped(seed)
        val p = point()
        scalarbase(p, d)
        return ByteArray(32).also { pack(it, p) }
    }

    /** The 64-byte signature of [message] by [seed]. */
    fun sign(seed: ByteArray, message: ByteArray): ByteArray {
        check(seed)
        val d = clamped(seed)
        val pk = publicKey(seed)
        val r = Sha512.digest(d.copyOfRange(32, 64), message)
        reduce(r)
        val p = point()
        scalarbase(p, r)
        val sig = ByteArray(64)
        pack(sig, p)
        val h = Sha512.digest(sig.copyOfRange(0, 32), pk, message)
        reduce(h)
        val x = LongArray(64)
        for (i in 0 until 32) x[i] = r[i].toLong() and 0xff
        for (i in 0 until 32) for (j in 0 until 32) x[i + j] += (h[i].toLong() and 0xff) * (d[j].toLong() and 0xff)
        modL(sig, 32, x)
        return sig
    }

    private fun unpackNeg(r: Array<LongArray>, p: ByteArray): Boolean {
        val t = LongArray(16)
        val chk = LongArray(16)
        val num = LongArray(16)
        val den = LongArray(16)
        val den2 = LongArray(16)
        val den4 = LongArray(16)
        val den6 = LongArray(16)
        GF1.copyInto(r[2])
        unpack25519(r[1], p)
        sq(num, r[1])
        mul(den, num, D)
        sub(num, num, r[2])
        add(den, r[2], den)
        sq(den2, den)
        sq(den4, den2)
        mul(den6, den4, den2)
        mul(t, den6, num)
        mul(t, t, den)
        pow2523(t, t)
        mul(t, t, num)
        mul(t, t, den)
        mul(t, t, den)
        mul(r[0], t, den)
        sq(chk, r[0])
        mul(chk, chk, den)
        if (neq(chk, num)) mul(r[0], r[0], I)
        sq(chk, r[0])
        mul(chk, chk, den)
        if (neq(chk, num)) return false
        if (par(r[0]) == ((p[31].toInt() and 0xff) shr 7)) sub(r[0], GF0, r[0])
        mul(r[3], r[0], r[1])
        return true
    }

    /** Whether [signature] is [publicKey]'s signature of [message]. Any malformed input is simply false. */
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != 32 || signature.size != 64) return false
        val q = point()
        if (!unpackNeg(q, publicKey)) return false
        val h = Sha512.digest(signature.copyOfRange(0, 32), publicKey, message)
        reduce(h)
        val p = point()
        scalarmult(p, q, h)
        scalarbase(q, signature.copyOfRange(32, 64))
        addPoints(p, q)
        val t = ByteArray(32)
        pack(t, p)
        var diff = 0
        for (i in 0 until 32) diff = diff or (t[i].toInt() xor signature[i].toInt())
        return diff == 0
    }
}

/** Lower-case hex of [bytes]. */
internal fun hexOf(bytes: ByteArray): String =
    buildString(bytes.size * 2) {
        for (b in bytes) {
            val v = b.toInt() and 0xff
            append(HEX_DIGITS[v shr 4]).append(HEX_DIGITS[v and 0xf])
        }
    }

/** The bytes of a hex string, or null for anything that is not hex. */
internal fun bytesOfHex(hex: String): ByteArray? {
    if (hex.length % 2 != 0) return null
    val out = ByteArray(hex.length / 2)
    for (i in out.indices) {
        val hi = hex[2 * i].digitToIntOrNull(16) ?: return null
        val lo = hex[2 * i + 1].digitToIntOrNull(16) ?: return null
        out[i] = ((hi shl 4) or lo).toByte()
    }
    return out
}

private const val HEX_DIGITS = "0123456789abcdef"
