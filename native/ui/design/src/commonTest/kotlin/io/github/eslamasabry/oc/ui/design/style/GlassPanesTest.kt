package io.github.eslamasabry.oc.ui.design.style

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import io.github.eslamasabry.oc.ui.design.effects.AuroraImage
import io.github.eslamasabry.oc.ui.design.effects.EdgeHold
import io.github.eslamasabry.oc.ui.design.effects.FrostConditions
import io.github.eslamasabry.oc.ui.design.effects.FrostPolicy
import io.github.eslamasabry.oc.ui.design.effects.FrostStrength
import io.github.eslamasabry.oc.ui.design.effects.GlassEdges
import io.github.eslamasabry.oc.ui.design.effects.LiveSpots
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Glass's in-flow panes as clear frosted glass (owner 2026-11-08, option 1; no words plate, "get rid of the haze behind
 * the text"). Extends B-KIT-003 (contrast floors), B-KIT-018 (frost's holds) and B-KIT-037 (other styles untouched), and
 * B-KIT-005 (the Corners knob) for the owner's Corners report.
 */
class GlassPanesTest {
    private val panes =
        listOf(
            SurfaceRole.Content to SurfaceLevel.Surface1,
            SurfaceRole.Content to SurfaceLevel.Surface2,
            SurfaceRole.Control to SurfaceLevel.Surface3,
            SurfaceRole.Track to SurfaceLevel.Surface1,
            SurfaceRole.Field to SurfaceLevel.Surface1,
        )

    private fun roles(pack: PackId, dark: Boolean): Roles =
        StyleResolver.adjust(ThemeResolver.roles(pack, null, dark, saver = false, dynamic = null), Style.Glass)

    /**
     * What lies behind a pane on a Glass page: the ground, the painted aurora's darkest and brightest spots under the
     * clear panes' lighter blur, and, over a shader look (owner 2026-11-09: the panes now blur those too), the farthest
     * any look moves from the ground. Each pane is measured over the backdrop it can have.
     */
    private fun backdrops(roles: Roles): List<List<Argb>> {
        val ground = roles.ground.withAlpha(1f)
        return listOf(
            listOf(ground) + AuroraImage.clearSpotsOf(roles.ground, roles.accent, roles.dark),
            LiveSpots.of(roles.ground, roles.dark),
        )
    }

    /** Where a pane's words lie under its sheen (GlassFinish.sheenAt): a card's below the pane inset, a chip's anywhere. */
    private fun wordsDp(role: SurfaceRole): Float = if (role == SurfaceRole.Content) GlassFinish.PANE_WORDS_DP else 0f

    private fun finished(roles: Roles, role: SurfaceRole, level: SurfaceLevel, spots: List<Argb>, selected: Boolean = false): Pair<SurfaceDecoration, Argb> {
        val request = SurfaceRequest(roles, role, Knobs(), level, selected = selected)
        val solid = request.levelFill.over(roles.ground.withAlpha(1f))
        return GlassFinish.thickened(GlassSpec.resolveClear(request)!!, roles, solid, spots, wordsDp = wordsDp(role)) to solid
    }

    @Test
    fun `B-KIT-003 clear panes start much lighter than today's and every one stays a translucent pane`() {
        for (dark in listOf(true, false)) for ((role, _) in panes) {
            val clear = GlassSpec.clearOpacity(role, dark)
            assertTrue(clear in .14f..0.24f, "$role dark=$dark: $clear")
            assertTrue(clear < GlassSpec.opacity(role, dark) - .2f, "$role dark=$dark: much lighter than ${GlassSpec.opacity(role, dark)}")
        }
        // Owner 2026-11-09, "crisper": a moderate blur, so colour and shapes behind stay visible; a big pane a little more.
        for ((role, _) in panes) {
            val sigma = GlassSpec.clearFrostSigma(role)
            assertTrue(sigma in 8f..GlassSpec.PANE_FROST * .65f, "$role: moderate blur $sigma")
        }
        assertTrue(GlassSpec.clearFrostSigma(SurfaceRole.Content) >= GlassSpec.clearFrostSigma(SurfaceRole.Control), "a big pane keeps a little more frost")
    }

