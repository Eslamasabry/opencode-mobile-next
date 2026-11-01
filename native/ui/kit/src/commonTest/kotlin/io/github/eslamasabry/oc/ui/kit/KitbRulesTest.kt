package io.github.eslamasabry.oc.ui.kit

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.kit.chip.FilterChipsRules
import io.github.eslamasabry.oc.ui.kit.frame.JumpPillRules
import io.github.eslamasabry.oc.ui.kit.frame.KitFadeEdges
import io.github.eslamasabry.oc.ui.kit.frame.KitJumpEdge
import io.github.eslamasabry.oc.ui.kit.frame.ScrollFadeRules
import io.github.eslamasabry.oc.ui.kit.input.SearchEscape
import io.github.eslamasabry.oc.ui.kit.input.SearchRules
import io.github.eslamasabry.oc.ui.kit.modal.KitModalMotionRules
import io.github.eslamasabry.oc.ui.kit.modal.SideSheetRules
import io.github.eslamasabry.oc.ui.kit.modal.SideSheetValue
import io.github.eslamasabry.oc.ui.kit.motion.KitMotionAnswer
import io.github.eslamasabry.oc.ui.kit.motion.KitMotionClass
import io.github.eslamasabry.oc.ui.kit.motion.KitMotionManifest
import io.github.eslamasabry.oc.ui.kit.motion.KitMotionRules
import io.github.eslamasabry.oc.ui.kit.motion.KitMotionSpecs
import io.github.eslamasabry.oc.ui.kit.motion.KitMotionState
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.RefreshPhase
import io.github.eslamasabry.oc.ui.kit.motion.RefreshRules
import io.github.eslamasabry.oc.ui.kit.motion.fadeThroughIn
import io.github.eslamasabry.oc.ui.kit.motion.fadeThroughOut
import io.github.eslamasabry.oc.ui.kit.motion.iconTurn
import io.github.eslamasabry.oc.ui.kit.motion.kitDimAlpha
import io.github.eslamasabry.oc.ui.kit.motion.KitDimLevel
import io.github.eslamasabry.oc.ui.kit.motion.pace
import io.github.eslamasabry.oc.ui.kit.motion.spatialFast
import io.github.eslamasabry.oc.ui.kit.motion.tipFade
import io.github.eslamasabry.oc.ui.kit.motion.travelOrNull
import io.github.eslamasabry.oc.ui.kit.nav.KitTab
import io.github.eslamasabry.oc.ui.kit.nav.TabArrow
import io.github.eslamasabry.oc.ui.kit.nav.TabBodyMotion
import io.github.eslamasabry.oc.ui.kit.nav.TabRules
import io.github.eslamasabry.oc.ui.kit.teaching.TipRules
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S2-KITB pure rules (plan 08 §3.6, §5.5, §5.9.2, §5.10, §5.15; plan 06 K2). Named after catalog rows. */
class KitbRulesTest {
    private fun specs(level: MotionLevel, scale: Float = 1f) = KitMotionSpecs(KitMotionState(level, debugTimeScale = scale))

    @Test
    fun `B-KIT-023 under Off every S2-KITB builder snaps`() {
        val off = specs(MotionLevel.Off)
        val all =
            listOf(
                off.spatialFast<Float>(), off.fadeThroughIn<Float>(), off.fadeThroughOut<Float>(), off.iconTurn<Float>(),
                off.tipFade<Float>(), off.pace<Float>(KitPace.Quick), off.pace<Float>(KitPace.Standard),
            )
        all.forEach { assertIs<SnapSpec<Float>>(it) }
        assertNull(off.travelOrNull<Float>())
    }

    @Test
    fun `B-KIT-023 Full and Calm builders carry the plan's numbers, and Calm does not travel`() {
        val full = specs(MotionLevel.Full)
        assertEquals(MotionTokens.QUICK, (full.pace<Float>(KitPace.Quick) as TweenSpec).durationMillis)
        assertEquals(MotionTokens.STANDARD, (full.pace<Float>(KitPace.Standard) as TweenSpec).durationMillis)
        assertEquals(MotionTokens.TIP_FADE, (full.tipFade<Float>() as TweenSpec).durationMillis)
        assertEquals(200, (full.iconTurn<Float>() as TweenSpec).durationMillis)
        val out = full.fadeThroughOut<Float>() as TweenSpec
        val inn = full.fadeThroughIn<Float>() as TweenSpec
        assertEquals(83, out.durationMillis, "fade-through: out over the first third of STANDARD")
        assertEquals(83, inn.delay)
        assertEquals(MotionTokens.STANDARD, inn.delay + inn.durationMillis)
        val fast = full.spatialFast<Float>() as SpringSpec
        assertEquals(MotionTokens.SPATIAL_FAST_K, fast.stiffness)
        assertEquals(MotionTokens.SPATIAL_ZETA, fast.dampingRatio)
        assertIs<SpringSpec<Float>>(full.travelOrNull<Float>())
        assertNull(specs(MotionLevel.Calm).travelOrNull<Float>(), "Calm turns small travels into fades")
        assertEquals(MotionTokens.QUICK * 2, (specs(MotionLevel.Full, 2f).pace<Float>(KitPace.Quick) as TweenSpec).durationMillis)
    }

