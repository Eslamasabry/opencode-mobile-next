package io.github.eslamasabry.oc.ui.kit.style

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.RoleDerivation
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.lerp
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import io.github.eslamasabry.oc.ui.design.effects.KitKnobs
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What a style fixes for every part: its Fine-tune defaults and the tokens the resolver reads (Dart `KitStyleTokens`,
 * `lib/ui/kit/style/kit_style.dart`).
 */
public data class KitStyleTokens(
    /** The Fine-tune knobs this style starts at. */
    val defaults: KitKnobs,
    /** The radius Corners › Sharp gives a box in this style (Paper: 0). */
    val sharpRadius: Float = 2f,
    /** Whether the style stays itself under the platform's high contrast; otherwise it is Solid then. */
    val keepsUnderHighContrast: Boolean = false,
    /** Whether the style's shapes come from its shadows (Soft): Depth › Flat then turns Edges on. */
    val dependsOnDepth: Boolean = false,
    val type: KitStyleType = KitStyleType(),
    val live: KitLivePolicy = KitLivePolicy(),
    val glow: KitStyleGlow = KitStyleGlow(),
    /** A surface inside a surface that casts casts nothing itself (hook H9). */
    val flattensNested: Boolean = false,
    /** Whether a content surface inside a content surface paints its own face (Solid). */
    val keepsNestedSurfaces: Boolean = false,
    /** A divider's width in dp; null keeps the part's own hairline. */
    val dividerWidth: Float? = null,
    val needsYou: KitNeedsYouLook = KitNeedsYouLook(),
    /** The underline under a link's words, as a multiple of the words' stroke; 0 none; null as the divider draws. */
    val linkUnderline: Float? = null,
    /** Whether a glowing field keeps its halo; null as [glow]'s halo. */
    val fieldHalo: Boolean? = null,
    /** Whether lists sit straight on the page (Solid, Signal). */
    val flatLists: Boolean = false,
    /** Whether lists and the transcript run on one track with signal lights (Signal). */
    val track: Boolean = false,
) {
    val fieldHaloShown: Boolean get() = fieldHalo ?: glow.halo
}

/** How the words "Needs you" are set on a request card. */
public enum class KitNeedsYouTag { Caption, Pill }

/** What a request card shows while it needs the person. */
public data class KitNeedsYouDecision(
    val tint: Boolean,
    val line: Boolean,
    val ring: Boolean,
    val startEdge: Boolean,
    val pill: Boolean,
)

/**
 * How a request card that needs the person is told apart (Dart `KitNeedsYouLook`). Every null follows the glow's halo:
 * with a halo, the full card is tinted and ringed; without one it is calm with the start edge alone. The question look
 * is never tinted or ringed.
 */
public data class KitNeedsYouLook(
    val tag: KitNeedsYouTag = KitNeedsYouTag.Caption,
    val tint: Boolean? = null,
    val ring: Boolean? = null,
    val startEdge: Boolean? = null,
) {
    public fun decide(halo: Boolean, question: Boolean = false): KitNeedsYouDecision {
        val line = ring ?: halo
        val ringed = !question && line
        return KitNeedsYouDecision(
            tint = !question && (tint ?: halo),
            line = line,
            ring = ringed,
            startEdge = startEdge ?: !ringed,
            pill = tag == KitNeedsYouTag.Pill,
        )
    }
}

/** How the live background shows under a style. */
public data class KitLivePolicy(val maxStrength: Float? = null, val offWhenChosen: Boolean = false) {
    public fun cap(strength: Float): Float = maxStrength?.let { if (strength <= it) strength else it } ?: strength
}

/** How the running glow around the message box is drawn. */
public data class KitStyleGlow(val halo: Boolean = true, val lineWidth: Float? = null)

/** A style's type tweaks over the kit's type scale; null keeps the role's own value. */
public data class KitStyleType(
    val displayWeight: Int? = null,
    val displayTracking: Float? = null,
    val largeTitleSize: Float? = null,
    val largeTitleWeight: Int? = null,
    val titleSize: Float? = null,
    val bodyWeight: Int? = null,
    val monoLabels: Boolean = false,
    val engravedLabels: Boolean = false,
    val firmWeights: Boolean = false,
) {
    val isNone: Boolean get() = this == KitStyleType()
}

/** One surface to resolve: the part's own [solid] look and where it sits (Dart `KitSurfaceRequest`). */
public data class KitSurfaceRequest(
    val roles: Roles,
    val role: KitSurfaceRole,
    /** The part's own look: what Solid paints, unchanged. */
    val solid: KitSurfaceLook,
    val knobs: KitKnobs,
    val level: SurfaceLevel = SurfaceLevel.Surface1,
    val radius: KitRadii = KitRadii.Zero,
    val circle: Boolean = false,
    /** A part's own fill (an accent button, a selected chip), kept as it is. */
    val color: Argb? = null,
    val selected: Boolean = false,
    val pressed: Boolean = false,
    val hovered: Boolean = false,
    val disabled: Boolean = false,
    /** A divider between rows of one group, not between groups. */
    val withinGroup: Boolean = false,
    val edgeWidth: Float = 1f,
    /** An action the person presses (a button, Send), not a mark or a choice. */
    val action: Boolean = false,
) {
    val levelFill: Argb
        get() = when (level) {
            SurfaceLevel.Ground -> roles.ground
            SurfaceLevel.Surface1 -> roles.surface1
            SurfaceLevel.Surface2 -> roles.surface2
            SurfaceLevel.Surface3 -> roles.surface3
        }
}

