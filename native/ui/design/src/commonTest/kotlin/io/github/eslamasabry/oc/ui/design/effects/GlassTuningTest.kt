package io.github.eslamasabry.oc.ui.design.effects

import io.github.eslamasabry.oc.ui.design.style.GlassLook
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Glass lab's numbers (2026-11-13): an untouched tuning is today's glass, and the copied block reads back. */
class GlassTuningTest {
    private val both = listOf(true, false)

    @Test
    fun `B-GLASS-LAB-001 the shipped numbers are today's recipes and constants`() {
        for (kind in GlassPaneKind.entries) for (dark in both) {
            val k = GlassTuning.shipped(kind, dark)
            val r = LiquidEdge.recipe(kind.role, dark)
            assertEquals(r.blurDp, k.blurDp, "$kind blur")
            assertEquals(r.refractionDp, k.lensStrengthDp, "$kind bend")
            assertEquals(r.rimMaxDp, k.lensWidthDp, "$kind lens width")
            assertEquals(r.rimShare, k.lensShare, "$kind lens cap")
            assertEquals(r.tint, k.tint, "$kind tint")
            assertEquals(r.saturation, k.saturation)
            assertEquals(r.contrast, k.contrast)
            assertEquals(r.brightness, k.brightness)
            assertEquals(r.hold.chroma, k.colourLimit)
            assertEquals(if (r.hold.knee) 1f else 0f, k.soften)
            assertEquals(if (r.depth) 1f else 0f, k.lensMiddle)
            assertEquals(r.shadowAlpha, k.shadow)
            assertEquals(RimLight.FLOOR, k.rimFloor)
            assertEquals(RimLight.FALLOFF, k.rimFocus)
            assertEquals(LiquidJoin.KNEE, k.softenKnee)
            assertEquals(LiquidJoin.KNEE_SLOPE, k.softenKeep)
            assertEquals(if (dark) GlassLook.Default.liquidRimDark else GlassTuning.LIGHT_RIM_ALPHA, k.rimBrightness)
            assertEquals(45f, k.lightDeg)
            assertEquals(GlassTuning.RIM_WIDTH_DP, k.rimWidthDp)
            listOf(k.dispersion, k.innerShadow, k.tintLightness).forEach {
                assertEquals(0f, it, "$kind off by default")
            }
            assertEquals(GlassTuning.SHIPPED_BEND_SHAPE, k.bendShape, "$kind dome bend")
            val wantSpec = if (dark) GlassTuning.SHIPPED_SPECULAR_DARK else GlassTuning.SHIPPED_SPECULAR_LIGHT
            val wantSheen = if (dark) GlassTuning.SHIPPED_SHEEN_DARK else GlassTuning.SHIPPED_SHEEN_LIGHT
            assertEquals(wantSpec, k.specular, "$kind specular")
            assertEquals(wantSheen, k.sheen, "$kind sheen")
            // Menus, sheets, dialogs (MODAL_HOLD) and floating bars/composer (WORDS_HOLD) hold center detail cleanly.
            val modal = kind == GlassPaneKind.Menu || kind == GlassPaneKind.Sheet || kind == GlassPaneKind.Dialog
            val words = kind == GlassPaneKind.Bar || kind == GlassPaneKind.MessageBox
            val h =
                when {
                    kind == GlassPaneKind.Menu -> LiquidEdge.MODAL_HOLD
                    modal -> LiquidEdge.SHEET_HOLD
                    words -> LiquidEdge.WORDS_HOLD
                    else -> LiquidHold.Off
                }
            assertEquals(h.localDp, k.localDp, "$kind local softening")
            assertEquals(h.localFree, k.localFree, "$kind local free")
            assertEquals(h.localSlope, k.localSlope, "$kind local slope")
            assertEquals(h.inkKeep, k.inkKeep, "$kind ink-like strokes kept")
            assertEquals(h.inkRim, k.inkRim, "$kind ink-like strokes held in the rim")
            assertEquals(
                GlassTuning.WORDS_CLEAR_FLOATING,
                k.wordsClear,
                "$kind: floating chrome lets content enter lens rim",
            )
            assertEquals(if (kind == GlassPaneKind.Chip) LiquidJoin.REACH_PILL_DP else LiquidJoin.REACH_MENU_DP, k.joinReachDp)
        }
        // Apple Liquid Glass scale-dependent weight (B-609): clear 2 dp bar, deeper 6 dp modal.
        assertEquals(2f, GlassTuning.shipped(GlassPaneKind.Bar, true).blurDp)
        assertEquals(18f, GlassTuning.shipped(GlassPaneKind.Bar, true).lensStrengthDp)
        assertEquals(LiquidEdge.MODAL_BLUR_DP, GlassTuning.shipped(GlassPaneKind.Menu, true).blurDp)
        assertEquals(38f, GlassTuning.shipped(GlassPaneKind.Dialog, false).lensStrengthDp)
        assertTrue(GlassTuning.Shipped.isShipped)
    }

    @Test
    fun `B-GLASS-LAB-002 every shipped number sits on its slider's step, so a slider shows it exactly`() {
        for (kind in GlassPaneKind.entries) for (dark in both) for (knob in GlassKnob.entries) {
            val v = knob.of(GlassTuning.shipped(kind, dark))
            assertEquals(v, knob.snap(v), "$kind ${if (dark) "dark" else "light"} ${knob.id}")
        }
    }

