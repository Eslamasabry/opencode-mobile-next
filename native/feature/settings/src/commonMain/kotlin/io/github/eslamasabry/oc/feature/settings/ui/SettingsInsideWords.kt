package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsInside
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** S6: the title and acceptance words of a page inside a row, reusing Flutter's own keys. */
@Composable
internal fun insideTitle(inside: SettingsInside): String =
    when (inside) {
        SettingsInside.LinuxProcesses -> stringResource(Res.string.linuxMgrProcTitle)
        SettingsInside.LinuxPorts -> stringResource(Res.string.linuxMgrPortsTitle)
        SettingsInside.LinuxStorage -> stringResource(Res.string.linuxMgrStorageTitle)
        SettingsInside.LinuxPackages -> stringResource(Res.string.linuxMgrPkgTitle)
        SettingsInside.UsageSpent -> stringResource(Res.string.usageSectionSpent)
        SettingsInside.UsageBudgets -> stringResource(Res.string.usageBudgetTitle)
        SettingsInside.UsageRemaining -> stringResource(Res.string.usageTabPlans)
        SettingsInside.UsageAlerts -> stringResource(Res.string.usageAlertsTitle)
        SettingsInside.NotificationsQuiet -> stringResource(Res.string.monitorQuiet)
        SettingsInside.KeepRunningBattery -> stringResource(Res.string.r40RelationsKeepRunning)
        SettingsInside.AppearanceMode -> stringResource(Res.string.e7SettingsUi69)
        SettingsInside.AppearanceTheme -> stringResource(Res.string.e7SettingsUi70)
        SettingsInside.VoiceNotices -> stringResource(Res.string.voiceNoticesTitle)
        SettingsInside.AddServer -> stringResource(Res.string.e7SetupAddServer)
        SettingsInside.Demo -> stringResource(Res.string.settingsTryDemo)
    }

@Composable
internal fun insideAliases(inside: SettingsInside): String =
    when (inside) {
        SettingsInside.LinuxProcesses -> stringResource(Res.string.nativeSearchAliasesRunning)
        SettingsInside.LinuxPorts -> stringResource(Res.string.nativeSearchAliasesLinuxPorts)
        SettingsInside.LinuxStorage -> stringResource(Res.string.nativeSearchAliasesPhoneStorage)
        SettingsInside.LinuxPackages -> stringResource(Res.string.nativeSearchAliasesLinuxPackages)
        SettingsInside.UsageSpent -> stringResource(Res.string.discoverSpentAliases)
        SettingsInside.UsageBudgets -> stringResource(Res.string.discoverBudgetAliases)
        SettingsInside.UsageRemaining -> stringResource(Res.string.discoverRemainingAliases)
        SettingsInside.UsageAlerts -> stringResource(Res.string.discoverQuotaMonitorAliases)
        SettingsInside.NotificationsQuiet -> stringResource(Res.string.discoverNotifyQuietAliases)
        SettingsInside.KeepRunningBattery -> stringResource(Res.string.nativeSearchAliasesKeepRunning)
        SettingsInside.AppearanceMode -> stringResource(Res.string.discoverAppearanceModeAliases)
        SettingsInside.AppearanceTheme -> stringResource(Res.string.discoverThemeAliases)
        SettingsInside.VoiceNotices -> stringResource(Res.string.settingsHubSearchVoiceAliases)
        SettingsInside.AddServer -> stringResource(Res.string.discoverConnectionHelpAliases)
        SettingsInside.Demo -> stringResource(Res.string.nativeSearchAliasesDemo)
    }

internal fun insideGlyph(inside: SettingsInside) =
    when (inside) {
        SettingsInside.LinuxProcesses -> KitIcons.activity
        SettingsInside.LinuxPorts -> KitIcons.network
        SettingsInside.LinuxStorage -> KitIcons.storage
        SettingsInside.LinuxPackages -> KitIcons.packageBox
        SettingsInside.UsageSpent,
        SettingsInside.UsageBudgets,
        SettingsInside.UsageRemaining,
        SettingsInside.UsageAlerts -> KitIcons.usage
        SettingsInside.NotificationsQuiet -> KitIcons.notificationImportant
        SettingsInside.KeepRunningBattery -> KitIcons.batteryCharging
        SettingsInside.AppearanceMode,
        SettingsInside.AppearanceTheme -> KitIcons.appearance
        SettingsInside.VoiceNotices -> KitIcons.mic
        SettingsInside.AddServer -> KitIcons.add
        SettingsInside.Demo -> KitIcons.play
    }
