package io.github.eslamasabry.oc.ui.design.style

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import kotlin.math.roundToInt

/**
 * What the kit does to a Glass pane after [GlassSpec] resolved it, so its words read (Dart
 * `KitSurfaceDecoration.thickenedOver`, `lib/ui/kit/kit_surface_look.dart:814-889` at GLASS_REF 1bbdc3f9c).
 *
 * A clear pane thickens its tint (dark: with the ground's own smoke, so it deepens rather than greys; light: with more of
 * its own milk) just enough that `text1`, `text2` and `text3` read on it over every spot that can lie behind it.
 *
 * The native port is stricter than Flutter on purpose (owner, 2026-11-06: words on Glass must read as on Solid):
 * - Flutter measures the words through the soft words plate (`KitTextHalo.cover` .55 of [SurfaceDecoration.wordsPlate]
 *   round each letter, `kit_surface_look.dart:848`). The native kit draws no halo behind the letters, so the words are
 *   measured on the pane itself.
 * - Flutter thickens only over a picture. Native also measures over the plain ground, so a pane never reads below the
 *   Solid surface it replaces.
 * - The target per tone is AA ([FLOOR]) or, where the Solid surface itself reads lower, what Solid reaches: Glass is
 *   never asked to beat Solid, and never allowed to read below it.
 * - Where no tint reaches the target (a dark pane's smoke cannot darken it over the plain ground), the lit top-start
 *   corner (the sheen) is dropped first, then the Solid surface itself is used. Quieter, never less legible.
 */
public object GlassFinish {
    /** AA (4.5:1) with Flutter's small margin. */
    public const val FLOOR: Double = 4.5 + .04 // FG: lib/ui/kit/kit_surface_look.dart:826

    /** Bisection steps (Flutter's; an alpha step finer than 8 bits). */
    private const val STEPS: Int = 14 // FG: lib/ui/kit/kit_surface_look.dart:859

