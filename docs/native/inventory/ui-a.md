# Inventory UI-A — Flutter `lib/ui` files mapped to the native app

Machine-readable record: [`ui-a.jsonl`](ui-a.jsonl) — one line per Flutter file (248 files, 101384 Dart lines). Base: `native/p12-integrate-2026-11-03` at `71f74de8`. Analysis only; no product code changed.

Field meanings follow the unit brief (`file, lines, kind, summary, native, status, gaps, portability, target_module, catalog_rows, untested`) plus `gaps_uncatalogued` (the subset of `gaps` that no row of `catalog/behaviors.jsonl` describes) and, on one line, `listed_as`: the brief listed `lib/ui/kit/chat/kit_mic_hold_detector.dart`, which never existed; the real file `lib/ui/kit/chat/kit_mic_hold.dart` is recorded under that key.

## Totals

| status | Files | Dart lines |
|---|---:|---:|
| partial | 110 | 63759 |
| missing | 76 | 24358 |
| after-switch | 33 | 7788 |
| done | 21 | 4020 |
| not-needed | 8 | 1459 |

| kind | Files | Dart lines |
|---|---:|---:|
| kit | 225 | 94849 |
| other | 5 | 2246 |
| dead | 5 | 1114 |
| l10n | 4 | 338 |
| screen | 4 | 1596 |
| widget | 3 | 1031 |
| platform-bridge | 2 | 210 |

| portability | Files | Dart lines |
|---|---:|---:|
| agent-portable | 186 | 82279 |
| needs-design | 49 | 15109 |
| platform-specific | 13 | 3996 |

| target_module | Files | Dart lines |
|---|---:|---:|
| :ui:kit | 145 | 54126 |
| :ui:kit-chat | 60 | 28895 |
| :ui:kit-content | 18 | 10448 |
| :ui:design | 6 | 3073 |
| :desktopApp | 5 | 1241 |
| :feature:git | 5 | 1786 |
| :ui:words | 2 | 79 |
| :ui:shell | 2 | 398 |
| :feature:chat | 1 | 69 |
| :core:markdown | 1 | 221 |
| :core:security | 1 | 435 |
| :feature:servers | 1 | 559 |
| :feature:updates | 1 | 54 |

Untested (no Flutter test covers the main behaviour): **27** files. Files with no catalog row at all: **152**.

`after-switch` covers: the five `lib/ui/desktop/*` files and desktop-only kit parts (Linux desktop comes after the switch); AI Team / FactsBook parts (plan 01 D6); all live-background files plus `kit_text_halo` (plan 01 D11, scope rows B-KIT-020/B-KIT-047).

## The 20 biggest missing or partial files

