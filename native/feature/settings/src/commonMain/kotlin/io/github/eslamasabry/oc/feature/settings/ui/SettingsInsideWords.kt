package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsInside
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** S6: the title and acceptance words of a page inside a row, reusing Flutter's own keys. */
private fun insideKeys(inside: SettingsInside): Pair<StringResource, List<StringResource>> =
    when (inside) {
        SettingsInside.LinuxProcesses ->
            Res.string.linuxMgrProcTitle to listOf(Res.string.nativeSearchAliasesRunning)
        SettingsInside.LinuxPorts ->
            Res.string.linuxMgrPortsTitle to listOf(Res.string.nativeSearchAliasesLinuxPorts)
        SettingsInside.LinuxStorage ->
            Res.string.linuxMgrStorageTitle to listOf(Res.string.nativeSearchAliasesPhoneStorage)
        SettingsInside.LinuxPackages ->
            Res.string.linuxMgrPkgTitle to listOf(Res.string.nativeSearchAliasesLinuxPackages)
        SettingsInside.UsageSpent ->
            Res.string.usageSectionSpent to listOf(Res.string.discoverSpentAliases)
        SettingsInside.UsageBudgets ->
            Res.string.usageBudgetTitle to listOf(Res.string.discoverBudgetAliases)
        SettingsInside.UsageRemaining ->
            Res.string.usageTabPlans to listOf(Res.string.discoverRemainingAliases)
        SettingsInside.UsageAlerts ->
            Res.string.usageAlertsTitle to listOf(Res.string.discoverQuotaMonitorAliases)
        SettingsInside.NotificationsQuiet ->
            Res.string.monitorQuiet to listOf(Res.string.discoverNotifyQuietAliases)
        SettingsInside.KeepRunningBattery ->
            Res.string.r40RelationsKeepRunning to listOf(Res.string.nativeSearchAliasesKeepRunning)
        SettingsInside.AppearanceMode ->
            Res.string.e7SettingsUi69 to listOf(Res.string.discoverAppearanceModeAliases)
        SettingsInside.AppearanceTheme ->
            Res.string.e7SettingsUi70 to listOf(Res.string.discoverThemeAliases)
        SettingsInside.VoiceNotices ->
            Res.string.voiceNoticesTitle to listOf(Res.string.settingsHubSearchVoiceAliases)
        SettingsInside.AddServer ->
            Res.string.e7SetupAddServer to listOf(Res.string.discoverConnectionHelpAliases)
        SettingsInside.Demo ->
            Res.string.settingsTryDemo to listOf(Res.string.nativeSearchAliasesDemo)
    }

@Composable internal fun insideTitle(inside: SettingsInside): String = stringResource(insideKeys(inside).first)

@Composable
internal fun insideAliases(inside: SettingsInside): String =
    insideKeys(inside).second.map { stringResource(it) }.joinToString(" ")

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
