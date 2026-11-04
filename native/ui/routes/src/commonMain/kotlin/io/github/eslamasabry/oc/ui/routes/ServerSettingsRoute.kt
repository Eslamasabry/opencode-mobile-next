package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

/** Every command keeps the saved server named when the page opened. */
@Serializable
public data class ServerSettingsRoute(
    val profile: ProfileId,
    val page: ServerSettingsPage = ServerSettingsPage.Home,
) : AppRoute

@Serializable
public enum class ServerSettingsPage { Home, Check, Shell, Capabilities, Reach }
