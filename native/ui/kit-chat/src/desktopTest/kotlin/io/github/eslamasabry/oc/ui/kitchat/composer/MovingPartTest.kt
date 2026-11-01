package io.github.eslamasabry.oc.ui.kitchat.composer

import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.motion.MotionSignals
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.motion.resolveMotion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Plan 08 §3.6 `MovingPartTest` (MOT-15, Flutter `kit_composer_moving_part_test.dart`) and the glow's level table. */
class MovingPartTest {
    @Test
    fun `MOT-15 one moving part - the glow travels under Full, the voice ring only when the glow is off`() {
        assertEquals(MovingPart.Glow, movingPart(MotionLevel.Full, glowOn = true))
        assertEquals(MovingPart.StopRing, movingPart(MotionLevel.Full, glowOn = false))
        for (level in listOf(MotionLevel.Calm, MotionLevel.Off)) {
            assertEquals(MovingPart.None, movingPart(level, glowOn = true), "$level")
            assertEquals(MovingPart.None, movingPart(level, glowOn = false), "$level")
        }
    }

    @Test
    fun `B-CHAT-006 the glow is on and classic by default and a stored off stays off`() {
        assertEquals(KitGlowChoice.Classic, KitGlowChoice.of())
        assertEquals(KitGlowChoice.Classic, KitGlowChoice.of(on = true, soft = false))
        assertEquals(KitGlowChoice.Soft, KitGlowChoice.of(on = true, soft = true))
        assertEquals(KitGlowChoice.Off, KitGlowChoice.of(on = false, soft = true))
        assertEquals(KitGlowChoice.Off, KitGlowChoice.of(on = false))
    }

    @Test
    fun `B-KIT-002 the glow is one choice with the app's own speed - 3600 ms classic, 8000 ms soft`() {
        val classic = glowPlan(KitGlowChoice.Classic, MotionLevel.Full, saver = false, stillFrame = false)
        val soft = glowPlan(KitGlowChoice.Soft, MotionLevel.Full, saver = false, stillFrame = false)
        assertEquals(MotionTokens.GLOW_CLASSIC_LAP, classic.lapMillis)
        assertEquals(MotionTokens.GLOW_SOFT_LAP, soft.lapMillis)
        assertTrue(classic.halo && !soft.halo, "Classic has the halo, Soft has its wash")
        assertEquals(GlowDraw.None, glowPlan(KitGlowChoice.Off, MotionLevel.Full, false, false).draw)
    }

    @Test
    fun `glow levels - Full travels, Calm is still and lit, Off draws nothing, Saver draws a hairline`() {
        val full = glowPlan(KitGlowChoice.Classic, MotionLevel.Full, saver = false, stillFrame = false)
        assertEquals(GlowDraw.Travel, full.draw)
        assertTrue(full.loops)
        val calm = glowPlan(KitGlowChoice.Classic, MotionLevel.Calm, saver = false, stillFrame = false)
        assertEquals(GlowDraw.Still, calm.draw)
        assertFalse(calm.loops)
        assertEquals(1f, calm.strength)
        assertEquals(0.5f, glowPlan(KitGlowChoice.Soft, MotionLevel.Calm, false, false).strength, "Soft: half strength")
        assertEquals(GlowDraw.None, glowPlan(KitGlowChoice.Classic, MotionLevel.Off, saver = false, stillFrame = false).draw)
        // Saver forces motion Off, and still shows one hairline while a reply runs.
        val saver = glowPlan(KitGlowChoice.Classic, MotionLevel.Off, saver = true, stillFrame = false)
        assertEquals(GlowDraw.Hairline, saver.draw)
        assertFalse(saver.halo || saver.loops)
        assertEquals(GlowDraw.None, glowPlan(KitGlowChoice.Off, MotionLevel.Off, saver = true, stillFrame = false).draw)
        // A still frame keeps the travelling look at its frame phase and never runs the clock.
        val still = glowPlan(KitGlowChoice.Classic, MotionLevel.Full, saver = false, stillFrame = true)
        assertEquals(GlowDraw.Travel, still.draw)
        assertFalse(still.loops)
        assertEquals(0.57f, still.framePhase)
    }

    @Test
    fun `MOT-13 battery saver and a hot phone keep the glow still and lit, and remove animations stops it`() {
        fun level(powerSave: Boolean = false, heat: Boolean = false, scale: Float = 1f) =
            resolveMotion(MotionSignals(scale, powerSave, heat), MotionLevel.Full, saver = false)
        assertEquals(GlowDraw.Still, glowPlan(KitGlowChoice.Classic, level(powerSave = true), false, false).draw)
        assertEquals(GlowDraw.Still, glowPlan(KitGlowChoice.Classic, level(heat = true), false, false).draw)
        assertEquals(GlowDraw.None, glowPlan(KitGlowChoice.Classic, level(scale = 0f), false, false).draw)
        assertEquals(MovingPart.None, movingPart(level(powerSave = true), glowOn = true))
    }

    @Test
    fun `the lap clock wraps and runs at half speed when asked`() {
        val lap = MotionTokens.GLOW_CLASSIC_LAP
        assertEquals(0f, lapPhase(0, lap))
        assertEquals(0.5f, lapPhase(1_800_000_000L, lap), 1e-4f)
        assertEquals(0.5f, lapPhase(5_400_000_000L, lap), 1e-4f) // 1.5 laps wrap to half a lap
        assertEquals(0.25f, lapPhase(1_800_000_000L, lap, speed = 0.5f), 1e-4f)
        assertEquals(0f, lapPhase(1_000_000_000L, lap, speed = 0f))
    }

    @Test
    fun `the classic halo breathes twice per lap between 0_15 and 0_35 and holds 0_25 when still`() {
        assertEquals(0.15f, haloAlpha(0f), 1e-4f)
        assertEquals(0.35f, haloAlpha(0.25f), 1e-4f)
        assertEquals(0.15f, haloAlpha(0.5f), 1e-4f)
        assertEquals(0.35f, haloAlpha(0.75f), 1e-4f)
        assertEquals(0.25f, haloAlpha(null))
    }

    @Test
    fun `X6 the glow fades over 450 ms on the enter curve`() {
        assertEquals(0f, fadeProgress(0, 450f))
        assertTrue(fadeProgress(225_000_000L, 450f) > 0.8f, "easeOutCubic is past 0.8 at half time")
        assertEquals(1f, fadeProgress(450_000_000L, 450f))
        assertEquals(1f, fadeProgress(900_000_000L, 450f))
        assertEquals(1f, fadeProgress(0, 0f), "a zero-length fade is done")
    }
}
