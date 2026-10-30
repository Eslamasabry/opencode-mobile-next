# Style coherence audit (2026-10-21)

*Auditor only: no app code changed. Base: int `787709bfd` (Glass tuned through its spec, H12 merged). Brutal and Paper are still the baseline placeholders, and they are audited as they are. Branch `audit/style-coherence`; harness under `tool/capture/audit/` (audit tooling, not for merging). Renders: `/home/eslam/Storage2/launch-footage/audit-styles/<screen>/<combo>.png`, one full phone screen (412×915 dp at 2×) per image.*

## 1. Verdict

The resolver is real, and where a part calls it, every style shows up. Screens are clean: outside `lib/ui/kit/` nothing paints a surface. The only leak outside the kit is `lib/ui/app_theme.dart`, which sets the Material defaults for the framed sheets, tooltips and the overflow menu.

The kit itself, though, applies the style piecemeal. Of the 82 kit parts that paint a non-exempt surface:

| How the part takes the style | Parts | Share |
|---|---|---|
| Through `KitSurfaceLook` / `kitSurfaceBox` / `kitSurfaceMaterial` | 25 | 30 % |
| Partly: the shape follows Corners (H4/H5 tokens), but the fill, edge and depth are hand-painted | 22 | 27 % |
| Not at all: a raw `BoxDecoration` / `ShapeDecoration` / `Material` / `ColoredBox` from fixed roles | 35 | 43 % |

About 20 more are correctly exempt (§2).

Per style, the share of parts that look right differs:
- **Solid:** 100 %, because at its defaults everything is Solid.
- **Glass and Soft:** about 30 %. Their identity is fill and depth, so the "partly" parts read as Solid.
- **Paper:** about 57 %. Square corners reach the "partly" parts through the tokens, so at a glance Paper looks the most applied.
- **Brutal:** about 30 %, and less on the screens hosted by `HomeScreen`. A hook bug (H9 below) strips every hard shadow from Conversations, so Brutal's most visible screen loses its main cue.

The style is not done "through the kit" yet. It is done through less than a third of the kit.

## 2. Part table (static audit, working backwards from the code)

Legend:
- **a** honours the style
- **b** partly
- **c** ignores it
- **d** correctly exempt

"Fails" lists the styles where the gap is visible: G Glass, S Soft, B Brutal, P Paper. Lines are at `787709bfd`.

### Through the resolver (a)

| Part | File:line | Class | Fails / note |
|---|---|---|---|
| KitSurface panel / inset / tile | kit_surface.dart:245 (`kitSurfaceMaterial`) | a* | *Solid + Fine-tune is skipped (`solid` early-out, :258), see R4 |
| KitPanel | kit_panel.dart:79,162 | a | |
| KitRowGroup | kit_row.dart:1182 | a | Groups with `gapBefore: 0` abut, giving doubled rims/edges and overlapping shadows (composer tools sheet) |
| KitSliverRowGroup | kit_sliver_row_group.dart:145 | a | B on HomeScreen: shadow stripped (R2) |
| KitRowIcon tile | kit_row.dart:849 | a | |
| KitSegmented track and indicator | kit_segmented.dart:553,640,661 | a | `selected: style == glass` (:650), style named in a part (R5) |
| KitMenu panel | kit_menu.dart:516 | a | Divider stays a hairline (:464): B/P want ink |
| KitButton primary / secondary | kit_buttons.dart:476–506 | a* | *Disabled and tertiary stay Solid (:478); `style != solid` early-out (R4) |
| KitChip | kit_chip.dart:807 | a | |
| KitDialog panel | kit_dialog.dart:284 | a | |
| KitSearchField | kit_search_field.dart:451 | a/b | Prominent radius is `buttonHeight / 2` (:443), so P keeps a stadium on the Settings search |
| KitGlass (top-bar pills, dock, rail, composer shell) | glass/kit_glass.dart:88 | a | |
| KitSheet modal (showKitSheet) | kit_sheet_modal.dart:154 | a | |
| KitJumpPill | kit_jump_pill.dart:392 | a | |
| KitNotice.card | kit_notice.dart:629 | a | Tile tint at :655 is raw (minor) |
| Prompt bubble (KitMessage) | chat/kit_message.dart:505 | a | `style == soft` branch at :522 (R5) |
| KitRequestCard frame and tile | kit_request_shape.dart:72–150, :219 | a | Attention edge exempt (d) |
| KitSessionTabs | kit_session_tabs.dart:80,166 | a | `selected: style == glass` (:195) |
| KitChoiceList panel | kit_choice_list.dart:416 | a | In a request card this paints a second surface, so the card nests in G/S/B/P (finding V3) |
| KitFloatingPanel | kit_floating_panel.dart:97 | a | Solid branch at :85 is a raw `Material` |
| KitFactRow | kit_fact_row.dart:196 | a | |
| KitProgressRow | kit_progress_row.dart:312 | a | |
| KitNav lens (dock selection) | kit_nav.dart:879 | a | |
| Composer glow | chat/kit_composer_edge.dart:155, kit_composer_classic.dart:74 | a | Driven by H8 |
| Brutal / Paper sample in the Style picker | kit_option_card.dart:574 | a | |

