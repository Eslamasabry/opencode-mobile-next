package io.github.eslamasabry.oc.feature.usage.ui

import io.github.eslamasabry.oc.ui.kit.frame.kitPageListState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.integrations.usage.UsageBudgetUnit
import io.github.eslamasabry.oc.engine.integrations.usage.UsageBudgets
import io.github.eslamasabry.oc.engine.usage.UsageCalendar
import io.github.eslamasabry.oc.feature.usage.presentation.UsageActions
import io.github.eslamasabry.oc.feature.usage.presentation.UsageFigures
import io.github.eslamasabry.oc.feature.usage.presentation.UsagePhase
import io.github.eslamasabry.oc.feature.usage.presentation.UsageRange
import io.github.eslamasabry.oc.feature.usage.presentation.UsageReport
import io.github.eslamasabry.oc.feature.usage.presentation.UsageScope
import io.github.eslamasabry.oc.feature.usage.presentation.UsageUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.content.usage.KitTrendLine
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePanel
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitInputFormDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.input.KitSearchNoMatch
import io.github.eslamasabry.oc.ui.kit.row.KitMetricGrid
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateSize
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.p12SearchNoServerTitle
import io.github.eslamasabry.oc.ui.words.res.p12SearchOpenServers
import io.github.eslamasabry.oc.ui.words.res.p12UsageCost
import io.github.eslamasabry.oc.ui.words.res.p12UsageFailed
import io.github.eslamasabry.oc.ui.words.res.p12UsageNoServerBody
import io.github.eslamasabry.oc.ui.words.res.p12UsageProjectsFailed
import io.github.eslamasabry.oc.ui.words.res.p12UsageTryAgain
import io.github.eslamasabry.oc.ui.words.res.quotaBudgetSaveFailed
import io.github.eslamasabry.oc.ui.words.res.stateUsageBudgets
import io.github.eslamasabry.oc.ui.words.res.stateUsageUsd
import io.github.eslamasabry.oc.ui.words.res.usageActiveDays
import io.github.eslamasabry.oc.ui.words.res.usageAllProjects
import io.github.eslamasabry.oc.ui.words.res.usageAllTime
import io.github.eslamasabry.oc.ui.words.res.usageAuthorization
import io.github.eslamasabry.oc.ui.words.res.usageBudgetAmount
import io.github.eslamasabry.oc.ui.words.res.usageBudgetClearAll
import io.github.eslamasabry.oc.ui.words.res.usageBudgetDescription
import io.github.eslamasabry.oc.ui.words.res.usageBudgetInvalidTokens
import io.github.eslamasabry.oc.ui.words.res.usageBudgetInvalidUsd
import io.github.eslamasabry.oc.ui.words.res.usageBudgetNotSet
import io.github.eslamasabry.oc.ui.words.res.usageBudgetPrevious
import io.github.eslamasabry.oc.ui.words.res.usageBudgetProgress
import io.github.eslamasabry.oc.ui.words.res.usageBudgetReached
import io.github.eslamasabry.oc.ui.words.res.usageBudgetRemove
import io.github.eslamasabry.oc.ui.words.res.usageBudgetSaveTokens
import io.github.eslamasabry.oc.ui.words.res.usageBudgetSaveUsd
import io.github.eslamasabry.oc.ui.words.res.usageBudgetTokenUnit
import io.github.eslamasabry.oc.ui.words.res.usageBudgetTokensTitle
import io.github.eslamasabry.oc.ui.words.res.usageBudgetUsdTitle
import io.github.eslamasabry.oc.ui.words.res.usageCacheRead
import io.github.eslamasabry.oc.ui.words.res.usageCacheWrite
import io.github.eslamasabry.oc.ui.words.res.usageCostDisclosure
import io.github.eslamasabry.oc.ui.words.res.usageDescription
import io.github.eslamasabry.oc.ui.words.res.usageEmpty
import io.github.eslamasabry.oc.ui.words.res.usageFailed
import io.github.eslamasabry.oc.ui.words.res.usageInput
import io.github.eslamasabry.oc.ui.words.res.usageInvalidResponse
import io.github.eslamasabry.oc.ui.words.res.usageLoading
import io.github.eslamasabry.oc.ui.words.res.usageModelSteps
import io.github.eslamasabry.oc.ui.words.res.usageModelTokens
import io.github.eslamasabry.oc.ui.words.res.usageModels
import io.github.eslamasabry.oc.ui.words.res.usageNoFinishedTools
import io.github.eslamasabry.oc.ui.words.res.usageNoModels
import io.github.eslamasabry.oc.ui.words.res.usageNoTools
import io.github.eslamasabry.oc.ui.words.res.usageOutput
import io.github.eslamasabry.oc.ui.words.res.usagePeriod
import io.github.eslamasabry.oc.ui.words.res.usagePreviousResult
import io.github.eslamasabry.oc.ui.words.res.usagePrompts
import io.github.eslamasabry.oc.ui.words.res.usageProviderCostUnavailable
import io.github.eslamasabry.oc.ui.words.res.usageProviderModelCount
import io.github.eslamasabry.oc.ui.words.res.usageProviders
import io.github.eslamasabry.oc.ui.words.res.usageReasoning
import io.github.eslamasabry.oc.ui.words.res.usageRefresh
import io.github.eslamasabry.oc.ui.words.res.usageScope
import io.github.eslamasabry.oc.ui.words.res.usageSessions
import io.github.eslamasabry.oc.ui.words.res.usageSpentAllTime
import io.github.eslamasabry.oc.ui.words.res.usageSpentPeriod
import io.github.eslamasabry.oc.ui.words.res.usageSpentThirtyDays
import io.github.eslamasabry.oc.ui.words.res.usageSpentToday
import io.github.eslamasabry.oc.ui.words.res.usageSpentYear
import io.github.eslamasabry.oc.ui.words.res.usageSteps
import io.github.eslamasabry.oc.ui.words.res.usageStreak
import io.github.eslamasabry.oc.ui.words.res.usageSubagents
import io.github.eslamasabry.oc.ui.words.res.usageSucceeded
import io.github.eslamasabry.oc.ui.words.res.usageSuccessRate
import io.github.eslamasabry.oc.ui.words.res.usageThirtyDays
import io.github.eslamasabry.oc.ui.words.res.usageTimezone
import io.github.eslamasabry.oc.ui.words.res.usageTimezoneUnavailable
import io.github.eslamasabry.oc.ui.words.res.usageTinyCost
import io.github.eslamasabry.oc.ui.words.res.usageTitle
import io.github.eslamasabry.oc.ui.words.res.usageToday
import io.github.eslamasabry.oc.ui.words.res.usageTokens
import io.github.eslamasabry.oc.ui.words.res.usageToolCalls
import io.github.eslamasabry.oc.ui.words.res.usageToolReliability
import io.github.eslamasabry.oc.ui.words.res.usageToolsUnavailable
import io.github.eslamasabry.oc.ui.words.res.usageTotalTokens
import io.github.eslamasabry.oc.ui.words.res.usageUnfinished
import io.github.eslamasabry.oc.ui.words.res.usageUnsupported
import io.github.eslamasabry.oc.ui.kit.capability.KitCapabilityExplainer
import io.github.eslamasabry.oc.ui.kit.capability.KitCapabilityVariant
import io.github.eslamasabry.oc.ui.words.res.usageYear
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Usage and cost (Flutter `UsageScreen`, owner order): the total first, then the range and scope
 * that shape it, then the breakdowns (activity, tokens, providers, models, tools, days). The one
 * sentence that this is an estimate, not a provider bill, stays visible at the end. Kit parts only.
 */
