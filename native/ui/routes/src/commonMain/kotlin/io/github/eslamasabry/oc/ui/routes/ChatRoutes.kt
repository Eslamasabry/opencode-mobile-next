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
) : AppRoute {
    override val motion: RouteMotion
        get() = RouteMotion.GrowFromRow
}

/** The parent conversation and the subagents it started, reached from that conversation's menu. */
@Serializable public data class SessionRelationsRoute(val ref: ConversationRef) : AppRoute