    @Test
    fun `B-GLASS-LAB-003 the copied block reads back to the same numbers`() {
        val moved =
            GlassTuning.Shipped.copy(inApp = true)
                .with(GlassPaneKind.Menu, true, GlassKnob.Blur, 2.5f)
                .with(GlassPaneKind.Chip, false, GlassKnob.LensStrength, 20f)
                .with(GlassPaneKind.Bar, true, GlassKnob.Brightness, -.05f)
                .preset(GlassPreset.ClearLiquid, false)
        val block = GlassTuning.encode(moved, "2.2.0 (900)")
        assertTrue(block.startsWith(GlassTuning.HEADER))
        assertTrue("build 2.2.0 (900)" in block)
        // One line per pane kind and theme, every number.
        val lines = block.lines().drop(1)
        assertEquals(GlassPaneKind.entries.size * 2, lines.size)
        lines.forEach { line -> assertEquals(GlassKnob.entries.size, line.split(' ').size - 1, line) }
        assertEquals(moved.trimmed(), GlassTuning.decode(block))
        assertEquals(2.5f, GlassTuning.decode(block)!!.knobs(GlassPaneKind.Menu, true).blurDp)
        assertTrue(GlassTuning.decode(block)!!.inApp)
    }

    @Test
    fun `B-GLASS-LAB-004 an untouched block reads as the shipped glass, and text without numbers is refused`() {
        assertEquals(GlassTuning.Shipped, GlassTuning.decode(GlassTuning.encode(GlassTuning.Shipped)))
        assertNull(GlassTuning.decode("hello there"))
        assertNotNull(GlassTuning.decode("bar.dark blur=4"))
        assertEquals(4f, GlassTuning.decode("bar.dark blur=4 unknown=3 tint=x")!!.knobs(GlassPaneKind.Bar, true).blurDp)
    }

    @Test
    fun `B-GLASS-LAB-005 all linked moves every kind by the same step`() {
        val barBefore = GlassTuning.shipped(GlassPaneKind.Bar, true).blurDp
        val boxBefore = GlassTuning.shipped(GlassPaneKind.MessageBox, true).blurDp
        val menuBefore = GlassTuning.shipped(GlassPaneKind.Menu, true).blurDp
        val chipBefore = GlassTuning.shipped(GlassPaneKind.Chip, true).blurDp
        val delta = 2f
        val t = GlassTuning.Shipped.linked(GlassPaneKind.Bar, true, GlassKnob.Blur, barBefore + delta)
        assertEquals(barBefore + delta, t.knobs(GlassPaneKind.Bar, true).blurDp)
        assertEquals(boxBefore + delta, t.knobs(GlassPaneKind.MessageBox, true).blurDp)
        assertEquals(menuBefore + delta, t.knobs(GlassPaneKind.Menu, true).blurDp)
        assertEquals(chipBefore + delta, t.knobs(GlassPaneKind.Chip, true).blurDp)
        // The light theme is untouched.
        assertEquals(GlassTuning.shipped(GlassPaneKind.Bar, false), t.knobs(GlassPaneKind.Bar, false))
    }

    @Test
    fun `B-GLASS-LAB-006 light passing through is continuous and meets the shipped softening`() {
        val at = GlassTuning.passThrough(GlassTuning.PASS_SHIPPED_HOLD)
        assertEquals(1f, at[0])
        assertEquals(LiquidJoin.KNEE, at[1], 1e-4f)
        assertEquals(LiquidJoin.KNEE_SLOPE, at[2], 1e-4f)
        assertEquals(LiquidEdge.HOLD_CHROMA, at[3], 1e-4f)
        assertEquals(0f, GlassTuning.passThrough(1f)[0], "all light through: no softening")
        val near = GlassTuning.passThrough(.999f)
        assertTrue(near[2] > .99f && near[3] > .99f, "no step just below 1")
        var last = GlassTuning.passThrough(0f)
        for (i in 1..99) {
            val now = GlassTuning.passThrough(i / 100f)
            for (j in 1..3) assertTrue(now[j] >= last[j] - 1e-6f, "rises with the slider")
            last = now
        }
    }

    @Test
    fun `B-GLASS-LAB-007 the presets move the glass and Shipped puts it back`() {
        for (dark in both) {
            val clear = GlassTuning.Shipped.preset(GlassPreset.ClearLiquid, dark)
            assertFalse(clear.isShipped)
            val bar = clear.knobs(GlassPaneKind.Bar, dark)
            assertTrue(bar.blurDp < 1f && bar.lensStrengthDp > 0f && bar.specular > 0f && bar.bendShape < 0f, "clear liquid: little blur, a bend, a glint")
            assertTrue(bar.wordsClear < 1f, "the page shows under the bar's words")
            val menu = clear.knobs(GlassPaneKind.Menu, dark)
            assertTrue(menu.blurDp < GlassTuning.shipped(GlassPaneKind.Menu, dark).blurDp && menu.lensStrengthDp > 0f, "menus are clear and bend too")
            assertNotEquals(clear, GlassTuning.Shipped.preset(GlassPreset.Frost, dark))
            assertTrue(clear.preset(GlassPreset.Shipped, dark).trimmed().isShipped)
        }
    }

    @Test
    fun `B-GLASS-LAB-008 numbers are written without exponents or trailing zeros`() {
        assertEquals("3", GlassTuning.number(3f))
        assertEquals("0.18", GlassTuning.number(.18f))
        assertEquals("-0.03", GlassTuning.number(-.03f))
        assertEquals("0", GlassTuning.number(-0.0001f))
        assertEquals("1.25", GlassTuning.number(1.25f))
    }
}
