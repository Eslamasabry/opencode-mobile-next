package io.github.eslamasabry.oc.ui.design.effects

import io.github.eslamasabry.oc.ui.design.color.Argb
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * What a Glass pane can find behind it over a shader look (Aurora drift, Breathing mesh and the other live looks; owner
 * 2026-11-09: in-flow panes show real glass over every live background). Every look ends in the shader prelude's
 * `liveOut`, which keeps each pixel within [CAP_LIGHT] (light) or [CAP_DARK] (dark) of the ground (RGB distance, 0-1
 * channels) and a tint look within [TINT_MAX_MIX] of it. So whatever a look draws, its brightest and darkest pixel lie
 * at most that far from the ground along the luminance direction: [of] returns those two ends (and the ground), the
 * spots Glass's legibility measure ([io.github.eslamasabry.oc.ui.design.style.GlassFinish.thickened]) reads words over.
 * Unblurred, so conservative: the panes blur them towards the ground.
 */
public object LiveSpots {
    /** `liveOut`'s colour cap round the ground (LiveShaderLib.PRELUDE: `mix(0.20, 0.30, uDark)`). */
    public const val CAP_LIGHT: Float = .20f
    public const val CAP_DARK: Float = .30f

    // Rec. 709 luma weights: the direction in RGB that moves luminance most per unit of distance.
    private const val WR: Float = .2126f
    private const val WG: Float = .7152f
    private const val WB: Float = .0722f

    /**
     * The ground and the brightest and darkest colours a shader look can draw on it: the ground moved by the cap along
     * the luma direction, the grey axis and the green channel (WCAG luminance is not linear in the channels, so the
     * extreme lies near, not exactly on, the luma direction), each way.
     */
    public fun of(ground: Argb, dark: Boolean): List<Argb> {
        val cap = if (dark) CAP_DARK else CAP_LIGHT
        val directions =
            listOf(
                floatArrayOf(WR, WG, WB),
                floatArrayOf(1f, 1f, 1f),
                floatArrayOf(0f, 1f, 0f),
            )
        fun moved(d: FloatArray, sign: Float): Argb {
            val n = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])
            fun ch(v: Int, w: Float) = ((v / 255f + sign * cap * w / n).coerceIn(0f, 1f) * 255f).roundToInt()
            return Argb((0xFF shl 24) or (ch(ground.red, d[0]) shl 16) or (ch(ground.green, d[1]) shl 8) or ch(ground.blue, d[2]))
        }
        return listOf(Argb(ground.v or (0xFF shl 24))) + directions.flatMap { listOf(moved(it, 1f), moved(it, -1f)) }
    }
}
