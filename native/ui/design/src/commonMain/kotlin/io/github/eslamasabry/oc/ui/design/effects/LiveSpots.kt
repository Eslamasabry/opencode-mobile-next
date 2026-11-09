package io.github.eslamasabry.oc.ui.design.effects

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.lerp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * What a Glass pane can find behind it over a shader look (Aurora drift, Breathing mesh and the other live looks; owner
 * 2026-11-09: in-flow panes show real glass over every live background). Every look colours the ground with the theme's
 * look colours (the kit's `LivePalette`: the accent, its partner, the glow between the accent and the text, and the
 * text's ink): as light added to a dark ground (`liveGlow`'s screen blend of the colour's vivid form, which moves the
 * ground along `tint × (1 − ground)`), as coloured light multiplied into a light one (`liveGlow`), or as a mark mixed
 * towards the colour itself (the dot, line and instrument looks). And it ends in the shader prelude's `liveOut`, which keeps
 * each pixel within [CAP_LIGHT] (light) or [CAP_DARK] (dark) of the ground (RGB distance, 0-1 channels).
 *
 * So whatever a look draws lies in the ground's cap ball along those directions and their mixes: [of] returns the ground
 * and the ground moved to the cap (or to the colour itself, if nearer) along each colour's direction and each pair's
 * mid-direction. Unblurred, so conservative: the panes blur them towards the ground.
 */
public object LiveSpots {
    /** `liveOut`'s colour cap round the ground (LiveShaderLib.PRELUDE: `mix(0.20, 0.30, uDark)`). */
    public const val CAP_LIGHT: Float = .20f
    public const val CAP_DARK: Float = .30f

    /** The most [of] moves the ground, as a share of the cap. */
    public const val MAX_REACH: Float = 2f

    /** The kit's glow colour: the accent this far towards the text (LivePalette `GLOW_TOWARD_TEXT`). */
    private const val GLOW_TOWARD_TEXT: Float = .3f

    /**
     * The spots behind a Glass pane over the look [id] (glass-live 2026-11-09): [of] with the look's measured [LiveReach]
     * instead of the whole cap, under the clear panes' frost ([lens] false) or the liquid lens's light blur ([lens] true).
     * A look with no measured reach (an id the kit does not draw) keeps the whole cap.
     */
    public fun forLook(roles: Roles, id: String?, lens: Boolean): List<Argb> = of(roles, LiveReach.of(id, roles.dark, lens))

    /**
     * The ground and the farthest a shader look can move it, along each look colour and each pair of them: [reach] of
     * liveOut's cap (1, the default, is the whole cap; a look that does not end in the prelude's cap may reach past it,
     * [LiveReach]), never past the look colour itself.
     */
    public fun of(roles: Roles, reach: Float = 1f): List<Argb> {
        val dark = roles.dark
        val cap = (if (dark) CAP_DARK else CAP_LIGHT) * reach.coerceIn(0f, MAX_REACH)
        val g = rgb(roles.ground)
        val tints = listOf(roles.accent, roles.partner, lerp(roles.accent, roles.text1, GLOW_TOWARD_TEXT), roles.text1).map(::rgb)
        // How far each colour moves the ground per unit of its strength, and at most how far it can: the dot, line and
        // instrument looks mix towards the colour itself; the wash looks (liveGlow) lay its vivid form, screened on a
        // dark ground and multiplied in at full brightness on a light one (bg-looks 2026-11-10).
        val moves =
            tints.flatMap { t ->
                val v = vivid(t)
                if (dark) {
                    listOf(FloatArray(3) { t[it] * (1f - g[it]) }, FloatArray(3) { v[it] * (1f - g[it]) })
                } else {
                    val top = maxOf(v[0], v[1], v[2], .02f)
                    listOf(FloatArray(3) { t[it] - g[it] }, FloatArray(3) { g[it] * (v[it] / top - 1f) })
                }
            }
        val directions = moves + moves.indices.flatMap { i -> (i + 1 until moves.size).map { j -> FloatArray(3) { (moves[i][it] + moves[j][it]) / 2f } } }
        fun moved(d: FloatArray): Argb? {
            val n = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])
            if (n < 1e-4f) return null
            val k = minOf(cap, n) / n
            return argb(FloatArray(3) { (g[it] + d[it] * k).coerceIn(0f, 1f) })
        }
        return listOf(argb(g)) + directions.mapNotNull(::moved).distinct()
    }

    /** The prelude's `liveVivid`: the colour 40 % further from its own grey. */
    private fun vivid(c: FloatArray): FloatArray {
        val l = .2126f * c[0] + .7152f * c[1] + .0722f * c[2]
        return FloatArray(3) { (l + (c[it] - l) * VIVID).coerceIn(0f, 1f) }
    }

    private const val VIVID: Float = 1.4f

    private fun rgb(c: Argb): FloatArray = floatArrayOf(c.red / 255f, c.green / 255f, c.blue / 255f)

    private fun argb(c: FloatArray): Argb {
        fun ch(v: Float) = (v * 255f).roundToInt().coerceIn(0, 255)
        return Argb((0xFF shl 24) or (ch(c[0]) shl 16) or (ch(c[1]) shl 8) or ch(c[2]))
    }
}
