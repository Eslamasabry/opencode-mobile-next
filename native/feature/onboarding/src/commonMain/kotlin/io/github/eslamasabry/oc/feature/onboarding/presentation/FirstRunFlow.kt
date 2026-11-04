package io.github.eslamasabry.oc.feature.onboarding.presentation

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.OnboardingRoute
import io.github.eslamasabry.oc.ui.routes.PhoneSetupRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute

/**
 * First run end to end (07 §2.6, A03; Flutter `_Root` + `FirstRun`): a device with nothing saved
 * starts on the welcome, the person adds a server, and the first working connection lands them on
 * Conversations. A device that already had servers never sees the welcome (B-NAV-017). The host
 * calls [start] once the saved servers are readable and [onConnected] whenever a saved server
 * becomes connected; navigation is main-thread only, so the host calls both there.
 */
public class FirstRunFlow(private val firstRun: FirstRun, private val nav: AppNavigator) {
    /** The root for this start, from the saved servers ([RootDecision] row 1). */
    public suspend fun start(
        active: ProfileId?,
        saved: Set<ProfileId>,
        needsManagedMove: Boolean = false,
    ): RootChoice {
        val choice =
            RootDecision.decide(
                RootWorld(
                    activeProfileId = active,
                    savedProfileIds = saved,
                    needsManagedMove = needsManagedMove,
                )
            )
        firstRun.observeServers(hasServers = saved.isNotEmpty())
        when (choice) {
            is RootChoice.Servers ->
                if (choice.welcome) nav.replaceRoot(OnboardingRoute.Welcome)
                else firstRun.markReturning()
            RootChoice.ManagedMove -> {
                firstRun.markReturning()
                nav.replaceRoot(OnboardingRoute.ManagedMove)
            }
            else -> firstRun.markReturning()
        }
        return choice
    }

    /**
     * A saved server answered. While first run is pending and the person is still on the welcome or
     * the server setup, this is the finish line: first run ends and they land on Conversations.
     * Anywhere else (a new conversation already opening, Settings) nothing moves.
     */
    public suspend fun onConnected(top: AppRoute?) {
        if (top == OnboardingRoute.ManagedMove) return
        if (!firstRun.landingPending()) return
        if (top !is OnboardingRoute && top !is ServersRoute && top !is PhoneSetupRoute) return
        firstRun.markLanded()
        nav.land()
    }

    /** The welcome's one obvious action. */
    public fun addServer(): Unit = nav.open(ServersRoute.Home)

    public companion object {
        /** Where a finished first run lands. */
        public val Landing: AppRoute = ChatsRoute.Home
    }
}
