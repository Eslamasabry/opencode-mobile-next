package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.engine.settings.prefs.AppearanceChoice
import io.github.eslamasabry.oc.engine.settings.prefs.DevicePreferences
import io.github.eslamasabry.oc.engine.settings.prefs.LocaleChoice
import io.github.eslamasabry.oc.engine.settings.prefs.PreferenceChange
import io.github.eslamasabry.oc.engine.settings.prefs.Preferences
import io.github.eslamasabry.oc.engine.settings.prefs.TextSize
import io.github.eslamasabry.oc.engine.settings.prefs.ThemePack
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.text.AppTextSize
import io.github.eslamasabry.oc.ui.design.tokens.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import io.github.eslamasabry.oc.engine.settings.prefs.MotionLevel as StoredMotion
import io.github.eslamasabry.oc.engine.settings.prefs.SurfaceStyle as StoredStyle
import io.github.eslamasabry.oc.ui.design.effects.FrostStrength as KitFrost
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel as KitMotion

/**
 * `:engine:settings` [Preferences] as [PersonalChoices]: field-for-field over the acknowledged snapshot. Nothing is
 * optimistic: [value] moves only when the store acknowledged the write (B-STATE-005), and every change is one
 * all-or-nothing commit under the existing Flutter keys (B-STATE-003), so a migrated install keeps its choices.
 *
 * The facade can only be built once the preference file is warm, so the app hands it over late: [preferences]
 * suspends until then and returns null when the file could not be opened (writes then fail plainly, reads stay at
 * the defaults). [snapshot] is the acknowledged value the app already mirrors for the theme.
 */
public class PreferencesChoices(
    private val preferences: suspend () -> Preferences?,
    snapshot: StateFlow<DevicePreferences>,
    scope: CoroutineScope,
) : PersonalChoices {
    override val value: StateFlow<PersonalPrefs> =
        snapshot.map(PreferenceMapping::personal).stateIn(scope, SharingStarted.Eagerly, PreferenceMapping.personal(snapshot.value))

    override suspend fun set(change: PersonalChange): Outcome<PersonalPrefs> {
        val prefs = preferences() ?: return Failure.StorageRefused().failed()
        return prefs.set(PreferenceMapping.change(change, prefs.value.value)).map(PreferenceMapping::personal)
    }
}

/** The pure mapping between the stored device preferences and what Settings and the theme read. */
public object PreferenceMapping {
    public fun personal(p: DevicePreferences): PersonalPrefs =
        PersonalPrefs(
            mode = mode(p.appearance),
            pack = pack(p.themePack),
            style = SurfaceStyle.entries.first { it.name == p.effects.surfaceStyle.name },
            fineTuned = !p.effects.fineTune.isNone,
            motion = motion(p.effects.motion),
            reasoning = p.reasoningExpanded,
            timestamps = p.timestampsVisible,
            textSize = textSize(p.textSize),
            language = language(p.locale),
        )

    /** One [PreferenceChange] per [PersonalChange]; effect changes keep every other effect field ([current]). */
    public fun change(change: PersonalChange, current: DevicePreferences): PreferenceChange =
        when (change) {
            is PersonalChange.Mode -> PreferenceChange.Appearance(AppearanceChoice.entries.first { it.name == change.mode.name })
            is PersonalChange.Pack ->
                PreferenceChange.Theme(ThemePack.entries.firstOrNull { it.wire == change.pack.storageName } ?: ThemePack.OpenCode)
            // B-KIT-040: a new style starts at its own Fine-tune defaults.
            is PersonalChange.Style ->
                PreferenceChange.SetEffects(current.effects.withSurfaceStyle(StoredStyle.entries.first { it.name == change.style.name }))
            is PersonalChange.Motion ->
                PreferenceChange.SetEffects(current.effects.copy(motion = StoredMotion.entries.first { it.name == change.motion.name }))
            is PersonalChange.Reasoning -> PreferenceChange.ReasoningExpanded(change.on)
            is PersonalChange.Timestamps -> PreferenceChange.TimestampsVisible(change.on)
            is PersonalChange.TextSize ->
                PreferenceChange.Text(TextSize.entries.firstOrNull { it.wire == change.size.storageName } ?: TextSize.Normal)
            is PersonalChange.Language -> PreferenceChange.Locale(LocaleChoice.of(change.language.tag) ?: LocaleChoice.System)
        }

    /** What `KitTheme` wears, from the acknowledged snapshot: applied at the app root, so every screen follows (B-KIT-010). */
    public fun appearance(p: DevicePreferences): KitAppearance =
        KitAppearance(
            mode = mode(p.appearance),
            pack = pack(p.themePack),
            accent = p.accent,
            textSize = textSize(p.textSize),
            motion = motion(p.effects.motion),
            density = p.effects.fineTune.density?.let { d -> Density.entries.firstOrNull { it.name == d.name } } ?: Density.Comfortable,
            frost = KitFrost.fromStorage(p.frost.wire),
        )

    /** The chosen language's tag, or null to follow the phone. A stored language the native app has no words for still
     * applies (its words fall back to English, B-L10N-010). */
    public fun localeTag(p: DevicePreferences): String? = p.locale.code

    private fun mode(choice: AppearanceChoice): AppearanceMode = AppearanceMode.entries.first { it.name == choice.name }

    private fun pack(pack: ThemePack): PackId = PackId.entries.firstOrNull { it.storageName == pack.wire } ?: PackId.Opencode

    private fun motion(level: StoredMotion): KitMotion = KitMotion.entries.first { it.name == level.name }

    private fun textSize(size: TextSize): AppTextSize =
        AppTextSize.entries.firstOrNull { it.storageName == size.wire } ?: AppTextSize.Normal

    private fun language(choice: LocaleChoice): AppLanguage =
        when (choice) {
            LocaleChoice.En -> AppLanguage.English
            LocaleChoice.Ar -> AppLanguage.Arabic
            else -> AppLanguage.System
        }
}
