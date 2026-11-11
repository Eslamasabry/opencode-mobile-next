package io.github.eslamasabry.oc.ui.design.style

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.color.lerp
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import io.github.eslamasabry.oc.ui.design.effects.AuroraImage
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Step 1 of docs/designs/glass-in-the-kit.md (owner 2026-11-11 on the dark home: "Is this supposed to be glass?"): a
 * clear pane lit at both ends (1a), a dark page with light in it whose words do not pay (1b), and row lines that stop
 * before the rim under Glass (1c). The kit's drawing of them is tested in :ui:kit.
 */
class GlassInTheKitStep1Test {
    private fun roles(pack: PackId, dark: Boolean): Roles =
        StyleResolver.adjust(ThemeResolver.roles(pack, null, dark, saver = false, dynamic = null), Style.Glass)

    // ---- 1a. A lit bottom end on every clear pane

    @Test
    fun `B-KIT-003 1a the default look's clear pane rim is lit at both ends in dark, light unchanged`() {
        assertEquals(Triple(.24f, .10f, .30f), GlassLook.Default.paneRim(dark = true))
        // Light: as before step 1, a straight fade from .60 to .16 (the light theme's edge waits for step 2).
        assertEquals(Triple(.60f, null, .16f), GlassLook.Default.paneRim(dark = false))
        val (start, rest, end) = GlassLook.Default.paneRim(dark = true)
        assertTrue(rest!! < start && rest < end, "the rest of the rim is quieter than both lit ends")
    }

    @Test
    fun `B-KIT-003 1a Frosted and Smoked keep the rim they had before step 1`() {
        // Today's numbers (the internal PANE_RIM_* constants before 2026-11-11): .24 to .07 dark, .60 to .16 light.
        val today = listOf(Triple(.24f, null, .07f), Triple(.60f, null, .16f))
        for (spec in listOf(GlassLook.Frosted, GlassLooks.Frosted.spec, GlassLooks.Smoked.spec)) {
            assertEquals(today, listOf(spec.paneRim(true), spec.paneRim(false)))
        }
        // Smoked is Default in every other number (its smoke is on the preset).
        assertEquals(GlassLook.Default.withPaneRimOf(GlassLook.Frosted), GlassLooks.Smoked.spec)
    }

    @Test
    fun `B-KIT-003 1a a rim with a rest fades to and from a straight rim through the straight one's middle`() {
        val white = Argb(0xFFFFFFFF.toInt())
        val straight = SurfaceDecoration(edgeStart = white.withAlpha(.24f), edgeEnd = white.withAlpha(.07f), edgeWidthDp = 1f)
        val lit = straight.copy(edgeRest = white.withAlpha(.10f), edgeEnd = white.withAlpha(.30f))
        val middle = lerp(straight.edgeStart!!, straight.edgeEnd!!, .5f)
        assertNull(SurfaceFade.between(straight, straight.copy(edgeWidthDp = 2f), .5f).edgeRest, "two straight rims stay straight")
        assertEquals(lerp(middle, lit.edgeRest!!, .5f), SurfaceFade.between(straight, lit, .5f).edgeRest)
        assertEquals(lerp(lit.edgeRest!!, middle, .25f), SurfaceFade.between(lit, straight, .25f).edgeRest)
        // At the ends the fade is the decoration itself.
        assertEquals(lit, SurfaceFade.between(straight, lit, 1f))
        assertEquals(straight, SurfaceFade.between(straight, lit, 0f))
        // From no rim at all, the rest fades in from its own colour at alpha 0, as every other colour does.
        val none = SurfaceDecoration()
        assertEquals(lerp(lit.edgeRest!!.withAlpha(0f), lit.edgeRest!!, .5f), SurfaceFade.between(none, lit, .5f).edgeRest)
    }

    // ---- 1b. A page with light in it

    @Test
    fun `B-KIT-003 1b the dark page's chroma is 20 percent, light and the thumbnails unchanged`() {
        assertEquals(.20f, AuroraImage.CHROMA_DARK)
        assertEquals(.12f, AuroraImage.CHROMA_LIGHT)
        assertEquals(.14f, AuroraImage.thumbnailChromaFor(dark = true), "a look's thumbnail keeps the quiet plate")
        assertEquals(.12f, AuroraImage.thumbnailChromaFor(dark = false))
        assertEquals(AuroraImage.CHROMA_DARK, AuroraImage.chromaFor(dark = true))
    }

