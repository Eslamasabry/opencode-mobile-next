package io.github.eslamasabry.oc.ui.design.effects

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import io.github.eslamasabry.oc.ui.design.style.GlassFinish
import io.github.eslamasabry.oc.ui.design.style.GlassSpec
import io.github.eslamasabry.oc.ui.design.style.Knobs
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.StyleResolver
import io.github.eslamasabry.oc.ui.design.style.SurfaceRequest
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * glass-live (owner 2026-11-09, "why is glass not interacting with the background?"): Glass panes and liquid faces over
 * a shader look are thickened for that look's measured reach ([LiveReach]), not for the worst any look could draw. The
 * measurement itself is `LiveReachMeasureTest` in `:ui:kit` (it renders the looks); here, what the numbers do.
 */
class LiveReachTest {
    private fun roles(pack: PackId, dark: Boolean): Roles =
        StyleResolver.adjust(ThemeResolver.roles(pack, null, dark, saver = false, dynamic = null), Style.Glass)

    private fun distance(a: Argb, b: Argb): Double =
        sqrt(listOf(a.red - b.red, a.green - b.green, a.blue - b.blue).sumOf { (it / 255.0) * (it / 255.0) })

    private fun paneAlpha(roles: Roles, spots: List<Argb>): Float {
        val ground = roles.ground.withAlpha(1f)
        val request = SurfaceRequest(roles, SurfaceRole.Content, Knobs(), SurfaceLevel.Surface1)
        val solid = request.levelFill.over(ground)
        val d = GlassFinish.thickened(GlassSpec.resolveClear(request)!!, roles, solid, listOf(ground) + spots, GlassFinish.paneInks(roles, solid), wordsDp = GlassFinish.PANE_WORDS_DP)
        return (d.fill?.alpha ?: 0) / 255f
    }

    @Test
    fun `B-KIT-LIVE-044 a look's spots lie within its reach of the cap, and an unmeasured id keeps the whole cap`() {
        for (pack in PackId.entries) for (dark in listOf(true, false)) {
            val roles = roles(pack, dark)
            val cap = if (dark) LiveSpots.CAP_DARK else LiveSpots.CAP_LIGHT
            for ((id, _) in LiveReach.rows) for (lens in listOf(false, true)) {
                val reach = LiveReach.of(id, dark, lens)
                assertTrue(reach in 0f..LiveSpots.MAX_REACH, "$id: $reach")
                val spots = LiveSpots.forLook(roles, id, lens)
                assertEquals(roles.ground.withAlpha(1f), spots.first())
                for (s in spots) assertTrue(distance(s, roles.ground) <= cap * reach + .01, "$pack dark=$dark $id lens=$lens: $s")
            }
            // An id the kit does not draw is measured as the farthest of any look.
            assertEquals(LiveSpots.of(roles, LiveReach.most(dark, false)), LiveSpots.forLook(roles, "notALook", lens = false))
            assertEquals(LiveSpots.of(roles, LiveReach.most(dark, true)), LiveSpots.forLook(roles, null, lens = true))
        }
    }

    @Test
    fun `B-KIT-LIVE-045 a pane over a look inside the cap is never thicker than the whole-cap guess, and thinner over thin marks`() {
        for (pack in PackId.entries) for (dark in listOf(true, false)) {
            val roles = roles(pack, dark)
            val guess = paneAlpha(roles, LiveSpots.of(roles))
            // Looks measured inside the cap (every look's frost on a dark theme; all but brushed metal's on a light one).
            for ((id, _) in LiveReach.rows.filter { LiveReach.of(it.key, dark, lens = false) <= 1f }) {
                val a = paneAlpha(roles, LiveSpots.forLook(roles, id, lens = false))
                assertTrue(a <= guess + 1e-3f, "$pack dark=$dark $id: $a > $guess")
            }
        }
        // The case the owner asked about: matrix rain's thin glyphs barely move a frosted ground, so its panes stay clear.
        for (dark in listOf(true, false)) {
            val roles = roles(PackId.Opencode, dark)
            val matrix = paneAlpha(roles, LiveSpots.forLook(roles, "matrixRain", lens = false))
            val guess = paneAlpha(roles, LiveSpots.of(roles))
            assertTrue(matrix < guess, "dark=$dark: matrix rain $matrix < whole cap $guess")
        }
    }

    @Test
    fun `B-KIT-LIVE-046 a liquid face over a known picture keeps its words at their targets over that picture`() {
        for (pack in PackId.entries) for (dark in listOf(true, false)) {
            val roles = ThemeResolver.roles(pack, null, dark, saver = false, dynamic = null)
            for (role in listOf(LiquidRole.Bar, LiquidRole.Composer, LiquidRole.Pill, LiquidRole.Sheet, LiquidRole.Menu)) {
                val recipe = LiquidEdge.recipe(role, dark)
                val whole = LiquidEdge.faceTint(roles, role).fill.alpha
                for (picture in listOf<List<Argb>>(emptyList()) + LiveReach.rows.keys.map { LiveSpots.forLook(roles, it, lens = true) }) {
                    val face = LiquidEdge.face(roles, role, picture = picture)
                    assertTrue(face.fill!!.alpha <= whole + 1, "$pack dark=$dark $role: never thicker than over every picture")
                    // Words over every spot that picture can put behind the content, as the face measures them.
                    for (spot in LiquidEdge.themeSpots(roles, recipe, picture)) for (f in listOfNotNull(face.fill, face.fillStart)) {
                        val bare = f.over(spot)
                        for (seen in listOf(bare, face.sheen.over(bare))) for ((ink, target) in LiquidEdge.inks(roles, role)) {
                            assertTrue(wcagContrast(ink, seen) >= target - 1e-3, "$pack dark=$dark $role: ${wcagContrast(ink, seen)} < $target")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `B-KIT-LIVE-047 without a known picture a liquid face measures every picture the app draws, as before`() {
        val roles = ThemeResolver.roles(PackId.Opencode, null, dark = true, saver = false, dynamic = null)
        val recipe = LiquidEdge.recipe(LiquidRole.Composer, true)
        val every = LiquidEdge.themeSpots(roles, recipe)
        val aurora = AuroraImage.spotsOf(roles.ground, roles.accent, roles.dark, frosted = true).toList().map { LiquidEdge.filtered(it, recipe) }
        val looks = LiveSpots.of(roles, LiveReach.most(dark = true, lens = true)).map { LiquidEdge.filtered(it, recipe) }
        assertTrue(every.containsAll(aurora) && every.containsAll(looks))
        val flat = LiquidEdge.themeSpots(roles, recipe, emptyList())
        assertTrue(flat.size < every.size, "the flat ground has no picture spots")
    }
}