@Composable
public fun UsageScreen(state: UsageUi, actions: UsageActions) {
    KitSheetHost {
        KitScreen(
            topBar = {
                KitTopBar(
                    title = stringResource(Res.string.usageTitle),
                    subtitle = state.serverName,
                    exit = KitTopBarExit.Back,
                    exitLabel = stringResource(Res.string.kitTopBarBack),
                    onExit = actions::back,
                    actions =
                        // F: lib/ui/screens/usage_screen.dart:253: no Refresh while the server cannot report usage.
                        if (state.server == null || state.phase == UsagePhase.Unsupported) emptyList()
                        else
                            listOf(
                                KitLineAction(
                                    stringResource(Res.string.usageRefresh),
                                    actions::refresh,
                                    enabled = !state.refreshing,
                                    disabledReason = stringResource(Res.string.usageLoading),
                                    icon = KitIcons.retry,
                                )
                            ),
                )
            },
            loading = state.refreshing,
            loadingLabel = stringResource(Res.string.usageLoading),
            width = KitScreenWidth.Reading,
            edges = KitScreenEdges.Bleed,
        ) {
            // F: lib/ui/screens/usage_screen.dart:266-275: the whole page is the missing feature, said by the
            // capability explainer's page state (no description, filters or budgets on a server without usage).
            if (state.phase == UsagePhase.Unsupported)
                KitCapabilityExplainer(
                    "server.oc2",
                    Modifier.testTag("usage-unsupported"),
                    variant = KitCapabilityVariant.State,
                    serverName = state.serverName,
                    source = "usage",
                )
            else UsageContent(state, actions)
        }
        BudgetEditor(state, actions)
    }
}

