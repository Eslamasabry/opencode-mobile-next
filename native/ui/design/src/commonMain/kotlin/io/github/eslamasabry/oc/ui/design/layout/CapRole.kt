package io.github.eslamasabry.oc.ui.design.layout

/** Where a capped part sits when its slot is wider than its cap (Dart `kit_max_width.dart` alignments). */
public enum class CapAlign { Start, Center, End }

/**
 * The hard maximum widths by role (plan 06 §8.1; owner rule R6). A part applies its cap as its outermost modifier
 * (`Modifier.kitMaxWidth`), so a caller cannot widen it. Fill, Wrapper and Scope carry no cap and add no layout node.
 */
public enum class CapRole(public val capDp: Int?, public val defaultAlign: CapAlign) {
    Conversation(Layout.CONVERSATION_WIDTH, CapAlign.Center),
    Reading(Layout.READING_WIDTH, CapAlign.Center),
    List(Layout.LIST_WIDTH, CapAlign.Center),
    Sheet(Layout.SHEET_MAX_WIDTH, CapAlign.Center),
    Confirm(Layout.CONFIRM_WIDTH, CapAlign.Center),
    Dialog(Layout.DIALOG_WIDTH, CapAlign.Center),
    Popover(Layout.POPOVER_MAX_WIDTH, CapAlign.Start),
    Toast(Layout.UNDO_MAX_WIDTH, CapAlign.Start),
    State(Layout.STATE_MAX_WIDTH, CapAlign.Center),
    Lane(Layout.LANE_MAX_WIDTH, CapAlign.Start),
    Control(Layout.CONTROL_MAX_WIDTH, CapAlign.Center),
    Chip(Layout.CHIP_MAX_WIDTH, CapAlign.Start),
    Glyph(Layout.GLYPH_MAX_WIDTH, CapAlign.Center),
    Fill(null, CapAlign.Start),
    Wrapper(null, CapAlign.Start),
    Scope(null, CapAlign.Start),
}
