package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.ColumnScope
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
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
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
                    title = stringResource(Res.string.crashOfferSettingTitle),
                    checked = on,
                    leading = KitRowLeading.Glyph(KitIcons.bug),
                    supporting = stringResource(Res.string.crashOfferSettingDetail),
                    supportingMaxLines = 3,
                    onCheckedChange = actions::onOfferCrashReports,
                )
            }
            usage?.let { on ->
                KitSwitchRow(
                    title = stringResource(Res.string.telemetryUsageTitle),
                    checked = on,
                    modifier = Modifier.testTag("privacy-send-usage"),
                    leading = KitRowLeading.Glyph(KitIcons.shield),
                    supporting = stringResource(Res.string.telemetryUsageDetail),
                    supportingMaxLines = 8,
                    onCheckedChange = actions::onUsage,
                )
            }
            send?.let { on ->
                KitSwitchRow(
                    title = stringResource(Res.string.telemetryCrashesTitle),
                    checked = on,
                    modifier = Modifier.testTag("privacy-crash-reports"),
                    leading = KitRowLeading.Glyph(KitIcons.bug),
                    supporting = stringResource(Res.string.telemetryCrashesDetail),
                    supportingMaxLines = 6,
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
                supportingMaxLines = 2,
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
    KitRowGroup(
        label = stringResource(Res.string.collectUsageLabel),
        modifier = Modifier.testTag("collect-usage"),
    ) {
        privacy.usage?.let { on ->
            KitSwitchRow(
                title = stringResource(Res.string.telemetryUsageTitle),
                checked = on,
                onCheckedChange = actions::onUsage,
            )
        }
        HealthContract.items.getValue(CollectGroup.Usage).forEach { CollectRow(it) }
    }
    KitRowGroup(
        label = stringResource(Res.string.collectCrashesLabel),
        modifier = Modifier.testTag("collect-crashes"),
    ) {
        privacy.crashReports?.let { on ->
            KitSwitchRow(
                title = stringResource(Res.string.telemetryCrashesTitle),
                checked = on,
                onCheckedChange = actions::onCrashReports,
            )
        }
        HealthContract.items.getValue(CollectGroup.Crashes).forEach { CollectRow(it) }
    }
    KitRowGroup(label = stringResource(Res.string.collectReportLabel)) {
        HealthContract.items.getValue(CollectGroup.Report).forEach { CollectRow(it) }
    }
    KitRowGroup(label = stringResource(Res.string.collectNeverLabel)) {
        KitRow(title = stringResource(Res.string.collectNever), titleMaxLines = 6)
    }
    KitGroupNote(stringResource(Res.string.collectRules))
}

@Composable
private fun CollectRow(item: CollectItem) {
    KitRow(title = stringResource(collectWords(item)), titleMaxLines = 4)
}

internal fun collectWords(item: CollectItem): StringResource =
    when (item) {
        CollectItem.Connect -> Res.string.collectConnect
        CollectItem.Drop -> Res.string.collectDrop
        CollectItem.Send -> Res.string.collectSend
        CollectItem.Stall -> Res.string.collectStall
        CollectItem.Queue -> Res.string.collectQueue
        CollectItem.PhoneSetup -> Res.string.collectPhoneSetup
        CollectItem.Agents -> Res.string.collectAgents
        CollectItem.Background -> Res.string.collectBackground
        CollectItem.Updates -> Res.string.collectUpdates
        CollectItem.Pages -> Res.string.collectPages
        CollectItem.Start -> Res.string.collectStart
        CollectItem.Smooth -> Res.string.collectSmooth
        CollectItem.Memory -> Res.string.collectMemory
        CollectItem.Device -> Res.string.collectDevice
        CollectItem.CrashKind -> Res.string.collectCrashKind
        CollectItem.Exit -> Res.string.collectExit
        CollectItem.Steps -> Res.string.collectSteps
        CollectItem.CrashKeys -> Res.string.collectCrashKeys
        CollectItem.Report -> Res.string.collectReport
    }
