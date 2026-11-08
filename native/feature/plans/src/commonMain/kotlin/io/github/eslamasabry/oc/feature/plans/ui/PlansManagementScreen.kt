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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWidgetWindows
import io.github.eslamasabry.oc.feature.plans.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.choice.*
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
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
                PlanManagePage.Reader -> Res.string.usageReadFromTitleHere
                PlanManagePage.Add -> Res.string.m40UsageAdd
                else -> Res.string.m40PlansTitle
            }
        )
    KitScreen(
        topBar = {
            KitTopBar(
                title =
                    if (
                        state.page == PlanManagePage.Reader && state.readers[state.readerId] != null
                    )
                        stringResource(Res.string.usageReadFrom, state.readerName)
                    else title,
                exit = KitTopBarExit.Back,
                exitLabel = words.back,
                onExit = actions::dismiss,
            )
        },
        loading = state.busy,
        loadingLabel = words.loading,
        width = KitScreenWidth.Reading,
        edges = KitScreenEdges.Bleed,
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement =
                Arrangement.spacedBy(
                    if (state.page == PlanManagePage.Widget || state.page == PlanManagePage.Reader)
                        0.dp
                    else Space.S2.dp
                ),
        ) {
            if (
                state.failure != null &&
                    state.page != PlanManagePage.Widget &&
                    state.page != PlanManagePage.Reader
            )
                KitErrorNotice(
                    stringResource(Res.string.m40UsageSaveFailed),
                    failure = state.failure,
                    detailsLabel = words.details,
                    copyLabel = words.copyDetails,
                )
            when (state.page) {
                PlanManagePage.Alerts -> AlertsBody(state, plans, actions)
                PlanManagePage.Widget -> WidgetBody(state, plans, actions)
                PlanManagePage.Reader -> UsageReaderBody(state, plans, actions)
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
    val hold =
        if (state.busy) KitEnabled.Disabled(stringResource(Res.string.e7AppearanceSaving))
        else KitEnabled.Enabled
    KitRowGroup {
        KitSwitchRow(
            title = stringResource(Res.string.m40UsageAlertsWatch),
            supporting = stringResource(Res.string.m40UsageAlertsEvery),
            checked = p.enabled,
            enabled = hold,
            supportingMaxLines = 2, // F: lib/ui/screens/usage/usage_alerts_screen.dart:109
            onCheckedChange = actions::setAlerts,
        )
    }
    if (!p.enabled) return
    KitRowGroup(label = stringResource(Res.string.m40UsageTellMe)) {
        ThresholdChooser(
            p.threshold,
            false,
            null,
            enabled = hold,
            onSelect = actions::setDefaultThreshold,
        )
        KitSwitchRow(
            title = stringResource(Res.string.m40UsageReset),
            checked = p.notifyReset,
            enabled = hold,
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
                    enabled = hold,
                ) {
                    actions.setThreshold(card.key, if (it < 0) null else it)
                }
                KitSwitchRow(
                    title = stringResource(Res.string.m40UsageReset),
                    checked = card.notifyReset,
                    enabled = hold,
                    onCheckedChange = { actions.setReset(card.key, it) },
                )
            }
        }
    KitRowGroup(label = stringResource(Res.string.m40UsageWhen)) {
        KitSwitchRow(
            title = stringResource(Res.string.m40UsageQuiet),
            supporting = stringResource(Res.string.m40UsageQuietShared),
            checked = p.quietStart != null && p.quietEnd != null,
            enabled = hold,
            onCheckedChange = actions::setQuiet,
        )
        if (p.quietStart != null && p.quietEnd != null) {
            MinuteChooser(
                stringResource(Res.string.m40UsageQuietStart),
                p.quietStart,
                actions::setQuietStart,
                hold,
            )
            MinuteChooser(
                stringResource(Res.string.m40UsageQuietEnd),
                p.quietEnd,
                actions::setQuietEnd,
                hold,
            )
            if (p.quietStart == p.quietEnd)
                KitNotice(stringResource(Res.string.m40UsageQuietAllDay))
        }
        KitSwitchRow(
            title = stringResource(Res.string.m40UsageWifi),
            checked = p.wifiOnly,
            enabled = hold,
            onCheckedChange = actions::setWifi,
        )
    }
    if (
        plans.phase == PlansPhase.Empty || (plans.phase == PlansPhase.List && plans.cards.isEmpty())
    )
        KitText(
            stringResource(Res.string.m40UsageNothingToWatch),
            Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
            role = TypeRole.Caption,
            tone = TextTone.Secondary,
        )
}

