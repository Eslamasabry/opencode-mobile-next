package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceGroup
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceQuestion
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Corners
import io.github.eslamasabry.oc.ui.design.text.AppTextSize
import io.github.eslamasabry.oc.ui.design.tokens.Density

/** Everything a person can do on the Settings pages. Each call names its target (rule 15). */
public interface SettingsActions {
    public fun onAppearanceQuestion(question: AppearanceQuestion?) {}

    public fun onAppearanceGroup(group: AppearanceGroup) {}

    public fun onAppearanceNext() {}

    public fun onAppearanceSkip() {}

    public fun onAppearanceAgain() {}

    public fun onWhatsNewNext() {}

    public fun onWhatsNewPrevious() {}

    public fun onWhatsNewClose() {}

    public fun onWhatsNewShowMe() {}

    /** Conversations › Phrase suggestions: bring back the active server's hidden phrases. */
    public fun onShowHiddenPhrases() {}

    /** Privacy › Local data: ask before discarding [kind]. */
    public fun onAskDiscard(kind: LocalDataKind) {}

    public fun onConfirmDiscard() {}

    public fun onCancelDiscard() {}

    /** A server-scoped row with several saved servers: open [section] for [profile]. */
    public fun onSavedServer(
        profile: io.github.eslamasabry.oc.core.common.ids.ProfileId,
        section: SettingsOutside?,
    ) {}

    /**
     * The appearance guide by step (0 colours, 1 style, 2 comfort, 3 glow); [done] ends it, step 0
     * and not done starts it again.
     */
    public fun onAppearanceGuide(step: Int, done: Boolean) {}

    /** Help › Tips › Show tips again: every tip and the appearance guide can show again. */
    public fun onShowTipsAgain() {}

    /** What's new: go to step [index]. */
    public fun onNewsStep(index: Int) {}

    /** What's new: Show me for the step on screen. */
    public fun onNewsShow() {}

    /** Appearance › Glass: the frost strength. */
    public fun onFrost(strength: io.github.eslamasabry.oc.ui.design.effects.FrostStrength) {}

    /** Appearance › Corners and Spacing in one write; null puts the style's own back. */
    public fun onShape(corners: Corners?, density: Density?) {}

    /** Appearance › Glow: null turns the activity glow off, a style turns it on with that look. */
    public fun onGlow(style: io.github.eslamasabry.oc.engine.settings.prefs.GlowStyle?) {}

    /** Appearance › Reset: every appearance choice back to its default in one write. */
    public fun onResetAppearance() {}

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