    @Test
    fun `B-KIT-054 the reduced-motion matrix as data, class by class`() {
        val r = KitMotionRules
        for (cls in listOf(KitMotionClass.Fade, KitMotionClass.Fold, KitMotionClass.Transform, KitMotionClass.Settle)) {
            assertEquals(KitMotionAnswer.Moves, r.answer(cls, MotionLevel.Full))
            assertEquals(KitMotionAnswer.Moves, r.answer(cls, MotionLevel.Calm))
            assertEquals(KitMotionAnswer.Instant, r.answer(cls, MotionLevel.Off))
        }
        assertEquals(KitMotionAnswer.Fades, r.answer(KitMotionClass.Travel, MotionLevel.Calm))
        assertEquals(KitMotionAnswer.Instant, r.answer(KitMotionClass.Travel, MotionLevel.Off))
        assertEquals(KitMotionAnswer.Moves, r.answer(KitMotionClass.ProgressLoop, MotionLevel.Calm))
        assertEquals(KitMotionAnswer.Still, r.answer(KitMotionClass.ProgressLoop, MotionLevel.Off))
        assertEquals(KitMotionAnswer.Still, r.answer(KitMotionClass.AmbientLoop, MotionLevel.Calm))
        KitMotionLevels.forEach { assertEquals(KitMotionAnswer.Follows, r.answer(KitMotionClass.Follow, it)) }
    }

    @Test
    fun `B-KIT-054 every S2-KITB motion part is registered with exactly one class`() {
        val expected =
            setOf(
                "KitSwap", "KitSwap.count", "KitSpin", "KitReveal", "KitEntrance", "KitPresence", "KitAnimatedRows",
                "KitAnimatedValue", "KitDim", "KitRefresh.pull", "KitRefresh.breath", "KitTip", "KitJumpPill",
                "KitTabSwitcher", "KitTabStrip.indicator", "KitSearchField.clear", "KitScrollFade", "KitSheetStep",
                "KitSideSheet.drag", "KitSideSheet.settle", "KitModalMotion.dialog", "KitModalMotion.menu",
                "KitModalMotion.sheet",
            )
        assertEquals(expected, KitMotionManifest.parts.keys)
        assertEquals(KitMotionClass.Travel, KitMotionManifest.parts.getValue("KitSwap.count"))
        assertEquals(KitMotionClass.AmbientLoop, KitMotionManifest.parts.getValue("KitRefresh.breath"))
        assertEquals(KitMotionClass.Follow, KitMotionManifest.parts.getValue("KitRefresh.pull"))
    }

    @Test
    fun `B-KIT-023 KitDim dims to the stale or disabled alpha and never to zero`() {
        assertEquals(1f, kitDimAlpha(dimmed = false, level = KitDimLevel.Disabled))
        assertEquals(0.38f, kitDimAlpha(dimmed = true, level = KitDimLevel.Disabled))
        assertEquals(0.60f, kitDimAlpha(dimmed = true, level = KitDimLevel.Stale))
    }

    @Test
    fun `B-KIT-054 refresh phases - pull, arm, release before armed returns, refresh, done`() {
        val r = RefreshRules
        assertEquals(RefreshPhase.Idle, r.next(RefreshPhase.Idle, 0f, refreshing = false))
        assertEquals(RefreshPhase.Pulling, r.next(RefreshPhase.Idle, 0.4f, refreshing = false))
        assertEquals(RefreshPhase.Armed, r.next(RefreshPhase.Pulling, 1f, refreshing = false))
        assertEquals(RefreshPhase.Pulling, r.next(RefreshPhase.Armed, 0.9f, refreshing = false), "pulled back under the line")
        assertEquals(RefreshPhase.Idle, r.next(RefreshPhase.Pulling, 0f, refreshing = false), "released before armed: returns")
        assertEquals(RefreshPhase.Refreshing, r.next(RefreshPhase.Armed, 1f, refreshing = true))
        assertEquals(RefreshPhase.Done, r.next(RefreshPhase.Refreshing, 0f, refreshing = false))
        assertEquals(RefreshPhase.Done, r.next(RefreshPhase.Done, 0f, refreshing = false), "done holds until its fade ends")
        assertEquals(RefreshPhase.Idle, r.next(RefreshPhase.Done, 0f, refreshing = false, doneSettled = true))
        assertEquals(0f, r.bracketStroke(0f))
        assertEquals(1f, r.bracketStroke(1.6f), "the brackets are whole once armed")
        assertTrue(r.bracketStroke(0.5f) > 0.5f, "Flutter easeOutCubic: front-loaded")
        assertTrue(r.sparkShows(RefreshPhase.Armed) && r.sparkShows(RefreshPhase.Refreshing))
        assertFalse(r.sparkShows(RefreshPhase.Pulling))
    }