| # | File | Lines | Status | Native today | Gaps |
|---|---|---:|---|---|---|
| 1 | `kit/chat/kit_composer_chips.dart` | 1501 | partial | `BasicComposerHost.kt`, `ComposerScreen.kt`, `ModelChipLabel.kt` | model chip menu (next/previous/favourites via long-press/custom actions); attachment image thumbnails and read-only chips on sent prompts; suggestion rows with description lines, visibleCount + "Show all" and the empty note |
| 2 | `kit/kit_row.dart` | 1491 | partial | `KitRow.kt`, `KitRowParts.kt`, `KitRow.kt` | row menu on long-press/right-click/Shift+F10 with semantic custom actions (native only onLongClick); swipe action; KitRowChip trailing chip column; unavailable form (capability) and enable action; destructive row styling inside the row; titleMaxLines, titleAccent, titleMark, titleDetail; roomy index rows, leadingAtTop, below slot, padding; lead variants: number, badge, check, change mark, bare icon, needs-you dot |
| 3 | `kit/chat/kit_tool_row.dart` | 1485 | partial | `KitToolRow.kt`, `KitToolView.kt`, `KitToolGlyph.kt` | step commands (KitStepCommand) / row menu; notice-style action drop under words at large text |
| 4 | `kit/kit_image.dart` | 1434 | partial | `KitImageView.kt`, `KitZoom.kt`, `KitZoomControls.kt` | KitImage loader (ImageRef source, decode-size, shape clip, loading and failure states); KitAvatar (initials, icon, error badge, optional logo); KitZoom canvas mode (work graph) and KitZoomController; keyboard zoom and pan shortcuts |
| 5 | `kit/kit_date_time_picker.dart` | 1432 | missing | — | date / time / date-and-time picker sheet with kit calendar and typed entry; KitDateTimeRow (not set, set, error, disabled); schedule edit and agent card forms use typed text fields instead (TODO(kit) in ScheduleEditScreen) |
| 6 | `kit/kit_request_card.dart` | 1412 | partial | `KitPermissionCard.kt`, `ChatRequestSlot.kt`, `RequestSlotUi.kt` | choice, gate and reply kinds; answered-elsewhere and expired collapsed rows; Inbox row form (asRow); target lines with change size; details sheet for permission/question; PC keyboard shortcuts once focused; one-answer-once guard across all kinds |
| 7 | `kit/kit_log_panel.dart` | 1388 | missing | — | KitLogPanel part; KitLogBuffer ring buffer (partial line, drop and count overflow); follow newest line, jump pill counts new lines; poll only while visible; live/quiet/ended/failed words; redaction before display and copy; Copy all; Wrap toggle; line selection and copy |
| 8 | `kit/kit_code_block.dart` | 1387 | partial | `KitCodeBlock.kt`, `KitCodeChunk.kt`, `CopyCorner.kt` | header band with caption, file name, counts and 'Copy commands' label; Wrap toggle offered only while a line overflows, with accent on-state; edge fade while a line scrolls sideways |
| 9 | `kit/chat/kit_message.dart` | 1383 | partial | `TranscriptParts.kt`, `ChatTranscript.kt` | prompt bubble width modes and read-only attachment chips/caption; "reads this when…" wait note with escalating action; thought fold (Thinking…/Thought for N); notice fold with action and marker divider rows; speaker line for team roles |
| 10 | `kit/chat/kit_markdown.dart` | 1372 | partial | `KitMarkdown.kt`, `KitMdModel.kt`, `KitMdSource.kt` | find highlighter spans; custom block builder hook; syntax highlighting waits on CodeTokenizer benchmark |
| 11 | `kit/kit_top_bar.dart` | 1338 | partial | `KitTopBar.kt`, `KitExitGlyph.kt` | KitTopBarScope chip with compact menu, KitTopBarLink, KitTopBarBranch, KitTopBarOrigin; KitTopBar.shell + KitShellControls / KitShellControlsLayout; server pill (name, status word, switcher chevron); centred variant; subtitle needs-you count and subtitleTail; update dot on the Settings gear; exit auto-choice from route (fullscreen dialog Close vs Back) |
| 12 | `kit/kit_option_card.dart` | 1309 | missing | — | KitOptionCard theme/glow/surface/plain variants; KitAccentSwatch / KitAccentRow; chosen ring + check; disabled reason; look variant (after the switch with live looks) |
| 13 | `kit/kit_field.dart` | 1297 | partial | `KitField.kt` | draft persistence across dismissal/process kill; "Still checking after 8 s" offers onSlow action; composer mode (unframed multiline); decimal flag for number kind; disabledReason in helper line and trailing actions; per-style field look/halo (KitFieldLook, kitFieldFrame); one-line kind growing to maxLines |
| 14 | `kit/kit_buttons.dart` | 1293 | partial | `KitButton.kt`, `KitActionBlock.kt` | copy actions (KitAction.copy / copyText with redaction and a brief 'Copied' state); keyboard shortcut hint in the label; calm, neutral and hero variants and expand flag; KitInset tertiary inset helper |
| 15 | `kit/chat/kit_step_timeline.dart` | 1238 | partial | `KitWorkLine.kt`, `ChatTranscript.kt` | timeline with node states (pending, running, waiting, done tick, failed flash, stopped); change preview cards (KitStepPreview) |
| 16 | `kit/chat/kit_turn.dart` | 1234 | partial | `KitLiveSweep.kt`, `ChatTranscript.kt`, `TurnFactsWords.kt` | no KitTurn part: turn rhythm/segments (first/middle/last) laid out ad hoc by feature rows; live line under a running turn (activity words: sending, waiting for server, first word slow, thinking, writing, working; elapsed clock; slow-after escalation; AI Team slow words) not drawn; KitLiveSweep exists but is unused; live line extras: plan step 'Step 3 of 5', pending choice with Undo, tap to open steps, stuck command line; footer Copy and More buttons with 'Copy reply so far' wording and meta words (model · tokens · time) on the latest turn; native footer is a quiet tappable line; long-press / right-click / Shift+F10 turn menu on every block; phase end lines for starting(slow)/interrupted/highlighted states |
| 17 | `kit/kit_nav.dart` | 1153 | partial | `KitNavRail.kt`, `KitSidebar.kt`, `KitNavDestination.kt` | KitNav frame choosing none/rail/sidebar by window (short-window rule) not wired into the shell; sidebar destination pane (list in sidebar) and pinned primary; update-available dot; lens spring motion; keyboard focus group (Tab to selected, Up/Down) |
| 18 | `kit/kit_text.dart` | 1099 | partial | `KitText.kt`, `TypeRole.kt`, `TypeScale.kt` | KitSelectable / KitSelectMode (long-press vs fine-pointer-only selection); KitMonoCut.Middle path middle-ellipsis; KitLtr isolation as a public part for mono values; tabular figures option; text halo hook for live background (after switch) |
| 19 | `kit/kit_swatch.dart` | 1057 | missing | — | KitSwatch theme miniature (own roles, frame in current theme); KitSwatch.accent circle; KitSwatchGrid (columns by width, arrow-key focus group); KitThemePreview; disabled swatch with reason (plan 06 §7.2 slice 4; ui/design color/Accents.kt holds only the accent data) |
| 20 | `kit/kit_capability_explainer.dart` | 1049 | missing | — | capability registry (hosts, 21 enable flows, words) matching capabilities.json; explainer variants: unavailable row, missing state, offer notice, folded, working; enable-flow handler registration so Enable never leads nowhere |

## Gaps no catalog row covers (untested behaviour the catalog missed)

The catalog was built from Flutter tests, so these behaviours have no row and no parity test will catch them. Grouped by file (missing/partial only; files marked `untested` flagged ⚠).

