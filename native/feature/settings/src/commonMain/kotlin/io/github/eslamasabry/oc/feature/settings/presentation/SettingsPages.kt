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

    public data class ByServer(val section: SettingsOutside) : SettingsPage

    public data object Appearance : SettingsPage

    public data object Voice : SettingsPage

    public data object Notifications : SettingsPage

    public data object Updates : SettingsPage

    public data object About : SettingsPage

    public data object Notices : SettingsPage

    /** About › Open source › All licences: every library in this build with its licence, then the licence texts. */
    public data object Licences : SettingsPage

    public data object PhraseChips : SettingsPage
    public data object WhatsNew : SettingsPage
    public data class VoiceLicense(val id: String) : SettingsPage

    public data object Privacy : SettingsPage

    public data object PrivacyPolicy : SettingsPage

    /** Settings › This app › Privacy › What we collect (plan 18 §6): one page, no new level. */
    public data object WhatWeCollect : SettingsPage

    /** Settings › This app › Language: one check-mark choice per language, each in its own name. */
    public data object Language : SettingsPage

    /**
     * Settings › Help › Report a problem (R1): what happened, what is included, then Send report (or Copy text in a
     * build that cannot send), and the report code.
     */
    public data object ReportProblem : SettingsPage

    /** Local files kept for the deferred assistant, with a measured Delete action. */
    public data object Storage : SettingsPage

    /**
     * Settings › This app › Notifications › Quiet hours start ([start]) or end: one check-mark
     * choice per time.
     */
    public data class QuietTime(val start: Boolean) : SettingsPage

    /**
     * Settings › Help › Guide (Flutter guide_screen.dart): the three steps to a first conversation,
     * this phone's own path, the demo and the advanced notes.
     */
    public data object Guide : SettingsPage

    /** About › Tips and shortcuts › Keyboard shortcuts (Flutter shortcuts.dart `showShortcutsHelp`). */
    public data object KeyboardShortcuts : SettingsPage
}

/**
 * Places a Settings row opens that other features own (servers, providers, tools, the language
 * sheet, …). The host maps each to its route or sheet; this module never builds them.
 */
public enum class SettingsOutside {
    Servers,
    ThisPhone,
    /** This phone's App storage page (what the app keeps, with sizes and the clear actions): the real Storage. */
    AppStorage,
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
    // BUILD-DIAGNOSTICS-ATTENTION: Report a problem's public GitHub form.
    ReportOnGitHub,
    Updates,
    KeptPrompts,
    ReadState,
    // s26: the AI Team page (Flutter Settings › Agents › AI Team) and FactsBook on this phone (About › Developer).
    AiTeam,
    MemoryOnPhone,
    // s28: the Guide's doors (F: guide_screen.dart): Add server itself, phone setup, the offline demo.
    AddServer,
    PhoneSetup,
    Demo,
    // S6: the Linux manager's own pages, reached from Settings search.
    LinuxProcesses,
    LinuxPorts,
    LinuxStorage,
    LinuxPackages,
}

/** Opens a place another feature owns. */
public fun interface SettingsOpeners {
    public fun open(place: SettingsOutside)
}
