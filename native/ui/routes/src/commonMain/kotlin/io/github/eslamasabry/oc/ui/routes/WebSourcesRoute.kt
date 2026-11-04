package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import kotlinx.serialization.Serializable

/** Sources are reviewed against this conversation and its captured draft revision. */
@Serializable public data class WebSourcesRoute(val ref: ConversationRef) : AppRoute