### Partly (b): radius follows Corners, but fill, edge and depth do not

| Part | File:line | Fails |
|---|---|---|
| KitField (dialogs, forms, Report a problem) | kit_field.dart:884 | G S B P: no tint, no Soft well, no 2 dp ink, no ink box. The multi-line form has **no edge at rest**: fill against ground is ~1.06:1 in light, in every style |
| KitAskField ("Ask for a change…", Preview) | kit_ask_field.dart:182 | G S B P |
| KitStartBox (New conversation) | kit_start_box.dart:91–150 | Two-way `solid` / not-solid branch; the accent halo is always on, so P casts a glow, which "print" forbids |
| KitChoiceCards (Welcome) | kit_choice_cards.dart:237–290 | `glassOrSoft` branch also catches B/P; the chosen ring is hand-drawn |
| Composer suggestion chips and panel | chat/kit_composer_chips.dart:280,1018,1068 | G S B P |
| KitAgentStrip chip | chat/kit_agent_strip.dart:240 | G S B P |
| KitTabSwitcher fill | motion/kit_tab_switcher.dart:339 | G S B P (only the words use `wordsOn`) |
| Queued-message bubble | chat/kit_queued_message.dart:249 | G S B P. It should match the prompt bubble, which honours the style |
| KitTerm bubble | kit_term.dart:324 | G S B P |
| KitTip bubble | kit_tip.dart:240 | G S B P |
| Viewer sheet (showKitViewer, 6 callers) | kit_viewer.dart:115 | G (no blur), S, B (no ink), P |
| showKitFramedSheet (Git, timeline, commands, agent, folder browser ×3, team editor) | kit_sheet.dart:349 → app_theme.dart:353 | G S B P: Material theme sheet |
| Code block | kit_code_block.dart:620,653 | Opaque fill is right (d); the S well, B ink edge and P rule are missing |
| Details fold body | kit_details_fold.dart:379 | Same as the code block |
| Log panel | kit_log_panel.dart:800 | Same as the code block |
| Command box in request card and question | kit_request_card.dart:609, kit_request_question.dart:416 | P/S: fill = ground, so the box vanishes in Inbox |
| Terminal output block | kit_terminal_view.dart:555 | Same as the code block |
| Diff file list | diff/kit_diff_chrome.dart:122 | G S B P |
| Search field (prominent) | kit_search_field.dart:443 | P radius (see above) |
| Popover tooltips | app_theme.dart:489 (literal 8) | G S B P |
| Overflow "More" menu | kit_buttons.dart:1080 (`PopupMenuButton`) | G S B P: Material popup, not KitMenu |
| Step slider | kit_step_slider.dart:169–226 | B/P track and thumb (minor) |

### Ignores the style (c): raw decoration from fixed roles

