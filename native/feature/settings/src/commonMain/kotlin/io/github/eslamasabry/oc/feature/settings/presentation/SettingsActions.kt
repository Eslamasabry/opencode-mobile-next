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

    public fun onShowHiddenPhrases() {}

    /** About › Developer: Memory (preview) on or off. */
    public fun onMemoryPreview(on: Boolean) {}

    public fun onAskDiscard(kind: LocalDataKind) {}

    public fun onConfirmDiscard() {}

    public fun onCancelDiscard() {}

    public fun onSavedServer(
        profile: io.github.eslamasabry.oc.core.common.ids.ProfileId,
        section: SettingsOutside?,
    ) {}


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

    /** Appearance › Glass edges (under Glass): liquid or frosted. */
    public fun onGlassEdges(edges: io.github.eslamasabry.oc.ui.design.effects.GlassEdges) {}

    /** Appearance › Reset to the style's defaults: Corners and Spacing back to the style's own. */
    public fun onResetFineTune() {}

    /** Appearance › Accent: a swatch id, or null for Match theme (B-KIT-049). */
    public fun onAccent(id: String?) {}

    /** Appearance › Background › Look: a look's id turns the background on with it; null is Off. */
    public fun onLiveLook(id: String?) {}

    /** Appearance › Background › How strong? */
    public fun onLiveStrength(strength: LiveStrength) {}

    public fun onReadAloud(on: Boolean)

    public fun onNotification(change: NotificationChange)

    public fun onSendTestNotification()

    public fun onCheckForUpdates()

    public fun onUpdateChecks(on: Boolean)

    public fun onUpdateAction()

    public fun onDetailedUsage(on: Boolean)

    /** The Privacy switch "Send crash reports to the developer". */
    public fun onCrashReports(on: Boolean) {}

    /** Privacy › Usage and app health (Flutter 2.1.5): the stored `usage` answer. */
    public fun onUsage(on: Boolean) {}

    /** The Privacy switch "Offer crash reports" (Flutter 2.1.5 E02-S91): only whether a crash is offered. */
    public fun onOfferCrashReports(on: Boolean) {}

    public fun onNoteSeen()

    /** Appearance › Text size: applies at the app root at once (B-KIT-059). */
    public fun onTextSize(size: AppTextSize) {}

    /** Language: one tap chooses; the page stays, now in the chosen language (B-L10N-001). */
    public fun onLanguage(language: AppLanguage) {}

    /** Report a problem: the words stay in memory and are redacted in the report. */
    public fun onReportDescription(text: String) {}

    /** Report a problem › What's included: opens ([open]) or closes the sheet listing exactly what is sent. */
    public fun onShowIncluded(open: Boolean) {}

    /** Report a problem › Send report: sends exactly the report "What's included" lists, once. */
    public fun onSendProblemReport() {}

    /** Report a problem › Post publicly on GitHub: asks first, since an issue is public. */
    public fun onAskPostPublicly() {}

    /** The public-post question's answer: [post] opens the GitHub form (nothing is filed until the person submits). */
    public fun onPostPublicly(post: Boolean) {}

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

    public fun onAskDeleteLocalModel() {}

    public fun onDeleteLocalModel() {}

    public fun onCancelDeleteLocalModel() {}

    public fun onRefreshLocalModels() {}

    public fun onVoiceLicense(id: String) {}

    public fun onRetryLicense() {}

    public fun onCopyLicense() {}

    public fun onLicenseWebsite() {}
}
