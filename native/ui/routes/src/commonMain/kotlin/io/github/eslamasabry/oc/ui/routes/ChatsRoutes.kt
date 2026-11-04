package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

/** Owned by the :feature:chats agent. */
@Serializable
public sealed interface ChatsRoute : AppRoute {
    @Serializable
    public data object Home : ChatsRoute {
        override val motion: RouteMotion
            get() = RouteMotion.Root
    }

    @Serializable public data object Attention : ChatsRoute

    @Serializable public data class All(val filter: String? = null) : ChatsRoute

    @Serializable
    public data class New(val profile: ProfileId?, val draftKey: String? = null) : ChatsRoute {
        override val motion: RouteMotion
            get() = RouteMotion.SlideUp
    }
}
