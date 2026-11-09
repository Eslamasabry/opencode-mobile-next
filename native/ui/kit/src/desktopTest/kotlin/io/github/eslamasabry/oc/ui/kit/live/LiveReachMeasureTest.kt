@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.design.effects.LiveReach
import io.github.eslamasabry.oc.ui.design.effects.LiveSpots
import io.github.eslamasabry.oc.ui.design.style.GlassFinish
import io.github.eslamasabry.oc.ui.design.style.GlassSpec
import io.github.eslamasabry.oc.ui.design.style.Knobs
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceRequest
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * glass-live (owner 2026-11-09, "why is glass not interacting with the background?"): Glass panes over a shader look are
 * only as thick as that look needs. Every look is rendered through the host's own shader code ([SkiaLiveLook]) at Bold,
 * blurred as the lightest clear pane frost and as the lightest liquid lens blur it, and its farthest pixel from the ground must
 * stay inside its [LiveReach] row; then every frosted pixel must leave the pane's words readable.
 *
 * With LIVE_REACH_MEASURE=1 it measures every pack instead of the guard's three and prints the table [LiveReach] holds
 * (and the pane and face tints before and after) instead of checking it.
 */
class LiveReachMeasureTest {
    private val looks = LiveLooks.ids.mapNotNull { LiveLooks.shader(it) }
    private val measuring = System.getenv("LIVE_REACH_MEASURE") == "1"

    /** A phone page in dp, rendered at one pixel per dp (the device renders the look at half its pixels: 1.3 px/dp). */
    private val w = 360
    private val h = 780
    private val bold = 1.6f
    private val moments = if (measuring) listOf(3f, 17f, 31f, 46f, 60f, 77f, 94f, 113f) else listOf(5f, 41f, 88f)
    private val packs = if (measuring) PackId.entries.toList() else listOf(PackId.Opencode, PackId.Solarized, PackId.Github)

    /** Light blur sigma of the lens in dp: the library's blur radius (2 dp) as a Gaussian sigma (radius × 0.577). */
    private val lensSigma = LiveReach.LENS_BLUR_DP * .57735f

    private data class Theme(val roles: Roles, val colors: KitColors) {
        val palette: LivePalette get() = livePaletteOf(colors)
    }

    private val themes = HashMap<Pair<PackId, Boolean>, Theme>()

