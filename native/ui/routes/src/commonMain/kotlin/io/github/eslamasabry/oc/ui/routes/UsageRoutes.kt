package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

/** Owned by :feature:usage (unit P12-SEARCH-USAGE). Plan 07 row 72 (`UsageRoute.Hub`), Spent section only so far. */
@Serializable
public sealed interface UsageRoute : AppRoute {
    /** Usage and cost one server reports; null = the active server. */
    @Serializable public data class Hub(val profile: ProfileId? = null) : UsageRoute
}
