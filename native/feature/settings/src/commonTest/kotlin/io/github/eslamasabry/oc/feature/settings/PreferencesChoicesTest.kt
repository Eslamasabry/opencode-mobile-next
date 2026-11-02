package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.engine.settings.prefs.DevicePreferences
import io.github.eslamasabry.oc.engine.settings.prefs.Effects
import io.github.eslamasabry.oc.engine.settings.prefs.FineTune
import io.github.eslamasabry.oc.engine.settings.prefs.LocaleChoice
import io.github.eslamasabry.oc.engine.settings.prefs.PreferenceChange
import io.github.eslamasabry.oc.engine.settings.prefs.Preferences
import io.github.eslamasabry.oc.engine.settings.prefs.ThemePack
import io.github.eslamasabry.oc.feature.settings.presentation.AppLanguage
import io.github.eslamasabry.oc.feature.settings.presentation.PersonalChange
import io.github.eslamasabry.oc.feature.settings.presentation.PreferenceMapping
import io.github.eslamasabry.oc.feature.settings.presentation.PreferencesChoices
import io.github.eslamasabry.oc.feature.settings.presentation.SurfaceStyle
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.text.AppTextSize
import io.github.eslamasabry.oc.ui.design.tokens.Density
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import io.github.eslamasabry.oc.engine.settings.prefs.Density as StoredDensity
import io.github.eslamasabry.oc.engine.settings.prefs.MotionLevel as StoredMotion
import io.github.eslamasabry.oc.engine.settings.prefs.SurfaceStyle as StoredStyle
import io.github.eslamasabry.oc.engine.settings.prefs.TextSize as StoredTextSize

