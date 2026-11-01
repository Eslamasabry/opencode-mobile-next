package io.github.eslamasabry.oc.engine.settings.prefs

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.engine.settings.prefs.DevicePreferenceKeys as K
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PreferencesTest {
    private val app = WriterLane("app")

    private fun TestScope.fresh(disk: TestDisk = TestDisk()) = storeOver(disk, StandardTestDispatcher(testScheduler))

    @Test
    fun `B-STATE-003 legacy values keep defaults and the old celebrations switch folds into Motion`() = runTest {
        val disk = TestDisk()
        val store = fresh(disk)
        store.seed(
            "oc.appearance" to "light",
            "oc.themePack" to "unknown",
            "oc.appLocale" to "ar",
            "oc.effectsMotion" to "full",
            "oc.effectsCelebrations" to false,
            "oc.effectsActivityGlow" to false,
            "oc.effectsGlowStyle" to "unknown",
            "oc.effectsGlowSpeed" to 42L,
            "oc.transcript.reasoningExpanded" to true,
            "oc.transcript.timestampsVisible" to "invalid",
            "oc.effectsSurfaceStyle" to "glass",
            "oc.glass" to "strong",
        )
        val commitsBefore = disk.commits.size
        val owner = Preferences(store, app, backgroundScope)
        val value = owner.value.value
        assertEquals(AppearanceChoice.Light, value.appearance)
        assertEquals(ThemePack.OpenCode, value.themePack)
        assertEquals(LocaleChoice.Ar, value.locale)
        assertEquals(MotionLevel.Calm, value.effects.motion)
        assertFalse(value.effects.celebrations)
        assertFalse(value.effects.activityGlow)
        assertEquals(GlowStyle.Classic, value.effects.glowStyle)
        assertEquals(SurfaceStyle.Solid, value.effects.surfaceStyle, "the retired Glass style reads as Solid")
        assertEquals(FrostStrength.Strong, value.frost)
        assertTrue(value.reasoningExpanded)
        assertFalse(value.timestampsVisible)
        assertEquals(0L, owner.revision.value)
        assertEquals(commitsBefore, disk.commits.size, "loading never writes")
        assertEquals(MotionLevel.Calm, owner.motion.value)
        assertEquals(LocaleChoice.Ar, owner.locale.value)
    }

    @Test
    fun `B-STATE-003 every device choice persists under its existing key and survives a restart`() = runTest {
        val disk = TestDisk()
        val store = fresh(disk)
        val owner = Preferences(store, app, backgroundScope)
        assertIs<Outcome.Ok<*>>(owner.set(PreferenceChange.Theme(ThemePack.Gruvbox)))
        assertIs<Outcome.Ok<*>>(owner.set(PreferenceChange.Locale(LocaleChoice.of("ar-EG")!!)))
        assertIs<Outcome.Ok<*>>(owner.set(PreferenceChange.ReasoningExpanded(true)))
        assertIs<Outcome.Ok<*>>(owner.set(PreferenceChange.TimestampsVisible(true)))
        assertIs<Outcome.Ok<*>>(owner.set(PreferenceChange.Text(TextSize.Large)))
        assertIs<Outcome.Ok<*>>(owner.set(PreferenceChange.Accent("teal")))
        assertIs<Outcome.Ok<*>>(owner.set(PreferenceChange.Frost(FrostStrength.Off)))
        assertEquals(LocaleChoice.Ar, owner.value.value.locale)
        assertEquals("gruvbox", store.string("oc.themePack"))
        assertEquals("ar", store.string("oc.appLocale"))
        assertEquals(true, store.bool("oc.transcript.reasoningExpanded"))
        assertEquals(true, store.bool("oc.transcript.timestampsVisible"))
        assertEquals("large", store.string("oc.textSize"))
        assertEquals("teal", store.string("oc.appearanceAccent"))
        assertEquals("off", store.string("oc.glass"))

        val restarted = Preferences(fresh(TestDisk(disk.persisted())), app, backgroundScope)
        assertEquals(owner.value.value, restarted.value.value)

        owner.set(PreferenceChange.Locale(LocaleChoice.System))
        owner.set(PreferenceChange.Accent(null))
        assertEquals(LocaleChoice.System, owner.value.value.locale)
        assertNull(store.get("oc.appLocale"))
        assertNull(store.get("oc.appearanceAccent"))
        assertNull(LocaleChoice.of("de"), "an unsupported language is not a choice")
    }

    @Test
    fun `B-STATE-003 choosing Full removes the legacy celebrations key and keeps the other effect choices`() = runTest {
        val store = fresh()
        store.seed(
            "oc.effectsMotion" to "full",
            "oc.effectsCelebrations" to false,
            "oc.effectsGlowStyle" to "softRing",
            "oc.effectsCorners" to "sharp",
        )
        val owner = Preferences(store, app, backgroundScope)
        assertEquals(MotionLevel.Calm, owner.value.value.effects.motion)
        owner.set(PreferenceChange.SetEffects(owner.value.value.effects.copy(motion = MotionLevel.Full)))
        assertNull(store.get("oc.effectsCelebrations"))
        assertTrue(owner.value.value.effects.celebrations)
        assertEquals(GlowStyle.SoftRing, owner.value.value.effects.glowStyle)
        assertEquals(Corners.Sharp, owner.value.value.effects.fineTune.corners)
    }

    @Test
    fun `B-STATE-004 writes are serialized and listeners see only acknowledged values`() = runTest {
        val gated = GatedStore(fresh())
        val owner = Preferences(gated, app, backgroundScope)
        val seen = mutableListOf<AppearanceChoice>()
        backgroundScope.launch { owner.value.collect { seen += it.appearance } }
        runCurrent()
        val gate = CompletableDeferred<Unit>().also { gated.gate = it }
        val first = async { owner.set(PreferenceChange.Appearance(AppearanceChoice.Light)) }
        val second = async { owner.set(PreferenceChange.Appearance(AppearanceChoice.System)) }
        runCurrent()
        assertEquals(listOf<Map<String, Any?>>(mapOf("oc.appearance" to "light")), gated.calls)
        assertEquals(AppearanceChoice.Dark, owner.value.value.appearance)
        assertEquals(listOf(AppearanceChoice.Dark), seen)
        gate.complete(Unit)
        assertIs<Outcome.Ok<*>>(first.await())
        runCurrent()
        assertIs<Outcome.Ok<*>>(second.await())
        runCurrent()
        assertEquals(AppearanceChoice.System, owner.value.value.appearance)
        assertEquals(2L, owner.revision.value)
        assertEquals("system", gated.string("oc.appearance"))
        assertFalse(AppearanceChoice.Saver in seen)
        assertEquals(AppearanceChoice.System, seen.last())
    }

    @Test
    fun `B-STATE-004 a refused write never publishes the optimistic value and the next write works`() = runTest {
        val disk = TestDisk()
        val store = fresh(disk)
        store.seed("oc.appearance" to "dark")
        val owner = Preferences(store, app, backgroundScope)
        disk.refuse = { _, _ -> true }
        val refused = owner.set(PreferenceChange.Appearance(AppearanceChoice.Light))
        assertIs<Failure.StorageRefused>(assertIs<Outcome.Failed>(refused).failure)
        assertEquals(AppearanceChoice.Dark, owner.value.value.appearance)
        assertEquals("dark", store.string("oc.appearance"))
        assertEquals(0L, owner.revision.value)
        disk.refuse = null
        assertIs<Outcome.Ok<*>>(owner.set(PreferenceChange.Appearance(AppearanceChoice.System)))
        assertEquals(AppearanceChoice.System, owner.value.value.appearance)
    }

    @Test
    fun `B-STATE-004 a multi-key effects change is all-or-nothing when a later key is refused`() = runTest {
        val disk = TestDisk()
        val store = fresh(disk)
        store.seed("oc.effectsMotion" to "full")
        val owner = Preferences(store, app, backgroundScope)
        disk.refuse = { key, _ -> key == "oc.effectsActivityGlow" }
        val refused = owner.set(PreferenceChange.SetEffects(Effects(motion = MotionLevel.Off, activityGlow = false)))
        assertIs<Failure.StorageRefused>(assertIs<Outcome.Failed>(refused).failure)
        assertEquals(Effects.Defaults, owner.value.value.effects)
        assertEquals("full", store.string("oc.effectsMotion"))
        assertEquals("full", (disk.persisted()["flutter.oc.effectsMotion"] as? io.github.eslamasabry.oc.core.data.kv.RawPref.Str)?.value)
        assertEquals(0L, owner.revision.value)
    }

    @Test
    fun `B-STATE-004 a refused write cannot leak its optimistic value into a later save`() = runTest {
        val disk = TestDisk()
        val store = fresh(disk)
        store.seed("oc.appearance" to "dark")
        val owner = Preferences(store, app, backgroundScope)
        disk.throwing = true
        assertIs<Outcome.Failed>(owner.set(PreferenceChange.Appearance(AppearanceChoice.Light)))
        disk.throwing = false
        assertIs<Outcome.Ok<*>>(owner.set(PreferenceChange.ReasoningExpanded(true)))
        assertEquals(AppearanceChoice.Dark, owner.value.value.appearance)
        assertTrue(owner.value.value.reasoningExpanded)
        val reread = Preferences(fresh(TestDisk(disk.persisted())), app, backgroundScope)
        assertEquals(AppearanceChoice.Dark, reread.value.value.appearance)
    }

    @Test
    fun `B-KIT-002 the live background held off by a style persists and is cleared by the person's switch`() = runTest {
        val disk = TestDisk()
        val store = fresh(disk)
        val owner = Preferences(store, app, backgroundScope)
        val paper = Effects.Defaults.copy(surfaceStyle = SurfaceStyle.Paper, liveBackground = false, liveHeldByStyle = true)
        assertIs<Outcome.Ok<*>>(owner.set(PreferenceChange.SetEffects(paper)))
        assertEquals(true, store.bool("oc.effectsLiveHeldByStyle"))
        val restarted = Preferences(fresh(TestDisk(disk.persisted())), app, backgroundScope)
        assertTrue(restarted.value.value.effects.liveHeldByStyle)
        assertEquals(SurfaceStyle.Paper, restarted.value.value.effects.surfaceStyle)
        owner.set(PreferenceChange.SetEffects(paper.copy(liveBackground = true, liveHeldByStyle = false)))
        assertNull(store.get("oc.effectsLiveHeldByStyle"))
        assertEquals(true, store.bool("oc.effectsBackgroundOn"))
    }

    @Test
    fun `B-KIT-002 the reply glow is one choice and each style keeps its own depth and edges whatever was stored`() = runTest {
        val store = fresh()
        store.seed(
            "oc.effectsGlowColours" to "two",
            "oc.effectsGlowSpeed" to "slow",
            "oc.effectsDepth" to "raised",
            "oc.effectsEdges" to "none",
            "oc.effectsDensity" to "compact",
            "oc.effectsCorners" to "soft",
        )
        val owner = Preferences(store, app, backgroundScope)
        val effects = owner.value.value.effects
        assertEquals(Density.Compact, effects.fineTune.density, "Spacing is still the person's")
        assertEquals(Corners.Soft, effects.fineTune.corners)
        // A new style resets the knobs (and drops the never-read depth and edges with them).
        owner.set(PreferenceChange.SetEffects(effects.copy(surfaceStyle = SurfaceStyle.Soft)))
        assertEquals(FineTune.None, owner.value.value.effects.fineTune)
        for (key in listOf("oc.effectsCorners", "oc.effectsDensity", "oc.effectsDepth", "oc.effectsEdges")) {
            assertNull(store.get(key), key)
        }
        assertEquals(PrefValue.Str("two"), store.get("oc.effectsGlowColours"), "ignored keys are left alone")
    }

    @Test
    fun `B-SEC-010 revoking consent reaches device preference observers at once`() = runTest {
        val consent = FakeConsentAuthority()
        val owner = Preferences(fresh(), app, backgroundScope, consent)
        val before = owner.privacyEpoch.value
        // The consent store bumps its epoch synchronously, before its storage write is acknowledged.
        consent.mutableEpoch.value += 1
        assertTrue(owner.privacyEpoch.value > before)
    }

    @Test
    fun `B-SEC-010 one preferences instance shares the owner and the consent authority`() = runTest {
        val consent = FakeConsentAuthority()
        val owner = Preferences(fresh(), app, backgroundScope, consent)
        assertSame(consent, owner.consent)
        assertSame(consent.epoch, owner.privacyEpoch)
        assertEquals(DevicePreferences.Defaults, owner.value.value)
        assertEquals(AppearanceChoice.Dark, owner.appearance.value)
        assertEquals(LocaleChoice.System, owner.locale.value)
        assertEquals(Effects.Defaults, owner.value.value.effects)
        assertFalse(owner.saver.value)
        owner.set(PreferenceChange.Appearance(AppearanceChoice.Saver))
        assertTrue(owner.saver.value)
    }

    @Test
    fun `B-STATE-081 retired preference keys are dropped on load and nothing else is touched`() = runTest {
        val disk = TestDisk()
        val store = fresh(disk)
        store.seed(
            "oc.effectsLiveBackground" to true,
            "oc.glassLiquidActive" to true,
            "oc.glassLiquidStrikes" to 2L,
            "oc.glassLiquidOffUntil" to "123456",
            "oc.effectsGlass" to false,
            "oc.activeProfile" to "laptop",
        )
        disk.refuse = { _, _ -> true }
        assertIs<Outcome.Failed>(RetiredPreferences.drop(store, app))
        assertEquals(true, store.bool("oc.effectsLiveBackground"), "a refused drop stays for the next load")
        disk.refuse = null
        assertEquals(Outcome.Ok(4), RetiredPreferences.drop(store, app))
        for (key in RetiredPreferences.KEYS) assertNull(store.get(key), key)
        assertEquals("laptop", store.string("oc.activeProfile"))
        assertEquals(false, store.bool("oc.effectsGlass"))
        val commits = disk.commits.size
        assertEquals(Outcome.Ok(0), RetiredPreferences.drop(store, app))
        assertEquals(commits, disk.commits.size, "nothing to drop: no commit")
    }

    @Test
    fun `B-STATE-003 every owned key is registered as an app-wide shape`() {
        val shapes = K.SHAPES.map { it.prefix }.toSet()
        for (key in K.READ) assertTrue(key in shapes, key)
        assertTrue(K.SHAPES.none { it.prefix.split('.').size < 2 })
    }
}