@Composable
internal fun ThresholdChooser(
    level: Int,
    inherit: Boolean,
    default: Int?,
    inherited: Boolean = false,
    enabled: KitEnabled = KitEnabled.Enabled,
    onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val title = stringResource(if (inherit) Res.string.usageAlertAt else Res.string.m40UsageDownTo)
    @Composable
    fun label(value: Int): String =
        if (inherit) stringResource(Res.string.usageUsedPercent, value.toString())
        else plansWords().left(100 - value) // F: lib/ui/screens/usage/usage_alerts_screen.dart:136
    val choices = buildList {
        if (inherit && default != null)
            add(
                KitChoice(
                    -1,
                    stringResource(Res.string.usageAlertSameAsOthers, default.toString()),
                    enabled = enabled,
                )
            )
        for (value in
            (listOf(50, 60, 70, 80, 90, 100) +
                    level) // F: lib/ui/screens/usage/usage_alerts_screen.dart:13
                .distinct()
                .sorted()) // F: lib/ui/screens/usage/usage_alerts_screen.dart:13
        add(KitChoice(value, label(value), enabled = enabled))
    }
    KitRow(
        title = title,
        trailing = KitRowTrailing.Value(label(level)),
        enabled = enabled.isEnabled,
        disabledReason = enabled.reason,
        onClick = { open = !open },
        modifier = Modifier.testTag("usage-alert-threshold"),
    )
    if (inherit) {
        // Subscription details still use the existing sheet; its owner must provide a page before
        // a second sheet can replace it. Keep this available path until that navigation seam lands.
        if (open)
            for (choice in choices) KitChoiceRow(
                title = choice.title,
                selected = choice.value == if (inherited) -1 else level,
                enabled = enabled,
                onClick = {
                    open = false
                    onSelect(choice.value)
                },
            )
    } else
        KitSheet(
            visible = open,
            title = title,
            onDismissRequest = { open = false },
        ) {
            KitChoiceList(
                choices,
                level,
                title,
                onSelect = {
                    open = false
                    onSelect(it)
                },
            )
        }
}

@Composable
private fun MinuteChooser(
    title: String,
    minute: Int,
    onSelect: (Int) -> Unit,
    enabled: KitEnabled = KitEnabled.Enabled,
) {
    var open by remember { mutableStateOf(false) }
    fun clock(value: Int) =
        "${(value/60).toString().padStart(2,'0')}:${(value%60).toString().padStart(2,'0')}"
    KitRow(
        title = title,
        trailing = KitRowTrailing.Value(clock(minute)),
        enabled = enabled.isEnabled,
        disabledReason = enabled.reason,
        onClick = { open = !open },
    )
    if (open)
        for (value in ((0 until 1440 step 30).toList() + minute).distinct().sorted()) KitChoiceRow(
            title = clock(value),
            selected = value == minute,
            enabled = enabled,
            onClick = {
                open = false
                onSelect(value)
            },
        )
}

