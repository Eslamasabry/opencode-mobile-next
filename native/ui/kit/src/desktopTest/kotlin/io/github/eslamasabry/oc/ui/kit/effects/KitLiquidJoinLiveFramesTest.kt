package io.github.eslamasabry.oc.ui.kit.effects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Live glass, first build, in pictures ([LiveGlassFrames]): the bar's ⋯ menu joined to the bar, opened, closed 120 ms
 * later and reopened 100 ms later over code and a bright picture in dark Glass, drawn frame by frame through the join
 * shader with Android's half backdrop resolution. With the Glass lab's "Lens through motion" on, no measured value of any
 * frame jumps against its neighbours: the bar away from the menu (its light and its sharpness: the body's blur and
 * resolution), the menu's rows and where the glass is. Today's join, measured by the same ruler, steps (this is the
 * check failing without the live renderer).
 */
class KitLiquidJoinLiveFramesTest {
    private val scene = LiveGlassFrames.Scene("turns", listOf(0L to true, 120L to false, 220L to true), 720)

    private val rulers: List<Triple<String, Double, (LiveGlassFrames.Frame) -> Double>> =
        listOf(
            Triple("bar light", BAR_LIGHT_STEP, { f: LiveGlassFrames.Frame -> f.barLight }),
            Triple("bar sharpness", BAR_SHARP_STEP, { f: LiveGlassFrames.Frame -> f.barSharp }),
            Triple("menu rows", MENU_STEP, { f: LiveGlassFrames.Frame -> f.menuMiddle }),
            Triple("column sharpness", BAR_SHARP_STEP, { f: LiveGlassFrames.Frame -> f.columnSharp }),
            Triple("glass bottom", GLASS_STEP_DP, { f: LiveGlassFrames.Frame -> f.glassBottomDp }),
            Triple("glass side", GLASS_STEP_DP, { f: LiveGlassFrames.Frame -> f.glassLeftDp }),
        )

    private fun stepsOf(frames: List<LiveGlassFrames.Frame>): Map<String, List<Pair<Int, Double>>> =
        rulers.associate { (name, tol, value) -> name to LiveGlassFrames.steps(frames, tol, value) }.filterValues { it.isNotEmpty() }

    @Test
    fun `B-GLASS-LIVE-010 the bar's menu turned round mid-way - no frame steps live, today's join steps`() {
        val live = LiveGlassFrames.run(LiveGlassFrames.Variant.Live, scene, DENSITY, keepImages = false)
        assertTrue(live.count { it.joined } > live.size / 2, "the menu is joined to the bar for most of the scene")
        assertEquals(emptyMap(), stepsOf(live), "live: steps (frame, change)")
        assertEquals(emptyList(), LiveGlassFrames.stalls(live), "live: stalls (first frame, frames, kind)")
        val today = LiveGlassFrames.run(LiveGlassFrames.Variant.Today, scene, DENSITY, keepImages = false)
        assertTrue(stepsOf(today).isNotEmpty() || LiveGlassFrames.stalls(today).isNotEmpty(), "the ruler finds today's steps or stalls")
    }

    private companion object {
        const val DENSITY = 1f
        const val BAR_LIGHT_STEP = .3
        const val BAR_SHARP_STEP = .15
        const val MENU_STEP = .5
        const val GLASS_STEP_DP = 1.5
    }
}
