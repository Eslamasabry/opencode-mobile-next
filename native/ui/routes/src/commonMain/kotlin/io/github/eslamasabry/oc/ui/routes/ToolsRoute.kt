package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import kotlinx.serialization.Serializable

/** The server's real tools and provider accounts; a conversation keeps its own profile/location. */
@Serializable
public data class ToolsRoute(val tab: String = "commands", val ref: ConversationRef? = null) :
    AppRoute
