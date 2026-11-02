package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import kotlinx.serialization.Serializable

/**
 * The prompts unit's pages (P12-PROMPTS): saved prompts, the server's slash commands and the agent's task list. Each
 * names the conversation it was opened from, so an inserted prompt or command goes back to that composer only.
 */
@Serializable
public sealed interface PromptsRoute : AppRoute {
    public val ref: ConversationRef

    @Serializable public data class Saved(override val ref: ConversationRef) : PromptsRoute

    @Serializable public data class Commands(override val ref: ConversationRef) : PromptsRoute

    @Serializable public data class Tasks(override val ref: ConversationRef) : PromptsRoute
}
