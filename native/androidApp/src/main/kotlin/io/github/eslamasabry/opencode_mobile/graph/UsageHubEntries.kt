package io.github.eslamasabry.opencode_mobile.graph

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.feature.plans.presentation.*
import io.github.eslamasabry.oc.feature.plans.ui.*
import io.github.eslamasabry.oc.feature.usage.UsageEntries
import io.github.eslamasabry.oc.feature.usage.presentation.UsagePhase
import io.github.eslamasabry.oc.feature.usage.presentation.UsageViewModel
import io.github.eslamasabry.oc.feature.usage.ui.UsageContent
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.nav.KitTab
import io.github.eslamasabry.oc.ui.kit.nav.KitTabSwitcher
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.routes.*
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * App-owned composition keeps the two feature modules independent and gives Usage one bar and one
 * Refresh.
 */
@Inject
@ContributesIntoSet(
    AppScope::class,
    binding = binding<EntryInstaller>(),
    replaces = [UsageEntries::class],
)
class UsageHubEntries(private val source: PlansSource) : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<UsageRoute.Hub> { route ->
            val spending =
                assistedMetroViewModel<UsageViewModel, UsageViewModel.Factory> { create(route) }
            val plans = metroViewModel<PlansViewModel>()
            val manager = metroViewModel<PlansManagementViewModel>()
            val signIn = metroViewModel<PlanSignInViewModel>()
            val used by spending.state.collectAsStateWithLifecycle()
            val plan by plans.state.collectAsStateWithLifecycle()
            val management by manager.state.collectAsStateWithLifecycle()
            val auth by signIn.state.collectAsStateWithLifecycle()
            val facts by source.snapshot.collectAsStateWithLifecycle()
            var chosenPlans by rememberSaveable { mutableStateOf(true) }
            var actionConsumed by rememberSaveable { mutableStateOf(false) }
            var confirming by rememberSaveable { mutableStateOf(false) }
            var invalidTarget by rememberSaveable { mutableStateOf(false) }
            val target = facts.connections.firstOrNull { it.id == route.fixConnection }
            fun openTarget() {
                val provider = route.fixProvider
                if (target == null || provider == null) {
                    invalidTarget = true
                    return
                }
                if (route.readFrom) manager.openReader(target.id, target.name)
                else signIn.open(provider, target.id)
            }
            LaunchedEffect(facts.loaded) {
                if (!facts.loaded || actionConsumed) return@LaunchedEffect
                actionConsumed = true
                when {
                    route.widgetSettings -> manager.openWidget()
                    route.fixProvider != null -> {
                        if (target == null) invalidTarget = true
                        else if (route.confirmFix && !route.readFrom) confirming = true
                        else openTarget()
                    }
                    route.alertToken != null -> {
                        invalidTarget =
                            facts.subscriptions.none {
                                io.github.eslamasabry.oc.engine.integrations.usage
                                    .UsageAlertDelivery
                                    .tokenFor(it.key) == route.alertToken &&
                                    it.readFrom?.id == route.alertProfile
                            }
                    }
                }
            }
            LifecycleResumeEffect(plans) {
                plans.refresh()
                onPauseOrDispose {}
            }
            LaunchedEffect(auth.step) { if (auth.step == PlanSignInStep.Complete) plans.refresh() }
            val hasSpending =
                used.server != null &&
                    used.phase != UsagePhase.Unsupported &&
                    used.phase != UsagePhase.NoServer
            val plansActive = chosenPlans || !hasSpending
            when {
                auth.active -> PlanSignInScreen(auth, signIn)
                management.page != PlanManagePage.None ->
                    PlansManagementScreen(management, plan, manager, signIn::open)
                else ->
                    KitScreen(
                        topBar = {
                            KitTopBar(
                                title = stringResource(Res.string.settingsHubGroupUsage),
                                exit = KitTopBarExit.Back,
                                exitLabel = stringResource(Res.string.kitTopBarBack),
                                onExit = plans::back,
                                actions =
                                    if (facts.connections.isEmpty() && !hasSpending) emptyList()
                                    else
                                        listOf(
                                            KitLineAction(
                                                stringResource(
                                                    if (plansActive) Res.string.usagePlansRefresh
                                                    else Res.string.usageRefreshSpending
                                                ),
                                                if (plansActive) plans::refresh
                                                else spending::refresh,
                                                enabled =
                                                    if (plansActive) !plan.refreshing
                                                    else !used.refreshing,
                                                disabledReason =
                                                    stringResource(Res.string.usageLoading),
                                            )
                                        ),
                            )
                        },
                        width = KitScreenWidth.Reading,
                        loading = if (plansActive) plan.refreshing else used.refreshing,
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            if (invalidTarget) KitNotice(stringResource(Res.string.hub40Changed))
                            Box(Modifier.weight(1f)) {
                                if (facts.loaded && facts.connections.isEmpty() && !hasSpending) {
                                    KitStateView(
                                        KitStateKind.Missing,
                                        stringResource(Res.string.usageHubUnavailableTitle),
                                        body = stringResource(Res.string.usageHubUnavailableBody),
                                    )
                                } else if (!hasSpending) {
                                    PlansContent(plan, plans, management, manager, signIn::open)
                                } else {
                                    KitTabSwitcher(
                                        tabs =
                                            listOf(
                                                KitTab(stringResource(Res.string.usageTabPlans)),
                                                KitTab(stringResource(Res.string.usageTabSpending)),
                                            ),
                                        index = if (plansActive) 0 else 1,
                                        onSelected = { chosenPlans = it == 0 },
                                        semanticsLabel =
                                            stringResource(Res.string.settingsHubGroupUsage),
                                        bodies =
                                            listOf(
                                                {
                                                    PlansContent(
                                                        plan,
                                                        plans,
                                                        management,
                                                        manager,
                                                        signIn::open,
                                                    )
                                                },
                                                { UsageContent(used, spending) },
                                            ),
                                    )
                                }
                            }
                        }
                    }
            }
            KitConfirm(
                spec =
                    if (confirming && target != null)
                        KitConfirmSpec(
                            title = stringResource(Res.string.hub40ContinueOn, target.name),
                            body = stringResource(Res.string.hub40ContinueBody),
                            confirmLabel = stringResource(Res.string.hub40Continue),
                            cancelLabel = stringResource(Res.string.kitTopBarBack),
                        )
                    else null,
                onConfirm = {
                    confirming = false
                    openTarget()
                },
                onCancel = { confirming = false },
            )
        }
    }
}
