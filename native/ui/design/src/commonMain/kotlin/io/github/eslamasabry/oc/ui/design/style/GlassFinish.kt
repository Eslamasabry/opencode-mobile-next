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

        fun reads(a: Float): Boolean {
            for (face in listOf(fill, fillStart)) {
                for (under in spots) {
                    val seen = at(face, a).over(under.opaque())
                    for (i in texts.indices) if (wcagContrast(texts[i], seen) < targets[i] - 1e-6) return false
                }
            }
            return true
        }

        if (reads(0f)) return d
        var lo = 0f
        var hi = 1f
        repeat(STEPS) {
            val mid = (lo + hi) / 2f
            if (reads(mid)) hi = mid else lo = mid
        }
        return d.copy(fill = at(fill, hi), fillStart = at(fillStart, hi))
    }
}