/** Settings over the real `:engine:settings` Preferences facade and an in-memory Flutter preference file. */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesChoicesTest {
    /** A preference file that can refuse every commit (a full disk). */
    private class Disk : InMemoryPrefsBackend() {
        var refuse = false

        override fun commit(changes: Map<String, RawPref?>): Boolean = if (refuse) false else super.commit(changes)
    }

    private class Rig(val scope: TestScope) {
        val disk = Disk()
        val store = PrefsKeyValueStore(disk, FlutterPrefsCodec(JavaListDecoder.Unsupported), StateMigrationGate.alwaysOpen(), StandardTestDispatcher(scope.testScheduler))
        val prefs = Preferences(store, WriterLane("app"), scope.backgroundScope)
        val choices = PreferencesChoices({ prefs }, prefs.value, scope.backgroundScope)
    }

    private fun rig(body: suspend Rig.() -> Unit) = runTest { Rig(this).apply { runCurrent() }.body() }

    @Test
    fun `B-KIT-061 nothing stored reads the defaults, Dark on Graphite, Full motion, Default text, the phone's language`() =
        rig {
            val p = choices.value.value
            assertEquals(AppearanceMode.Dark, p.mode)
            assertEquals(PackId.Opencode, p.pack)
            assertEquals(MotionLevel.Full, p.motion)
            assertEquals(AppTextSize.Normal, p.textSize)
            assertEquals(AppLanguage.System, p.language)
        }

    @Test
    fun `B-KIT-059 a text size choice is stored under oc_textSize and read back acknowledged`() =
        rig {
            assertIs<Outcome.Ok<*>>(choices.set(PersonalChange.TextSize(AppTextSize.Large)))
            scope.runCurrent()
            assertEquals(AppTextSize.Large, choices.value.value.textSize)
            assertEquals(PrefValue.Str("large"), store.get("oc.textSize"))
            assertEquals(AppTextSize.Large, PreferenceMapping.appearance(prefs.value.value).textSize)
        }

    @Test
    fun `B-L10N-001 Arabic is stored as oc_appLocale ar, and Use system language removes the key`() =
        rig {
            choices.set(PersonalChange.Language(AppLanguage.Arabic))
            scope.runCurrent()
            assertEquals(PrefValue.Str("ar"), store.get("oc.appLocale"))
            assertEquals(AppLanguage.Arabic, choices.value.value.language)
            assertEquals("ar", PreferenceMapping.localeTag(prefs.value.value))

            choices.set(PersonalChange.Language(AppLanguage.System))
            scope.runCurrent()
            assertNull(store.get("oc.appLocale"))
            assertNull(PreferenceMapping.localeTag(prefs.value.value))
        }

    @Test
    fun `B-STATE-005 a refused write leaves the acknowledged choice and fails plainly`() =
        rig {
            disk.refuse = true
            assertIs<Outcome.Failed>(choices.set(PersonalChange.Motion(MotionLevel.Off)))
            scope.runCurrent()
            assertEquals(MotionLevel.Full, choices.value.value.motion)
            assertNull(store.get("oc.effectsMotion"))
        }

    @Test
    fun `B-STATE-005 a facade that never opened refuses writes instead of hanging`() =
        runTest {
            val snapshot = kotlinx.coroutines.flow.MutableStateFlow(DevicePreferences.Defaults)
            val closed = PreferencesChoices({ null }, snapshot, backgroundScope)
            assertIs<Outcome.Failed>(closed.set(PersonalChange.Mode(AppearanceMode.Light)))
            assertEquals(AppearanceMode.Dark, closed.value.value.mode)
        }

    @Test
    fun `B-KIT-006 motion is one app-wide choice that persists under its Flutter key`() =
        rig {
            choices.set(PersonalChange.Motion(MotionLevel.Calm))
            scope.runCurrent()
            assertEquals(PrefValue.Str("calm"), store.get("oc.effectsMotion"))
            assertEquals(MotionLevel.Calm, PreferenceMapping.appearance(prefs.value.value).motion)
        }

    @Test
    fun `B-KIT-040 a new style keeps the other effects and clears Fine-tune`() {
        val current =
            DevicePreferences(
                effects = Effects(motion = StoredMotion.Calm, fineTune = FineTune(density = StoredDensity.Compact)),
            )
        val change = PreferenceMapping.change(PersonalChange.Style(SurfaceStyle.Brutal), current)
        assertIs<PreferenceChange.SetEffects>(change)
        assertEquals(StoredStyle.Brutal, change.value.surfaceStyle)
        assertEquals(StoredMotion.Calm, change.value.motion)
        assertTrue(change.value.fineTune.isNone)
    }

    @Test
    fun `B-STATE-003 every colour pack round-trips by its stored Dart name`() {
        for (pack in PackId.entries) {
            val change = PreferenceMapping.change(PersonalChange.Pack(pack), DevicePreferences.Defaults)
            assertIs<PreferenceChange.Theme>(change)
            assertEquals(pack.storageName, change.value.wire)
            assertEquals(pack, PreferenceMapping.personal(DevicePreferences(themePack = change.value)).pack)
        }
        assertEquals(ThemePack.entries.size, PackId.entries.size)
    }

    @Test
    fun `B-KIT-010 the stored choices become what the theme wears`() {
        val stored =
            DevicePreferences(
                appearance = io.github.eslamasabry.oc.engine.settings.prefs.AppearanceChoice.Saver,
                themePack = ThemePack.Nord,
                textSize = StoredTextSize.Smallest,
                accent = "teal",
                locale = LocaleChoice.Ar,
                effects = Effects(motion = StoredMotion.Off, fineTune = FineTune(density = StoredDensity.Compact)),
            )
        val kit = PreferenceMapping.appearance(stored)
        assertEquals(AppearanceMode.Saver, kit.mode)
        assertEquals(PackId.Nord, kit.pack)
        assertEquals(AppTextSize.Smallest, kit.textSize)
        assertEquals("teal", kit.accent)
        assertEquals(MotionLevel.Off, kit.motion)
        assertEquals(Density.Compact, kit.density)
        assertEquals(AppLanguage.Arabic, PreferenceMapping.personal(stored).language)
    }
}
