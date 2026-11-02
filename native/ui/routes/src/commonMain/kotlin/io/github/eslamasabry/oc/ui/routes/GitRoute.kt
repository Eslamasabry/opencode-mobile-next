package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import kotlinx.serialization.Serializable

/**
 * The Git page of a conversation's project (plan 07 D09; unit P12-GIT, `:feature:git`). The project folder and the
 * server are read from the conversation itself, so the route carries only its reference.
 */
@Serializable
public data class GitRoute(val ref: ConversationRef) : AppRoute