| Part | File:line | Fails |
|---|---|---|
| **KitAgentChoice cards (New conversation)** | kit_agent_choice.dart:204 | G S B P: identical in every style |
| **KitChoiceTiles (Model picker)** | kit_choice_tiles.dart:182 | G S B P: opaque tiles on a glass sheet |
| **KitUndo bar** | kit_undo.dart:659 | G S B P: a floating piece with no blur, raise, ink or square corners |
| **KitFloatingAction (+ on Conversations)** | kit_floating_action.dart:63 | S B P: a round accent disc in Paper, with no ink or hard shadow in Brutal |
| KitConsequences inset | kit_consequences.dart:101 | G S B P. It duplicates `KitSurface.inset`, which honours the style |
| Sheet header icon tile | kit_sheet_parts.dart:71 | G S B P (`KitSurface.tile` honours) |
| Dialog icon tile | kit_dialog.dart:821 | Same |
| Empty-state icon tile | kit_state_view.dart:728 | Same |
| Step-light tile | chat/kit_step_light.dart:151 | Same |
| Composer tray tool circles | chat/kit_composer_tray.dart:109 | G S B P |
| Composer mic / send circle | chat/kit_composer_caption.dart:188 | S B P (mint main action stays: fine) |
| KitStatusTag (Servers) | kit_status_tag.dart:61 | B P |
| Row selected fill | kit_row.dart:580 | S (should press in), B, P (should invert) |
| Row count badge | kit_row.dart:1069 | B P |
| Swipe background | kit_row.dart:930 | minor |
| KitDivider | kit_divider.dart:62 | S (none), B (2 dp ink), P (1 dp ink), per contract F8 |
| KitTappable hover / pressed fill | kit_tappable.dart:559 | S (inset), B (sink, H2), G (brighter): every list row's press |
| Switch (Switch.adaptive) | kit_row_parts.dart:479,509 | S B P: Material switch everywhere |
| Desktop sidebar pane and selected row | kit_nav.dart:290,1080 | G S B P |
| Icon-button pressed fill and tooltip | kit_icon_button.dart:335,442 | minor |
| Terminal key bar and caps | terminal_key_bar.dart:379,736,768 | G S B P |
| Terminal view key band and pill | kit_terminal_view.dart:382,873 | G S B P |
| Date / time wheel band | kit_wheel.dart:108 | B P |
| Board lane cards | kit_board_lane.dart:632,677 | G S B P |
| Team cards (AI Team) | team/team_parts.dart:208 | G S B P. Its amber blend fill also breaks Soft's "no amber fill" (contract F2) |
| Mini table header | kit_mini_table.dart:161 | minor |
| Markdown table and blockquote | chat/kit_markdown.dart:738,770,959 | B P |
| Diff chrome: header band, file header, selection bar | diff/kit_diff_chrome.dart:25,485,695 | G S B P |
| Diff rows: hunk bar | diff/kit_diff_rows.dart:600 | G S B P |
| Skeleton transcript | kit_skeleton_transcript.dart:37,59 | minor |
| Image frames and placeholder | kit_image.dart:305,1365 | minor |
| Carousel dots | kit_carousel.dart:125–157 | minor |
| Window-row bars | kit_window_row.dart:90 | minor |
| Style picker samples for Solid / Glass / Soft | kit_option_card.dart:488–570 | Hand-painted, so Glass and Soft tuning never reaches the picker (F10) |
| Live preview mock reply bubble | live_background/live_mock_chat.dart | F10: Appearance owns it |

### Correctly exempt (d)

