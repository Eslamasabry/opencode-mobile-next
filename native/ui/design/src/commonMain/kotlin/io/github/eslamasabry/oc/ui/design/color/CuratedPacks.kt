package io.github.eslamasabry.oc.ui.design.color

/**
 * The three hand-spelled packs besides Graphite (Dart `_catppuccin`, `_gruvbox`, `_solarized` in
 * `lib/ui/theme_packs.dart`): the scheme slots the role derivation reads, copied value for value. Each tertiary is the
 * one Flutter's `ColorScheme.fromSeed` gives for the pack's seed (the Dart copies do not override it), computed once
 * with material_color_utilities 0.13.0.
 */
internal object CuratedPacks {
    fun of(id: PackId): Pack =
        when (id) {
            PackId.Catppuccin -> catppuccin
            PackId.Gruvbox -> gruvbox
            PackId.Solarized -> solarized
            else -> error("not a curated pack: $id")
        }

    private val catppuccin by lazy {
        Pack(
            PackId.Catppuccin,
            "Catppuccin",
            dark = PackPalette.of(
                dark = true, primary = argb(0xFFCBA6F7), secondary = argb(0xFFB4BEFE), tertiary = argb(0xFFF2B7C0),
                onSurface = argb(0xFFCDD6F4), background = argb(0xFF181825), navigation = argb(0xFF1B1B2B),
                success = argb(0xFFA6E3A1),
            ),
            light = PackPalette.of(
                dark = false, primary = argb(0xFF8839EF), secondary = argb(0xFF565FA8), tertiary = argb(0xFF7F525B),
                onSurface = argb(0xFF4C4F69), background = argb(0xFFEFF1F5), navigation = argb(0xFFE6E9EF),
                success = argb(0xFF40A02B),
            ),
        )
    }

    private val gruvbox by lazy {
        Pack(
            PackId.Gruvbox,
            "Gruvbox",
            dark = PackPalette.of(
                dark = true, primary = argb(0xFFFE8019), secondary = argb(0xFFFABD2F), tertiary = argb(0xFFCBC992),
                onSurface = argb(0xFFEBDBB2), background = argb(0xFF1D2021), navigation = argb(0xFF232120),
                success = argb(0xFFB8BB26),
            ),
            light = PackPalette.of(
                dark = false, primary = argb(0xFFAF3A03), secondary = argb(0xFFB57614), tertiary = argb(0xFF695E2F),
                onSurface = argb(0xFF3C3836), background = argb(0xFFFBF1C7), navigation = argb(0xFFF2E5BC),
                success = argb(0xFF79740E),
            ),
        )
    }

    private val solarized by lazy {
        Pack(
            PackId.Solarized,
            "Solarized",
            dark = PackPalette.of(
                dark = true, primary = argb(0xFF268BD2), secondary = argb(0xFF2AA198), tertiary = argb(0xFFD3BFE6),
                onSurface = argb(0xFF93A1A1), background = argb(0xFF00212A), navigation = argb(0xFF01313E),
                success = argb(0xFF859900),
            ),
            light = PackPalette.of(
                dark = false, primary = argb(0xFF1E6FA8), secondary = argb(0xFF1F7A72), tertiary = argb(0xFF68587A),
                onSurface = argb(0xFF586E75), background = argb(0xFFFDF6E3), navigation = argb(0xFFEEE8D5),
                success = argb(0xFF6C7E00),
            ),
        )
    }
}
