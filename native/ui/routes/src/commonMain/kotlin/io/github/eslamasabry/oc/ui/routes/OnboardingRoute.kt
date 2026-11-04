package io.github.eslamasabry.oc.ui.routes

import kotlinx.serialization.Serializable

/** First run (plan 07 §5 A03): the welcome a new device starts on. */
@Serializable
public sealed interface OnboardingRoute : AppRoute {
    @Serializable public data object ManagedMove : OnboardingRoute

    @Serializable
    public data object Welcome : OnboardingRoute {
        override val motion: RouteMotion
            get() = RouteMotion.Root
    }
}
