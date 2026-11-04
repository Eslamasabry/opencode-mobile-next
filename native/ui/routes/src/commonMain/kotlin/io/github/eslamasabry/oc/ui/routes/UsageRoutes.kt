package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

/**
 * Owned by :feature:usage (unit P12-SEARCH-USAGE). Plan 07 row 72 (`UsageRoute.Hub`), Plans and
 * Spending.
 */
@Serializable
public sealed interface UsageRoute : AppRoute {
    /** Usage and cost one server reports; null = the active server. */
    @Serializable
    public data class Hub(
        val profile: ProfileId? = null,
        val widgetSettings: Boolean = false,
        val fixProvider: String? = null,
        val fixConnection: String? = null,
        val readFrom: Boolean = false,
        val confirmFix: Boolean = false,
        val alertToken: String? = null,
        val alertProfile: String? = null,
    ) : UsageRoute
}