/** The Spending body; also shown under the app-owned Usage hub. */
@Composable
public fun UsageContent(state: UsageUi, actions: UsageActions) {
    val gutter = LocalKitTokens.current.gutter
    val recordsField = rememberTextFieldState(state.modelSearch)
    // Clearing from the state (no-match Clear, another server) empties the field too.
    LaunchedEffect(state.modelSearch) {
        if (state.modelSearch.isEmpty() && recordsField.text.isNotEmpty()) recordsField.clearText()
    }
    LazyColumn(contentPadding = PaddingValues(top = Space.S3.dp, bottom = Space.S6.dp), state = kitPageListState()) {
        if (state.phase == UsagePhase.NoServer) {
            item(key = "no-server") {
                KitStateView(
                    kind = KitStateKind.Missing,
                    modifier = Modifier.padding(horizontal = gutter.dp),
                    title = stringResource(Res.string.p12SearchNoServerTitle),
                    body = stringResource(Res.string.p12UsageNoServerBody),
                    primary =
                        KitAction(
                            stringResource(Res.string.p12SearchOpenServers),
                            actions::openServers,
                        ),
                )
            }
            return@LazyColumn
        }
        item(key = "description") {
            KitText(
                stringResource(Res.string.usageDescription),
                Modifier.padding(horizontal = gutter.dp, vertical = Space.S3.dp),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
        }
        item(key = "status") { Status(state, actions) }
        // The Cost segment (B-160): 24h · 7d · 30d · 90d. Flutter's Today, Year and All time stay in the Period
        // picker below; while one of them is chosen no segment is selected, so the segment steps aside.
        if (state.phase != UsagePhase.Unsupported && state.range in UsageRange.segment)
            item(key = "segment") { RangeSegment(state, actions) }
        state.report?.let { report ->
            item(key = "total") { Total(report, state.range, state.timezone, state) }
        }
        if (state.phase != UsagePhase.Unsupported) item(key = "filters") { Filters(state, actions) }
        if (state.budgetsAvailable)
            item(key = "budgets") {
                Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
                Budgets(state, actions)
            }
        state.report?.let { report -> breakdowns(report, state, actions, recordsField) }
        if (state.report != null)
            item(key = "disclosure") {
                Column(
                    Modifier.padding(
                        horizontal = gutter.dp,
                        vertical = LocalKitTokens.current.sectionGap.dp,
                    )
                ) {
                    KitText(
                        stringResource(Res.string.usageCostDisclosure),
                        role = TypeRole.Secondary,
                        tone = TextTone.Secondary,
                    )
                    Spacer(Modifier.height(Space.S4.dp))
                    KitDetailsFold(
                        stringResource(Res.string.usageAboutNumbers),
                        stringResource(Res.string.kitCopyDetails),
                        notes =
                            listOf(
                                stringResource(Res.string.usageProviderScope),
                                stringResource(Res.string.usageScopedProviderTotals),
                                stringResource(Res.string.usageInspectionDisclosure),
                                stringResource(Res.string.usageBudgetDescription),
                            ),
                    )
                }
            }
    }
}

@Composable
private fun Budgets(state: UsageUi, actions: UsageActions) {
    var confirmingClear by remember(state.server) { mutableStateOf(false) }
    KitConfirm(
        spec =
            if (confirmingClear)
                KitConfirmSpec(
                    title = stringResource(Res.string.usageBudgetClearTitle),
                    confirmLabel = stringResource(Res.string.usageBudgetClearConfirm),
                    body = stringResource(Res.string.usageBudgetClearDescription),
                    kind = KitConfirmKind.Destructive,
                )
            else null,
        onConfirm = {
            confirmingClear = false
            actions.clearBudgets()
        },
        onCancel = { confirmingClear = false },
    )
    KitRowGroup(label = stringResource(Res.string.stateUsageBudgets)) {
        KitRow(
            title =
                stringResource(
                    if (state.usdBudget == null) Res.string.usageBudgetUsd
                    else Res.string.usageBudgetUsdTitle
                ),
            trailing =
                if (state.usdBudget == null)
                    KitRowTrailing.Value(stringResource(Res.string.usageBudgetNotSet))
                else KitRowTrailing.Chevron,
            enabled = !state.budgetSaving,
            disabledReason = stringResource(Res.string.rb50BudgetSaving),
            supporting =
                state.usdBudget?.let { limit ->
                    stringResource(
                        Res.string.usageBudgetProgress,
                        UsageFigures.money(state.report?.cost ?: 0.0),
                        UsageFigures.money(limit),
                        stringResource(Res.string.stateUsageUsd),
                    )
                },
            onClick = { actions.editBudget(UsageBudgetUnit.Usd) },
        )
        KitRow(
            title =
                stringResource(
                    if (state.tokenBudget == null) Res.string.usageBudgetTokens
                    else Res.string.usageBudgetTokensTitle
                ),
            trailing =
                if (state.tokenBudget == null)
                    KitRowTrailing.Value(stringResource(Res.string.usageBudgetNotSet))
                else KitRowTrailing.Chevron,
            enabled = !state.budgetSaving,
            disabledReason = stringResource(Res.string.rb50BudgetSaving),
            supporting =
                state.tokenBudget?.let { limit ->
                    stringResource(
                        Res.string.usageBudgetProgress,
                        UsageFigures.count(state.report?.tokens?.total ?: 0),
                        UsageFigures.count(limit.toLong()),
                        stringResource(Res.string.usageBudgetTokenUnit),
                    )
                },
            onClick = { actions.editBudget(UsageBudgetUnit.Tokens) },
        )
        if (state.hasSavedBudgets || state.usdBudget != null || state.tokenBudget != null)
            KitRow(
                title = stringResource(Res.string.usageBudgetClearAll),
                enabled = !state.budgetSaving,
                disabledReason = stringResource(Res.string.rb50BudgetSaving),
                onClick = { confirmingClear = true },
            )
    }
    KitText(
        stringResource(Res.string.usageBudgetDescription),
        Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
        tone = TextTone.Secondary,
    )
    if (
        state.report?.let { reading ->
            (state.usdBudget?.let { reading.cost >= it } == true) ||
                (state.tokenBudget?.let { reading.tokens.total.toDouble() >= it } == true)
        } == true
    )
        KitNotice(
            stringResource(
                if (state.previous) Res.string.usageBudgetPrevious
                else Res.string.usageBudgetReached
            )
        )
    if (state.budgetFailed) KitNotice(stringResource(Res.string.quotaBudgetSaveFailed))
}

@Composable
private fun BudgetEditor(state: UsageUi, actions: UsageActions) {
    val unit = state.budgetUnit ?: return
    key(unit, state.server, state.range, state.scope) {
        val field = rememberTextFieldState(state.budgetInput)
        val usd = unit == UsageBudgetUnit.Usd
        val hasCurrent = if (usd) state.usdBudget != null else state.tokenBudget != null
        val invalid =
            stringResource(
                if (usd) Res.string.usageBudgetInvalidUsd else Res.string.usageBudgetInvalidTokens
            )
        KitInputFormDialog(
            state = field,
            title =
                stringResource(
                    if (usd) Res.string.usageBudgetUsd else Res.string.usageBudgetTokens
                ),
            fieldLabel = stringResource(Res.string.usageBudgetAmount),
            confirmLabel =
                stringResource(
                    if (usd) Res.string.usageBudgetSaveUsd else Res.string.usageBudgetSaveTokens
                ),
            cancelLabel = stringResource(Res.string.workCancel),
            kind = if (usd) KitFieldKind.Text else KitFieldKind.Number,
            helper =
                stringResource(
                    if (usd) Res.string.usageBudgetHelperUsd else Res.string.usageBudgetHelperTokens
                ),
            validate = { value ->
                val parsed = value.trim().toDoubleOrNull()
                if (parsed != null && UsageBudgets.validLimit(parsed, unit)) null else invalid
            },
            onTextChanged = actions::budgetChanged,
            onSave = {
                // IME submission and the button both use the latest field, before any frame-delayed
                // state update.
                actions.budgetChanged(field.text.toString())
                actions.saveBudget()
            },
            onDismissRequest = actions::closeBudget,
            alternative =
                if (hasCurrent)
                    KitAction(stringResource(Res.string.usageBudgetRemove), actions::removeBudget)
                else null,
            working = state.budgetSaving,
            enabled =
                if (state.budgetSaving)
                    KitEnabled.Disabled(stringResource(Res.string.rb50BudgetSaving))
                else KitEnabled.Enabled,
            failure =
                if (state.budgetFailed) stringResource(Res.string.quotaBudgetSaveFailed) else null,
        )
    }
}

@Composable
private fun Total(report: UsageReport, range: UsageRange, timezone: String?, state: UsageUi) {
    val gutter = LocalKitTokens.current.gutter
    Column(Modifier.padding(horizontal = gutter.dp, vertical = Space.S4.dp)) {
        KitSurfacePanel {
            Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                val zone = timezone?.takeIf { UsageCalendar.isReadable(it) }
                val first = zone?.let { UsageCalendar.dayLabel(report.fromMs, it) }
                val last = zone?.let { UsageCalendar.dayLabel(report.toMs - 1, it) }
                val days =
                    if (first != null && last != null)
                        stringResource(Res.string.usagePeriod, first, last)
                    else null
                // The label names the days the server actually covered when they differ from the
                // range
                // asked for.
                val label =
                    when {
                        !report.coversRequest && days != null ->
                            stringResource(Res.string.usageSpentPeriod, days)
                        !report.coversRequest -> stringResource(Res.string.p12UsageCost)
                        else ->
                            when (range) {
                                UsageRange.Today -> stringResource(Res.string.usageSpentToday)
                                UsageRange.Last24Hours ->
                                    stringResource(Res.string.s9CostSpent24Hours)
                                UsageRange.SevenDays -> stringResource(Res.string.s9CostSpent7Days)
                                UsageRange.ThirtyDays ->
                                    stringResource(Res.string.usageSpentThirtyDays)
                                UsageRange.NinetyDays -> stringResource(Res.string.s9CostSpent90Days)
                                UsageRange.Year -> stringResource(Res.string.usageSpentYear)
                                UsageRange.AllTime -> stringResource(Res.string.usageSpentAllTime)
                            }
                    }
                KitText(label, role = TypeRole.Label, tone = TextTone.Secondary)
                KitText(
                    cost(report.cost),
                    Modifier.testTag("usage-total-cost"),
                    role = TypeRole.LargeTitle,
                )
                Spacer(Modifier.height(Space.S1.dp))
                val project =
                    (state.scope as? UsageScope.Project)?.let { scope ->
                        state.projects.firstOrNull { it.id == scope.id }?.name
                    }
                if (project != null)
                    KitText(project, role = TypeRole.Secondary, tone = TextTone.Secondary)
                if (zone != null && days != null) {
                    if (report.coversRequest)
                        KitText(days, role = TypeRole.Secondary, tone = TextTone.Secondary)
                    KitText(
                        stringResource(Res.string.usageTimezone, zone),
                        role = TypeRole.Caption,
                        tone = TextTone.Tertiary,
                    )
                }
                if (report.empty) {
                    Spacer(Modifier.height(Space.S3.dp))
                    KitText(stringResource(Res.string.usageEmpty))
                }
            }
        }
    }
}

