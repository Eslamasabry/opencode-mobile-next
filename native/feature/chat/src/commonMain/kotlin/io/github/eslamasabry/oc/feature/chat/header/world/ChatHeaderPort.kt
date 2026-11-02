package io.github.eslamasabry.oc.feature.chat.header.world

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlinx.coroutines.flow.StateFlow

/**
 * What the conversation header reads and does beyond the shared [io.github.eslamasabry.oc.feature.chat.world.ChatWorld]
 * (P12 CHAT-HEADER): the conversation's server and place, and the conversation-level acts of the ⋯ menu. Every act is
 * one gateway call on the conversation's own connection; a closed or replaced connection answers a Failure, never a
 * retry on another server.
 */
public interface ChatHeaderPort {
    /** The conversation's server and session facts, safe for display; refreshed by [refresh] and after each act. */
    public val facts: StateFlow<HeaderFacts>

    /** Reads the session again (share link, folder, model, context use). */
    public suspend fun refresh(): Outcome<Unit>

    public suspend fun rename(title: String): Outcome<Unit>

    /** The share link, or null when the server made none. */
    public suspend fun share(): Outcome<String?>

    public suspend fun unshare(): Outcome<Unit>

    /** A copy of the whole conversation; its id. */
    public suspend fun fork(): Outcome<SessionId>

    public suspend fun archive(): Outcome<Unit>

    public suspend fun delete(): Outcome<Unit>

    /** Who is signed in and the catalog, for the free-model note (never logged: provider config can carry keys). */
    public suspend fun freeModelFacts(): Outcome<io.github.eslamasabry.oc.feature.chat.header.freemodel.FreeModelFacts> =
        Outcome.Failed(io.github.eslamasabry.oc.core.common.Failure.Unavailable(io.github.eslamasabry.oc.core.common.FeatureId("serverCatalog")))

    /** The connection's permission asks for the approval chip; null where the host has no requests engine. */
    public val approvals: io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalPort?
        get() = null

    /** Stops watching the connection (the header's entry left). */
    public fun close() {}
}

/**
 * The header's facts. [serverName] is the saved server's own name; [directory] is the session's folder (or the
 * connection's location). Server text, shown as is.
 */
public data class HeaderFacts(
    val serverName: String? = null,
    val directory: String? = null,
    val shareUrl: String? = null,
    val model: String? = null,
    val providerId: String? = null,
    val agent: String? = null,
    val contextTokens: Long? = null,
    val contextLimit: Long? = null,
    val totalTokens: Long? = null,
    val connected: Boolean = false,
)

/** Builds one port per open conversation. */
public fun interface ChatHeaderPorts {
    public fun open(ref: io.github.eslamasabry.oc.core.common.ids.ConversationRef): ChatHeaderPort
}
