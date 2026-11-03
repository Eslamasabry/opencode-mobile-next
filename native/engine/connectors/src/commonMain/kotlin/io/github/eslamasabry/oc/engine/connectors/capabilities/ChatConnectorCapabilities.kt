package io.github.eslamasabry.oc.engine.connectors.capabilities

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities

/**
 * What a chat connector card may promise on this gateway (B-AGENT-130). Decided from [ServerCapabilities] only, never
 * from the backend kind: OpenCode 2 adds runtime connectors but cannot finish a sign-in in chat or confirm tool
 * readiness; a qualified backend can do all of it; an unqualified one promises nothing.
 */
public data class ChatConnectorActions(
    /** The server takes location-scoped MCP additions until restart. */
    val runtimeAdds: Boolean,
    /** Connect is offered from a chat card (Dart `mcpChatConnect`). */
    val connect: Boolean,
    /** The chat card may run the OAuth round trip (Dart `mcpChatOAuth`). */
    val signIn: Boolean,
    /** "Tools ready for the next turn" may be claimed after a refresh (Dart `mcpChatToolRefresh`). */
    val toolRefresh: Boolean,
) {
    public companion object {
        public fun of(capabilities: ServerCapabilities): ChatConnectorActions =
            ChatConnectorActions(
                runtimeAdds = capabilities.mcpRuntimeAdds,
                connect = capabilities.mcpChatConnect,
                signIn = capabilities.mcpChatOAuth,
                toolRefresh = capabilities.mcpChatToolRefresh,
            )
    }
}

/**
 * Whether a suggested-connector card shows Connect at all (Dart `ConnectionConnectorCardHost.resolve` and
 * `McpChatController._supported` with cards on): Agent cards, chat connect, the server catalogue and runtime adds.
 */
public fun ServerCapabilities.chatConnectorOffered(): Boolean =
    genUi && mcpChatConnect && serverCatalog && mcpRuntimeAdds

/** The controller's own gate (Dart `McpChatController._supported`), independent of Agent cards. */
public fun ServerCapabilities.chatConnectSupported(): Boolean = mcpChatConnect && serverCatalog && mcpRuntimeAdds

/** The sign-in round trip can finish inside the chat (Dart `canSignIn`). */
public fun ServerCapabilities.chatConnectorSignIn(): Boolean = mcpChatOAuth && mcpOAuth
