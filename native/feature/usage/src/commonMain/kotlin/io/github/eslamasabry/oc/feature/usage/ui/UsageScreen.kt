package io.github.eslamasabry.oc.feature.usage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.integrations.usage.UsageBudgetUnit
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
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitFilterChips
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.kitTopBarClose
import io.github.eslamasabry.oc.ui.words.res.p12SearchNoServerTitle
import io.github.eslamasabry.oc.ui.words.res.p12SearchOpenServers
import io.github.eslamasabry.oc.ui.words.res.p12UsageActivity
import io.github.eslamasabry.oc.ui.words.res.p12UsageCost
import io.github.eslamasabry.oc.ui.words.res.p12UsageCounts
import io.github.eslamasabry.oc.ui.words.res.p12UsageDay
import io.github.eslamasabry.oc.ui.words.res.p12UsageFailed
import io.github.eslamasabry.oc.ui.words.res.p12UsageNoServerBody
import io.github.eslamasabry.oc.ui.words.res.p12UsageProjectsFailed
import io.github.eslamasabry.oc.ui.words.res.p12UsageRange
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
import io.github.eslamasabry.oc.ui.words.res.usageSpentAllTime
import io.github.eslamasabry.oc.ui.words.res.usageSpentPeriod
import io.github.eslamasabry.oc.ui.words.res.usageSpentThirtyDays
import io.github.eslamasabry.oc.ui.words.res.usageSpentToday
import io.github.eslamasabry.oc.ui.words.res.usageSpentYear
import io.github.eslamasabry.oc.ui.words.res.usagePreviousResult
import io.github.eslamasabry.oc.ui.words.res.usagePrompts
import io.github.eslamasabry.oc.ui.words.res.usageProviderCostUnavailable
import io.github.eslamasabry.oc.ui.words.res.usageProviderModelCount
import io.github.eslamasabry.oc.ui.words.res.usageProviders
import io.github.eslamasabry.oc.ui.words.res.usageReasoning
import io.github.eslamasabry.oc.ui.words.res.usageRefresh
import io.github.eslamasabry.oc.ui.words.res.usageScope
import io.github.eslamasabry.oc.ui.words.res.usageSessions
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
                        if (state.server == null) emptyList()
                        else
                            listOf(
                                KitLineAction(
                                    stringResource(Res.string.usageRefresh),
                                    actions::refresh,
                                    enabled = !state.refreshing,
                                    disabledReason = stringResource(Res.string.usageLoading),
                                )
                            ),
                )
            },
            loading = state.refreshing,
            loadingLabel = stringResource(Res.string.usageLoading),
            width = KitScreenWidth.Reading,
        ) {
            UsageContent(state, actions)
        }
    }
}

/**
 * The Spending body under the app-owned Usage hub, and the body of [UsageScreen]. The budget editor
 * sheet rides along; the nearest kit sheet host (the screen's own, or a [KitScreen]'s) shows it.
 */