| Part | File:line | Why |
|---|---|---|
| Code, diff, log and terminal **fills** | above | Code never sits on translucency (contract F7) |
| Attention edge | kit_request_shape.dart:121 | Amber "needs you" |
| Needs-you dot and badge | kit_needs_you.dart:363, kit_row.dart:805, kit_request_question.dart:225, kit_request_row.dart:185 | Amber "needs you" |
| KitQr paper | kit_qr.dart:130 | Must scan |
| Scanner ground | kit_scanner.dart:287 | Camera |
| Status dots | chat/kit_turn.dart:841, chat/kit_step_node.dart:63, kit_top_bar.dart:1239, kit_status_answer.dart:399 | Meaning colour |
| Progress bars and rings | kit_progress.dart:65, kit_progress_ring.dart:378 | Indicators |
| Chart keys | kit_chart.dart:238 | Data |
| Image-markup colours | kit_image_markup.dart:717 | Content |
| Sheet handles | kit_sheet_parts.dart:22, kit_viewer.dart:169 | |
| Page transition wash | motion/kit_page_transitions.dart:222 | |
| Arrival wash | kit_arrival.dart:147 | |
| Live background ground | kit_live_background.dart:260 | |
| Theme swatches | kit_swatch.dart | A theme preview |
| Scenes and illustrations | scenes/*, kit_illustration.dart | Style-agnostic by contract |
| Focus rings | many | |
| Date-picker day circles | kit_date_time_picker.dart:1014 | |

### Outside the kit

- **Screens and widgets: clean.** 0 direct paint widgets and 0 raw fill, radius or edge arguments. About 20 sites pick a `KitSurfaceLevel` or `KitShape` role, which the kit resolves.
- **`lib/ui/app_theme.dart` is the one leak:**
  - `cardTheme` :333
  - `dialogTheme` :342
  - `bottomSheetTheme` :353 (reached by `showKitFramedSheet`)
  - `inputDecorationTheme` :367
  - Stadium buttons and chips :438, :462
  - `tooltipTheme` :489 (literal 8)
  - `radiusControl` :275, :426, :447, :549 (not a token)
- **G16 / G17 / G21 baselines:** empty outside the kit. Two stale G21 glass allowlist rows remain (`home_screen.dart`, the missing `widgets/glass_surface.dart`; `kit_ratchet_test.dart:706-717`).

## 3. Per-screen findings (visual audit)

**Matrix rendered:**
- Every screen in Solid dark, Glass dark (live background on), Soft light, Brutal light and Paper light.
- All 10 combinations for chat with a question, the Settings hub, Conversations and the composer tools sheet.
- Saver (Glass chosen), high contrast (Glass and Brutal), Arabic RTL (Solid dark, Brutal light), Fine-tune Sharp + Compact + Flat + edges off (Solid dark, Soft light).
- 34 screens, 220 images.

**Checks that pass:**
- Saver forces Solid on a true-black ground with no live background.
- High contrast turns Glass to Solid and keeps Brutal flat with its ink.
- RTL mirrors Brutal's hard shadow.
- Fine-tune reaches corners, density and edges (Soft flat gains its edge).
- No red anywhere; amber only on needs-you; one mint main action per screen.

**Severity:** blocks the look / visible / minor.

| # | Screen | Style | What the user sees | Part | Severity |
|---|---|---|---|---|---|
| V1 | Conversations (every page hosted on `KitSurface(level: ground)`) | B | Row groups, chips and server pill lose every hard shadow; Scheduled and Settings keep theirs | H9 cast scope + page ground `KitSurface(level: ground)` home_screen.dart:490 (also managed_move_view.dart:67, widgets/markdown.dart:285) → kit_surface_look.dart:853–866 | blocks |
| V2 | New conversation | G S B P | The four agent cards are the same Solid rounded cards in every style, while the start box and Model/Project group change | kit_agent_choice.dart:204 | blocks |
| V3 | Chat with a question, AI Team question | G S B P | Answers sit in a second pane inside the card: a glass pane in a glass card, raised in raised, ink box in ink box. Solid shows none | kit_choice_list.dart:416 inside kit_request_shape | blocks |
| V4 | Git sheet; file reader; timeline / commands / folder-browser sheets | G S B P | Sheet is an opaque white or dark slab with big round corners: no blur in Glass, no ink in Brutal, not square in Paper. Prompt tools and Servers sheets do style, so sheets differ from each other | kit_sheet.dart:349, kit_viewer.dart:115 | blocks |
| V5 | Model picker | G S B P | Glass sheet with opaque hairline tiles; Brutal tiles rounded with no ink; Soft tiles flat | kit_choice_tiles.dart:182 | visible |
| V6 | Undo snackbar | G S B P | Same grey rounded bar in all styles | kit_undo.dart:659 | visible |
| V7 | Rename dialog, Report a problem, forms | G S B P | Field stays Solid: no ink box, no well, no tint. The Report field has no visible edge (~1.06:1) | kit_field.dart:884 | visible (a11y: fails WCAG 1.4.11 in every style) |
| V8 | Inbox, permission card | P S | Command box disappears (fill = ground); B shows a plain grey pill | kit_request_card.dart:609 | visible |
| V9 | Confirm sheet | G S B P | Icon tile and consequences box stay Solid grey; S box invisible | kit_sheet_parts.dart:71, kit_consequences.dart:101 | visible |
| V10 | Conversations | S B P | `+` stays a round accent disc: no ink or shadow in Brutal, round in Paper | kit_floating_action.dart:63 | visible |
| V11 | Prompt tools sheet | G B P | Two row groups touch: doubled rim (G), doubled 2 dp ink and overlapping hard shadow (B), doubled rule (P) | kit_row.dart:1182 with `gapBefore: 0` from the sheet | visible |
| V12 | Every row with a toggle (sheet, Scheduled) | S B P | Material switch, unchanged | kit_row_parts.dart:479 | visible |
| V13 | Review changes (diff) | G S B P | Header band, "…lines above" white strip, hunk bars and file headers are identical in every style | diff/kit_diff_chrome.dart:25,485; diff/kit_diff_rows.dart:600 | visible |
| V14 | Terminal | G S B P | Key bar caps identical | terminal_key_bar.dart:736,768 | visible |
| V15 | Preview | G S B P | "Ask for a change…" field keeps Solid's green hairline pill | kit_ask_field.dart:182 | visible |
| V16 | Settings hub | P | Search field is a stadium while everything else is square | kit_search_field.dart:443 | minor |
| V17 | New conversation | P | Start box glows (accent halo) on print | kit_start_box.dart:101 | minor |
| V18 | Composer (all chats) | S B P | Mic / send circle and tray circles are plain discs (no raise, ink or square) | chat/kit_composer_caption.dart:188, chat/kit_composer_tray.dart:109 | minor |
| V19 | Appearance | B P | Theme swatch cards round, mock reply bubble round and white | kit_swatch.dart, live_mock_chat.dart (F10) | minor |
| V20 | Welcome, Tips, dialogs | B P | Tertiary links ("Just show me", "Show me", "Cancel") have no ink underline (contract F5) | kit_buttons.dart (tertiary) | minor |
| V21 | Server switcher over Conversations | S | Needs-you card has an amber-tinted face (contract F2: no amber fill in Soft) | kit_request_shape.dart:76 via the spec | minor |
| V22 | Conversations, Brutal / Paper | B P | Live background stays on when the style is set without the picker: Paper's `offWhenChosen` only runs in `KitStyles.choose` | style/kit_style.dart:255 | minor (by design; noted) |

Screens that took the style well: Settings hub (except V16), Scheduled, Tips, This phone, Report a problem card (not its field), Servers sheet, Welcome cards, Inbox list (except V8), chat prompt bubble, request card and composer shell.

## 4. Coherence analysis

**Within each style:**
- **Glass.** Panes and sheets blur, but four floating or modal pieces stay opaque: the Undo bar, framed sheets, the viewer sheet and the model tiles. That is the mix of glass panes beside opaque rows the contract forbids. Nested pane in the question card (V3). Opaque code inside glass is correct.
- **Soft.** Raised groups beside flat Solid cards: agent cards, choice tiles, team cards, board lanes. Material switches. Nested raised panel in a raised card (V3). Wells missing for fields, code and command boxes. In Soft, a ground-coloured fill makes those boxes invisible instead of inset (V8, V9).
- **Brutal.**
  - The edge language holds where the resolver runs (2 dp ink, Soft corners).
  - The shadow language is broken by V1: no shadows on the home screen, shadows on Scheduled.
  - Boxes in boxes (V3, V8 outline) and doubled ink on abutting groups (V11).
  - Tertiary links have no underline.
  - FAB, switches, fields and tiles stay Solid.
- **Paper.**
  - The shape language holds best (square through tokens).
  - Edges are inconsistent: content has a light rule (α 0.28), controls an ink box, and the hand-painted parts a hairline or nothing. The command box and Soft-style wells vanish.
  - Round leftovers: FAB, prominent search, swatches, mic button.
  - The start box halo breaks "no shadows".

**Parts that should look alike and do not:**
- Prompt bubble (styles) vs queued bubble (does not).
- `KitSurface.inset` (styles) vs `KitConsequences` (does not).
- `KitSurface.tile` and `KitRowIcon` (style) vs the sheet, dialog, empty-state and step-light tiles (do not).
- `showKitSheet` sheets (style) vs `showKitFramedSheet` and viewer sheets (do not).
- KitMenu (styles) vs the `PopupMenuButton` overflow (does not).
- Chat chips (style) vs composer suggestion and agent-strip chips (do not).

**Owner rules:**
- No red found.
- Amber is only used for needs-you, but the team card blends amber into its fill and the Soft needs-you face is tinted.
- One solid-mint main action holds on every screen checked.
- No glass outside Glass.

## 5. Root causes

- **R1. Parts paint their own decoration (35 parts).** They use a raw `BoxDecoration` / `ShapeDecoration` / `Material` from `roles.surfaceN`, `tokens.*Surface` or `roles.accent`, and never ask `KitSurfaceLook`. The biggest visible ones are agent choice, choice tiles, undo, FAB, field, the icon tiles, consequences, switch, diff chrome, the key bar and team cards.
- **R2. A hook misfires: H9 treats a page ground as a casting surface.** `KitSurface(level: ground, shape: square)` hosts whole pages (home_screen.dart:490, managed_move_view.dart:67, markdown.dart:285). Under Brutal it resolves as a content surface with a 4 dp hard shadow, so `kitSurfaceBox` (kit_surface_look.dart:853–866) marks the whole page as "inside a surface that casts" and strips every shadow below it. The page ground also gets a content edge and shadow of its own (Paper rule, Soft pair), which only the screen edge hides.
- **R3. Modal paths bypass the resolver.** These modals use the Material theme or hand-set colours instead of the kit's styled modal surface:
  - `showKitFramedSheet` (Material `bottomSheetTheme`)
  - `showKitViewer` (hand-set `sheetSurface`)
  - `PopupMenuButton` overflow, tooltips (`tooltipTheme`)
  - KitTip and KitTerm bubbles
- **R4. Two-way "solid or not" branches from the Surfaces era.** `style == solid` early-outs (kit_surface.dart:258, kit_buttons.dart:478, kit_dialog.dart:226/286, kit_sheet_modal.dart:20, kit_floating_panel.dart:40, kit_start_box.dart:92, kit_choice_cards.dart:237, kit_fact_row.dart:190, kit_menu.dart:509, kit_jump_pill.dart:412, kit_search_field.dart:478, kit_request_shape.dart:82, kit_nav.dart:886):
  - They skip `decorate` under Solid, so Solid + Fine-tune Depth › Raised or Edges › Off never reaches these parts. Static finding; the rendered Fine-tune used Flat.
  - They treat Brutal and Paper as "Glass or Soft" (the start box halo, the choice card ring).
  - `decorate` already returns `solid` unchanged at Solid's defaults, so the early-out is unnecessary.
- **R5. Style names inside parts.** Examples: `selected: style == glass` (kit_segmented.dart:650, kit_session_tabs.dart:195), `style == soft` (chat/kit_message.dart:522), and the per-style sample switch (kit_option_card.dart:488). These belong in the specs (`onColor`, `resolve`).
- **R6. Resolver gaps for roles.** The five roles have no answer for:
  - **page ground** (needs to resolve to "no edge, no cast", R2)
  - **field / well** (the contract's F6 "boundary ≥ 3:1 whatever Edges says"; today fields borrow `control`)
  - **code / technical** (opaque fill but a style edge: S well, B ink, P rule)
  - **divider** (S none, B 2 dp ink, P 1 dp ink)
  - **state layer** (KitTappable pressed / hover)
  - **toggle** (switch track and thumb)

  Hence every part improvises.
- **R7. Nesting has no rule.** H9 removes nested shadows (Brutal only). Nothing removes a nested *surface*, so a panel inside a card paints a second face in every non-Solid style (V3, V8, V11).
- **R8. Radii computed from heights** (`buttonHeight / 2`, `side / 4`, `buttonRadius * .72`) and literals (`app_theme.dart:489` 8, `radiusControl`) escape Corners.

## 6. Cleanup plan (works backwards; slices by kit-file ownership)

**Invariants for every slice:**
- **Solid stays pixel-identical.** Only `test/goldens/kit/kit_surfaces_golden_test.dart` and `kit_option_card_golden_test.dart` render non-Solid styles today, so every other golden must not move. Each slice:
  - adds its parts to `kit_surfaces_golden_test.dart` (five styles × light/dark);
  - re-runs `test/kit/kit_style_test.dart` and `test/kit/kit_surface_look_test.dart`;
  - re-runs this harness for the screens it names (`tool/capture/audit/run_style_audit.sh <combo> all`).
- **Order:**
  1. S0 first (shared files).
  2. S1–S7 in parallel; no two share a file.
  3. S8 last.

| Slice | Files (sole owner) | Parts | Expected visible change | Tests / goldens that move | Size |
|---|---|---|---|---|---|
| **S0 Resolver roles and hook fixes (shared, first)** | kit_surface_look.dart, style/kit_style*.dart, kit_surface.dart, kit_tokens.dart | (1) A `KitSurfaceLevel.ground` / page-ground path in `kitSurfaceMaterial` that resolves to "fill only, never casts" (fixes R2/V1). (2) New roles `field`, `code`, `divider`, `stateLayer`, `toggle` (R6), with each spec's answer (placeholders for B/P). (3) A nested-surface rule: a surface inside a surface takes the `face` only, no second fill or edge, in all non-Solid styles (R7). (4) Replace the `style == solid` early-out in `kitSurfaceMaterial` with "Solid at its defaults" (R4) | B: Conversations gets its hard shadows back. G/S/B/P: answers in question cards stop nesting. Nothing in Solid | kit_style_test (new role and nesting invariants), kit_surface_look_test, kit_surfaces_golden_test (G/S/B/P only) | M (~400 lines) |
| **S1 Modal paths** | kit_sheet.dart, kit_viewer.dart, kit_term.dart, kit_tip.dart, kit_undo.dart, kit_buttons.dart (overflow only; coordinate with S3) | `showKitFramedSheet` and `showKitViewer` through `kitSurfaceBox` with `role: sheet`; KitTerm and KitTip bubbles `role: floating`; KitUndo `role: floating`; overflow → KitMenu | G: Git, reader, timeline and command sheets and Undo blur. B: ink sheet and Undo. P: square sheets. S: ground-matched | kit_sheet, kit_viewer, kit_undo, kit_tip goldens (Solid unchanged); new style rows | M |
| **S2 Choice cards family** | kit_agent_choice.dart, kit_choice_tiles.dart, kit_choice_cards.dart, kit_start_box.dart | Through `decorate` with `selected`; drop the `glassOrSoft` / `solid` branches; halo through `tokens.glow.halo` (H8) | New conversation, Model picker and Welcome take every style; P start box stops glowing | kit_agent_choice, kit_choice_tiles, kit_choice_cards, kit_start_box goldens (non-Solid rows only) | M |
| **S3 Controls** | kit_row_parts.dart (switch), kit_floating_action.dart, kit_buttons.dart (tertiary underline), kit_status_tag.dart, kit_icon_button.dart, chat/kit_composer_caption.dart, chat/kit_composer_tray.dart | Switch on `toggle`; FAB `floating`; tertiary underline via spec; status tag `control`; mic / send / tray circles `control` (accent kept) | S: raised switch, FAB and tray. B: ink FAB with hard shadow, ink switch. P: square FAB and ink switch, underlined links | kit_row, kit_buttons, kit_floating_action, composer goldens (non-Solid) | M |
| **S4 Fields** | kit_field.dart, kit_ask_field.dart, kit_search_field.dart, chat/kit_composer_chips.dart | `role: field` (S well, B 2 dp ink, P ink box, G tint and rim), boundary ≥ 3:1 at rest; prominent search radius from `tokens.pillRadius`; suggestion chips and panel through `decorate` | Every form and dialog field takes the style; the Report field gains a visible edge; P search square | kit_field, kit_search_field goldens. **Note:** the at-rest edge also changes Solid multi-line fields; that is an a11y fix, so the owner decides whether Solid may move there | M |
| **S5 Tiles and insets** | kit_sheet_parts.dart, kit_dialog.dart (tile only; coordinate with S1 if it touches the dialog), kit_state_view.dart, chat/kit_step_light.dart, kit_consequences.dart, kit_notice.dart (tile) | Replace hand tiles with `KitSurface.tile` and the consequences box with `KitSurface.inset` | Confirm sheet, dialogs, empty states and step lights match the row icons in every style | kit_confirm_sheet, kit_state_view, kit_notice goldens (non-Solid) | S |
| **S6 Transcript and technical surfaces** | kit_code_block.dart, kit_details_fold.dart, kit_log_panel.dart, kit_request_card.dart, kit_request_question.dart, kit_terminal_view.dart, terminal_key_bar.dart, chat/kit_queued_message.dart, chat/kit_agent_strip.dart, chat/kit_markdown.dart, kit_mini_table.dart | `role: code` (opaque fill kept), queued bubble = prompt-bubble decoration, agent-strip chip → KitChip path, key caps `control`, markdown table / blockquote on `divider` | Inbox and permission commands visible in P/S; B ink code edges; queued bubble matches the prompt; terminal keys style | kit_code_block, kit_log_panel, kit_terminal, chat goldens (non-Solid) | L |
| **S7 Lists, rows, diff and team** | kit_row.dart (selected fill, badge, swipe), kit_tappable.dart, kit_divider.dart, kit_nav.dart (sidebar), diff/kit_diff_chrome.dart, diff/kit_diff_rows.dart, kit_board_lane.dart, team/team_parts.dart, kit_wheel.dart, motion/kit_tab_switcher.dart, kit_segmented.dart / kit_session_tabs.dart (move `selected: style == glass` into the spec), chat/kit_message.dart (soft branch into the spec) | `stateLayer` for press and hover, `divider` role, diff bands as `content`, lane and team cards through `decorate` (no amber fill in Soft) | Pressing a row sinks in S/B; dividers follow the style; Review changes, AI Team and Board take the style | kit_row, kit_divider, kit_diff, team goldens (non-Solid); kit_tappable tests | L |
| **S8 Theme leftovers and previews (last)** | app_theme.dart, kit_option_card.dart, kit_swatch.dart, live_background/live_mock_chat.dart, test/kit_ratchet_test.dart | Theme tooltip / radiusControl → tokens; Style picker samples for Solid / Glass / Soft through `KitSurfaceLook.resolve`; drop the two stale G21 glass rows | The picker shows the tuned Glass and Soft; tooltips follow Corners | kit_option_card golden (expected: the Glass and Soft samples move) | S |

**After S0–S8:** re-run this harness for all five styles. The target is (c) = 0 outside the exempt list and (b) only where an exemption keeps the fill.

**A gate to add** (S0 owner, test-only): a ratchet row for `BoxDecoration(` / `ShapeDecoration(` / `ColoredBox(` / `Material(color:` *inside* the kit outside the resolver files and an exempt list, only shrinking. That would have caught R1.

## 7. Image index (for the owner)

One screen per image:

1. `/home/eslam/Storage2/launch-footage/audit-styles/new-conversation/brutal-light.png`: in Brutal, the start box and Model/Project group are Brutal, but the four agent cards are still the plain Solid cards.
2. `/home/eslam/Storage2/launch-footage/audit-styles/conversations/brutal-light.png`: Conversations in Brutal has ink edges but no hard shadows at all, because a hook strips them under the page.
3. `/home/eslam/Storage2/launch-footage/audit-styles/scheduled/brutal-light.png`: the same style on Scheduled, with its hard shadows. This is what Conversations should look like.
4. `/home/eslam/Storage2/launch-footage/audit-styles/git-sheet/glass-dark.png`: the Git sheet in Glass is the same opaque slab as in Solid, with no blur, unlike the prompt tools sheet.
5. `/home/eslam/Storage2/launch-footage/audit-styles/chat-question-card/soft-light.png`: in Soft, the answers sit in a second raised panel inside the raised question card.
6. `/home/eslam/Storage2/launch-footage/audit-styles/report-problem/paper-light.png`: the text box under "Anything to add?" has no visible edge in any style, and no Paper ink box.

## Method and limits

- **Harness:** `tool/capture/audit/` (`audit_env.dart`, `run_style_audit.sh`, `sort_renders.py`, `style_audit_extras_test.dart`). It drives the existing census and vision captures through `AUDIT_*` environment variables. Small no-op-by-default hooks went into:
  - `tool/capture/fixtures.dart`
  - `census/census_core.dart`
  - six `vision_*_test.dart` files
  - `test/support/chats_fakes.dart`, `test/support/settings_scenes.dart`, `test/revamp/screen_phone_1_fixtures.dart`

  Every hook is marked "AUDIT TOOLING".
- **Effects** are injected as main.dart does: `KitEffectsScope` (Saver → `underSaver`) plus `kitStyleTheme` above the navigator, with blurred shadows on for the capture.
- **Not rendered:** a sheet over a menu at tablet width, the desktop sidebar (static only), 320 dp at 2× text.
- **Not checked visually:** Solid + Depth › Raised (R4 is a static finding).
- **Coverage:** Brutal and Paper are placeholders, so their spec values will change, but every gap above is a part that never asks the spec, so it survives tuning.
- **Paper's live background:** set directly, not through the picker, so `offWhenChosen` did not run (V22).
