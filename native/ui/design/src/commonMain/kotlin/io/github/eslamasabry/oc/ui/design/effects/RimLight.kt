package io.github.eslamasabry.oc.ui.design.effects

import kotlin.math.abs
import kotlin.math.pow

/*
 * The rim light round every liquid glass pane (owner 2026-11-13, "You are fixing these broken corners everywhere also
 * right?"). The light is directional: it falls along the top-start/bottom-end diagonal, so the edge facing it (the
 * top-start arc) and its mirror (the bottom-end arc) are the brightest, which is what makes the pane read as glass.
 *
 * Its strength at a point of the edge comes from the cosine between the edge's outward direction there and the light
 * (the vendored backdrop library's highlight: `pow(abs(dot(direction, light)), falloff)`). That cosine is 0 where the
 * edge runs along the light, at the top-end and bottom-start corners, and no falloff changes that: `pow(0, f)` is 0 for
 * every falloff above 0, and a larger falloff only widens the dark arcs. So those two corners had no rim at all and the
 * pane read as a broken shape. The [FLOOR] keeps a hairline there: [lit] fades a floor in as the light falls off, so
 * the two lit arcs keep their peak and the two weak arcs keep [FLOOR] of it, with no step anywhere.
 */
public object RimLight {
    /** How tightly the light gathers at its lit arcs (the library's default is 1): a brighter top-start segment. */
    public const val FALLOFF: Float = 1.6f

    /**
     * The least the rim ever is, as a share of its peak, all the way round a pane. 35 %: on a dark page the dark theme's
     * rim (white at .72, added) keeps about a quarter of white's light at the weak corners, a clear hairline over the
     * ground and over Aurora's green and lilac, while the lit arcs stay almost three times brighter, so the pane still
     * reads as lit from one side and never as a flat outlined box.
     */
    public const val FLOOR: Float = .35f

    /**
     * The rim's strength (0 to 1 of its peak) where the edge's outward direction makes [cosine] with the light: the
     * library's `|cosine|^falloff`, with [floor] faded in as that light falls off (`l + floor (1 - l)^2`): 1 where the
     * edge faces the light, [floor] where it runs along it, and rising all the way between (its slope never drops
     * below `1 - 2 floor`).
     */
    public fun lit(cosine: Float, falloff: Float = FALLOFF, floor: Float = FLOOR): Float {
        val l = abs(cosine).coerceAtMost(1f).pow(falloff)
        return l + floor * (1f - l) * (1f - l)
    }

    /**
     * [lit] as shader text (AGSL on Android, SkSL on desktop: the same text), for every shader that draws a rim: the
     * plain pane's highlight and the joined body's rim.
     */
    public const val SHADER_LIT: String =
        """
float rimLit(float rimCosine, float rimFalloff, float rimLeast) {
    float l = pow(min(abs(rimCosine), 1.0), rimFalloff);
    return l + rimLeast * (1.0 - l) * (1.0 - l);
}
"""
}
