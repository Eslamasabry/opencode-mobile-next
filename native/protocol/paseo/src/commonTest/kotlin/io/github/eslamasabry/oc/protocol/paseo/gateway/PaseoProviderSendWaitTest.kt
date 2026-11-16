package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class PaseoProviderSendWaitTest {
    @Test
    fun `B-AGENT-162 a ready conversation provider does not wait for another loading provider`() = runTest {
        val h = GatewayHarness(this)
        h.daemon.handlers["get_providers_snapshot_request"] = {
            "get_providers_snapshot_response" to jsonOf("entries" to listOf(
                mapOf("provider" to "claude", "status" to "ready"),
                mapOf("provider" to "codex", "status" to "loading"),
            ))
        }
        h.gateway.requireProviderAvailable("claude")
        assertEquals(1, h.daemon.of("get_providers_snapshot_request").size)
        // A partial snapshot must not poison the all-provider catalog cache.
        h.gateway.providerSnapshot()
        assertEquals(9, h.daemon.of("get_providers_snapshot_request").size)
    }

    @Test
    fun `B-AGENT-162 the conversation provider itself still loading is waited for`() = runTest {
        val h = GatewayHarness(this)
        var reads = 0
        h.daemon.handlers["get_providers_snapshot_request"] = {
            reads++
            "get_providers_snapshot_response" to jsonOf("entries" to listOf(
                mapOf("provider" to "claude", "status" to if (reads < 3) "loading" else "ready"),
                mapOf("provider" to "codex", "status" to "loading"),
            ))
        }
        h.gateway.requireProviderAvailable("claude")
        assertEquals(3, reads)
    }
}
