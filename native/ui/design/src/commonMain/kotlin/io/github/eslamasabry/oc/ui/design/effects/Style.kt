package io.github.eslamasabry.oc.ui.design.effects

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.RoleDerivation
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.SignalPalette
import io.github.eslamasabry.oc.ui.design.color.argb
import io.github.eslamasabry.oc.ui.design.color.lerp
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import io.github.eslamasabry.oc.ui.design.tokens.Density
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The role of a face, independent of a Compose component (Flutter KitSurfaceRole). */
public enum class SurfaceRole { Content, Control, Track, Floating, Sheet, Ground, Field, Code, Divider, StateLayer, Toggle }

public data class StyleShadow(
    val color: Argb,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val blur: Float = 0f,
    val spread: Float = 0f,
    val inset: Boolean = false,
)

/** Resolved paint. Distances are logical pixels; the renderer mirrors [hardOffset] toward the reading end. */
public data class SurfaceDecoration(
    val fill: Argb,
    val edge: Argb = Argb(0),
    val edgeEnd: Argb = edge,
    val edgeWidth: Float = 0f,
    val shadows: List<StyleShadow> = emptyList(),
    val hardShadow: Argb = Argb(0),
    val hardOffset: Float = 0f,
    val faceShift: Float = 0f,
    val words: Argb? = null,
)

public data class SurfaceRequest(
    val role: SurfaceRole,
    val level: SurfaceLevel = SurfaceLevel.Surface1,
    val fill: Argb? = null,
    val selected: Boolean = false,
    val pressed: Boolean = false,
    val disabled: Boolean = false,
    val withinGroup: Boolean = false,
    val action: Boolean = false,
    val uniform: Boolean = true,
    val circle: Boolean = false,
    val edgeWidth: Float = 1f,
)

