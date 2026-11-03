package io.github.eslamasabry.oc.ui.kit.style

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.argb
import io.github.eslamasabry.oc.ui.design.color.relativeLuminance
import io.github.eslamasabry.oc.ui.design.effects.KitCorners
import io.github.eslamasabry.oc.ui.design.effects.KitDepth
import io.github.eslamasabry.oc.ui.design.effects.KitEdges
import io.github.eslamasabry.oc.ui.design.effects.KitKnobs
import io.github.eslamasabry.oc.ui.design.effects.KitSurfaceStyle
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KitStyleSoftSignalTest {
    private val solid = KitSurfaceLook(radius = KitRadii.all(18f), fill = argb(0xFF808080))

    private fun soft(
        roles: Roles,
        role: KitSurfaceRole = KitSurfaceRole.Content,
        level: SurfaceLevel = SurfaceLevel.Surface1,
        color: Argb? = null,
        selected: Boolean = false,
        pressed: Boolean = false,
        disabled: Boolean = false,
    ) = KitStyles.resolve(roles, KitSurfaceStyle.Soft, role, solid, null, level, color = color, selected = selected, pressed = pressed, disabled = disabled)

    private fun face(roles: Roles, d: KitSurfaceLook) = d.fill.on(roles.ground.withAlpha(1f))

    /** Every shipped theme under Soft's own roles. */
    private val softThemes = everyTheme().map { (n, r) -> n to KitStyleSoft.adjustRoles(r) }

    @Test
    fun `B-KIT-045 Soft is raised with no edges, a calm needs-you card and a glowing ask field`() {
        val t = KitStyleSoft.tokens
        assertEquals(KitDepth.Raised, t.defaults.depth)
        assertEquals(KitEdges.None, t.defaults.edges)
        assertTrue(t.dependsOnDepth)
        assertEquals(.7f, t.live.maxStrength)
        assertEquals(KitNeedsYouDecision(tint = false, line = false, ring = false, startEdge = true, pill = false), t.needsYou.decide(t.glow.halo))
        assertTrue(t.fieldHaloShown)
        for (roles in graphite) {
            val d = soft(roles)
            assertEquals(0f, d.inset)
            assertTrue(d.distance > 0f && d.lightShadow.alpha > 0 && d.darkShadow.alpha > 0)
            assertTrue(abs(relativeLuminance(d.fill) - relativeLuminance(roles.ground)) < .02)
            assertEquals(1f, soft(roles, selected = true).inset)
            assertEquals(1f, soft(roles, pressed = true).inset)
            assertEquals(1f, soft(roles, KitSurfaceRole.Track).inset)
        }
        val dark = soft(graphiteDark)
        assertTrue(dark.edgeStart.alpha > 0)
        assertTrue(relativeLuminance(dark.fillStart) > relativeLuminance(graphiteDark.ground))
        assertTrue(dark.darkShadow.alpha / 255.0 >= .7)
    }

    @Test
    fun `B-KIT-045 an accent thumb stands raised out of a pressed-in track with words at AA`() {
        for ((name, roles) in softThemes) {
            val d = soft(roles, KitSurfaceRole.Control, color = roles.accent, selected = true)
            assertEquals(0f, d.inset, name)
            assertEquals(1f, soft(roles, KitSurfaceRole.Track).inset, name)
            assertTrue(d.distance > 0f, name)
            assertEquals(roles.accent, d.fill, "$name: no muddy well")
            assertTrue(ratio(roles.onAccent, face(roles, d)) >= 4.5, name)
            assertEquals(1f, soft(roles, KitSurfaceRole.Control, color = roles.accent, selected = true, pressed = true).inset, name)
            if (roles.dark) {
                val tint = roles.success.withAlpha(.16f)
                assertEquals(tint, soft(roles, KitSurfaceRole.Control, color = tint).fill, name)
            }
        }
    }

    @Test
    fun `B-KIT-045 a chosen control without a fill is pressed in with accent words at AA, disabled is flat`() {
        for ((name, roles) in softThemes) {
            val d = soft(roles, KitSurfaceRole.Control, SurfaceLevel.Surface3, selected = true)
            assertEquals(1f, d.inset, name)
            val words = KitStyleSoft.onColor(
                KitSurfaceRequest(roles, KitSurfaceRole.Control, solid, KitStyleSoft.tokens.defaults, SurfaceLevel.Surface3, selected = true),
            )!!
            assertTrue(ratio(words, face(roles, d)) >= 4.5, name)
            assertNull(KitStyleSoft.onColor(KitSurfaceRequest(roles, KitSurfaceRole.Control, solid, KitStyleSoft.tokens.defaults, SurfaceLevel.Surface3)))
        }
        for (roles in graphite) {
            for (color in listOf(null, roles.accent)) {
                val d = soft(roles, KitSurfaceRole.Control, color = color, disabled = true)
                assertFalse(d.casts)
                assertEquals(0f, d.inset)
                assertEquals(0f, d.distance)
            }
        }
    }

    @Test
    fun `B-KIT-045 your prompt has its own tint with AA words and the code well keeps AA colours`() {
        val fails = mutableListOf<String>()
        for ((name, roles) in softThemes) {
            val prompt = soft(roles, level = SurfaceLevel.Surface2)
            assertNotEquals(soft(roles).fill, prompt.fill, name)
            assertEquals(0f, prompt.inset, name)
            assertTrue(prompt.distance > 0f, name)
            for (ink in listOf(roles.text1, roles.text2)) assertTrue(ratio(ink, face(roles, prompt)) >= 4.5, name)
            val code = soft(roles, KitSurfaceRole.Code, color = if (roles.dark) roles.surface1 else roles.ground)
            assertEquals(1f, code.inset, name)
            val well = face(roles, code)
            for ((what, ink) in listOf(
                "text1" to roles.text1, "text2" to roles.text2, "keyword" to roles.codeKeyword,
                "string" to roles.codeString, "type" to roles.codeType, "added" to roles.codeAdded,
            )) {
                val c = ratio(ink.on(well), well)
                if (c < 4.5) fails += "$name $what $c"
            }
        }
        assertTrue(fails.isEmpty(), fails.joinToString("\n"))
    }

    private fun control(color: Argb? = null, pressed: Boolean = false, action: Boolean = true) =
        KitSurfaceRequest(
            graphiteLight, KitSurfaceRole.Control, KitSurfaceLook.Bare, KitStyleSignal.tokens.defaults, radius = KitRadii.all(4f),
            color = color, pressed = pressed, action = action,
        )

    @Test
    fun `B-KIT-042 Signal is a registered style on a track`() {
        assertSame(KitStyleSignal, KitStyles.spec(KitSurfaceStyle.Signal))
        val t = KitStyleSignal.tokens
        assertTrue(t.track)
        assertTrue(t.flatLists)
        assertTrue(t.type.engravedLabels)
        assertEquals(KitCorners.Sharp, t.defaults.corners)
        assertTrue(t.live.offWhenChosen)
        assertTrue(t.keepsUnderHighContrast)
        for (style in KitSurfaceStyle.entries - KitSurfaceStyle.Signal) assertFalse(KitStyles.spec(style).tokens.track, "$style")
    }

    @Test
    fun `B-KIT-042 cream enamel by day, a night signal box by dark, words at AA and not inverted`() {
        val day = KitStyleSignal.adjustRoles(graphiteLight)
        val night = KitStyleSignal.adjustRoles(graphiteDark)
        assertEquals(KitSignalEnamel.Day.ground, day.ground)
        assertEquals(KitSignalEnamel.Night.ground, night.ground)
        for (roles in listOf(day, night)) {
            for (fill in listOf(roles.ground, roles.surface2, roles.surface3)) {
                for (ink in listOf(roles.text1, roles.text2, roles.text3)) assertTrue(ratio(ink, fill) >= 4.5)
            }
            assertEquals((if (roles.dark) graphiteDark else graphiteLight).text1, roles.text1)
        }
    }

    @Test
    fun `B-KIT-042 an action with its own fill is a raised key and the rest are flat outlines`() {
        val key = KitStyleSignal.resolve(control(color = graphiteLight.accent))
        assertEquals(graphiteLight.accent, key.fill)
        assertEquals(KitShadow(key.shadows.single().color, dy = 3f), key.shadows.single())
        assertEquals(0f, key.shadows.single().blur)
        val held = KitStyleSignal.resolve(control(color = graphiteLight.accent, pressed = true))
        assertEquals(1f, held.shadows.single().dy)
        assertTrue(KitStyleSignal.resolve(control(color = graphiteLight.surface3, action = false)).shadows.isEmpty())
        val filter = KitStyleSignal.resolve(control())
        assertTrue(filter.shadows.isEmpty())
        assertEquals(KitStyleSignal.OUTLINE, filter.edgeWidth)
        assertEquals(0, filter.fill.alpha)
        // A chosen control is set in reverse, with enamel words.
        val chosen = KitStyleSignal.resolve(control().copy(selected = true))
        assertEquals(KitStyleSignal.invertedInk(graphiteLight), chosen.fill)
        assertEquals(graphiteLight.ground.withAlpha(1f), KitStyleSignal.onColor(control().copy(selected = true)))
        // A night key is dimmed, its words the better of ink and ground.
        val nightKey = KitSurfaceRequest(
            graphiteDark, KitSurfaceRole.Control, KitSurfaceLook.Bare, KitStyleSignal.tokens.defaults,
            radius = KitRadii.all(4f), color = graphiteDark.accent, action = true,
        )
        val words = KitStyleSignal.onColor(nightKey)!!
        assertTrue(ratio(words, KitStyleSignal.keyFace(graphiteDark, graphiteDark.accent)) >= 4.5)
    }
}
