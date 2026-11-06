package io.github.eslamasabry.oc.protocol.codex

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * E02-S09 (one Codex client): Codex conversations and the Agents hub share one gateway stack, so a
 * fix to Codex reaches every screen. The app's `server is CodexGateway` checks (handoff review,
 * accounts, plans) only see the gateway the connection registry builds; a second class of the same
 * name silently turned them off.
 */
class OneCodexClientTest {
    private fun exists(name: String): Class<*> = Class.forName("io.github.eslamasabry.oc.protocol.codex.$name")

    @Test
    fun `B-AGENT-067 one Codex gateway, transport, socket and factory exist`() {
        for (name in listOf("gateway.CodexGateway", "gateway.CodexGatewayFactory", "transport.CodexTransport", "transport.CodexSocket", "account.CodexAccountSession")) {
            assertNotNull(exists(name))
        }
    }

    @Test
    fun `B-AGENT-067 the second Codex stack is gone`() {
        for (name in listOf("CodexGateway", "CodexGatewayFactory", "CodexTransport", "CodexSocket", "CodexSocketFactory", "CodexAccountSession", "CodexWireException")) {
            assertFailsWith<ClassNotFoundException>(name) { exists(name) }
        }
    }
}
