package io.github.eslamasabry.oc.ui.design.color

/**
 * Deriving a whole role set from a few colours (plan 06 §4.1), ported line by line from `lib/ui/theme_roles.dart`
 * (`deriveRoles`, `readableOn`, `_guardAccent`, `withAccent`, `withPartner`, `onColor`, `accentKeepsMeaning`). The work
 * runs on float colours as Flutter does and rounds once (see [DColor]).
 */
public object RoleDerivation {
    /** LOOK-39: the least hue distance, in degrees, an accent keeps from attention. */
    public const val ACCENT_MIN_HUE_DISTANCE: Double = 30.0

    /** LOOK-39: the least CIEDE2000 difference an accent keeps from attention. */
    public const val ACCENT_MIN_DELTA_E: Double = 20.0

    private val ink = DColor(1.0, 0x11 / 255.0, 0x12 / 255.0, 0x14 / 255.0)

    /** Text that reads on [fill]: near-black ink or white, whichever measures better (Dart `onColor`). */
    public fun onColor(fill: Argb): Argb = onColorD(fill.toD()).toArgb()

    internal fun onColorD(fill: DColor): DColor =
        if (contrast(ink, fill) >= contrast(DColor.White, fill)) ink else DColor.White

    /** [color] moved toward [toward] only as far as it takes to reach [min] against every one of [grounds]. */
    public fun readableOn(color: Argb, grounds: List<Argb>, min: Double, toward: Argb): Argb =
        readableOnD(color.toD(), grounds.map { it.toD() }, min, toward.toD()).toArgb()

    internal fun readableOnD(color: DColor, grounds: List<DColor>, min: Double, toward: DColor): DColor {
        var result = color
        for (i in 0..50) {
            result = mix(color, toward, i / 50.0)
            if (grounds.all { contrast(result, it) >= min }) break
        }
        return result
    }

    /** The accent reads as text on [grounds] (4.5:1) and carries its own on-colour at 4.5:1 (Dart `_guardAccent`). */
    internal fun guardAccentD(accent: DColor, grounds: List<DColor>, dark: Boolean): DColor {
        val far = if (dark) DColor.White else DColor.Black
        var result = readableOnD(accent, grounds, 4.5, far)
        for (i in 0 until 40) {
            if (contrast(onColorD(result), result) >= 4.5) break
            result = mix(result, far, .04)
        }
        return result
    }

    /** Whether [accent] keeps its meaning against [roles]' attention (LOOK-39; Dart `accentKeepsMeaning`). */
    public fun accentKeepsMeaning(accent: Argb, roles: Roles): Boolean {
        val a = accent.toD()
        val att = roles.attention.toD()
        return hueDistance(a, att) >= ACCENT_MIN_HUE_DISTANCE && deltaE2000(a, att) >= ACCENT_MIN_DELTA_E
    }

    /**
     * Every role from an accent, a ground and a brightness, with the contrast floors built in (Dart `deriveRoles`):
     * text1 7:1, text2 and text3 4.5:1 on the ground and every surface, the accent reads as a link and carries its
     * on-colour, attention reads as text. [partners] are the theme's own second colours, tried in order.
     */
    public fun deriveRoles(
        accent: Argb,
        ground: Argb,
        dark: Boolean,
        text: Argb? = null,
        attention: Argb? = null,
        success: Argb? = null,
        partners: List<Argb> = emptyList(),
    ): Roles {
        val far = if (dark) DColor.White else DColor.Black
        val defaults = Graphite.of(dark)
        val g = ground.toD()
        val base = (text ?: defaults.text1).toD()
        val surface1: DColor
        val surface2: DColor
        val surface3: DColor
        if (dark) {
            surface1 = mix(g, base, .035)
            surface2 = mix(g, base, .065)
            surface3 = mix(g, base, .11)
        } else {
            surface1 = mix(g, DColor.White, .8)
            surface2 = surface1
            surface3 = mix(g, base, .05)
        }
        val surfaces = listOf(g, surface1, surface2, surface3)
        val attentionCard = alphaBlend(defaults.attentionSurface.toD(), surface1)
        val text1 = readableOnD(base, surfaces + attentionCard, 7.0, far)
        val text2 = readableOnD(mix(text1, g, if (dark) .36 else .30), surfaces, 4.5, far)
        val text3 = readableOnD(mix(text1, g, if (dark) .46 else .40), surfaces, 4.5, far)
        val a = guardAccentD(accent.toD(), listOf(g, surface1), dark)
        val att = readableOnD((attention ?: defaults.attention).toD(), listOf(g, surface1, attentionCard), 4.5, far)
        val ok = readableOnD((success ?: defaults.success).toD(), listOf(g, surface1), 4.5, far)
        fun code(hue: Argb) = readableOnD(hue.toD(), listOf(surface1, surface2), 4.5, far)
        fun usable(c: DColor) =
            hueDistance(c, att) >= ACCENT_MIN_HUE_DISTANCE && deltaE2000(c, att) >= ACCENT_MIN_DELTA_E &&
                deltaE2000(c, a) >= ACCENT_MIN_DELTA_E
        val candidates = (partners + defaults.codeKeyword + defaults.codeType).map { it.toD() }
        val partnerHue = candidates.firstOrNull(::usable) ?: defaults.codeKeyword.toD()
        val p = guardAccentD(partnerHue, listOf(g, surface1), dark)
        return Roles(
            dark = dark,
            ground = g.toArgb(),
            surface1 = surface1.toArgb(),
            surface2 = surface2.toArgb(),
            surface3 = surface3.toArgb(),
            hairline = defaults.hairline,
            text1 = text1.toArgb(),
            text2 = text2.toArgb(),
            text3 = text3.toArgb(),
            accent = a.toArgb(),
            onAccent = onColorD(a).toArgb(),
            partner = p.toArgb(),
            onPartner = onColorD(p).toArgb(),
            attention = att.toArgb(),
            attentionFill = defaults.attentionFill,
            onAttentionFill = defaults.onAttentionFill,
            attentionSurface = defaults.attentionSurface,
            attentionLine = defaults.attentionLine,
            success = ok.toArgb(),
            scrim = defaults.scrim,
            codeKeyword = code(defaults.codeKeyword).toArgb(),
            codeString = code(defaults.codeString).toArgb(),
            codeType = code(defaults.codeType).toArgb(),
            elevationShadow = defaults.elevationShadow,
        )
    }
}

/** These roles with another accent: moved only as far as it takes to read on the ground (Dart `withAccent`). */
public fun Roles.withAccent(accent: Argb): Roles {
    val a = RoleDerivation.guardAccentD(accent.toD(), listOf(ground.toD(), surface1.toD()), dark)
    return copy(accent = a.toArgb(), onAccent = RoleDerivation.onColorD(a).toArgb())
}

/** These roles with another partner (the Accent swatch the person picked; Dart `withPartner`). */
public fun Roles.withPartner(partner: Argb): Roles {
    val p = RoleDerivation.guardAccentD(partner.toD(), listOf(ground.toD(), surface1.toD()), dark)
    return copy(partner = p.toArgb(), onPartner = RoleDerivation.onColorD(p).toArgb())
}
