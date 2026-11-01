package io.github.eslamasabry.oc.engine.settings.prefs

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SharedPreferencesAcrossGraphsTest {
    @Test
    fun `B-STATE-126 display preferences are shared across live graphs once acknowledged`() = runTest {
        val store = storeOver(TestDisk(), StandardTestDispatcher(testScheduler))
        val app = WriterLane("app")
        // The app graph's one instance, borrowed by two connection graphs, plus a second instance over the same store
        // (a Kotlin surface or an isolated graph): every reader sees the same acknowledged value.
        val shared = Preferences(store, app, backgroundScope)
        val mainGraph = shared
        val secondaryGraph = shared
        val isolated = Preferences(store, app, backgroundScope)

        assertIs<Outcome.Ok<*>>(mainGraph.set(PreferenceChange.Appearance(AppearanceChoice.Light)))
        assertIs<Outcome.Ok<*>>(secondaryGraph.set(PreferenceChange.Theme(ThemePack.Nord)))
        assertIs<Outcome.Ok<*>>(isolated.set(PreferenceChange.Locale(LocaleChoice.Ar)))
        assertIs<Outcome.Ok<*>>(mainGraph.set(PreferenceChange.ReasoningExpanded(true)))
        val chosen = Effects(motion = MotionLevel.Calm, activityGlow = false, glowStyle = GlowStyle.SoftRing)
        assertIs<Outcome.Ok<*>>(isolated.set(PreferenceChange.SetEffects(chosen)))
        runCurrent()

        for (target in listOf(mainGraph, secondaryGraph, isolated)) {
            val value = target.value.value
            assertEquals(AppearanceChoice.Light, value.appearance)
            assertEquals(ThemePack.Nord, value.themePack)
            assertEquals(LocaleChoice.Ar, value.locale)
            assertEquals(true, value.reasoningExpanded)
            assertEquals(chosen, value.effects)
            assertEquals(MotionLevel.Calm, target.motion.value)
        }
    }

    @Test
    fun `B-STATE-126 disposing a secondary graph leaves shared preferences usable`() = runTest {
        val store = storeOver(TestDisk(), StandardTestDispatcher(testScheduler))
        val app = WriterLane("app")
        val shared = Preferences(store, app, backgroundScope)
        val secondaryScope = CoroutineScope(backgroundScope.coroutineContext + Job())
        val secondary = Preferences(store, app, secondaryScope)
        secondaryScope.cancel()

        assertIs<Outcome.Ok<*>>(shared.set(PreferenceChange.Appearance(AppearanceChoice.Dark)))
        assertEquals(AppearanceChoice.Dark, shared.value.value.appearance)
        val later = Preferences(store, app, backgroundScope)
        assertEquals(AppearanceChoice.Dark, later.value.value.appearance)
        assertIs<Outcome.Ok<*>>(later.set(PreferenceChange.Locale(LocaleChoice.Ar)))
        runCurrent()
        assertEquals(LocaleChoice.Ar, shared.locale.value)
        // The disposed graph's instance stops following; it never breaks the shared owner.
        assertEquals(LocaleChoice.System, secondary.locale.value)
    }
}
