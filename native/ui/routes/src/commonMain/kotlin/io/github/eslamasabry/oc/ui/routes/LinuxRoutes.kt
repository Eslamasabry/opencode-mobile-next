package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

/**
 * The Linux manager of the phone's own Ubuntu (plan 07 §2.2.2). Owned by :feature:linux. Shown only
 * with builtinLinux.
 */
@Serializable
public sealed interface LinuxRoute : AppRoute {
    @Serializable public data object Home : LinuxRoute

    @Serializable public data object KeptServices : LinuxRoute

    @Serializable public data object Backups : LinuxRoute

    @Serializable public data object Ports : LinuxRoute

    @Serializable public data object Storage : LinuxRoute

    @Serializable public data object Packages : LinuxRoute

    /**
     * Project services also work on remote servers; capability admission happens in the feature.
     */
    @Serializable
    public data class Services(
        val profile: ProfileId,
        val directory: String,
        val workspace: String? = null,
    ) : LinuxRoute
}
