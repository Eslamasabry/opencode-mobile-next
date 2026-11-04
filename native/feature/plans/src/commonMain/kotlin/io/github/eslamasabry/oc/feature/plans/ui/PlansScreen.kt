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
import io.github.eslamasabry.oc.engine.integrations.usage.UsageFix
import io.github.eslamasabry.oc.feature.plans.presentation.CollectorFailure
import io.github.eslamasabry.oc.feature.plans.presentation.CollectorUi
import io.github.eslamasabry.oc.feature.plans.presentation.PlanCardUi
import io.github.eslamasabry.oc.feature.plans.presentation.PlansActions
import io.github.eslamasabry.oc.feature.plans.presentation.PlansPhase
import io.github.eslamasabry.oc.feature.plans.presentation.PlansUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
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
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            if (state.collector.available) CollectorSetup(state.collector, actions)
            when (state.phase) {
                PlansPhase.Loading -> KitStateView(KitStateKind.Loading, words.loading)
                PlansPhase.Empty ->
                    KitStateView(
                        KitStateKind.Empty,
                        words.emptyTitle,
                        body = words.emptyBody,
                        primary = KitAction(words.refresh, actions::refresh),
                    )
                PlansPhase.List ->
                    Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
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
                                                    io.github.eslamasabry.oc.engine.integrations
                                                        .usage
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
    }
}

@Composable
private fun CollectorSetup(state: CollectorUi, actions: PlansActions) {
    KitRowGroup(label = stringResource(Res.string.linuxQuotaTitle)) {
        KitRow(
            title =
                stringResource(
                    if (state.enabled) Res.string.linuxQuotaEnabled else Res.string.linuxQuotaSetup
                ),
            supporting = stringResource(Res.string.linuxQuotaBody),
            supportingMaxLines = 3,
        )
        state.failure?.let { failure ->
            val message =
                stringResource(
                    when (failure) {
                        CollectorFailure.LinuxNotReady -> Res.string.linuxQuotaLinuxNotReady
                        CollectorFailure.NoNode -> Res.string.linuxQuotaNoNode
                        CollectorFailure.NodeTooOld -> Res.string.linuxQuotaOldNode
                        CollectorFailure.Copy,
                        CollectorFailure.Prepare -> Res.string.linuxQuotaPrepareFailed
                        CollectorFailure.Start -> Res.string.linuxQuotaStartFailed
                        CollectorFailure.Remove -> Res.string.linuxQuotaRemoveFailed
                    }
                )
            val detail = state.details
            if (detail != null)
                KitErrorNotice(
                    message = message,
                    failure = detail,
                    detailsLabel = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                )
            else KitRow(title = message, supportingMaxLines = 3)
        }
        KitButton(
            label =
                stringResource(
                    if (state.enabled) Res.string.linuxQuotaTurnOff else Res.string.linuxQuotaSetup
                ),
            onClick = if (state.enabled) actions::turnOffCollector else actions::setUpCollector,
            working = state.busy,
            role = KitButtonRole.Secondary,
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
            if (card.fix == UsageFix.TryAgain) {
                KitRow(title = fixLine, onClick = actions::refresh)
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
