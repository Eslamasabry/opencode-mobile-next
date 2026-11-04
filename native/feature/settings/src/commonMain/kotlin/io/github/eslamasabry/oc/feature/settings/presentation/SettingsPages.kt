package io.github.eslamasabry.oc.feature.settings.presentation

/**
 * The five groups of the Settings hub (plan 07 §4.2, target-ia §1.3): hub → page → sheet at most.
 * The order is the order on screen. Each has one word (or two, "This app") in `:ui:words`.
 */
public enum class SettingsGroupId {
    Server,
    Agent,
    Conversations,
    ThisApp,
    Help,
}

/**
 * The pages this module draws. A feature-local stand-in for plan 07's `SettingsRoute` keys
 * (`:ui:routes` has no settings routes yet; the routes owner adds them, then [SettingsPage] maps
 * one to one onto them).
 */
public sealed interface SettingsPage {
    public data object ServerConfiguration : SettingsPage

    public data object ServerCapabilities : SettingsPage

    public data object Hub : SettingsPage

    public data class Group(val id: SettingsGroupId) : SettingsPage

    public data object Appearance : SettingsPage

    public data object Voice : SettingsPage

    public data object Notifications : SettingsPage

    public data object Updates : SettingsPage

    public data object About : SettingsPage

    public data object Notices : SettingsPage

    public data object PhraseChips : SettingsPage
    public data object WhatsNew : SettingsPage

    public data object Privacy : SettingsPage

    public data object PrivacyPolicy : SettingsPage

    /** Settings › This app › Privacy › What we collect (plan 18 §6): one page, no new level. */
    public data object WhatWeCollect : SettingsPage

    /** Settings › This app › Language: one check-mark choice per language, each in its own name. */
    public data object Language : SettingsPage

    /**
     * Settings › Help › Report a problem: the safe diagnostics report, previewed, then copied or
     * shared.
     */
    public data object ReportProblem : SettingsPage

    /**
     * Settings › This app › Notifications › Quiet hours start ([start]) or end: one check-mark
     * choice per time.
     */
    public data class QuietTime(val start: Boolean) : SettingsPage
}

/**
 * Places a Settings row opens that other features own (servers, providers, tools, the language
 * sheet, …). The host maps each to its route or sheet; this module never builds them.
 */
public enum class SettingsOutside {
    Servers,
    ThisPhone,
    ModelAndMode,
    Providers,
    Usage,
    Tools,
    Agents,
    DefaultShell,
    Permissions,
    KeepRunning,
    Language,
    Guide,
    Tips,
    Capabilities,
    ReportProblem,
    WhatsNew,
    Notices,
    PrivacyPolicy,
    StarOnGitHub,
    ShareApp,
    VoiceSetup,
    VoiceNotices,
    AndroidNotificationSettings,
    UsageAlerts,
    QuietHours,
    ReleaseNotes,
    // BUILD-DIAGNOSTICS-ATTENTION: Report a problem's GitHub form and Everything this phone kept
    // (:feature:diagnostics).
    ReportOnGitHub,
    KeptDiagnostics,
}

/** Opens a place another feature owns. */
public fun interface SettingsOpeners {
    public fun open(place: SettingsOutside)
}
