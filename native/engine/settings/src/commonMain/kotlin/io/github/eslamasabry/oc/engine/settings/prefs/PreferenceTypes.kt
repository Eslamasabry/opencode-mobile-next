package io.github.eslamasabry.oc.engine.settings.prefs

// Device preference values (Flutter `lib/state/app_preferences.dart`, `lib/state/effects.dart`,
// `lib/state/profiles/preferences_types.dart`, `lib/state/glass_choice.dart`). Every enum is stored by its Dart name
// ([wire]) under the existing key; an unknown stored name reads as the default (plan 06 §5.2, B-STATE-003).

/** A value stored by its Dart enum name. */
public interface WireNamed {
    public val wire: String
}

internal inline fun <reified T> wireOf(raw: Any?): T? where T : Enum<T>, T : WireNamed =
    enumValues<T>().firstOrNull { it.wire == raw }

/** Light or dark (`oc.appearance`). Saver is dark on true black with motion and the live look off. */
public enum class AppearanceChoice(override val wire: String) : WireNamed {
    System("system"), Light("light"), Dark("dark"), Saver("saver"),
}

/** Colour identity (`oc.themePack`), 31 packs in the Dart order; `dynamic` stays last. */
public enum class ThemePack(override val wire: String) : WireNamed {
    OpenCode("opencode"), Catppuccin("catppuccin"), Gruvbox("gruvbox"), Solarized("solarized"), Dracula("dracula"),
    Nord("nord"), TokyoNight("tokyoNight"), OneDark("oneDark"), Monokai("monokai"), RosePine("rosePine"),
    Everforest("everforest"), Kanagawa("kanagawa"), Ayu("ayu"), NightOwl("nightOwl"), GitHub("github"),
    Palenight("palenight"), Synthwave("synthwave"), Cobalt("cobalt"), Midnight("midnight"), Paper("paper"),
    Forest("forest"), Ocean("ocean"), Sunset("sunset"), Sakura("sakura"), Lavender("lavender"), Mint("mint"),
    Coffee("coffee"), Slate("slate"), Amber("amber"), HighContrast("highContrast"), Dynamic("dynamic"),
}

/** The app language (`oc.appLocale`): [System] removes the key. Unsupported stored codes read as [System]. */
public enum class LocaleChoice(public val code: String?) {
    System(null), En("en"), Ar("ar"), Zh("zh"), Es("es"), Pt("pt"), Ja("ja"), Ru("ru");

    public companion object {
        /** The language part of [tag] (`ar-EG` → [Ar]); null when unsupported. Null [tag] → [System]. */
        public fun of(tag: String?): LocaleChoice? {
            if (tag == null) return System
            val language = tag.substringBefore('-').substringBefore('_')
            return entries.firstOrNull { it.code != null && it.code == language }
        }
    }
}

public enum class MotionLevel(override val wire: String) : WireNamed { Full("full"), Calm("calm"), Off("off") }

public enum class TextSize(override val wire: String, public val factor: Double) : WireNamed {
    Smallest("smallest", 0.85), Small("small", 0.92), Normal("normal", 1.0), Large("large", 1.15),
}

/** The reply glow (`oc.effectsGlowStyle`). */
public enum class GlowStyle(override val wire: String) : WireNamed { Classic("classic"), SoftRing("softRing") }

/** Never read from storage (`oc.effectsGlowColours` is I): always [One]. Kept for the settings facade schema. */
public enum class GlowColours(override val wire: String) : WireNamed { One("one"), Two("two") }

/** Never read from storage (`oc.effectsGlowSpeed` is I): always [Normal]. Kept for the settings facade schema. */
public enum class GlowSpeed(override val wire: String) : WireNamed { Slow("slow"), Normal("normal"), Fast("fast") }

public enum class LiveIntensity(override val wire: String) : WireNamed {
    Subtle("subtle"), Normal("normal"), Bold("bold"),
}

/**
 * `oc.effectsSurfaceStyle` (Flutter `KitSurfaceStyle` at GLASS_REF 1bbdc3f9c, `lib/state/effects.dart`): solid, glass,
 * soft, brutal, paper, signal, in picker order. Glass is a school again (owner, 2026-11-05; rebuild standard §5.7); the
 * retired `crystal` and any unknown name read as [Solid].
 */
