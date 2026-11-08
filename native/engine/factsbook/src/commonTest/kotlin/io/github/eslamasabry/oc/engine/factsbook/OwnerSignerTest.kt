package io.github.eslamasabry.oc.engine.factsbook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Flutter test/factsbook/ed25519_test.dart: RFC 8032 §7.1, RFC 8785, and the owner proof the node verifies. */
class OwnerSignerTest {
    private fun hex(s: String) = bytesOfHex(s)!!

    @Test
    fun `B-FB-014 Ed25519 matches the RFC 8032 vectors and verifies only its own signatures`() {
        val vectors =
            listOf(
                Triple(
                    "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60",
                    "",
                    "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a" to
                        "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b",
                ),
                Triple(
                    "4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb",
                    "72",
                    "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c" to
                        "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00",
                ),
            )
        for ((seed, message, expected) in vectors) {
            val (pk, sig) = expected
            assertEquals(pk, hexOf(Ed25519.publicKey(hex(seed))))
            val signed = Ed25519.sign(hex(seed), hex(message))
            assertEquals(sig, hexOf(signed))
            assertTrue(Ed25519.verify(hex(pk), hex(message), signed))
            assertFalse(Ed25519.verify(hex(pk), hex(message) + byteArrayOf(1), signed))
            val tampered = signed.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() }
            assertFalse(Ed25519.verify(hex(pk), hex(message), tampered))
        }
        assertFailsWith<IllegalArgumentException> { Ed25519.publicKey(ByteArray(31)) }
        assertEquals(
            "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
            hexOf(Sha512.digest("abc".encodeToByteArray())),
        )
    }

    @Test
    fun `B-FB-015 canonical JSON sorts keys by code unit, escapes only what JSON needs and keeps integers`() {
        val value = Json.parseToJsonElement("""{"b":2,"a":[1,true,null,"x"],"é":{"z":1,"A":0}}""")
        assertEquals("""{"a":[1,true,null,"x"],"b":2,"é":{"A":0,"z":1}}""", Jcs.string(value))
        assertEquals("\"a\\\"b\\\\c\\n\\u0001é\"", Jcs.string(JsonPrimitive("a\"b\\c\n\u0001é")))
        assertEquals("3", Jcs.string(JsonPrimitive(3.0)))
        assertFailsWith<IllegalArgumentException> { Jcs.string(JsonPrimitive(1.5)) }
    }

    @Test
    fun `B-FB-016 the owner proof signs vault client method path body nonce and time, never the proof itself`() =
        runTest {
            val seed = hex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")
            val link = FactsBookLink("http://127.0.0.1:8787", "cli_01", "v_main", "fp", "n", listOf("read"))
            val signer =
                OwnerSigner(
                    seed,
                    deviceId = "dev_1",
                    node = link,
                    signing = FactsBookSigning({ ByteArray(it) { i -> i.toByte() } }) { FbResult.Ok(Instant.parse("2026-11-08T10:00:05.900Z")) },
                    now = { error("the device check gives the time") },
                )
            val body = Json.parseToJsonElement("""{"decision":"approve","digest":"d","proof":{"old":1}}""").jsonObject
            val proof = assertIs<FbResult.Ok<JsonObject>>(signer.proof("post", "/v1/review/rvw_1/decision", body)).value
            assertEquals("dev_1", proof["signer"]!!.jsonPrimitive.content)
            assertEquals("000102030405060708090a0b0c0d0e0f", proof["nonce"]!!.jsonPrimitive.content)
            assertEquals("2026-11-08T10:00:05Z", proof["authenticated_at"]!!.jsonPrimitive.content)
            val envelope =
                """{"authenticated_at":"2026-11-08T10:00:05Z","body":{"decision":"approve","digest":"d"},"client":"cli_01",""" +
                    """"method":"POST","nonce":"000102030405060708090a0b0c0d0e0f","path":"/v1/review/rvw_1/decision","vault":"v_main"}"""
            val sig = hex(proof["sig"]!!.jsonPrimitive.content)
            assertEquals(64, sig.size)
            assertTrue(Ed25519.verify(Ed25519.publicKey(seed), envelope.encodeToByteArray(), sig))
            assertEquals("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a", signer.publicKeyHex)

            // A screen lock that says no stops the signature.
            val refused = OwnerSigner(seed, "dev_1", link, FactsBookSigning({ ByteArray(it) }) { FbResult.Err(FactsBookError("device_auth_cancelled")) }) { Instant.DISTANT_PAST }
            assertEquals("device_auth_cancelled", assertIs<FbResult.Err>(refused.proof("POST", "/v1/x", JsonObject(emptyMap()))).error.code)
        }

    @Test
    fun `B-FB-017 the control history is trusted only when signed by the pinned root and linked in order`() {
        val seed = hex("4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb")
        val pk = hexOf(Ed25519.publicKey(seed))
        val fingerprint = io.github.eslamasabry.oc.core.common.dart.DartJson.sha256Hex(Ed25519.publicKey(seed))
        fun signed(fields: Map<String, kotlinx.serialization.json.JsonElement>): JsonObject {
            val unsigned = JsonObject(fields)
            return JsonObject(fields + ("sig" to JsonPrimitive(hexOf(Ed25519.sign(seed, Jcs.bytes(unsigned))))))
        }
        val first = signed(mapOf("cseq" to JsonPrimitive(1), "kind" to JsonPrimitive("genesis"), "signer" to JsonPrimitive("root")))
        val prev = io.github.eslamasabry.oc.core.common.dart.DartJson.sha256Hex(Jcs.bytes(first))
        val second = signed(mapOf("cseq" to JsonPrimitive(2), "kind" to JsonPrimitive("enrol"), "signer" to JsonPrimitive("root"), "prev" to JsonPrimitive(prev)))
        val good = ControlChain(fingerprint, pk, listOf(first, second), forked = false)
        assertTrue(ControlVerifier.verify(good, fingerprint).trusted)
        // A fingerprint the node offers in place of the pinned one is never taken.
        assertFalse(ControlVerifier.verify(good, "other").trusted)
        // Out of order: the links break.
        assertFalse(ControlVerifier.verify(good.copy(records = listOf(second, first)), fingerprint).chainLinked)
        // A record changed after signing.
        val changed = JsonObject(second + ("kind" to JsonPrimitive("revoke")))
        assertFalse(ControlVerifier.verify(good.copy(records = listOf(first, changed)), fingerprint).signaturesValid)
        assertFalse(ControlVerifier.verify(good.copy(forked = true), fingerprint).trusted)
    }
}
