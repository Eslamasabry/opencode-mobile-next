package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.engine.analytics.WhatWeCollect
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Corners
import io.github.eslamasabry.oc.ui.design.text.AppTextSize
import io.github.eslamasabry.oc.ui.design.tokens.Density
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Everything the Settings screens draw. No copy, no Compose (K2). */
public data class SettingsUi(
    /** The open pages, hub first; the last one is on screen. */
    val stack: ImmutableList<SettingsPage> = persistentListOf(SettingsPage.Hub),
    val hub: HubUi = HubUi(),
    val group: GroupUi? = null,
    val appearance: AppearanceUi = AppearanceUi(),
    val voice: VoiceFacts? = null,
    val notifications: NotificationFacts? = null,
    val updates: UpdateFacts? = null,
    val about: AboutFacts = AboutFacts(),
    val privacy: PrivacyUi? = null,
    /** The language in use (acknowledged), for the Language page and its row. */
    val language: AppLanguage = AppLanguage.System,
    val capabilities: ServerCapabilitiesUi? = null,
    /** Report a problem's preview; null until the page opens. */
    val report: ReportUi? = null,
    val reportDescription: String = "",
    val phraseChips: Boolean = true,
    val focusedRow: SettingsRowId? = null,
    val focusedAnchor: String? = null,
    val crash: CrashUi? = null,
    /** One plain line about the last save that did not stick, if any. */
    val note: SettingsNote? = null,
) {
    val page: SettingsPage
        get() = stack.last()
}

public data class HubUi(
    val groups: ImmutableList<SettingsGroupId> = persistentListOf(),
    val server: ServerLine? = null,
    val query: String = "",
    /** Rows matching [query], best first; empty with a query = nothing found. */
    val results: ImmutableList<SettingsRowId> = persistentListOf(),
    /** Every row that exists now: the screen builds the search entries from these. */
    val searchable: ImmutableList<SettingsRowId> = persistentListOf(),
)

public data class GroupUi(val id: SettingsGroupId, val rows: ImmutableList<SettingsRowUi>)

/**
 * The Appearance page (plan 07 I04, plan 06 K4): mode, colours, style, motion. Every choice applies
 * at once.
 */
public data class AppearanceUi(
    val mode: AppearanceMode = AppearanceMode.Dark,
    val pack: PackId = PackId.Opencode,
    /** The packs listed now: the most used (plus the one in use) until "See all" opens the rest. */
    val packs: ImmutableList<PackId> = persistentListOf(),
    /** How many packs "See all" shows in total; 0 when everything is already listed. */
    val seeAllCount: Int = 0,
    val style: SurfaceStyle = SurfaceStyle.Solid,
    /**
     * The Corners shown as chosen: the person's choice, else the style's own (a choice equal to it
     * does not differ).
     */
    val corners: Corners = Corners.Round,
    val spacing: Density = Density.Comfortable,
    /**
     * Corners or Spacing differ from the style's own: "Reset to <style> defaults" is offered
     * (B-KIT-017).
     */
    val fineTuned: Boolean = false,
    /** The Accent swatch id; null = Match theme (B-KIT-049). */
    val accent: String? = null,
    val motion: MotionLevel = MotionLevel.Full,
    val textSize: AppTextSize = AppTextSize.Normal,
    /** The five styles are listed only where the app draws them ([SettingsFacts.surfaceStyles]). */
    val styles: Boolean = true,
    /** Saver holds Style and Motion (B-KIT-008): shown, not changeable, with the reason. */
    val heldBySaver: Boolean = false,
    val systemMotionOff: Boolean = false,
)

public sealed interface SpeechModel {
    public data object Unsupported : SpeechModel

    /** [size] is a data value ("75 MB") formatted by the engine. */
    public data class NotDownloaded(val size: String) : SpeechModel

    public data class Downloading(val name: String) : SpeechModel

    public data class Ready(val name: String) : SpeechModel
}

public data class VoiceFacts(
    val speechModel: SpeechModel = SpeechModel.Unsupported,
    /** False where read aloud does not exist (plan 07 I11): the row is absent. */
    val readAloudSupported: Boolean = false,
    val readAloudOn: Boolean = false,
)

/**
 * Minutes after midnight, local. The same start and end mean quiet all day (Flutter
 * `SharedNotifyRules`).
 */
public data class QuietHours(val start: Int, val end: Int) {
    val allDay: Boolean
        get() = start == end

    public companion object {
        /**
         * What turning quiet hours on starts with (Flutter `defaultQuietStart` /
         * `defaultQuietEnd`).
         */
        public val Default: QuietHours = QuietHours(22 * 60, 8 * 60)

        /**
         * The times the start and end pages offer: every half hour, plus [current] when it is
         * between them.
         */
        public fun choices(current: Int): List<Int> =
            ((0 until 24 * 60 step 30) + current).distinct().sorted()
    }
}

public enum class BackgroundState {
    Off,
    Running,
    StoppedByAndroid,
}

public data class NotificationFacts(
    /** Android does not let the app notify: the page leads with the fix. */
    val blocked: Boolean = false,
    val finishedWork: Boolean = true,
    val questions: Boolean = true,
    val usageAlertsShown: Boolean = false,
    val quietHours: QuietHours? = null,
    val wifiOnly: Boolean = false,
    val background: BackgroundState? = null,
)

public sealed interface NotificationChange {
    public data class FinishedWork(val on: Boolean) : NotificationChange

    public data class Questions(val on: Boolean) : NotificationChange

    public data class WifiOnly(val on: Boolean) : NotificationChange

    /**
     * Quiet hours for every server (`oc.notify.quietStart` / `quietEnd`, minutes after midnight);
     * null turns them off.
     */
    public data class Quiet(val hours: QuietHours?) : NotificationChange
}

public enum class UpdateFailure {
    Network,
    NoFile,
    Checksum,
    Signer,
    Package,
    Version,
    Storage,
    Installer,
}

public sealed interface UpdateState {
    public data object NeverChecked : UpdateState

    public data object Checking : UpdateState

    /**
     * [checked] is a date already formatted for the person's locale (data, not copy); null = just
     * now.
     */
    public data class UpToDate(val checked: String?) : UpdateState

    public data class Available(val version: String, val sizeMb: String) : UpdateState

    public data class Downloading(val version: String, val percent: Int) : UpdateState

    public data class Downloaded(val version: String) : UpdateState

    public data object Installing : UpdateState

    public data class Failed(val why: UpdateFailure) : UpdateState
}

public data class UpdateFacts(
    val current: String,
    val build: String,
    val checksOn: Boolean = true,
    val state: UpdateState = UpdateState.NeverChecked,
)

public data class AboutFacts(
    val version: String = "",
    val build: String = "",
    val whatsNew: Boolean = true,
    /** The open-source notices page exists in this build. */
    val notices: Boolean = true,
)

public data class PrivacyUi(val collect: WhatWeCollect, val saving: Boolean = false)

public enum class SettingsNote {
    AppearanceNotSaved,
    ChoiceNotSaved,
    ConsentNotSaved,
    TestNotSent,
    UpdateFailed,
    ReportCopied,
    ShareFailed,
}

/** Report a problem's preview (B-SEC-060: the exact safe text that is shared). */
public sealed interface ReportUi {
    public data object Loading : ReportUi

    public data class Ready(val text: String) : ReportUi

    /** The report could not be built: a plain line and Try again, never the reason's text. */
    public data object Failed : ReportUi
}
