package io.github.eslamasabry.oc.feature.plans.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.plans.presentation.PlanCardUi
import io.github.eslamasabry.oc.feature.plans.presentation.PlansActions
import io.github.eslamasabry.oc.feature.plans.presentation.PlansPhase
import io.github.eslamasabry.oc.feature.plans.presentation.PlansUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Usage › Plans: one group per plan, what is left in each window and when it resets; every action
 * is a visible row.
 */
@Composable
public fun PlansScreen(state: PlansUi, actions: PlansActions) {
    val words = plansWords()
    KitScreen(
        topBar = {
            KitTopBar(
                title = words.title,
                exit = KitTopBarExit.Back,
                exitLabel = words.back,
                onExit = actions::back,
            )
        },
        loading = state.phase == PlansPhase.Loading,
        loadingLabel = words.loading,
        bottom =
            if (state.phase == PlansPhase.Loading) null
            else
                ({
                    KitButton(
                        if (state.refreshing) words.refreshing else words.refresh,
                        actions::refresh,
                        Modifier.fillMaxWidth()
                            .padding(
                                horizontal = LocalKitTokens.current.gutter.dp,
                                vertical = Space.S2.dp,
                            ),
                        role = KitButtonRole.Secondary,
                        working = state.refreshing,
                    )
                }),
        width = KitScreenWidth.Reading,
    ) {
        when (state.phase) {
            PlansPhase.Loading -> KitStateView(KitStateKind.Loading, words.loading)
            PlansPhase.Empty ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
                ) {
                    CollectorControls(state, actions)
                    KitStateView(
                        KitStateKind.Empty,
                        words.emptyTitle,
                        body = words.emptyBody,
                        primary = KitAction(words.refresh, actions::refresh),
                    )
                }
            PlansPhase.List ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
                ) {
                    CollectorControls(state, actions)
                    for (card in state.cards) PlanCard(card, state.nowMs, words, actions)
                    if (state.keys.isNotEmpty()) {
                        KitRowGroup(label = words.keysLabel) {
                            for (key in state.keys) {
                                KitRow(
                                    title = words.cardTitle(key.name, key.connection),
                                    supporting =
                                        if (key.viaOpenCode) words.viaOpenCode
                                        else
                                            words.state(
                                                io.github.eslamasabry.oc.engine.integrations.usage
                                                    .SubscriptionState
                                                    .ApiKeyOnly
                                            ),
                                    supportingMaxLines = 2,
                                )
                            }
                        }
                    }
                    if (state.addable.isNotEmpty()) {
                        KitRowGroup(label = words.addLabel) {
                            for (provider in state.addable) KitRow(
                                title = words.providerName(provider)
                            )
                        }
                    }
                }
        }
    }
    val ask = state.ask
    val place = ask?.collector?.let { if (it.inApp) words.thisPhone else it.name }.orEmpty()
    KitConfirm(
        spec =
            ask?.let {
                KitConfirmSpec(
                    title =
                        stringResource(
                            when {
                                !it.enable -> Res.string.plansNativeOffQuestion
                                it.collector.inApp -> Res.string.plansNativeSetupQuestion
                                else -> Res.string.plansNativeRemoteQuestion
                            },
                            place,
                        ),
                    confirmLabel =
                        stringResource(
                            when {
                                !it.enable -> Res.string.plansNativeTurnOff
                                it.collector.inApp -> Res.string.plansNativeSetUp
                                else -> Res.string.plansNativeAllow
                            }
                        ),
                    body =
                        stringResource(
                            when {
                                !it.enable -> Res.string.plansNativeOffBody
                                it.collector.inApp -> Res.string.plansNativeSetupBody
                                else -> Res.string.plansNativeRemoteBody
                            }
                        ),
                    cancelLabel = stringResource(Res.string.plansNativeCancel),
                )
            },
        onConfirm = actions::confirmCollector,
        onCancel = actions::cancelCollector,
    )
}

@Composable
private fun CollectorControls(state: PlansUi, actions: PlansActions) {
    val disabled =
        if (state.actionBusy || state.refreshing)
            KitEnabled.Disabled(stringResource(Res.string.plansNativeCollectorWorking))
        else KitEnabled.Enabled
    if (state.actionFailed) KitErrorNotice(stringResource(Res.string.plansNativeCollectorFailed))
    if (state.alertsAvailable) {
        KitSwitchRow(
            title = stringResource(Res.string.plansNativeAlerts),
            checked = state.alertsOn,
            supporting = stringResource(Res.string.plansNativeAlertsBody),
            enabled = disabled,
            onCheckedChange = actions::setAlertsOn,
        )
    }
    if (state.collectors.isNotEmpty())
        KitRowGroup(label = stringResource(Res.string.plansNativeCollectors)) {
            for (option in state.collectors) KitRow(
                title = if (option.inApp) plansWords().thisPhone else option.name,
                supporting =
                    stringResource(
                        if (option.enabled) Res.string.plansNativeCollectorOn
                        else if (option.inApp) Res.string.plansNativeCollectorSetup
                        else Res.string.plansNativeCollectorRemote
                    ),
                supportingMaxLines = 3,
                enabled = disabled.isEnabled,
                disabledReason = disabled.reason,
                onClick = { actions.askCollector(option.profile) },
            )
        }
}

@Composable
private fun PlanCard(card: PlanCardUi, nowMs: Long, words: PlansWords, actions: PlansActions) {
    KitRowGroup(label = words.cardTitle(card.name, card.connection)) {
        KitRow(
            title = words.state(card.state),
            supporting =
                listOfNotNull(
                        card.plan?.let { words.plan(it) },
                        words.alertAt(card.alertThreshold).takeIf { card.windows.isNotEmpty() },
                    )
                    .joinToString(" · ")
                    .ifBlank { null },
            supportingMaxLines = 2,
        )
        for (w in card.windows) {
            KitRow(
                title = words.window(w.kind, w.label),
                supporting =
                    listOfNotNull(
                            words.left(w.leftPercent),
                            w.resetsAtMs?.let { words.resetsIn((it - nowMs) / 60_000) },
                        )
                        .joinToString(" · "),
            )
        }
        if (card.resetSince) KitRow(title = words.resetSince, supportingMaxLines = 2)
        words.fix(card.fix, card.fixConnection)?.let { fixLine ->
            if (card.canFix) {
                KitRow(title = fixLine, onClick = { actions.fix(card.key) })
            } else {
                KitRow(title = fixLine, supportingMaxLines = 2)
            }
        }
        for (on in card.signedInOn) {
            KitRow(
                title = words.signedIn(on.connection, on.state, on.online, on.readsFromHere),
                supportingMaxLines = 2,
            )
        }
    }
}
