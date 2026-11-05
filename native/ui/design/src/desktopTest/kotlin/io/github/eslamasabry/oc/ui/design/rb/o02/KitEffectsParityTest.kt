package io.github.eslamasabry.oc.ui.design.rb.o02

import kotlin.test.*
import io.github.eslamasabry.oc.ui.design.effects.*
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.*
import io.github.eslamasabry.oc.ui.design.text.AppTextSize
import io.github.eslamasabry.oc.ui.design.tokens.Density

class KitEffectsParityTest {
    @Test fun defaultsAndStoredNames() {
        val defaults = KitAppearance.Default
        assertEquals(MotionLevel.Full, defaults.motion)
        assertTrue(defaults.celebrations); assertTrue(defaults.haptics); assertTrue(defaults.activityGlow)
        assertFalse(defaults.liveBackground); assertFalse(defaults.liveHeldByStyle); assertFalse(defaults.saver)
        assertEquals("aurora", defaults.liveStyle)
        assertEquals(Style.Solid, defaults.style); assertTrue(defaults.fineTune.isNone)
        assertEquals(listOf("Full", "Calm", "Off"), MotionLevel.entries.map { it.name })
        assertEquals(listOf("smallest", "small", "normal", "large"), AppTextSize.entries.map { it.storageName })
        assertEquals(listOf(.85f, .92f, 1f, 1.15f), AppTextSize.entries.map { it.factor })
        assertEquals(listOf("solid", "glass", "soft", "brutal", "paper", "signal"), Style.entries.map { it.storageName })
    }
    @Test fun styleChangeResetsFineTuneAndSaverDoesNotMutateStoredChoices() {
        val original = KitAppearance(style = Style.Soft, fineTune = FineTuneChoice(Corners.Sharp, Density.Compact), liveBackground = true)
        for (style in Style.entries) assertTrue(original.withSurfaceStyle(style).fineTune.isNone)
        val saver = original.underSaver
        assertTrue(saver.saver); assertEquals(MotionLevel.Off, saver.motion)
        assertFalse(saver.celebrations); assertFalse(saver.liveBackground); assertEquals(Style.Solid, saver.style)
        assertTrue(saver.fineTune.isNone); assertEquals(original.haptics, saver.haptics)
        assertEquals(Style.Soft, original.style); assertTrue(original.liveBackground)
    }
    @Test fun backgroundSwitchReleasesOnlyChangedHold() {
        val held = KitAppearance(liveHeldByStyle = true)
        assertTrue(held.withLiveBackground(false).liveHeldByStyle)
        assertFalse(held.withLiveBackground(true).liveHeldByStyle)
    }
}
