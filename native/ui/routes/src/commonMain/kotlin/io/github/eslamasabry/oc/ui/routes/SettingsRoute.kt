package io.github.eslamasabry.oc.ui.routes

import kotlinx.serialization.Serializable

/** App preferences (plan 07 §5 I). The hub and its pages share one route: the pages are the settings module's own stack. */
@Serializable
public sealed interface SettingsRoute : AppRoute {
    @Serializable public data object Home : SettingsRoute
}
