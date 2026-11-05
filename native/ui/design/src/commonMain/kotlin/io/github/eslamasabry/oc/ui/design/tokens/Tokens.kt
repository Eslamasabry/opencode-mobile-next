package io.github.eslamasabry.oc.ui.design.tokens

/** Fine-tune spacing knob (the UI exposes Corners and Spacing; owner 2026-10-22). */
public enum class Density { Comfortable, Compact }

/**
 * Sizes in dp from Dart `KitTokens.fallback` and its static constants (`lib/ui/kit/kit_tokens.dart`). Compact steps
 * stay on the 4 dp scale and touch targets never shrink (`KitTokens._styled`). Slice 0 carries the sizes the P7 parts
 * and the 20 frozen signatures need; the rest of plan 06 §4.5 joins with the parts that read them.
 */
public data class Tokens(
    val gutter: Int = 16,
    val sectionGap: Int = 24,
    val labelGap: Int = 8,
    val panelPadding: Int = 16,
    val rowHeightOne: Int = 56,
    val rowHeightTwo: Int = 72,
    val choiceRowMin: Int = 56,
    val buttonHeight: Int = 52,
    val minTarget: Int = 48,
    val chipHeight: Int = 32,
    val iconTile: Int = 28,
    val smallIcon: Int = 20,
    val navHeight: Int = 60,
    val navRadius: Int = 22,
    val leadColumn: Int = 40,
    val statusLineMin: Int = 52,
    val handleWidth: Int = 36,
    val handleThickness: Int = 5,
    val handleBand: Int = 22,
    val loadingBar: Int = 2,
    val progressBar: Int = 4,
    val skeletonRow: Int = 64,
    val composerRadius: Int = 26,
    val composerRadiusWide: Int = 18,
    val bubbleRadius: Int = 20,
    val bubbleTail: Int = 6,
    val panelInset: Int = 24,
    /** Leading and state icons grow with text up to this. */
    val maxIconScale: Float = 1.5f,
) {
    public companion object {
        public val Comfortable: Tokens = Tokens()

        /** 52 → 48, 56 → 48, 64 → 56, 24 → 16, 8 → 4 (Dart `_styled`; plan 06 §4.3 says 48/54, the code says 48/56). */
        public val Compact: Tokens =
            Comfortable.let {
                it.copy(
                    buttonHeight = maxOf(it.minTarget, it.buttonHeight - Space.S1),
                    rowHeightOne = maxOf(it.minTarget, it.rowHeightOne - Space.S2),
                    rowHeightTwo = maxOf(it.minTarget, it.rowHeightTwo - Space.S2),
                    sectionGap = it.sectionGap - Space.S2,
                    labelGap = it.labelGap - Space.S1,
                )
            }

        public fun of(density: Density): Tokens = if (density == Density.Compact) Compact else Comfortable
    }
}

/** Alphas that are not colour roles (Dart constants). */
public object Alphas {
    public const val DISABLED: Float = 0.38f
    public const val STALE: Float = 0.60f
    /** The press ripple / pressed fill: the part's ink at this alpha (Dart `KitInk.alpha`). */
    public const val PRESSED: Float = 0.12f
    public const val HOVERED: Float = 0.08f
    public const val FOCUSED: Float = 0.10f
}
