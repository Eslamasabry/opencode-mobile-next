package io.github.eslamasabry.oc.feature.plans.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.integrations.usage.SignInState
import io.github.eslamasabry.oc.engine.integrations.usage.SubscriptionState
import io.github.eslamasabry.oc.engine.integrations.usage.UsageFix
import io.github.eslamasabry.oc.feature.plans.presentation.*
import io.github.eslamasabry.oc.feature.plans.presentation.CollectorFailure
import io.github.eslamasabry.oc.feature.plans.presentation.CollectorUi
import io.github.eslamasabry.oc.feature.plans.presentation.PlanCardUi
import io.github.eslamasabry.oc.feature.plans.presentation.PlansActions
import io.github.eslamasabry.oc.feature.plans.presentation.PlansPhase
import io.github.eslamasabry.oc.feature.plans.presentation.PlansUi
import io.github.eslamasabry.oc.feature.plans.presentation.cardWindows
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.state.KitWindowRow
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Usage › Plans: measured readings first, urgent first, with full facts a visible tap away; every
 * action is a visible row.
 */
@Composable
public fun PlansScreen(
    state: PlansUi,
    actions: PlansActions,
    management: PlansManagementUi? = null,
    manage: PlansManagementActions? = null,
    onAdd: ((String, String?) -> Unit)? = null,
) {
    val words = plansWords()
    KitSheetHost {
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
            PlansContent(state, actions, management, manage, onAdd)
        }
    }
}

/**
 * The body is reusable under the Usage hub's single bar and active-section Refresh. When the
 * management pages are present ([manage]), alerts and widget choices live there; otherwise this
 * page shows them inline.
 */
