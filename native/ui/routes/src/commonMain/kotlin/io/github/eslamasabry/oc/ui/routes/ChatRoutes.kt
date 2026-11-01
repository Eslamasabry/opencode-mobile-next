package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import kotlinx.serialization.Serializable

/** Owned by the chat lead. */
@Serializable
public data class ChatRoute(val ref: ConversationRef, val focusComposer: Boolean = false) : AppRoute {
    override val motion: RouteMotion
        get() = RouteMotion.GrowFromRow
}
