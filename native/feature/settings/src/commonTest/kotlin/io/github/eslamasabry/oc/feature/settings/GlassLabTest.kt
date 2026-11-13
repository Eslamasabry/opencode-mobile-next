package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.engine.settings.prefs.DevicePreferences
import io.github.eslamasabry.oc.feature.settings.presentation.GlassLabPresenter
import io.github.eslamasabry.oc.feature.settings.presentation.GlassLabSetting
import io.github.eslamasabry.oc.feature.settings.presentation.PreferenceMapping
import io.github.eslamasabry.oc.ui.design.effects.GlassKnob
import io.github.eslamasabry.oc.ui.design.effects.GlassPaneKind
import io.github.eslamasabry.oc.ui.design.effects.GlassPreset
import io.github.eslamasabry.oc.ui.design.effects.GlassTuning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Settings › Appearance › Glass lab (2026-11-13): what the lab stores, and what the app then wears. */
class GlassLabTest {
    private class Port(initial: String? = null) : GlassLabSetting {
        val saved = mutableListOf<String?>()
        private val flow = MutableStateFlow(initial)
        override val stored: StateFlow<String?> = flow
        override val build: String = "2.2.0 (900)"

        override fun save(text: String?) {
            saved += text
            flow.value = text
        }
    }

    @Test
    fun `B-GLASS-LAB-020 a moved slider changes the draft at once and is stored once it rests`() = runTest {
        val port = Port()
        val lab = GlassLabPresenter(backgroundScope, port)
        lab.onKind(GlassPaneKind.Menu)
        repeat(30) { lab.onKnob(GlassKnob.Blur, 2f + it * .5f, dark = true) }
        assertEquals(16.5f, lab.state.value.tuning.knobs(GlassPaneKind.Menu, true).blurDp)
        runCurrent()
        assertTrue(port.saved.isEmpty(), "nothing stored while the finger moves")
        advanceTimeBy(GlassLabPresenter.SAVE_MS + 1)
        runCurrent()
        assertEquals(1, port.saved.size)
        assertEquals(16.5f, GlassTuning.decode(port.saved.single()!!)!!.knobs(GlassPaneKind.Menu, true).blurDp)
    }

    @Test
    fun `B-GLASS-LAB-021 the stored numbers survive a new visit, and the app wears them only while the switch is on`() = runTest {
        val port = Port()
        val lab = GlassLabPresenter(backgroundScope, port)
        lab.onPreset(GlassPreset.ClearLiquid, dark = true)
        val off = port.saved.last()
        assertNotNull(off)
        // Off: the app's appearance carries no tuning, so every pane is the shipped glass.
        assertNull(PreferenceMapping.appearance(DevicePreferences(glassTuning = off)).glassTuning)
        lab.onInApp(true)
        val on = port.saved.last()
        val worn = PreferenceMapping.appearance(DevicePreferences(glassTuning = on)).glassTuning
        assertNotNull(worn)
        assertEquals(lab.state.value.tuning.trimmed(), worn)
        // A new visit (an app restart) starts from what was stored.
        val again = GlassLabPresenter(backgroundScope, Port(on))
        assertTrue(again.state.value.inApp)
        assertEquals(lab.state.value.tuning.knobs(GlassPaneKind.Bar, true), again.state.value.tuning.knobs(GlassPaneKind.Bar, true))
        // Reset all keeps the switch and stores the shipped numbers; switched off as well, nothing is stored.
        again.onResetAll()
        assertTrue(again.state.value.tuning.isShipped)
        again.onInApp(false)
        assertNull(PreferenceMapping.appearance(DevicePreferences(glassTuning = null)).glassTuning)
        assertEquals(PreferenceMapping.appearance(DevicePreferences()), PreferenceMapping.appearance(DevicePreferences(glassTuning = null)))
    }

    @Test
    fun `B-GLASS-LAB-022 a pasted block loads its numbers; text without numbers is refused and says so`() = runTest {
        val lab = GlassLabPresenter(backgroundScope, Port())
        assertFalse(lab.onPaste("hello"))
        assertTrue(lab.state.value.pasteRefused)
        val block = GlassTuning.encode(GlassTuning.Shipped.with(GlassPaneKind.Chip, false, GlassKnob.LensStrength, 30f), "x")
        assertTrue(lab.onPaste(block))
        assertFalse(lab.state.value.pasteRefused)
        assertEquals(30f, lab.state.value.tuning.knobs(GlassPaneKind.Chip, false).lensStrengthDp)
        // The copied block is the lab's numbers with the build.
        assertTrue("build 2.2.0 (900)" in lab.state.value.copyBlock)
        assertEquals(lab.state.value.tuning.trimmed(), GlassTuning.decode(lab.state.value.copyBlock))
    }

    @Test
    fun `B-GLASS-LAB-023 all linked moves every pane kind by the same step`() = runTest {
        val lab = GlassLabPresenter(backgroundScope, Port())
        lab.onKind(null)
        lab.onKnob(GlassKnob.LensStrength, 44f, dark = false)
        val t = lab.state.value.tuning
        assertEquals(44f, t.knobs(GlassPaneKind.Bar, false).lensStrengthDp)
        assertEquals(58f, t.knobs(GlassPaneKind.Sheet, false).lensStrengthDp)
        assertEquals(22f, t.knobs(GlassPaneKind.Chip, false).lensStrengthDp)
    }
}
