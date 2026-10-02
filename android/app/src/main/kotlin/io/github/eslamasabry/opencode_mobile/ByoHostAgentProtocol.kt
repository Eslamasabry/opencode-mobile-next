package io.github.eslamasabry.opencode_mobile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger

/** Bounded RFC9987 agent subset. No add/export/general-purpose signing. */
internal object ByoHostAgentProtocol {
    const val MAX_FRAME = 65536
    const val ALGORITHM = "ecdsa-sha2-nistp256"
    private val failure = byteArrayOf(5)

    fun string(value: ByteArray): ByteArray = ByteArrayOutputStream().also {
        DataOutputStream(it).apply { writeInt(value.size); write(value) }
    }.toByteArray()
    fun text(value: String) = string(value.toByteArray(Charsets.UTF_8))
    private fun uint(value: Int) = ByteArrayOutputStream().also {
        DataOutputStream(it).writeInt(value)
    }.toByteArray()

    fun publicBlob(x: BigInteger, y: BigInteger): ByteArray {
        fun coordinate(value: BigInteger): ByteArray {
            require(value.signum() >= 0 && value.bitLength() <= 256)
            val raw = value.toByteArray().let { if (it.size == 33) it.copyOfRange(1, 33) else it }
            return ByteArray(32 - raw.size) + raw
        }
        return text(ALGORITHM) + text("nistp256") + string(byteArrayOf(4) + coordinate(x) + coordinate(y))
    }

    fun reply(frame: ByteArray, publicKey: ByteArray, user: String, sign: (ByteArray) -> ByteArray): ByteArray {
        return try {
            require(frame.size in 1..MAX_FRAME)
            val request = Reader(frame)
            when (request.byte()) {
                11 -> {
                    request.end()
                    byteArrayOf(12) + uint(1) + string(publicKey) + text("OpenCode phone")
                }
                13 -> {
                    require(request.string().contentEquals(publicKey))
                    val data = request.string()
                    require(request.int() == 0)
                    request.end()
                    val auth = Reader(data)
                    require(auth.string().size in listOf(32, 48, 64))
                    require(auth.byte() == 50)
                    require(auth.text() == user)
                    require(auth.text() == "ssh-connection")
                    require(auth.text() == "publickey")
                    require(auth.byte() == 1)
                    require(auth.text() == ALGORITHM)
                    require(auth.string().contentEquals(publicKey))
                    auth.end()
                    val signature = text(ALGORITHM) + string(derSignature(sign(data)))
                    byteArrayOf(14) + string(signature)
                }
                else -> failure
            }
        } catch (_: Exception) { failure }
    }

    /** Android Signature returns DER; SSH needs a string containing two mpints. */
    fun derSignature(der: ByteArray): ByteArray {
        val input = Reader(der)
        require(input.byte() == 0x30)
        // P256 DER sequence always fits a single DER length octet.
        require(input.byte() == der.size - 2)
        fun integer(): ByteArray {
            require(input.byte() == 2)
            val size = input.byte()
            require(size in 1..33)
            val bytes = input.bytes(size)
            require(bytes[0].toInt() and 0x80 == 0)
            require(size == 1 || bytes[0] != 0.toByte() || bytes[1].toInt() and 0x80 != 0)
            val n = BigInteger(bytes)
            require(n.signum() > 0 && n.bitLength() <= 256)
            return string(n.toByteArray())
        }
        val r = integer()
        val s = integer()
        input.end()
        return r + s
    }

    private class Reader(bytes: ByteArray) {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        fun byte() = input.readUnsignedByte()
        fun int() = input.readInt()
        fun bytes(size: Int): ByteArray {
            require(size >= 0 && size <= input.available() && size <= MAX_FRAME)
            return ByteArray(size).also { input.readFully(it) }
        }
        fun string() = bytes(int())
        fun text() = string().toString(Charsets.UTF_8)
        fun end() { require(input.available() == 0) }
    }
}