@Composable
public fun PlansContent(
    state: PlansUi,
    actions: PlansActions,
    management: PlansManagementUi? = null,
    manage: PlansManagementActions? = null,
    onAdd: ((String, String?) -> Unit)? = null,
) {
    val words = plansWords()
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        // LINUX-2: the phone's own usage collector, above the plans.
        if (state.phase == PlansPhase.List && state.collector.available)
            CollectorSetup(state.collector, actions)
        // AGENT-2: usage alerts and the provider-usage collectors.
        if (state.phase == PlansPhase.List) UsageCollectorRows(state, actions, manage == null)
        when (state.phase) {
            PlansPhase.Loading -> KitStateView(KitStateKind.Loading, words.loading)
            PlansPhase.Empty ->
                KitStateView(
                    KitStateKind.Empty,
                    words.emptyTitle,
                    body = words.emptyBody,
                    primary = KitAction(words.refresh, actions::refresh),
                )
            PlansPhase.List -> {
                for (card in
                    state.cards.filter {
                        it.windows.isNotEmpty() || it.state == SubscriptionState.NoLimits
                    }) PlanCard(card, state.nowMs, words, actions) { opened = card.key }
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
        if (manage == null) {
            // STATE: the alert threshold and the reset notice.
            if (state.phase == PlansPhase.List) {
                KitRowGroup(label = stringResource(Res.string.statePlansAlerts)) {
                    KitRow(
                        title =
                            stringResource(
                                Res.string.statePlansThreshold,
                                state.defaultThreshold,
                            ),
                        onClick = { actions.editThreshold(null) },
                    )
                    KitRow(
                        title =
                            stringResource(
                                if (state.resetNotice) Res.string.statePlansResetOn
                                else Res.string.statePlansResetOff
                            ),
                        onClick = actions::toggleResetNotice,
                    )
                }
                if (state.failed && !state.editing)
                    KitNotice(stringResource(Res.string.statePlansSaveFailed))
            }
            if (state.phase != PlansPhase.Loading) {
                KitRowGroup(label = stringResource(Res.string.plansWidgetSettings)) {
                    KitSwitchRow(
                        title = stringResource(Res.string.plansWidgetSetupRows),
                        checked = state.widgetSettings.showSetupRows,
                        onCheckedChange = actions::setWidgetSetupRows,
                    )
                    for (windows in
                        io.github.eslamasabry.oc.engine.integrations.usage.UsageWidgetWindows
                            .entries) {
                        KitRow(
                            title =
                                stringResource(
                                    when (windows) {
                                        io.github.eslamasabry.oc.engine.integrations.usage
                                            .UsageWidgetWindows
                                            .Both -> Res.string.plansWidgetBoth
                                        io.github.eslamasabry.oc.engine.integrations.usage
                                            .UsageWidgetWindows
                                            .FiveHour -> Res.string.plansWidgetFiveHour
                                        io.github.eslamasabry.oc.engine.integrations.usage
                                            .UsageWidgetWindows
                                            .Weekly -> Res.string.plansWidgetWeekly
                                    }
                                ),
                            supporting =
                                if (state.widgetSettings.windows == windows)
                                    stringResource(Res.string.plansWidgetSelected)
                                else null,
                            onClick = { actions.setWidgetWindows(windows) },
                        )
                    }
                }
            }
        }
        if (state.addable.isNotEmpty() && (manage == null || onAdd == null)) {
            KitRowGroup(label = words.addLabel) {
                for (provider in state.addable) KitRow(title = words.providerName(provider))
            }
        }
        if (state.phase != PlansPhase.Loading && manage != null)
            KitRowGroup {
                if (state.addable.isNotEmpty() && onAdd != null)
                    KitRow(
                        title = stringResource(Res.string.m40UsageAdd),
                        trailing = KitRowTrailing.Chevron,
                        onClick = manage::openAdd,
                    )
                KitRow(
                    title = stringResource(Res.string.m40UsageAlerts),
                    trailing = KitRowTrailing.Chevron,
                    onClick = manage::openAlerts,
                )
                KitRow(
                    title = stringResource(Res.string.m40UsageWidget),
                    trailing = KitRowTrailing.Chevron,
                    onClick = manage::openWidget,
                )
            }
        if (state.collectors.isNotEmpty()) {
            KitRowGroup(label = stringResource(Res.string.plansCollectors)) {
                for (target in state.collectors) KitRow(
                    title =
                        stringResource(
                            Res.string.plansCollectorTarget,
                            if (target.provider == "paseo") "Paseo"
                            else if (target.provider == "builtin")
                                stringResource(Res.string.plansBuiltinReader)
                            else words.providerName(target.provider),
                            target.name,
                        ),
                    supporting =
                        stringResource(
                            if (target.enabled) Res.string.plansCollectorEnabled
                            else if (target.provider == "paseo")
                                Res.string.plansPaseoDisabled
                            else if (target.provider == "builtin")
                                Res.string.plansBuiltinDisabled
                            else Res.string.plansCollectorDisabled
                        ),
                    onClick = { actions.chooseCollector(target) },
                )
            }
        }
        if (state.collectorFailed)
            KitNotice(stringResource(Res.string.plansCollectorFailed))
        // AGENT-1: explicit computer usage readers.
        if (state.computerReaders.isNotEmpty()) {
            KitRowGroup(label = words.readers) {
                KitRow(title = words.readerExplanation, supportingMaxLines = 3)
                for (reader in state.computerReaders) {
                    KitRow(
                        title = reader.name,
                        supporting =
                            reader.problem?.let { words.readerProblem(it) }
                                ?: if (reader.enabled) words.readerOn else words.readerOff,
                        supportingMaxLines = 3,
                    )
                    KitRow(
                        title = words.readerAction(reader.name, reader.enabled),
                        onClick =
                            if (state.readerBusy) null
                            else
                                ({
                                    actions.setComputerReaderEnabled(
                                        reader.id,
                                        !reader.enabled,
                                    )
                                }),
                    )
                    if (reader.enabled) {
                        KitRow(
                            title = words.readNow(reader.name),
                            onClick =
                                if (state.readerBusy) null
                                else ({ actions.readComputerNow(reader.id) }),
                        )
                        if (reader.found.isNotEmpty())
                            KitRow(
                                title = words.found(reader.found),
                                supportingMaxLines = 2,
                            )
                    }
                }
                if (state.readerBusy) KitRow(title = words.reading)
                if (state.readerFailed)
                    KitRow(title = words.readerFailed, supportingMaxLines = 2)
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
        if (card != null)
            PlanDetails(card, state.nowMs, words, actions, management, manage, onAdd) {
                opened = null
            }
    }
    val pending = state.confirmCollector
    KitConfirm(
        spec =
            pending?.let {
                KitConfirmSpec(
                    title =
                        if (it.provider == "paseo")
                            stringResource(Res.string.plansPaseoQuestion)
                        else if (it.provider == "builtin")
                            stringResource(Res.string.plansBuiltinQuestion)
                        else
                            stringResource(
                                Res.string.plansCollectorQuestion,
                                words.providerName(it.provider),
                            ),
                    confirmLabel = stringResource(Res.string.plansCollectorAllow),
                    body =
                        stringResource(
                            if (it.provider == "paseo") Res.string.plansPaseoTrust
                            else if (it.provider == "builtin") Res.string.plansBuiltinTrust
                            else Res.string.plansCollectorTrust,
                            it.name,
                        ),
                )
            },
        onConfirm = actions::confirmCollector,
        onCancel = actions::cancelCollector,
    )
    // AGENT-2: the provider-usage collector consent.
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
    // STATE: the threshold editor.
    if (state.editing)
        key(state.editKey) {
            val field = rememberTextFieldState(state.thresholdInput)
            KitSheet(
                visible = true,
                title = stringResource(Res.string.statePlansAlerts),
                closeLabel = words.back,
                onDismissRequest = actions::dismissEditor,
                primary =
                    KitAction(
                        stringResource(Res.string.statePlansSave),
                        actions::saveThreshold,
                    ),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                    KitField(
                        field,
                        stringResource(Res.string.statePlansPercent),
                        kind = KitFieldKind.Number,
                        checking = state.saving,
                        onTextChanged = actions::thresholdChanged,
                    )
                    if (state.failed) KitNotice(stringResource(Res.string.statePlansSaveFailed))
                    if (state.editKey != null)
                        KitButton(
                            stringResource(Res.string.statePlansUseDefault),
                            actions::useDefaultThreshold,
                            role = KitButtonRole.Tertiary,
                            working = state.saving,
                        )
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
private fun UsageCollectorRows(state: PlansUi, actions: PlansActions, showAlerts: Boolean) {
    val disabled =
        if (state.actionBusy || state.refreshing)
            KitEnabled.Disabled(stringResource(Res.string.plansNativeCollectorWorking))
        else KitEnabled.Enabled
    if (state.actionFailed) KitErrorNotice(stringResource(Res.string.plansNativeCollectorFailed))
    // With the management pages present, Usage alerts live there (one switch, not two).
    if (showAlerts && state.alertsAvailable) {
        KitSwitchRow(
            title = stringResource(Res.string.plansNativeAlerts),
            checked = state.alertsOn,
            supporting = stringResource(Res.string.plansNativeAlertsBody),
            enabled = disabled,
            onCheckedChange = actions::setAlertsOn,
        )
    }
    if (state.collectorRows.isNotEmpty())
        KitRowGroup(label = stringResource(Res.string.plansNativeCollectors)) {
            for (option in state.collectorRows) KitRow(
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
        if (card.state == SubscriptionState.SignInExpired) {
            KitErrorNotice(words.state(card.state, card.providerId))
        } else if (
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
private fun PlanDetails(
    card: PlanCardUi,
    nowMs: Long,
    words: PlansWords,
    actions: PlansActions,
    management: PlansManagementUi?,
    manage: PlansManagementActions?,
    onAdd: ((String, String?) -> Unit)?,
    close: () -> Unit,
) {
    Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        KitRowGroup {
            KitRow(
                title = words.state(card.state, card.providerId),
                supporting =
                    listOfNotNull(
                            card.plan?.let { words.plan(it) },
                            words.alertAt(card.alertThreshold).takeIf { card.windows.isNotEmpty() },
                        )
                        .joinToString(" · ")
                        .ifBlank { null },
                supportingMaxLines = 3,
            )
            for (w in card.windows) KitWindowRow(
                title = words.window(w.kind, w.label, w.id),
                left = w.leftPercent / 100f,
                valueLabel = words.left(w.leftPercent),
                valueSemantics = words.left(w.leftPercent),
                note = words.resetDetail(w, nowMs),
            )
            words.fix(card.fix, card.fixConnection)?.let { line ->
                val label =
                    if (card.state == SubscriptionState.SignInExpired && card.fix == UsageFix.SignIn)
                        stringResource(Res.string.plansNavSignInAgain)
                    else line
                val launch: (() -> Unit)? =
                    when (card.fix) {
                        UsageFix.TryAgain -> actions::refresh
                        UsageFix.SignIn,
                        UsageFix.AddKey ->
                            onAdd?.let { add ->
                                {
                                    close()
                                    add(card.providerId, card.fixConnection?.id)
                                }
                            }
                        UsageFix.SetUp ->
                            card.fixConnection?.let { connection ->
                                manage?.let { controller ->
                                    {
                                        close()
                                        controller.openReader(connection.id, connection.name)
                                    }
                                }
                            }
                        UsageFix.None -> null
                    }
                KitRow(
                    title = label,
                    // The plans owner's own fix (sign-in route) when no page launcher is given.
                    onClick =
                        launch
                            ?: if (card.canFix) ({
                                close()
                                actions.fix(card.key)
                            })
                            else null,
                    supportingMaxLines = 3,
                )
            }
            for (on in card.signedInOn) KitRow(
                title = words.signedIn(on.connection, on.state, on.online, on.readsFromHere),
                supportingMaxLines = 3,
                onClick =
                    if (on.state != SignInState.SignedIn && onAdd != null) {
                        {
                            close()
                            onAdd(card.providerId, on.connection.id)
                        }
                    } else null,
            )
        }
        if (manage != null && management != null && card.windows.isNotEmpty())
            KitRowGroup(label = stringResource(Res.string.m40UsageAlerts)) {
                ThresholdChooser(
                    card.alertThreshold,
                    true,
                    management.preferences.threshold,
                    inherited = !card.hasAlertOverride,
                ) {
                    manage.setThreshold(card.key, if (it < 0) null else it)
                }
                KitSwitchRow(
                    title = stringResource(Res.string.m40UsageReset),
                    supporting = stringResource(Res.string.m40UsageResetHelp),
                    checked = card.notifyReset,
                    onCheckedChange = { manage.setReset(card.key, it) },
                )
            }
        if (manage == null) {
            // Without the management pages: this card's alert level and widget row, inline.
            KitRowGroup {
                if (card.windows.isNotEmpty())
                    KitRow(
                        title = stringResource(Res.string.statePlansThreshold, card.alertThreshold),
                        onClick = {
                            close()
                            actions.editThreshold(card.key)
                        },
                    )
                KitSwitchRow(
                    title = stringResource(Res.string.plansWidgetVisible),
                    checked = card.widgetVisible,
                    onCheckedChange = { actions.setWidgetVisible(card.key, it) },
                )
            }
        }
        if (manage != null && management != null) {
            for (connection in
                (card.signedInOn.map { it.connection } + listOfNotNull(card.readFrom)).distinctBy {
                    it.id
                }) {
                val reader = management.readers[connection.id]
                if (reader?.enabled == true)
                    KitRow(
                        title =
                            stringResource(
                                Res.string.m40UsageReadOff,
                                words.connection(connection),
                            ),
                        trailing = KitRowTrailing.Chevron,
                        onClick = {
                            close()
                            manage.openReader(connection.id, connection.name)
                        },
                    )
            }
        }
        if (onAdd != null)
            KitRow(
                title = stringResource(Res.string.m40UsageChooseSignIn),
                trailing = KitRowTrailing.Chevron,
                onClick = {
                    close()
                    onAdd(card.providerId, null)
                },
            )
        KitDetailsFold(
            label = words.details,
            copyLabel = words.copyDetails,
            modifier = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
            notes = words.facts(card, nowMs),
        )
    }
}
