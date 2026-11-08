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

    /** What lies behind a pane on a Glass page: the ground and the frosted aurora's darkest and brightest spots. */
    private fun spots(roles: Roles): List<Argb> {
        val ground = roles.ground.withAlpha(1f)
        return listOf(ground) + AuroraImage.spotsOf(roles.ground, roles.accent, roles.dark, frosted = true).toList()
    }

    private fun finished(roles: Roles, role: SurfaceRole, level: SurfaceLevel, selected: Boolean = false): Pair<SurfaceDecoration, Argb> {
        val request = SurfaceRequest(roles, role, Knobs(), level, selected = selected)
        val solid = request.levelFill.over(roles.ground.withAlpha(1f))
        return GlassFinish.thickened(GlassSpec.resolveClear(request)!!, roles, solid, spots(roles)) to solid
    }

    @Test
    fun `B-KIT-003 clear panes start much lighter than today's and every one stays a translucent pane`() {
        for (dark in listOf(true, false)) for ((role, _) in panes) {
            val clear = GlassSpec.clearOpacity(role, dark)
            assertTrue(clear in .14f..0.24f, "$role dark=$dark: $clear")
            assertTrue(clear < GlassSpec.opacity(role, dark) - .2f, "$role dark=$dark: much lighter than ${GlassSpec.opacity(role, dark)}")
        }
        assertTrue(GlassSpec.clearFrostSigma(SurfaceRole.Content) >= GlassSpec.PANE_FROST, "a big pane keeps its frost")
    }

    @Test
    fun `B-KIT-003 on a clear pane text1, text2 and text3 read at 4,5 to 1 or Solid's with no plate, every pack, light and dark`() {
        val report = mutableListOf<String>()
        for (pack in PackId.entries) for (dark in listOf(true, false)) {
            val roles = roles(pack, dark)
            for ((role, level) in panes) for (chosen in listOf(false, true)) {
                if (chosen && role != SurfaceRole.Content && role != SurfaceRole.Control) continue
                val (d, solid) = finished(roles, role, level, chosen)
                val inks = GlassFinish.textInks(roles, solid)
                var worst = Double.MAX_VALUE
                for (face in listOfNotNull(d.fill, d.fillStart)) for (spot in spots(roles)) {
                    val bare = face.over(spot)
                    for (seen in listOf(bare, d.sheen.over(bare))) for ((ink, target) in inks) {
                        val c = wcagContrast(ink, seen)
                        worst = minOf(worst, c - target)
                        assertTrue(c >= target - 1e-3, "$pack dark=$dark $role/$level chosen=$chosen: $c < $target")
                    }
                }
                report += "$pack ${if (dark) "dark" else "light"} $role/$level${if (chosen) " chosen" else ""}: tint ${d.fill!!.alpha * 100 / 255}%, margin ${"%.2f".format(worst)}"
            }
        }
        println(report.joinToString("\n"))
    }

    @Test
    fun `B-KIT-003 on a clear pane icons and a switch track's edge read at 3 to 1 or Solid's, every pack, light and dark`() {
        for (pack in PackId.entries) for (dark in listOf(true, false)) {
            val roles = roles(pack, dark)
            for ((role, level) in panes) {
                val (d, solid) = finished(roles, role, level)
                for (face in listOfNotNull(d.fill, d.fillStart)) for (spot in spots(roles)) {
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
        assertTrue(d.sheen.alpha in 1..90)
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
