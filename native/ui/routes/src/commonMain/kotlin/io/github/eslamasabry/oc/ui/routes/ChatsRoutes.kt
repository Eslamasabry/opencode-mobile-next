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

    @Serializable public data object Attention : ChatsRoute

    @Serializable public data class All(val filter: String? = null) : ChatsRoute

    @OptIn(ExperimentalUuidApi::class)
    @Serializable
    public data class New(
        val profile: ProfileId?,
        val draftKey: String? = null,
        val commandId: String = Uuid.random().toString(),
        val tabId: String = Uuid.random().toString(),
        /** Words the field starts with, for the person to read, change and send (F: `showNewChat(initialText:)`). */
        val initialText: String = "",
        /**
         * Asks for a [NewChatReports] report under this key: the first message was sent, the person left without
         * sending, or the send is not known (F: `showNewChat(onSent:)`).
         */
        val reportKey: String? = null,
        /** A phone agent the form starts with chosen (F: the Agents sheet's "Use {agent}"); ignored when not offered. */
        val phoneAgent: String? = null,
        /**
         * A folder the form starts in, as if the person had picked it (F: "Start a new conversation" from an old
         * agent conversation keeps its folder). Ignored when it is not on the server any more.
         */
        val directory: String? = null,
    ) : ChatsRoute {
        override val motion: RouteMotion
            get() = RouteMotion.SlideUp
    }
}