/** A design school: its tokens and its resolver (Dart `KitStyleSpec`). */
public interface KitStyleSpec {
    public val tokens: KitStyleTokens

    /** The look for [r]. Solid returns `r.solid` itself at its defaults. */
    public fun resolve(r: KitSurfaceRequest): KitSurfaceLook

    /** The words' colour on a surface (hook H3); null keeps the part's own. */
    public fun onColor(r: KitSurfaceRequest): Argb? = null

    /** The highlight on a control with a fill of its own; null keeps the part's own layer. */
    public fun resolveFilledLayer(r: KitSurfaceRequest): KitSurfaceLook? = null

    /** The theme's roles under this style (hook H6): only the ground, surfaces, hairline and shadow may change. */
    public fun adjustRoles(roles: Roles): Roles = roles
}

/** The theme's own near-white: text1 in dark, surface2 in light, opaque. */
public fun kitStyleLight(roles: Roles): Argb = (if (roles.dark) roles.text1 else roles.surface2).withAlpha(1f)

/** The theme's shadow colour, opaque. */
public fun kitStyleShade(roles: Roles): Argb = roles.elevationShadow.withAlpha(1f)

/** The page in any style: the fill alone, no edge and no shadow. */
public fun kitStyleGround(r: KitSurfaceRequest): KitSurfaceLook =
    KitSurfaceLook(radius = r.radius, circle = r.circle, fill = r.color ?: r.levelFill)

/**
 * A boundary that reads at 3:1 or more at rest (WCAG 1.4.11) against the ground, every surface step and the theme's
 * near-white: the quietest grey on the way from `text3` to `text1` that reaches it (Dart `kitStyleBoundary`).
 */
public fun kitStyleBoundary(roles: Roles): Argb {
    val base = roles.ground.withAlpha(1f)
    val grounds = listOf(roles.ground, roles.surface1, roles.surface2, roles.surface3, kitStyleLight(roles)).map { it.over(base) }
    val quiet = roles.text3.withAlpha(.5f).over(grounds.first())
    return RoleDerivation.readableOn(quiet, grounds, 3.1, roles.text1.withAlpha(1f))
}

/** WCAG contrast (Dart `contrastRatio`). */
internal fun contrast(a: Argb, b: Argb): Double = wcagContrast(a, b)

/** Flutter `Color.lerp` with a Double share. */
internal fun mix(a: Argb, b: Argb, t: Double): Argb = lerp(a, b, t.toFloat())

/** Flutter `HSLColor`, enough for the styles' faces and key edges. */
internal data class Hsl(val alpha: Double, val hue: Double, val saturation: Double, val lightness: Double) {
    fun toArgb(): Argb {
        val chroma = (1.0 - abs(2.0 * lightness - 1.0)) * saturation
        val h = hue / 60.0
        val secondary = chroma * (1.0 - abs(h.mod(2.0) - 1.0))
        val match = lightness - chroma / 2.0
        val (r, g, b) =
            when {
                hue < 60.0 -> Triple(chroma, secondary, 0.0)
                hue < 120.0 -> Triple(secondary, chroma, 0.0)
                hue < 180.0 -> Triple(0.0, chroma, secondary)
                hue < 240.0 -> Triple(0.0, secondary, chroma)
                hue < 300.0 -> Triple(secondary, 0.0, chroma)
                else -> Triple(chroma, 0.0, secondary)
            }
        fun q(x: Double) = (x * 255.0).roundToInt().coerceIn(0, 255)
        return Argb((q(alpha) shl 24) or (q(r + match) shl 16) or (q(g + match) shl 8) or q(b + match))
    }

    companion object {
        fun of(c: Argb): Hsl {
            val r = c.red / 255.0
            val g = c.green / 255.0
            val b = c.blue / 255.0
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val delta = max - min
            var hue =
                when {
                    max == 0.0 -> 0.0
                    max == r -> 60.0 * ((g - b) / delta).mod(6.0)
                    max == g -> 60.0 * ((b - r) / delta + 2)
                    else -> 60.0 * ((r - g) / delta + 4)
                }
            if (hue.isNaN()) hue = 0.0
            val lightness = (max + min) / 2.0
            val saturation = if (lightness == 1.0) 0.0 else (delta / (1.0 - abs(2.0 * lightness - 1.0))).coerceIn(0.0, 1.0)
            return Hsl(c.alpha / 255.0, hue, if (saturation.isNaN()) 0.0 else saturation, lightness)
        }
    }
}
