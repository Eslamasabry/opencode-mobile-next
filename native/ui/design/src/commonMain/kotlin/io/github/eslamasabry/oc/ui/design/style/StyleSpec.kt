package io.github.eslamasabry.oc.ui.design.style

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.RoleDerivation
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.lerp
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel

/**
 * A design school: its tokens and its resolver (Dart `KitStyleSpec`). Pure data in, pure data out: the kit draws the
 * [SurfaceDecoration] it returns. A null from [resolve] means the part keeps its own look (Solid at its defaults).
 */
public interface StyleSpec {
    public val tokens: StyleTokens

    public fun resolve(r: SurfaceRequest): SurfaceDecoration?

    /** The words' colour on a surface (hook H3): a chosen chip, segment or tab, a button. Null keeps the part's own. */
    public fun onColor(r: SurfaceRequest): Argb? = null

    /** The press or hover highlight on a control with a fill of its own. Null keeps the part's own layer. */
    public fun resolveFilledLayer(r: SurfaceRequest): SurfaceDecoration? = null

    /** The theme's roles under this style (hook H6): only ground, surface1-3, hairline, scrim, elevationShadow may change. */
    public fun adjustRoles(roles: Roles): Roles = roles
}

internal fun Roles.clear(): Argb = ground.withAlpha(0f)

internal fun Argb.opaque(): Argb = withAlpha(1f)

internal fun contrast(a: Argb, b: Argb): Double = wcagContrast(a, b)

/** The theme's own near-white: the light on a surface (text1 in dark, surface2 in light), fully opaque. */
internal fun styleLight(roles: Roles): Argb = (if (roles.dark) roles.text1 else roles.surface2).opaque()

/** The theme's shadow colour, fully opaque: the shade on a surface. */
internal fun styleShade(roles: Roles): Argb = roles.elevationShadow.opaque()

/** The page in any style: the fill alone, no edge and no shadow. */
internal fun styleGround(r: SurfaceRequest): SurfaceDecoration =
    SurfaceDecoration(radii = r.radii, circle = r.circle, fill = r.color ?: r.levelFill)

/** The one tight floating drop (0, 6, blur 16, spread -6). */
internal fun floatingLift(roles: Roles): Shadow = Shadow(0f, 6f, 16f, roles.elevationShadow, spreadDp = -6f)

private val boundaries = LinkedHashMap<Roles, Argb>()

/**
 * A boundary that reads at 3:1 or more at rest (WCAG 1.4.11) against the ground, every surface step and the theme's
 * near-white: a field's or an off switch's edge in a style whose own edges are softer. Opaque.
 */
public fun styleBoundary(roles: Roles): Argb =
    boundaries.getOrPut(roles) {
        if (boundaries.size > 16) boundaries.remove(boundaries.keys.first())
        val base = roles.ground.opaque()
        val grounds = listOf(roles.ground, roles.surface1, roles.surface2, roles.surface3, styleLight(roles)).map { it.over(base) }
        val quiet = roles.text3.withAlpha(.5f).over(grounds.first())
        RoleDerivation.readableOn(quiet, grounds, 3.1, roles.text1.opaque())
    }

internal fun hslLightness(c: Argb): Float {
    val max = maxOf(c.red, c.green, c.blue) / 255f
    val min = minOf(c.red, c.green, c.blue) / 255f
    return (max + min) / 2f
}

internal fun hslSaturation(c: Argb): Float {
    val max = maxOf(c.red, c.green, c.blue) / 255f
    val min = minOf(c.red, c.green, c.blue) / 255f
    val l = (max + min) / 2f
    val d = max - min
    return if (d == 0f) 0f else d / (1f - kotlin.math.abs(2f * l - 1f))
}

internal fun hslHue(c: Argb): Float {
    val r = c.red / 255f
    val g = c.green / 255f
    val b = c.blue / 255f
    val max = maxOf(r, g, b)
    val d = max - minOf(r, g, b)
    if (d == 0f) return 0f
    val h = when (max) {
        r -> ((g - b) / d) % 6f
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    }
    return ((h * 60f) + 360f) % 360f
}

internal fun fromHsl(hue: Float, saturation: Float, lightness: Float): Argb {
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun ch(v: Float) = ((v + m) * 255f).let { kotlin.math.round(it).toInt().coerceIn(0, 255) }
    return Argb((0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b))
}

/** [color] with its HSL lightness replaced. */
internal fun withLightness(color: Argb, lightness: Float): Argb {
    val o = color.opaque()
    return fromHsl(hslHue(o), hslSaturation(o), lightness).let { Argb((color.alpha shl 24) or (it.v and 0xFFFFFF)) }
}

internal fun mix(a: Argb, b: Argb, t: Float): Argb = lerp(a, b, t)

internal val SurfaceLevel.isStep: Boolean get() = this == SurfaceLevel.Surface2 || this == SurfaceLevel.Surface3
