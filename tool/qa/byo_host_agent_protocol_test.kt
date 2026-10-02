package io.github.eslamasabry.opencode_mobile

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/** Standalone fake-keystore wire test; run via kotlinc + java, no Android SDK. */
fun main() {
    val key = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()
    val public = key.public as ECPublicKey
    val blob = ByoHostAgentProtocol.publicBlob(public.w.affineX, public.w.affineY)
    var signatures = 0
    fun sign(data: ByteArray): ByteArray {
        signatures++
        return Signature.getInstance("SHA256withECDSA").run {
            initSign(key.private); update(data); sign()
        }
    }
    fun auth(user: String = "owner", service: String = "ssh-connection", method: String = "publickey") =
        ByoHostAgentProtocol.string(ByteArray(32) { 42 }) + byteArrayOf(50) +
            ByoHostAgentProtocol.text(user) + ByoHostAgentProtocol.text(service) +
            ByoHostAgentProtocol.text(method) + byteArrayOf(1) +
            ByoHostAgentProtocol.text(ByoHostAgentProtocol.ALGORITHM) + ByoHostAgentProtocol.string(blob)
    fun request(data: ByteArray, flags: Int = 0): ByteArray = byteArrayOf(13) +
        ByoHostAgentProtocol.string(blob) + ByoHostAgentProtocol.string(data) +
        byteArrayOf(0, 0, 0, flags.toByte())
    fun reply(frame: ByteArray) = ByoHostAgentProtocol.reply(frame, blob, "owner", ::sign)
    fun failure(frame: ByteArray) { check(reply(frame).contentEquals(byteArrayOf(5))) }
    val identities = reply(byteArrayOf(11))
    check(identities[0] == 12.toByte())
    check(signatures == 0)
    val data = auth()
    val signed = reply(request(data))
    check(signed[0] == 14.toByte())
    fun DataInputStream.string(): ByteArray = ByteArray(readInt()).also { readFully(it) }
    val response = DataInputStream(ByteArrayInputStream(signed.copyOfRange(1, signed.size)))
    val encoded = DataInputStream(ByteArrayInputStream(response.string()))
    check(encoded.string().toString(Charsets.UTF_8) == ByoHostAgentProtocol.ALGORITHM)
    val mpints = DataInputStream(ByteArrayInputStream(encoded.string()))
    val r = mpints.string()
    val s = mpints.string()
    val der = byteArrayOf(0x30, (4 + r.size + s.size).toByte(), 2, r.size.toByte()) +
        r + byteArrayOf(2, s.size.toByte()) + s
    check(Signature.getInstance("SHA256withECDSA").run {
        initVerify(key.public); update(data); verify(der)
    })
    check(signatures == 1)
    failure(request(auth(user = "other")))
    failure(request(auth(service = "other")))
    failure(request(auth(method = "other")))
    failure(request(data, flags = 2))
    failure(request(data + byteArrayOf(1)))
    failure(byteArrayOf(13))
    failure(byteArrayOf(17)) // add identity: forbidden
    failure(byteArrayOf(19)) // remove identity: forbidden
    failure(byteArrayOf(11, 0)) // identities trailing data
    failure(ByteArray(ByoHostAgentProtocol.MAX_FRAME + 1))
    check(signatures == 1)
    // Repeated randomized JCA signatures exercise DER sign-bit and mpint padding.
    repeat(100) { check(reply(request(data))[0] == 14.toByte()) }
    println("BYO agent protocol: 112 checks passed (JCA fake signer; no Android hardware claim)")
}
