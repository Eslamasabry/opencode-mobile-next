package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

/** Owned by the :feature:chats agent. */
@Serializable
public sealed interface ChatsRoute : AppRoute {
    @Serializable
    public data object Home : ChatsRoute {
        override val motion: RouteMotion
            get() = RouteMotion.Root
    }

    @Serializable public data class All(val filter: String? = null) : ChatsRoute

    @OptIn(ExperimentalUuidApi::class)
    @Serializable
    public data class New(
        val profile: ProfileId?,
        val draftKey: String? = null,
        val commandId: String = Uuid.random().toString(),
        val tabId: String = Uuid.random().toString(),
    ) : ChatsRoute {
        override val motion: RouteMotion
            get() = RouteMotion.SlideUp
    }
}
