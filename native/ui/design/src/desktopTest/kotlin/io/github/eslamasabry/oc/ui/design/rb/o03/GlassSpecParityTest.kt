package io.github.eslamasabry.oc.ui.design.rb.o03

import io.github.eslamasabry.oc.ui.design.color.Graphite
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.style.Depth
import io.github.eslamasabry.oc.ui.design.style.Edges
import io.github.eslamasabry.oc.ui.design.style.GlassSpec
import io.github.eslamasabry.oc.ui.design.style.Knobs
import io.github.eslamasabry.oc.ui.design.style.LivePolicy
import io.github.eslamasabry.oc.ui.design.style.StyleType
import io.github.eslamasabry.oc.ui.design.style.SurfaceDecoration
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** lib/ui/kit/style/kit_style_glass.dart at 1bbdc3f9c: every constant of the spec. */
class GlassSpecParityTest {
    @Test fun constantsAndTokens() {
        assertEquals(2f, GlassSpec.CHOSEN_RING)
        assertEquals(50f, GlassSpec.WEIGHT_BOOST)
        assertEquals(.08f, GlassSpec.SHEEN)
        assertEquals(20f, GlassSpec.PANE_FROST)
        assertEquals(26f, GlassSpec.FLOAT_FROST)
        val t = GlassSpec.tokens
        assertEquals(Knobs(), t.defaults)
        assertTrue(t.blursBehind)
        assertEquals(StyleType(weightBoost = 50f), t.type)
        assertEquals(LivePolicy(fillsMiddle = true, lookWhenChosen = "auroraImage"), t.live)
        assertEquals(2f, t.sharpRadius)
    }

    @Test fun opacityPerRole() {
        val want =
            mapOf(
                SurfaceRole.Content to (.42f to .50f), SurfaceRole.Control to (.48f to .56f), SurfaceRole.Track to (.38f to .46f),
                SurfaceRole.Floating to (.50f to .62f), SurfaceRole.Sheet to (.58f to .72f), SurfaceRole.Field to (.5f to .5f),
                SurfaceRole.Toggle to (.28f to .38f), SurfaceRole.Ground to (1f to 1f), SurfaceRole.Code to (1f to 1f),
                SurfaceRole.Divider to (1f to 1f), SurfaceRole.StateLayer to (1f to 1f),
            )
        for ((role, v) in want) {
            assertEquals(v.first, GlassSpec.opacity(role, dark = true), "$role dark")
            assertEquals(v.second, GlassSpec.opacity(role, dark = false), "$role light")
        }
    }

    @Test fun frostAndSigmaPerRole() {
        assertEquals(.07f, GlassSpec.frostOf(SurfaceRole.Content))
        assertEquals(.07f, GlassSpec.frostOf(SurfaceRole.Track))
        assertEquals(.08f, GlassSpec.frostOf(SurfaceRole.Control))
        assertEquals(.06f, GlassSpec.frostOf(SurfaceRole.Sheet))
        assertEquals(0f, GlassSpec.frostOf(SurfaceRole.Divider))
        for (r in listOf(SurfaceRole.Content, SurfaceRole.Control, SurfaceRole.Track, SurfaceRole.Field)) assertEquals(20f, GlassSpec.frostSigma(r))
        for (r in listOf(SurfaceRole.Floating, SurfaceRole.Sheet)) assertEquals(26f, GlassSpec.frostSigma(r))
        for (r in listOf(SurfaceRole.Toggle, SurfaceRole.Ground, SurfaceRole.Code, SurfaceRole.Divider, SurfaceRole.StateLayer)) assertEquals(0f, GlassSpec.frostSigma(r))
    }

    @Test fun plateShade() {
        assertEquals(.38f, GlassSpec.plateShadeOf(SurfaceRole.Content))
        assertEquals(.6f, GlassSpec.plateShadeOf(SurfaceRole.Field))
        assertEquals(.52f, GlassSpec.plateShadeOf(SurfaceRole.Sheet))
        assertEquals(.4f, GlassSpec.plateShadeOf(SurfaceRole.Code))
    }

    @Test fun rimsAndInnerGlow() {
        val (dt, db) = GlassSpec.rims(Graphite.dark)
        assertEquals(77, dt.alpha) // .30
        assertEquals(20, db.alpha) // .08
        val (lt, lb) = GlassSpec.rims(Graphite.light)
        assertEquals(242, lt.alpha) // .95
        assertEquals(26, lb.alpha) // .10
        assertEquals(26, GlassSpec.innerGlowOf(Graphite.dark).alpha) // .10
        assertEquals(140, GlassSpec.innerGlowOf(Graphite.light).alpha) // .55
    }

