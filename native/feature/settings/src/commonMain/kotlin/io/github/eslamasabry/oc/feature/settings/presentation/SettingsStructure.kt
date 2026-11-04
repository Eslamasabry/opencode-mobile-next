package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Every row of the five groups, in screen order inside its group (plan 07 §4.2). [tag] is the
 * stable test tag and the search anchor (the freeze tip's `settingsGroupRowIds`, plan 07 §10.2).
 */
public enum class SettingsRowId(public val tag: String, public val group: SettingsGroupId) {
    Servers("settings-servers", SettingsGroupId.Server),
    ServerCapabilities("settings-server-capabilities", SettingsGroupId.Server),
    ServerConfiguration("settings-server-configuration", SettingsGroupId.Server),
    ThisPhone("settings-this-phone", SettingsGroupId.Server),
    ModelAndMode("settings-model-mode", SettingsGroupId.Agent),
    Providers("settings-providers", SettingsGroupId.Agent),
    Usage("settings-usage", SettingsGroupId.Agent),
    Tools("settings-tools", SettingsGroupId.Agent),
    Agents("settings-agents", SettingsGroupId.Agent),
    ShowReasoning("settings-show-reasoning", SettingsGroupId.Conversations),
    ShowTimestamps("settings-show-timestamps", SettingsGroupId.Conversations),
    PhraseChips("settings-phrase-chips", SettingsGroupId.Conversations),
    DefaultShell("settings-default-shell", SettingsGroupId.Conversations),
    Voice("settings-voice", SettingsGroupId.Conversations),
    Permissions("settings-permissions", SettingsGroupId.Conversations),
    Notifications("settings-notifications", SettingsGroupId.ThisApp),
    Background("settings-background", SettingsGroupId.ThisApp),
    Appearance("settings-appearance", SettingsGroupId.ThisApp),
    Language("settings-language", SettingsGroupId.ThisApp),
    Privacy("settings-privacy", SettingsGroupId.ThisApp),
    Updates("settings-updates", SettingsGroupId.ThisApp),
    Storage("settings-storage", SettingsGroupId.ThisApp),

    Guide("settings-guide", SettingsGroupId.Help),
    Tips("settings-tips", SettingsGroupId.Help),
    Capabilities("settings-capabilities", SettingsGroupId.Help),
    ReportProblem("settings-report-problem", SettingsGroupId.Help),
    About("settings-about", SettingsGroupId.Help),
}

/** What a row does when tapped (switch rows flip in place, plan 07 I10). */
public sealed interface SettingsDoor {
    public data class Page(val page: SettingsPage) : SettingsDoor

    public data class Outside(val place: SettingsOutside) : SettingsDoor

    public data class Switch(val on: Boolean) : SettingsDoor
}

public data class SettingsRowUi(val id: SettingsRowId, val door: SettingsDoor)

/** The connected server's name for the hub's one value line ("laptop · On this phone"). */
public data class ServerLine(val name: String, val onPhone: Boolean)

/**
 * The facts the structure reads. Gates are [PlatformCapabilities] flags and whether a server is
 * connected — never the backend kind (rule 10).
 */
public data class SettingsFacts(
    val platform: PlatformCapabilities,
    /** Null: no server is set up yet. */
    val server: ServerLine? = null,
    /** Material You needs the platform's tones (API 31+). */
    val dynamicColour: Boolean = false,
    /** The phone's "Remove animations" is on: nothing moves whatever Motion says. */
    val systemMotionOff: Boolean = false,
    /**
     * The host can build and hand over the diagnostics report: Report a problem opens its page
     * here.
     */
    val diagnostics: Boolean = false,
    val configurationInspection: Boolean = false,
    val serverCapabilities: io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities? =
        null,
    val usageStatistics: Boolean = false,
    val localModelStorage: Boolean = false,
    /**
     * The places the host can open; a row leading anywhere else is absent, never dead (02 §3.7).
     * Null = every place (a host with every feature routed).
     */
    val opens: Set<SettingsOutside>? = null,
    /**
     * The app draws the five surface styles: the Style choice is offered only then (a choice must
     * change something).
     */
    val surfaceStyles: Boolean = true,
    /**
     * The conversation shows reasoning and times as these switches say: the two switches exist only
     * then.
     */
    val transcriptDisplay: Boolean = true,
    val phraseChips: Boolean = false,
) {
    public fun canOpen(place: SettingsOutside): Boolean = opens?.contains(place) ?: true
}

/**
 * Which rows exist (plan 07 §4.2, I01–I20). A row the platform or the server cannot serve is
 * absent, never greyed.
 */
public object SettingsStructure {
    public fun rows(
        group: SettingsGroupId,
        facts: SettingsFacts,
        reasoning: Boolean,
        timestamps: Boolean,
    ): ImmutableList<SettingsRowUi> {
        val p = facts.platform
        val connected = facts.server != null
        return SettingsRowId.entries
            .filter { it.group == group }
            .mapNotNull { id ->
                val door: SettingsDoor? =
                    when (id) {
                        SettingsRowId.ServerConfiguration ->
                            SettingsDoor.Page(SettingsPage.ServerConfiguration).takeIf {
                                facts.configurationInspection
                            }
                        SettingsRowId.ServerCapabilities ->
                            SettingsDoor.Page(SettingsPage.ServerCapabilities)
                        SettingsRowId.Servers -> SettingsDoor.Outside(SettingsOutside.Servers)
                        SettingsRowId.ThisPhone ->
                            SettingsDoor.Outside(SettingsOutside.ThisPhone).takeIf {
                                p.builtinLinux
                            }
                        SettingsRowId.ModelAndMode ->
                            SettingsDoor.Outside(SettingsOutside.ModelAndMode).takeIf { connected }
                        SettingsRowId.Providers ->
                            SettingsDoor.Outside(SettingsOutside.Providers).takeIf { connected }
                        SettingsRowId.Usage -> SettingsDoor.Outside(SettingsOutside.Usage)
                        SettingsRowId.Tools ->
                            SettingsDoor.Outside(SettingsOutside.Tools).takeIf { connected }
                        SettingsRowId.Agents ->
                            SettingsDoor.Outside(SettingsOutside.Agents).takeIf { p.builtinLinux }
                        SettingsRowId.ShowReasoning ->
                            SettingsDoor.Switch(reasoning).takeIf { facts.transcriptDisplay }
                        SettingsRowId.ShowTimestamps ->
                            SettingsDoor.Switch(timestamps).takeIf { facts.transcriptDisplay }
                        SettingsRowId.PhraseChips ->
                            SettingsDoor.Page(SettingsPage.PhraseChips).takeIf { facts.phraseChips }
                        SettingsRowId.DefaultShell ->
                            SettingsDoor.Outside(SettingsOutside.DefaultShell).takeIf { connected }
                        SettingsRowId.Voice ->
                            SettingsDoor.Page(SettingsPage.Voice).takeIf { p.voice || p.readAloud }
                        SettingsRowId.Permissions ->
                            SettingsDoor.Outside(SettingsOutside.Permissions)
                        SettingsRowId.Notifications ->
                            SettingsDoor.Page(SettingsPage.Notifications).takeIf { p.notifications }
                        SettingsRowId.Background ->
                            SettingsDoor.Outside(SettingsOutside.KeepRunning).takeIf {
                                p.backgroundService
                            }
                        SettingsRowId.Appearance -> SettingsDoor.Page(SettingsPage.Appearance)
                        SettingsRowId.Language -> SettingsDoor.Page(SettingsPage.Language)
                        SettingsRowId.Privacy -> SettingsDoor.Page(SettingsPage.Privacy)
                        SettingsRowId.Updates ->
                            SettingsDoor.Page(SettingsPage.Updates).takeIf {
                                p.inAppUpdater || p.desktopReleaseCheck
                            }
                        SettingsRowId.Storage ->
                            SettingsDoor.Page(SettingsPage.Storage).takeIf { facts.localModelStorage }
                        SettingsRowId.Guide -> SettingsDoor.Outside(SettingsOutside.Guide)
                        SettingsRowId.Capabilities ->
                            SettingsDoor.Outside(SettingsOutside.Capabilities)
                        SettingsRowId.Tips -> SettingsDoor.Outside(SettingsOutside.Tips)
                        SettingsRowId.ReportProblem ->
                            if (facts.diagnostics) SettingsDoor.Page(SettingsPage.ReportProblem)
                            else SettingsDoor.Outside(SettingsOutside.ReportProblem)
                        SettingsRowId.About -> SettingsDoor.Page(SettingsPage.About)
                    }
                door
                    ?.takeIf { it !is SettingsDoor.Outside || facts.canOpen(it.place) }
                    ?.let { SettingsRowUi(id, it) }
            }
            .toImmutableList()
    }

    /** The groups with at least one row; a group with no rows has no door (I02). */
    public fun groups(facts: SettingsFacts): ImmutableList<SettingsGroupId> =
        SettingsGroupId.entries
            .filter { rows(it, facts, reasoning = false, timestamps = false).isNotEmpty() }
            .toImmutableList()

    /**
     * Every row that exists now, in hub order: the search set (B-NAV-072: rows the phone lacks stay
     * out of search).
     */
    public fun searchable(facts: SettingsFacts): ImmutableList<SettingsRowId> =
        groups(facts)
            .flatMap { g -> rows(g, facts, reasoning = false, timestamps = false).map { it.id } }
            .toImmutableList()
}
