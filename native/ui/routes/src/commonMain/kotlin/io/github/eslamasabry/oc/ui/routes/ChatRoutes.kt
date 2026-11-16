package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import kotlinx.serialization.Serializable

/** Owned by the chat lead. */
@Serializable
public data class ChatRoute(
    val ref: ConversationRef,
    val focusComposer: Boolean = false,
    val requestId: String? = null,
    val landOnFailure: Boolean = false,
    /**
     * A conversation-menu act to run once the page is up (a list row's Share, Details, Copy
     * handoff or Continue here): the name of a chat `HeaderAction`; unknown names do nothing.
     */
    val thenAction: String? = null,
) : AppRoute {
    override val motion: RouteMotion
        get() = openMotion

    public companion object {
        /**
         * How every way into a conversation (Home, All conversations, Search, a notification, the edge light) opens it,
         * and Back closes it. The app's ordinary page transition (fade-forwards, as every other full page): the row →
         * page grow showed two pages blended on the phone (a skeleton over Home, the title drawn twice, Home's words
         * through the conversation; device report, build 10054). The grow stays built (`KitGrowPage`, the shell's
         * `GrowFromRow` handling) and its shell tests set `GrowFromRow` here; it comes back when it opens with no blended
         * frame. Production code never writes this.
         */
        public var openMotion: RouteMotion = RouteMotion.SharedAxisX
    }
}

/** The parent conversation and the subagents it started, reached from that conversation's menu. */
@Serializable public data class SessionRelationsRoute(val ref: ConversationRef) : AppRoute