    @Test
    fun `B-KIT-003 on a clear pane text1, text2 and text3 read at 4,5 to 1 or Solid's with no plate, every pack, light and dark`() {
        val report = mutableListOf<String>()
        for (pack in PackId.entries) for (dark in listOf(true, false)) {
            val roles = roles(pack, dark)
            for ((role, level) in panes) for (chosen in listOf(false, true)) for ((b, spots) in backdrops(roles).withIndex()) {
                if (chosen && role != SurfaceRole.Content && role != SurfaceRole.Control) continue
                val (d, solid) = finished(roles, role, level, spots, chosen)
                val inks = GlassFinish.textInks(roles, solid)
                val sheen = GlassFinish.sheenAt(d.sheen, wordsDp(role))
                var worst = Double.MAX_VALUE
                for (face in listOfNotNull(d.fill, d.fillStart)) for (spot in spots) {
                    val bare = face.over(spot)
                    for (seen in listOf(bare, sheen.over(bare))) for ((ink, target) in inks) {
                        val c = wcagContrast(ink, seen)
                        worst = minOf(worst, c - target)
                        assertTrue(c >= target - 1e-3, "$pack dark=$dark $role/$level chosen=$chosen backdrop=$b: $c < $target")
                    }
                }
                val over = if (b == 0) "aurora" else "shader"
                report += "$pack ${if (dark) "dark" else "light"} $role/$level${if (chosen) " chosen" else ""} over $over: tint ${d.fill!!.alpha * 100 / 255}%, gloss ${if (d.sheen.alpha > 0) "kept" else "dropped"}, margin ${"%.2f".format(worst)}"
                // Still glass: no pane, chosen or not, falls back to an opaque face.
                assertTrue(d.fill!!.alpha < 255, "$pack dark=$dark $role/$level chosen=$chosen backdrop=$b: opaque")
            }
        }
        println(report.joinToString("\n"))
    }

    @Test
    fun `B-KIT-003 on a clear pane icons and a switch track's edge read at 3 to 1 or Solid's, every pack, light and dark`() {
        for (pack in PackId.entries) for (dark in listOf(true, false)) {
            val roles = roles(pack, dark)
            for ((role, level) in panes) for (spots in backdrops(roles)) {
                val (d, solid) = finished(roles, role, level, spots)
                for (face in listOfNotNull(d.fill, d.fillStart)) for (spot in spots) {
                    val seen = face.over(spot)
                    for ((ink, target) in GlassFinish.glyphInks(roles, solid)) {
                        val c = wcagContrast(ink.withAlpha(1f), seen)
                        assertTrue(c >= target - 1e-3, "$pack dark=$dark $role/$level icon $ink: $c < $target")
                    }
                }
            }
        }
    }

    @Test
    fun `B-KIT-003 a clear pane has no words plate drawn, a soft sheen and a lit rim`() {
        val roles = roles(PackId.Opencode, true)
        val d = GlassSpec.resolveClear(SurfaceRequest(roles, SurfaceRole.Content, Knobs()))!!
        assertTrue(d.sheen.alpha in 1..100)
        assertTrue(d.frost == GlassSpec.CLEAR_CONTENT_FROST)
        // Floating parts and sheets are liquid or keep their frost: resolveClear leaves them as they are.
        for (role in listOf(SurfaceRole.Floating, SurfaceRole.Sheet, SurfaceRole.Toggle, SurfaceRole.Code)) {
            val r = SurfaceRequest(roles, role, Knobs(), SurfaceLevel.Surface2)
            assertEquals(GlassSpec.resolve(r), GlassSpec.resolveClear(r), "$role")
        }
    }

    @Test
    fun `B-KIT-037 a chosen pane is accent-tinted glass, never an opaque slab, and an accent button keeps its fill`() {
        for (dark in listOf(true, false)) {
            val roles = roles(PackId.Opencode, dark)
            // The segmented control's chosen thumb and a chosen row pass a surface step as their own fill.
            for (own in listOf(null, roles.surface3, roles.surface2)) {
                val d = GlassSpec.resolve(SurfaceRequest(roles, SurfaceRole.Control, Knobs(), SurfaceLevel.Surface3, color = own, selected = true))!!
                assertTrue(d.fill!!.alpha < 255, "dark=$dark own=$own: translucent")
                assertEquals(roles.accent, d.edgeStart)
                assertEquals(GlassSpec.CHOSEN_RIM, d.edgeWidthDp)
                assertNotEquals(0f, d.frost)
                val plain = GlassSpec.resolve(SurfaceRequest(roles, SurfaceRole.Control, Knobs(), SurfaceLevel.Surface3))!!
                assertNotEquals(plain.fill!!.withAlpha(1f), d.fill!!.withAlpha(1f), "leans to the accent")
            }
            val button = GlassSpec.resolve(SurfaceRequest(roles, SurfaceRole.Control, Knobs(), color = roles.accent, selected = true))!!
            assertEquals(roles.accent, button.fill)
        }
    }

