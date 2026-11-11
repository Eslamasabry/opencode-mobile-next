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
    fun `B-KIT-003 1b the dark page's chroma stays at the quiet plate's, light and the thumbnails unchanged`() {
        // The note asked .20; measured, any step above .14 costs words on today's clear panes (see AuroraImage.CHROMA_DARK
        // and the margin table printed below), so it is held until the owner decides whether words may pay.
        assertEquals(AuroraImage.CHROMA_DARK_QUIET, AuroraImage.CHROMA_DARK)
        assertEquals(.14f, AuroraImage.CHROMA_DARK_QUIET)
        assertEquals(.12f, AuroraImage.CHROMA_LIGHT)
        assertEquals(.14f, AuroraImage.thumbnailChromaFor(dark = true), "a look's thumbnail keeps the quiet plate")
        assertEquals(.12f, AuroraImage.thumbnailChromaFor(dark = false))
        assertEquals(AuroraImage.CHROMA_DARK, AuroraImage.chromaFor(dark = true))
    }

    @Test
    fun `B-KIT-003 1b two still soft lights in page fractions, dark only, full at the centre and gone at the rim`() {
        assertEquals(listOf(AuroraImage.Light(.15f, .25f, .45f), AuroraImage.Light(.70f, .55f, .40f)), AuroraImage.LIGHTS_DARK)
        // Drawn only while they add light (none for now, see AuroraImage.LIGHT_PEAK_LUMA).
        assertEquals(if (AuroraImage.LIGHT_PEAK_LUMA > 0f) AuroraImage.LIGHTS_DARK else emptyList(), AuroraImage.lightsFor(dark = true))
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
            assertEquals(1f, AuroraImage.lightAt(.15f * w, .25f * h, w, h), .001f)
            assertEquals(0f, AuroraImage.lightAt(.15f * w, .25f * h, w, h, AuroraImage.lightsFor(dark = false)))
        }
    }

    @Test
    fun `B-KIT-003 1b the note's light adds about 10 percent luma at its centre, in the accent's hue`() {
        for (pack in PackId.entries) {
            val accent = roles(pack, true).accent.withAlpha(1f)
            val light = AuroraImage.lightColorFor(accent, AuroraImage.NOTE_LIGHT_PEAK_LUMA)
            val luma = (.2126 * light.red + .7152 * light.green + .0722 * light.blue) / 255.0
            assertTrue(luma <= AuroraImage.NOTE_LIGHT_PEAK_LUMA + .006, "$pack: at most about .10 ($luma)")
            assertTrue(luma >= AuroraImage.NOTE_LIGHT_PEAK_LUMA * .6, "$pack: a light you can see ($luma)")
            // The accent's hue: the channels keep the accent's order.
            val order = listOf(accent.red, accent.green, accent.blue).withIndex().sortedBy { it.value }.map { it.index }
            val lit = listOf(light.red, light.green, light.blue)
            assertTrue(lit[order[2]] >= lit[order[0]], "$pack: the accent's brightest channel stays brightest")
        }
    }

    @Test
    fun `B-KIT-003 1b words do not pay - the spots stay at the quiet plate's, so no pane or liquid face is thicker`() {
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
                // Uncapped, the note's brighter page (.20) would have moved them.
                assertNotEquals(
                    AuroraImage.clearSpotsOf(r.ground, r.accent, dark, chroma = .20f),
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
                    most = maxOf(most, AuroraImage.lightAt(x, y, w, h))
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

    /**
     * The worst margin (contrast less its target) of a clear pane's words and icons, the pane thickened as the kit
     * thickens it (over the capped spots), over the page as drawn (chroma [AuroraImage.CHROMA_DARK]) with [share] of a
     * light's peak added on every spot.
     */
    private fun clearPaneMargin(pack: PackId, id: GlassLookId, role: SurfaceRole, level: SurfaceLevel, chosen: Boolean, share: Float, chroma: Float = AuroraImage.CHROMA_DARK, peak: Float = AuroraImage.LIGHT_PEAK_LUMA): Pair<Double, String> {
        val r = roles(pack, true)
        val ground = r.ground.withAlpha(1f)
        val measured = listOf(ground) + AuroraImage.clearSpotsOf(r.ground, r.accent, true)
        val drawn = listOf(ground) + AuroraImage.clearSpotsOf(r.ground, r.accent, true, chroma = chroma)
        val behind = drawn + drawn.map { AuroraImage.lit(it, r.accent, share, peak) }
        val request = SurfaceRequest(r, role, Knobs(), level, selected = chosen, look = GlassLooks.of(id).spec)
        val solid = request.levelFill.over(ground)
        val wordsDp = if (role == SurfaceRole.Content) GlassFinish.PANE_WORDS_DP else 0f
        val d = GlassFinish.thickened(GlassSpec.resolveClear(request)!!, r, solid, measured, GlassFinish.paneInks(r, solid), wordsDp = wordsDp)
        val sheen = GlassFinish.sheenAt(d.sheen, wordsDp)
        var worst = Double.MAX_VALUE
        var what = ""
        for (face in listOfNotNull(d.fill, d.fillStart)) for (spot in behind) {
            val bare = face.over(spot)
            for (seen in listOf(bare, sheen.over(bare))) for ((ink, target) in GlassFinish.textInks(r, solid)) {
                val m = wcagContrast(ink, seen) - target
                if (m < worst) { worst = m; what = "words $ink over $spot (target $target)" }
            }
            for ((ink, target) in GlassFinish.glyphInks(r, solid)) {
                val m = wcagContrast(ink.withAlpha(1f), bare) - target
                if (m < worst) { worst = m; what = "icon $ink over $spot (target $target)" }
            }
        }
        return worst to what
    }

    @Test
    fun `B-KIT-003 1b on the brighter page, at the brightest light, words and icons on a clear pane still read`() {
        println("glass-in-the-kit 1b: the brightest light adds ${"%.2f".format(brightestLight)} of a light's peak")
        // What the page may carry before any clear pane's words or icons fall below their target (the pane as thick as
        // the capped spots make it): per chroma, the worst margin with no light, and the most light (as a share of a
        // light's peak at the note's .10) every pane still reads under. Printed for the owner's call; the assertion below
        // is on the page as shipped (AuroraImage.CHROMA_DARK, AuroraImage.LIGHT_PEAK_LUMA) at the brightest point.
        val cases = buildList {
            for (pack in PackId.entries) for (id in GlassLookId.entries) for ((role, level) in panes) for (chosen in listOf(false, true)) {
                if (chosen && role != SurfaceRole.Content && role != SurfaceRole.Control) continue
                add(listOf(pack, id, role, level, chosen))
            }
        }
        fun margin(c: List<Any>, share: Float, chroma: Float) =
            clearPaneMargin(c[0] as PackId, c[1] as GlassLookId, c[2] as SurfaceRole, c[3] as SurfaceLevel, c[4] as Boolean, share, chroma, AuroraImage.NOTE_LIGHT_PEAK_LUMA)
        for (step in 0..6) {
            val chroma = AuroraImage.CHROMA_DARK_QUIET + step * .01f
            var bare = Double.MAX_VALUE
            var bareAt = ""
            var most = Float.MAX_VALUE
            var mostAt = ""
            for (c in cases) {
                val (m, why) = margin(c, 0f, chroma)
                if (m < bare) { bare = m; bareAt = "$c: $why" }
                if (m < -1e-3) continue
                var lo = 0f
                var hi = 2f
                repeat(14) {
                    val mid = (lo + hi) / 2f
                    if (margin(c, mid, chroma).first >= -1e-3) lo = mid else hi = mid
                }
                if (lo < most) { most = lo; mostAt = "$c: ${margin(c, hi, chroma).second}" }
            }
            println(
                "glass-in-the-kit 1b: chroma ${"%.2f".format(chroma)}: no light, worst margin ${"%.3f".format(bare)} ($bareAt); " +
                    "panes that read with no light read under up to ${"%.4f".format(most)} of a light's peak " +
                    "(peak luma ${"%.4f".format(most * AuroraImage.NOTE_LIGHT_PEAK_LUMA / maxOf(brightestLight, 1f))} per light; tightest $mostAt)",
            )
        }
        for (pack in PackId.entries) for (id in GlassLookId.entries) for ((role, level) in panes) for (chosen in listOf(false, true)) {
            if (chosen && role != SurfaceRole.Content && role != SurfaceRole.Control) continue
            val (m, why) = clearPaneMargin(pack, id, role, level, chosen, brightestLight)
            assertTrue(m >= -1e-3, "$id $pack $role/$level chosen=$chosen at the brightest light: $why, margin $m")
        }
    }

    /** One clear pane as the kit draws it today: thickened over the capped spots with today's inks. */
    private class Pane(val name: String, val roles: Roles, val d: SurfaceDecoration, val sheen: Argb, val words: List<Pair<Argb, Double>>, val icons: List<Pair<Argb, Double>>)

    private val clearPanes: List<Pane> by lazy {
        buildList {
            for (pack in PackId.entries) for (id in GlassLookId.entries) for ((role, level) in panes) for (chosen in listOf(false, true)) {
                if (chosen && role != SurfaceRole.Content && role != SurfaceRole.Control) continue
                val r = roles(pack, true)
                val ground = r.ground.withAlpha(1f)
                val measured = listOf(ground) + AuroraImage.clearSpotsOf(r.ground, r.accent, true)
                val request = SurfaceRequest(r, role, Knobs(), level, selected = chosen, look = GlassLooks.of(id).spec)
                val solid = request.levelFill.over(ground)
                val wordsDp = if (role == SurfaceRole.Content) GlassFinish.PANE_WORDS_DP else 0f
                val d = GlassFinish.thickened(GlassSpec.resolveClear(request)!!, r, solid, measured, GlassFinish.paneInks(r, solid), wordsDp = wordsDp)
                add(Pane("$id $pack $role/$level chosen=$chosen", r, d, GlassFinish.sheenAt(d.sheen, wordsDp), GlassFinish.textInks(r, solid), GlassFinish.glyphInks(r, solid)))
            }
        }
    }

    /** What lies behind [p] on the page drawn at [chroma] with lights of [peak] luma, at the brightest point. */
    private fun behind(p: Pane, chroma: Float, peak: Float): List<Argb> {
        val drawn = listOf(p.roles.ground.withAlpha(1f)) + AuroraImage.clearSpotsOf(p.roles.ground, p.roles.accent, true, chroma = chroma)
        return if (peak <= 0f) drawn else drawn + drawn.map { AuroraImage.lit(it, p.roles.accent, brightestLight, peak) }
    }

    /** The worst margin of [p]'s words, its secondary and tertiary inks lifted [lift] of the way to its primary. */
    private fun wordsMargin(p: Pane, lift: Float, chroma: Float, peak: Float): Pair<Double, String> {
        var worst = Double.MAX_VALUE
        var what = ""
        val inks = GlassSpec.liftedInks(p.roles, lift)
        for (face in listOfNotNull(p.d.fill, p.d.fillStart)) for (spot in behind(p, chroma, peak)) {
            val bare = face.over(spot)
            for (seen in listOf(bare, p.sheen.over(bare))) for ((i, pair) in p.words.withIndex()) {
                val m = wcagContrast(inks[i], seen) - pair.second
                if (m < worst) { worst = m; what = "${p.name}: ink ${inks[i]} over $spot (target ${pair.second})" }
            }
        }
        return worst to what
    }

    /** The worst margin of [p]'s icons and track edges (drawn in today's inks, not lifted). */
    private fun iconsMargin(p: Pane, chroma: Float, peak: Float): Pair<Double, String> {
        var worst = Double.MAX_VALUE
        var what = ""
        for (face in listOfNotNull(p.d.fill, p.d.fillStart)) for (spot in behind(p, chroma, peak)) for ((ink, target) in p.icons) {
            val m = wcagContrast(ink.withAlpha(1f), face.over(spot)) - target
            if (m < worst) { worst = m; what = "${p.name}: icon $ink over $spot (target $target)" }
        }
        return worst to what
    }

    @Test
    fun `B-KIT-003 1b' report - the brightest light the clear panes read under, per secondary ink lift, at chroma 20 percent`() {
        fun most(ok: (Float) -> Boolean): Float {
            if (!ok(0f)) return -1f
            var lo = 0f
            var hi = .2f
            repeat(14) { val mid = (lo + hi) / 2f; if (ok(mid)) lo = mid else hi = mid }
            return lo
        }
        val icons = most { peak -> clearPanes.all { iconsMargin(it, .20f, peak).first >= -1e-3 } }
        val iconsWorst = clearPanes.map { iconsMargin(it, .20f, .10f) }.minBy { it.first }
        println("glass-in-the-kit 1b': icons read under a peak of up to ${"%.4f".format(icons)} (at .10: ${"%.3f".format(iconsWorst.first)}, ${iconsWorst.second})")
        for (step in 0..10) {
            val lift = step * .05f
            val words = most { peak -> clearPanes.all { wordsMargin(it, lift, .20f, peak).first >= -1e-3 } }
            val worst = clearPanes.map { wordsMargin(it, lift, .20f, .08f) }.minBy { it.first }
            val hierarchy = clearPanes.minOf { p -> listOfNotNull(p.d.fill, p.d.fillStart).minOf { f -> wcagContrast(p.roles.text1, GlassSpec.liftedInks(p.roles, lift)[1].over(f.over(p.roles.ground.withAlpha(1f)))) } }
            println(
                "glass-in-the-kit 1b': lift ${"%.2f".format(lift)}: words read under a peak of up to ${"%.4f".format(words)}; " +
                    "at .08 the worst margin ${"%.3f".format(worst.first)} (${worst.second}); text1/text2 ratio at least ${"%.2f".format(hierarchy)}",
            )
        }
    }

    /**
     * What letting the words pay would cost (for the owner's call, not a pass/fail): how much thicker each clear pane's
     * tint would have to be to read over the page as the design note drew it (chroma .20 and a .10 light at the
     * brightest point), against the tint it has today.
     */
    @Test
    fun `B-KIT-003 1b report - how much thicker the clear panes would be for the note's page`() {
        var most = 0f
        var at = ""
        val tints = mutableListOf<Float>()
        for (pack in PackId.entries) for (id in GlassLookId.entries) for ((role, level) in panes) {
            val r = roles(pack, true)
            val ground = r.ground.withAlpha(1f)
            val today = listOf(ground) + AuroraImage.clearSpotsOf(r.ground, r.accent, true)
            val drawn = listOf(ground) + AuroraImage.clearSpotsOf(r.ground, r.accent, true, chroma = .20f)
            val noted = drawn + drawn.map { AuroraImage.lit(it, r.accent, brightestLight, AuroraImage.NOTE_LIGHT_PEAK_LUMA) }
            val request = SurfaceRequest(r, role, Knobs(), level, look = GlassLooks.of(id).spec)
            val solid = request.levelFill.over(ground)
            val wordsDp = if (role == SurfaceRole.Content) GlassFinish.PANE_WORDS_DP else 0f
            fun tint(spots: List<Argb>) =
                GlassFinish.thickened(GlassSpec.resolveClear(request)!!, r, solid, spots, GlassFinish.paneInks(r, solid), wordsDp = wordsDp).fill!!.alpha / 255f
            val a = tint(today)
            val b = tint(noted)
            if (id == GlassLookId.Glossy && role == SurfaceRole.Content && level == SurfaceLevel.Surface1) tints += b - a
            if (b - a > most) { most = b - a; at = "$id $pack $role/$level: ${"%.2f".format(a)} -> ${"%.2f".format(b)}" }
        }
        tints.sort()
        println(
            "glass-in-the-kit 1b report: for the note's page the clear panes would thicken by up to ${"%.2f".format(most)} ($at); " +
                "the home's list panel (Glossy Content/Surface1) by a median ${"%.2f".format(tints[tints.size / 2])} over the packs",
        )
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

    @Test
    fun `B-KIT-003 1b' the page's ink lift applies only on a dark Glass page painted clear, and keeps the hierarchy`() {
        assertEquals(GlassSpec.PAGE_SECOND_INK_LIFT, GlassSpec.pageInkLift(dark = true, paintedClear = true))
        assertEquals(0f, GlassSpec.pageInkLift(dark = true, paintedClear = false), "the fallback keeps its inks")
        assertEquals(0f, GlassSpec.pageInkLift(dark = false, paintedClear = true), "the light page has no light to answer")
        assertTrue(GlassSpec.PAGE_SECOND_INK_LIFT in 0f..LiftLimit, "at most half way to the primary ink")
        // The secondary words stay visibly below the primary ones on every clear pane's face.
        for (p in clearPanes) for (f in listOfNotNull(p.d.fill, p.d.fillStart)) {
            val face = f.over(p.roles.ground.withAlpha(1f))
            val inks = GlassSpec.liftedInks(p.roles, GlassSpec.PAGE_SECOND_INK_LIFT)
            val ratio = wcagContrast(inks[0], inks[1].over(face))
            assertTrue(ratio >= 1.3 - 1e-3, "${p.name}: text1 / lifted text2 only $ratio")
        }
        // The lifted inks are the plain ones at no lift.
        val r = roles(PackId.Opencode, true)
        assertEquals(listOf(r.text1, r.text2, r.text3), GlassSpec.liftedInks(r, 0f))
    }

    private val LiftLimit = .5f

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
