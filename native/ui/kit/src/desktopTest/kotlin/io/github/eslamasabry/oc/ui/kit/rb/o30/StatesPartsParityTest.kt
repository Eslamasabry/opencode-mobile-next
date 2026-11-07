package io.github.eslamasabry.oc.ui.kit.rb.o30

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitCurves
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnFrame
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnInk
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnScene
import io.github.eslamasabry.oc.ui.kit.scene.StatesParts
import io.github.eslamasabry.oc.ui.kit.scene.StatesParts.wash
import io.github.eslamasabry.oc.ui.kit.scene.sceneRect
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every constant and rule of lib/ui/kit/scenes/states_parts.dart, and the kit_illustration.dart contract it uses. */
class StatesPartsParityTest {
    init {
        ensureGraphics()
    }

    @Test fun `the drawing pens are kit_illustration's 5 and 2_5 units`() {
        assertEquals(5f, KitDrawnInk.STROKE)
        assertEquals(2.5f, KitDrawnInk.HAIRLINE)
    }

    @Test fun `a scene's default box is 120 by 120 and it does not mirror`() {
        val scene = object : KitDrawnScene {
            override fun DrawScope.paint(frame: KitDrawnFrame) = Unit
        }
        assertEquals(Size(120f, 120f), scene.box)
        assertEquals(false, scene.mirrorsInRtl)
    }

    @Test fun `the palette maps theme roles as KitPalette_fromRoles does`() {
        val p = testPalette
        assertEquals(testColors.accent, p.accent)
        assertEquals(testColors.accent.copy(alpha = FlutterKitTokens.SCENE_WASH_ALPHA), p.accentSoft)
        assertEquals(testColors.text1, p.ink)
        assertEquals(testColors.text2, p.muted)
        assertEquals(testColors.text3, p.line)
        assertEquals(testColors.surface3, p.surface)
        assertEquals(testColors.success, p.success)
        assertEquals(testColors.attention, p.warning)
        assertEquals(testColors.text1, p.failure, "no red for failures (B2 interim)")
        assertEquals(testColors.accent, p.progress)
    }

    @Test fun `interval clamps outside its window and eases inside it`() {
        assertEquals(0f, KitDrawnInk.interval(.2f, .2f, .6f))
        assertEquals(0f, KitDrawnInk.interval(.1f, .2f, .6f))
        assertEquals(1f, KitDrawnInk.interval(.6f, .2f, .6f))
        assertEquals(1f, KitDrawnInk.interval(.9f, .2f, .6f))
        // KitMotion.enter is Cubic(.215, .61, .355, 1): about .876 at the midpoint.
        assertNear(.876f, KitDrawnInk.interval(.4f, .2f, .6f), .005f, "enter at half")
        // Steady is linear.
        assertNear(.5f, KitDrawnInk.interval(.4f, .2f, .6f, FlutterKitCurves.steady), what = "steady at half")
        // Land overshoots past 1 inside the window.
        assertTrue(KitDrawnInk.interval(.7f, 0f, 1f, FlutterKitCurves.land) > 1f)
        // Emphasized (a three-point cubic) passes through its midpoint (.166666, .4).
        val emphasized = KitDrawnInk.interval(.166666f, 0f, 1f, FlutterKitCurves.emphasized)
        assertNear(.4f, emphasized, .01f, "emphasized midpoint")
    }

    @Test fun `wave goes 0 to 1 to 0 over one loop, shifted by its phase`() {
        assertNear(0f, KitDrawnInk.wave(0f))
        assertNear(1f, KitDrawnInk.wave(.5f))
        assertNear(.5f, KitDrawnInk.wave(.25f))
        assertNear(.5f, KitDrawnInk.wave(0f, .25f))
        assertNear(0f, KitDrawnInk.wave(1f))
    }

    @Test fun `fade multiplies a colour's own alpha, clamped`() {
        val half = Color(0x80FF0000)
        assertNear(half.alpha * .5f, KitDrawnInk.fade(half, .5f).alpha)
        assertNear(half.alpha, KitDrawnInk.fade(half, 2f).alpha)
        assertNear(0f, KitDrawnInk.fade(half, -1f).alpha)
    }

    @Test fun `the wash sits at 60,62 radius 44 and a sheet folds 14 with 6 corners`() {
        assertEquals(Offset(60f, 62f), StatesParts.WASH_CENTRE)
        assertEquals(44f, StatesParts.WASH_RADIUS)
        assertEquals(14f, StatesParts.SHEET_FOLD)
        assertEquals(6f, StatesParts.SHEET_RADIUS)
    }

    @Test fun `the wash grows from 82 percent over the first 35 percent of the entrance`() {
        val scene = object : KitDrawnScene {
            override fun DrawScope.paint(frame: KitDrawnFrame) = wash(frame)
        }
        assertEquals(0, inked(paint(scene, frameAt(0f))), "nothing before the wash starts")
        val grown = inked(paint(scene, frameAt(1f)))
        val partial = inked(paint(scene, frameAt(.1f)))
        assertTrue(partial in 1 until grown, "the wash is smaller while it grows ($partial < $grown)")
        // Fully grown: a 44-unit circle at 2 px per unit is about pi * 88^2 pixels.
        val area = (PI * 88 * 88).toFloat()
        assertNear(area, grown.toFloat(), area * .03f, "grown wash area")
    }

    @Test fun `a sheet spans its rect and its fold is the top end corner`() {
        val rect = Rect(26f, 32f, 80f, 102f)
        assertBounds(rect, StatesParts.sheet(rect))
        assertBounds(Rect(80f - 14f, 32f, 80f, 32f + 14f), StatesParts.fold(rect))
        assertBounds(Rect(92f - 12f, 20f, 92f, 32f), StatesParts.fold(Rect(44f, 20f, 92f, 84f), fold = 12f))
    }

    @Test fun `a spark reaches size from its centre in four points and scales with t`() {
        assertBounds(Rect(92f, 30f, 108f, 46f), StatesParts.sparkPath(Offset(100f, 38f), 8f, 1f))
        assertBounds(Rect(96f, 34f, 104f, 42f), StatesParts.sparkPath(Offset(100f, 38f), 8f, .5f))
    }

    @Test fun `ticks run from inner toward outer at their angles, clockwise from the right`() {
        val lines = StatesParts.tickLines(Offset(63f, 61f), listOf(-90f, 0f), 11f, 18f, 1f)
        assertNearOffset(Offset(63f, 50f), lines[0].first)
        assertNearOffset(Offset(63f, 43f), lines[0].second)
        assertNearOffset(Offset(74f, 61f), lines[1].first)
        assertNearOffset(Offset(81f, 61f), lines[1].second)
        val half = StatesParts.tickLines(Offset(63f, 61f), listOf(0f), 11f, 18f, .5f)
        assertNearOffset(Offset(77.5f, 61f), half[0].second)
    }

    @Test fun `Rect_fromCenter is centred`() {
        assertEquals(Rect(-8.5f, -6.5f, 8.5f, 6.5f), sceneRect(Offset.Zero, 17f, 13f))
    }

    private fun assertBounds(expected: Rect, path: Path) {
        val b = path.getBounds()
        assertNear(expected.left, b.left, .05f, "left")
        assertNear(expected.top, b.top, .05f, "top")
        assertNear(expected.right, b.right, .05f, "right")
        assertNear(expected.bottom, b.bottom, .05f, "bottom")
    }

    private fun assertNearOffset(expected: Offset, actual: Offset) {
        assertNear(expected.x, actual.x, what = "x")
        assertNear(expected.y, actual.y, what = "y")
    }
}