    @Test
    fun `B-KIT-054 refresh never loops under Off or Calm, and breathes only while refreshing at Full`() {
        val full = KitMotionState(MotionLevel.Full)
        assertTrue(RefreshRules.breathes(full, RefreshPhase.Refreshing))
        assertFalse(RefreshRules.breathes(full, RefreshPhase.Armed))
        assertFalse(RefreshRules.breathes(KitMotionState(MotionLevel.Calm), RefreshPhase.Refreshing))
        assertFalse(RefreshRules.breathes(KitMotionState(MotionLevel.Off), RefreshPhase.Refreshing))
        assertFalse(RefreshRules.breathes(KitMotionState(MotionLevel.Full, stillFrame = true), RefreshPhase.Refreshing))
    }

    @Test
    fun `B-KIT-030 a search earns its place above 8 items, and Esc clears before it leaves`() {
        assertFalse(SearchRules.worthShowing(8))
        assertTrue(SearchRules.worthShowing(9))
        assertEquals(SearchEscape.Clear, SearchRules.escape("opus"))
        assertEquals(SearchEscape.Leave, SearchRules.escape(""))
        assertEquals(MotionTokens.TYPING_SETTLE.toLong(), SearchRules.SETTLE_MS)
    }

    @Test
    fun `B-KIT-050 a strip holds 2 to 8 tabs and the bodies must match`() {
        val tabs = listOf(KitTab("A"), KitTab("B"))
        assertTrue(TabRules.validStrip(tabs.size))
        assertFalse(TabRules.validStrip(1))
        assertFalse(TabRules.validStrip(9))
        assertTrue(TabRules.bodiesMatch(tabs.size, 2))
        assertFalse(TabRules.bodiesMatch(tabs.size, 3))
    }

    @Test
    fun `B-KIT-050 counts loading show no number, never 0`() {
        assertNull(TabRules.countWords(null))
        assertEquals("0", TabRules.countWords(0))
        assertEquals("12", TabRules.countWords(12))
    }

    @Test
    fun `B-KIT-050 arrows move in reading order, mirrored in RTL, and stop at the ends`() {
        assertEquals(1, TabRules.arrow(0, TabArrow.Right, size = 3, rtl = false))
        assertEquals(0, TabRules.arrow(1, TabArrow.Right, size = 3, rtl = true))
        assertEquals(2, TabRules.arrow(2, TabArrow.Right, size = 3, rtl = false))
        assertEquals(0, TabRules.arrow(0, TabArrow.Left, size = 3, rtl = false))
        assertEquals(1, TabRules.arrow(0, TabArrow.Left, size = 3, rtl = true))
    }

    @Test
    fun `B-KIT-050 body switch is fade-through at Full, cross-fade at Calm, instant at Off`() {
        assertEquals(TabBodyMotion.FadeThrough, TabRules.bodyMotion(MotionLevel.Full))
        assertEquals(TabBodyMotion.CrossFade, TabRules.bodyMotion(MotionLevel.Calm))
        assertEquals(TabBodyMotion.Instant, TabRules.bodyMotion(MotionLevel.Off))
        assertTrue(TabRules.indicatorMoves(MotionLevel.Full))
        assertFalse(TabRules.indicatorMoves(MotionLevel.Calm))
    }

    @Test
    fun `B-KIT-023 the jump pill shows past 480 dp from the latest, and rises toward its own edge`() {
        assertFalse(JumpPillRules.shows(distanceFromEdgePx = 480f * 2.625f, density = 2.625f))
        assertTrue(JumpPillRules.shows(distanceFromEdgePx = 481f * 2.625f, density = 2.625f))
        assertEquals(MotionTokens.ENTRANCE_RISE_DP, JumpPillRules.riseDp(KitJumpEdge.Bottom, KitMotionState(MotionLevel.Full)))
        assertEquals(-MotionTokens.ENTRANCE_RISE_DP, JumpPillRules.riseDp(KitJumpEdge.Top, KitMotionState(MotionLevel.Full)))
        assertEquals(0, JumpPillRules.riseDp(KitJumpEdge.Bottom, KitMotionState(MotionLevel.Calm)))
    }

