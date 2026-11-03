package io.github.eslamasabry.oc.engine.connectors

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.connectors.capabilities.ChatConnectorActions
import io.github.eslamasabry.oc.engine.connectors.capabilities.chatConnectorOffered
import io.github.eslamasabry.oc.engine.connectors.capabilities.chatConnectorSignIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The MCP flags of Dart `testServerCapabilities` (test/support/fake_server_capabilities.dart). */
internal val qualifiedCapabilities =
    ServerCapabilities(mcpRuntimeAdds = true, mcpChatConnect = true, mcpChatOAuth = true, mcpChatToolRefresh = true)

/** The MCP flags of Dart `api2ServerCapabilities` (lib/api2/gateway_mappers.dart). */
internal val openCode2Capabilities =
    ServerCapabilities(
        mcpOAuth = false,
        mcpChatConnect = true,
        mcpChatOAuth = false,
        mcpChatToolRefresh = false,
        mcpConfigWrites = false,
        mcpRuntimeAdds = true,
        mcpRuntimeRemovals = true,
        setupMcpInventory = true,
    )

class ChatConnectorCapabilitiesTest {
    @Test
    fun `B-AGENT-130 unqualified gateways do not promise chat connector actions`() {
        val actions = ChatConnectorActions.of(ServerCapabilities())
        assertFalse(actions.connect)
        assertFalse(actions.signIn)
        assertFalse(actions.toolRefresh)
        assertFalse(ServerCapabilities(genUi = true).chatConnectorOffered())
        assertFalse(ServerCapabilities().chatConnectorSignIn())
    }

    @Test
    fun `B-AGENT-130 a qualified synthetic backend can connect and refresh chat connectors with OAuth`() {
        val actions = ChatConnectorActions.of(qualifiedCapabilities)
        val all = ChatConnectorActions(runtimeAdds = true, connect = true, signIn = true, toolRefresh = true)
        assertEquals(all, actions)
        assertTrue(qualifiedCapabilities.withGenUi(true).chatConnectorOffered())
        assertTrue(qualifiedCapabilities.chatConnectorSignIn())
    }

    @Test
    fun `B-AGENT-130 OpenCode 2 supports runtime chat connectors without chat OAuth`() {
        val actions = ChatConnectorActions.of(openCode2Capabilities)
        assertTrue(actions.runtimeAdds)
        assertTrue(actions.connect)
        assertFalse(actions.signIn)
        assertFalse(actions.toolRefresh)
        assertTrue(openCode2Capabilities.withGenUi(true).chatConnectorOffered())
        assertFalse(openCode2Capabilities.chatConnectorSignIn())
    }

    @Test
    fun `B-AGENT-130 managed agent cards preserve connector capability decisions`() {
        val capabilities = ServerCapabilities(mcpChatConnect = true, mcpChatOAuth = false, mcpChatToolRefresh = true)
        for (genUi in listOf(true, false)) {
            val changed = capabilities.withGenUi(genUi)
            assertEquals(genUi, changed.genUi)
            val actions = ChatConnectorActions.of(changed)
            assertTrue(actions.connect)
            assertFalse(actions.signIn)
            assertTrue(actions.toolRefresh)
        }
        val withOAuth = ChatConnectorActions.of(ServerCapabilities(mcpChatOAuth = true).withGenUi(true))
        assertFalse(withOAuth.connect)
        assertTrue(withOAuth.signIn)
        assertFalse(withOAuth.toolRefresh)
    }
}