    @Test
    fun `B-KIT-018 clear panes need only the blur, every other hold keeps today's panes`() {
        val ready = FrostConditions(styleAllows = true, lensSupported = true)
        val b = FrostStrength.Balanced
        assertTrue(FrostPolicy.clearPanes(ready, b, GlassEdges.Liquid))
        assertTrue(FrostPolicy.clearPanes(ready.copy(lensSupported = false), b, GlassEdges.Liquid), "no lens: still clear")
        assertFalse(FrostPolicy.clearPanes(ready, b, GlassEdges.Frosted), "Glass edges: Frosted")
        assertFalse(FrostPolicy.clearPanes(ready, FrostStrength.Off, GlassEdges.Liquid), "frost Off")
        for (held in listOf(ready.copy(saver = true), ready.copy(reducedMotion = true), ready.copy(highContrast = true), ready.copy(styleAllows = false))) {
            assertFalse(FrostPolicy.clearPanes(held, b, GlassEdges.Liquid), "$held")
            assertNotEquals(null, FrostPolicy.edgesHeldBy(held, b, GlassEdges.Liquid))
        }
        assertEquals(EdgeHold.Platform, FrostPolicy.edgesHeldBy(ready.copy(lensSupported = false), b, GlassEdges.Liquid))
    }

    @Test
    fun `B-KIT-037 every style but Glass resolves exactly as before`() {
        // resolveClear exists on Glass only; the other specs' resolve is untouched by the clear panes and the chosen
        // glass (their chosen surfaces keep their own looks).
        for (style in Style.entries.filter { it != Style.Glass }) {
            val spec = StyleResolver.spec(style)
            val roles = StyleResolver.adjust(ThemeResolver.roles(PackId.Opencode, null, true, false, null), style)
            val d = spec.resolve(SurfaceRequest(roles, SurfaceRole.Content, spec.tokens.defaults))
            assertEquals(0, d?.sheen?.alpha ?: 0, "$style draws no sheen")
        }
    }

    // ---- Crisper glossy glass with good inside padding (owner 2026-11-09)

    @Test
    fun `B-KIT-003 a clear pane is crisp and glossy - no shadow halo, no soft top glow, a specular and a top sheen`() {
        for (dark in listOf(true, false)) {
            val roles = roles(PackId.Opencode, dark)
            for ((role, level) in panes.filter { it.first != SurfaceRole.Field }) {
                val d = GlassSpec.resolveClear(SurfaceRequest(roles, role, Knobs(), level))!!
                assertTrue(d.shadows.isEmpty(), "$role dark=$dark: no shadow halo")
                assertEquals(0, d.innerGlow.alpha, "$role dark=$dark: no soft glow inside the top")
                assertTrue(d.specular.alpha > d.sheen.alpha, "$role dark=$dark: a specular brighter than the sheen")
                assertTrue(d.specular.alpha < 255, "$role dark=$dark: restrained, never a solid white line")
                assertEquals(1f, d.edgeWidthDp, "$role dark=$dark: a thin 1 dp rim")
            }
            // A part's own fill (an accent button) keeps its face: no specular, no sheen.
            val button = GlassSpec.resolveClear(SurfaceRequest(roles, SurfaceRole.Control, Knobs(), color = roles.accent))!!
            assertEquals(0, button.specular.alpha)
            // Today's thick panes (held, or Glass edges Frosted) keep Flutter's look exactly: shadow and glow, no specular.
            val held = GlassSpec.resolve(SurfaceRequest(roles, SurfaceRole.Content, Knobs()))!!
            assertTrue(held.shadows.isNotEmpty())
            assertEquals(0, held.specular.alpha)
        }
    }

    @Test
    fun `B-KIT-003 the sheen fades out above a card's first line, which Glass's pane inset keeps clear of it`() {
        val roles = roles(PackId.Opencode, true)
        val sheen = GlassSpec.sheenOf(roles)
        assertEquals(sheen, GlassFinish.sheenAt(sheen, 0f), "a chip's words: the sheen at full strength")
        val atWords = GlassFinish.sheenAt(sheen, GlassFinish.PANE_WORDS_DP)
        assertTrue(atWords.alpha < sheen.alpha * .6f, "a card's words lie under less than 60 % of it: ${atWords.alpha}")
        assertEquals(0, GlassFinish.sheenAt(sheen, SurfaceDecoration.SHEEN_MAX_DP).alpha)
        assertTrue(GlassFinish.PANE_WORDS_DP >= 12f + GlassSpec.PANE_INSET)
    }

