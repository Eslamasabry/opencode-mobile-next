package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.text.AppTextSize

/** The five styles (B-KIT-037), in picker order; stored as `oc.effectsSurfaceStyle` by `:engine:settings`. */
public enum class SurfaceStyle { Solid, Soft, Brutal, Paper, Signal }

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
    val motion: MotionLevel = MotionLevel.Full,
    val reasoning: Boolean = false,
    val timestamps: Boolean = false,
    /** In-app text size (`oc.textSize`), applied at the app root (B-KIT-059). */
    val textSize: AppTextSize = AppTextSize.Normal,
    /** The app language (`oc.appLocale`); [AppLanguage.System] follows the phone (B-L10N-001). */
    val language: AppLanguage = AppLanguage.System,
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

    public data class Reasoning(val on: Boolean) : PersonalChange

    public data class Timestamps(val on: Boolean) : PersonalChange

    public data class TextSize(val size: AppTextSize) : PersonalChange

    public data class Language(val language: AppLanguage) : PersonalChange
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
