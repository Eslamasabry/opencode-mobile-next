package io.github.eslamasabry.oc.ui.design.color

/**
 * A generated pack's source colours for one brightness (Dart `_Tones`): background, text, primary, secondary and an
 * optional success. [seedTertiary] is the tertiary Flutter's `ColorScheme.fromSeed(seedColor: primary)` produces
 * (material_color_utilities 0.13.0 `SchemeTonalSpot`, contrast 0), computed once in Dart and committed as data: the
 * role derivation falls back to it for the partner when the secondary sits near amber.
 */
public data class PackSource(
    val background: Argb,
    val text: Argb,
    val primary: Argb,
    val secondary: Argb,
    val seedTertiary: Argb,
    val success: Argb?,
)

public data class GeneratedSpec(val label: String, val dark: PackSource, val light: PackSource)

/** The one recipe for the 26 generated packs (Dart `_build`, `lib/ui/theme_packs_generated.dart`). */
public object PackRecipe {
    /** Dart `_readable`: the float loop `t += .04` is reproduced exactly, including where it stops. */
    private fun readable(color: DColor, grounds: List<DColor>, min: Double, toward: DColor): DColor {
        var result = color
        var t = 0.0
        while (t <= 1.0) {
            result = mix(color, toward, t)
            if (grounds.all { contrast(result, it) >= min }) break
            t += .04
        }
        return result
    }

    public fun generate(id: PackId, spec: GeneratedSpec): Pack =
        Pack(id, spec.label, palette(spec.dark, dark = true), palette(spec.light, dark = false))

    /**
     * The slots the role derivation reads. The containers, outline and the navigation-bar deepening loop of `_build`
     * feed only Material slots no native part reads, so they are not ported.
     */
    internal fun palette(t: PackSource, dark: Boolean): PackPalette {
        val bg = t.background.toD()
        val text = t.text.toD()
        val far = if (dark) DColor.White else DColor.Black
        fun step(d: Double, l: Double) = if (dark) d else l
        val surface = if (dark) mix(bg, text, .04) else mix(bg, DColor.White, .6)
        val low = mix(bg, text, step(.06, .04))
        val mid = mix(bg, text, step(.09, .07))
        val high = mix(bg, text, step(.13, .10))
        val grounds = listOf(bg, surface, low, mid, high)
        val fg = readable(text, grounds, 7.0, far)
        val primary = readable(t.primary.toD(), listOf(bg, surface), 3.0, far)
        val secondary = readable(t.secondary.toD(), listOf(bg, surface), 3.0, far)
        val success =
            readable(
                (t.success ?: if (dark) argb(0xFF86D8A5) else argb(0xFF1E7A44)).toD(),
                listOf(bg, surface), 3.0, far,
            )
        return PackPalette(
            primaryD = primary,
            secondaryD = secondary,
            tertiaryD = t.seedTertiary.toD(),
            onSurfaceD = fg,
            backgroundD = bg,
            navigationD = mix(bg, fg, step(.03, .025)),
            successD = success,
            dark = dark,
        )
    }

}
