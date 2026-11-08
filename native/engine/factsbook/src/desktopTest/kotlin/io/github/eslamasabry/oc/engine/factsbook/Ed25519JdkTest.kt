package io.github.eslamasabry.oc.engine.factsbook

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.EdECPoint
import java.security.spec.EdECPublicKeySpec
import java.security.spec.NamedParameterSpec
import java.math.BigInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/** The pure-Kotlin Ed25519 and SHA-512 against the JDK's own, over many random seeds and message lengths. */
class Ed25519JdkTest {
    private fun jdkPublicKey(raw: ByteArray): java.security.PublicKey {
        // RFC 8032 encoding: little-endian y with the sign of x in the top bit.
        val bytes = raw.copyOf()
        val xOdd = (bytes[31].toInt() and 0x80) != 0
        bytes[31] = (bytes[31].toInt() and 0x7f).toByte()
        val y = BigInteger(1, bytes.reversedArray())
        return KeyFactory.getInstance("Ed25519").generatePublic(EdECPublicKeySpec(NamedParameterSpec.ED25519, EdECPoint(xOdd, y)))
    }

    @Test
    fun `B-FB-018 every signature the app makes is one the JDK verifies, and SHA-512 agrees with the JDK`() {
        val random = Random(25)
        repeat(40) { round ->
            val seed = random.nextBytes(32)
            val message = random.nextBytes(round * 7)
            assertContentEquals(MessageDigest.getInstance("SHA-512").digest(message), Sha512.digest(message))
            val sig = Ed25519.sign(seed, message)
            val verifier = Signature.getInstance("Ed25519")
            verifier.initVerify(jdkPublicKey(Ed25519.publicKey(seed)))
            verifier.update(message)
            assertTrue(verifier.verify(sig), "round $round")
            assertTrue(Ed25519.verify(Ed25519.publicKey(seed), message, sig))
        }
    }
}
