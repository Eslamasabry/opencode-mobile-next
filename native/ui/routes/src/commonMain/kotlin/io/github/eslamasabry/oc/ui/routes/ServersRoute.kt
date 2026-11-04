package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

/** Server management is separate from app preferences. */
@Serializable
public sealed interface ServersRoute : AppRoute {
    @Serializable public data object Home : ServersRoute

    /** PROTO: the server's setup inspection (configuration sources, MCP inventory, public registry). */
    @Serializable public data class Setup(val profileId: ProfileId) : ServersRoute

    /** SEC-2: the setup assistant (consented config edits with audit) for one saved server. */
    @Serializable public data class SetupAssist(val profileId: ProfileId) : ServersRoute

    @Serializable public data class Remove(val profile: ProfileId) : ServersRoute

    @Serializable public data class MoveQueued(val source: ProfileId) : ServersRoute

    @Serializable public data class Permissions(val profile: ProfileId) : ServersRoute

    @Serializable
    public data class Edit(val profile: ProfileId? = null, val newConversation: Boolean = false) :
        ServersRoute
}