    private fun theme(pack: PackId, dark: Boolean): Theme =
        themes.getOrPut(pack to dark) {
            var roles: Roles? = null
            var colors: KitColors? = null
            runDesktopComposeUiTest(10, 10) {
                setContent {
                    KitTheme(
                        KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, pack = pack, style = Style.Glass),
                        FakeKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        roles = LocalKitRoles.current
                        colors = LocalKitColors.current
                    }
                }
                waitForIdle()
            }
            Theme(requireNotNull(roles), requireNotNull(colors))
        }

    private fun rgb(c: Int) = floatArrayOf(((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f)

    /** The farthest pixel of [px] from [ground] (RGB distance, 0–1 channels): what liveOut caps. */
    private fun farthest(px: IntArray, ground: Int): Float {
        val g = rgb(ground)
        var m = 0f
        for (c in px) {
            val r = ((c shr 16) and 0xFF) / 255f - g[0]
            val gg = ((c shr 8) and 0xFF) / 255f - g[1]
            val b = (c and 0xFF) / 255f - g[2]
            val d = sqrt(r * r + gg * gg + b * b)
            if (d > m) m = d
        }
        return m
    }

    /** Each look's farthest frosted and lensed pixel as a share of the cap, dark and light, over [packs] and [moments]. */
    private fun measure(): Map<String, LiveReach.Row> =
        looks.associate { look ->
            val skia = SkiaLiveLook(look)
            val reach = FloatArray(4) // frostDark, frostLight, lensDark, lensLight
            for (pack in packs) for (dark in listOf(true, false)) {
                val t = theme(pack, dark)
                val ground = t.roles.ground.withAlpha(1f).v
                val cap = if (dark) LiveSpots.CAP_DARK else LiveSpots.CAP_LIGHT
                for (at in moments) {
                    val moment = SkiaLiveLook.Moment(at, bold, 1f)
                    val frost = farthest(skia.pixels(w, h, t.palette, moment, LiveReach.FROST_SIGMA_DP), ground) / cap
                    val lens = farthest(skia.pixels(w, h, t.palette, moment, lensSigma), ground) / cap
                    val i = if (dark) 0 else 1
                    reach[i] = maxOf(reach[i], frost)
                    reach[i + 2] = maxOf(reach[i + 2], lens)
                }
            }
            look.id to LiveReach.Row(reach[0], reach[1], reach[2], reach[3])
        }

    /**
     * A measured share as the table holds it: 15 % room, rounded up to a twentieth; a look within 8-bit dither of the cap
     * (up to [DITHER]) is held at the cap itself.
     */
    private fun tabled(x: Float): Float {
        val room = if (x <= DITHER) minOf(1f, x * LiveReach.MARGIN) else x * LiveReach.MARGIN
        return (ceil(room * 20f - 1e-4f) / 20f).coerceAtLeast(.05f)
    }

    /** One 8-bit step of liveOut's dither, as a share of the light cap (the smaller): what the light blur leaves of it. */
    private val DITHER = 1.05f

    @Test
    fun `B-KIT-LIVE-042 no look reaches past its measured row once a pane blurs it`() {
        val measured = measure()
        if (measuring) {
            printTable(measured)
            return
        }
        val misses =
            measured.flatMap { (id, m) ->
                val row = LiveReach.rows[id] ?: return@flatMap listOf("$id: no row")
                listOf("frostDark" to (m.frostDark to row.frostDark), "frostLight" to (m.frostLight to row.frostLight), "lensDark" to (m.lensDark to row.lensDark), "lensLight" to (m.lensLight to row.lensLight))
                    .filter { (_, v) -> v.first > maxOf(v.second, if (v.second >= 1f) DITHER else 0f) + 1e-4f }
                    .map { (k, v) -> "$id $k: measured ${v.first} > row ${v.second}" }
            }
        assertTrue(misses.isEmpty(), misses.joinToString("\n"))
        assertTrue(looks.all { it.id in LiveReach.rows }, "every look the kit draws has a measured row")
    }

    /**
     * The proof that matters: a clear content pane thickened over the look's own spots keeps text1, text2 and text3 at
     * their targets over every pixel the frosted look puts behind it, at Bold, light and dark (as GlassFinish measures:
     * both ends of the tint, under the sheen where the first line of words lies).
     */
    @Test
    fun `B-KIT-LIVE-043 a pane thinned for its look still reads over every frosted pixel of it`() {
        val failures = mutableListOf<String>()
        for (pack in if (measuring) packs else listOf(PackId.Opencode, PackId.Github)) for (dark in listOf(true, false)) {
            val t = theme(pack, dark)
            val roles = t.roles
            val ground = roles.ground.withAlpha(1f)
            for (look in looks) {
                val request = SurfaceRequest(roles, SurfaceRole.Content, Knobs(), SurfaceLevel.Surface1)
                val solid = request.levelFill.over(ground)
                val inks = GlassFinish.textInks(roles, solid)
                val spots = listOf(ground) + LiveSpots.forLook(roles, look.id, lens = false)
                val d = GlassFinish.thickened(GlassSpec.resolveClear(request)!!, roles, solid, spots, GlassFinish.paneInks(roles, solid), wordsDp = GlassFinish.PANE_WORDS_DP)
                val faces = listOfNotNull(d.fill, d.fillStart).distinct()
                val sheen = GlassFinish.sheenAt(d.sheen, GlassFinish.PANE_WORDS_DP)
                val skia = SkiaLiveLook(look)
                var worst = Double.MAX_VALUE
                for (at in listOf(12f, 64f)) {
                    val seen = HashSet<Int>()
                    skia.pixels(w, h, t.palette, SkiaLiveLook.Moment(at, bold, 1f), LiveReach.FROST_SIGMA_DP).forEach { seen += it }
                    for (c in seen) {
                        val under = Argb(c or (0xFF shl 24))
                        for (f in faces) {
                            val bare = f.over(under)
                            for (s in if (sheen.alpha == 0) listOf(bare) else listOf(bare, sheen.over(bare))) {
                                for ((ink, target) in inks) worst = minOf(worst, wcagContrast(ink, s) - target)
                            }
                        }
                    }
                }
                // A hair of tolerance for the frost's own rounding (the sampled pixels are 8-bit and dithered).
                if (worst < -.05) failures += "$pack dark=$dark ${look.id}: ${"%.3f".format(worst)} below target"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private fun printTable(measured: Map<String, LiveReach.Row>) {
        val out = StringBuilder()
        out.appendLine("LIVE-REACH measured (share of liveOut's cap), packs=${packs.size}, moments=${moments.size}, Bold")
        out.appendLine("id\tfrostDark\tfrostLight\tlensDark\tlensLight\t-> table")
        for ((id, m) in measured) {
            out.appendLine(
                "$id\t%.3f\t%.3f\t%.3f\t%.3f\t-> Row(%sf, %sf, %sf, %sf)".format(
                    m.frostDark, m.frostLight, m.lensDark, m.lensLight,
                    tabled(m.frostDark), tabled(m.frostLight), tabled(m.lensDark), tabled(m.lensLight),
                )
            )
        }
        // The tints before (the whole cap for every look) and after (the look's own row), on the default pack.
        out.appendLine("TINTS (Opencode): clear card pane fill alpha, liquid bar / composer face alpha; before -> after")
        for (dark in listOf(true, false)) {
            val roles = theme(PackId.Opencode, dark).roles
            val ground = roles.ground.withAlpha(1f)
            val request = SurfaceRequest(roles, SurfaceRole.Content, Knobs(), SurfaceLevel.Surface1)
            val solid = request.levelFill.over(ground)
            fun pane(spots: List<Argb>): Float {
                val d = GlassFinish.thickened(GlassSpec.resolveClear(request)!!, roles, solid, listOf(ground) + spots, GlassFinish.paneInks(roles, solid), wordsDp = GlassFinish.PANE_WORDS_DP)
                return (d.fill?.alpha ?: 0) / 255f
            }
            fun face(role: LiquidRole, picture: List<Argb>?): Float = LiquidEdge.faceTint(roles, role, picture).fill.alpha / 255f
            val before = pane(LiveSpots.of(roles))
            val barBefore = face(LiquidRole.Bar, null)
            val composerBefore = face(LiquidRole.Composer, null)
            out.appendLine("dark=$dark flat ground: bar ${"%.2f".format(face(LiquidRole.Bar, emptyList()))} composer ${"%.2f".format(face(LiquidRole.Composer, emptyList()))}")
            for ((id, m) in measured) {
                val row = LiveReach.Row(tabled(m.frostDark), tabled(m.frostLight), tabled(m.lensDark), tabled(m.lensLight))
                val frost = if (dark) row.frostDark else row.frostLight
                val lens = if (dark) row.lensDark else row.lensLight
                val after = pane(LiveSpots.of(roles, frost))
                val pic = LiveSpots.of(roles, lens)
                out.appendLine(
                    "dark=$dark $id\tpane %.2f -> %.2f\tbar %.2f -> %.2f\tcomposer %.2f -> %.2f".format(
                        before, after, barBefore, face(LiquidRole.Bar, pic), composerBefore, face(LiquidRole.Composer, pic),
                    )
                )
            }
        }
        println(out)
        System.getenv("LIVE_REACH_OUT")?.let { java.io.File(it).writeText(out.toString()) }
    }
}
