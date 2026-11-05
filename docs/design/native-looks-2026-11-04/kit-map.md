# Kit map: Flutter kit parts to native Compose parts (2026-11-04)

This maps every part of the Flutter kit (`lib/ui/kit/**` plus the theme files, product 2.1.1, `mobile-next/master` 8302de537) to its native Kotlin Multiplatform and Compose counterpart, so a design lead can port the look part by part. Each Flutter file is in exactly one row (small helpers share a row). Status says whether the native part exists and whether it looks like ours; I have only judged "matches" where tokens and structure are ported, and "(unverified)" means the part exists but I did not compare pixels.

Method and limits: native files were read from the VPS worktree `~/wt/lane-looks/native` (branch `native/looks-pass-2026-11-04`), which has uncommitted edits in progress (for example the new Phosphor `KitIcons.kt`), so this is a snapshot. Screen counts are distinct files that name any public class or function of the part: Flutter in `lib/ui/screens`, `lib/ui/widgets` and the other non-kit `lib/ui` files; native in `native/feature/*`, `ui/routes` and `ui/shell`. A Flutter count of 0 means the part is only used inside other kit parts. Calibration came from the `before/` PNGs (chat running, conversations, servers). Priority: P1 = many screens or one of the 8 key journeys (first run, conversations home, chat with a running turn, model picker, settings hub, servers, file viewer, This phone / Keep running).


## Status counts

looks different: 12, looks different (unverified): 58, matches: 10, missing: 20 (total rows: 100)


## Kit map