@Composable
private fun WidgetBody(state: PlansManagementUi, plans: PlansUi, actions: PlansManagementActions) {
    val settings = state.preferences.widget
    val tokens = LocalKitTokens.current
    val gutter = Modifier.padding(horizontal = tokens.gutter.dp)
    val hold =
        if (state.busy) KitEnabled.Disabled(stringResource(Res.string.e7AppearanceSaving))
        else KitEnabled.Enabled
    Spacer(Modifier.height(Space.S2.dp))
    KitText(stringResource(Res.string.usageWidgetIntro), gutter)
    Spacer(Modifier.height(tokens.sectionGap.dp))
    if (plans.cards.isEmpty()) {
        KitText(
            stringResource(Res.string.usageEmptyTitle),
            gutter,
            role = TypeRole.Secondary,
            tone = TextTone.Secondary,
        )
    } else {
        KitRowGroup(
            label = stringResource(Res.string.usageWidgetSettingsShow),
            leadingTiles = true,
        ) {
            for (card in plans.cards) {
                val connection = card.readFrom ?: card.fixConnection
                KitLeadingSwitchRow(
                    title = plansWords().cardTitle(card.name, card.connection),
                    leading = KitRowLeading.Logo(card.providerId, card.name),
                    supporting =
                        listOfNotNull(
                                card.plan
                                    ?.trim()
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.replaceFirstChar { it.titlecase() },
                                connection
                                    ?.takeIf {
                                        plans.cards
                                            .mapNotNull { it.readFrom ?: it.fixConnection }
                                            .map { it.id }
                                            .distinct()
                                            .size > 1
                                    }
                                    ?.let {
                                        stringResource(
                                            Res.string.usageOnConnection,
                                            plansWords().connection(it),
                                        )
                                    },
                            )
                            .joinToString(" · ")
                            .ifBlank { null },
                    supportingMaxLines = 4, // F: lib/ui/screens/usage_widget_screen.dart:110
                    checked = card.key !in settings.hiddenKeys,
                    onCheckedChange = { actions.setWidgetHidden(card.key, !it) },
                    enabled = hold,
                    modifier = Modifier.testTag("usage-widget-plan-${card.key}"),
                )
            }
        }
    }
    Spacer(Modifier.height(tokens.sectionGap.dp))
    KitSectionLabel(
        stringResource(Res.string.usageWidgetSettingsWindows),
        Modifier.clearAndSetSemantics {},
    )
    Box(gutter) {
        KitSegmented(
            segments =
                listOf(
                        UsageWidgetWindows.Both,
                        UsageWidgetWindows.FiveHour,
                        UsageWidgetWindows.Weekly,
                    )
                    .map { window ->
                        KitSegment(
                            window,
                            stringResource(
                                when (window) {
                                    UsageWidgetWindows.Both -> Res.string.usageWidgetWindowBoth
                                    UsageWidgetWindows.FiveHour ->
                                        Res.string.usageWidgetWindowFiveHour
                                    UsageWidgetWindows.Weekly -> Res.string.usageWidgetWindowWeekly
                                }
                            ),
                        )
                    },
            selected = settings.windows,
            semanticsLabel = stringResource(Res.string.usageWidgetSettingsWindows),
            onChange = actions::setWidgetWindows,
            enabled = hold,
            modifier = Modifier.testTag("usage-widget-windows"),
        )
    }
    Spacer(Modifier.height(tokens.sectionGap.dp))
    KitRowGroup(leadingTiles = true) {
        KitLeadingSwitchRow(
            title = stringResource(Res.string.usageWidgetSettingsSetup),
            leading = KitRowLeading.Glyph(KitIcons.login),
            supporting = stringResource(Res.string.usageWidgetSettingsSetupHelp),
            supportingMaxLines = 4, // F: lib/ui/screens/usage_widget_screen.dart:164
            checked = settings.showSetupRows,
            onCheckedChange = actions::setWidgetSetup,
            enabled = hold,
            modifier = Modifier.testTag("usage-widget-setup"),
        )
    }
    if (state.failure != null) {
        Spacer(Modifier.height(Space.S3.dp))
        Box(gutter) {
            KitErrorNotice(
                stringResource(Res.string.usageSaveFailed),
                failure = state.failure,
                detailsLabel = stringResource(Res.string.kitDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
            )
        }
    }
    Spacer(Modifier.height(Space.S6.dp))
}
