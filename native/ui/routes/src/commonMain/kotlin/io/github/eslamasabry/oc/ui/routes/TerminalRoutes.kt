package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

/** A server terminal: opened in a project by the person, or from a conversation's saved server and project. */
@Serializable
public sealed interface TerminalRoute : AppRoute {
    /** A server terminal in the project explicitly opened by the person. */
    @Serializable
    public data class Home(val profile: ProfileId, val directory: String) : TerminalRoute

    /** The server terminal belongs to this conversation's saved server and project. */
    @Serializable public data class Conversation(val from: ConversationRef) : TerminalRoute
}
