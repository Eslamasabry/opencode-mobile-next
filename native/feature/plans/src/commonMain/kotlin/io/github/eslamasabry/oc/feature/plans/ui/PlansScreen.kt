package io.github.eslamasabry.oc.feature.plans.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.integrations.usage.SubscriptionState
import io.github.eslamasabry.oc.engine.integrations.usage.UsageFix
import io.github.eslamasabry.oc.feature.plans.presentation.PlanCardUi
import io.github.eslamasabry.oc.feature.plans.presentation.PlansActions
import io.github.eslamasabry.oc.feature.plans.presentation.PlansPhase
import io.github.eslamasabry.oc.feature.plans.presentation.PlansUi
import io.github.eslamasabry.oc.feature.plans.presentation.cardWindows
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.state.KitWindowRow
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** Plans display measured readings first, urgent first, with full facts a visible tap away. */
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
                actions =
                    listOf(
                        KitLineAction(
                            words.refresh,
                            actions::refresh,
                            enabled = !state.refreshing,
                            disabledReason = words.refreshing,
                        )
                    ),
            )
        },
        loading = state.phase == PlansPhase.Loading || state.refreshing,
        loadingLabel = words.loading,
        width = KitScreenWidth.Reading,
    ) {
        PlansContent(state, actions)
    }
}

/** The body is reusable under the Usage hub's single bar and active-section Refresh. */
@Composable
public fun PlansContent(state: PlansUi, actions: PlansActions) {
    val words = plansWords()
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    when (state.phase) {
        PlansPhase.Loading -> KitStateView(KitStateKind.Loading, words.loading)
        PlansPhase.Empty ->
            KitStateView(KitStateKind.Empty, words.emptyTitle, body = words.emptyBody)
        PlansPhase.List ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
            ) {
                for (card in
                    state.cards.filter {
                        it.windows.isNotEmpty() || it.state == SubscriptionState.NoLimits
                    }) {
                    PlanCard(card, state.nowMs, words, actions) { opened = card.key }
                }
                val setup =
                    state.cards.filter {
                        it.windows.isEmpty() && it.state != SubscriptionState.NoLimits
                    }
                if (setup.isNotEmpty())
                    KitRowGroup(label = words.needsSetup) {
                        for (card in setup) KitRow(
                            title = words.cardTitle(card.name, card.connection),
                            supporting = words.state(card.state, card.providerId),
                            supportingMaxLines = 3,
                            trailing = KitRowTrailing.Chevron,
                            onClick = { opened = card.key },
                        )
                    }
                if (state.keys.isNotEmpty())
                    KitRowGroup(label = words.keysLabel) {
                        for (key in state.keys) KitRow(
                            title = words.cardTitle(key.name, key.connection),
                            supporting =
                                if (key.viaOpenCode) words.viaOpenCode
                                else words.state(SubscriptionState.ApiKeyOnly),
                            supportingMaxLines = 2,
                        )
                    }
            }
    }
    val card = state.cards.firstOrNull { it.key == opened }
    KitSheet(
        visible = card != null,
        onDismissRequest = { opened = null },
        title = card?.let { words.cardTitle(it.name, it.connection) } ?: words.title,
        closeLabel = words.back,
        height = KitSheetHeight.Full,
        primary = KitAction(words.refresh, actions::refresh, working = state.refreshing),
    ) {
        if (card != null) PlanDetails(card, state.nowMs, words, actions)
    }
}

@Composable
private fun PlanCard(
    card: PlanCardUi,
    nowMs: Long,
    words: PlansWords,
    actions: PlansActions,
    open: () -> Unit,
) {
    KitRowGroup {
        KitRow(
            title = words.cardTitle(card.name, card.connection),
            supporting = card.plan?.let { words.plan(it) },
            trailing =
                words.summary(card, nowMs)?.let { KitRowTrailing.Value(it) }
                    ?: KitRowTrailing.Chevron,
            onClick = open,
        )
        if (
            card.state != SubscriptionState.Ok &&
                card.state != SubscriptionState.NearLimit &&
                card.state != SubscriptionState.Exhausted
        ) {
            KitRow(title = words.state(card.state, card.providerId), supportingMaxLines = 3)
        }
        for (w in card.cardWindows()) {
            val at = w.resetsAtMs?.takeIf { it > nowMs }
            KitWindowRow(
                title = words.window(w.kind, w.label, w.id),
                left = w.leftPercent / 100f,
                valueLabel =
                    listOfNotNull("${w.leftPercent}%", at?.let { words.whenAt(it, nowMs) })
                        .joinToString(" · "),
                valueSemantics =
                    listOfNotNull(words.left(w.leftPercent), at?.let { words.whenAt(it, nowMs) })
                        .joinToString(" · "),
            )
        }
        if (card.resetSince) KitRow(title = words.resetSince, supportingMaxLines = 2)
        words.checked(card, nowMs)?.let { KitRow(title = it) }
        if (card.fix == UsageFix.TryAgain)
            KitRow(
                title = words.fix(card.fix, card.fixConnection).orEmpty(),
                onClick = actions::refresh,
            )
    }
}

@Composable
private fun PlanDetails(card: PlanCardUi, nowMs: Long, words: PlansWords, actions: PlansActions) {
    Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        KitRowGroup {
            KitRow(
                title = words.state(card.state, card.providerId),
                supporting = card.plan?.let { words.plan(it) },
                supportingMaxLines = 3,
            )
            for (w in card.windows) KitWindowRow(
                title = words.window(w.kind, w.label, w.id),
                left = w.leftPercent / 100f,
                valueLabel = words.left(w.leftPercent),
                valueSemantics = words.left(w.leftPercent),
                note = words.resetDetail(w, nowMs),
            )
            words.fix(card.fix, card.fixConnection)?.let { label ->
                KitRow(
                    title = label,
                    onClick = if (card.fix == UsageFix.TryAgain) actions::refresh else null,
                    supportingMaxLines = 3,
                )
            }
            for (on in card.signedInOn) KitRow(
                title = words.signedIn(on.connection, on.state, on.online, on.readsFromHere),
                supportingMaxLines = 3,
            )
        }
        KitDetailsFold(
            label = words.details,
            copyLabel = words.copyDetails,
            modifier = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
            notes = words.facts(card, nowMs),
        )
    }
}