@Composable
public fun UsageContent(state: UsageUi, actions: UsageActions) {
    val gutter = LocalKitTokens.current.gutter
    LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
        if (state.phase == UsagePhase.NoServer) {
            item(key = "no-server") {
                KitStateView(
                    kind = KitStateKind.Missing,
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
        state.report?.let { report ->
            item(key = "total") { Total(report, state.range, state.timezone) }
        }
        item(key = "filters") { Filters(state, actions) }
        item(key = "status") { Status(state, actions) }
        if (state.budgetsAvailable) item(key = "budgets") { Budgets(state, actions) }
        state.report?.let { report -> breakdowns(report) }
        item(key = "disclosure") {
            KitText(
                stringResource(Res.string.usageCostDisclosure),
                role = KitTextRole.Supporting,
                modifier = Modifier.padding(horizontal = gutter.dp, vertical = Space.S3.dp),
            )
        }
    }
    BudgetEditor(state, actions)
}

@Composable
private fun Budgets(state: UsageUi, actions: UsageActions) {
    KitRowGroup(label = stringResource(Res.string.stateUsageBudgets)) {
        KitRow(
            title = stringResource(Res.string.usageBudgetUsdTitle),
            supporting =
                state.usdBudget?.let { limit ->
                    stringResource(
                        Res.string.usageBudgetProgress,
                        UsageFigures.money(state.report?.cost ?: 0.0),
                        UsageFigures.money(limit),
                        stringResource(Res.string.stateUsageUsd),
                    )
                } ?: stringResource(Res.string.usageBudgetNotSet),
            onClick = { actions.editBudget(UsageBudgetUnit.Usd) },
        )
        KitRow(
            title = stringResource(Res.string.usageBudgetTokensTitle),
            supporting =
                state.tokenBudget?.let { limit ->
                    stringResource(
                        Res.string.usageBudgetProgress,
                        UsageFigures.count(state.report?.tokens?.total ?: 0),
                        UsageFigures.count(limit.toLong()),
                        stringResource(Res.string.usageBudgetTokenUnit),
                    )
                } ?: stringResource(Res.string.usageBudgetNotSet),
            onClick = { actions.editBudget(UsageBudgetUnit.Tokens) },
        )
        if (state.hasSavedBudgets)
            KitRow(
                title = stringResource(Res.string.usageBudgetClearAll),
                onClick = actions::clearBudgets,
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
        KitSheet(
            visible = true,
            title =
                stringResource(
                    if (unit == UsageBudgetUnit.Usd) Res.string.usageBudgetUsdTitle
                    else Res.string.usageBudgetTokensTitle
                ),
            closeLabel = stringResource(Res.string.kitTopBarClose),
            onDismissRequest = actions::closeBudget,
            primary =
                KitAction(
                    stringResource(
                        if (unit == UsageBudgetUnit.Usd) Res.string.usageBudgetSaveUsd
                        else Res.string.usageBudgetSaveTokens
                    ),
                    actions::saveBudget,
                ),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                KitField(
                    field,
                    stringResource(Res.string.usageBudgetAmount),
                    kind = KitFieldKind.Number,
                    onTextChanged = actions::budgetChanged,
                    checking = state.budgetSaving,
                    error =
                        if (state.budgetInvalid)
                            stringResource(
                                if (unit == UsageBudgetUnit.Usd) Res.string.usageBudgetInvalidUsd
                                else Res.string.usageBudgetInvalidTokens
                            )
                        else null,
                )
                if (state.budgetFailed) KitNotice(stringResource(Res.string.quotaBudgetSaveFailed))
                KitButton(
                    stringResource(Res.string.usageBudgetRemove),
                    actions::removeBudget,
                    role = KitButtonRole.Tertiary,
                    working = state.budgetSaving,
                )
            }
        }
    }
}

@Composable
private fun Total(report: UsageReport, range: UsageRange, timezone: String?) {
    val gutter = LocalKitTokens.current.gutter
    Column(
        Modifier.padding(horizontal = gutter.dp, vertical = Space.S3.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
    ) {
        val zone = timezone?.takeIf { UsageCalendar.isReadable(it) }
        val first = zone?.let { UsageCalendar.dayLabel(report.fromMs, it) }
        val last = zone?.let { UsageCalendar.dayLabel(report.toMs - 1, it) }
        val days = if (first != null && last != null) stringResource(Res.string.usagePeriod, first, last) else null
        // The label names the days the server actually covered when they differ from the range asked for.
        val label =
            when {
                !report.coversRequest && days != null -> stringResource(Res.string.usageSpentPeriod, days)
                !report.coversRequest -> stringResource(Res.string.p12UsageCost)
                else ->
                    when (range) {
                        UsageRange.Today -> stringResource(Res.string.usageSpentToday)
                        UsageRange.ThirtyDays -> stringResource(Res.string.usageSpentThirtyDays)
                        UsageRange.Year -> stringResource(Res.string.usageSpentYear)
                        UsageRange.AllTime -> stringResource(Res.string.usageSpentAllTime)
                    }
            }
        KitText(label, role = TypeRole.Label, tone = TextTone.Secondary)
        KitText(cost(report.cost), role = TypeRole.LargeTitle)
        if (zone != null && days != null) {
            if (report.coversRequest) KitText(days, role = TypeRole.Secondary, tone = TextTone.Secondary)
            KitText(stringResource(Res.string.usageTimezone, zone), role = TypeRole.Caption, tone = TextTone.Tertiary)
        }
    }
}

@Composable
private fun Filters(state: UsageUi, actions: UsageActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        if (state.servers.size > 1) {
            KitFilterChips {
                for (server in state.servers) {
                    KitChip(
                        label = server.name,
                        kind = KitChipKind.Action,
                        selected = server.id == state.server,
                        onClick = { actions.chooseServer(server.id) },
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)) {
            KitSegmented(
                segments =
                    listOf(
                        KitSegment(UsageRange.Today, stringResource(Res.string.usageToday)),
                        KitSegment(
                            UsageRange.ThirtyDays,
                            stringResource(Res.string.usageThirtyDays),
                        ),
                        KitSegment(UsageRange.Year, stringResource(Res.string.usageYear)),
                        KitSegment(UsageRange.AllTime, stringResource(Res.string.usageAllTime)),
                    ),
                selected = state.range,
                semanticsLabel = stringResource(Res.string.p12UsageRange),
                onChange = actions::chooseRange,
            )
        }
        KitSectionLabel(stringResource(Res.string.usageScope))
        KitFilterChips {
            KitChip(
                label = stringResource(Res.string.usageAllProjects),
                kind = KitChipKind.Action,
                selected = state.scope == UsageScope.AllProjects,
                onClick = { actions.chooseScope(UsageScope.AllProjects) },
            )
            for (project in state.projects) {
                KitChip(
                    label = project.name,
                    kind = KitChipKind.Action,
                    selected = state.scope == UsageScope.Project(project.id),
                    onClick = { actions.chooseScope(UsageScope.Project(project.id)) },
                )
            }
        }
        if (state.projectsFailed) KitNotice(stringResource(Res.string.p12UsageProjectsFailed))
    }
}

@Composable
private fun Status(state: UsageUi, actions: UsageActions) {
    val retry = KitLineAction(stringResource(Res.string.p12UsageTryAgain), actions::refresh)
    when (state.phase) {
        UsagePhase.Loading -> if (state.report == null) KitSkeletonRows()
        UsagePhase.Ready ->
            if (state.report?.empty == true) {
                KitStateView(
                    kind = KitStateKind.Empty,
                    title = stringResource(Res.string.usageEmpty),
                    body = stringResource(Res.string.usageDescription),
                )
            }
        UsagePhase.Unsupported ->
            KitStateView(
                kind = KitStateKind.Calm,
                title = stringResource(Res.string.usageUnsupported),
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
    if (state.previous) KitNotice(stringResource(Res.string.usagePreviousResult))
}

private fun LazyListScope.breakdowns(report: UsageReport) {
    if (report.empty) return
    item(key = "counts") {
        KitRowGroup(label = stringResource(Res.string.p12UsageCounts)) {
            ValueRow(stringResource(Res.string.usageSessions), UsageFigures.count(report.sessions))
            ValueRow(
                stringResource(Res.string.usageSubagents),
                UsageFigures.count(report.subagents),
            )
            ValueRow(stringResource(Res.string.usagePrompts), UsageFigures.count(report.prompts))
            ValueRow(stringResource(Res.string.usageSteps), UsageFigures.count(report.steps))
            ValueRow(
                stringResource(Res.string.usageActiveDays),
                UsageFigures.count(report.activeDays),
            )
            ValueRow(stringResource(Res.string.usageStreak), UsageFigures.count(report.streak))
        }
    }
    item(key = "tokens") {
        val t = report.tokens
        KitRowGroup(label = stringResource(Res.string.usageTokens)) {
            ValueRow(stringResource(Res.string.usageTotalTokens), UsageFigures.compact(t.total))
            ValueRow(stringResource(Res.string.usageInput), UsageFigures.compact(t.input))
            ValueRow(stringResource(Res.string.usageOutput), UsageFigures.compact(t.output))
            ValueRow(stringResource(Res.string.usageReasoning), UsageFigures.compact(t.reasoning))
            ValueRow(stringResource(Res.string.usageCacheRead), UsageFigures.compact(t.cacheRead))
            ValueRow(stringResource(Res.string.usageCacheWrite), UsageFigures.compact(t.cacheWrite))
        }
    }
    val providers = UsageFigures.providers(report.models)
    if (providers.isNotEmpty()) {
        item(key = "providers") {
            KitRowGroup(label = stringResource(Res.string.usageProviders)) {
                for (p in providers) {
                    KitRow(
                        title = p.providerId,
                        supporting =
                            pluralStringResource(
                                Res.plurals.usageProviderModelCount,
                                p.models,
                                p.models,
                            ),
                        trailing =
                            KitRowTrailing.Value(
                                p.cost?.let { cost(it) }
                                    ?: stringResource(Res.string.usageProviderCostUnavailable),
                                chevron = false,
                            ),
                    )
                }
            }
        }
    }
    item(key = "models") {
        KitRowGroup(label = stringResource(Res.string.usageModels)) {
            val models = UsageFigures.models(report.models)
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
    val days = UsageFigures.activeDays(report.activity)
    if (days.isNotEmpty()) {
        item(key = "days") {
            KitRowGroup(label = stringResource(Res.string.p12UsageActivity)) {
                for (d in days) KitRow(
                    title =
                        stringResource(Res.string.p12UsageDay, d.date, UsageFigures.count(d.steps))
                )
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
    if (UsageFigures.isTiny(value)) stringResource(Res.string.usageTinyCost) else UsageFigures.money(value, symbolAfter = LocalLayoutDirection.current == LayoutDirection.Rtl)
