package io.github.eslamasabry.oc.feature.plans

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.feature.plans.presentation.*
import io.github.eslamasabry.oc.feature.plans.ui.*
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.PlansAlertsRoute
import io.github.eslamasabry.oc.ui.routes.PlansRoute

/** Native route installation; all commands remain on the target-naming presentation ports. */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class PlansEntries(private val nav: AppNavigator) : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<PlansRoute> { PlansJourney(back = nav::back) }
        entry<PlansAlertsRoute> { PlansJourney(alertsOnly = true, back = nav::back) }
    }
}

/** Shared entry content; the app-owned hub composes the same state/action screens. */
@Composable
private fun PlansJourney(alertsOnly: Boolean = false, back: () -> Unit) {
    val plans = metroViewModel<PlansViewModel>()
    val manager = metroViewModel<PlansManagementViewModel>()
    val signIn = metroViewModel<PlanSignInViewModel>()
    val state by plans.state.collectAsStateWithLifecycle()
    val management by manager.state.collectAsStateWithLifecycle()
    val auth by signIn.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(plans) {
        plans.refresh()
        onPauseOrDispose {}
    }
    LaunchedEffect(auth.step) { if (auth.step == PlanSignInStep.Complete) plans.refresh() }
    when {
        auth.active -> PlanSignInScreen(auth, signIn)
        alertsOnly ->
            PlansManagementScreen(
                management.copy(page = PlanManagePage.Alerts),
                state,
                object : PlansManagementActions by manager {
                    override fun dismiss() = back()
                },
                signIn::open,
            )
        management.page != PlanManagePage.None ->
            PlansManagementScreen(management, state, manager, signIn::open)
        else -> PlansScreen(state, plans, management, manager, signIn::open)
    }
}
