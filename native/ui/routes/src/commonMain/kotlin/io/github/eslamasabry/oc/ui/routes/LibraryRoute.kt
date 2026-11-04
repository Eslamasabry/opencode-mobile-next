package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import kotlinx.serialization.Serializable

/**
 * PORT-SCREENS-2: the server's real tools and provider accounts as one tabbed library; a conversation keeps its own
 * profile/location. (Named apart from PROTO's [ToolsRoute], which opens one server's tools page.)
 */
@Serializable
public data class LibraryRoute(val tab: String = "commands", val ref: ConversationRef? = null) :
    AppRoute
