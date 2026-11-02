package io.github.eslamasabry.oc.ui.routes

import kotlinx.serialization.Serializable

/** The Linux manager of the phone's own Ubuntu (plan 07 §2.2.2). Owned by :feature:linux. Shown only with builtinLinux. */
@Serializable
public sealed interface LinuxRoute : AppRoute {
    @Serializable public data object Home : LinuxRoute

    @Serializable public data object Ports : LinuxRoute

    @Serializable public data object Storage : LinuxRoute

    @Serializable public data object Packages : LinuxRoute
}
