package io.github.eslamasabry.oc.ui.design.rb.o03

import io.github.eslamasabry.oc.ui.design.style.BrutalSpec
import io.github.eslamasabry.oc.ui.design.style.Corners
import io.github.eslamasabry.oc.ui.design.style.Depth
import io.github.eslamasabry.oc.ui.design.style.Knobs
import io.github.eslamasabry.oc.ui.design.style.PaperSpec
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.StyleResolver
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Audit 2026-11-14, finding 9: Brutal and Paper were half applied in a conversation. The two schools now dress every
 * part (one token), a part nested in an outlined pane takes the edge without a second shadow, and Solid, Soft, Glass
 * and Signal keep every part's own look.
 */
class StyleDressingTest {
    @Test
    fun `B-KIT-STY-01 only Brutal and Paper dress the parts that keep a look of their own`() {
        for (style in Style.entries) {
            val dresses = StyleResolver.spec(style).tokens.dressesOwnLooks
            assertEquals(style == Style.Brutal || style == Style.Paper, dresses, "$style dresses own looks")
        }
    }

    @Test
    fun `B-KIT-STY-02 Brutal - a control nested in the box keeps its ink edge and casts no shadow, pressed or not`() {
        val knobs = Knobs(corners = Corners.Soft, depth = Depth.Raised)
        for (roles in bothModes) {
            val nested = BrutalSpec.resolve(req(roles, SurfaceRole.Control, knobs, level = SurfaceLevel.Surface2).copy(nested = true))
            assertEquals(BrutalSpec.EDGE, nested.edgeWidthDp)
            assertEquals(roles.text1, nested.edgeStart)
            assertNull(nested.hardShadow)
            assertEquals(0f, nested.hardOffsetDp)
            val pressed = BrutalSpec.resolve(req(roles, SurfaceRole.Control, knobs, pressed = true).copy(nested = true))
            assertEquals(0f, pressed.faceShiftDp, "a nested control has no shadow to sink into")
            // The same control standing on the page keeps its shadow.
            val standing = BrutalSpec.resolve(req(roles, SurfaceRole.Control, knobs, level = SurfaceLevel.Surface2))
            assertEquals(3f, standing.hardOffsetDp)
            // Send and the mic (the accent) keep their mint face and their edge, no shadow.
            val send = BrutalSpec.resolve(req(roles, SurfaceRole.Control, knobs, color = roles.accent).copy(nested = true, circle = true))
            assertEquals(BrutalSpec.mintOf(roles), send.fill)
            assertEquals(BrutalSpec.EDGE, send.edgeWidthDp)
            assertNull(send.hardShadow)
        }
    }

    @Test
    fun `B-KIT-STY-03 Paper - a control nested in the box is ruled quieter than the box, square, flat`() {
        val knobs = Knobs(corners = Corners.Sharp)
        for (roles in bothModes) {
            val nested = PaperSpec.resolve(req(roles, SurfaceRole.Control, knobs, level = SurfaceLevel.Surface2).copy(nested = true))
            val box = PaperSpec.resolve(req(roles, SurfaceRole.Floating, knobs, level = SurfaceLevel.Surface2))
            assertEquals(1f, nested.edgeWidthDp)
            assertEquals(roles.text1.withAlpha(PaperSpec.NESTED_ALPHA), nested.edgeStart)
            assertTrue(nested.edgeStart!!.alpha < box.edgeStart!!.alpha, "the nested rule is quieter than the box's")
            assertTrue(nested.shadows.isEmpty())
            assertEquals(0f, nested.radii.topStart)
            // The round chip and the accent disc square up under Paper's Sharp corners.
            val round = PaperSpec.resolve(req(roles, SurfaceRole.Control, knobs, level = SurfaceLevel.Surface2).copy(nested = true, circle = true))
            assertFalse(round.circle)
            val send = PaperSpec.resolve(req(roles, SurfaceRole.Control, knobs, level = SurfaceLevel.Surface3, color = roles.accent).copy(nested = true, circle = true))
            assertFalse(send.circle)
        }
    }

    @Test
    fun `B-KIT-STY-04 Solid, Soft, Glass and Signal read nothing from nesting`() {
        for (style in listOf(Style.Solid, Style.Soft, Style.Glass, Style.Signal)) {
            val spec = StyleResolver.spec(style)
            val knobs = spec.tokens.defaults
            for (roles in bothModes) {
                for (role in listOf(SurfaceRole.Control, SurfaceRole.Content, SurfaceRole.Floating, SurfaceRole.Code)) {
                    val plain = req(roles, role, knobs, level = SurfaceLevel.Surface2)
                    assertEquals(spec.resolve(plain), spec.resolve(plain.copy(nested = true)), "$style $role")
                }
            }
        }
    }
}