    /**
     * [d] thickened so the words read on it over each of [spots] (opaque colours that may lie behind the pane: the
     * ground, and the backdrop's darkest and brightest spots when a picture is behind it). [solid] is the opaque Solid
     * surface the pane stands for. A decoration with no plate (a part's own fill) is returned as it is
     * (`kit_surface_look.dart:824`).
     */
    public fun thickened(
        d: SurfaceDecoration,
        roles: Roles,
        solid: Argb,
        spots: List<Argb>,
        inks: List<Pair<Argb, Double>>? = null,
        /**
         * How far below the face's top edge its first line of words lies at least, in dp; 0 (a part whose words may sit
         * anywhere: a chip, a liquid face) measures them under the sheen's full strength.
         */
        wordsDp: Float = 0f,
    ): SurfaceDecoration {
        val fill = d.fill ?: return d
        if (d.wordsPlate.alpha == 0 || spots.isEmpty()) return d
        val fillStart = d.fillStart ?: fill
        val solidFace = solid.opaque()
        val measured = inks ?: textInks(roles, solidFace)
        val texts = measured.map { it.first } // FG: lib/ui/kit/kit_surface_look.dart:825
        val targets = measured.map { it.second }
        val smoke = roles.ground.opaque() // FG: lib/ui/kit/kit_surface_look.dart:830

        fun at(face: Argb, a: Float): Argb {
            val fa = face.alpha / 255f
            if (!roles.dark) return face.withAlpha(if (a > fa) a else fa) // FG: lib/ui/kit/kit_surface_look.dart:832
            val out = 1f - (1f - fa) * (1f - a) // FG: lib/ui/kit/kit_surface_look.dart:833
            if (out <= 0f) return face
            val k = fa / out // FG: lib/ui/kit/kit_surface_look.dart:835
            fun ch(f: Int, s: Int) = (f * k + s * (1f - k)).roundToInt().coerceIn(0, 255)
            val alpha = (out * 255f).roundToInt().coerceIn(0, 255)
            return Argb((alpha shl 24) or (ch(face.red, smoke.red) shl 16) or (ch(face.green, smoke.green) shl 8) or ch(face.blue, smoke.blue))
        }

        // A clear pane's top sheen lies over the first line of words (it lightens: it can cost light words in a dark
        // theme), so the words must read both under it and below it.
        fun reads(faces: List<Argb>, a: Float, sheen: Argb): Boolean {
            for (face in faces) {
                for (under in spots) {
                    val bare = at(face, a).over(under.opaque())
                    for (seen in if (sheen.alpha == 0) listOf(bare) else listOf(bare, sheen.over(bare))) {
                        for (i in texts.indices) if (wcagContrast(texts[i], seen) < targets[i] - 1e-6) return false
                    }
                }
            }
            return true
        }

        // The thinnest tint at which [faces] read, or null when even the thickest does not.
        fun thinnest(faces: List<Argb>, sheen: Argb = sheenAt(d.sheen, wordsDp)): Float? = leastAlpha { reads(faces, it, sheen) }

        // A clear pane (one with a sheen) is clear first, glossy second (owner 2026-11-08: much lighter): where its lit
        // corner and sheen would cost more than [GLOSS_ALLOWANCE] of extra tint, they go and the pane stays thin.
        if (d.sheen.alpha != 0) {
            val glossy = thinnest(listOf(fill, fillStart))
            val plain = thinnest(listOf(fill), Argb(0))
            if (plain != null && (glossy == null || glossy > plain + GLOSS_ALLOWANCE)) {
                val thin = d.copy(fill = at(fill, plain), fillStart = at(fill, plain), sheen = Argb(0))
                return withoutCostlyGlow(thin, texts, targets, spots, wordsDp)
            }
        }
        val faced =
            thinnest(listOf(fill, fillStart))?.let { a ->
                if (a == 0f) d else d.copy(fill = at(fill, a), fillStart = at(fillStart, a))
            }
                // Native only (legibility over decoration): a dark pane thickens with the ground's own smoke, which
                // cannot darken it over the plain ground, so where the lit top-start corner (the sheen) still reads
                // below the target the corner goes, and the pane is its plain tint.
                ?: thinnest(listOf(fill))?.let { a -> d.copy(fill = at(fill, a), fillStart = at(fill, a)) }
                // Then a clear pane's sheen goes (it lightens the first line).
                ?: thinnest(listOf(fill), Argb(0))?.let { a -> d.copy(fill = at(fill, a), fillStart = at(fill, a), sheen = Argb(0)) }
                // Last: the Solid surface itself, opaque, which the target never asks more of.
                ?: d.copy(fill = solidFace, fillStart = solidFace, sheen = Argb(0))
        return withoutCostlyGlow(faced, texts, targets, spots, wordsDp)
    }

    /**
     * [sheen] as strong as it still is [wordsDp] below a face's top edge: it fades out over at most
     * [SurfaceDecoration.SHEEN_MAX_DP] (a short face fades it sooner), so words that far down lie under at most this much
     * of it. Glass's pane inset keeps a pane's first line clear of most of it (owner 2026-11-09).
     */
    public fun sheenAt(sheen: Argb, wordsDp: Float): Argb {
        if (sheen.alpha == 0 || wordsDp <= 0f) return sheen
        val share = (1f - wordsDp / SurfaceDecoration.SHEEN_MAX_DP).coerceIn(0f, 1f)
        return sheen.withAlpha(sheen.alpha / 255f * share)
    }

    /** Where a Glass pane's first line of words lies at least: the kit's compact padding plus Glass's pane inset. */
    public val PANE_WORDS_DP: Float get() = FIRST_LINE_DP + GlassSpec.PANE_INSET

