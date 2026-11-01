package io.github.eslamasabry.oc.ui.design

/**
 * Pure design tokens (no Compose; ADR §5.2 :ui:design). Slice 0 holds one neutral placeholder palette so the kit
 * has something to draw; the 31 packs, Style resolver and contrast guard are slice 1 (kit owner).
 */
public data class ColorRoles(
    val background: Long,
    val onBackground: Long,
    val surface: Long,
    val onSurface: Long,
    val primary: Long,
    val onPrimary: Long,
    val muted: Long,
)

public object PlaceholderPalette {
    public val light: ColorRoles =
        ColorRoles(0xFFFAFAF7, 0xFF1B1C1A, 0xFFFFFFFF, 0xFF1B1C1A, 0xFF2F5D50, 0xFFFFFFFF, 0xFF5F625D)
    public val dark: ColorRoles =
        ColorRoles(0xFF121412, 0xFFE3E3DE, 0xFF1C1F1C, 0xFFE3E3DE, 0xFF8FC9B5, 0xFF0B2A21, 0xFFA3A69F)
}

/** Spacing steps in dp, used only by kit parts. */
public object Spacing {
    public const val XS: Int = 4
    public const val S: Int = 8
    public const val M: Int = 16
    public const val L: Int = 24
}

/** Motion duration tokens within 150–250 ms (ADR §9.4). */
public object MotionTokens {
    public const val SHORT_MS: Int = 150
    public const val MEDIUM_MS: Int = 200
    public const val LONG_MS: Int = 250
}
