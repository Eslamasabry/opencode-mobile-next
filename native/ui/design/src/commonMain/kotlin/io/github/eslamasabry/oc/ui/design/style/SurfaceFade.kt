package io.github.eslamasabry.oc.ui.design.style

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.lerp as lerpColor

/**
 * The cross-fade between two surface looks (Dart `KitSurfaceDecoration.lerpSurface`,
 * `lib/ui/kit/kit_surface_look.dart:718-760` at GLASS_REF 1bbdc3f9c). A change of Style fades every surface over
 * [STYLE_FADE_MS]; a state change inside Glass over [STATE_FADE_MS]; reduced motion and motion Off change at once
 * (`kit_surface_look.dart:1210-1220`). Pure data: the kit animates `t` and paints the result.
 */
public object SurfaceFade {
    /** A change of Style. */
    public const val STYLE_FADE_MS: Int = 200 // FG: lib/ui/kit/kit_surface_look.dart:92

    /** A change of state (pressed, chosen) inside one style. */
    public const val STATE_FADE_MS: Int = 120 // FG: lib/ui/kit/kit_surface_look.dart:93

    /** [a] at `t = 0` to [b] at `t = 1`, field by field as Flutter lerps it. */
    public fun between(a: SurfaceDecoration, b: SurfaceDecoration, t: Float): SurfaceDecoration {
        if (t <= 0f) return a
        if (t >= 1f) return b
        fun c(x: Argb?, y: Argb?): Argb? =
            when {
                x == null && y == null -> null
                // A colour fades in from its own hue at alpha 0 (Flutter's copyTransparent keeps the hue,
                // kit_surface_look.dart:1011-1019), never from transparent black.
                x == null -> lerpColor(y!!.withAlpha(0f), y, t)
                y == null -> lerpColor(x, x.withAlpha(0f), t)
                else -> lerpColor(x, y, t)
            }
        fun d(x: Float, y: Float): Float = x + (y - x) * t
        // A distance that is 0 on one side takes the other's, so a shadow fades rather than slides
        // (kit_surface_look.dart:736-753).
        fun keep(x: Float, y: Float): Float = if (x == 0f) y else if (y == 0f) x else d(x, y)
        return SurfaceDecoration(
            radii = Radii(d(a.radii.topStart, b.radii.topStart), d(a.radii.topEnd, b.radii.topEnd), d(a.radii.bottomStart, b.radii.bottomStart), d(a.radii.bottomEnd, b.radii.bottomEnd)),
            circle = if (t < .5f) a.circle else b.circle,
            fill = c(a.fill, b.fill),
            fillStart = c(a.fillStart ?: a.fill, b.fillStart ?: b.fill),
            edgeStart = c(a.edgeStart ?: a.edgeEnd, b.edgeStart ?: b.edgeEnd),
            edgeEnd = c(a.edgeEnd ?: a.edgeStart, b.edgeEnd ?: b.edgeStart),
            edgeWidthDp = d(a.edgeWidthDp, b.edgeWidthDp),
            shadows = shadows(a.shadows, b.shadows, t),
            paired = paired(a.paired, b.paired, t),
            hardShadow = c(a.hardShadow, b.hardShadow),
            hardOffsetDp = keep(a.hardOffsetDp, b.hardOffsetDp),
            faceShiftDp = d(a.faceShiftDp, b.faceShiftDp),
            wordsPlate = if (t < .5f) a.wordsPlate else b.wordsPlate, // FG: lib/ui/kit/kit_surface_look.dart:755
            frost = if (t < .5f) a.frost else b.frost, // FG: lib/ui/kit/kit_surface_look.dart:757
            innerGlow = c(a.innerGlow, b.innerGlow) ?: Argb(0),
            sheen = c(a.sheen, b.sheen) ?: Argb(0),
            specular = c(a.specular, b.specular) ?: Argb(0),
            glint = d(a.glint, b.glint),
        )
    }

    /** Flutter `BoxShadow.lerpList`: pairs lerp; a shadow on one side only shrinks to nothing (`BoxShadow.scale`). */
    private fun shadows(a: List<Shadow>, b: List<Shadow>, t: Float): List<Shadow> {
        val n = maxOf(a.size, b.size)
        return List(n) { i ->
            val x = a.getOrNull(i)
            val y = b.getOrNull(i)
            when {
                x != null && y != null ->
                    Shadow(
                        x.dxDp + (y.dxDp - x.dxDp) * t,
                        x.dyDp + (y.dyDp - x.dyDp) * t,
                        x.blurDp + (y.blurDp - x.blurDp) * t,
                        lerpColor(x.color, y.color, t),
                        x.spreadDp + (y.spreadDp - x.spreadDp) * t,
                    )
                x != null -> x.scaled(1f - t)
                else -> y!!.scaled(t)
            }
        }
    }

    private fun Shadow.scaled(k: Float): Shadow = Shadow(dxDp * k, dyDp * k, blurDp * k, color, spreadDp * k)

    private fun paired(a: PairedShadow?, b: PairedShadow?, t: Float): PairedShadow? {
        if (a == null && b == null) return null
        val x = a ?: PairedShadow(b!!.light.withAlpha(0f), b.dark.withAlpha(0f), b.distanceDp, b.blurDp, b.inset)
        val y = b ?: PairedShadow(x.light.withAlpha(0f), x.dark.withAlpha(0f), x.distanceDp, x.blurDp, x.inset)
        return PairedShadow(
            lerpColor(x.light, y.light, t),
            lerpColor(x.dark, y.dark, t),
            if (x.distanceDp == 0f) y.distanceDp else if (y.distanceDp == 0f) x.distanceDp else x.distanceDp + (y.distanceDp - x.distanceDp) * t,
            if (x.blurDp == 0f) y.blurDp else if (y.blurDp == 0f) x.blurDp else x.blurDp + (y.blurDp - x.blurDp) * t,
            x.inset + (y.inset - x.inset) * t,
        )
    }
}