    @Test fun paneIsTintedFrostedAndPlated() {
        for (roles in bothModes) {
            val d = GlassSpec.resolve(req(roles, SurfaceRole.Content, level = SurfaceLevel.Surface1))!!
            assertEquals(GlassSpec.opacity(SurfaceRole.Content, roles.dark), d.fill!!.alpha / 255f, .005f)
            assertNotNull(d.fillStart)
            assertEquals(20f, d.frost)
            assertEquals(GlassSpec.plateFor(roles, SurfaceRole.Content), d.wordsPlate)
            assertEquals(GlassSpec.innerGlowOf(roles), d.innerGlow)
            assertEquals(GlassSpec.rims(roles).first, d.edgeStart)
        }
    }

    @Test fun contentShadowFollowsDepth() {
        val flat = GlassSpec.resolve(req(Graphite.dark, SurfaceRole.Content, Knobs(depth = Depth.Flat)))!!.shadows.single()
        assertEquals(12f, flat.dyDp)
        assertEquals(32f, flat.blurDp)
        assertEquals(-8f, flat.spreadDp)
        val raised = GlassSpec.resolve(req(Graphite.dark, SurfaceRole.Content, Knobs(depth = Depth.Raised)))!!.shadows.single()
        assertEquals(16f, raised.dyDp)
        assertEquals(40f, raised.blurDp)
    }

    @Test fun chosenPaneIsRingedInTheAccentAndEdgesOffDropsTheRim() {
        val ringed = GlassSpec.resolve(req(Graphite.light, SurfaceRole.Control, selected = true))!!
        assertEquals(Graphite.light.accent, ringed.edgeStart)
        assertEquals(2f, ringed.edgeWidthDp)
        val rimless = GlassSpec.resolve(req(Graphite.light, SurfaceRole.Content, Knobs(edges = Edges.None)))!!
        assertEquals(0, rimless.edgeStart!!.alpha)
    }

    @Test fun ownFillStaysAndHasNoPlateOrFrost() {
        val own = Graphite.light.accent
        val d = GlassSpec.resolve(req(Graphite.light, SurfaceRole.Control, color = own))!!
        assertEquals(own, d.fill)
        assertEquals(own, d.fillStart)
        assertEquals(0, d.wordsPlate.alpha)
        assertEquals(0f, d.frost)
        assertEquals(0, d.innerGlow.alpha)
    }

    @Test fun partsAnswerThemselves() {
        for (roles in bothModes) {
            assertEquals(roles.ground, GlassSpec.resolve(req(roles, SurfaceRole.Ground, level = io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel.Ground))!!.fill)
            assertEquals(roles.surface1.withAlpha(1f).over(roles.ground), GlassSpec.resolve(req(roles, SurfaceRole.Code))!!.fill)
            val field = GlassSpec.resolve(req(roles, SurfaceRole.Field))!!
            assertEquals(20f, field.frost)
            val toggle = GlassSpec.resolve(req(roles, SurfaceRole.Toggle))!!
            assertEquals(0f, toggle.frost)
        }
        val press = GlassSpec.resolve(req(Graphite.dark, SurfaceRole.StateLayer, pressed = true))!!
        assertEquals(26, press.fill!!.alpha) // .10
        assertEquals(15, GlassSpec.resolve(req(Graphite.dark, SurfaceRole.StateLayer))!!.fill!!.alpha) // .06
        assertEquals(107, GlassSpec.resolve(req(Graphite.light, SurfaceRole.StateLayer, pressed = true))!!.fill!!.alpha) // .42
        assertEquals(71, GlassSpec.resolve(req(Graphite.light, SurfaceRole.StateLayer))!!.fill!!.alpha) // .28
    }

    @Test fun onColorAndScrim() {
        val r = req(Graphite.light, SurfaceRole.Control)
        assertEquals(Graphite.light.text1, GlassSpec.onColor(r))
        assertNull(GlassSpec.onColor(req(Graphite.light, SurfaceRole.Control, color = Graphite.light.accent)))
        assertEquals(61, GlassSpec.adjustRoles(Graphite.dark).scrim.alpha) // .24
        assertEquals(26, GlassSpec.adjustRoles(Graphite.light).scrim.alpha) // .10
    }

}