    @Test
    fun `B-KIT-023 a line fades only at an edge with more past it, mirrored in RTL`() {
        assertEquals(KitFadeEdges(start = false, end = false), ScrollFadeRules.edges(value = 0, max = 0))
        assertEquals(KitFadeEdges(start = false, end = true), ScrollFadeRules.edges(value = 0, max = 300))
        assertEquals(KitFadeEdges(start = true, end = true), ScrollFadeRules.edges(value = 120, max = 300))
        assertEquals(KitFadeEdges(start = true, end = false), ScrollFadeRules.edges(value = 300, max = 300))
        val edges = KitFadeEdges(start = true, end = false)
        assertEquals(true to false, ScrollFadeRules.leftRight(edges, rtl = false))
        assertEquals(false to true, ScrollFadeRules.leftRight(edges, rtl = true))
        // The outer third is fully clear and the fade never takes more than half the line.
        val stops = ScrollFadeRules.stops(widthPx = 1000f, fadePx = 120f)
        assertEquals(listOf(0f, 0.04f, 0.12f, 0.88f, 0.96f, 1f), stops.map { (it * 1000).roundToInt() / 1000f })
        assertEquals(0.5f, ScrollFadeRules.stops(widthPx = 100f, fadePx = 120f)[2])
        assertEquals(16 + 32, FilterChipsRules.fadeWidthDp(gutter = 16))
    }

    @Test
    fun `B-NAV-038 a tip sits above an anchor below the top quarter, at most 280 wide, rising only at Full`() {
        assertTrue(TipRules.above(anchorTopPx = 900f, windowHeightPx = 2400f))
        assertFalse(TipRules.above(anchorTopPx = 400f, windowHeightPx = 2400f))
        assertEquals(280, TipRules.MAX_WIDTH_DP)
        assertEquals(MotionTokens.TIP_RISE_DP, TipRules.riseDp(KitMotionState(MotionLevel.Full)))
        assertEquals(0, TipRules.riseDp(KitMotionState(MotionLevel.Calm)))
        assertEquals(MotionTokens.TIP_HOLD.toLong(), TipRules.HOLD_MS)
    }

    @Test
    fun `B-KIT-032 the side sheet hides past its end edge, mirrored in RTL, and previews 10 percent`() {
        assertEquals(400f, SideSheetRules.hiddenOffset(widthPx = 400f, rtl = false))
        assertEquals(-400f, SideSheetRules.hiddenOffset(widthPx = 400f, rtl = true))
        assertEquals(20f, SideSheetRules.predictiveShift(progress = 0.5f, widthPx = 400f, rtl = false))
        assertEquals(-40f, SideSheetRules.predictiveShift(progress = 1f, widthPx = 400f, rtl = true))
        assertEquals(SideSheetValue.Shown, SideSheetRules.settle(offsetPx = 100f, widthPx = 400f, velocityPx = 0f, rtl = false))
        assertEquals(SideSheetValue.Hidden, SideSheetRules.settle(offsetPx = 250f, widthPx = 400f, velocityPx = 0f, rtl = false))
        assertEquals(SideSheetValue.Hidden, SideSheetRules.settle(offsetPx = 40f, widthPx = 400f, velocityPx = 2000f, rtl = false))
        assertEquals(SideSheetValue.Hidden, SideSheetRules.settle(offsetPx = -40f, widthPx = 400f, velocityPx = -2000f, rtl = true))
        assertEquals(SideSheetValue.Shown, SideSheetRules.settle(offsetPx = 40f, widthPx = 400f, velocityPx = -2000f, rtl = false))
        assertEquals(0f, SideSheetRules.clampDrag(offsetPx = -30f, widthPx = 400f, rtl = false), "never past its resting place")
    }

    @Test
    fun `B-KIT-016 B-KIT-022 dialogs rise and menus drop only at Full, and never scale`() {
        assertEquals(MotionTokens.ENTRANCE_RISE_DP, KitModalMotionRules.dialogRiseDp(KitMotionState(MotionLevel.Full)))
        assertEquals(0, KitModalMotionRules.dialogRiseDp(KitMotionState(MotionLevel.Calm)))
        assertEquals(-MotionTokens.MENU_DROP_DP, KitModalMotionRules.menuDropDp(KitMotionState(MotionLevel.Full), above = false))
        assertEquals(MotionTokens.MENU_DROP_DP, KitModalMotionRules.menuDropDp(KitMotionState(MotionLevel.Full), above = true))
        assertEquals(0, KitModalMotionRules.menuDropDp(KitMotionState(MotionLevel.Off), above = false))
        assertEquals(1f, KitModalMotionRules.INITIAL_SCALE, "MOT-2: modals never scale in")
    }

    private companion object {
        val KitMotionLevels = MotionLevel.entries
    }
}
