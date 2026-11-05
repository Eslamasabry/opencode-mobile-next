package io.github.eslamasabry.oc.ui.design.rb.o03

import io.github.eslamasabry.oc.ui.design.color.Graphite
import io.github.eslamasabry.oc.ui.design.color.argb
import io.github.eslamasabry.oc.ui.design.style.Corners
import io.github.eslamasabry.oc.ui.design.style.Edges
import io.github.eslamasabry.oc.ui.design.style.Knobs
import io.github.eslamasabry.oc.ui.design.style.NeedsYouLook
import io.github.eslamasabry.oc.ui.design.style.Radii
import io.github.eslamasabry.oc.ui.design.style.SignalPalette
import io.github.eslamasabry.oc.ui.design.style.SignalSpec
import io.github.eslamasabry.oc.ui.design.style.StyleType
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** lib/ui/kit/style/kit_style_signal.dart (+ SignalPalette, theme_packs_signal.dart): tokens, outlines, keys. */
class KitStyleSignalParityTest {
    @Test fun constantsAndTokens() {
        assertEquals(1.5f, SignalSpec.OUTLINE)
        val t = SignalSpec.tokens
        assertEquals(Knobs(corners = Corners.Sharp), t.defaults)
        assertEquals(4f, t.sharpRadius)
        assertTrue(t.keepsUnderHighContrast)
        assertTrue(t.live.offWhenChosen)
        assertEquals(NeedsYouLook(tint = false, ring = false, startEdge = false), t.needsYou)
        assertTrue(t.flatLists)
        assertTrue(t.track)
        assertEquals(
            StyleType(displayWeight = 700, largeTitleSize = 32, largeTitleWeight = 760, displayTracking = -.02f, titleSize = 22, engravedLabels = true, firmWeights = true),
            t.type,
        )
    }

    @Test fun paletteIsTheSameWhateverThePack() {
        assertEquals(argb(0xFFEFE9DA), SignalPalette.Light.ground)
        assertEquals(argb(0xFF12110E), SignalPalette.Night.ground)
        assertEquals(argb(0xFFFF3B2F), SignalPalette.LampRed)
        assertEquals(argb(0xFF3BE070), SignalPalette.LampGreen)
        assertEquals(SignalPalette.Light, SignalPalette.forRoles(Graphite.light))
        assertEquals(SignalPalette.Night, SignalPalette.forRoles(Graphite.dark))
    }

    @Test fun fieldIsAMachinedWindowInInk() {
        for (roles in bothModes) {
            val d = SignalSpec.resolve(req(roles, SurfaceRole.Field))
            assertEquals(1.5f, d.edgeWidthDp)
            assertEquals(roles.text2.withAlpha(1f), d.edgeStart)
            assertEquals(0, d.fill!!.alpha)
            assertEquals(2f, SignalSpec.resolve(req(roles, SurfaceRole.Field, selected = true)).edgeWidthDp)
        }
    }

    @Test fun cornersCapAtFourWhenSharp() {
        val d = SignalSpec.resolve(req(Graphite.light, SurfaceRole.Track, Knobs(corners = Corners.Sharp), radii = Radii(12f, 2f, 12f, 2f)))
        assertEquals(Radii(4f, 2f, 4f, 2f), d.radii)
        val round = SignalSpec.resolve(req(Graphite.light, SurfaceRole.Track, Knobs(corners = Corners.Round), radii = Radii.all(12f)))
        assertEquals(Radii.all(12f), round.radii)
    }

    @Test fun actionKeyIsRaisedThreeDpAndSinksToOne() {
        val roles = Graphite.light
        val face = roles.accent
        val rest = SignalSpec.resolve(req(roles, SurfaceRole.Control, color = face).copy(action = true))
        assertEquals(3f, rest.shadows.single().dyDp)
        assertEquals(0f, rest.shadows.single().blurDp)
        assertEquals(face, rest.fill)
        val down = SignalSpec.resolve(req(roles, SurfaceRole.Control, color = face, pressed = true).copy(action = true))
        assertEquals(1f, down.shadows.single().dyDp)
    }

    @Test fun darkKeyFaceSinksTowardsTheGround() {
        val roles = Graphite.dark
        assertEquals(SignalPalette.Night.key, SignalSpec.keyFace(roles, SignalPalette.Night.key))
        assertTrue(SignalSpec.keyFace(roles, roles.accent) != roles.accent)
        assertEquals(roles.accent, SignalSpec.keyFace(Graphite.light, roles.accent))
    }

    @Test fun dividerIsTheHairlineUnlessSection() {
        val roles = Graphite.light
        assertEquals(roles.hairline, SignalSpec.resolve(req(roles, SurfaceRole.Divider, Knobs(edges = Edges.Hairline))).fill)
        assertEquals(0, SignalSpec.resolve(req(roles, SurfaceRole.Divider, Knobs(edges = Edges.Hairline), selected = true)).fill!!.alpha)
    }

    @Test fun chosenControlIsInvertedAndNamesItsWords() {
        for (roles in bothModes) {
            val r = req(roles, SurfaceRole.Control, selected = true)
            assertEquals(SignalSpec.invertedInk(roles), SignalSpec.resolve(r).fill)
            assertEquals(roles.ground.withAlpha(1f), SignalSpec.onColor(r))
        }
    }

    @Test fun adjustedRolesAreReadable() {
        for (roles in bothModes) {
            val a = SignalSpec.adjustRoles(roles)
            assertEquals(a.ground, a.surface1)
            assertTrue(a.ground != roles.ground)
        }
    }
}