- `agent_error_words.dart` — no native mapping from the 11 causes to agentError* headline/hint words (strings exist but are unused; Cause enum is internal); humanized flag letting a recognised cause override a typed error headline
- `agents/agent_persona_text.dart` — no Kotlin accessor maps AgentPersonaId/AgentMaker to the agentPersona*/agentMaker* strings (strings exist, unused); brand names (Claude Code, Codex…) as untranslated constants
- `app_iconography.dart` — generated KitGlyphs set (Phosphor) not landed; only a handful of hand-drawn stand-in marks; selected-navigation duotone variants; RTL mirroring set for direction glyphs; navigation/action/inline icon sizes
- `app_theme.dart` — stackedActions (buttons stack at large text) helper not confirmed
- `git/git_sheet.dart` — "Ask the agent to fix conflicts" (sends a prompt to the conversation agent); "Write it for me" commit message from the agent's last-turn summary; opened as a sheet from the chat with the conversation context (native is a page)
- `git/git_sheet_home.dart` — single main action chosen from what is waiting (_GitMain); conflicted files → one tap asks the agent to resolve them; details fold with exact branch/remote/address
- `git/git_sheet_steps.dart` — commit message prefilled from ticked file names ("Update a and b"); identity prefilled from GitHub account name and noreply address; "Modified · +3 −1" per-file kind and counts line
- `kit/chat/kit_background_work_line.dart` — tap to open the task list in place; per-task rows with redacted command (max 3 lines) and onOpenTask; fade out when the last task ends
- `kit/chat/kit_composer.dart` — no kit KitComposer yet (interim feature arrangement per ComposerScreen doc); glow is state only in the screen; "Stop and send" is a secondary button, not the amber pill; in-box tray, top caption line, blocked-with-reason state
- `kit/chat/kit_composer_caption.dart` — send-failure row with Details and a fix action in Retry's place (native has a one-line note / attachment failure only); long-press menu on Send ("Send later or repeat"); Stop ring arc that turns when it is the one moving part
- `kit/chat/kit_composer_chips.dart` — model chip menu (next/previous/favourites via long-press/custom actions); attachment image thumbnails and read-only chips on sent prompts; suggestion rows with description lines, visibleCount + "Show all" and the empty note
- `kit/chat/kit_composer_classic.dart` — kit part not yet used by the chat composer (ComposerScreen/BasicComposerHost draw an interim arrangement)
- `kit/chat/kit_composer_edge.dart` — kit part not yet used by the chat composer (ComposerScreen/BasicComposerHost draw an interim arrangement)
- `kit/chat/kit_composer_glow.dart` — kit part not yet used by the chat composer (ComposerScreen/BasicComposerHost draw an interim arrangement)
- `kit/chat/kit_composer_layer.dart` — floating composer layer: body scrolls under the composer with measured bottom inset and frost only when content is under it; header glass run-up of the body; countdown seconds and read-aloud toggle inside the pill (not confirmed)
- `kit/chat/kit_composer_status_strip.dart` — kit part not yet used by the chat composer (ComposerScreen/BasicComposerHost draw an interim arrangement)
- `kit/chat/kit_composer_tray.dart` — in-box tray row of round buttons (native offers attach choices as rows in a sheet); "+" ⇄ "×" and fold/fade motion; disabled tray button with its reason
- `kit/chat/kit_composer_types.dart` — KitComposerFailure with fix action; KitComposerTrayAction; delivery enum (after reply / steer / stop and send) as one kit type
- `kit/chat/kit_find_mark.dart` — hit wash spans (all hits, stronger active hit) not drawn in transcript markdown; only the file viewer line mark exists
- `kit/chat/kit_markdown.dart` — find highlighter spans; custom block builder hook; syntax highlighting waits on CodeTokenizer benchmark
- `kit/chat/kit_markdown_image.dart` — inline image thumbnails (native shows a named row per picture); hash-named file and bare "Image" alt fallback naming (not confirmed); dedupe of the same picture
- `kit/chat/kit_markdown_table.dart` ⚠ — column fitting by longest word with shared remainder (native: each column as wide as its widest cell, max 320 dp, always one scroller); inline-code cells never break mid-token; framed surface with header band
- `kit/chat/kit_message.dart` — prompt bubble width modes and read-only attachment chips/caption; "reads this when…" wait note with escalating action; thought fold (Thinking…/Thought for N); notice fold with action and marker divider rows; speaker line for team roles
- `kit/chat/kit_message_lift.dart` — send lift animation (only the LIFT_START_SCALE token exists in MotionTokens)
- `kit/chat/kit_mic_hold.dart` — release must still be heard when voice mode removes the mic mid-hold (native cancels the hold on dispose)
- `kit/chat/kit_phrase_chips.dart` — phrase chip row UI (native has only engine/composer PhraseChips logic); Edit and Hide menu actions
- `kit/chat/kit_project_file_list.dart` — search field + top-level folder browse (native offers @ match chips only); loading bar and error/empty states
- `kit/chat/kit_project_file_sheet.dart` ⚠ — project file picker sheet
- `kit/chat/kit_queued_message.dart` — offline "sends when back online" state; receipt checking / pending-receipt / not-safely-recorded states; OpenCode 2 accepted-after-reply / next-step / server-queued context update states
- `kit/chat/kit_step_light.dart` — name sweep on the running step
- `kit/chat/kit_step_node.dart` — timeline node placement
- `kit/chat/kit_step_signal.dart` — Signal style node lamps, sleepers and folded line
- `kit/chat/kit_step_timeline.dart` — change preview cards (KitStepPreview)
- `kit/chat/kit_tool_row.dart` — step commands (KitStepCommand) / row menu; notice-style action drop under words at large text
- `kit/chat/kit_transcript_excerpt.dart` — no read-only last-known excerpt while the transcript loads (ChatLoad has no excerpt phase); 'Updated … ago / Refreshing' age line; newest-at-bottom layout matching transcript gutters, older messages cut at top
- `kit/chat/kit_turn.dart` — no KitTurn part: turn rhythm/segments (first/middle/last) laid out ad hoc by feature rows; live line under a running turn (activity words: sending, waiting for server, first word slow, thinking, writing, working; elapsed clock; slow-after escalation; AI Team slow words) not drawn; KitLiveSweep exists but is unused; live line extras: plan step 'Step 3 of 5', pending choice with Undo, tap to open steps, stuck command line; footer Copy and More buttons with 'Copy reply so far' wording and meta words (model · tokens · time) on the latest turn; native footer is a quiet tappable line; long-press / right-click / Shift+F10 turn menu on every block; phase end lines for starting(slow)/interrupted/highlighted states
- `kit/chat/kit_work_line.dart` — running 'tail' rule (only the newest N steps in view while work runs) not ported; native caps only by KIT_WORK_STEP_CAP
- `kit/diff/kit_diff_chrome.dart` — file list with open-file highlight and viewed ticks; file header (name, folder, status line, counts, Source switch for Markdown); folded multi-file mode with per-file scrolling box; line/hunk selection bar; loading skeleton rows; 'Showing 12 of 22 lines · Open all' footer (native shows only a static too-big line)
- `kit/diff/kit_diff_markdown.dart` — rendered Markdown diff (block-level added/removed/unchanged runs); Source/rendered toggle for Markdown files
- `kit/diff/kit_diff_model.dart` — split (side-by-side) pair rows; per-file syntax colour of diff lines (lazy parse cache, line limit)
- `kit/diff/kit_diff_rows.dart` — split view rows; line and hunk selection (tap, drag, select-hunk action); soft-wrap option; tapping a gap bar to unfold hidden lines; syntax-coloured line text
- `kit/glass/kit_glass.dart` — no shared floating-surface part for dock, rail and top controls (surface2 + hairline + tight elevation shadow); Style-resolved floating role (Soft/Brutal/Paper looks; native has no Style resolver); composer well alpha while content scrolls under it
- `kit/kit_action_stack.dart` — forced stacking on every window regardless of destructive flag (native stacks only when compact or a destructive tertiary); tertiary text buttons start-aligned rather than full width
- `kit/kit_agent_card.dart` — agent glyph tile in the heading (one card shape shared with KitRequestCard); inList mode (no reading-width centring or page gutter under a list row)
- `kit/kit_agent_choice.dart` — agent choice card on the new-chat screen (persona mark, name, line, check, needs-setup look); sideways layout when spanning a row
- `kit/kit_agent_grid.dart` — two-column equal-height agent grid with spanning odd card and single column at large text
- `kit/kit_agent_grid_span.dart` — span signal for agent cards (needed with KitAgentGrid)
- `kit/kit_arrival.dart` — search-result arrival on a row (anchor ids, one-shot claim, scroll into view, focus, accent wash)
- `kit/kit_ask_field.dart` — ask field pill on the dev preview (mic when empty, Send with text, listening and working phases, note line)
- `kit/kit_ask_line.dart` — inline one-time ask line above the composer (inline and stacked states); always-allow invitation that uses it
- `kit/kit_bidi.dart` — no shared kit-level ltr/auto isolate helper and ltrText; isolation exists only in request words and Markdown inline code; already-isolated check to avoid double wrapping across screens
- `kit/kit_bottom_inset.dart` — shared clearance publisher/reader (KitClearance) for dock, pinned block, composer, keyboard and rail; KitJumpPill takes a manual bottomInset instead; physical-pixel rounding of published insets
- `kit/kit_buttons.dart` — copy actions (KitAction.copy / copyText with redaction and a brief 'Copied' state); keyboard shortcut hint in the label; calm, neutral and hero variants and expand flag; KitInset tertiary inset helper
- `kit/kit_capability_explainer.dart` — explainer variants: unavailable row, missing state, offer notice, folded, working
- `kit/kit_chart.dart` — line charts are drawn as bars; multiple series and a legend (native node holds one series); one zero-including scale with negatives below the zero line and axis labels; grow-in motion on first appearance
- `kit/kit_checklist.dart` — step checklist part (marks, progress, estimate/cost, Stop/Resume, failure row with Try again, folded log, slow escalation, compact form, live-region summary, done haptic)
- `kit/kit_chip.dart` — summary kind (work chip with expand chevron and expanded semantics); icon-only chip, leading mark/glyph slot, long-press, spoken override and live-region options
- `kit/kit_choice_cards.dart` — large choice cards with icon, check, chosen surface, disabled reason and busy state
- `kit/kit_choice_list.dart` — 'Something else' free answer (KitChoiceOther); receipt states: sending, answered, answered elsewhere; taps ignored while sending; keyboard model (one Tab stop, arrows, Home/End); row menu (More button, long-press, right-click)
- `kit/kit_choice_tiles.dart` — reason-led choice tiles with check, busy state and the 'All models' more line
- `kit/kit_code_block.dart` — header band with caption, file name, counts and 'Copy commands' label; Wrap toggle offered only while a line overflows, with accent on-state; edge fade while a line scrolls sideways
- `kit/kit_code_block_parts.dart` — sideways scroller with edge fade; Wrap toggle control
- `kit/kit_code_surface.dart` ⚠ — Style-resolved code-role surface (native draws a fixed surface; no Style resolver yet)
- `kit/kit_confirm_sheet.dart` — marked consequence items (lost / kept / info); safer alternative action on its own line; action run inside the question with working state and failure notice + Try again; replacing an open sheet's content in place instead of a new layer
- `kit/kit_consequences.dart` — lost / kept / info marks per fact (native draws a neutral dot only); surface panel with inset hairlines
- `kit/kit_context_chart.dart` — per-reply context usage bar chart with level colours, compact threshold line and compaction marks
- `kit/kit_context_ring.dart` — percent number beside the ring, counting with the arc; one halo when crossing 70 % or 95 % upward; compacting spin with 'Compacting' word and drain to new value; quiet list-row ring variant (number only from 70 %)
- `kit/kit_context_sheet.dart` — per-reply context chart; explainer paragraph on what the window is and the measured-at time
- `kit/kit_copy.dart` — no shared KitCopy: copies call the clipboard directly without redaction default, haptic or a single 'Copied' announcement (KitDetailsFold notes 'KitCopy (K1) replaces this')
- `kit/kit_date_time_picker.dart` — date / time / date-and-time picker sheet with kit calendar and typed entry; KitDateTimeRow (not set, set, error, disabled); schedule edit and agent card forms use typed text fields instead (TODO(kit) in ScheduleEditScreen)
- `kit/kit_details_fold.dart` — custom child slot inside the fold; standalone technical-details sheet with 'Copy all' (showKitTechnicalDetails) replacing raw-error dialogs; debug assert when a value matches the redactor
- `kit/kit_dialog.dart` — async onSubmit with working state and inline server error keeping the dialog open; alternative action (destructive stacks actions); draft that survives dismissal/process kill; discard-changes question on back/outside tap when edited; hint/helper lines and multi-line growing field (maxLines); validation silent until first edit or submit attempt; alert details fold and icon tile; alert ignores outside tap; compact-window bottom-sheet presentation with handle
- `kit/kit_diff_view.dart` — side-by-side mode from expanded width; file header, file switcher and "Change 1 of N" navigator across files; line selection with action bar (Comment, Add to prompt, Copy lines) and whole-hunk detection; expandable gap bars (reveal unchanged lines per tap); folded review mode (one file open at a time, viewed ticks); loading/empty/error/binary/renamed/too-big states; showKitDiff page route; wrap toggle
- `kit/kit_divider.dart` — no standalone KitDivider part (only KitRowGroup draws inset hairlines; KitMenu uses M3 HorizontalDivider); vertical divider; section rule variant (Paper style); endInset for rows reaching into the margin
- `kit/kit_error_fallback.dart` — KitErrorFallback part (safe-mode and migration-failure screens); works without theme/localization (English fallback); Back only under a navigator that can pop and when room allows
- `kit/kit_feed_item.dart` — no KitFeedItem kit part; ChatsScreen uses a plain KitRow with a status word; state tones (amber needs-you, accent working, green done, plain failed); agent name and brand mark in the meta line; relative time in the meta line; running line (command / task in progress) and Signal segment timer; request card under the row; one-sentence semantics; read-only (last-known) variant at same size
- `kit/kit_field.dart` — draft persistence across dismissal/process kill; "Still checking after 8 s" offers onSlow action; composer mode (unframed multiline); decimal flag for number kind; disabledReason in helper line and trailing actions; per-style field look/halo (KitFieldLook, kitFieldFrame); one-line kind growing to maxLines
- `kit/kit_fine_tune_panel.dart` — Reset to <style> defaults reveal; Fine-tune page in Appearance (only the stored prefs exist)
- `kit/kit_floating_action.dart` — KitFloatingAction part (56 dp accent button, label as tooltip/semantics); clears published bottom inset; list clearance; fades out when onPressed null without rebuilding the list; Signal ink-key variant with words
- `kit/kit_floating_panel.dart` — KitFloatingPanel part; rise-and-fade entrance above the keyboard; ground tap closes unless dismissal is blocked
- `kit/kit_frost.dart` — solid-to-frost fade over FROST_FADE when content scrolls under; "shown" gating (blur only while content is under it); noise layer over the tint; style hold (styleAllows hard-coded true); composer pane insets and wellOf zone for inner parts; not wired into the chat header or composer
- `kit/kit_frost_header.dart` — KitFrostHeaderHost / extendUp / topClearanceOf opt-in; bar painted over content and hit-tested first; solid while loading bar runs
- `kit/kit_group_note.dart` — optional tertiary action ("Why") at line end, wrapping under the words
- `kit/kit_guide.dart` — KitGuide part (chip rail, groups, question, next bar); guided first visit with suggested next chip and answered checks; question fade-rise motion; Appearance guide flow
- `kit/kit_handoff_record.dart` — KitHandoffRecord part; Show preview fold capped at previewLimit characters; omitted/hidden facts and save path
- `kit/kit_hero_question.dart` — KitHeroQuestion part; display size capped at 1.15x text scale; smaller size on narrow phones; Arabic zero tracking
- `kit/kit_icon.dart` — no public KitIcon; only kit-internal KitGlyph with a few stand-in glyphs; generated KitGlyphs set (Phosphor) and KitIconSize s/m/l; bundled brand marks (KitIcon.mark, KitBrandMark, AppBrandMark); duotone selected navigation glyphs (AppGlyph)
- `kit/kit_icon_button.dart` — copy variant (check for copiedHold, one Copied announcement, redaction); quarter-turn motion for the Settings gear; circle surface variant; quiet (text2) and destructive marks; tooltip on fine pointer; glyph sizes 20/22/24
- `kit/kit_image.dart` — KitImage loader (ImageRef source, decode-size, shape clip, loading and failure states); KitAvatar (initials, icon, error badge, optional logo); KitZoom canvas mode (work graph) and KitZoomController; keyboard zoom and pan shortcuts
- `kit/kit_image_gallery.dart` — KitImageGallery part (pager, lazy neighbour loading, per-picture loading/error); swipe only when not zoomed; arrow keys; Edit opens markup; Send hands edited bytes to composer
- `kit/kit_image_markup.dart` — KitImageMarkup part; four inks, Undo/Clear, crop handles; full-resolution export capped at 9 MiB; Done vs Send result
- `kit/kit_key_value.dart` — no standalone KitKeyValue part (inline in KitCardBody); tabular figures for values; stack value under label at large text / narrow window; prose values always stack; maxRows bound
- `kit/kit_last_known.dart` — KitLastKnown part (labelled group, "Updated ..." and "Refreshing"); rows inert: no tap, menu or running marks; replaced by the live list, even when empty
- `kit/kit_lead_column.dart` — KitLeadColumn scope (list vs compact rail) read by notices, steps and rows
- `kit/kit_level_meter.dart` — listening=false rest state (host must pass 0)
- `kit/kit_link_scope.dart` — link claim scope: local dev-server links open the in-app preview from a transcript tap
- `kit/kit_log_panel.dart` — follow newest line, jump pill counts new lines; poll only while visible; live/quiet/ended/failed words; redaction before display and copy; Copy all; Wrap toggle; line selection and copy
- `kit/kit_log_panel_tools.dart` — log search with match marking and Previous/Next; Pause follow toggle
- `kit/kit_menu.dart` — copy item (KitMenuItem.copy with redaction and Copied announcement); shortcut hint on fine pointer; description line under a label; keyboard navigation (Up/Down wrap, Home/End) and focus ring; opening at a point (right-click/long-press); disabled reason shown as second line
- `kit/kit_mini_table.dart` — no standalone KitMiniTable part (inline in KitCardBody); numeric columns end-aligned with tabular figures; maxRows/maxColumns caps and short-row padding; spoken summary "N columns, M rows"
- `kit/kit_motion.dart` — entrance/celebration/breath durations for illustrations (after the switch); logPoll interval; tab lens springs (lead/trail/drag/lift)
- `kit/kit_nav.dart` — KitNav frame choosing none/rail/sidebar by window (short-window rule) not wired into the shell; sidebar destination pane (list in sidebar) and pinned primary; update-available dot; lens spring motion; keyboard focus group (Tab to selected, Up/Down)
- `kit/kit_needs_you.dart` — mark (row leading mark) form; span ("Needs you - " / "N need you - ") form; standalone badge with 99+ cap and semantics; pointing row that opens the conversation at its request card; reasonWord for notification copy
- `kit/kit_notice.dart` — fade-and-rise when it appears or its tone changes; app-wide KitReportHook (report handler with redaction) instead of a caller-passed action
- `kit/kit_one_time_code.dart` — no KitOneTimeCode part; GitHub code shown as a plain KitRow title; large mono LTR face in field frame; Copy control with Copied announcement
- `kit/kit_optical.dart` ⚠ — optical end inset for trailing glyphs (chevron, more, copy, check)
- `kit/kit_option_card.dart` — KitOptionCard theme/glow/surface/plain variants; KitAccentSwatch / KitAccentRow; chosen ring + check; disabled reason; look variant (after the switch with live looks)
- `kit/kit_page_heading.dart` — line as switcher with chevron and 48 dp target (onLineTap, lineTapLabel); needs-you line in attention tone with forward chevron; namesRoute
- `kit/kit_panel.dart` — no KitPanel / KitSurface.panel content panel (surface1, 18 dp, icon+title header, optional whole-panel tap); tinted-tone retired looks (only team/FactsBook callers, after-switch)
- `kit/kit_paste_image_menu.dart` ⚠ — 'Paste image' item in the field's text-selection (long-press) menu, checked when the menu opens; native offers it only in the composer's Add sheet
- `kit/kit_plan_block.dart` — plan block (title + tier note + summary on one line that wraps at 1.3x, window rows under it, tap opens the plan); Usage plans section natively
- `kit/kit_progress_ring.dart` — progress ring (measured % floored / steps "2 of 6" / finished check), caption inside; headline live region announced once per step; step trail (done/current/waiting marks); halted neutral tone; arc never runs backwards; phone setup progress page that would host it
- `kit/kit_progress_row.dart` — progress row with title, determinate bar and value words; automatic near-limit (80-99%) and at-limit (100%) words and colour; stale "as of 10:42" line; segments form: stacked bar, max 4 named + folded "Other", two-column legend; bar tween motion and cross-fade of limit word; one merged semantics node
- `kit/kit_reach.dart` — sheet "rails" reach scope: flat row groups in a sheet body extend past the body padding to the sheet edges (and reset for nested parts)
- `kit/kit_receipt.dart` — act label / in-flight sendingLabel words; "at 10:42" transition time; Undo on a confirmed receipt within the undo window; automatic-action line (KitAutoLine: "{label} at {time} · Undo"); receipt tap opens where the write lives; row supporting-line span form
- `kit/kit_request_answers.dart` — choose-many answers (sheet multi-select with pinned Send); reply answer (external-task reply field with draft and Send); gate Go answer with look-first secondary (AI Team gates are after-switch); PC shortcuts A / D / 1-9
- `kit/kit_request_card.dart` — choice, gate and reply kinds; answered-elsewhere and expired collapsed rows; Inbox row form (asRow); target lines with change size; details sheet for permission/question; PC keyboard shortcuts once focused; one-answer-once guard across all kinds
- `kit/kit_request_question.dart` — question look: calm surface with an attention start edge; KitRequestTarget mono lines, left-to-right, with change size "+4 −1"
- `kit/kit_request_row.dart` — request as one Inbox/attention-feed row with place, wait age, command and trailing answers; Inbox screen to host it
- `kit/kit_request_shape.dart` — per-style needs-you loudness (calm start edge for Brutal/Paper, Signal printed slip); accent border while focused; entrance motion and in-list placement
- `kit/kit_request_sheet.dart` — permission sheet (full command block, change diff, Always-allow risk switch with scope/duration, reject reason note); question/choice sheets (single sends on tap, multi with Send); gate and reply sheets; closes itself when answered elsewhere / expired with an outcome; note kept as a draft
- `kit/kit_row.dart` — titleMaxLines, titleAccent, titleMark, titleDetail; lead variants: number, badge, check, change mark, bare icon, needs-you dot; unavailable form (capability) and enable action
- `kit/kit_row_group_seam.dart` ⚠ — framed-style group panels and the seam gap between abutting framed groups (native draws Solid flat lists only)
- `kit/kit_row_parts.dart` — KitRowIcon current mark (accent circle); "Connected · " / "Recommended · " accent spans; KitUntil durations for a risky switch and its status-line "Turn off" contribution while on; switch row onOpen form (row opens, switch toggles separately); style-drawn switch looks
- `kit/kit_row_value.dart` — count badge pill ("99+") with worded semantics; prominent value form
- `kit/kit_scanner.dart` — viewfinder dim outside the window and corner brackets not found natively
- `kit/kit_screen.dart` — jump pill slot floating over the body; bodySays / bodyQuiets status filtering; bottom inset published to the body (endPadding) for floating parts; side pane offered as a full sheet below large
- `kit/kit_search_field.dart` — labelled Filter menu at the end of the field; Ctrl/Cmd+F focus hook; page-search pill look (Settings hub); Arrow Down moves to the first result; calm form of KitSearchNoMatch
- `kit/kit_section_label.dart` — trailing part (drops under the words when it does not fit); explanation via KitTerm on the words; margin parameter for padded hosts; gapBefore rules (0 when first on page, collapsing caller spacers); Paper style rule above the words
- `kit/kit_segmented.dart` — segment counts ("Needs you 2"); icon-only compact pill with tooltips; fit-to-words widths
- `kit/kit_sense_ask.dart` — take/choose photo, choose file, record actions (native photo ask says the picker is not wired); picked items list with previews and remove; max reached / Send disabled reasons, sending state; file and voice asks
- `kit/kit_server_trust.dart` — trust sheet with origin and private-network note (native: a cleartext switch row in the server form); certificate fingerprint review: expanded LTR SHA-256, validity dates, changed-certificate replace
- `kit/kit_session_tabs.dart` — session pill strip (selected fill, ended look and label, long-press/right-click acts, horizontal scroll); multi-session terminal screen to host it
- `kit/kit_sheet.dart` — footer line pinned above actions; secondaryDismisses (one close control); virtualised itemBuilder body; Esc closes the top sheet
- `kit/kit_sheet_frame.dart` — header scrolls away with the body when it would squeeze the body at very large text; pinned block capped and cut (primary first) instead of overflowing
- `kit/kit_sheet_modal.dart` — style-specific modal surfaces (non-Solid looks); confirm asked in place of the sheet content (native shows a dialog above the sheet); Esc handling
- `kit/kit_sheet_parts.dart` — handle's spoken Dismiss action; swipe down on handle/header to close; attention tone icon tile; pinned bar form (text button at start, ellipsized primary at end)
- `kit/kit_since.dart` — general KitSince timer part with ticks modes (escalation, minutes, seconds, seconds-then-minutes); onSlow callback once per wait
- `kit/kit_skeleton_transcript.dart` ⚠ — transcript-shaped skeleton (native chat shows KitSkeletonRows instead)
- `kit/kit_sliver_row_group.dart` — lazy row-group panel for long lists (head rows + lazy items, inset hairlines, label, empty draws nothing)
- `kit/kit_start_box.dart` — start box field with min lines and growth; still accent halo brightening on focus (style-dependent); read-only while the conversation starts; new-conversation screen that uses it
- `kit/kit_state_view.dart` — inline size (KitStateSize.inline); illustration slot; content and footer slots; missing-capability cost notice; arrival fade-and-rise motion
- `kit/kit_status_answer.dart` — big answer part with tone marks (ok/rest/working/needs-you/off) and three scales; live countdown line; cross-fade with rise on a new answer
- `kit/kit_status_line.dart` — quiet caption form; More menu for further actions; onOpen on quiet words; appear/change fade-and-rise motion
- `kit/kit_status_mark.dart` — general leading state mark part (waiting ring, working spinner/still dot, done check, failed glyph, paused); word in semantics and optional visible label; cross-fade on change and contrast floor for the waiting ring
- `kit/kit_status_tag.dart` — worded status tag in four tones (needs-you, running, done, plain failed)
- `kit/kit_steer_box.dart` — KitSteerBox phase looks (dictating/sending/sent) and blocked reason
- `kit/kit_step_slider.dart` — KitStepSlider part and its keyboard/a11y stepping
- `kit/kit_surface.dart` — KitSurface/KitPanel part itself
- `kit/kit_surface_look.dart` — field role 3:1 boundary contrast floor; technical role always opaque
- `kit/kit_swatch.dart` — KitThemePreview
- `kit/kit_swipe_action.dart` — deferred commit timing (on route pop / new undo)
- `kit/kit_tappable.dart` — tooltip with shortcut hint; accent focus ring of token width; press shown on next frame outside scrollables
- `kit/kit_task_chip.dart` — KitTaskChip part
- `kit/kit_task_mark.dart` — KitTaskMark part
- `kit/kit_task_sheet.dart` — task list sheet with still ring when agent idle
- `kit/kit_term.dart` — KitTerm part
- `kit/kit_terminal_links.dart` — tapped-link sheet (Open link / Copy)
- `kit/kit_terminal_view.dart` — tail-first output with all/too-long/empty states; command highlighting of program/flags/strings
- `kit/kit_text.dart` — KitSelectable / KitSelectMode; tabular figures option
- `kit/kit_tip.dart` — KitTour cutout scrim and gliding highlight
- `kit/kit_top_bar.dart` — KitTopBarScope/Link/Branch/Origin; KitShellControls; server pill; subtitle needs-you count and tail
- `kit/kit_top_bar_centred.dart` — KitTopBar.centred layout
- `kit/kit_top_bar_pill.dart` — server pill with host-first ellipsis
- `kit/kit_top_bar_scope.dart` — scope line, origin and link
- `kit/kit_top_bar_tail.dart` ⚠ — subtitle needs-you span and tail
- `kit/kit_typing_guard.dart` ⚠ — KitTypingGuard
- `kit/kit_undo.dart` — deferred commit/commitPending; Try again on undo failure
- `kit/kit_update_dot.dart` ⚠ — KitUpdateDot drawn on gear and rail
- `kit/kit_vector_logo.dart` — KitVectorLogo
- `kit/kit_viewer.dart` — find in file; Wrap toggle
- `kit/kit_viewer_bodies.dart` — Markdown prose body; delimited table body
- `kit/kit_viewer_build.dart` — Wrap toggle, find, svg body
- `kit/kit_viewer_content.dart` — svg and delimited kinds
- `kit/kit_vitals_strip.dart` — KitVitalsStrip
- `kit/kit_wheel.dart` — KitWheel
- `kit/kit_widget_preview.dart` — KitWidgetPreview
- `kit/kit_window_row.dart` — KitWindowRow
- `kit/kit_word_text.dart` — KitWordText