    /**
     * [d] without its top-edge glow where the glow, as strong as it is at a pane's first line of words (the kit's
     * compact padding, [FIRST_LINE_DP] down a [SurfaceDecoration.INNER_GLOW_DEPTH_DP] glow), would take a tone below
     * its target. Native only: a dark pane's light glow lowers the contrast of light words; a light pane's glow is
     * white milk and only helps dark words, so it stays.
     */
    private fun withoutCostlyGlow(d: SurfaceDecoration, texts: List<Argb>, targets: List<Double>, spots: List<Argb>, wordsDp: Float): SurfaceDecoration {
        val glow = d.innerGlow
        if (glow.alpha == 0) return d
        val fill = d.fill ?: return d
        val share = (1f - maxOf(FIRST_LINE_DP, wordsDp) / SurfaceDecoration.INNER_GLOW_DEPTH_DP).coerceAtLeast(0f)
        val atLine = glow.withAlpha(glow.alpha / 255f * share)
        for (face in listOf(fill, d.fillStart ?: fill)) {
            for (under in spots) {
                val seen = atLine.over(face.over(under.opaque()))
                for (i in texts.indices) if (wcagContrast(texts[i], seen) < targets[i] - 1e-6) return d.copy(innerGlow = Argb(0))
            }
        }
        return d
    }

    /**
     * The least alpha in 0..1 at which [reads] holds, by [STEPS] halvings (finer than 8 bits); null when even 1 does not.
     * The one bisection every Glass legibility measure uses: an in-flow pane's tint ([thickened]) and a liquid pane's
     * ([io.github.eslamasabry.oc.ui.design.effects.LiquidEdge.face]).
     */
    public fun leastAlpha(reads: (Float) -> Boolean): Float? {
        if (reads(0f)) return 0f
        if (!reads(1f)) return null
        var lo = 0f
        var hi = 1f
        repeat(STEPS) {
            val mid = (lo + hi) / 2f
            if (reads(mid)) hi = mid else lo = mid
        }
        return hi
    }

    /** text1, text2 and text3 with their targets: AA ([FLOOR]), or what the Solid surface [solid] reaches if less. */
    public fun textInks(roles: Roles, solid: Argb): List<Pair<Argb, Double>> =
        listOf(roles.text1, roles.text2, roles.text3).map { it to minOf(FLOOR, wcagContrast(it, solid.opaque())) }

    /** Non-text contrast (WCAG 1.4.11, 3:1) with this file's small margin: icons, a switch's track, a field's edge. */
    public const val NON_TEXT_FLOOR: Double = 3.0 + .04

    /**
     * What else must read on a clear in-flow pane: its icons (text1, text2 and the accent) and the edge of a switch's
     * track or a field ([styleBoundary]), each at [NON_TEXT_FLOOR] or what Solid reaches if less. The words ([textInks],
     * 4.5:1) ask more of the tint than these, so a pane [thickened] for its words carries them too (tested).
     */
    public fun glyphInks(roles: Roles, solid: Argb): List<Pair<Argb, Double>> =
        listOf(roles.text1, roles.text2, roles.accent.opaque(), styleBoundary(roles))
            .map { it to minOf(NON_TEXT_FLOOR, wcagContrast(it, solid.opaque())) }

    /**
     * What a clear in-flow pane is thickened for: its words ([textInks]) and its icons and track edges ([glyphInks]).
     * Over the shader looks' wider spots (owner 2026-11-09: panes now blur them) a switch track's edge can ask more of
     * the tint than the words do, so a clear pane measures both.
     */
    public fun paneInks(roles: Roles, solid: Argb): List<Pair<Argb, Double>> = textInks(roles, solid) + glyphInks(roles, solid)

    /** How much extra tint a clear pane's gloss (its lit corner and sheen) may cost before it goes. */
    public const val GLOSS_ALLOWANCE: Float = .06f

    /** Where a pane's first line of words sits: the kit's compact surface padding (Space 3). */
    private const val FIRST_LINE_DP: Float = 12f // FG: lib/ui/kit/kit_surface.dart:187 lib/ui/kit/kit_tokens.dart:40-44
}
