# Port plan: the Flutter kit, part by part (2026-11-05)

Owner redirect, 2026-11-05: rebuild the Flutter design system and kit exactly; improvements come later, one at a time, with the owner's approval. This plan lists every Flutter kit file at `mobile-next/master` (8302de537, the 2.1.1 product), its native file(s) in `native/ui/*`, and its goldens under `test/goldens/kit/`, grouped into batches of about ten parts for one agent each, in dependency order (a batch only needs batches above it).

## Native looks-pass changes to check against the goldens

The looks pass (2026-11-04/05) changed some native parts before the redirect. Tokens are back on Flutter's values
(generated). These part-level changes stay until their batch gates them: a batch keeps one only if its goldens agree,
otherwise the part goes back to Flutter's look.

| Change | Part | Batch |
|---|---|---|
| Phosphor glyph set (KitIcons, KitIcon) | foundation | 1 (Flutter's AppIconography set; keep) |
| Press: corners round and the face settles 2 % | KitButton, KitFab | controls |
| Search field as a filled pill without an outline | KitSearchField | controls |
| Field radius 9 → 14 (Flutter fieldRadius 14) and the framed flag | KitField | controls |
| Bare row glyphs, state word with lead mark, meta column | KitRow | rows |
| Top bar icon actions and the large mode | KitTopBar | frame and navigation |
| Sheet Close as the X glyph (Flutter's default close) | KitSheet | sheets |
| Picker Tick rows with a trailing slot | KitChoiceRow | controls |
| KitChoiceCard, KitSummaryCard, KitFab (new parts) | – | controls, rows |
| Message box as two rows (words, then plus, chips and Send) | KitComposerBox | chat (Flutter's idle box is one row: plus, words, mic) |
| Tool rows on one line, the work line in the Flutter shape | KitToolRow, KitWorkLine | chat |
| Prompt bubble on surface2; Material typography on Geist | KitPromptBubble, MaterialBridge | chat, foundation |

Parked improvements (owner-approved, re-apply only after the rebuild): the bright live colour on light themes; the
14/13/12 small-text split; 72 dp two-line rows.

## How each part is done

1. Read the Dart part and its golden test (`test/goldens/kit/<part>_golden_test.dart`): the scene inputs, the size, the frame.
2. Fetch its goldens: `native/tool/design/fetch_flutter_goldens.py --repo <oc_app> --out looks-gate/flutter <stem>...`.
3. Add a gate case in `native/ui/kit-chat/src/desktopTest/.../gate/` with the same inputs (`FlutterGoldenGate.check("<stem>") { ... }`; `Frame.Part` repeats `kitGalleryPart`, `Frame.Screen` for scenes with their own backdrop).
4. Change the native part until its diff % is as low as the fonts allow; keep every existing native signature, add parameters with defaults only. Tokens come from `ui/design/generated/FlutterKit.kt` (run `native/tool/design/gen_tokens.py`), never typed.
5. Commit the part with its gate case and report the diff % per golden (light and dark).

Golden counts below are per part (light and dark, all sizes). "–" means no native file names the Dart part yet (missing, or ported under another name: check the kit map).


### Batch 1: foundation (10 parts, 226 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/kit_motion.dart` | `design/generated/FlutterKit.kt`, `design/motion/MotionTokens.kt`, `kit/motion/KitMotion.kt` | 54 (kit_motion_box_default, kit_motion_box_default_1280x800, kit_motion_box_default_1600x1000, kit_motion_box_default_360x800 …) |
| `lib/ui/kit/kit_surface.dart` | `kit/foundation/KitSurface.kt` | 34 (kit_surface_inset, kit_surface_levels, kit_surface_panel, kit_surface_panel_1280x800 …) |
| `lib/ui/kit/kit_icon.dart` | `kit/icon/KitIcon.kt` | 32 (kit_icon_brand, kit_icon_duotone, kit_icon_sizes, kit_icon_sizes_1280x800 …) |
| `lib/ui/kit/kit_text.dart` | `design/generated/FlutterKit.kt`, `design/type/TypeRole.kt`, `design/type/TypeScale.kt` … | 32 (kit_text_mono, kit_text_mono_ar, kit_text_mono_ar_1280x800, kit_text_mono_text2 …) |
| `lib/ui/kit/kit_since.dart` | `kitchat/turn/KitElapsed.kt` | 22 (kit_since_connecting, kit_since_minutes, kit_since_minutes_ar, kit_since_minutes_ar_1280x800 …) |
| `lib/ui/kit/kit_tappable.dart` | `kit/foundation/KitTappable.kt` | 22 (kit_tappable_disabled, kit_tappable_disabled_1280x800, kit_tappable_enabled, kit_tappable_enabled_1280x800 …) |
| `lib/ui/kit/motion/kit_tab_switcher.dart` | `kit/nav/KitTabStrip.kt`, `kit/nav/KitTabSwitcher.kt` | 22 (kit_tab_switcher_counts_loading, kit_tab_switcher_counts_loading_1280x800, kit_tab_switcher_default, kit_tab_switcher_default_1280x800 …) |
| `lib/ui/kit/style/kit_signal.dart` | – | 8 (kit_signal_chat, kit_signal_chat_folded, kit_signal_home, kit_signal_home_ar) |
| `lib/ui/kit/kit_bidi.dart` | `kit/text/KitBidi.kt` | 0 |
| `lib/ui/kit/kit_bottom_inset.dart` | – | 0 |

### Batch 2: foundation (10 parts, 0 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/kit_copy.dart` | – | 0 |
| `lib/ui/kit/kit_effects.dart` | – | 0 |
| `lib/ui/kit/kit_layout.dart` | `design/layout/Layout.kt` | 0 |
| `lib/ui/kit/kit_redact.dart` | – | 0 |
| `lib/ui/kit/kit_shape.dart` | `design/tokens/Shape.kt` | 0 |
| `lib/ui/kit/kit_time.dart` | – | 0 |
| `lib/ui/kit/kit_tokens.dart` | `design/generated/FlutterKit.kt`, `design/tokens/Tokens.kt` | 0 |
| `lib/ui/kit/motion/kit_animated_rows.dart` | `kit/motion/KitAnimatedRows.kt` | 0 |
| `lib/ui/kit/motion/kit_haptics.dart` | – | 0 |
| `lib/ui/kit/motion/kit_motion_parts.dart` | `kit/motion/KitAnimatedValue.kt`, `kit/motion/KitDim.kt`, `kit/motion/KitSpin.kt` … | 0 |

### Batch 3: foundation (10 parts, 0 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/motion/kit_page_transitions.dart` | – | 0 |
| `lib/ui/kit/motion/kit_refresh.dart` | `kit/motion/KitRefresh.kt` | 0 |
| `lib/ui/kit/motion/kit_reveal.dart` | `kit/motion/KitAnimatedRows.kt`, `kit/motion/KitEntrance.kt`, `kit/motion/KitLeaving.kt` … | 0 |
| `lib/ui/kit/style/kit_signal_slip.dart` | `kit/style/KitSignalSlip.kt` | 0 |
| `lib/ui/kit/style/kit_style.dart` | – | 0 |
| `lib/ui/kit/style/kit_style_brutal.dart` | `design/style/BrutalSpec.kt` | 0 |
| `lib/ui/kit/style/kit_style_paper.dart` | `design/style/PaperSpec.kt` | 0 |
| `lib/ui/kit/style/kit_style_signal.dart` | – | 0 |
| `lib/ui/kit/style/kit_style_soft.dart` | `design/style/SoftSpec.kt` | 0 |
| `lib/ui/kit/style/kit_style_solid.dart` | – | 0 |

### Batch 4: foundation (1 parts, 0 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/style/kit_style_theme.dart` | – | 0 |

### Batch 5: controls (10 parts, 300 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/kit_image.dart` | `kit/content/image/KitImageView.kt`, `kit/content/image/KitZoom.kt`, `kit/content/image/KitZoomControls.kt` | 42 (kit_image_avatar, kit_image_avatar_ar, kit_image_avatar_ar_1280x800, kit_image_avatar_error …) |
| `lib/ui/kit/kit_icon_button.dart` | `kit/action/KitIconButton.kt` | 36 (kit_icon_button_copied, kit_icon_button_default, kit_icon_button_default_1280x800, kit_icon_button_default_1600x1000 …) |
| `lib/ui/kit/kit_choice_list.dart` | `kit/choice/KitChoiceCard.kt`, `kit/choice/KitChoiceList.kt` | 30 (kit_choice_list_answered, kit_choice_list_answered_elsewhere, kit_choice_list_choice_disabled, kit_choice_list_default …) |
| `lib/ui/kit/kit_progress.dart` | `kit/state/KitLoadingBar.kt`, `kit/state/KitProgress.kt`, `kit/state/KitSkeletonRows.kt` | 30 (kit_progress_failed, kit_progress_known, kit_progress_staged, kit_progress_staged_1280x800 …) |
| `lib/ui/kit/kit_segmented.dart` | `kit/choice/KitSegmented.kt` | 30 (kit_segmented_default, kit_segmented_default_1280x800, kit_segmented_default_1600x1000, kit_segmented_default_360x800 …) |
| `lib/ui/kit/kit_swatch.dart` | `kit/choice/KitSwatch.kt` | 30 (kit_swatch_accent, kit_swatch_disabled, kit_swatch_grid, kit_swatch_grid_1280x800 …) |
| `lib/ui/kit/kit_field.dart` | `kit/input/KitField.kt` | 28 (kit_field_checking, kit_field_checking_slow, kit_field_counter, kit_field_default …) |
| `lib/ui/kit/kit_term.dart` | – | 26 (kit_term_default, kit_term_focused, kit_term_open, kit_term_open_1280x800 …) |
| `lib/ui/kit/kit_progress_row.dart` | – | 24 (kit_progress_row_at_limit, kit_progress_row_empty, kit_progress_row_loaded, kit_progress_row_loaded_1280x800 …) |
| `lib/ui/kit/kit_status_mark.dart` | – | 24 (kit_status_mark_all, kit_status_mark_all_1280x800, kit_status_mark_all_1600x1000, kit_status_mark_all_360x800 …) |

### Batch 6: controls (10 parts, 131 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/kit_divider.dart` | – | 22 (kit_divider_insets, kit_divider_insets_1280x800, kit_divider_insets_1600x1000, kit_divider_insets_360x800 …) |
| `lib/ui/kit/kit_level_meter.dart` | – | 22 (kit_level_meter_listening_high, kit_level_meter_listening_high_1280x800, kit_level_meter_listening_high_1600x1000, kit_level_meter_listening_high_360x800 …) |
| `lib/ui/kit/kit_search_field.dart` | `kit/input/KitSearchField.kt` | 20 (kit_search_field_disabled, kit_search_field_empty, kit_search_field_filtered, kit_search_field_nomatch …) |
| `lib/ui/kit/kit_chip.dart` | `kit/chip/KitChip.kt` | 16 (kit_chip_default, kit_chip_default_1280x800, kit_chip_default_text2, kit_chip_default_text2_1280x800 …) |
| `lib/ui/kit/kit_needs_you.dart` | `kit/state/KitNeedsYou.kt` | 16 (kit_needs_you_badges, kit_needs_you_badges_1280x800, kit_needs_you_marks, kit_needs_you_marks_1280x800 …) |
| `lib/ui/kit/kit_qr.dart` | `kit/content/qr/KitQr.kt` | 10 (kit_qr_default, kit_qr_default_1280x800, kit_qr_too_long, kit_qr_too_long_1280x800 …) |
| `lib/ui/kit/kit_key_value.dart` | – | 7 (kit_key_value_default, kit_key_value_default_1280x800, kit_key_value_default_ar, kit_key_value_default_text2 …) |
| `lib/ui/kit/kit_filter_chip.dart` | – | 6 (kit_filter_chip_default, kit_filter_chip_default_1280x800, kit_filter_chip_default_text2, kit_filter_chip_default_text2_1280x800) |
| `lib/ui/kit/kit_filter_chips.dart` | `kit/chip/KitFilterChips.kt` | 6 (kit_filter_chips_default, kit_filter_chips_default_1280x800, kit_filter_chips_default_text2, kit_filter_chips_default_text2_1280x800) |
| `lib/ui/kit/kit_section_label.dart` | `kit/row/KitSectionLabel.kt` | 6 (kit_section_label_page, kit_section_label_page_1280x800, kit_section_label_page_text2) |

### Batch 7: controls (6 parts, 6 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/kit_status_tag.dart` | – | 6 (kit_status_tag_default, kit_status_tag_default_1280x800, kit_status_tag_default_text2, kit_status_tag_default_text2_1280x800) |
| `lib/ui/kit/kit_buttons.dart` | `kit/KitButton.kt`, `kit/action/KitActionBlock.kt` | 0 |
| `lib/ui/kit/kit_illustration.dart` | – | 0 |
| `lib/ui/kit/kit_skeleton_transcript.dart` | – | 0 |
| `lib/ui/kit/kit_task_mark.dart` | – | 0 |
| `lib/ui/kit/kit_technical_value.dart` | `kit/state/KitDetailsFold.kt` | 0 |

### Batch 8: rows, groups and states (10 parts, 306 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/kit_notice.dart` | `kit/state/KitNotice.kt` | 52 (kit_notice_cost, kit_notice_default, kit_notice_default_1280x800, kit_notice_default_1600x1000 …) |
| `lib/ui/kit/kit_code_block.dart` | `kitchat/code/CodeLanguage.kt`, `kitchat/code/CodeTokenizer.kt`, `kitchat/code/KitCodeBlock.kt` | 44 (kit_code_block_code_header, kit_code_block_code_header_1280x800, kit_code_block_code_header_text2, kit_code_block_code_header_text2_1280x800 …) |
| `lib/ui/kit/kit_state_view.dart` | `kit/state/KitStateView.kt` | 30 (kit_state_view_calm, kit_state_view_calm_inline, kit_state_view_calm_text2, kit_state_view_empty …) |
| `lib/ui/kit/kit_status_line.dart` | `kit/status/KitStatusLine.kt` | 30 (kit_status_line_connection, kit_status_line_connection_1280x800, kit_status_line_connection_text2, kit_status_line_connection_text2_1280x800 …) |
| `lib/ui/kit/kit_log_panel.dart` | `kit/content/log/KitLogBuffer.kt`, `kit/content/log/KitLogPanel.kt`, `kit/log/KitLogPanel.kt` | 26 (kit_log_panel_dropped, kit_log_panel_empty, kit_log_panel_ended_failed, kit_log_panel_failed_report …) |
| `lib/ui/kit/kit_receipt.dart` | `kit/state/KitReceipt.kt` | 26 (kit_receipt_answered_elsewhere, kit_receipt_answered_elsewhere_text2, kit_receipt_answered_elsewhere_text2_1280x800, kit_receipt_automatic …) |
| `lib/ui/kit/kit_row.dart` | `kit/KitRow.kt`, `kit/row/KitRow.kt` | 26 (kit_row_context_menu, kit_row_default, kit_row_default_1280x800, kit_row_destructive …) |
| `lib/ui/kit/kit_action_stack.dart` | `kit/action/KitActionBlock.kt` | 24 (kit_action_stack_default, kit_action_stack_default_1280x800, kit_action_stack_default_1600x1000, kit_action_stack_default_360x800 …) |
| `lib/ui/kit/kit_checklist.dart` | – | 24 (kit_checklist_before_start, kit_checklist_before_start_text2, kit_checklist_compact, kit_checklist_done …) |
| `lib/ui/kit/kit_task_card.dart` | – | 24 (kit_task_card_blocked, kit_task_card_default, kit_task_card_default_1280x800, kit_task_card_default_text2 …) |

### Batch 9: rows, groups and states (10 parts, 81 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/kit_details_fold.dart` | `kit/state/KitDetailsFold.kt` | 20 (kit_details_fold_collapsed, kit_details_fold_empty, kit_details_fold_open_child, kit_details_fold_open_text …) |
| `lib/ui/kit/kit_group_note.dart` | `kit/row/KitGroupNote.kt` | 16 (kit_group_note_default, kit_group_note_default_1280x800, kit_group_note_default_1600x1000, kit_group_note_default_360x800 …) |
| `lib/ui/kit/kit_feed_item.dart` | `kit/row/KitRow.kt`, `kit/row/KitRowParts.kt` | 10 (kit_feed_item_default, kit_feed_item_default_1280x800, kit_feed_item_default_text2, kit_feed_item_default_text2_1280x800 …) |
| `lib/ui/kit/kit_consequences.dart` | `kit/modal/KitConfirm.kt` | 8 (kit_consequences_default, kit_consequences_default_1280x800, kit_consequences_default_text2, kit_consequences_default_text2_1280x800) |
| `lib/ui/kit/kit_last_known.dart` | – | 8 (kit_last_known_loading, kit_last_known_loading_1280x800, kit_last_known_loading_text2, kit_last_known_loading_text2_1280x800 …) |
| `lib/ui/kit/kit_mini_table.dart` | – | 7 (kit_mini_table_default, kit_mini_table_default_1280x800, kit_mini_table_default_ar, kit_mini_table_default_text2 …) |
| `lib/ui/kit/kit_sliver_row_group.dart` | – | 6 (kit_sliver_row_group_default, kit_sliver_row_group_default_1280x800, kit_sliver_row_group_default_text2) |
| `lib/ui/kit/kit_swipe_action.dart` | – | 6 (kit_swipe_action_acted, kit_swipe_action_revealing, kit_swipe_action_revealing_1280x800) |
| `lib/ui/kit/kit_code_block_parts.dart` | `kitchat/code/CodeText.kt` | 0 |
| `lib/ui/kit/kit_panel.dart` | – | 0 |

### Batch 10: rows, groups and states (4 parts, 0 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/kit_row_group_seam.dart` | `kit/row/KitRowGroup.kt` | 0 |
| `lib/ui/kit/kit_row_parts.dart` | `kit/row/KitExpandRow.kt`, `kit/row/KitRowParts.kt`, `kit/row/KitSwitchRow.kt` | 0 |
| `lib/ui/kit/kit_row_value.dart` | `kit/row/KitRowParts.kt` | 0 |
| `lib/ui/kit/kit_status_slot.dart` | `kit/status/KitStatusSlot.kt` | 0 |

### Batch 11: sheets, dialogs, menus and requests (10 parts, 242 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/kit_sheet.dart` | `kit/modal/KitSheet.kt` | 36 (kit_sheet_attention, kit_sheet_consequences, kit_sheet_default, kit_sheet_disabled …) |
| `lib/ui/kit/kit_menu.dart` | `kit/modal/KitMenu.kt` | 30 (kit_menu_checked, kit_menu_default, kit_menu_default_1280x800, kit_menu_default_1600x1000 …) |
| `lib/ui/kit/kit_request_card.dart` | – | 30 (kit_request_card_answered, kit_request_card_answered_elsewhere, kit_request_card_choice_waiting, kit_request_card_disabled …) |
| `lib/ui/kit/kit_undo.dart` | `kit/modal/KitUndoBar.kt` | 26 (kit_undo_accessible, kit_undo_default, kit_undo_default_1280x800, kit_undo_default_1600x1000 …) |
| `lib/ui/kit/kit_date_time_picker.dart` | – | 24 (kit_date_time_picker_date, kit_date_time_picker_date_1280x800, kit_date_time_picker_date_and_time, kit_date_time_picker_date_invalid …) |
| `lib/ui/kit/kit_dialog.dart` | `kit/modal/KitDialog.kt` | 24 (kit_dialog_alert, kit_dialog_alert_with_action, kit_dialog_alert_with_details, kit_dialog_input_default …) |
| `lib/ui/kit/kit_request_sheet.dart` | – | 24 (kit_request_sheet_form, kit_request_sheet_form_discard, kit_request_sheet_gate, kit_request_sheet_gate_confirm …) |
| `lib/ui/kit/kit_capability_explainer.dart` | `kit/capability/KitCapabilityExplainer.kt` | 20 (kit_capability_explainer_default_1280x800, kit_capability_explainer_default_text2, kit_capability_explainer_default_text2_1280x800, kit_capability_explainer_offer …) |
| `lib/ui/kit/kit_jump_pill.dart` | `kit/frame/KitJumpPill.kt` | 16 (kit_jump_pill_default, kit_jump_pill_default_1280x800, kit_jump_pill_default_text2, kit_jump_pill_default_text2_1280x800 …) |
| `lib/ui/kit/kit_context_sheet.dart` | – | 12 (kit_context_sheet_compacting, kit_context_sheet_details, kit_context_sheet_details_1280x800, kit_context_sheet_details_360x800 …) |

### Batch 12: sheets, dialogs, menus and requests (7 parts, 14 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/kit_floating_action.dart` | `kit/action/KitFab.kt` | 10 (kit_floating_action_default, kit_floating_action_default_1280x800, kit_floating_action_default_text2, kit_floating_action_default_text2_1280x800 …) |
| `lib/ui/kit/kit_confirm_sheet.dart` | `kit/modal/KitConfirm.kt` | 4 (kit_confirm_sheet_alternative, kit_confirm_sheet_alternative_1280x800) |
| `lib/ui/kit/kit_request_answers.dart` | – | 0 |
| `lib/ui/kit/kit_scrollbar.dart` | – | 0 |
| `lib/ui/kit/kit_sheet_frame.dart` | – | 0 |
| `lib/ui/kit/kit_sheet_modal.dart` | – | 0 |
| `lib/ui/kit/kit_sheet_parts.dart` | `kit/modal/KitSheetStep.kt` | 0 |

### Batch 13: frame and navigation (6 parts, 84 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/kit_top_bar.dart` | `kit/frame/KitTopBar.kt` | 26 (kit_top_bar_brand, kit_top_bar_close, kit_top_bar_default, kit_top_bar_default_1280x800 …) |
| `lib/ui/kit/kit_screen.dart` | `kit/frame/KitScreen.kt` | 18 (kit_screen_default, kit_screen_default_1280x800, kit_screen_keyboard, kit_screen_loading …) |
| `lib/ui/kit/kit_breadcrumb.dart` | – | 16 (kit_breadcrumb_collapsed, kit_breadcrumb_default, kit_breadcrumb_default_1280x800, kit_breadcrumb_default_text2 …) |
| `lib/ui/kit/kit_nav.dart` | `kit/nav/KitNavRail.kt`, `kit/nav/KitSidebar.kt` | 10 (kit_nav_phone_no_bar, kit_nav_rail_needs_you_1280x800, kit_nav_rail_part, kit_nav_sidebar_default_1280x800 …) |
| `lib/ui/kit/kit_arrival.dart` | – | 8 (kit_arrival_marked, kit_arrival_marked_1280x800, kit_arrival_marked_text2, kit_arrival_marked_text2_1280x800) |
| `lib/ui/kit/kit_page_route.dart` | – | 6 (kit_page_route_android_mid_forward, kit_page_route_mid_forward, kit_page_route_mid_reverse) |

### Batch 14: chat (10 parts, 278 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/kit_diff_view.dart` | `kitchat/diff/KitDiffView.kt` | 36 (kit_diff_view_binary_renamed, kit_diff_view_binary_renamed_1280x800, kit_diff_view_empty, kit_diff_view_empty_1280x800 …) |
| `lib/ui/kit/chat/kit_composer.dart` | `kitchat/composer/KitComposerBox.kt`, `kitchat/composer/KitStopAndSendPill.kt` | 32 (kit_composer_busy_cannot_send, kit_composer_busy_empty, kit_composer_busy_text, kit_composer_busy_text_text2 …) |
| `lib/ui/kit/chat/kit_markdown.dart` | `kitchat/markdown/KitMarkdown.kt` | 30 (kit_markdown_default, kit_markdown_default_1280x800, kit_markdown_default_text2, kit_markdown_empty …) |
| `lib/ui/kit/kit_ask_line.dart` | – | 30 (kit_ask_line_ask_inline, kit_ask_line_ask_inline_1280x800, kit_ask_line_ask_inline_1600x1000, kit_ask_line_ask_inline_360x800 …) |
| `lib/ui/kit/chat/kit_message.dart` | – | 28 (kit_message_default, kit_message_default_1280x800, kit_message_default_text2, kit_message_default_text2_1280x800 …) |
| `lib/ui/kit/chat/kit_turn.dart` | `kitchat/turn/KitLiveSweep.kt` | 28 (kit_turn_default_1280x800, kit_turn_default_text2, kit_turn_default_text2_1280x800, kit_turn_failed …) |
| `lib/ui/kit/chat/kit_tool_row.dart` | `kitchat/tool/KitToolRow.kt` | 26 (kit_tool_row_agent, kit_tool_row_agent_done, kit_tool_row_default_1280x800, kit_tool_row_default_text2 …) |
| `lib/ui/kit/chat/kit_agent_strip.dart` | – | 24 (kit_agent_strip_done, kit_agent_strip_done_1280x800, kit_agent_strip_lead_only, kit_agent_strip_lead_only_1280x800 …) |
| `lib/ui/kit/chat/kit_composer_chips.dart` | `kitchat/composer/KitComposerBox.kt` | 24 (kit_composer_chips_attachments, kit_composer_chips_attachments_read_only, kit_composer_chips_default, kit_composer_chips_default_1280x800 …) |
| `lib/ui/kit/chat/kit_queued_message.dart` | – | 20 (kit_queued_message_after_reply, kit_queued_message_default, kit_queued_message_default_1280x800, kit_queued_message_default_text2 …) |

### Batch 15: chat (10 parts, 113 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/chat/kit_work_line.dart` | `kitchat/turn/KitWorkLine.kt` | 18 (kit_work_line_default_1280x800, kit_work_line_default_text2, kit_work_line_default_text2_1280x800, kit_work_line_done …) |
| `lib/ui/kit/kit_context_ring.dart` | `kitchat/turn/KitContextRing.kt` | 18 (kit_context_ring_calm, kit_context_ring_compacting, kit_context_ring_compacting_text2, kit_context_ring_compacting_text2_1280x800 …) |
| `lib/ui/kit/chat/kit_step_timeline.dart` | – | 16 (kit_step_timeline_collapsed, kit_step_timeline_expanded, kit_step_timeline_expanded_1280x800, kit_step_timeline_expanded_ar …) |
| `lib/ui/kit/kit_sense_ask.dart` | – | 11 (kit_sense_ask_default, kit_sense_ask_default_1280x800, kit_sense_ask_default_ar, kit_sense_ask_default_text2 …) |
| `lib/ui/kit/chat/kit_background_work_line.dart` | – | 10 (kit_background_work_line_many, kit_background_work_line_one, kit_background_work_line_one_1280x800, kit_background_work_line_one_text2 …) |
| `lib/ui/kit/chat/kit_project_file_list.dart` | – | 10 (kit_project_file_list_default, kit_project_file_list_default_1280x800, kit_project_file_list_default_text2, kit_project_file_list_default_text2_1280x800 …) |
| `lib/ui/kit/kit_context_chart.dart` | – | 10 (kit_context_chart_climb, kit_context_chart_climb_1280x800, kit_context_chart_climb_360x800, kit_context_chart_climb_ar …) |
| `lib/ui/kit/chat/kit_phrase_chips.dart` | `kit/chip/KitPhraseChips.kt` | 8 (kit_phrase_chips_default, kit_phrase_chips_default_1280x800, kit_phrase_chips_default_text2, kit_phrase_chips_default_text2_1280x800 …) |
| `lib/ui/kit/chat/kit_composer_glow.dart` | `kitchat/composer/ComposerGlow.kt` | 6 (kit_composer_glow_classic_one, kit_composer_glow_classic_two, kit_composer_glow_soft_ring) |
| `lib/ui/kit/chat/kit_stream_caret.dart` | – | 6 (kit_stream_caret_default, kit_stream_caret_default_1280x800, kit_stream_caret_default_text2) |

### Batch 16: chat (10 parts, 10 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/chat/kit_transcript_excerpt.dart` | – | 6 (kit_transcript_excerpt_loading, kit_transcript_excerpt_loading_1280x800, kit_transcript_excerpt_loading_text2, kit_transcript_excerpt_loading_text2_1280x800) |
| `lib/ui/kit/chat/kit_markdown_table.dart` | – | 4 (kit_markdown_table, kit_markdown_table_1280x800) |
| `lib/ui/kit/chat/kit_composer_caption.dart` | – | 0 |
| `lib/ui/kit/chat/kit_composer_classic.dart` | `kitchat/composer/ComposerGlow.kt`, `kitchat/composer/KitComposerBox.kt` | 0 |
| `lib/ui/kit/chat/kit_composer_edge.dart` | – | 0 |
| `lib/ui/kit/chat/kit_composer_layer.dart` | – | 0 |
| `lib/ui/kit/chat/kit_composer_status_strip.dart` | `kitchat/composer/ComposerStripReveal.kt`, `kitchat/composer/KitComposerStop.kt` | 0 |
| `lib/ui/kit/chat/kit_composer_tray.dart` | `kitchat/composer/KitComposerTray.kt` | 0 |
| `lib/ui/kit/chat/kit_composer_types.dart` | – | 0 |
| `lib/ui/kit/chat/kit_find_mark.dart` | – | 0 |

### Batch 17: chat (8 parts, 0 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/chat/kit_markdown_fresh.dart` | – | 0 |
| `lib/ui/kit/chat/kit_markdown_image.dart` | – | 0 |
| `lib/ui/kit/chat/kit_message_lift.dart` | – | 0 |
| `lib/ui/kit/chat/kit_project_file_sheet.dart` | – | 0 |
| `lib/ui/kit/chat/kit_step_light.dart` | `kitchat/tool/KitStepLight.kt` | 0 |
| `lib/ui/kit/chat/kit_step_node.dart` | – | 0 |
| `lib/ui/kit/chat/kit_step_signal.dart` | – | 0 |
| `lib/ui/kit/kit_context_region.dart` | – | 0 |

### Batch 18: content and charts (10 parts, 178 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/kit_viewer.dart` | `kit/content/viewer/KitViewerFrame.kt` | 48 (kit_viewer_binary, kit_viewer_binary_1280x800, kit_viewer_code, kit_viewer_code_1280x800 …) |
| `lib/ui/kit/kit_terminal_view.dart` | `kit/content/terminal/KitTerminalView.kt` | 34 (kit_terminal_view_disabled, kit_terminal_view_empty, kit_terminal_view_keys_compact_915x412, kit_terminal_view_keys_latched …) |
| `lib/ui/kit/kit_image_gallery.dart` | – | 20 (kit_image_gallery_disabled, kit_image_gallery_disabled_1280x800, kit_image_gallery_error, kit_image_gallery_error_1280x800 …) |
| `lib/ui/kit/kit_board_lane.dart` | – | 16 (kit_board_lane_lane_empty, kit_board_lane_loaded, kit_board_lane_loaded_1280x800, kit_board_lane_loaded_text2 …) |
| `lib/ui/kit/kit_image_markup.dart` | – | 16 (kit_image_markup_error, kit_image_markup_error_1280x800, kit_image_markup_loading, kit_image_markup_loading_1280x800 …) |
| `lib/ui/kit/kit_work_graph.dart` | – | 16 (kit_work_graph_auto_1280x800, kit_work_graph_empty, kit_work_graph_layers, kit_work_graph_one …) |
| `lib/ui/kit/kit_chart.dart` | – | 15 (kit_chart_default, kit_chart_default_1280x800, kit_chart_default_ar, kit_chart_default_text2 …) |
| `lib/ui/kit/kit_scanner.dart` | – | 13 (kit_scanner_error, kit_scanner_error_text2, kit_scanner_loading, kit_scanner_loading_slow …) |
| `lib/ui/kit/kit_viewer_bodies.dart` | – | 0 |
| `lib/ui/kit/kit_viewer_build.dart` | `kit/content/image/KitImageView.kt`, `kit/content/viewer/KitViewerBinaryBody.kt`, `kit/content/viewer/KitViewerCodeBody.kt` … | 0 |

### Batch 19: content and charts (2 parts, 0 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/kit_viewer_content.dart` | `kit/content/viewer/KitPdfPage.kt`, `kit/content/viewer/KitViewerBytes.kt` | 0 |
| `lib/ui/kit/terminal_key_bar.dart` | – | 0 |

### Batch 20: other parts (10 parts, 230 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/kit_steer_box.dart` | – | 30 (kit_steer_box_answered, kit_steer_box_answered_1280x800, kit_steer_box_answered_text2, kit_steer_box_answered_text2_1280x800 …) |
| `lib/ui/kit/kit_server_trust.dart` | – | 24 (kit_server_trust_certificate, kit_server_trust_certificate_1280x800, kit_server_trust_certificate_1600x1000, kit_server_trust_certificate_360x800 …) |
| `lib/ui/kit/team/kit_digest.dart` | – | 22 (kit_digest_answered, kit_digest_disabled, kit_digest_empty, kit_digest_error …) |
| `lib/ui/kit/team/kit_findings_card.dart` | – | 22 (kit_findings_card_answered, kit_findings_card_disabled, kit_findings_card_empty, kit_findings_card_error …) |
| `lib/ui/kit/team/kit_merge_queue.dart` | – | 22 (kit_merge_queue_answered, kit_merge_queue_disabled, kit_merge_queue_empty, kit_merge_queue_error …) |
| `lib/ui/kit/team/kit_milestone_row.dart` | – | 22 (kit_milestone_row_answered, kit_milestone_row_disabled, kit_milestone_row_empty, kit_milestone_row_error …) |
| `lib/ui/kit/team/kit_phase_card.dart` | – | 22 (kit_phase_card_answered, kit_phase_card_disabled, kit_phase_card_empty, kit_phase_card_error …) |
| `lib/ui/kit/team/kit_plan_card.dart` | – | 22 (kit_plan_card_answered, kit_plan_card_disabled, kit_plan_card_empty, kit_plan_card_error …) |
| `lib/ui/kit/team/kit_project_row.dart` | – | 22 (kit_project_row_answered, kit_project_row_disabled, kit_project_row_empty, kit_project_row_error …) |
| `lib/ui/kit/team/kit_promote_card.dart` | – | 22 (kit_promote_card_answered, kit_promote_card_disabled, kit_promote_card_empty, kit_promote_card_error …) |

### Batch 21: agents and team (10 parts, 173 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/team/kit_server_lane.dart` | – | 22 (kit_server_lane_answered, kit_server_lane_disabled, kit_server_lane_empty, kit_server_lane_error …) |
| `lib/ui/kit/team/kit_timeline_day.dart` | – | 22 (kit_timeline_day_answered, kit_timeline_day_disabled, kit_timeline_day_empty, kit_timeline_day_error …) |
| `lib/ui/kit/kit_frost.dart` | `design/effects/FrostPolicy.kt`, `kit/foundation/KitFrost.kt` | 20 (kit_frost_chat, kit_frost_default, kit_frost_default_1280x800, kit_frost_default_1600x1000 …) |
| `lib/ui/kit/kit_ask_field.dart` | – | 16 (kit_ask_field_answered, kit_ask_field_disabled, kit_ask_field_empty, kit_ask_field_empty_1280x800 …) |
| `lib/ui/kit/kit_continued_line.dart` | – | 16 (kit_continued_line_default, kit_continued_line_default_1280x800, kit_continued_line_default_text2, kit_continued_line_default_text2_1280x800 …) |
| `lib/ui/kit/kit_fact_row.dart` | `kit/row/KitFactRow.kt` | 16 (kit_fact_row_arabic_ar, kit_fact_row_arabic_ar_1280x800, kit_fact_row_default, kit_fact_row_default_1280x800 …) |
| `lib/ui/kit/kit_handoff_record.dart` | – | 16 (kit_handoff_record_folded, kit_handoff_record_folded_1280x800, kit_handoff_record_folded_text2, kit_handoff_record_folded_text2_1280x800 …) |
| `lib/ui/kit/kit_word_text.dart` | – | 16 (kit_word_text_cut, kit_word_text_cut_1280x800, kit_word_text_cut_1600x1000, kit_word_text_cut_360x800 …) |
| `lib/ui/kit/kit_agent_card.dart` | `kitchat/card/KitAgentCard.kt` | 15 (kit_agent_card_answered, kit_agent_card_default, kit_agent_card_default_1280x800, kit_agent_card_default_ar …) |
| `lib/ui/kit/kit_one_time_code.dart` | – | 14 (kit_one_time_code_default, kit_one_time_code_default_1280x800, kit_one_time_code_default_1600x1000, kit_one_time_code_default_360x800 …) |

### Batch 22: other parts (10 parts, 102 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/kit_choice_cards.dart` | `kit/choice/KitChoiceCard.kt` | 12 (kit_choice_cards_default, kit_choice_cards_default_1280x800, kit_choice_cards_default_text2, kit_choice_cards_default_text2_1280x800 …) |
| `lib/ui/kit/kit_choice_tiles.dart` | – | 12 (kit_choice_tiles_default, kit_choice_tiles_default_1280x800, kit_choice_tiles_default_text2, kit_choice_tiles_default_text2_1280x800 …) |
| `lib/ui/kit/kit_progress_ring.dart` | – | 12 (kit_progress_ring_finished, kit_progress_ring_halted, kit_progress_ring_measured, kit_progress_ring_measured_1280x800 …) |
| `lib/ui/kit/kit_carousel.dart` | – | 10 (kit_carousel_five, kit_carousel_five_1280x800, kit_carousel_five_text2, kit_carousel_five_text2_1280x800 …) |
| `lib/ui/kit/kit_fine_tune_panel.dart` | – | 10 (kit_fine_tune_panel_default, kit_fine_tune_panel_default_1280x800, kit_fine_tune_panel_default_text2, kit_fine_tune_panel_default_text2_1280x800 …) |
| `lib/ui/kit/kit_guide.dart` | – | 10 (kit_guide_default, kit_guide_default_1280x800, kit_guide_default_text2, kit_guide_default_text2_1280x800 …) |
| `lib/ui/kit/kit_live_look_preview.dart` | – | 10 (kit_live_look_preview_disabled, kit_live_look_preview_disabled_1280x800, kit_live_look_preview_on, kit_live_look_preview_on_1280x800 …) |
| `lib/ui/kit/kit_step_slider.dart` | – | 10 (kit_step_slider_default, kit_step_slider_default_1280x800, kit_step_slider_default_text2, kit_step_slider_default_text2_1280x800 …) |
| `lib/ui/kit/kit_option_card.dart` | – | 8 (kit_option_card_default, kit_option_card_default_1280x800, kit_option_card_default_text2, kit_option_card_default_text2_1280x800 …) |
| `lib/ui/kit/kit_page_heading.dart` | `kit/frame/KitPageHeading.kt` | 8 (kit_page_heading_disabled, kit_page_heading_switcher, kit_page_heading_switcher_1280x800, kit_page_heading_switcher_text2) |

### Batch 23: other parts (10 parts, 74 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/kit_presence.dart` | `kit/motion/KitPresence.kt` | 8 (kit_presence_shown, kit_presence_shown_1280x800, kit_presence_shown_text2, kit_presence_shown_text2_1280x800) |
| `lib/ui/kit/kit_start_box.dart` | – | 8 (kit_start_box_default, kit_start_box_default_1280x800, kit_start_box_default_text2, kit_start_box_default_text2_1280x800 …) |
| `lib/ui/kit/kit_status_answer.dart` | – | 8 (kit_status_answer_calm, kit_status_answer_default, kit_status_answer_default_1280x800, kit_status_answer_default_text2 …) |
| `lib/ui/kit/kit_web_preview.dart` | – | 8 (kit_web_preview_error, kit_web_preview_error_text2, kit_web_preview_error_text2_1280x800, kit_web_preview_page …) |
| `lib/ui/kit/kit_wheel.dart` | – | 8 (kit_wheel_default, kit_wheel_default_1280x800, kit_wheel_default_text2, kit_wheel_default_text2_1280x800) |
| `lib/ui/kit/kit_window_row.dart` | `kit/state/KitWindowRow.kt` | 8 (kit_window_row_default, kit_window_row_default_1280x800, kit_window_row_default_ar, kit_window_row_default_ar_1280x800 …) |
| `lib/ui/kit/team/kit_spec_block.dart` | – | 8 (kit_spec_block_disabled, kit_spec_block_empty, kit_spec_block_text2, kit_spec_block_text2_1280x800) |
| `lib/ui/kit/kit_agent_choice.dart` | – | 6 (kit_agent_choice_default, kit_agent_choice_default_1280x800, kit_agent_choice_default_text2, kit_agent_choice_default_text2_1280x800) |
| `lib/ui/kit/kit_agent_grid.dart` | – | 6 (kit_agent_grid_default, kit_agent_grid_default_1280x800, kit_agent_grid_default_text2, kit_agent_grid_default_text2_1280x800) |
| `lib/ui/kit/kit_agent_line.dart` | – | 6 (kit_agent_line_default, kit_agent_line_default_1280x800, kit_agent_line_default_text2, kit_agent_line_default_text2_1280x800) |

### Batch 24: other parts (10 parts, 60 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/kit_error_fallback.dart` | – | 6 (kit_error_fallback_error, kit_error_fallback_error_1280x800, kit_error_fallback_error_text2) |
| `lib/ui/kit/kit_floating_panel.dart` | – | 6 (kit_floating_panel_default, kit_floating_panel_default_1280x800, kit_floating_panel_default_text2, kit_floating_panel_default_text2_1280x800) |
| `lib/ui/kit/kit_hero_question.dart` | – | 6 (kit_hero_question_default, kit_hero_question_default_1280x800, kit_hero_question_default_text2, kit_hero_question_default_text2_1280x800) |
| `lib/ui/kit/kit_ink_layer.dart` | – | 6 (kit_ink_layer_rest, kit_ink_layer_rest_1280x800, kit_ink_layer_rest_text2) |
| `lib/ui/kit/kit_live_background.dart` | – | 6 (kit_live_background_default, kit_live_background_default_1280x800, kit_live_background_default_text2, kit_live_background_default_text2_1280x800) |
| `lib/ui/kit/kit_plan_block.dart` | – | 6 (kit_plan_block_default, kit_plan_block_default_1280x800, kit_plan_block_default_text2, kit_plan_block_default_text2_1280x800) |
| `lib/ui/kit/kit_scroll_fade.dart` | `kit/frame/KitScrollFade.kt` | 6 (kit_scroll_fade_default, kit_scroll_fade_default_1280x800, kit_scroll_fade_default_text2, kit_scroll_fade_default_text2_1280x800) |
| `lib/ui/kit/kit_session_tabs.dart` | – | 6 (kit_session_tabs_default, kit_session_tabs_default_1280x800, kit_session_tabs_default_text2, kit_session_tabs_default_text2_1280x800) |
| `lib/ui/kit/kit_task_chip.dart` | – | 6 (kit_task_chip_default, kit_task_chip_default_1280x800, kit_task_chip_default_text2, kit_task_chip_default_text2_1280x800) |
| `lib/ui/kit/kit_tip.dart` | `kit/teaching/KitTip.kt` | 6 (kit_tip_default, kit_tip_default_1280x800, kit_tip_default_text2, kit_tip_default_text2_1280x800) |

### Batch 25: other parts (10 parts, 14 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/kit_widget_preview.dart` | `kit/KitWidgetPreview.kt` | 6 (kit_widget_preview_default, kit_widget_preview_default_1280x800, kit_widget_preview_default_text2, kit_widget_preview_default_text2_1280x800) |
| `lib/ui/kit/kit_widget_style_form.dart` | – | 6 (kit_widget_style_form_default, kit_widget_style_form_default_1280x800, kit_widget_style_form_default_text2, kit_widget_style_form_default_text2_1280x800) |
| `lib/ui/kit/kit_frost_header.dart` | – | 2 (kit_frost_header) |
| `lib/ui/kit/diff/kit_diff_chrome.dart` | `kitchat/diff/DiffModel.kt` | 0 |
| `lib/ui/kit/diff/kit_diff_markdown.dart` | – | 0 |
| `lib/ui/kit/diff/kit_diff_model.dart` | `kitchat/diff/DiffModel.kt` | 0 |
| `lib/ui/kit/diff/kit_diff_rows.dart` | `kitchat/diff/DiffModel.kt` | 0 |
| `lib/ui/kit/kit.dart` | – | 0 |
| `lib/ui/kit/kit_agent_grid_span.dart` | – | 0 |
| `lib/ui/kit/kit_code_surface.dart` | – | 0 |

### Batch 26: other parts (10 parts, 0 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/kit_lead_column.dart` | – | 0 |
| `lib/ui/kit/kit_link_scope.dart` | – | 0 |
| `lib/ui/kit/kit_log_panel_tools.dart` | `kit/content/log/KitLogPanel.kt` | 0 |
| `lib/ui/kit/kit_max_width.dart` | `design/layout/CapRole.kt`, `kit/foundation/KitMaxWidth.kt` | 0 |
| `lib/ui/kit/kit_optical.dart` | – | 0 |
| `lib/ui/kit/kit_reach.dart` | – | 0 |
| `lib/ui/kit/kit_request_question.dart` | – | 0 |
| `lib/ui/kit/kit_request_row.dart` | – | 0 |
| `lib/ui/kit/kit_request_shape.dart` | – | 0 |
| `lib/ui/kit/kit_surface_look.dart` | – | 0 |

### Batch 27: other parts (10 parts, 0 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/kit_surface_paint.dart` | – | 0 |
| `lib/ui/kit/kit_system_bars.dart` | `kit/platform/KitPlatform.kt` | 0 |
| `lib/ui/kit/kit_task_sheet.dart` | – | 0 |
| `lib/ui/kit/kit_terminal_links.dart` | `kit/content/terminal/KitTerminalLinks.kt` | 0 |
| `lib/ui/kit/kit_text_halo.dart` | – | 0 |
| `lib/ui/kit/kit_top_bar_centred.dart` | – | 0 |
| `lib/ui/kit/kit_top_bar_pill.dart` | – | 0 |
| `lib/ui/kit/kit_top_bar_scope.dart` | `kit/frame/KitTopBarScope.kt` | 0 |
| `lib/ui/kit/kit_top_bar_tail.dart` | – | 0 |
| `lib/ui/kit/kit_vector_logo.dart` | – | 0 |

### Batch 28: agents and team (2 parts, 0 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/team/kit_team_data.dart` | – | 0 |
| `lib/ui/kit/team/team_parts.dart` | – | 0 |

### Batch 29: scenes (10 parts, 0 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/scenes/folders_open_scene.dart` | – | 0 |
| `lib/ui/kit/scenes/portal_scene.dart` | – | 0 |
| `lib/ui/kit/scenes/servers_cast.dart` | – | 0 |
| `lib/ui/kit/scenes/servers_link_scene.dart` | – | 0 |
| `lib/ui/kit/scenes/servers_welcome_scene.dart` | – | 0 |
| `lib/ui/kit/scenes/setup_cast.dart` | – | 0 |
| `lib/ui/kit/scenes/setup_phone_scene.dart` | – | 0 |
| `lib/ui/kit/scenes/setup_ready_scene.dart` | – | 0 |
| `lib/ui/kit/scenes/setup_steps_scene.dart` | – | 0 |
| `lib/ui/kit/scenes/setup_unplugged_scene.dart` | – | 0 |

### Batch 30: scenes (5 parts, 0 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/scenes/states_parts.dart` | – | 0 |
| `lib/ui/kit/scenes/states_scenes.dart` | – | 0 |
| `lib/ui/kit/scenes/states_working_scene.dart` | – | 0 |
| `lib/ui/kit/scenes/team_discover_scenes.dart` | – | 0 |
| `lib/ui/kit/scenes/team_scenes.dart` | – | 0 |

### Batch 31: live background (10 parts, 0 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/live_background/kit_live_clock.dart` | – | 0 |
| `lib/ui/kit/live_background/kit_live_style.dart` | – | 0 |
| `lib/ui/kit/live_background/kit_live_styles.dart` | – | 0 |
| `lib/ui/kit/live_background/live_mock_chat.dart` | – | 0 |
| `lib/ui/kit/live_background/styles/kit_live_aurora.dart` | – | 0 |
| `lib/ui/kit/live_background/styles/kit_live_blueprint.dart` | – | 0 |
| `lib/ui/kit/live_background/styles/kit_live_bokeh.dart` | – | 0 |
| `lib/ui/kit/live_background/styles/kit_live_brushed_metal.dart` | – | 0 |
| `lib/ui/kit/live_background/styles/kit_live_caustics.dart` | – | 0 |
| `lib/ui/kit/live_background/styles/kit_live_circuit.dart` | – | 0 |

### Batch 32: live background (10 parts, 0 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/live_background/styles/kit_live_constellation.dart` | – | 0 |
| `lib/ui/kit/live_background/styles/kit_live_dot_ripple.dart` | – | 0 |
| `lib/ui/kit/live_background/styles/kit_live_flow_threads.dart` | – | 0 |
| `lib/ui/kit/live_background/styles/kit_live_glow_spill.dart` | – | 0 |
| `lib/ui/kit/live_background/styles/kit_live_halftone.dart` | – | 0 |
| `lib/ui/kit/live_background/styles/kit_live_honeycomb.dart` | – | 0 |
| `lib/ui/kit/live_background/styles/kit_live_light_rays.dart` | – | 0 |
| `lib/ui/kit/live_background/styles/kit_live_liquid_blobs.dart` | – | 0 |
| `lib/ui/kit/live_background/styles/kit_live_matrix_rain.dart` | – | 0 |
| `lib/ui/kit/live_background/styles/kit_live_mesh.dart` | – | 0 |

### Batch 33: live background (6 parts, 0 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/live_background/styles/kit_live_oscilloscope.dart` | – | 0 |
| `lib/ui/kit/live_background/styles/kit_live_radar.dart` | – | 0 |
| `lib/ui/kit/live_background/styles/kit_live_silk.dart` | – | 0 |
| `lib/ui/kit/live_background/styles/kit_live_support.dart` | – | 0 |
| `lib/ui/kit/live_background/styles/kit_live_toolkit.dart` | – | 0 |
| `lib/ui/kit/live_background/styles/kit_live_topo.dart` | – | 0 |

### Batch 34: not ported (owner 2026-10-26: no glass) (1 parts, 18 goldens)

| Flutter part | Native file(s) | Goldens |
|---|---|---|
| `lib/ui/kit/glass/kit_glass.dart` | – | 18 (kit_glass_apart, kit_glass_default, kit_glass_default_1280x800, kit_glass_default_1600x1000 …) |

---
278 Flutter kit files, 34 batches, 2973 kit goldens (2640 assigned to a part; the rest belong to galleries named after scenes).

The gate lives in `native/ui/kit-chat/src/desktopTest/.../gate/FlutterGoldenGate.kt` because kit-chat sees both kits; the first batch on `:ui:kit-content` parts moves it to a shared test fixture (one small commit).