/** Style policy and exact surface rules ported from lib/ui/kit/style/kit_style_*.dart. */
public data class StyleSpec(
    val style: SurfaceStyle,
    val corners: Corners,
    val density: Density,
    val highContrast: Boolean,
) {
    public val sharpRadius: Int get() = when (style) { SurfaceStyle.Paper -> 0; SurfaceStyle.Signal -> 4; else -> 2 }
    public val flatLists: Boolean get() = style == SurfaceStyle.Solid || style == SurfaceStyle.Paper || style == SurfaceStyle.Signal
    public val firmType: Boolean get() = style != SurfaceStyle.Solid
    public val monoLabels: Boolean get() = style == SurfaceStyle.Brutal || style == SurfaceStyle.Paper
    public val engravedLabels: Boolean get() = style == SurfaceStyle.Signal
    public val track: Boolean get() = style == SurfaceStyle.Signal
    public val glowHalo: Boolean get() = style != SurfaceStyle.Brutal && style != SurfaceStyle.Paper
    public val glowWidth: Float? get() = when (style) { SurfaceStyle.Brutal -> 2f; SurfaceStyle.Paper -> 1f; else -> null }
    public val raised: Boolean get() = !highContrast && (style == SurfaceStyle.Soft || style == SurfaceStyle.Brutal)

    /** Stadiums and circles are deliberately resolved by their own shape, never passed here. */
    public fun corner(round: Int): Int = when (corners) {
        Corners.Round -> round
        Corners.Soft -> (round * .6f).roundToInt()
        Corners.Sharp -> min(round, sharpRadius)
    }

    public fun adjustRoles(roles: Roles): Roles = when (style) {
        SurfaceStyle.Solid -> roles
        SurfaceStyle.Soft -> softRoles(roles)
        SurfaceStyle.Brutal -> brutalRoles(roles)
        SurfaceStyle.Paper -> {
            val ink = roles.text1.withAlpha(1f)
            val ground = if (roles.dark) lerp(roles.ground, ink, .02f)
            else roles.attentionFill.withAlpha(.04f).over(lerp(roles.surface2, ink, .04f))
            roles.copy(ground = ground, surface1 = ground, surface3 = lerp(ground, ink, if (roles.dark) .08f else .05f), hairline = ink.withAlpha(.2f))
        }
        SurfaceStyle.Signal -> signalRoles(roles)
    }

    public fun resolve(r: SurfaceRequest, roles: Roles, solid: SurfaceDecoration): SurfaceDecoration {
        if (r.role == SurfaceRole.Ground) return solid.copy(edgeWidth = 0f, shadows = emptyList(), hardOffset = 0f, faceShift = 0f)
        return when (style) {
            SurfaceStyle.Solid -> solid
            SurfaceStyle.Soft -> soft(r, roles)
            SurfaceStyle.Brutal -> brutal(r, roles, solid)
            SurfaceStyle.Paper -> paper(r, roles)
            SurfaceStyle.Signal -> signal(r, roles)
        }
    }

    private fun soft(r: SurfaceRequest, p: Roles): SurfaceDecoration {
        val white = light(p)
        val black = p.elevationShadow.withAlpha(1f)
        val ground = r.fill ?: p.ground
        val filledChoice = r.role == SurfaceRole.Control && r.fill != null && r.selected
        val well = r.role in setOf(SurfaceRole.Track, SurfaceRole.Field, SurfaceRole.Toggle, SurfaceRole.Code, SurfaceRole.StateLayer) || r.pressed || (r.selected && !filledChoice)
        if (r.role == SurfaceRole.Divider) return SurfaceDecoration(if (highContrast) p.hairline else Argb(0))
        if (r.role == SurfaceRole.StateLayer && !r.pressed)
            return SurfaceDecoration((if (p.dark) white else black).withAlpha(if (p.dark) .04f else .03f))
        if (r.disabled && (r.role == SurfaceRole.Control || r.role == SurfaceRole.Content)) return SurfaceDecoration(ground)
        val (distance, blur) = if (!raised) 0f to 0f else when (r.role) {
            SurfaceRole.Content, SurfaceRole.Floating -> 6f to 16f
            SurfaceRole.Control -> 3f to 8f
            SurfaceRole.Sheet, SurfaceRole.Ground, SurfaceRole.Divider -> 0f to 0f
            else -> 2.5f to 6f
        }
        if (r.role == SurfaceRole.Sheet) return SurfaceDecoration(
            if (p.dark) white.withAlpha(.03f).over(ground) else ground,
            white.withAlpha(if (p.dark) .1f else .9f), white.withAlpha(0f), r.edgeWidth,
            if (highContrast) emptyList() else listOf(StyleShadow(black.withAlpha(if (p.dark) .55f else .14f), dy = -4f, blur = 28f)),
        )
        val prompt = r.role == SurfaceRole.Content && r.level == SurfaceLevel.Surface2 && r.fill == null && !well
        val face = if (prompt) tinted(ground, white, if (p.dark) .1f else .6f, if (p.dark) .06f else 0f, listOf(p.text1, p.text2))
            ?: tinted(ground, p.partner, .1f, 0f, listOf(p.text1, p.text2)) ?: ground else ground
        val fill = if (p.dark) {
            if (well) black.withAlpha(.18f).over(ground)
            else white.withAlpha(if (prompt || r.fill != null) 0f else .05f).over(face)
        } else if (well) {
            if (r.role == SurfaceRole.Code) lightCodeWell(p, ground) else black.withAlpha(.025f).over(ground)
        } else face
        val lightShadow = white.withAlpha(if (p.dark) { if (r.fill != null) .07f else .1f } else .7f)
        val darkShadow = if (p.dark) black.withAlpha(.85f)
            else if (filledChoice) lerp(r.fill!!, p.text1, .55f).withAlpha(.34f) else p.text1.withAlpha(.26f)
        var edgeStart = white.withAlpha(if (well) 0f else if (p.dark) .16f else if (filledChoice) .35f else .7f)
        var edgeEnd = white.withAlpha(if (p.dark && well) .06f else 0f)
        if (highContrast) { edgeStart = boundary(p); edgeEnd = edgeStart }
        if (r.role == SurfaceRole.Field || r.role == SurfaceRole.Toggle) {
            edgeStart = if (r.selected) { if (r.role == SurfaceRole.Toggle) r.fill ?: p.accent else p.accent } else boundary(p)
            edgeEnd = edgeStart
        }
        val words = if (r.selected && r.role == SurfaceRole.Control && r.fill == null)
            RoleDerivation.readableOn(p.accent, listOf(fill, p.ground), 4.6, p.text1.withAlpha(1f)) else null
        return SurfaceDecoration(fill, edgeStart, edgeEnd, r.edgeWidth,
            if (distance <= 0f) emptyList() else listOf(
                StyleShadow(lightShadow, -distance, -distance, blur, inset = well),
                StyleShadow(darkShadow, distance, distance, blur, inset = well),
            ), words = words)
    }

    private fun brutal(r: SurfaceRequest, p: Roles, solid: SurfaceDecoration): SurfaceDecoration {
        val ink = p.text1
        val part = r.role in setOf(SurfaceRole.Field, SurfaceRole.Toggle, SurfaceRole.Code)
        if (part) return SurfaceDecoration(
            (if (r.role == SurfaceRole.Toggle && !r.selected) null else r.fill) ?: level(p, r.level), ink, edgeWidth = 2f,
            shadows = if (r.role == SurfaceRole.Field && r.selected) listOf(StyleShadow(p.accent, spread = 3f)) else emptyList(),
        )
        if (r.role == SurfaceRole.Divider) return SurfaceDecoration(if (!r.withinGroup) ink.withAlpha(.9f) else Argb(0))
        if (r.role == SurfaceRole.StateLayer) return SurfaceDecoration(if (r.pressed) p.surface3 else ink.withAlpha(.04f))
        if (r.level == SurfaceLevel.Ground && r.fill == null) return solid
        if (r.disabled && r.role == SurfaceRole.Control) return SurfaceDecoration(r.fill ?: p.surface3, p.text3, edgeWidth = 2f)
        val compact = density == Density.Compact
        val fall = when (r.role) {
            SurfaceRole.Content, SurfaceRole.Sheet -> if (compact) 3f else 4f
            SurfaceRole.Control -> if (compact) 2f else 3f
            SurfaceRole.Floating -> if (compact) 4f else 6f
            else -> 0f
        }
        val even = r.role != SurfaceRole.Sheet || r.uniform
        val down = r.selected || r.pressed
        val acts = r.fill == p.accent && !r.selected
        val mint = if (acts) mint(p) else null
        val rest = if (r.role == SurfaceRole.Control) p.surface2 else level(p, r.level)
        val fill = mint ?: r.fill ?: if (down) p.surface3 else rest
        val ringed = p.dark && r.selected && r.fill == null && (r.role == SurfaceRole.Content || r.role == SurfaceRole.Control)
        return SurfaceDecoration(
            fill, if (ringed) p.accent else ink, edgeWidth = 2f,
            hardShadow = if (p.dark) lerp(p.text3, p.ground, .4f).withAlpha(1f) else ink,
            hardOffset = if (raised && !down && even) fall else 0f,
            faceShift = if (r.pressed && raised && even) fall else 0f,
            words = if (acts && mint != p.accent) p.text1 else null,
        )
    }

    private fun paper(r: SurfaceRequest, p: Roles): SurfaceDecoration {
        val ink = p.text1.withAlpha(if (p.dark) .6f else .9f)
        val inverted = r.role == SurfaceRole.Control && r.selected && !r.circle
        val down = r.selected || r.pressed
        return when (r.role) {
            SurfaceRole.Field -> SurfaceDecoration(Argb(0), ink, edgeWidth = if (r.selected) 2f else 1f)
            SurfaceRole.Toggle -> SurfaceDecoration((if (r.selected) r.fill else null) ?: level(p, r.level), ink, edgeWidth = if (r.selected) 2f else 1f)
            SurfaceRole.Code -> SurfaceDecoration(r.fill ?: level(p, r.level), ink, edgeWidth = 1f)
            SurfaceRole.Divider -> SurfaceDecoration(if (r.selected) p.text1.withAlpha(.9f) else p.text1.withAlpha(.2f))
            SurfaceRole.StateLayer -> SurfaceDecoration(if (r.pressed) lerp(p.ground, p.text1, if (p.dark) .1f else .07f) else p.text1.withAlpha(.03f))
            SurfaceRole.Content -> {
                val step = r.level == SurfaceLevel.Surface2 || r.level == SurfaceLevel.Surface3
                SurfaceDecoration(r.fill ?: if (down) p.surface3 else if (step) p.ground else Argb(0), ink, edgeWidth = if (step || r.selected) 1f else 0f)
            }
            SurfaceRole.Control, SurfaceRole.Track -> SurfaceDecoration(
                if (inverted) p.text1.withAlpha(1f) else r.fill ?: if (down) p.surface3 else p.ground,
                if (inverted) p.text1.withAlpha(1f) else ink,
                edgeWidth = if (r.fill == null || r.selected) { if (r.selected && !inverted) 2f else 1f } else 0f,
                words = if (inverted) p.ground.withAlpha(1f) else null,
            )
            SurfaceRole.Floating -> SurfaceDecoration(r.fill ?: p.ground, ink, edgeWidth = 1f)
            SurfaceRole.Sheet -> SurfaceDecoration(r.fill ?: level(p, r.level), ink, edgeWidth = 1f)
            SurfaceRole.Ground -> SurfaceDecoration(r.fill ?: level(p, r.level))
        }
    }

    private fun signal(r: SurfaceRequest, p: Roles): SurfaceDecoration {
        val ink = p.text2.withAlpha(1f)
        val own = r.fill
        val inverted = r.role == SurfaceRole.Control && r.selected && own == null && !r.circle
        val down = r.selected || r.pressed
        if ((r.role == SurfaceRole.Control || r.role == SurfaceRole.Floating) && r.action && own != null && own.alpha == 255 && !r.selected && !r.disabled && r.uniform) {
            val palette = SignalPalette.of(p.dark)
            val face = if (!p.dark || own == palette.key) own else lerp(own, p.ground.withAlpha(1f), .3f)
            val edge = if (own == palette.key) palette.keyEdge else halfLightness(face)
            val words = if (!p.dark || own == palette.key) null else {
                val a = p.text1.withAlpha(1f); val b = p.ground.withAlpha(1f)
                if (wcagContrast(a, face) >= wcagContrast(b, face)) a else b
            }
            return SurfaceDecoration(face, edge, edgeWidth = 1f, shadows = listOf(StyleShadow(edge, dy = if (r.pressed) 1f else 3f)), faceShift = if (r.pressed) 2f else 0f, words = words)
        }
        return when (r.role) {
            SurfaceRole.Field -> SurfaceDecoration(Argb(0), ink, edgeWidth = if (r.selected) 2f else 1.5f)
            SurfaceRole.Toggle -> SurfaceDecoration((if (r.selected) own else null) ?: level(p, r.level), ink, edgeWidth = 1.5f)
            SurfaceRole.Code -> SurfaceDecoration(own ?: level(p, r.level), p.hairline, edgeWidth = 1f)
            SurfaceRole.Divider -> SurfaceDecoration(if (r.selected) Argb(0) else p.hairline)
            SurfaceRole.StateLayer -> SurfaceDecoration(if (r.pressed) lerp(p.ground, p.text1, if (p.dark) .12f else .08f) else p.text1.withAlpha(.04f))
            SurfaceRole.Content -> {
                val step = r.level == SurfaceLevel.Surface2 || r.level == SurfaceLevel.Surface3
                SurfaceDecoration(own ?: if (down) p.surface3 else if (step) p.surface2 else Argb(0), p.hairline, edgeWidth = if (step || r.selected) 1f else 0f)
            }
            SurfaceRole.Control -> when {
                inverted -> SurfaceDecoration(p.text1.withAlpha(1f), words = p.ground.withAlpha(1f))
                own != null -> SurfaceDecoration(if (own.alpha == 255) own else Argb(0))
                else -> SurfaceDecoration(if (down) p.surface3 else Argb(0), ink, edgeWidth = 1.5f)
            }
            SurfaceRole.Track -> SurfaceDecoration(Argb(0), ink, edgeWidth = 1.5f)
            SurfaceRole.Floating -> SurfaceDecoration(own ?: p.ground, ink, edgeWidth = 1.5f)
            SurfaceRole.Sheet -> SurfaceDecoration(own ?: level(p, r.level), p.hairline, edgeWidth = 1f)
            SurfaceRole.Ground -> SurfaceDecoration(own ?: level(p, r.level))
        }
    }

    public companion object {
        public fun of(appearance: KitAppearance, highContrast: Boolean = false): StyleSpec {
            val style = when {
                appearance.saver -> SurfaceStyle.Solid
                highContrast && appearance.style == SurfaceStyle.Soft -> SurfaceStyle.Solid
                else -> appearance.style
            }
            val corners = if (appearance.saver) Corners.Round else appearance.corners ?: when (style) {
                SurfaceStyle.Brutal -> Corners.Soft
                SurfaceStyle.Paper, SurfaceStyle.Signal -> Corners.Sharp
                else -> Corners.Round
            }
            return StyleSpec(style, corners, if (appearance.saver) Density.Comfortable else appearance.density, highContrast)
        }
    }
}

