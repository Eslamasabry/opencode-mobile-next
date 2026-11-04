package io.github.eslamasabry.oc.feature.plans.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWidgetWindows
import io.github.eslamasabry.oc.feature.plans.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.choice.*
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Preferences and consent screens over the same native subscriptions engine as the Plans page and
 * home widget.
 */
@Composable
public fun PlansManagementScreen(
    state: PlansManagementUi,
    plans: PlansUi,
    actions: PlansManagementActions,
    onAdd: (String, String?) -> Unit,
) {
    io.github.eslamasabry.oc.ui.kit.modal.KitBackGuard(enabled = true, onBack = actions::dismiss)
    val words = plansWords()
    val title =
        stringResource(
            when (state.page) {
                PlanManagePage.Alerts -> Res.string.m40UsageAlerts
                PlanManagePage.Widget -> Res.string.m40UsageWidget
                PlanManagePage.Reader -> Res.string.m40UsageReadFrom
                PlanManagePage.Add -> Res.string.m40UsageAdd
                else -> Res.string.m40PlansTitle
            }
        )
    KitScreen(
        topBar = {
            KitTopBar(
                title = title,
                subtitle = state.readerName.takeIf { state.page == PlanManagePage.Reader },
                exit = KitTopBarExit.Back,
                exitLabel = words.back,
                onExit = actions::dismiss,
            )
        },
        loading = state.busy,
        loadingLabel = words.loading,
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            if (state.failure != null)
                KitErrorNotice(
                    stringResource(Res.string.m40UsageSaveFailed),
                    failure = state.failure,
                    detailsLabel = words.details,
                    copyLabel = words.copyDetails,
                )
            when (state.page) {
                PlanManagePage.Alerts -> AlertsBody(state, plans, actions)
                PlanManagePage.Widget -> WidgetBody(state, plans, actions)
                PlanManagePage.Reader -> ReaderBody(state, actions)
                PlanManagePage.Add ->
                    KitRowGroup {
                        for (id in plans.addable) KitRow(
                            title = words.providerName(id),
                            trailing = KitRowTrailing.Chevron,
                            onClick = {
                                actions.dismiss()
                                onAdd(id, null)
                            },
                        )
                    }
                PlanManagePage.None -> Unit
            }
        }
    }
}

@Composable
private fun AlertsBody(state: PlansManagementUi, plans: PlansUi, actions: PlansManagementActions) {
    val p = state.preferences
    KitRowGroup {
        KitSwitchRow(
            title = stringResource(Res.string.m40UsageAlertsWatch),
            supporting = stringResource(Res.string.m40UsageAlertsEvery),
            checked = p.enabled,
            onCheckedChange = actions::setAlerts,
        )
    }
    if (!p.enabled) return
    KitRowGroup(label = stringResource(Res.string.m40UsageTellMe)) {
        ThresholdChooser(p.threshold, false, null, onSelect = actions::setDefaultThreshold)
        KitSwitchRow(
            title = stringResource(Res.string.m40UsageReset),
            checked = p.notifyReset,
            onCheckedChange = actions::setDefaultReset,
        )
    }
    val overrides = plans.cards.filter { it.hasAlertOverride || it.notifyReset != p.notifyReset }
    if (overrides.isNotEmpty())
        KitRowGroup(label = stringResource(Res.string.m40UsageDifferent)) {
            for (card in overrides) {
                KitRow(
                    title = plansWords().cardTitle(card.name, card.connection),
                    supporting = plansWords().left(100 - card.alertThreshold),
                )
                ThresholdChooser(
                    card.alertThreshold,
                    true,
                    p.threshold,
                    inherited = !card.hasAlertOverride,
                ) {
                    actions.setThreshold(card.key, if (it < 0) null else it)
                }
                KitSwitchRow(
                    title = stringResource(Res.string.m40UsageReset),
                    checked = card.notifyReset,
                    onCheckedChange = { actions.setReset(card.key, it) },
                )
            }
        }
    KitRowGroup(label = stringResource(Res.string.m40UsageWhen)) {
        KitSwitchRow(
            title = stringResource(Res.string.m40UsageQuiet),
            supporting = stringResource(Res.string.m40UsageQuietShared),
            checked = p.quietStart != null && p.quietEnd != null,
            onCheckedChange = actions::setQuiet,
        )
        if (p.quietStart != null && p.quietEnd != null) {
            MinuteChooser(
                stringResource(Res.string.m40UsageQuietStart),
                p.quietStart,
                actions::setQuietStart,
            )
            MinuteChooser(
                stringResource(Res.string.m40UsageQuietEnd),
                p.quietEnd,
                actions::setQuietEnd,
            )
            if (p.quietStart == p.quietEnd)
                KitNotice(stringResource(Res.string.m40UsageQuietAllDay))
        }
        KitSwitchRow(
            title = stringResource(Res.string.m40UsageWifi),
            checked = p.wifiOnly,
            onCheckedChange = actions::setWifi,
        )
    }
    if (plans.cards.isEmpty()) KitNotice(stringResource(Res.string.m40UsageNothingToWatch))
}

@Composable
internal fun ThresholdChooser(
    level: Int,
    inherit: Boolean,
    default: Int?,
    inherited: Boolean = false,
    onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    KitRow(
        title = stringResource(Res.string.m40UsageDownTo),
        trailing = KitRowTrailing.Value(plansWords().left(100 - level)),
        onClick = { open = !open },
    )
    if (open) {
        if (inherit && default != null)
            KitChoiceRow(
                title = stringResource(Res.string.m40UsageSameAsOthers, 100 - default),
                selected = inherited,
                onClick = {
                    open = false
                    onSelect(-1)
                },
            )
        for (value in (listOf(50, 60, 70, 80, 90, 100) + level).distinct().sorted()) KitChoiceRow(
            title = plansWords().left(100 - value),
            selected = !inherited && value == level,
            onClick = {
                open = false
                onSelect(value)
            },
        )
    }
}

