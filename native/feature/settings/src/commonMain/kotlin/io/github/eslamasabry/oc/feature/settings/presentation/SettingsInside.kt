package io.github.eslamasabry.oc.feature.settings.presentation

/**
 * S6: pages and rows INSIDE the hub's rows that Search must reach (F: search_index/inside_pages.dart), so a person who
 * types "ports" or "budgets" lands on the page instead of "Nothing matches". Each one opens a real page and is offered
 * only when the row that holds it is (the same platform flags and openable places). [group] is the hub group it is
 * listed under in results.
 */
public enum class SettingsInside(
    public val tag: String,
    public val group: SettingsGroupId,
) {
    LinuxProcesses("inside-linux-running", SettingsGroupId.Server),
    LinuxPorts("inside-linux-ports", SettingsGroupId.Server),
    LinuxStorage("inside-linux-storage", SettingsGroupId.Server),
    LinuxPackages("inside-linux-packages", SettingsGroupId.Server),
    UsageSpent("inside-usage-spent", SettingsGroupId.Agent),
    UsageBudgets("inside-usage-budgets", SettingsGroupId.Agent),
    UsageRemaining("inside-usage-remaining", SettingsGroupId.Agent),
    UsageAlerts("inside-usage-alerts", SettingsGroupId.Agent),
    NotificationsQuiet("inside-notifications-quiet", SettingsGroupId.ThisApp),
    KeepRunningBattery("inside-keep-running-battery", SettingsGroupId.ThisApp),
    AppearanceMode("inside-appearance-mode", SettingsGroupId.ThisApp),
    AppearanceTheme("inside-appearance-theme", SettingsGroupId.ThisApp),
    VoiceNotices("inside-voice-notices", SettingsGroupId.Conversations),
    // Privacy and data: the rows at the bottom of that page, found from the hub (small-faults 301).
    PrivacyWhatWeCollect("inside-privacy-collect", SettingsGroupId.ThisApp),
    PrivacyPolicy("inside-privacy-policy", SettingsGroupId.ThisApp),
    PrivacyUsage("inside-privacy-usage", SettingsGroupId.ThisApp),
    PrivacyCrashSend("inside-privacy-crash-send", SettingsGroupId.ThisApp),
    PrivacyCrashAsk("inside-privacy-crash-ask", SettingsGroupId.ThisApp),
    AddServer("settings-add-server", SettingsGroupId.Server),
    Demo("settings-try-demo", SettingsGroupId.Help);

    /** Where it leads. */
    public fun door(): SettingsDoor =
        when (this) {
            LinuxProcesses -> SettingsDoor.Outside(SettingsOutside.LinuxProcesses)
            LinuxPorts -> SettingsDoor.Outside(SettingsOutside.LinuxPorts)
            LinuxStorage -> SettingsDoor.Outside(SettingsOutside.LinuxStorage)
            LinuxPackages -> SettingsDoor.Outside(SettingsOutside.LinuxPackages)
            UsageSpent,
            UsageBudgets,
            UsageRemaining -> SettingsDoor.Outside(SettingsOutside.Usage)
            UsageAlerts -> SettingsDoor.Outside(SettingsOutside.UsageAlerts)
            NotificationsQuiet -> SettingsDoor.Page(SettingsPage.Notifications)
            KeepRunningBattery -> SettingsDoor.Outside(SettingsOutside.KeepRunning)
            AppearanceMode,
            AppearanceTheme -> SettingsDoor.Page(SettingsPage.Appearance)
            VoiceNotices -> SettingsDoor.Outside(SettingsOutside.VoiceNotices)
            PrivacyWhatWeCollect -> SettingsDoor.Page(SettingsPage.WhatWeCollect)
            PrivacyPolicy -> SettingsDoor.Outside(SettingsOutside.PrivacyPolicy)
            PrivacyUsage,
            PrivacyCrashSend,
            PrivacyCrashAsk -> SettingsDoor.Page(SettingsPage.Privacy)
            AddServer -> SettingsDoor.Outside(SettingsOutside.AddServer)
            Demo -> SettingsDoor.Outside(SettingsOutside.Demo)
        }

    /** Whether the row that holds it exists on this phone, with this host. */
    public fun offered(f: SettingsFacts): Boolean {
        val p = f.platform
        val reachable =
            when (val d = door()) {
                is SettingsDoor.Outside -> f.canOpen(d.place)
                else -> true
            }
        val held =
            when (this) {
                LinuxProcesses,
                LinuxPorts,
                LinuxStorage,
                LinuxPackages -> p.builtinLinux
                NotificationsQuiet -> p.notifications
                KeepRunningBattery -> p.backgroundService
                VoiceNotices -> p.voice || p.readAloud
                else -> true
            }
        return reachable && held
    }

    /**
     * The Privacy and data rows that exist only when the page has them: What we collect and the two health switches
     * need the build's health contract ([PrivacyUi] present), the crash question needs the build's crash offer. The
     * privacy policy is always on the page.
     */
    internal fun onPrivacyPage(privacy: PrivacyUi?): Boolean =
        when (this) {
            PrivacyWhatWeCollect -> privacy != null
            PrivacyUsage -> privacy?.usage != null
            PrivacyCrashSend -> privacy?.crashReports != null
            PrivacyCrashAsk -> privacy?.offerCrashReports != null
            else -> true
        }

    public companion object {
        public fun searchable(f: SettingsFacts, privacy: PrivacyUi? = null): List<SettingsInside> =
            entries.filter { it.offered(f) && it.onPrivacyPage(privacy) }

    }
}