@Composable
private fun Filters(state: UsageUi, actions: UsageActions) {
    var picker by remember { mutableStateOf<String?>(null) }
    val rangeChoices =
        listOf(
            KitChoice(UsageRange.Today, stringResource(Res.string.usageToday)),
            KitChoice(UsageRange.Last24Hours, stringResource(Res.string.s9CostLast24Hours)),
            KitChoice(UsageRange.SevenDays, stringResource(Res.string.s9CostLast7Days)),
            KitChoice(UsageRange.ThirtyDays, stringResource(Res.string.usageThirtyDays)),
            KitChoice(UsageRange.NinetyDays, stringResource(Res.string.s9CostLast90Days)),
            KitChoice(UsageRange.Year, stringResource(Res.string.usageYear)),
            KitChoice(UsageRange.AllTime, stringResource(Res.string.usageAllTime)),
        )
    val scopeChoices =
        listOf(
            KitChoice<UsageScope>(
                UsageScope.AllProjects,
                stringResource(Res.string.usageAllProjects),
            )
        ) + state.projects.map { KitChoice<UsageScope>(UsageScope.Project(it.id), it.name) }
    Column {
        Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
        if (state.servers.size > 1)
            KitRowGroup {
                KitRow(
                    stringResource(Res.string.settingsV3ServersBlock),
                    trailing = KitRowTrailing.Value(state.serverName.orEmpty()),
                    onClick = { picker = "server" },
                )
            }
        KitRowGroup {
            KitRow(
                stringResource(Res.string.usageRangeLabel),
                modifier = Modifier.testTag("usage-range"),
                trailing =
                    KitRowTrailing.Value(rangeChoices.first { it.value == state.range }.title),
                onClick = { picker = "range" },
            )
            KitRow(
                stringResource(Res.string.usageScope),
                modifier = Modifier.testTag("usage-scope"),
                trailing =
                    KitRowTrailing.Value(
                        scopeChoices.firstOrNull { it.value == state.scope }?.title
                            ?: stringResource(Res.string.usageCurrentProject)
                    ),
                onClick = { picker = "scope" },
            )
        }
        if (state.projectsFailed) KitNotice(stringResource(Res.string.p12UsageProjectsFailed))
    }
    KitSheet(
        picker != null,
        { picker = null },
        stringResource(
            when (picker) {
                "range" -> Res.string.usageRangeLabel
                "server" -> Res.string.settingsV3ServersBlock
                else -> Res.string.usageScope
            }
        ),
    ) {
        when (picker) {
            "range" ->
                KitChoiceList(
                    rangeChoices,
                    state.range,
                    stringResource(Res.string.usageRangeLabel),
                    onSelect = {
                        picker = null
                        actions.chooseRange(it)
                    },
                )
            "scope" ->
                KitChoiceList(
                    scopeChoices,
                    state.scope,
                    stringResource(Res.string.usageScope),
                    onSelect = {
                        picker = null
                        actions.chooseScope(it)
                    },
                )
            "server" ->
                KitChoiceList(
                    state.servers.map { KitChoice(it.id, it.name) },
                    state.server,
                    stringResource(Res.string.settingsV3ServersBlock),
                    empty = {},
                    onSelect = {
                        picker = null
                        actions.chooseServer(it)
                    },
                )
            else -> Unit
        }
    }
}

