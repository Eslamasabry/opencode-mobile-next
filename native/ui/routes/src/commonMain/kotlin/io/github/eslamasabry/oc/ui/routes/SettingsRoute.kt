package io.github.eslamasabry.oc.ui.routes

import kotlinx.serialization.Serializable

/**
 * App preferences (plan 07 §5 I). The hub and its pages share one route: the pages are the settings
 * module's own stack.
 */
@Serializable
public sealed interface SettingsRoute : AppRoute {
    @Serializable public data object Home : SettingsRoute

    @Serializable public data object ReportProblem : SettingsRoute

    /**
     * A stable settings row identifier; the settings feature resolves its page and scroll target.
     */
    @Serializable public data class Target(val rowTag: String) : SettingsRoute

    @Serializable
    public data class ModelDefaults(
        val profile: io.github.eslamasabry.oc.core.common.ids.ProfileId? = null
    ) : SettingsRoute
    @Serializable public data object WhatsNew : SettingsRoute

    @Serializable
    public data class Permissions(val profile: io.github.eslamasabry.oc.core.common.ids.ProfileId) :
        SettingsRoute

    @Serializable public data object Notifications : SettingsRoute
}
