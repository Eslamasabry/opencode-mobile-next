package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import kotlinx.serialization.Serializable

@Serializable public data object LocalTerminalRoute : AppRoute

/** The server terminal belongs to this conversation's saved server and project. */
@Serializable public data class TerminalRoute(val from: ConversationRef) : AppRoute