@Composable
private fun Status(state: UsageUi, actions: UsageActions) {
    val retry = KitLineAction(stringResource(Res.string.p12UsageTryAgain), actions::refresh)
    when (state.phase) {
        UsagePhase.Loading -> if (state.report == null) KitSkeletonRows()
        UsagePhase.Ready -> Unit
        UsagePhase.Unsupported ->
            KitStateView(
                kind = KitStateKind.Calm,
                title = stringResource(Res.string.usageUnsupported),
                size = KitStateSize.Inline,
            )
        UsagePhase.NoTimeZone ->
            KitNotice(stringResource(Res.string.usageTimezoneUnavailable), actions = listOf(retry))
        UsagePhase.Authorization ->
            KitNotice(stringResource(Res.string.usageAuthorization), actions = listOf(retry))
        UsagePhase.Invalid ->
            KitNotice(stringResource(Res.string.usageInvalidResponse), actions = listOf(retry))
        UsagePhase.Failed ->
            KitErrorNotice(
                stringResource(Res.string.p12UsageFailed),
                Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
                failure = state.failure,
                detailsLabel = stringResource(Res.string.kitDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
                retry = retry,
            )
        UsagePhase.NoServer -> Unit
    }
    if (state.previous || (state.refreshing && state.report != null))
        KitText(
            stringResource(Res.string.usagePreviousResult),
            Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
            role = TypeRole.Secondary,
            tone = TextTone.Secondary,
        )
}

private fun LazyListScope.breakdowns(
    report: UsageReport,
    state: UsageUi,
    actions: UsageActions,
    recordsField: TextFieldState,
) {
    if (report.empty) return
    item(key = "counts") {
        Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
        KitSectionLabel(stringResource(Res.string.usageScopedTotals))
        Column(Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)) {
            KitSurfacePanel {
                KitMetricGrid(
                    listOf(
                        stringResource(Res.string.usageSessions) to
                            UsageFigures.count(report.sessions),
                        stringResource(Res.string.usagePrompts) to
                            UsageFigures.count(report.prompts),
                        stringResource(Res.string.usageSteps) to UsageFigures.count(report.steps),
                        stringResource(Res.string.usageSubagents) to
                            UsageFigures.count(report.subagents),
                        stringResource(Res.string.usageActiveDays) to
                            UsageFigures.count(report.activeDays),
                        stringResource(Res.string.usageStreak) to UsageFigures.count(report.streak),
                    )
                )
            }
        }
    }
    item(key = "chart") { Chart(report, state) }
    if (report.activity.isNotEmpty())
        item(key = "days") {
            Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
            KitRowGroup(label = stringResource(Res.string.usageBusiestDays)) {
                // Top five by activity, as usage_screen.dart:727-729.
                report.activity
                    .sortedByDescending { it.steps }
                    .take(5)
                    .forEach { day -> // F: lib/ui/screens/usage_screen.dart:730
                        KitRow(
                            day.date,
                            trailing =
                                KitRowTrailing.Value(
                                    pluralStringResource(
                                        Res.plurals.usageDaySteps,
                                        day.steps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                                        UsageFigures.count(day.steps),
                                    ),
                                    chevron = false,
                                ),
                        )
                    }
            }
        }
    item(key = "tokens") {
        Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
        KitSectionLabel(stringResource(Res.string.usageTokens))
        val t = report.tokens
        Column(Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)) {
            KitSurfacePanel {
                KitMetricGrid(
                    listOf(
                        stringResource(Res.string.usageTotalTokens) to UsageFigures.count(t.total),
                        stringResource(Res.string.usageInput) to UsageFigures.count(t.input),
                        stringResource(Res.string.usageOutput) to UsageFigures.count(t.output),
                        stringResource(Res.string.usageReasoning) to
                            UsageFigures.count(t.reasoning),
                        stringResource(Res.string.usageCacheRead) to
                            UsageFigures.count(t.cacheRead),
                        stringResource(Res.string.usageCacheWrite) to
                            UsageFigures.count(t.cacheWrite),
                    )
                )
            }
            Spacer(Modifier.height(Space.S2.dp))
            // Cached vs uncached input and output, as plain sentences (B-160).
            KitDetailsFold(
                stringResource(Res.string.s9CostTokenDetails),
                stringResource(Res.string.kitCopyDetails),
                notes =
                    listOf(
                        stringResource(Res.string.s9CostCached, UsageFigures.count(t.cacheRead)),
                        stringResource(Res.string.s9CostUncached, UsageFigures.count(t.input)),
                        stringResource(Res.string.s9CostCacheWrites, UsageFigures.count(t.cacheWrite)),
                        stringResource(
                            Res.string.s9CostOutput,
                            UsageFigures.count(t.output + t.reasoning),
                            UsageFigures.count(t.reasoning),
                        ),
                    ),
            )
        }
    }
    val matching = state.matchingModels
    val providerIds =
        (report.models.map { it.providerId } + listOfNotNull(state.providerFilter))
            .distinct()
            .sorted()
    if (report.models.isNotEmpty() || state.hasRecordFilters) {
        item(key = "records-search") {
            Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
            KitSearchField(
                label = stringResource(Res.string.usageSearchRecords),
                onSearch = actions::searchRecords,
                state = recordsField,
                modifier =
                    Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
                        .testTag("usage-search"),
                resultCount = if (state.hasRecordFilters) matching.size else null,
                filters =
                    providerIds.map { id ->
                        KitMenuItem(
                            label = id,
                            onSelect = {
                                actions.chooseProvider(if (state.providerFilter == id) null else id)
                            },
                            checked = state.providerFilter == id,
                        )
                    },
                activeFilter = state.providerFilter,
                removeFilterLabel = stringResource(Res.string.kitSearchClear),
                onClearFilter = { actions.chooseProvider(null) },
            )
        }
    }
    val providers = UsageFigures.providers(matching)
    if (providers.isNotEmpty()) {
        item(key = "providers") {
            // The split (B-160): each provider's share of the cost and its tokens. The server reports cost per
            // provider and model only, so the split is by provider and says so.
            val total = providers.sumOf { it.cost ?: 0.0 }
            KitRowGroup(label = stringResource(Res.string.s9CostSplit)) {
                for (p in providers) {
                    val share = UsageFigures.share(p.cost, total)
                    KitRow(
                        title = p.providerId,
                        supporting =
                            listOfNotNull(
                                    share?.let {
                                        stringResource(
                                            Res.string.s9CostSplitRow,
                                            UsageFigures.percent(it),
                                            UsageFigures.compact(p.tokens),
                                        )
                                    },
                                    pluralStringResource(
                                        Res.plurals.usageProviderModelCount,
                                        p.models,
                                        p.models,
                                    ),
                                )
                                .joinToString(" · "),
                        trailing =
                            KitRowTrailing.Value(
                                p.cost?.let { cost(it) }
                                    ?: stringResource(Res.string.usageProviderCostUnavailable),
                                chevron = false,
                            ),
                    )
                }
            }
            KitText(
                stringResource(Res.string.s9CostSplitNote),
                Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
        }
    }
    item(key = "models") {
        val modelsLabel = stringResource(Res.string.usageModels)
        val models = UsageFigures.models(matching)
        if (models.isEmpty() && state.hasRecordFilters) {
            KitSearchNoMatch(
                query = state.modelSearch.ifBlank { state.providerFilter.orEmpty() },
                onClear = actions::clearRecordFilters,
                what = modelsLabel,
                modifier = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
            )
            return@item
        }
        if (state.hasRecordFilters) {
            val subtotal = models.sumOf { it.cost }
            KitSectionLabel(
                listOf(
                        stringResource(
                            Res.string.usageMatchingRecords,
                            UsageFigures.count(models.size.toLong()),
                        ),
                        if (subtotal.isFinite()) cost(subtotal)
                        else stringResource(Res.string.usageProviderCostUnavailable),
                        stringResource(
                            Res.string.usageModelSteps,
                            UsageFigures.count(models.sumOf { it.steps }),
                        ),
                        stringResource(
                            Res.string.usageModelTokens,
                            UsageFigures.count(models.sumOf { it.tokens.total }),
                        ),
                    )
                    .joinToString(" · ")
            )
        }
        KitRowGroup(label = modelsLabel) {
            if (models.isEmpty()) {
                KitText(
                    stringResource(Res.string.usageNoModels),
                    role = KitTextRole.Supporting,
                    modifier = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
                )
            }
            for (m in models) {
                val name =
                    if (m.variant.isNullOrBlank()) m.modelId else "${m.modelId} · ${m.variant}"
                KitRow(
                    title = name,
                    supporting =
                        stringResource(Res.string.usageModelSteps, UsageFigures.count(m.steps)) +
                            " · " +
                            stringResource(
                                Res.string.usageModelTokens,
                                UsageFigures.compact(m.tokens.total),
                            ),
                    server = m.providerId,
                    trailing = KitRowTrailing.Value(cost(m.cost), chevron = false),
                )
            }
        }
    }
    item(key = "tools") {
        KitRowGroup(label = stringResource(Res.string.usageToolReliability)) {
            val tools = report.tools
            val gutter = LocalKitTokens.current.gutter
            when {
                tools == null ->
                    KitText(
                        stringResource(Res.string.usageToolsUnavailable),
                        role = KitTextRole.Supporting,
                        modifier = Modifier.padding(horizontal = gutter.dp),
                    )
                tools.calls == 0L ->
                    KitText(
                        stringResource(Res.string.usageNoTools),
                        role = KitTextRole.Supporting,
                        modifier = Modifier.padding(horizontal = gutter.dp),
                    )
                else -> {
                    ValueRow(
                        stringResource(Res.string.usageToolCalls),
                        UsageFigures.count(tools.calls),
                    )
                    ValueRow(
                        stringResource(Res.string.usageSucceeded),
                        UsageFigures.count(tools.succeeded),
                    )
                    ValueRow(
                        stringResource(Res.string.usageFailed),
                        UsageFigures.count(tools.failed),
                    )
                    ValueRow(
                        stringResource(Res.string.usageUnfinished),
                        UsageFigures.count(tools.unfinished),
                    )
                    KitText(
                        tools.successRate?.let {
                            stringResource(Res.string.usageSuccessRate, UsageFigures.percent(it))
                        } ?: stringResource(Res.string.usageNoFinishedTools),
                        role = KitTextRole.Supporting,
                        modifier = Modifier.padding(horizontal = gutter.dp, vertical = Space.S2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ValueRow(title: String, value: String) {
    KitRow(title = title, trailing = KitRowTrailing.Value(value, chevron = false))
}

@Composable
private fun cost(value: Double): String =
    if (UsageFigures.isTiny(value)) stringResource(Res.string.usageTinyCost)
    else
        UsageFigures.money(value, symbolAfter = LocalLayoutDirection.current == LayoutDirection.Rtl)

/** 24h · 7d · 30d · 90d (B-160); the kit's segmented control, two to four parts. */
@Composable
private fun RangeSegment(state: UsageUi, actions: UsageActions) {
    val words =
        mapOf(
            UsageRange.Last24Hours to stringResource(Res.string.s9Cost24h),
            UsageRange.SevenDays to stringResource(Res.string.s9Cost7d),
            UsageRange.ThirtyDays to stringResource(Res.string.s9Cost30d),
            UsageRange.NinetyDays to stringResource(Res.string.s9Cost90d),
        )
    KitSegmented(
        segments = UsageRange.segment.map { KitSegment(it, words.getValue(it)) },
        selected = state.range,
        semanticsLabel = stringResource(Res.string.s9CostRange),
        modifier =
            Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S2.dp)
                .testTag("usage-segment"),
        onChange = actions::chooseRange,
    )
}

/**
 * One small chart (B-160): agent steps per calendar day, the only series the server reports by day. For Today and
 * 24 hours it says plainly that there is no hourly series instead of drawing one from nothing.
 */
@Composable
private fun Chart(report: UsageReport, state: UsageUi) {
    val gutter = LocalKitTokens.current.gutter
    val zone = state.timezone?.takeIf { UsageCalendar.isReadable(it) } ?: return
    Column(Modifier.padding(horizontal = gutter.dp)) {
        Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
        KitText(
            stringResource(Res.string.s9CostChartDaily),
            role = TypeRole.Label,
            tone = TextTone.Secondary,
        )
        Spacer(Modifier.height(Space.S2.dp))
        val days = if (state.range.charted) UsageFigures.dailySteps(report, zone) else emptyList()
        if (days.size < 2) {
            KitText(
                stringResource(Res.string.s9CostChartHourly),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
            return@Column
        }
        val busiest = days.maxBy { it.steps }
        KitTrendLine(
            values = days.map { it.steps.toDouble() },
            semantics =
                stringResource(
                    Res.string.s9CostChartSpoken,
                    days.first().date,
                    days.last().date,
                    busiest.date,
                ),
            modifier = Modifier.testTag("usage-chart"),
        )
    }
}