    @Test
    fun `B-KIT-003 1b two still soft lights in page fractions, dark only, full at the centre and gone at the rim`() {
        assertEquals(
            listOf(AuroraImage.Light(.15f, .25f, .45f), AuroraImage.Light(.70f, .55f, .40f)),
            AuroraImage.lightsFor(dark = true),
        )
        assertTrue(AuroraImage.lightsFor(dark = false).isEmpty(), "the light theme gets no lights in step 1")
        assertEquals(1f, AuroraImage.lightShare(0f))
        assertEquals(1f, AuroraImage.lightShare(.45f), "full over the inner 45 %")
        assertEquals(0f, AuroraImage.lightShare(1f), "gone at the rim")
        var last = 1f
        for (i in 0..100) {
            val s = AuroraImage.lightShare(i / 100f)
            assertTrue(s <= last + 1e-6f, "fades without a ring at ${i / 100f}")
            last = s
        }
        // In page fractions: the same place on a phone and on a tablet.
        for ((w, h) in listOf(360f to 800f, 1280f to 800f)) {
            assertEquals(1f, AuroraImage.lightAt(.15f * w, .25f * h, w, h, dark = true), .001f)
            assertEquals(0f, AuroraImage.lightAt(.15f * w, .25f * h, w, h, dark = false))
        }
    }

    @Test
    fun `B-KIT-003 1b a light adds about 10 percent luma at its centre, in the accent's hue`() {
        for (pack in PackId.entries) {
            val accent = roles(pack, true).accent.withAlpha(1f)
            val light = AuroraImage.lightColorFor(accent)
            val luma = (.2126 * light.red + .7152 * light.green + .0722 * light.blue) / 255.0
            assertTrue(luma <= AuroraImage.LIGHT_PEAK_LUMA + .006, "$pack: at most about .10 ($luma)")
            assertTrue(luma >= AuroraImage.LIGHT_PEAK_LUMA * .6, "$pack: a light you can see ($luma)")
            // The accent's hue: the channels keep the accent's order.
            val order = listOf(accent.red, accent.green, accent.blue).withIndex().sortedBy { it.value }.map { it.index }
            val lit = listOf(light.red, light.green, light.blue)
            assertTrue(lit[order[2]] >= lit[order[0]], "$pack: the accent's brightest channel stays brightest")
        }
    }

    @Test
    fun `B-KIT-003 1b words do not pay - the spots stay at the quiet plate's, so no pane or liquid face is thicker`() {
        assertTrue(AuroraImage.CHROMA_DARK > AuroraImage.CHROMA_DARK_QUIET, "the cap has something to cap")
        for (pack in PackId.entries) for (dark in listOf(true, false)) {
            val r = roles(pack, dark)
            val quiet = if (dark) AuroraImage.CHROMA_DARK_QUIET else AuroraImage.CHROMA_LIGHT
            for (frosted in listOf(true, false)) {
                assertEquals(
                    AuroraImage.spotsOf(r.ground, r.accent, dark, frosted, chroma = quiet),
                    AuroraImage.spotsOf(r.ground, r.accent, dark, frosted),
                    "$pack dark=$dark frosted=$frosted",
                )
            }
            assertEquals(AuroraImage.clearSpotsOf(r.ground, r.accent, dark, chroma = quiet), AuroraImage.clearSpotsOf(r.ground, r.accent, dark))
            if (dark) {
                // Uncapped, the brighter page would have moved them (so this test fails if the cap goes).
                assertNotEquals(
                    AuroraImage.clearSpotsOf(r.ground, r.accent, dark, chroma = AuroraImage.CHROMA_DARK),
                    AuroraImage.clearSpotsOf(r.ground, r.accent, dark),
                    "$pack",
                )
            }
        }
    }

    /** The most the two lights add at any one point, over the page shapes the app runs on (phones, tablets, landscape). */
    private val brightestLight: Float by lazy {
        var most = 0f
        for ((w, h) in listOf(360f to 800f, 412f to 915f, 800f to 360f, 915f to 412f, 800f to 1280f, 1280f to 800f)) {
            var y = 0f
            while (y <= h) {
                var x = 0f
                while (x <= w) {
                    most = maxOf(most, AuroraImage.lightAt(x, y, w, h, dark = true))
                    x += 4f
                }
                y += 4f
            }
        }
        most
    }

    private val panes =
        listOf(
            SurfaceRole.Content to SurfaceLevel.Surface1,
            SurfaceRole.Content to SurfaceLevel.Surface2,
            SurfaceRole.Control to SurfaceLevel.Surface3,
            SurfaceRole.Track to SurfaceLevel.Surface1,
            SurfaceRole.Field to SurfaceLevel.Surface1,
        )

