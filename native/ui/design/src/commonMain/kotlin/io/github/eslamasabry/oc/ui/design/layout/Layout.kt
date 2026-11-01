package io.github.eslamasabry.oc.ui.design.layout

/**
 * Every width constant (plan 06 §3.2, §8.1; Dart `KitLayout`, `lib/ui/kit/kit_layout.dart`). The only width literals in
 * the codebase: `LiteralBanTest` fails a width compared to a literal anywhere else. Values in dp.
 */
public object Layout {
    public const val MEDIUM_FROM: Int = 600
    public const val EXPANDED_FROM: Int = 840
    public const val LARGE_FROM: Int = 1200
    public const val SHORT_HEIGHT: Int = 480

    public const val CONVERSATION_WIDTH: Int = 700
    public const val READING_WIDTH: Int = 720
    public const val LIST_WIDTH: Int = 960
    public const val SHEET_MAX_WIDTH: Int = 640
    public const val CONFIRM_WIDTH: Int = 560
    public const val DIALOG_WIDTH: Int = 480
    public const val POPOVER_MAX_WIDTH: Int = 320
    public const val POPOVER_MIN_WIDTH: Int = 200
    public const val UNDO_MAX_WIDTH: Int = 480
    public const val STATE_MAX_WIDTH: Int = 440
    public const val LANE_MAX_WIDTH: Int = 400
    public const val CONTROL_MAX_WIDTH: Int = 400
    public const val CHIP_MAX_WIDTH: Int = 320
    public const val GLYPH_MAX_WIDTH: Int = 96

    public const val PANE_LIST_WIDTH: Int = 296
    public const val PANE_DETAIL_MAX_WIDTH: Int = 700
    public const val PANE_DETAIL_MIN_WIDTH: Int = 360
    public const val PANE_SIDE_WIDTH: Int = 340

    public const val RAIL_WIDTH: Int = 80
    public const val SIDEBAR_MAX_WIDTH: Int = 400
    public const val SIDEBAR_MAX_SHARE: Float = 1f / 3f

    /** The width under which a pair row (label + value) stacks (Dart `pairRowMinWidth`). */
    public const val PAIR_ROW_MIN_WIDTH: Int = 340

    /**
     * The sidebar grows with text from 296 dp, up to 400 dp or a third of the window, never under 296 (Dart
     * `KitLayout.sidebarWidth`). [textScale] is the effective scale at body size.
     */
    public fun sidebarWidth(textScale: Float, windowWidthDp: Float): Float {
        val cap = minOf(SIDEBAR_MAX_WIDTH.toFloat(), windowWidthDp * SIDEBAR_MAX_SHARE)
        return (PANE_LIST_WIDTH * textScale).coerceIn(PANE_LIST_WIDTH.toFloat(), maxOf(PANE_LIST_WIDTH.toFloat(), cap))
    }
}
