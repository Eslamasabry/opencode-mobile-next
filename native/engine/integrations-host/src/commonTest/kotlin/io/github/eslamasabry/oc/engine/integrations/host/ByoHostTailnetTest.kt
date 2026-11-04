package io.github.eslamasabry.oc.engine.integrations.host

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Flutter test/byo_host/setup_tailnet_test.dart. */
class ByoHostTailnetTest {
    private val denied = HostResult.Failed(ByoHostFailureCode.TailnetRequired)

    private fun target(host: String, port: Int = 22) = ByoHostTarget.of("alice", host, port)!!

    @Test
    fun `B-SEC-027 numeric tailnet ranges preserve user and port and skip DNS`() =
        runTest {
            val resolver = ByoHostTailnetResolver { error("DNS must not run") }
            for (host in listOf(ip4(100, 64, 0, 1), ip4(100, 127, 255, 254), ip6("fd7a", "115c", "a1e0", "", "1234"))) {
                val resolved = resolver.resolve(target(host, 2222)).ok()
                assertEquals("alice", resolved.user)
                assertEquals(2222, resolved.port)
                val address = assertNotNull(HostAddress.parse(resolved.host))
                assertTrue(ByoHostTailnetResolver.isTailnetAddress(address))
            }
        }

    @Test
    fun `B-SEC-027 refuses public, ordinary private, loopback and near-tailnet literals`() =
        runTest {
            val resolver = ByoHostTailnetResolver { error("literal must not reach DNS") }
            for (host in listOf(
                ip4(8, 8, 8, 8), ip4(192, 168, 1, 2), ip4(10, 0, 0, 1), ip4(127, 0, 0, 1), ip4(100, 63, 255, 255), ip4(100, 128, 0, 1), ip6("2001", "db8", "", "1"),
                ip6("fd7a", "115c", "a1e1", "", "1"), ip6("fc00", "", "1"), ip6("0", "0", "0", "0", "0", "0", "0", "1"), ip6("0", "0", "0", "0", "0", "ffff", "6440", "1"),
            )) {
                assertEquals(denied, resolver.resolve(target(host)), host)
            }
        }

    @Test
    fun `B-SEC-027 refuses reserved Tailscale service endpoints`() =
        runTest {
            val resolver = ByoHostTailnetResolver { error("literal must not reach DNS") }
            for (host in listOf(
                ip4(100, 100, 100, 100), ip4(100, 100, 0, 2), ip4(100, 115, 92, 1), ip4(100, 115, 93, 254), ip4(100, 101, 102, 103), ip6("fd7a", "115c", "a1e0", "", "53"),
            )) {
                assertEquals(denied, resolver.resolve(target(host)), host)
            }
        }

    @Test
    fun `B-SEC-027 resolves a hostname once and returns a stable numeric target`() =
        runTest {
            var calls = 0
            val resolver =
                ByoHostTailnetResolver { host ->
                    calls++
                    assertEquals(tsNet("machine.example"), host)
                    listOf(ip6("fd7a", "115c", "a1e0", "", "1234"), ip4(100, 80, 1, 2))
                }
            val resolved = resolver.resolve(target(tsNet("machine.example"))).ok()
            assertEquals(1, calls)
            assertEquals(ip4(100, 80, 1, 2), resolved.host)
        }

    @Test
    fun `B-SEC-027 suffix and mixed DNS do not authorize public SSH`() =
        runTest {
            for (answers in listOf(
                listOf(ip4(203, 0, 113, 1)),
                listOf(ip4(100, 80, 1, 2), ip4(203, 0, 113, 1)),
                listOf(ip4(100, 80, 1, 2), ip6("2001", "db8", "", "1")),
                emptyList(),
            )) {
                val resolver = ByoHostTailnetResolver { answers }
                assertEquals(denied, resolver.resolve(target(tsNet("looks-private"))), answers.toString())
            }
        }

    @Test
    fun `B-SEC-028 DNS exceptions are replaced with safe fixed failures`() =
        runTest {
            val resolver = ByoHostTailnetResolver { throw IllegalStateException("SECRET_LOOKUP_FAILURE") }
            val result = resolver.resolve(target(tsNet("unavailable")))
            assertEquals(denied, result)
            assertFalse("SECRET_LOOKUP_FAILURE" in result.toString())
            assertFalse("SECRET_LOOKUP_FAILURE" in ByoHostFailureCode.TailnetRequired.toFailure().toString())
        }

    @Test
    fun `B-SEC-028 owner steps contain exact manual setup and honest read-only checks`() {
        val steps = byoHostOwnerSetup(ByoHostTarget.of("alice", ip4(100, 80, 1, 2))!!)
        assertEquals(listOf("tailnet", "account", "sshNetwork", "linger", "sshPolicy", "identity"), steps.map { it.id })
        val linger = steps.single { it.id == "linger" }
        assertEquals("sudo loginctl enable-linger 'alice'", linger.commands.first())
        assertTrue("never enables linger" in linger.verification)
        val policy = steps.single { it.id == "sshPolicy" }
        assertTrue("Match User alice" in policy.commands.first())
        assertTrue("AllowTcpForwarding local" in policy.commands.first())
        assertTrue("AllowStreamLocalForwarding no" in policy.commands.first())
        assertTrue("PermitTunnel no" in policy.commands.first())
        assertFalse("PasswordAuthentication" in policy.commands.first())
        assertEquals("sudo systemctl reload ssh", policy.commands.last())
        assertTrue("does not prove the running daemon" in policy.verification)
        assertTrue(steps.all { it.copy.isNotEmpty() && it.verification.isNotEmpty() })
        // Unmodifiable (Dart: steps.clear() throws).
        assertTrue((steps as Any) is ImmutableList<*>)
    }
}
