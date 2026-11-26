package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.analytics.SendingState
import io.github.eslamasabry.oc.feature.settings.presentation.CollectGroup
import io.github.eslamasabry.oc.feature.settings.presentation.CollectItem
import io.github.eslamasabry.oc.feature.settings.presentation.HealthContract
import io.github.eslamasabry.oc.feature.settings.presentation.PrivacyUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › Privacy and data, after the local data rows (F: release/2.1.5
 * personal_settings_screens.dart:278-330): one "Crash reports" group with Offer crash reports, then
 * the two switches of the health contract (Usage and app health, Crash reports), both off until
 * turned on; then What we collect and the privacy policy.
 */
@Composable
internal fun ColumnScope.PrivacyBody(privacy: PrivacyUi?, actions: SettingsActions) {
    val offer = privacy?.offerCrashReports
    val usage = privacy?.usage
    val send = privacy?.crashReports
    if (offer != null || usage != null || send != null) {
        KitRowGroup(label = stringResource(Res.string.crashOfferSection), leadingTiles = true) {
            offer?.let { on ->
                KitSwitchRow(
                    title = stringResource(Res.string.privacyCrashAskTitle),
                    checked = on,
                    leading = KitRowLeading.Glyph(KitIcons.bug),
                    supporting = stringResource(Res.string.crashOfferSettingDetail),
                    supportingMaxLines = Int.MAX_VALUE,
                    onCheckedChange = actions::onOfferCrashReports,
                )
            }
            usage?.let { on ->
                KitSwitchRow(
                    title = stringResource(Res.string.telemetryUsageTitle),
                    checked = on,
                    modifier = Modifier.testTag("privacy-send-usage"),
                    leading = KitRowLeading.Glyph(KitIcons.shield),
                    supporting = stringResource(Res.string.nativeTelemetryUsageDetail),
                    supportingMaxLines = Int.MAX_VALUE,
                    onCheckedChange = actions::onUsage,
                )
            }
            send?.let { on ->
                KitSwitchRow(
                    title = stringResource(Res.string.privacyCrashSendTitle),
                    checked = on,
                    modifier = Modifier.testTag("privacy-crash-reports"),
                    leading = KitRowLeading.Glyph(KitIcons.bug),
                    supporting = stringResource(Res.string.telemetryCrashesDetail),
                    supportingMaxLines = Int.MAX_VALUE,
                    onCheckedChange = actions::onCrashReports,
                )
            }
        }
    }
    KitRowGroup(leadingTiles = true) {
        if (privacy != null) {
            KitRow(
                title = stringResource(Res.string.collectTitle),
                modifier = Modifier.testTag("privacy-what-we-collect"),
                leading = KitRowLeading.Glyph(KitIcons.info),
                supporting = stringResource(Res.string.collectRowSummary),
                // Whole at 360 dp (look of build 10086: it ended in "…" at text 1.0): it wraps as far as it needs.
                supportingMaxLines = Int.MAX_VALUE,
                trailing = KitRowTrailing.Chevron,
                onClick = { actions.onOpen(SettingsPage.WhatWeCollect) },
            )
        }
        KitRow(
            title = stringResource(Res.string.privacyPolicyTitle),
            leading = KitRowLeading.Glyph(KitIcons.privacy),
            supporting = stringResource(Res.string.privacyPagesSummary),
            supportingMaxLines = 2, // F: lib/ui/screens/settings/personal_settings_screens.dart:260
            trailing = KitRowTrailing.Chevron,
            onClick = { actions.onOutside(SettingsOutside.PrivacyPolicy) },
        )
    }
}

/**
 * What we collect, from the 2.1.5 contract ([HealthContract], docs/telemetry/health-and-diagnostics.md):
 * what this build sends now, each switch with what it sends, the problem report, and what is never sent.
 */
@Composable
internal fun ColumnScope.WhatWeCollectBody(privacy: PrivacyUi, actions: SettingsActions) {
    val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
    // A build without the telemetry vendor sends nothing: say so (with it, the switches below really send).
    if (privacy.collect?.sending != SendingState.Sending && !privacy.vendorSends) {
        KitNotice(
            stringResource(Res.string.collectUsageNotSentYet),
            gutter.testTag("collect-not-sent"),
            liveRegion = false,
        )
    }
    KitGroupNote(stringResource(Res.string.collectRules))
    CollectSection(
        label = stringResource(Res.string.collectUsageLabel),
        items = HealthContract.items.getValue(CollectGroup.Usage),
        modifier = Modifier.testTag("collect-usage"),
        toggle =
            privacy.usage?.let { on ->
                {
                    KitSwitchRow(
                        title = stringResource(Res.string.telemetryUsageTitle),
                        checked = on,
                        onCheckedChange = actions::onUsage,
                    )
                }
            },
    )
    CollectSection(
        label = stringResource(Res.string.collectCrashesLabel),
        items = HealthContract.items.getValue(CollectGroup.Crashes),
        modifier = Modifier.testTag("collect-crashes"),
        toggle =
            privacy.crashReports?.let { on ->
                {
                    KitSwitchRow(
                        title = stringResource(Res.string.privacyCrashSendTitle),
                        checked = on,
                        onCheckedChange = actions::onCrashReports,
                    )
                }
            },
    )
    CollectSection(
        label = stringResource(Res.string.collectOwnLabel),
        items = HealthContract.items.getValue(CollectGroup.OwnServer),
        modifier = Modifier.testTag("collect-own-server"),
        closing = stringResource(Res.string.collectOwnTogether),
    )
    CollectSection(
        label = stringResource(Res.string.collectReportLabel),
        items = HealthContract.items.getValue(CollectGroup.Report),
    )
    CollectSection(
        label = stringResource(Res.string.collectNeverLabel),
        items = emptyList(),
        closing = stringResource(Res.string.collectNever),
    )
}