    @Test
    fun `B-KIT-037 only Glass insets its panes and grows their corners, and never under Sharp`() {
        for (style in Style.entries) {
            val tokens = StyleResolver.spec(style).tokens
            if (style == Style.Glass) {
                assertTrue(tokens.paneInsetDp in 4f..8f, "Glass: 4-8 dp of inner inset, ${tokens.paneInsetDp}")
                assertTrue(tokens.paneCornerBoostDp in 2f..tokens.paneInsetDp, "Glass: a slightly larger corner")
            } else {
                assertEquals(0f, tokens.paneInsetDp, "$style keeps its padding")
                assertEquals(0f, tokens.paneCornerBoostDp, "$style keeps its corners")
            }
            val panel = FlutterKitTokens.PANEL_CORNER_RADIUS.toFloat()
            for (corners in Corners.entries) {
                val plain = StyleResolver.corner(panel, corners, tokens.sharpRadius)
                val pane = StyleResolver.paneCorner(panel, corners, tokens)
                if (style != Style.Glass || corners == Corners.Sharp) assertEquals(plain, pane, "$style $corners")
                else assertTrue(pane > plain, "$style $corners: $pane > $plain")
            }
            assertEquals(999f, StyleResolver.paneCorner(999f, Corners.Round, tokens), "a stadium stays a stadium")
        }
    }

    @Test
    fun `B-KIT-003 over a shader look a pane measures the farthest any look moves from the ground`() {
        for (pack in PackId.entries) for (dark in listOf(true, false)) {
            val roles = roles(pack, dark)
            val spots = LiveSpots.of(roles.ground, dark)
            assertEquals(roles.ground.withAlpha(1f), spots.first())
            val cap = if (dark) LiveSpots.CAP_DARK else LiveSpots.CAP_LIGHT
            for (s in spots) {
                val d = kotlin.math.sqrt(
                    listOf(s.red - roles.ground.red, s.green - roles.ground.green, s.blue - roles.ground.blue)
                        .sumOf { (it / 255.0) * (it / 255.0) }
                )
                assertTrue(d <= cap + .01, "$pack dark=$dark: $s lies within the look's cap ($d)")
            }
            // Both ways: something brighter and something darker than the ground (unless the ground is at an end).
            assertTrue(spots.any { it.green > roles.ground.green } || roles.ground.green == 255)
        }
    }

    // ---- Corners (owner 2026-11-08: "the Corners knob is visibly broken")

    private val parts =
        mapOf(
            "card" to FlutterKitTokens.CARD_RADIUS.toFloat(),
            "row group" to FlutterKitTokens.PANEL_CORNER_RADIUS.toFloat(),
            "sheet" to FlutterKitTokens.SHEET_RADIUS.toFloat(),
            "segmented" to FlutterKitTokens.BUTTON_RADIUS.toFloat(),
            "chip" to 999f,
        )

    @Test
    fun `B-KIT-005 Round, Soft and Sharp are in that order on every part in every style`() {
        for (style in Style.entries) {
            val spec = StyleResolver.spec(style)
            val roles = StyleResolver.adjust(ThemeResolver.roles(PackId.Opencode, null, true, false, null), style)
            for ((part, token) in parts) {
                val r =
                    Corners.entries.associateWith { corners ->
                        val knobs = StyleResolver.knobsFor(style, FineTuneChoice(corners = corners))
                        assertEquals(corners, knobs.corners, "$style keeps the person's $corners")
                        val radius = StyleResolver.corner(token, knobs.corners, spec.tokens.sharpRadius)
                        // What the style draws for that radius (Paper and Signal square their own under Sharp).
                        spec.resolve(SurfaceRequest(roles, SurfaceRole.Content, knobs, radii = Radii.all(radius)))?.radii?.topStart ?: radius
                    }
                val round = r.getValue(Corners.Round)
                val soft = r.getValue(Corners.Soft)
                val sharp = r.getValue(Corners.Sharp)
                assertTrue(round >= soft && soft >= sharp, "$style $part: Round $round, Soft $soft, Sharp $sharp")
                assertTrue(round > sharp, "$style $part: Round $round is rounder than Sharp $sharp")
                if (token < 999f) assertTrue(round > soft && soft > sharp, "$style $part: three distinct steps")
            }
        }
    }

    @Test
    fun `B-KIT-005 a chosen ring is drawn on its own surface's outline`() {
        // Glass draws a chosen pane's accent rim inside its own face (no second ring on another shape), so ring and
        // surface share one set of radii under every Corners value.
        for (corners in Corners.entries) {
            val knobs = StyleResolver.knobsFor(Style.Glass, FineTuneChoice(corners = corners))
            val radius = StyleResolver.corner(FlutterKitTokens.CARD_RADIUS.toFloat(), knobs.corners, GlassSpec.tokens.sharpRadius)
            val roles = roles(PackId.Opencode, true)
            val d = GlassSpec.resolve(SurfaceRequest(roles, SurfaceRole.Content, knobs, radii = Radii.all(radius), selected = true))!!
            assertEquals(Radii.all(radius), d.radii)
            assertEquals(roles.accent, d.edgeStart)
            assertTrue(d.edgeWidthDp > 0f)
        }
    }
}