@Composable
private fun MinuteChooser(title: String, minute: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    fun clock(value: Int) =
        "${(value/60).toString().padStart(2,'0')}:${(value%60).toString().padStart(2,'0')}"
    KitRow(
        title = title,
        trailing = KitRowTrailing.Value(clock(minute)),
        onClick = { open = !open },
    )
    if (open)
        for (value in ((0 until 1440 step 30).toList() + minute).distinct().sorted()) KitChoiceRow(
            title = clock(value),
            selected = value == minute,
            onClick = {
                open = false
                onSelect(value)
            },
        )
}

@Composable
private fun WidgetBody(state: PlansManagementUi, plans: PlansUi, actions: PlansManagementActions) {
    val settings = state.preferences.widget
    KitRowGroup(label = stringResource(Res.string.m40UsageWidgetPlans)) {
        for (card in plans.cards) KitSwitchRow(
            title = plansWords().cardTitle(card.name, card.connection),
            checked = card.key !in settings.hiddenKeys,
            onCheckedChange = { actions.setWidgetHidden(card.key, !it) },
        )
    }
    KitRowGroup {
        KitSwitchRow(
            title = stringResource(Res.string.m40UsageWidgetSetup),
            checked = settings.showSetupRows,
            onCheckedChange = actions::setWidgetSetup,
        )
    }
    KitRowGroup(label = stringResource(Res.string.m40UsageWidgetWindows)) {
        for (window in UsageWidgetWindows.entries) KitChoiceRow(
            title =
                stringResource(
                    when (window) {
                        UsageWidgetWindows.Both -> Res.string.m40UsageBoth
                        UsageWidgetWindows.FiveHour -> Res.string.m40PlansFiveHours
                        UsageWidgetWindows.Weekly -> Res.string.m40PlansWeek
                    }
                ),
            selected = window == settings.windows,
            onClick = { actions.setWidgetWindows(window) },
        )
    }
}

@Composable
private fun ReaderBody(state: PlansManagementUi, actions: PlansManagementActions) {
    val words = plansWords()
    val reader = state.readers[state.readerId]
    if (reader == null) {
        KitStateView(KitStateKind.Missing, stringResource(Res.string.m40UsageReaderGone))
        return
    }
    val local = reader.kind == PlanReaderKind.InAppUbuntu
    KitNotice(
        stringResource(
            if (local) Res.string.m40UsageReadPhoneBody else Res.string.m40UsageReadComputerBody
        )
    )
    if (!local)
        KitDetailsFold(
            label = stringResource(Res.string.m40UsageWhatRuns),
            copyLabel = plansWords().copyDetails,
            notes =
                listOf(
                    stringResource(Res.string.m40UsageReadCommand),
                    stringResource(Res.string.m40UsageReadPrivacy),
                ),
        )
    if (reader.busy)
        KitStateView(
            KitStateKind.Working,
            stringResource(
                when (reader.phase) {
                    PlanReaderPhase.Copying -> Res.string.m40UsageCopying
                    PlanReaderPhase.SignIns -> Res.string.m40UsageCheckingSignIns
                    PlanReaderPhase.Starting -> Res.string.m40UsageStarting
                    PlanReaderPhase.Reading -> Res.string.m40UsageReading
                    else -> Res.string.m40UsageChecking
                }
            ),
        )
    reader.problem?.let { problem ->
        KitNotice(
            stringResource(
                when (problem) {
                    PlanReaderProblem.Gone -> Res.string.m40UsageReaderGone
                    PlanReaderProblem.NodeMissing,
                    PlanReaderProblem.NodeTooOld -> Res.string.m40UsageNeedsNode
                    PlanReaderProblem.LinuxNotReady -> Res.string.m40UsageLinuxNotReady
                    PlanReaderProblem.ServerTooOld -> Res.string.m40UsageServerTooOld
                    PlanReaderProblem.Prepare -> Res.string.m40UsagePrepareFailed
                    PlanReaderProblem.Start -> Res.string.m40UsageStartFailed
                    PlanReaderProblem.Remove -> Res.string.m40UsageRemoveFailed
                    PlanReaderProblem.SaveFailed -> Res.string.m40UsageSaveFailed
                    PlanReaderProblem.Unreachable -> Res.string.m40UsageUnreachable
                    else -> Res.string.m40UsageNoAnswer
                }
            )
        )
    }
    if (reader.phase == PlanReaderPhase.Ready)
        KitNotice(
            if (reader.foundProviderIds.isEmpty()) stringResource(Res.string.m40UsageFoundNone)
            else
                stringResource(
                    Res.string.m40UsageFound,
                    reader.foundProviderIds.joinToString(", ") { words.providerName(it) },
                )
        )
    KitButton(
        label =
            stringResource(
                if (reader.enabled) Res.string.m40UsageReadNow else Res.string.m40UsageTurnOn
            ),
        onClick = if (reader.enabled) actions::readerRead else actions::readerStart,
        working = reader.busy || state.busy,
    )
    if (reader.enabled)
        KitButton(
            label = stringResource(Res.string.m40UsageTurnOff),
            onClick = actions::readerOff,
            role = KitButtonRole.Tertiary,
            working = state.busy,
        )
}
