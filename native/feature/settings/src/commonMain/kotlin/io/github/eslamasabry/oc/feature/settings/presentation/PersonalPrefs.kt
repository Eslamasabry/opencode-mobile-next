package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Corners
import io.github.eslamasabry.oc.ui.design.text.AppTextSize
import io.github.eslamasabry.oc.ui.design.tokens.Density

/**
 * The six styles (B-KIT-037), in picker order (Glass back in its GLASS_REF place, after Solid; rebuild standard §5.1);
 * stored as `oc.effectsSurfaceStyle` by `:engine:settings`. Solid stays the default; Glass is only ever the person's
 * own choice.
 */
public enum class SurfaceStyle { Solid, Glass, Soft, Brutal, Paper, Signal }

/** Background › How strong? (`oc.effectsLiveIntensity`; Dart `KitLiveIntensity`): Subtle .7, Normal 1, Bold 1.6. */
public enum class LiveStrength(public val strength: Float) {
    Subtle(.7f), // FG: lib/state/effects.dart:45
    Normal(1f), // FG: lib/state/effects.dart:46
    Bold(1.6f), // FG: lib/state/effects.dart:47
}

/**
 * The personal choices these pages show (the `Device`-owned slice of `:engine:settings` `DevicePreferences`).
 * Defaults are the engine's: Dark, Graphite, Solid, Full motion, reasoning folded, no timestamps.
 */
public data class PersonalPrefs(
    val mode: AppearanceMode = AppearanceMode.Dark,
    val pack: PackId = PackId.Opencode,
    val style: SurfaceStyle = SurfaceStyle.Solid,
    /** Fine-tune knobs are set (corners or density); a new style clears them. */
    val fineTuned: Boolean = false,
    /** The person's Corners choice over the style's own; null = the style's (B-KIT-017). */
    val corners: Corners? = null,
    /** The person's Spacing choice over the style's own; null = the style's. */
    val spacing: Density? = null,
    /** `oc.appearanceAccent` swatch id; null = Match theme (B-KIT-049). */
    val accent: String? = null,
    val motion: MotionLevel = MotionLevel.Full,
    val reasoning: Boolean = false,
    val timestamps: Boolean = false,
    /** In-app text size (`oc.textSize`), applied at the app root (B-KIT-059). */
    val textSize: AppTextSize = AppTextSize.Normal,
    /** The app language (`oc.appLocale`); [AppLanguage.System] follows the phone (B-L10N-001). */
    val language: AppLanguage = AppLanguage.System,
    /** Background on (`oc.effectsBackgroundOn`); off by default: the page its plain colour. */
    val liveBackground: Boolean = false,
    /** The chosen look's id (`oc.effectsLiveStyle`, Dart `KitLiveStyle.id`). */
    val liveStyle: String = "aurora",
    val liveStrength: LiveStrength = LiveStrength.Normal,
) {
    public val saver: Boolean get() = mode == AppearanceMode.Saver
}

/** One personal change; each is one all-or-nothing write in the engine. */
public sealed interface PersonalChange {
    public data class Mode(val mode: AppearanceMode) : PersonalChange

    public data class Pack(val pack: PackId) : PersonalChange

    /** Also clears the fine-tune choices (B-KIT-040). */
    public data class Style(val style: SurfaceStyle) : PersonalChange

    public data class Motion(val motion: MotionLevel) : PersonalChange

    /** Fine-tune Corners; null puts the style's own back. */
    public data class Corner(val corners: Corners?) : PersonalChange

    /** Fine-tune Spacing; null puts the style's own back. */
    public data class Spacing(val spacing: Density?) : PersonalChange

    /** The Accent swatch by id; null = Match theme. */
    public data class Accent(val id: String?) : PersonalChange

    public data class Reasoning(val on: Boolean) : PersonalChange

    public data class Timestamps(val on: Boolean) : PersonalChange

    public data class TextSize(val size: AppTextSize) : PersonalChange

    public data class Language(val language: AppLanguage) : PersonalChange

    /** Background › Look: a look's id turns the background on with it; null turns it off (Dart `_lookBody`). */
    public data class LiveLook(val id: String?) : PersonalChange

    /** Background › How strong? */
    public data class LiveIntensity(val strength: LiveStrength) : PersonalChange
}

/**
 * The languages the Language page offers, in Flutter's picker order (`AppLocaleStore.pickerLanguages`): every language
 * the app ships words for (`:ui:words` values-*), plus the phone's own. [tag] is the BCP 47 tag the theme and the words
 * use; null follows the phone. A saved choice is always one of these, so it is shown and can be cleared (B-L10N-008).
 */
public enum class AppLanguage(public val tag: String?) {
    System(null),
    English("en"),
    Arabic("ar"),
    Spanish("es"),
    Japanese("ja"),
    Portuguese("pt"),
    Russian("ru"),
    Chinese("zh"),
}
