package io.github.eslamasabry.oc.ui.routes

import kotlinx.serialization.Serializable

/** This phone and its pages (plan 07 §2.2.2 F01, F08, F15). Owned by :feature:phone. Shown only with builtinLinux. */
@Serializable
public sealed interface PhoneRoute : AppRoute {
    @Serializable public data object ThisPhone : PhoneRoute

    @Serializable public data object Running : PhoneRoute

    @Serializable public data object KeepRunning : PhoneRoute
}
