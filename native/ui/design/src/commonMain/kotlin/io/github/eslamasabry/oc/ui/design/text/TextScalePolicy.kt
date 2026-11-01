package io.github.eslamasabry.oc.ui.design.text

/** The in-app Text size (`oc.textSize`); it multiplies on top of the system scale. */
public enum class AppTextSize(public val factor: Float, public val storageName: String) {
    Smallest(0.85f, "smallest"),
    Small(0.92f, "small"),
    Normal(1.0f, "normal"),
    Large(1.15f, "large"),
}

/** Plan 06 §9: frame text capped at 1.3×, reading text full up to the root cap. */
public object TextScalePolicy {
    /** No text renders larger than this times its spec size (Dart `AppTheme.maxTextScale`). */
    public const val ROOT_CAP: Float = 2.5f

    /** Frame text never renders larger than this times its spec size (Dart `kKitChromeMaxTextScale`; owner 2026-10-31). */
    public const val FRAME_CAP: Float = 1.3f

    /** Fixed caps that stay (Dart named constants). */
    public const val ICON_CAP: Float = 1.5f
    public const val TERMINAL_CAP: Float = 2.0f
    public const val TERMINAL_KEY_CAP: Float = 1.3f
    public const val MONOGRAM_CAP: Float = 1.3f
    public const val BADGE_CAP: Float = 1.3f

    /**
     * Layout decisions compare against this, never against 1.3: Android reports 1.3 as 1.2999999523, and the
     * non-linear curve lands a little under the nominal scale.
     */
    public const val LARGE_TEXT_AT: Float = 1.29f

    public fun isLargeText(effectiveScale: Float): Boolean = effectiveScale >= LARGE_TEXT_AT

    /** The size in dp a [specSp] text renders at, given what the platform would give ([platformDp]) and a [cap]. */
    public fun capped(specSp: Float, platformDp: Float, cap: Float): Float = minOf(platformDp, specSp * cap)
}