| Part (Flutter name) | Flutter file | Native file(s) | Status | Gap | Screens (Flutter / native) | Priority |
|---|---|---|---|---|---|---|
| KitIcon, AppIcons (Phosphor regular) | `kit_icon.dart`, `app_iconography.dart` | `KitIcons.kt`, `KitIcon.kt`, `KitIconBuild.kt` (+3) | looks different | Phosphor set (198 glyphs, names match AppIcons) now exists in the worktree but is uncommitted and no screen calls KitIcons yet; screens and bars still show text, rows have no leading icons | 289 / 0 | P1 |
| KitButton, KitAction, KitActionStack (primary/secondary/tertiary) | `kit_buttons.dart`, `kit_action_stack.dart` | `KitButton.kt`, `KitActionBlock.kt`, `KitInlineAction.kt` (+1) | looks different (unverified) | Roles exist; screens use bare text links/content-width buttons where Flutter has one full-width mint primary at the bottom | 239 / 159 | P1 |
| KitTokens, KitShape, spacing | `kit_tokens.dart`, `kit_shape.dart` | `Tokens.kt`, `Shape.kt`, `Space.kt` (+1) | matches | Graphite ramp, radii (14/18/22/26/30) and 4 dp steps ported with KDoc parity; values not pixel-checked | 228 / 112 | P1 |
| KitText, text roles, word helpers | `kit_text.dart`, `kit_word_text.dart`, `kit_optical.dart` (+6) | `KitElapsed.kt`, `KitText.kt`, `TypeRole.kt` (+6) | matches | 2026-10-30 scale (28/34 title, 22/28, 16/22) and 1.3x chrome cap ported; screens do not use the 28 sp large title (see KitPageHeading) | 226 / 105 | P1 |
| KitRow (title, supporting, leading, trailing) | `kit_row.dart`, `kit_row_parts.dart`, `kit_lead_column.dart` | `KitRow.kt`, `KitRowParts.kt`, `KitEnabled.kt` | looks different | Part supports the 28 dp icon tile and chevron but screens pass no icons: rows lose the leading column; state word is grey at far right | 183 / 144 | P1 |
| KitNotice (error, cost, offer) | `kit_notice.dart` | `KitNotice.kt` | looks different (unverified) | Ported | 166 / 139 | P1 |
| Theme roles, packs, accents, app theme | `theme_roles.dart`, `app_theme.dart`, `theme_packs.dart` (+2) | `Roles.kt`, `Packs.kt`, `PacksSignal.kt` (+11) | matches | Roles and derivation ported; mint #3DDC8A / #087F43 and amber attention in place | 141 / 44 | P1 |
| KitScreen (+ twoPane, threePane) | `kit_screen.dart` | `KitScreen.kt`, `KitParts.kt` | looks different (unverified) | Frame and panes exist; hub screens start with a small top-bar label, not the Flutter 28 sp large title | 138 / 103 | P1 |
| KitTopBar (+ scope, centred, pill, tail) | `kit_top_bar.dart`, `kit_top_bar_scope.dart`, `kit_top_bar_centred.dart` (+2) | `KitTopBar.kt`, `KitTopBarScope.kt`, `KitExitGlyph.kt` (+1) | looks different | Actions are text KitLineAction that wrap ('Sear/ch'); Flutter uses Phosphor icon buttons plus overflow; server-scope pill missing | 128 / 123 | P1 |
| KitStateView (empty, error, loading, offline) | `kit_state_view.dart`, `kit_error_fallback.dart` | `KitStateView.kt` | looks different (unverified) | Ported; Flutter line-art scenes absent | 111 / 45 | P1 |
| KitConfirmSheet, KitConsequences | `kit_confirm_sheet.dart`, `kit_consequences.dart` | `KitConfirm.kt` | looks different (unverified) | Ported (destructive, consequences) | 96 / 68 | P1 |
| KitDetailsFold, KitTechnicalValue | `kit_details_fold.dart`, `kit_technical_value.dart` | `KitDetailsFold.kt` | looks different (unverified) | Ported (Details under every error) | 90 / 21 | P1 |
| KitMenu | `kit_menu.dart` | `KitMenu.kt` | looks different (unverified) | Ported (panel and row menu); no overflow wired in top bar | 69 / 7 | P1 |
| KitStatusLine, KitStatusSlot, KitStatusMark, KitStatusAnswer | `kit_status_line.dart`, `kit_status_slot.dart`, `kit_status_mark.dart` (+1) | `KitStatusLine.kt`, `KitStatusSlot.kt`, `KitStatus.kt` (+2) | looks different | Status is a grey word at far right; Flutter leads the subline with a coloured word (amber Needs you, mint Working); 'Everything is running' is not the page hero | 54 / 11 | P1 |
| KitSheet (+ frame, modal, parts, step) | `kit_sheet.dart`, `kit_sheet_frame.dart`, `kit_sheet_modal.dart` (+1) | `KitSheet.kt`, `KitSheetStep.kt`, `KitBackGuard.kt` | looks different (unverified) | Ported with height steps; model picker content is the weak part, not the shell | 53 / 71 | P1 |
| KitChoiceList (single, multi) | `kit_choice_list.dart` | `KitChoiceList.kt` | looks different | Model picker keeps radio + provider letter + per-row 'Add favorite' text button; Flutter one-line rows with star icon | 50 / 24 | P1 |
| KitField | `kit_field.dart` | `KitField.kt` | looks different | Outlined box with label above; Flutter field is filled, 14 dp radius, label inside | 44 / 57 | P1 |
| KitSectionLabel, KitGroupNote | `kit_section_label.dart`, `kit_group_note.dart` | `KitSectionLabel.kt`, `KitGroupNote.kt` | looks different (unverified) | Ported; spacing (22 dp between sections) unverified | 43 / 51 | P1 |
| KitRowValue, KitKeyValue | `kit_row_value.dart`, `kit_key_value.dart` | `KitRowParts.kt` | looks different (unverified) | Trailing value exists; Servers still shows plain key-value lines with no row | 29 / 76 | P1 |
| KitIconButton | `kit_icon_button.dart` | `KitIconButton.kt` | looks different | Part exists but bars do not use it; 48 dp target and ring ok, glyphs are stand-ins | 29 / 0 | P1 |
| KitSearchField, KitSearchNoMatch | `kit_search_field.dart` | `KitSearchField.kt` | looks different | Outlined box with a label above; Flutter is a filled fully rounded field with magnifier (or icon only) | 22 / 11 | P1 |
| KitIllustration + line-art scenes (first run, setup, servers, states) | `kit_illustration.dart`, `scenes/*` | - | missing | Missing: no line-art phone/prompt scene on first run, empty states or setup | 21 / 0 | P1 |
| KitNeedsYou | `kit_needs_you.dart` | `KitNeedsYou.kt` | looks different (unverified) | Ported; amber surface 9-12% unverified | 19 / 1 | P1 |
| KitCodeBlock (+ parts, surface) | `kit_code_block.dart`, `kit_code_block_parts.dart`, `kit_code_surface.dart` | `KitCodeBlock.kt`, `CodeText.kt`, `CodeLanguage.kt` (+8) | looks different (unverified) | Close in render (14 dp, mono, copy and wrap corners); copy/wrap are text and glyph, not Flutter icon pair | 18 / 16 | P1 |
| KitLayout, window classes, KitMaxWidth, bottom inset | `kit_layout.dart`, `kit_max_width.dart`, `kit_bottom_inset.dart` (+1) | `Layout.kt`, `WindowClass.kt`, `CapRole.kt` (+2) | matches | Compact/medium/wide classes and per-part caps ported | 18 / 0 | P1 |
| KitMarkdown (+ fresh, table, image) | `chat/kit_markdown.dart`, `chat/kit_markdown_fresh.dart`, `chat/kit_markdown_table.dart` (+1) | `KitMarkdown.kt`, `KitMdModel.kt`, `KitMdSource.kt` (+4) | looks different (unverified) | Ported; inline code chip and bold look close in render; tables not compared | 13 / 7 | P1 |
| KitComposer (pill, send disc, field, voice) | `chat/kit_composer.dart`, `chat/kit_composer_layer.dart`, `chat/kit_composer_edge.dart` (+3) | `KitStopAndSendPill.kt`, `KitMicButton.kt`, `KitMicPress.kt` (+4) | looks different | Missing as one part: separate model/attach buttons, plain outlined field, Stop and 'Add to this turn' buttons; no 26 dp pill, no round mint send | 12 / 15 | P1 |
| KitRequestCard, request question, answers, rows | `kit_request_card.dart`, `kit_request_question.dart`, `kit_request_answers.dart` (+2) | `KitPermissionCard.kt`, `KitQuestionCard.kt`, `KitRequestFrame.kt` (+1) | looks different (unverified) | Permission and question cards ported; always-allow confirm unverified | 10 / 1 | P1 |
| KitViewer (frame, bodies, content, build) | `kit_viewer.dart`, `kit_viewer_bodies.dart`, `kit_viewer_build.dart` (+1) | `KitViewerFrame.kt`, `KitViewerCodeBody.kt`, `KitViewerTextBody.kt` (+9) | looks different | File viewer stacks 'Wrap code lines / More file actions / Open with / Save' as centred text under the code; Flutter uses one actions row group with icons | 9 / 4 | P1 |
| KitCapabilityExplainer | `kit_capability_explainer.dart` | `KitCapabilityExplainer.kt`, `KitCapabilities.kt`, `KitCapabilityWords.kt` | looks different (unverified) | Ported; This phone / Keep running rows lack icons | 9 / 3 | P1 |
| KitToolRow, step nodes, step light, step signal, timeline | `chat/kit_tool_row.dart`, `chat/kit_step_node.dart`, `chat/kit_step_light.dart` (+2) | `KitToolRow.kt`, `KitToolNode.kt`, `KitStepLight.kt` (+5) | looks different (unverified) | Verb + mono arg rows ported; timeline rail and step signal not confirmed | 7 / 2 | P1 |
| KitMessage (prompt bubble, agent prose) | `chat/kit_message.dart`, `chat/kit_message_lift.dart` | `TranscriptParts.kt`, `KitMessageDetails.kt`, `KitPromptLift.kt` | looks different | Prompt bubble is mint-tinted; Flutter is surface2 graphite, mint kept for actions and live states | 6 / 5 | P1 |
| KitAskField, KitAskLine, KitSenseAsk, KitHeroQuestion, KitStartBox, KitSteerBox | `kit_ask_field.dart`, `kit_ask_line.dart`, `kit_sense_ask.dart` (+3) | - | missing | No twins (native count not meaningful): native chat uses plain KitField ('Steer while it works...' outlined box) instead of the steer box | 6 / 0 | P1 |
| KitWorkLine (folded work line) | `chat/kit_work_line.dart` | `KitWorkLine.kt`, `KitElapsed.kt` | looks different (unverified) | Ported; renders '3 steps - Working' as grey text, Flutter 'Read 3 files - edited 1 file' folded line | 5 / 4 | P1 |
| KitTurn (turn layout, live sweep) | `chat/kit_turn.dart` | `KitLiveSweep.kt`, `TranscriptList.kt` | looks different (unverified) | Sweep ported; turn composition lives in feature/chat ChatTranscript | 5 / 2 | P1 |
| KitFloatingAction, KitFloatingPanel | `kit_floating_action.dart`, `kit_floating_panel.dart` | - | missing | No mint floating button ('New chat' is a text top-bar action); no floating panel | 4 / 0 | P1 |
| KitFilterChips, KitFilterChip | `kit_filter_chips.dart`, `kit_filter_chip.dart` | `KitFilterChips.kt` | looks different (unverified) | Ported; Conversations shows a cloud of chips over two rows instead of one short row | 3 / 3 | P1 |
| KitChoiceCards, KitChoiceTiles, KitOptionCard | `kit_choice_cards.dart`, `kit_choice_tiles.dart`, `kit_option_card.dart` | - | missing | Missing: first run shows two switches for an either/or; Flutter has selectable cards, mint outline and check | 3 / 0 | P1 |
| Composer chips (model, mode, attach, Ask first) | `chat/kit_composer_chips.dart` | - | missing | No twin: 'Ask first / Server default / Stop' chip row above the field is two loose buttons | 3 / 0 | P1 |
| Composer status strip, KitComposerStop, tray | `chat/kit_composer_status_strip.dart`, `chat/kit_composer_tray.dart` | `KitComposerStop.kt`, `ComposerStripReveal.kt`, `KitComposerTray.kt` | looks different (unverified) | Strip reveal and Stop chip ported; strip text not verified | 2 / 1 | P1 |
| KitPageHeading | `kit_page_heading.dart` | `KitPageHeading.kt` | looks different | Part exists, no screen uses it; hubs lack the 28/34 large title | 2 / 0 | P1 |
| KitRowGroup, row seam, sliver group | `kit_row_group_seam.dart`, `kit_sliver_row_group.dart` | `KitRowGroup.kt` | looks different (unverified) | Grouped hairline list exists; no sliver variant for long lists | 1 / 87 | P1 |
| Composer glow (running green glow) | `chat/kit_composer_glow.dart` | `ComposerGlow.kt`, `MovingPart.kt` | looks different (unverified) | Ported (lap and halo breath) but not drawn in the render (motion off); check on device | 0 / 2 | P1 |
| KitSwitchRow, KitExpandRow | `kit_row_parts.dart` | `KitSwitchRow.kt`, `KitExpandRow.kt` | looks different (unverified) | Ported; first run uses switches where Flutter has choice cards | 132 / 55 | P2 |
| KitProgress, KitProgressRow, KitProgressRing, loading bar | `kit_progress.dart`, `kit_progress_row.dart`, `kit_progress_ring.dart` | `KitProgress.kt`, `KitLoadingBar.kt`, `KitSkeletonRows.kt` | looks different (unverified) | Line and bar ported; ring and row variants missing | 75 / 39 | P2 |
| KitRefresh (pull to refresh) | `motion/kit_refresh.dart` | `KitRefresh.kt` | looks different (unverified) | Ported; Flutter uses its own mint indicator, native follows Android stretch - check | 46 / 0 | P2 |
| KitReveal, KitEntrance, KitAnimatedRows, KitLeaving | `motion/kit_reveal.dart`, `motion/kit_animated_rows.dart` | `KitReveal.kt`, `KitEntrance.kt`, `KitAnimatedRows.kt` (+1) | matches | Same fold/fade API | 36 / 1 | P2 |
| KitDialog (alert, input) | `kit_dialog.dart` | `KitDialog.kt` | looks different (unverified) | Ported | 35 / 12 | P2 |
| KitPageTransitions, KitPageRoute | `motion/kit_page_transitions.dart`, `kit_page_route.dart` | `ShellNavDisplay.kt`, `ShellMotion.kt`, `KitModalMotion.kt` (+2) | looks different (unverified) | Grow-from-row and predictive back exist; Flutter shared-axis variants not 1:1 | 25 / 2 | P2 |
| KitMotion tokens (durations, curves) | `kit_motion.dart` | `MotionTokens.kt`, `KitMotion.kt`, `KitMotionScheme.kt` (+2) | matches | 150-250 ms ladder and easing ported 1:1 | 23 / 9 | P2 |
| KitChip (+ wrap) | `kit_chip.dart` | `KitChip.kt` | looks different (unverified) | Tones and kinds ported; unverified | 18 / 16 | P2 |
| KitRedact, KitLinkScope | `kit_redact.dart`, `kit_link_scope.dart` | `KitChatHeaderParts.kt` | looks different (unverified) | Redaction folded into chat header parts | 17 / 12 | P2 |
| KitSurface, surface look and paint, ink layer | `kit_surface.dart`, `kit_surface_look.dart`, `kit_surface_paint.dart` (+2) | `KitSurface.kt`, `KitPress.kt` | looks different (unverified) | One raised layer and 7-8% hairline exist; per-style decoration not verified | 17 / 0 | P2 |
| KitSegmented | `kit_segmented.dart` | `KitSegmented.kt` | looks different (unverified) | Ported; Thinking choice shown as chips instead | 16 / 5 | P2 |
| KitUndo (undo snackbar) | `kit_undo.dart` | `KitUndoBar.kt` | looks different (unverified) | Ported; unverified | 11 / 3 | P2 |
| KitLogPanel | `kit_log_panel.dart`, `kit_log_panel_tools.dart` | `KitLogPanel.kt`, `KitLogBuffer.kt` | looks different (unverified) | Two native copies (kit and kit-content) | 9 / 7 | P2 |
| KitReceipt | `kit_receipt.dart` | `KitReceipt.kt` | looks different (unverified) | Ported | 9 / 2 | P2 |
| KitFactRow, provenance | `kit_fact_row.dart` | `KitFactRow.kt` | looks different (unverified) | Exists, no screen uses it directly | 9 / 0 | P2 |
| KitDiffView (+ model, rows, chrome, markdown) | `kit_diff_view.dart`, `diff/*` | `KitDiffView.kt`, `DiffModel.kt`, `DiffMarkdown.kt` (+1) | looks different (unverified) | Model ported with parity; rows/chrome unverified | 8 / 3 | P2 |
| KitImage, zoom, gallery, markup | `kit_image.dart`, `kit_image_gallery.dart`, `kit_image_markup.dart` | `KitImageView.kt`, `KitZoom.kt`, `KitZoomState.kt` (+3) | looks different (unverified) | Zoom ported; gallery and markup missing | 8 / 1 | P2 |
| KitChart, KitChecklist, KitMiniTable | `kit_chart.dart`, `kit_checklist.dart`, `kit_mini_table.dart` | - | missing | No kit twin (usage charts, checklists) | 8 / 0 | P2 |
| KitTerminalView, key bar, links | `kit_terminal_view.dart`, `terminal_key_bar.dart`, `kit_terminal_links.dart` (+1) | `KitTerminalView.kt`, `KitTerminalKeyBar.kt`, `KitTerminalKeybar.kt` (+2) | looks different (unverified) | Ported | 6 / 8 | P2 |
| KitTip (teaching bubbles) | `kit_tip.dart`, `kit_guide.dart` | `KitTip.kt` | looks different (unverified) | Tip ported; KitGuide (tour) has no twin | 4 / 4 | P2 |
| KitTappable (press, ripple, haptic) | `kit_tappable.dart`, `kit_ink_layer.dart` | `KitTappable.kt`, `KitPress.kt` | looks different (unverified) | Android ripple and haptics wired; Flutter ink alpha ladder ported but unverified | 4 / 1 | P2 |
| KitSwap, KitSpin, KitDim, KitAnimatedValue, KitPresence | `motion/kit_motion_parts.dart`, `kit_presence.dart` | `KitSwap.kt`, `KitSpin.kt`, `KitDim.kt` (+3) | matches | Ported with Replaces: notes | 4 / 1 | P2 |
| KitQueuedMessage, KitAgentStrip, KitBackgroundWorkLine | `chat/kit_queued_message.dart`, `chat/kit_agent_strip.dart`, `chat/kit_background_work_line.dart` | - | missing | No twin ('Queued - runs after this turn', agent strip) | 4 / 0 | P2 |
| KitTabSwitcher, KitTabStrip | `motion/kit_tab_switcher.dart` | `KitTabSwitcher.kt`, `KitTabStrip.kt` | looks different (unverified) | Sliding indicator ported; not compared | 3 / 2 | P2 |
| KitQr, KitScanner, KitWebPreview, KitWidgetPreview | `kit_qr.dart`, `kit_scanner.dart`, `kit_web_preview.dart` (+1) | `KitQr.kt`, `QrEncoder.kt` | looks different (unverified) | QR matches by rule; scanner and web preview are feature-local | 3 / 1 | P2 |
| KitSwatch (accent swatches) | `kit_swatch.dart` | `KitSwatch.kt` | looks different (unverified) | Ported; theme preview present | 2 / 1 | P2 |
| KitContextSheet, KitContextRegion, KitContextChart, KitContextRing | `kit_context_sheet.dart`, `kit_context_region.dart`, `kit_context_chart.dart` (+1) | `KitContextRing.kt` | looks different (unverified) | Ring ported; sheet is a feature file (ChatContextSheet), chart missing | 2 / 1 | P2 |
| KitAgentCard | `kit_agent_card.dart` | `KitAgentCard.kt` | looks different (unverified) | Ported with modes | 2 / 1 | P2 |
| KitJumpPill | `kit_jump_pill.dart` | `KitJumpPill.kt` | looks different (unverified) | Ported with edge logic; unverified | 2 / 0 | P2 |
| KitServerTrust, KitReach | `kit_server_trust.dart`, `kit_reach.dart` | - | missing | No kit twin (feature-local) | 2 / 0 | P2 |
| KitAgentChoice, KitAgentGrid, KitAgentLine | `kit_agent_choice.dart`, `kit_agent_grid.dart`, `kit_agent_grid_span.dart` (+1) | - | missing | No native twin (agent picker grid) | 1 / 0 | P2 |
| KitPhraseChips (phrase chips above input) | `chat/kit_phrase_chips.dart` | `KitPhraseChips.kt` | looks different (unverified) | Part exists; not wired into the chat screen | 1 / 0 | P2 |
| KitStyle schools (Solid, Soft, Brutal, Paper, Signal) and knobs | `style/kit_style.dart`, `style/kit_style_solid.dart`, `style/kit_style_soft.dart` (+6) | `KitSignalSlip.kt`, `KitRunningFeedLine.kt`, `Style.kt` (+8) | looks different (unverified) | Specs and resolver ported; the painters (soft inset shadows, brutal offset shadow, paper hairlines) need a side-by-side check in Compose | 0 / 21 | P2 |
| KitSwipeAction | `kit_swipe_action.dart` | - | missing | No native twin (swipe to archive/delete) | 0 / 0 | P2 |
| KitWindowRow, KitStatusTag, KitTaskMark | `kit_window_row.dart`, `kit_status_tag.dart`, `kit_task_mark.dart` | `KitWindowRow.kt` | looks different (unverified) | Window row ported; status tag and task mark have no twin | 22 / 2 | P3 |
| KitDivider | `kit_divider.dart` | - | missing | No standalone part (hairlines live inside row groups) | 18 / 0 | P3 |
| KitArrival | `kit_arrival.dart` | - | missing | No native twin (rows arriving after a load) | 16 / 0 | P3 |
| AI Team parts (digest, plan, phase, merge queue, board lane, work graph, task card/chip) | `team/*`, `kit_board_lane.dart`, `kit_work_graph.dart` (+3) | `KitAgentCard.kt` | missing | No kit twins; AI Team screens build their own | 13 / 1 | P3 |
| KitScrollFade, KitScrollbar | `kit_scroll_fade.dart`, `kit_scrollbar.dart` | `KitScrollFade.kt` | looks different (unverified) | Fade ported; Flutter's thin scrollbar has no twin | 7 / 0 | P3 |
| KitDateTimePicker, KitWheel, KitStepSlider | `kit_date_time_picker.dart`, `kit_wheel.dart`, `kit_step_slider.dart` | - | missing | No native twin (schedules use platform pickers?) | 6 / 0 | P3 |
| KitFeedItem, KitLastKnown, KitHandoffRecord | `kit_feed_item.dart`, `kit_last_known.dart`, `kit_handoff_record.dart` | - | missing | No native twin (activity feed lines, last-known value, handoff record) | 5 / 0 | P3 |
| KitPanel, KitPresence helpers, live backgrounds (24 styles) | `kit_panel.dart`, `kit_live_background.dart`, `live_background/*` | - | missing | Aurora off by default in Flutter; native has none (acceptable, P3) | 4 / 0 | P3 |
| KitFrost, KitGlass, frost header | `kit_frost.dart`, `kit_frost_header.dart`, `glass/kit_glass.dart` | `FrostPolicy.kt`, `KitFrost.kt` | looks different (unverified) | Policy and held-state only; no frosted header painter (owner removed Glass 10-26, Flutter part is legacy) | 3 / 2 | P3 |
| KitHaptics | `motion/kit_haptics.dart` | `Haptic.kt` | matches | KitHaptic enum ported | 3 / 1 | P3 |
| KitTranscriptExcerpt, KitFindMark, project file list/sheet | `chat/kit_transcript_excerpt.dart`, `chat/kit_find_mark.dart`, `chat/kit_project_file_list.dart` (+1) | `KitFindExcerpt.kt` | looks different (unverified) | Find excerpt ported; file list/sheet have no twin | 3 / 1 | P3 |
| KitSkeletonTranscript | `kit_skeleton_transcript.dart` | `KitSkeletonRows.kt` | looks different (unverified) | Generic skeleton rows only | 2 / 27 | P3 |
| KitFineTunePanel, KitWidgetStyleForm, KitLiveLookPreview | `kit_fine_tune_panel.dart`, `kit_widget_style_form.dart`, `kit_live_look_preview.dart` | `KitWidgetStylePreview.kt`, `KitWidgetPreview.kt`, `KitThemePreview.kt` | looks different (unverified) | Previews exist; fine-tune panel has no kit part | 2 / 3 | P3 |
| KitSessionTabs | `kit_session_tabs.dart` | - | missing | No native twin | 2 / 0 | P3 |
| KitOneTimeCode | `kit_one_time_code.dart` | - | missing | No native twin (pairing code input) | 2 / 0 | P3 |
| KitTaskSheet, KitRequestSheet | `kit_task_sheet.dart`, `kit_request_sheet.dart` | - | missing | No kit twin (feature-local IsolatedTaskSheet) | 2 / 0 | P3 |
| KitNav (rail, sidebar) | `kit_nav.dart` | `KitNavRail.kt`, `KitSidebar.kt`, `KitNavDestination.kt` | looks different (unverified) | Wide-screen rail and sidebar exist; no screen uses them yet in compact | 1 / 0 | P3 |
| KitSystemBars | `kit_system_bars.dart` | `KitPlatform.kt` | matches | Edge-to-edge bars handled in platform part | 0 / 3 | P3 |
| KitLevelMeter | `kit_level_meter.dart` | `KitVoicePill.kt`, `KitLevelMeterRules.kt` | looks different (unverified) | Folded into voice pill | 0 / 3 | P3 |
| KitVectorLogo, provider logos | `kit_vector_logo.dart` | `KitProviderLogo.kt`, `KitStaticSvg.kt` | looks different (unverified) | Static SVG provider marks; brand marks monochrome check pending | 0 / 1 | P3 |
| KitBreadcrumb, KitCarousel | `kit_breadcrumb.dart`, `kit_carousel.dart` | - | missing | No native twin (unused by key journeys) | 0 / 0 | P3 |
| KitStreamCaret | `chat/kit_stream_caret.dart` | - | looks different (unverified) | Mint caret present in render (folded into markdown) | 0 / 0 | P3 |
| Bare kit export | `kit.dart` | - | matches | Barrel file; no native counterpart needed | 0 / 0 | P3 |

