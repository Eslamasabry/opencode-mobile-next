package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

/** Owned by :feature:search (unit P12-SEARCH-USAGE). */
@Serializable
public sealed interface SearchRoute : AppRoute {
    /** Search conversation titles (and recent conversations' text) on one server; null = the active server. */
    @Serializable public data class Conversations(val profile: ProfileId? = null) : SearchRoute
}