public enum class SurfaceStyle(override val wire: String) : WireNamed {
    Solid("solid"), Glass("glass"), Soft("soft"), Brutal("brutal"), Paper("paper"), Signal("signal"),
}

public enum class Corners(override val wire: String) : WireNamed { Round("round"), Soft("soft"), Sharp("sharp") }

public enum class Density(override val wire: String) : WireNamed { Comfortable("comfortable"), Compact("compact") }

/** The frost strength (`oc.glass`, CR06-5): missing or unknown reads [Balanced]; device-wide, never swept. */
public enum class FrostStrength(override val wire: String) : WireNamed {
    Off("off"), Weak("weak"), Balanced("balanced"), Strong("strong"),
}

/**
 * The person's Fine-tune choices; null = the style's default. Only Corners and Spacing exist: each style keeps its
 * own depth and edges, and the stored `oc.effectsDepth` / `oc.effectsEdges` are never read (plan 06 §5.2).
 */
public data class FineTune(val corners: Corners? = null, val density: Density? = null) {
    public val isNone: Boolean get() = corners == null && density == null

    public companion object {
        public val None: FineTune = FineTune()
    }
}

/**
 * The stored effect choices. [celebrations] is derived from [motion] (the old switch folds into Motion). The reply
 * glow is one choice ([activityGlow] + [glowStyle]); its colours and speed are the app's own.
 */
public data class Effects(
    val motion: MotionLevel = MotionLevel.Full,
    val activityGlow: Boolean = true,
    val glowStyle: GlowStyle = GlowStyle.Classic,
    val liveBackground: Boolean = false,
    val liveHeldByStyle: Boolean = false,
    val liveStyle: String = DEFAULT_LIVE_STYLE,
    val liveIntensity: LiveIntensity = LiveIntensity.Normal,
    val surfaceStyle: SurfaceStyle = SurfaceStyle.Solid,
    val fineTune: FineTune = FineTune.None,
) {
    public val celebrations: Boolean get() = motion == MotionLevel.Full

    /** A new style with Fine-tune back at that style's defaults. */
    public fun withSurfaceStyle(style: SurfaceStyle): Effects = copy(surfaceStyle = style, fineTune = FineTune.None)

    public companion object {
        public const val DEFAULT_LIVE_STYLE: String = "aurora"
        public val Defaults: Effects = Effects()
    }
}

/** One acknowledged snapshot of every device preference. */
public data class DevicePreferences(
    val appearance: AppearanceChoice = AppearanceChoice.Dark,
    val themePack: ThemePack = ThemePack.OpenCode,
    val locale: LocaleChoice = LocaleChoice.System,
    val effects: Effects = Effects.Defaults,
    val textSize: TextSize = TextSize.Normal,
    /** The Accent swatch id; null matches the theme. */
    val accent: String? = null,
    val frost: FrostStrength = FrostStrength.Balanced,
    val reasoningExpanded: Boolean = false,
    val timestampsVisible: Boolean = false,
) {
    public val saver: Boolean get() = appearance == AppearanceChoice.Saver

    public companion object {
        public val Defaults: DevicePreferences = DevicePreferences()
    }
}

/** One write to the device preferences. Every change is one atomic commit. */
public sealed interface PreferenceChange {
    /** Reset only Appearance; conversation and language choices are retained. */
    public data object ResetAppearance : PreferenceChange

    public data class Appearance(val value: AppearanceChoice) : PreferenceChange

    public data class Theme(val value: ThemePack) : PreferenceChange

    public data class Locale(val value: LocaleChoice) : PreferenceChange

    public data class Text(val value: TextSize) : PreferenceChange

    /** Null goes back to the theme's own partner colour (the key is removed). */
    public data class Accent(val id: String?) : PreferenceChange

    public data class Frost(val value: FrostStrength) : PreferenceChange

    public data class ReasoningExpanded(val value: Boolean) : PreferenceChange

    public data class TimestampsVisible(val value: Boolean) : PreferenceChange

    /** Writes only the fields that differ; a style change resets the knobs (plan 06 §5.2). */
    public data class SetEffects(val value: Effects) : PreferenceChange
}
