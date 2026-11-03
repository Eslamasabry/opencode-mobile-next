package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import kotlinx.serialization.Serializable

/** Chat ⋯ › Export conversation and Notes (owned by :feature:sessiontools, P12-BUILD-PROJECT-ENTRY-CHATS). */
@Serializable
public sealed interface SessionToolsRoute : AppRoute {
    /** Save this conversation as a file: the server's complete copy (JSON) or a readable transcript (Markdown). */
    @Serializable public data class Export(val ref: ConversationRef) : SessionToolsRoute

    /** The conversation's own note, kept on its server under the mobile-owned key. */
    @Serializable public data class Notes(val ref: ConversationRef) : SessionToolsRoute
}
