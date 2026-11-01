package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel

/** Everything a person can do on the Settings pages. Each call names its target (rule 15). */
public interface SettingsActions {
    public fun onOpen(page: SettingsPage)

    /** True when Back was used here (search cleared or a page closed); false at the hub with no search. */
    public fun onBack(): Boolean

    public fun onRow(row: SettingsRowId)

    public fun onQuery(text: String)

    /** The screen hands over the words of every row that exists, in the person's language (no copy here, K2). */
    public fun onSearchEntries(entries: List<SettingsSearchEntry>)

    public fun onOutside(place: SettingsOutside)

    public fun onMode(mode: AppearanceMode)

    public fun onPack(pack: PackId)

    public fun onSeeAllPacks()

    public fun onStyle(style: SurfaceStyle)

    public fun onMotion(motion: MotionLevel)

    public fun onReadAloud(on: Boolean)

    public fun onNotification(change: NotificationChange)

    public fun onSendTestNotification()

    public fun onCheckForUpdates()

    public fun onUpdateChecks(on: Boolean)

    public fun onUpdateAction()

    public fun onDetailedUsage(on: Boolean)

    public fun onNoteSeen()
}
