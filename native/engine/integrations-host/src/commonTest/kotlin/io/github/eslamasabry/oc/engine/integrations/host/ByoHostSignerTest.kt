package io.github.eslamasabry.oc.engine.integrations.host

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Flutter test/byo_host/signer_test.dart (the Dart half of `oc/byo_host_signer`). */
class ByoHostSignerTest {
    private class Call(val method: String, val arguments: Map<String, String>)

    private val calls = mutableListOf<Call>()
    private var handler: (Call) -> Map<String, String>? = { call ->
        if (call.method == "ensureIdentity") {
            mapOf("keyAlias" to "oc.byoHostSsh.phone1", "publicKey" to "ecdsa-sha2-nistp256 AAAA")
        } else {
            null
        }
    }
    private val signer =
        ChannelByoHostSigner { method, arguments ->
            val call = Call(method, arguments)
            calls += call
            handler(call)
        }

    @Test
    fun `B-SEC-029 identity contains only profile alias and public key`() =
        runTest {
            val identity = signer.ensureIdentity("phone1")
            assertEquals("oc.byoHostSsh.phone1", identity.keyAlias)
            assertTrue(identity.publicKey.startsWith("ecdsa-sha2-nistp256 "))
            assertEquals(mapOf("profileId" to "phone1"), calls.single().arguments)
            assertEquals("ByoHostIdentity(<redacted>)", identity.toString())
        }

    @Test
    fun `B-SEC-029 agent lifecycle passes no private key or signing payload`() =
        runTest {
            signer.openAgent("phone1", "/private/files/linux/ubuntu/tmp/.oa-0123456789abcdef/s", "owner")
            signer.closeAgent("phone1")
            signer.deleteIdentity("phone1")
            assertEquals(listOf("openAgent", "closeAgent", "deleteIdentity"), calls.map { it.method })
            assertEquals(setOf("profileId", "socketPath", "user"), calls.first().arguments.keys)
        }

    @Test
    fun `B-SEC-029 reset covers orphan aliases without sending key material`() =
        runTest {
            signer.deleteAllIdentities()
            assertEquals("deleteAllIdentities", calls.single().method)
            assertTrue(calls.single().arguments.isEmpty())
        }

    @Test
    fun `B-SEC-029 unsafe aliases, users and socket paths never reach native`() =
        runTest {
            assertFailsWith<ByoHostSignerException> { signer.ensureIdentity("../phone1") }
            assertFailsWith<ByoHostSignerException> { signer.openAgent("phone1", "/tmp/agent", "root") }
            assertFailsWith<ByoHostSignerException> {
                signer.openAgent("phone1", "/private/files/linux/ubuntu/tmp/.oa-0123456789abcdef/s", "owner; id")
            }
            assertTrue(calls.isEmpty())
        }

    @Test
    fun `B-SEC-029 platform errors never expose native detail`() =
        runTest {
            handler = { throw IllegalStateException("sensitive detail") }
            val error = assertFailsWith<ByoHostSignerException> { signer.ensureIdentity("phone1") }
            assertEquals("ByoHostSignerException(unavailable)", error.toString())
            assertTrue("sensitive" !in error.message.orEmpty())
            assertEquals(null, error.cause)
        }

    @Test
    fun `B-SEC-029 unexpected identity alias is rejected`() =
        runTest {
            handler = { mapOf("keyAlias" to "oc.byoHostSsh.someoneElse", "publicKey" to "ecdsa-sha2-nistp256 AAAA") }
            assertFailsWith<ByoHostSignerException> { signer.ensureIdentity("phone1") }
        }
}