@Composable
private fun CollectSection(
    label: String,
    items: List<CollectItem>,
    modifier: Modifier = Modifier,
    toggle: (@Composable () -> Unit)? = null,
    closing: String? = null,
) {
    val gutter = LocalKitTokens.current.gutter
    Column(modifier.fillMaxWidth()) {
        if (toggle != null) {
            KitRowGroup(label = label, section = true) { toggle() }
            Spacer(Modifier.height(Space.S3.dp))
        } else {
            KitSectionLabel(label, gapBefore = LocalKitTokens.current.sectionGap.dp)
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = gutter.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            items.forEach { CollectDisclosure(it) }
            closing?.let { KitText(it, role = TypeRole.Body, tone = TextTone.Secondary) }
        }
    }
}

@Composable
private fun CollectDisclosure(item: CollectItem) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
        KitText(stringResource(collectLead(item)), role = TypeRole.Label, tone = TextTone.Primary)
        KitText(stringResource(collectWords(item)), role = TypeRole.Body, tone = TextTone.Secondary)
    }
}

private val collectLeads: Map<CollectItem, StringResource> =
    mapOf(
        CollectItem.Connect to Res.string.collectConnectLead,
        CollectItem.Drop to Res.string.collectDropLead,
        CollectItem.Send to Res.string.collectSendLead,
        CollectItem.Turns to Res.string.collectTurnsLead,
        CollectItem.Approvals to Res.string.collectApprovalsLead,
        CollectItem.Stall to Res.string.collectStallLead,
        CollectItem.Queue to Res.string.collectQueueLead,
        CollectItem.PhoneSetup to Res.string.collectPhoneSetupLead,
        CollectItem.Agents to Res.string.collectAgentsLead,
        CollectItem.Background to Res.string.collectBackgroundLead,
        CollectItem.Updates to Res.string.collectUpdatesLead,
        CollectItem.Pages to Res.string.collectPagesLead,
        CollectItem.Journey to Res.string.collectJourneyLead,
        CollectItem.Starters to Res.string.collectStartersLead,
        CollectItem.ShareIn to Res.string.collectShareInLead,
        CollectItem.StarAsk to Res.string.collectStarAskLead,
        CollectItem.Start to Res.string.collectStartLead,
        CollectItem.Smooth to Res.string.collectSmoothLead,
        CollectItem.Memory to Res.string.collectMemoryLead,
        CollectItem.Device to Res.string.collectDeviceLead,
        CollectItem.CrashKind to Res.string.collectCrashKindLead,
        CollectItem.Exit to Res.string.collectExitLead,
        CollectItem.Steps to Res.string.collectStepsLead,
        CollectItem.CrashKeys to Res.string.collectCrashKeysLead,
        CollectItem.Report to Res.string.collectReportLead,
        CollectItem.OwnBuild to Res.string.collectOwnBuildLead,
        CollectItem.OwnPhone to Res.string.collectOwnPhoneLead,
        CollectItem.OwnLook to Res.string.collectOwnLookLead,
        CollectItem.OwnUse to Res.string.collectOwnUseLead,
        CollectItem.OwnInstall to Res.string.collectOwnInstallLead,
    )

internal fun collectLead(item: CollectItem): StringResource = collectLeads.getValue(item)

internal fun collectWords(item: CollectItem): StringResource =
    when (item) {
        CollectItem.Connect -> Res.string.collectConnect
        CollectItem.Drop -> Res.string.collectDrop
        CollectItem.Send -> Res.string.collectSend
        CollectItem.Turns -> Res.string.collectTurns
        CollectItem.Approvals -> Res.string.collectApprovals
        CollectItem.Stall -> Res.string.collectStall
        CollectItem.Queue -> Res.string.collectQueue
        CollectItem.PhoneSetup -> Res.string.collectPhoneSetup
        CollectItem.Agents -> Res.string.collectAgents
        CollectItem.Background -> Res.string.collectBackground
        CollectItem.Updates -> Res.string.collectUpdates
        CollectItem.Pages -> Res.string.collectPages
        CollectItem.Journey -> Res.string.collectJourney
        CollectItem.Starters -> Res.string.collectStarters
        CollectItem.ShareIn -> Res.string.collectShareIn
        CollectItem.StarAsk -> Res.string.collectStarAsk
        CollectItem.Start -> Res.string.collectStart
        CollectItem.Smooth -> Res.string.collectSmooth
        CollectItem.Memory -> Res.string.collectMemory
        CollectItem.Device -> Res.string.collectDevice
        CollectItem.CrashKind -> Res.string.collectCrashKind
        CollectItem.Exit -> Res.string.collectExit
        CollectItem.Steps -> Res.string.collectSteps
        CollectItem.CrashKeys -> Res.string.collectCrashKeys
        CollectItem.Report -> Res.string.collectReport
        CollectItem.OwnBuild -> Res.string.collectOwnBuild
        CollectItem.OwnPhone -> Res.string.collectOwnPhone
        CollectItem.OwnLook -> Res.string.collectOwnLook
        CollectItem.OwnUse -> Res.string.collectOwnUse
        CollectItem.OwnInstall -> Res.string.collectOwnInstall
    }
