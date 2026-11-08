package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.engine.analytics.WhatWeCollect
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceGuideRecord
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceQuestion
import io.github.eslamasabry.oc.engine.settings.walkthrough.WhatsNewChange
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.EdgeHold
import io.github.eslamasabry.oc.ui.design.effects.GlassEdges
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
    /** About › Developer; null hides the group. */
    val memoryPreview: MemoryPreviewUi? = null,
    val whatsNew: WhatsNewUi? = null,
    val privacy: PrivacyUi? = null,
    val localData: LocalDataUi? = null,
    val phrases: PhraseSettingsFacts? = null,
    /** The language in use (acknowledged), for the Language page and its row. */
    val language: AppLanguage = AppLanguage.System,
    val capabilities: ServerCapabilitiesUi? = null,
    /** Help › Report a problem (R1); null until the page opens. */
    val report: ReportUi? = null,
    /** B-190: About's "Report code"; null in a build without the vendor. */
    val reportCode: String? = null,
    val reportDescription: String = "",
    val phraseChips: Boolean = true,
    val focusedRow: SettingsRowId? = null,
    val focusedAnchor: String? = null,
    val localModels: LocalModelStorageFacts? = null,
    /** One plain line about the last save that did not stick, if any. */
    val note: SettingsNote? = null,
    val voiceLicenses: Boolean = false,
    val license: LicenseUi? = null,
    /** Help › Guide: which of its doors this build and phone offer. */
    val guide: GuideUi = GuideUi(),
    /**
     * The hub's first group with its rows: a wide window shows it in the detail pane until another is chosen
     * (F: settings_screen.dart:390 `groups.firstOrNull`). Not a page: a phone never draws it.
     */
    val firstGroup: GroupUi? = null,
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
    val savedServers: ImmutableList<SettingsSavedServer> = persistentListOf(),
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
    /** Background › Look: the look shown as chosen, or null when the background is off (Dart `_lookBody`). */
    val liveLook: String? = null,
    /** Background › How strong? */
    val liveStrength: LiveStrength = LiveStrength.Normal,
    /** The six styles are listed only where the app draws them ([SettingsFacts.surfaceStyles]). */
    val styles: Boolean = true,
    /** Saver holds Style and Motion (B-KIT-008): shown, not changeable, with the reason. */
    val heldBySaver: Boolean = false,
    val systemMotionOff: Boolean = false,
    /** Fine-tune under Glass: liquid or frosted floating edges (shown only while Glass is the style). */
    val glassEdges: GlassEdges = GlassEdges.Liquid,
    /** Why the edges are frosted whatever the choice says (the platform, motion), or null. */
    val glassEdgesHold: EdgeHold? = null,
    val guide: AppearanceGuideRecord? = null,
    val question: AppearanceQuestion? = null,
    val questions: List<AppearanceQuestion> = emptyList(),
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

public data class NotificationProfile(
    val id: io.github.eslamasabry.oc.core.common.ids.ProfileId,
    val name: String,
    val enabled: Boolean,
    val notifications: Boolean,
    val supported: Boolean = true,
)

public data class NotificationFacts(
    /** Android does not let the app notify: the page leads with the fix. */
    val blocked: Boolean = false,
    val finishedWork: Boolean = true,
    val questions: Boolean = true,
    val usageAlertsShown: Boolean = false,
    val profiles: ImmutableList<NotificationProfile> = persistentListOf(),
    val checkInMinutes: Int? = null,
    val quietHours: QuietHours? = null,
    val wifiOnly: Boolean = false,
    val background: BackgroundState? = null,
    /** Android channel availability, separate from the saved notification choices. */
    val finishedWorkAllowed: Boolean = true,
    val questionsAllowed: Boolean = true,
) {
    /** A saved switch alone cannot promise that Android will deliver a notification. */
    val enabled: Boolean
        get() =
            !blocked && ((finishedWork && finishedWorkAllowed) || (questions && questionsAllowed))
}

public sealed interface NotificationChange {
    public data class ProfileWatch(
        val profile: io.github.eslamasabry.oc.core.common.ids.ProfileId,
        val on: Boolean,
    ) : NotificationChange

    public data class ProfileNotifications(
        val profile: io.github.eslamasabry.oc.core.common.ids.ProfileId,
        val on: Boolean,
    ) : NotificationChange

    public data class CheckIn(val minutes: Int?) : NotificationChange

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

/** About › Developer: the Memory (preview) switch, and "Use memory on this phone" while it is on. */
public data class MemoryPreviewUi(val on: Boolean, val phoneNode: Boolean)

public data class AboutFacts(
    val version: String = "",
    val build: String = "",
    val whatsNew: Boolean = true,
    /** The open-source notices page exists in this build. */
    val notices: Boolean = true,
    /** The application id (Flutter About › Details "Package id"); blank hides the fold. */
    val packageId: String = "",
    /** The signing certificate's SHA-256 as upper-case hex; null where the platform has none. */
    val signer: String? = null,
    /**
     * A hardware keyboard can reach the app (a desktop, or a phone or tablet with a keyboard): About
     * lists the keyboard shortcuts (F: about_screen.dart:301, `desktopInteractions`).
     */
    val keyboardShortcuts: Boolean = false,
    /** The accelerator's modifier word: "Ctrl", or "⌘" on a Mac. */
    val shortcutModifier: String = "Ctrl",
)

/** Help › Guide's doors (F: guide_screen.dart:40-140). Each is shown only where it leads somewhere. */
public data class GuideUi(
    /** Step 2 says "Scan the QR or paste the code" (the phone has a camera scanner). */
    val canScan: Boolean = false,
    /** Step 2's "Add server" button opens the add flow itself. */
    val addServer: Boolean = false,
    /** "Run it on this phone" opens phone setup (built-in Linux). */
    val phonePath: Boolean = false,
    /** "Try the demo" opens the offline demo. */
    val demo: Boolean = false,
    /** Passwords live in the Android Keystore here (the security note names it). */
    val androidKeystore: Boolean = false,
)

public data class PrivacyUi(
    /** The analytics engine's own view (sending state); null where the host binds no analytics engine. */
    val collect: WhatWeCollect?,
    val saving: Boolean = false,
    /** The crash-report switch; null while this build has no way to send one. */
    val crashReports: Boolean? = null,
    /** "Offer crash reports" (Flutter 2.1.5); null where the build has no crash offer. */
    val offerCrashReports: Boolean? = null,
    /** Flutter 2.1.5's "Usage and app health" answer; null where the build has no such switch. */
    val usage: Boolean? = null,
    /** B-180: the telemetry vendor is built in, so the two switches really send (What we collect drops its notice). */
    val vendorSends: Boolean = false,
)

public enum class SettingsNote {
    AppearanceNotSaved,
    ChoiceNotSaved,
    ConsentNotSaved,
    TestNotSent,
    UpdateFailed,
    LicenseCopied,
    LicenseCopyFailed,
    /** Report a problem › Post publicly on GitHub: the form could not be opened. */
    GitHubFailed,
}

/** A built-in walkthrough step. Strings and glyphs are resolved only by the screen. */
public data class WhatsNewUi(
    val version: String,
    val changes: List<WhatsNewChange>,
    val index: Int = 0,
    val canShow: Boolean = false,
) {
    public val change: WhatsNewChange
        get() = changes[index]

    public val last: Boolean
        get() = index == changes.lastIndex
}