Total uncatalogued gaps: **465**.

## Untested files

`early_l10n.dart` (not-needed), `kit/chat/kit_markdown_table.dart` (partial), `kit/chat/kit_project_file_sheet.dart` (missing), `kit/kit_code_surface.dart` (partial), `kit/kit_optical.dart` (missing), `kit/kit_paste_image_menu.dart` (partial), `kit/kit_row_group_seam.dart` (partial), `kit/kit_skeleton_transcript.dart` (missing), `kit/kit_system_bars.dart` (done), `kit/kit_top_bar_tail.dart` (missing), `kit/kit_typing_guard.dart` (missing), `kit/kit_update_dot.dart` (partial), `kit/kit_update_sheet.dart` (done), `kit/live_background/live_mock_chat.dart` (after-switch), `kit/live_background/styles/kit_live_aurora.dart` (after-switch), `kit/live_background/styles/kit_live_blueprint.dart` (after-switch), `kit/live_background/styles/kit_live_bokeh.dart` (after-switch), `kit/live_background/styles/kit_live_brushed_metal.dart` (after-switch), `kit/live_background/styles/kit_live_caustics.dart` (after-switch), `kit/live_background/styles/kit_live_circuit.dart` (after-switch), `kit/live_background/styles/kit_live_constellation.dart` (after-switch), `kit/live_background/styles/kit_live_dot_ripple.dart` (after-switch), `kit/live_background/styles/kit_live_flow_threads.dart` (after-switch), `kit/live_background/styles/kit_live_glow_spill.dart` (after-switch), `kit/live_background/styles/kit_live_halftone.dart` (after-switch), `kit/live_background/styles/kit_live_honeycomb.dart` (after-switch), `kit/live_background/styles/kit_live_light_rays.dart` (after-switch)

## Method

1. Pre-pass (script): line counts; Flutter tests that import the file by package path, are named after it, or reference its public types; catalog rows whose `sources` cite those tests (direct import / name match only, to avoid barrel-import noise via `kit.dart`).
2. Each file was read and its native counterpart searched (Dart file names cited in Kotlin comments, same-named kit parts, strings, domain words) across `native/ui/*`, `native/feature/*`, `native/engine/*`, `native/core/*`, `native/android/*`. Every native path in the record exists (checked).
3. Strict status: `done` only with named native files covering every user-visible behaviour; otherwise `partial` with gaps. `untested` is a judgement: `part` files exercised through their library tests are counted as tested.
4. Limits: native parts that exist but are not yet wired into a screen (e.g. `KitFrost`, `KitNavRail`, `KitSidebar`, several composer parts) are `partial` with "not wired" in gaps. `gaps_uncatalogued` was matched against the catalog by reading, not by a mechanical check.
