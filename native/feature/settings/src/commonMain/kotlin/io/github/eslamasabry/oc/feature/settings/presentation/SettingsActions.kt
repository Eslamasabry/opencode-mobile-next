package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Corners
import io.github.eslamasabry.oc.ui.design.text.AppTextSize
import io.github.eslamasabry.oc.ui.design.tokens.Density

/** Everything a person can do on the Settings pages. Each call names its target (rule 15). */
public interface SettingsActions {
    public fun onOpen(page: SettingsPage)

    /**
     * True when Back was used here (search cleared or a page closed); false at the hub with no
     * search.
     */
    public fun onBack(): Boolean

    public fun onRow(row: SettingsRowId)

    public fun onQuery(text: String)

    public fun onPhraseChips(on: Boolean) {}

    public fun onTarget(row: SettingsRowId) {}

    public fun onAnchor(tag: String) {}

    /**
     * The screen hands over the words of every row that exists, in the person's language (no copy
     * here, K2).
     */
    public fun onSearchEntries(entries: List<SettingsSearchEntry>)

    public fun onOutside(place: SettingsOutside)

    public fun onMode(mode: AppearanceMode)

    public fun onPack(pack: PackId)

    public fun onSeeAllPacks()

    public fun onStyle(style: SurfaceStyle)

    public fun onMotion(motion: MotionLevel)

    /** Appearance › Corners: the person's choice over the style's own (B-KIT-017). */
    public fun onCorners(corners: Corners) {}

    /** Appearance › Spacing: the person's choice over the style's own. */
    public fun onSpacing(spacing: Density) {}

    /** Appearance › Reset to the style's defaults: Corners and Spacing back to the style's own. */
    public fun onResetFineTune() {}

    /** Appearance › Accent: a swatch id, or null for Match theme (B-KIT-049). */
    public fun onAccent(id: String?) {}

    public fun onReadAloud(on: Boolean)

    public fun onNotification(change: NotificationChange)

    public fun onSendTestNotification()

    public fun onCheckForUpdates()

    public fun onUpdateChecks(on: Boolean)

    public fun onUpdateAction()

    public fun onDetailedUsage(on: Boolean)

    public fun onNoteSeen()

    /** Appearance › Text size: applies at the app root at once (B-KIT-059). */
    public fun onTextSize(size: AppTextSize) {}

    /** Language: one tap chooses; the page stays, now in the chosen language (B-L10N-001). */
    public fun onLanguage(language: AppLanguage) {}

    /** Report a problem: description stays in memory and is scrubbed in the preview. */
    public fun onReportDescription(text: String) {}

    public fun onSendReport() {
        onOutside(SettingsOutside.ReportOnGitHub)
    }

    /** Report a problem: copy the previewed report as it is shown. */
    public fun onCopyReport() {}

    public fun onRefreshExitHistory() {}

    public fun onCrashCapture(on: Boolean) {}

    public fun onClearCrashes() {}

    public fun onPreviewCrashes() {}

    public fun onShareCrashes() {}

    /** Report a problem: open Android's share sheet with the previewed report. */
    public fun onShareReport() {}

    /** Report a problem: build the report again after it could not be read. */
    public fun onRetryReport() {}

    /** Notifications › Quiet hours switch: on starts at 22:00–08:00, off clears both times. */
    public fun onQuietHours(on: Boolean) {}

    /**
     * Quiet hours start ([start]) or end page: one tap saves [minutes] and returns to
     * Notifications.
     */
    public fun onQuietTime(start: Boolean, minutes: Int) {}

    /**
     * The screen is on screen again (back from Android's settings): re-read what may have changed
     * outside.
     */
    public fun onResume() {}
}