private fun level(p: Roles, step: SurfaceLevel): Argb = when (step) {
    SurfaceLevel.Ground -> p.ground
    SurfaceLevel.Surface1 -> p.surface1
    SurfaceLevel.Surface2 -> p.surface2
    SurfaceLevel.Surface3 -> p.surface3
}
private fun light(p: Roles): Argb = (if (p.dark) p.text1 else p.surface2).withAlpha(1f)
private fun boundary(p: Roles): Argb {
    val grounds = listOf(p.ground, p.surface1, p.surface2, p.surface3, light(p)).map { it.over(p.ground.withAlpha(1f)) }
    return RoleDerivation.readableOn(p.text3.withAlpha(.5f).over(grounds.first()), grounds, 3.1, p.text1.withAlpha(1f))
}
private fun tinted(base: Argb, tint: Argb, most: Float, least: Float, inks: List<Argb>): Argb? {
    var amount = most
    while (amount > least) {
        val face = tint.withAlpha(amount).over(base)
        if (inks.all { wcagContrast(it, face) >= 4.5 }) return face
        amount -= .01f
    }
    return null
}
private fun lightCodeWell(p: Roles, fill: Argb): Argb {
    val white = light(p)
    for (step in 0..9) {
        val face = lerp(fill, white, step / 10f)
        if (listOf(p.text1, p.text2, p.codeKeyword, p.codeString, p.codeType, p.codeAdded).all { wcagContrast(it, face) >= 4.6 }) return face
    }
    return white
}
private fun readable(p: Roles): Boolean = listOf(p.ground, p.surface1, p.surface2, p.surface3).all { fill ->
    listOf(p.text1, p.text2, p.text3, p.attention).all { wcagContrast(it, fill) >= 4.5 }
}
private fun softRoles(p: Roles): Roles {
    val ink = p.text1.withAlpha(1f)
    var amount = if (p.dark) .07f else .045f
    while (amount > 0f) {
        val ground = lerp(p.ground, ink, amount)
        val faces = listOf(ground, light(p).withAlpha(if (p.dark) .05f else 0f).over(ground), lerp(ground, ink, if (p.dark) .035f else .03f))
        if (listOf(p.text2, p.text3).all { word -> faces.all { wcagContrast(word, it) >= 4.5 } })
            return p.copy(ground = ground, surface1 = ground, surface2 = if (p.dark) lerp(p.surface2, ink, amount) else p.surface2, surface3 = faces.last())
        amount -= .005f
    }
    return p
}
private fun brutalRoles(p: Roles): Roles {
    if (p.dark) return p.copy(surface1 = p.surface2)
    for (warmth in listOf(.12f, .09f, .06f, .03f)) {
        fun stir(c: Argb, share: Float): Argb = p.attentionFill.withAlpha(share).over(c)
        val paper = stir(p.surface2, warmth * .35f)
        val next = p.copy(ground = stir(p.ground, warmth), surface1 = paper, surface2 = paper, surface3 = stir(p.surface3, warmth))
        if (readable(next)) return next
    }
    return p
}
private fun signalRoles(p: Roles): Roles {
    val palette = SignalPalette.of(p.dark)
    val ink = p.text1.withAlpha(1f)
    for (share in listOf(1f, .7f, .4f)) {
        val ground = lerp(p.ground, palette.ground, share)
        val slip = lerp(p.surface2, palette.slip, share)
        val pressed = lerp(ground, ink, if (p.dark) .09f else .07f)
        val steps = listOf(ground, slip, pressed)
        val next = p.copy(ground = ground, surface1 = ground, surface2 = slip, surface3 = pressed,
            hairline = lerp(p.hairline, palette.rule, share), text3 = RoleDerivation.readableOn(p.text3, steps, 4.5, ink),
            attention = RoleDerivation.readableOn(p.attention, steps, 4.5, ink))
        if (readable(next)) return next
    }
    return p
}
private fun mint(p: Roles): Argb {
    if (p.dark) return p.accent
    val hsl = hsl(p.accent)
    var lightness = max(hsl[2], .55f)
    while (lightness <= .85f) {
        val face = fromHsl(hsl[0], min(hsl[1], .7f), lightness)
        if (wcagContrast(p.text1, face) >= 7) return face
        lightness += .03f
    }
    return p.accent
}
private fun halfLightness(c: Argb): Argb = hsl(c).let { fromHsl(it[0], it[1], it[2] * .5f) }
private fun hsl(c: Argb): FloatArray {
    val r = c.red / 255f; val g = c.green / 255f; val b = c.blue / 255f
    val hi = maxOf(r, g, b); val lo = minOf(r, g, b); val d = hi - lo; val l = (hi + lo) / 2f
    val h = if (d == 0f) 0f else when (hi) { r -> ((g - b) / d).mod(6f); g -> (b - r) / d + 2f; else -> (r - g) / d + 4f } * 60f
    val s = if (d == 0f) 0f else d / (1f - kotlin.math.abs(2f * l - 1f))
    return floatArrayOf(h, s, l)
}
private fun fromHsl(h: Float, s: Float, l: Float): Argb {
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s; val x = c * (1f - kotlin.math.abs((h / 60f).mod(2f) - 1f)); val m = l - c / 2f
    val rgb = when { h < 60 -> floatArrayOf(c, x, 0f); h < 120 -> floatArrayOf(x, c, 0f); h < 180 -> floatArrayOf(0f, c, x); h < 240 -> floatArrayOf(0f, x, c); h < 300 -> floatArrayOf(x, 0f, c); else -> floatArrayOf(c, 0f, x) }
    fun q(n: Float): Int = ((n + m) * 255).roundToInt().coerceIn(0, 255)
    return Argb((255 shl 24) or (q(rgb[0]) shl 16) or (q(rgb[1]) shl 8) or q(rgb[2]))
}
