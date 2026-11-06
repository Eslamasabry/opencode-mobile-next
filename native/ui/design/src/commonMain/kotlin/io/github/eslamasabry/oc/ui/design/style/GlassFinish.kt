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
    public fun thickened(d: SurfaceDecoration, roles: Roles, solid: Argb, spots: List<Argb>): SurfaceDecoration {
        val fill = d.fill ?: return d
        if (d.wordsPlate.alpha == 0 || spots.isEmpty()) return d
        val fillStart = d.fillStart ?: fill
        val texts = listOf(roles.text1, roles.text2, roles.text3) // FG: lib/ui/kit/kit_surface_look.dart:825
        val solidFace = solid.opaque()
        val targets = texts.map { minOf(FLOOR, wcagContrast(it, solidFace)) }
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

        fun reads(faces: List<Argb>, a: Float): Boolean {
            for (face in faces) {
                for (under in spots) {
                    val seen = at(face, a).over(under.opaque())
                    for (i in texts.indices) if (wcagContrast(texts[i], seen) < targets[i] - 1e-6) return false
                }
            }
            return true
        }

        // The thinnest tint at which [faces] read, or null when even the thickest does not.
        fun thinnest(faces: List<Argb>): Float? {
            if (reads(faces, 0f)) return 0f
            if (!reads(faces, 1f)) return null
            var lo = 0f
            var hi = 1f
            repeat(STEPS) {
                val mid = (lo + hi) / 2f
                if (reads(faces, mid)) hi = mid else lo = mid
            }
            return hi
        }

        val faced =
            thinnest(listOf(fill, fillStart))?.let { a ->
                if (a == 0f) d else d.copy(fill = at(fill, a), fillStart = at(fillStart, a))
            }
                // Native only (legibility over decoration): a dark pane thickens with the ground's own smoke, which
                // cannot darken it over the plain ground, so where the lit top-start corner (the sheen) still reads
                // below the target the corner goes, and the pane is its plain tint.
                ?: thinnest(listOf(fill))?.let { a -> d.copy(fill = at(fill, a), fillStart = at(fill, a)) }
                // Last: the Solid surface itself, opaque, which the target never asks more of.
                ?: d.copy(fill = solidFace, fillStart = solidFace)
        return withoutCostlyGlow(faced, texts, targets, spots)
    }

    /**
     * [d] without its top-edge glow where the glow, as strong as it is at a pane's first line of words (the kit's
     * compact padding, [FIRST_LINE_DP] down a [SurfaceDecoration.INNER_GLOW_DEPTH_DP] glow), would take a tone below
     * its target. Native only: a dark pane's light glow lowers the contrast of light words; a light pane's glow is
     * white milk and only helps dark words, so it stays.
     */
    private fun withoutCostlyGlow(d: SurfaceDecoration, texts: List<Argb>, targets: List<Double>, spots: List<Argb>): SurfaceDecoration {
        val glow = d.innerGlow
        if (glow.alpha == 0) return d
        val fill = d.fill ?: return d
        val share = 1f - FIRST_LINE_DP / SurfaceDecoration.INNER_GLOW_DEPTH_DP
        val atLine = glow.withAlpha(glow.alpha / 255f * share)
        for (face in listOf(fill, d.fillStart ?: fill)) {
            for (under in spots) {
                val seen = atLine.over(face.over(under.opaque()))
                for (i in texts.indices) if (wcagContrast(texts[i], seen) < targets[i] - 1e-6) return d.copy(innerGlow = Argb(0))
            }
        }
        return d
    }

    /** Where a pane's first line of words sits: the kit's compact surface padding (Space 3). */
    private const val FIRST_LINE_DP: Float = 12f // FG: lib/ui/kit/kit_surface.dart:187 lib/ui/kit/kit_tokens.dart:40-44
}