    @Test
    fun `B-KIT-003 1b on the brighter page, at the brightest light, words and icons on a clear pane still read`() {
        println("glass-in-the-kit 1b: the brightest light adds ${"%.2f".format(brightestLight)} of a light's peak")
        for (pack in PackId.entries) for (id in GlassLookId.entries) {
            val r = roles(pack, true)
            val ground = r.ground.withAlpha(1f)
            // What the kit thickens over (capped), and what is really behind the pane: the page as drawn (chroma .20),
            // each spot also under the brightest light.
            val measured = listOf(ground) + AuroraImage.clearSpotsOf(r.ground, r.accent, true)
            val drawn = listOf(ground) + AuroraImage.clearSpotsOf(r.ground, r.accent, true, chroma = AuroraImage.CHROMA_DARK)
            val behind = drawn + drawn.map { AuroraImage.lit(it, r.accent, brightestLight) }
            for ((role, level) in panes) for (chosen in listOf(false, true)) {
                if (chosen && role != SurfaceRole.Content && role != SurfaceRole.Control) continue
                val request = SurfaceRequest(r, role, Knobs(), level, selected = chosen, look = GlassLooks.of(id).spec)
                val solid = request.levelFill.over(ground)
                val wordsDp = if (role == SurfaceRole.Content) GlassFinish.PANE_WORDS_DP else 0f
                val d = GlassFinish.thickened(GlassSpec.resolveClear(request)!!, r, solid, measured, GlassFinish.paneInks(r, solid), wordsDp = wordsDp)
                val sheen = GlassFinish.sheenAt(d.sheen, wordsDp)
                for (face in listOfNotNull(d.fill, d.fillStart)) for (spot in behind) {
                    val bare = face.over(spot)
                    for (seen in listOf(bare, sheen.over(bare))) for ((ink, target) in GlassFinish.textInks(r, solid)) {
                        val c = wcagContrast(ink, seen)
                        assertTrue(c >= target - 1e-3, "$id $pack $role/$level chosen=$chosen over $spot: words $c < $target")
                    }
                    for ((ink, target) in GlassFinish.glyphInks(r, solid)) {
                        val c = wcagContrast(ink.withAlpha(1f), bare)
                        assertTrue(c >= target - 1e-3, "$id $pack $role/$level over $spot: icon $c < $target")
                    }
                }
            }
        }
    }

    @Test
    fun `B-KIT-003 1b on the brighter page, at the brightest light, a liquid face's words keep the moving floor`() {
        for (pack in PackId.entries) for (id in listOf(GlassLookId.Glossy, GlassLookId.Clear)) {
            val page = roles(pack, true)
            val look = GlassLooks.of(id)
            // The face as the kit makes it over the painted aurora (capped spots) ...
            val measured = AuroraImage.spotsOf(page.ground, page.accent, true, frosted = true).toList()
            // ... over the page as drawn, under the brightest light.
            val drawn = AuroraImage.spotsOf(page.ground, page.accent, true, frosted = true, chroma = AuroraImage.CHROMA_DARK).toList()
            val picture = drawn + drawn.map { AuroraImage.lit(it, page.accent, brightestLight) }
            for (role in listOf(LiquidRole.Bar, LiquidRole.Composer, LiquidRole.Sheet, LiquidRole.Menu, LiquidRole.Pill)) {
                val face = LiquidEdge.face(page, role, picture = measured, look = look)
                assertEquals(face, LiquidEdge.face(page, role, picture = AuroraImage.spotsOf(page.ground, page.accent, true, frosted = true, chroma = AuroraImage.CHROMA_DARK_QUIET).toList(), look = look), "$id $pack $role: no thicker than before")
                val spots = LiquidEdge.themeSpots(page, LiquidEdge.recipe(role, true, look), picture)
                val ink = LiquidEdge.inks(page, role).first().first
                for (f in listOfNotNull(face.fill, face.fillStart)) for (spot in spots) {
                    val c = wcagContrast(ink, f.over(spot))
                    assertTrue(c >= LiquidEdge.MOVING_FLOOR - 1e-3, "$id $pack $role over $spot: $c < ${LiquidEdge.MOVING_FLOOR}")
                }
            }
        }
    }

    // ---- 1c. Dividers stop before the rim under Glass

    @Test
    fun `B-KIT-037 1c a panel's row lines stop one gutter before the end edge under Glass, at the edge in every other style`() {
        for (style in Style.entries) {
            val gutters = StyleResolver.spec(style).tokens.dividerEndGutters
            assertEquals(if (style == Style.Glass) 1f else 0f, gutters, "$style")
        }
        assertTrue(abs(GlassSpec.DIVIDER_END_GUTTERS - 1f) < 1e-6f)
    }
}
