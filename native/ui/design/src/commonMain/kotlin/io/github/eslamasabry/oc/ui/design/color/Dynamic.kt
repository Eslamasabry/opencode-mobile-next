package io.github.eslamasabry.oc.ui.design.color

/** The platform scheme tones Material You reads for one brightness (plan 06 §4.2; Dart `dynamicThemePack`). */
public data class DynamicTones(
    val primary: Argb,
    val secondary: Argb,
    val tertiary: Argb,
    val onSurface: Argb,
    val surfaceContainerLowest: Argb,
    val surfaceContainerLow: Argb,
)

/**
 * Material You from the platform (API 31+, `KitPlatform.dynamicSource()`); null below API 31 and on desktop, where
 * Graphite stands in. Success is fixed (`#86D8A5` / `#1E7A44`): dynamic palettes carry no green with a stable meaning.
 * The contrast guard runs on the result either way (the role derivation).
 */
public data class DynamicSource(val dark: DynamicTones, val light: DynamicTones) {
    public fun toPack(): Pack = Pack(PackId.Dynamic, "Material You", palette(dark, true), palette(light, false))

    private fun palette(t: DynamicTones, dark: Boolean): PackPalette =
        PackPalette.of(
            dark = dark,
            primary = t.primary,
            secondary = t.secondary,
            tertiary = t.tertiary,
            onSurface = t.onSurface,
            background = t.surfaceContainerLowest,
            navigation = t.surfaceContainerLow,
            success = if (dark) argb(0xFF86D8A5) else argb(0xFF1E7A44),
        )
}
