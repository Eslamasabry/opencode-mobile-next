package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.engine.settings.prefs.DevicePreferences
import io.github.eslamasabry.oc.engine.settings.prefs.GlassLookChoice
import io.github.eslamasabry.oc.engine.settings.prefs.PreferenceChange
import io.github.eslamasabry.oc.feature.settings.presentation.PersonalChange
import io.github.eslamasabry.oc.feature.settings.presentation.PersonalPrefs
import io.github.eslamasabry.oc.feature.settings.presentation.PreferenceMapping
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsNote
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.presentation.SurfaceStyle
import io.github.eslamasabry.oc.feature.settings.presentation.UpdateFacts
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.EdgeHold
import io.github.eslamasabry.oc.ui.design.style.GlassLookId
import io.github.eslamasabry.oc.ui.design.style.GlassLooks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The Glass look (docs/designs/glass-looks-2026-11-09.md §3-§4): each look applies at once through the one write path,
 * the default is Glossy, the stored choice maps both ways, and a refused write keeps the look and says so. Extends
 * B-KIT-010 (the appearance applies at the app root) and B-STATE-005 (a refused write).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GlassLookSettingsTest {
    private fun TestScope.presenter(prefs: PersonalPrefs, choices: FakeChoices = FakeChoices(prefs), facts: io.github.eslamasabry.oc.feature.settings.presentation.SettingsFacts = phoneFacts()) =
        SettingsPresenter(
            scope = backgroundScope,
            facts = MutableStateFlow(facts),
            choices = choices,
            openers = RecordingOpeners(),
            analytics = FakeAnalytics(),
            notifications = FakeNotifications(),
            updates = FakeUpdates(UpdateFacts(current = "3.0.0", build = "10001")),
        )

    @Test
    fun `B-KIT-010 the default look is Glossy everywhere`() {
        assertEquals(GlassLookId.Glossy, PersonalPrefs().glassLook)
        assertEquals(GlassLookChoice.Glossy, DevicePreferences().glassLook)
        assertEquals(GlassLookId.Glossy, PreferenceMapping.appearance(DevicePreferences()).glassLook)
    }

    @Test
    fun `B-KIT-010 each offered look applies at once and reaches the app's appearance`() = runTest {
        val choices = FakeChoices(PersonalPrefs(style = SurfaceStyle.Glass))
        val p = presenter(choices.value.value, choices)
        runCurrent()
        p.onOpen(SettingsPage.Appearance); runCurrent()
        assertEquals(GlassLooks.offered, p.state.value.appearance.glassLooks)
        for (look in GlassLooks.offered.reversed()) {
            p.onGlassLook(look); runCurrent()
            assertEquals(look, p.state.value.appearance.glassLook, "$look is shown as chosen")
            assertEquals(look, choices.value.value.glassLook)
        }
        // Every look maps to its stored name and back into what KitTheme wears.
        for (look in GlassLookId.entries) {
            val change = PreferenceMapping.change(PersonalChange.Look(look), DevicePreferences())
            assertEquals(PreferenceChange.Look(GlassLookChoice.entries.first { it.name == look.name }), change)
            val stored = DevicePreferences(glassLook = GlassLookChoice.entries.first { it.name == look.name })
            assertEquals(look, PreferenceMapping.appearance(stored).glassLook)
            assertEquals(look, PreferenceMapping.personal(stored).glassLook)
        }
    }

    @Test
    fun `B-STATE-005 a refused look write keeps the old look and says it was not saved`() = runTest {
        val choices = FakeChoices(PersonalPrefs(style = SurfaceStyle.Glass))
        val p = presenter(choices.value.value, choices)
        runCurrent()
        p.onOpen(SettingsPage.Appearance); runCurrent()
        choices.refuseNext = true
        p.onGlassLook(GlassLookId.Frosted); runCurrent()
        assertEquals(GlassLookId.Glossy, p.state.value.appearance.glassLook)
        assertEquals(SettingsNote.AppearanceNotSaved, p.state.value.note)
    }

    @Test
    fun `B-KIT-008 Saver holds the look and a tap writes nothing`() = runTest {
        val choices = FakeChoices(PersonalPrefs(style = SurfaceStyle.Glass, mode = AppearanceMode.Saver))
        val p = presenter(choices.value.value, choices)
        runCurrent()
        p.onGlassLook(GlassLookId.Smoked); runCurrent()
        assertEquals(emptyList(), choices.writes)
    }

    @Test
    fun `B-KIT-018 motion off holds the looks, and no lens is said, not held`() = runTest {
        val motionOff = presenter(PersonalPrefs(style = SurfaceStyle.Glass), facts = phoneFacts().copy(systemMotionOff = true, liquidGlass = true))
        runCurrent()
        motionOff.onOpen(SettingsPage.Appearance); runCurrent()
        assertEquals(EdgeHold.Motion, motionOff.state.value.appearance.glassLookHold)
        val noLens = presenter(PersonalPrefs(style = SurfaceStyle.Glass), facts = phoneFacts().copy(liquidGlass = false))
        runCurrent()
        noLens.onOpen(SettingsPage.Appearance); runCurrent()
        assertEquals(EdgeHold.Platform, noLens.state.value.appearance.glassLookHold)
        val ready = presenter(PersonalPrefs(style = SurfaceStyle.Glass), facts = phoneFacts().copy(liquidGlass = true))
        runCurrent()
        ready.onOpen(SettingsPage.Appearance); runCurrent()
        assertNull(ready.state.value.appearance.glassLookHold)
    }
}