## Native-only parts (no Flutter twin)

| Native part | Path | What it is |
|---|---|---|
| KitSideSheet | `ui/kit/modal/KitSideSheet.kt` | Wide-window side sheet; Flutter used a centred sheet |
| KitPredictiveBack, KitModalMotion, KitBackGuard | `ui/kit/modal/` | Android predictive-back progress and modal motion; Flutter relies on framework routes |
| KitGrow (growKey) | `ui/kit/motion/KitGrow.kt` | Page grows out of the tapped row and shrinks back |
| KitForm, KitFormSheet, KitFormField | `ui/kit-chat/form/` | Structured form card for agent requests; Flutter builds this inside KitRequestCard |
| KitTerminalSlot, KitTerminalLive | `ui/kit-content/terminal/KitTerminalSlot.kt` | Platform slot for the embedded terminal view |
| KitDesktopSettingsMenu, DesktopKitPlatform | `ui/kit/desktopMain` | Desktop-only settings menu and platform part |
| AndroidFrost, AndroidKitPlatform, AndroidKitTerminalSlot, AndroidRemoteTerminalView, AndroidWidgetThemeColors | `ui/*/androidMain` | Android platform actuals (blur, bars, terminal, widget colours) |
| KitSelectTap, KitEnabled, KitAssert (KitRuleBroken) | `ui/kit/choice, foundation` | Selection tap rule, enabled-reason rule and debug asserts |
| KitChatEnvironment, KitChatLocals | `ui/kit-chat/` | Chat-side composition locals |
| PdfPageRasterizer, PdfLimits | `ui/kit-content/pdf, androidMain` | Host-side PDF decode with byte caps (Flutter used a plugin widget) |
| Appearance, KitAppearance | `ui/design/effects/Appearance.kt` | Appearance mode model (light/dark/system) kept in design module |
