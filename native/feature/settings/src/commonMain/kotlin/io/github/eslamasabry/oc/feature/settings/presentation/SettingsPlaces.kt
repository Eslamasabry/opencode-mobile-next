package io.github.eslamasabry.oc.feature.settings.presentation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** A part of a page a search result can mean: the page opens scrolled to it ([tag] is its anchor and test tag). */
public enum class SettingsSection(public val tag: String) {
    NotificationsQuiet("notifications-section-quiet"),
    NotificationsBackground("notifications-section-background"),
    AppearanceTextSize("appearance-section-text-size"),
}

/** Where a search result or a deep link lands. */
public sealed interface SettingsTarget {
    /** The row's group page, arrived at the row (a switch is shown, never flipped by the search). */
    public data class Row(val row: SettingsRowId) : SettingsTarget

    /** One of this module's pages, scrolled to [section]. */
    public data class Page(val page: SettingsPage, val section: SettingsSection? = null) : SettingsTarget

    /** A place another feature owns. */
    public data class Outside(val place: SettingsOutside) : SettingsTarget
}

/**
 * Things inside pages that search finds by their own name (Flutter `inside-*` entries). [tag] is the Flutter search
 * id; [parent] is the row whose page holds it (its words say where the result sits). Each is present only where the
 * server and this device can open it ([SettingsPlaces.available]), never shown and dead.
 */
public enum class SettingsPlaceId(public val tag: String, public val parent: SettingsRowId) {
    /** "Background checks" and staying connected: where they run on this phone (B-NAV-050). */
    BackgroundChecks("inside-notifications-background", SettingsRowId.Notifications),
    QuietHours("inside-notifications-quiet", SettingsRowId.Notifications),
    TextSize("inside-appearance-text-size", SettingsRowId.Appearance),
    Models("settings-models", SettingsRowId.ModelAndMode),
    CodingTools("inside-capabilities-tools", SettingsRowId.Tools),
    RunningOnPhone("inside-phone-running", SettingsRowId.ThisPhone),
    LinuxServices("inside-linux-services", SettingsRowId.ThisPhone),
    AiSetup("inside-server-ai-setup", SettingsRowId.Servers),
}

/** Where a place lands and whether it exists now (rule 10: [SettingsFacts] flags, never the backend kind). */
public object SettingsPlaces {
    public fun target(place: SettingsPlaceId): SettingsTarget =
        when (place) {
            SettingsPlaceId.BackgroundChecks ->
                SettingsTarget.Page(SettingsPage.Notifications, SettingsSection.NotificationsBackground)
            SettingsPlaceId.QuietHours -> SettingsTarget.Page(SettingsPage.Notifications, SettingsSection.NotificationsQuiet)
            SettingsPlaceId.TextSize -> SettingsTarget.Page(SettingsPage.Appearance, SettingsSection.AppearanceTextSize)
            SettingsPlaceId.Models -> SettingsTarget.Outside(SettingsOutside.ModelAndMode)
            SettingsPlaceId.CodingTools -> SettingsTarget.Outside(SettingsOutside.Tools)
            SettingsPlaceId.RunningOnPhone -> SettingsTarget.Outside(SettingsOutside.RunningOnPhone)
            SettingsPlaceId.LinuxServices -> SettingsTarget.Outside(SettingsOutside.LinuxServices)
            SettingsPlaceId.AiSetup -> SettingsTarget.Outside(SettingsOutside.AiSetup)
        }

    public fun available(place: SettingsPlaceId, facts: SettingsFacts): Boolean {
        val p = facts.platform
        val caps = facts.caps
        val connected = facts.server != null
        val gate =
            when (place) {
                // The Notifications page draws its background section only where the phone keeps a service.
                SettingsPlaceId.BackgroundChecks -> p.notifications && p.backgroundService
                SettingsPlaceId.QuietHours -> p.notifications
                SettingsPlaceId.TextSize -> true
                SettingsPlaceId.Models -> connected && caps.serverCatalog
                SettingsPlaceId.CodingTools -> connected && caps.serverCatalog && caps.toolInventory
                SettingsPlaceId.RunningOnPhone, SettingsPlaceId.LinuxServices -> facts.inAppLinux
                SettingsPlaceId.AiSetup -> connected && caps.setupConfigRead
            }
        val target = target(place)
        return gate && (target !is SettingsTarget.Outside || facts.canOpen(target.place))
    }

    /** Every place that exists now, in index order. */
    public fun searchable(facts: SettingsFacts): ImmutableList<SettingsPlaceId> =
        SettingsPlaceId.entries.filter { available(it, facts) }.toImmutableList()

    /**
     * A deep link's tag (a row's or a place's, the same ids search uses) to where it lands, or null when that row or
     * place does not exist here: a link to something absent opens Settings at the hub, never a dead page.
     */
    public fun resolve(tag: String, facts: SettingsFacts): SettingsTarget? {
        SettingsRowId.entries.firstOrNull { it.tag == tag }?.let { row ->
            return SettingsTarget.Row(row).takeIf { row in SettingsStructure.searchable(facts) }
        }
        val place = SettingsPlaceId.entries.firstOrNull { it.tag == tag } ?: return null
        return target(place).takeIf { available(place, facts) }
    }
}
