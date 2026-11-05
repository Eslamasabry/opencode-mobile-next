package io.github.eslamasabry.oc.ui.design.tokens

import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens

/** Fine-tune spacing knob (the UI exposes Corners and Spacing; owner 2026-10-22). */
public enum class Density { Comfortable, Compact }

/**
 * Sizes in dp from Dart `KitTokens.fallback` and its static constants (`lib/ui/kit/kit_tokens.dart`), read from the
 * generated [FlutterKitTokens] (native/tool/design/gen_tokens.py); no size here is typed by hand. Compact steps
 * stay on the 4 dp scale and touch targets never shrink (`KitTokens._styled`). Slice 0 carries the sizes the P7 parts
 * and the 20 frozen signatures need; the rest of plan 06 §4.5 joins with the parts that read them.
 */
public data class Tokens(
    val gutter: Int = FlutterKitTokens.GUTTER,
    val sectionGap: Int = FlutterKitTokens.SECTION_GAP,
    val labelGap: Int = FlutterKitTokens.LABEL_GAP,
    val panelPadding: Int = FlutterKitTokens.PANEL_PADDING,
    val rowHeightOne: Int = FlutterKitTokens.ROW_HEIGHT,
    val rowHeightTwo: Int = FlutterKitTokens.ROW_HEIGHT_TWO_LINE,
    val choiceRowMin: Int = FlutterKitTokens.CHOICE_ROW_MIN_HEIGHT,
    val buttonHeight: Int = FlutterKitTokens.BUTTON_HEIGHT,
    val minTarget: Int = FlutterKitTokens.MIN_TARGET,
    val chipHeight: Int = FlutterKitTokens.CHIP_HEIGHT,
    val iconTile: Int = FlutterKitTokens.ICON_TILE_SIZE,
    val smallIcon: Int = FlutterKitTokens.SMALL_ICON_SIZE,
    val navHeight: Int = FlutterKitTokens.NAV_HEIGHT,
    val navRadius: Int = FlutterKitTokens.NAV_RADIUS,
    val leadColumn: Int = 40,
    val statusLineMin: Int = FlutterKitTokens.STATUS_LINE_MIN_HEIGHT,
    val handleWidth: Int = FlutterKitTokens.HANDLE_WIDTH,
    val handleThickness: Int = FlutterKitTokens.HANDLE_THICKNESS,
    val handleBand: Int = FlutterKitTokens.HANDLE_HEIGHT,
    val loadingBar: Int = FlutterKitTokens.LOADING_BAR_HEIGHT,
    val progressBar: Int = FlutterKitTokens.PROGRESS_BAR_HEIGHT,
    val skeletonRow: Int = FlutterKitTokens.SKELETON_ROW_HEIGHT,
    val composerRadius: Int = FlutterKitTokens.COMPOSER_RADIUS,
    val composerRadiusWide: Int = FlutterKitTokens.COMPOSER_RADIUS_WIDE,
    val bubbleRadius: Int = FlutterKitTokens.BUBBLE_RADIUS,
    val bubbleTail: Int = FlutterKitTokens.BUBBLE_TAIL_RADIUS,
    val panelInset: Int = FlutterKitTokens.PANEL_INSET,
    /** Leading and state icons grow with text up to this. */
    val maxIconScale: Float = FlutterKitTokens.MAX_ICON_SCALE,
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
    public const val DISABLED: Float = FlutterKitTokens.DISABLED_ALPHA
    public const val STALE: Float = FlutterKitTokens.STALE_ALPHA
    /** The press ripple / pressed fill: the part's ink at this alpha (Dart `KitInk.alpha`). */
    public const val PRESSED: Float = 0.12f
    public const val HOVERED: Float = 0.08f
    public const val FOCUSED: Float = 0.10f
}
