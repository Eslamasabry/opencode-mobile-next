# Native UI rebuild scoreboard, 2026-11-08 (rerun)

The same method as [SCOREBOARD-2026-11-07.md](SCOREBOARD-2026-11-07.md), with strict counting, run on the new integration
head `native/screens-merge28` at **`342103266`** ("fix(kit): KitRow keeps kit B's padding parameter lost in the wave-7
merge"). That is 4c012f945 plus the coordinator's KitRow fix. Flutter reference: FLUTTER_REF `8302de537`; Glass `1bbdc3f9c`.

## Headline

| | 89ec49e55 (11-07) | **342103266 (now)** |
|---|---:|---:|
| goldens passing, strict | 339 / 4,525 (7.5 %) | **1,040 / 4,525 (23.0 %)** |
| goldens rendered (a gate case drew them) | 786 (17.4 %) | **2,219 (49.0 %)** |
| pass / rendered | 43.1 % | 46.9 % |
| not rendered (no gate case, or the case threw first) | 3,739 | 2,306 |
| waived | 2 | 6 |
| layout and alignment rows (L1, counted apart from goldens) | not recorded | 184 failing |
| matrix, cap and parity rows (X1, X2, P1) | – | 84 failing |

**RC1 gate G2 fails on both counts.** The daily path is at **1.8 %** against the 90 % target, and all goldens are at
**23.0 %** against 75 %. No daily-path screen golden passes yet: home 0 of 186, chat 0 of 280, composer 0 of 16,
settings hub 0 of 8. Sheets are at 14 of 284.

Since 11-07 the kit has moved a long way: `:ui:kit` went from 223 to 837 passing, and orders 07, 10 and 30 are at 90 %
or more. The screens have not. Most screen goldens still have no gate case: home has 20 of 186 rendered, chat 8 of 280,
settings 25 of 215.

### Run notes (what differs from a clean run)

- The VPS worktree was detached at 342103266, with JDK 21. Each order ran through `gate_order.sh --recheck <NN>` with
  `RB_DEV=1` (fetch, Gradle `desktopTest`, evaluate) using [scoreboard/tools/score.sh](scoreboard/tools/score.sh). The
  strict re-evaluation on the PC used [scoreboard/tools/strict.sh](scoreboard/tools/strict.sh), with every order marked
  done. The new harness no longer needs `OC_LAYOUT_BASELINE=update`: it records layout and alignment findings as L1
  rows and still renders.
- **Two merge losses found during the run:**
  1. On 4c012f945, `KitRow` had lost its `padding` parameter, so `:ui:kit` tests did not compile. The coordinator fixed
     it in 342103266, and the run restarted there. Only orders 42 and 43 had finished on 4c012f945, and those results
     were discarded.
  2. On 342103266, `:ui:kit-chat` tests don't compile. `rb/o08/KitCodeBlockGateTest.kt` uses `KitCodeFill` and the
     `copyWords`, `fileName`, `added` and `removed` parameters of `KitCodeBlock`, which the head no longer has; the two
     KitCodeBlock ports are being unified. That one file was moved aside in the VPS worktree only, and orders 14, 15 and
     16 were rerun. **The 44 `kit_code_block_*` goldens count as unmeasured** until a targeted rerun.
- Per-golden results (strict and as gated, with box %, blob, ink and line shift):
  [scoreboard/2026-11-08/goldens.tsv](scoreboard/2026-11-08/goldens.tsv). Layout and alignment findings:
  [scoreboard/2026-11-08/layout-align-findings.txt](scoreboard/2026-11-08/layout-align-findings.txt).

## The 12 worst remaining daily-path screens

Each image shows the Flutter golden on the left, the native render in the middle and the diff heat map on the right,
with a label strip on top. I looked at every one.

| # | Golden | Image | Measured | What still differs |
|---:|---|---|---|---|
| 1 | Composer + menu (`chat_approval_plus_light`) | [img](scoreboard/2026-11-08/chat_approval_plus_light.png) | box 45.3 %, blob 156,360 | The "Prompt tools" sheet shows two rows ("Commands and agents" highlighted, "More tools"). Flutter shows eight: Model, Auto-approve with its switch, Approval settings…, File from the project, Voice input, Commands and agents, Prompts and More tools. The sheet is 384 px shorter. Behind it, the chat header has a back arrow and sits 13 px higher. |
| 2 | Chat, loading (`chat_loading_dark`) | [img](scoreboard/2026-11-08/chat_loading_dark.png) | box 10.2 %, blob 6,052 | It is now the two-bubble skeleton, but 16 px higher, and the bar widths differ. The progress line sits under a shorter header (y 138, Flutter 150), and the "· Git" chip is missing. |
| 3 | Chat, load error (`chat_load_error_dark`) | [img](scoreboard/2026-11-08/chat_load_error_dark.png) | box 9.7 %, blob 4,958 | It now matches Flutter's layout (illustration, Report a problem, Details), but everything sits 13 px higher because the header is shorter (subtitle "shopfront" without "· Git"). The guard also raises `gap-step` 5 dp. |
| 4 | Chat, short turn (`chat_transcript_turn_dark`) | [img](scoreboard/2026-11-08/chat_transcript_turn_dark.png) | box 8.7 %, line moved 149 px | It is bottom-anchored now, and inline code matches. Still missing: the work line ("Read 3 files · edited 1 file"), the changed-file card, "All files" and the "Sonnet 4" footer, so the turn sits 193 px lower. There is no gap between paragraphs. The chip row lacks "Ask first", and its chip reads "Model" where Flutter has "Server default". The idle composer shows a dimmed send arrow where Flutter has the green mic. |
| 5 | Chat, wide (`chat_transcript_turn_1280x800_dark`) | [img](scoreboard/2026-11-08/chat_transcript_turn_1280x800_dark.png) | blob 2,874, line moved 348 px | The 700 dp pane is centred now. The differences left are #4's. |
| 6 | Home (`chats_home_mixed_dark`) | [img](scoreboard/2026-11-08/chats_home_mixed_dark.png) | box 7.0 %, line moved 159 px | The header now matches. Each row is new: a project avatar line ("AL alpha … 2 min ago") above the title, then the status below. Flutter has the title, then one meta line ("Needs you · alpha · 2 min ago"), so rows are 91 px apart against 69. "Show all conversations", "Projects", "Activity" and "Usage" rows are extra. |
| 7 | Home, project sheet, wide (`chats_home_sheet_1280x800_light`) | [img](scoreboard/2026-11-08/chats_home_sheet_1280x800_light.png) | box 4.9 %, blob 5,888 | The sheet itself is close; it sits about 8 px lower with rows 3-4 px taller. Most of the diff is the list behind it (#6). |
| 8 | Composer, approve everything (`chat_approval_confirm_light`) | [img](scoreboard/2026-11-08/chat_approval_confirm_light.png) | box 4.9 %, blob 4,174 | The confirm sheet is close but 10 px lower. The rest is the header (back arrow, one subtitle line) and the transcript behind it. |
| 9 | Settings › Appearance (`settings_appearance_guided_solid_returning_dark`) | [img](scoreboard/2026-11-08/settings_appearance_guided_solid_returning_dark.png) | box 22.1 %, blob 19,489 | "Which colours?" shows 2 themes; Flutter shows 6 (Graphite, Nord, Catppuccin, Dracula, Tokyo Night, Gruvbox) and "See all 31 themes". The accent picker has 8 unlabelled circles; Flutter has a 4×2 grid with names (Match, Mint, Teal, Blue, Violet, Pink, Mono, Silver) and Match ringed. Native puts Glass second among the styles, which is the owner's 11-05 decision (§5.1); Flutter at FLUTTER_REF has Soft there. That card needs a waiver, not a fix. |
| 10 | Settings › Notifications (`settings_notifications_loaded_dark`) | [img](scoreboard/2026-11-08/settings_notifications_loaded_dark.png) | box 13.9 %, blob 1,540 | The title, the grey on-but-blocked switches and the full-width button now match. Rows are still 56 dp apart where Flutter's are 65, so the list drifts 80 px by "Stay connected". The alignment guard also flags it: icon centres sit 10 dp below the title line. |
| 11 | Sheet, wide (`kit_sheet_with_icon_1280x800_light`) | [img](scoreboard/2026-11-08/kit_sheet_with_icon_1280x800_light.png) | box 40.8 %, blob 62,118 | On wide windows Flutter draws a centred dialog with its actions in one row at the end: "More languages · Keep current · Use English". Native stacks centred buttons with "More languages" under them, which makes the panel about 160 px taller. |
| 12 | Confirm sheet, tablet (`kit_confirm_destructive_800x1280_light`) | [img](scoreboard/2026-11-08/kit_confirm_destructive_800x1280_light.png) | box 23.7 %, blob 33,827 | It renders now (it was empty on 11-07). It is 640 dp wide and starts at x 80; Flutter is 560 dp and centred (the confirm cap). The drag handle is missing. The "Type fox to confirm" hint is in mono where Flutter uses the body face. The buttons sit at the start edge, not centred. |

Settings hub (`settings_hub_loaded_dark`, [img](scoreboard/2026-11-08/settings_hub_loaded_dark.png)) nearly passes:
only the line shift fails (3.7 px against a 2.5 max). The layout guard says the title starts at x 56 while the list's
rows start at 18.

## Scoreboard tables (strict, 342103266)

### Per order

| order | goldens | pass | fail (rendered) | fail (no gate case rendered it) | waived | deferred as gated¹ | pass / total | pass / rendered |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 01 | 226 | 156 | 66 | 2 | 2 | 0 | 69.0% | 69.6% |
| 02 | 10 | 0 | 10 | 0 | 0 | 8 | 0.0% | 0.0% |
| 05 | 300 | 82 | 56 | 162 | 0 | 0 | 27.3% | 59.4% |
| 06 | 131 | 75 | 56 | 0 | 0 | 17 | 57.3% | 57.3% |
| 07 | 38 | 36 | 2 | 0 | 0 | 0 | 94.7% | 94.7% |
| 08 | 306 | 143 | 111 | 52 | 0 | 0 | 46.7% | 56.3% |
| 09 | 81 | 36 | 45 | 0 | 0 | 0 | 44.4% | 44.4% |
| 10 | 20 | 18 | 2 | 0 | 0 | 0 | 90.0% | 90.0% |
| 11 | 242 | 4 | 36 | 202 | 0 | 0 | 1.7% | 10.0% |
| 12 | 42 | 10 | 32 | 0 | 0 | 0 | 23.8% | 23.8% |
| 13 | 84 | 22 | 56 | 6 | 0 | 8 | 26.2% | 28.2% |
| 14 | 286 | 28 | 142 | 116 | 0 | 0 | 9.8% | 16.5% |
| 15 | 113 | 16 | 20 | 77 | 0 | 0 | 14.2% | 44.4% |
| 16 | 16 | 0 | 11 | 5 | 0 | 0 | 0.0% | 0.0% |
| 17 | 40 | 2 | 10 | 28 | 0 | 0 | 5.0% | 16.7% |
| 18 | 178 | 49 | 129 | 0 | 0 | 0 | 27.5% | 27.5% |
| 20 | 230 | 143 | 87 | 0 | 0 | 0 | 62.2% | 62.2% |
| 21 | 173 | 50 | 77 | 46 | 0 | 0 | 28.9% | 39.4% |
| 22 | 102 | 57 | 45 | 0 | 0 | 0 | 55.9% | 55.9% |
| 23 | 74 | 6 | 46 | 22 | 0 | 0 | 8.1% | 11.5% |
| 24 | 60 | 25 | 17 | 18 | 0 | 0 | 41.7% | 59.5% |
| 25 | 14 | 6 | 8 | 0 | 0 | 0 | 42.9% | 42.9% |
| 29 | 22 | 16 | 2 | 0 | 4 | 2 | 72.7% | 72.7% |
| 30 | 8 | 8 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 31 | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 32 | 3 | 0 | 0 | 3 | 0 | 0 | 0.0% | 0.0% |
| 33 | 3 | 0 | 0 | 3 | 0 | 0 | 0.0% | 0.0% |
| 34 | 64 | 0 | 0 | 64 | 0 | 0 | 0.0% | 0.0% |
| 36 | 95 | 0 | 0 | 95 | 0 | 0 | 0.0% | 0.0% |
| 37 | 64 | 0 | 0 | 64 | 0 | 0 | 0.0% | 0.0% |
| 40 | 58 | 28 | 8 | 22 | 0 | 0 | 48.3% | 77.8% |
| 41 | 186 | 0 | 20 | 166 | 0 | 0 | 0.0% | 0.0% |
| 42 | 280 | 0 | 8 | 272 | 0 | 0 | 0.0% | 0.0% |
| 43 | 16 | 0 | 10 | 6 | 0 | 0 | 0.0% | 0.0% |
| 44 | 215 | 0 | 25 | 190 | 0 | 0 | 0.0% | 0.0% |
| 45 | 155 | 1 | 13 | 141 | 0 | 0 | 0.6% | 7.1% |
| 46 | 67 | 0 | 0 | 67 | 0 | 0 | 0.0% | 0.0% |
| 47 | 52 | 0 | 0 | 52 | 0 | 0 | 0.0% | 0.0% |
| 48 | 166 | 0 | 4 | 162 | 0 | 0 | 0.0% | 0.0% |
| 49 | 104 | 0 | 6 | 98 | 0 | 0 | 0.0% | 0.0% |
| 50 | 25 | 0 | 2 | 23 | 0 | 0 | 0.0% | 0.0% |
| 51 | 108 | 0 | 0 | 108 | 0 | 0 | 0.0% | 0.0% |
| 52 | 12 | 1 | 11 | 0 | 0 | 0 | 8.3% | 8.3% |
| 53 | 50 | 22 | 0 | 28 | 0 | 0 | 44.0% | 100.0% |
| **all orders** | 4525 | 1040 | 1173 | 2306 | 6 | 35 | 23.0% | 46.9% |

### Per Gradle module

| module | goldens | pass | fail (rendered) | fail (no gate case rendered it) | waived | deferred as gated¹ | pass / total | pass / rendered |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| `:ui:kit` | 1948 | 837 | 700 | 405 | 6 | 29 | 43.0% | 54.2% |
| `:ui:kit-chat` | 643 | 95 | 215 | 333 | 0 | 0 | 14.8% | 30.6% |
| `:feature:chat` | 280 | 0 | 8 | 272 | 0 | 0 | 0.0% | 0.0% |
| `:ui:gate` | 223 | 0 | 0 | 223 | 0 | 0 | 0.0% | 0.0% |
| `:ui:kit-content` | 217 | 56 | 151 | 10 | 0 | 6 | 25.8% | 27.1% |
| `:feature:settings` | 215 | 0 | 25 | 190 | 0 | 0 | 0.0% | 0.0% |
| `:feature:chats` | 186 | 0 | 20 | 166 | 0 | 0 | 0.0% | 0.0% |
| `:feature:phone` | 166 | 0 | 4 | 162 | 0 | 0 | 0.0% | 0.0% |
| `:feature:servers` | 155 | 1 | 13 | 141 | 0 | 0 | 0.6% | 7.1% |
| `:feature:diagnostics` | 108 | 0 | 0 | 108 | 0 | 0 | 0.0% | 0.0% |
| `:feature:agents` | 104 | 0 | 6 | 98 | 0 | 0 | 0.0% | 0.0% |
| `:feature:files` | 67 | 0 | 0 | 67 | 0 | 0 | 0.0% | 0.0% |
| `:feature:onboarding` | 58 | 28 | 8 | 22 | 0 | 0 | 48.3% | 77.8% |
| `:feature:projects` | 52 | 0 | 0 | 52 | 0 | 0 | 0.0% | 0.0% |
| `:ui:shell` | 50 | 22 | 0 | 28 | 0 | 0 | 44.0% | 100.0% |
| `:feature:usage` | 25 | 0 | 2 | 23 | 0 | 0 | 0.0% | 0.0% |
| `:feature:chat-composer` | 16 | 0 | 10 | 6 | 0 | 0 | 0.0% | 0.0% |
| `:feature:widget-panels` | 12 | 1 | 11 | 0 | 0 | 0 | 8.3% | 8.3% |

### Per part / screen gallery

| order · tracker id | goldens | pass | fail (rendered) | fail (no gate case rendered it) | waived | deferred as gated¹ | pass / total | pass / rendered |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 01 · `part-kit_icon` | 32 | 16 | 16 | 0 | 0 | 0 | 50.0% | 50.0% |
| 01 · `part-kit_motion` | 54 | 54 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 01 · `part-kit_signal` | 8 | 0 | 8 | 0 | 0 | 0 | 0.0% | 0.0% |
| 01 · `part-kit_since` | 22 | 18 | 4 | 0 | 0 | 0 | 81.8% | 81.8% |
| 01 · `part-kit_surface` | 34 | 16 | 18 | 0 | 0 | 0 | 47.1% | 47.1% |
| 01 · `part-kit_tab_switcher` | 22 | 22 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 01 · `part-kit_tappable` | 22 | 14 | 6 | 2 | 0 | 0 | 63.6% | 70.0% |
| 01 · `part-kit_text` | 32 | 16 | 14 | 0 | 2 | 0 | 50.0% | 50.0% |
| 02 · `part-kit_tokens` | 10 | 0 | 10 | 0 | 0 | 8 | 0.0% | 0.0% |
| 05 · `part-kit_choice_list` | 30 | 0 | 0 | 30 | 0 | 0 | 0.0% | 0.0% |
| 05 · `part-kit_field` | 28 | 0 | 0 | 28 | 0 | 0 | 0.0% | 0.0% |
| 05 · `part-kit_icon_button` | 36 | 7 | 29 | 0 | 0 | 0 | 19.4% | 19.4% |
| 05 · `part-kit_image` | 42 | 42 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 05 · `part-kit_progress` | 30 | 0 | 0 | 30 | 0 | 0 | 0.0% | 0.0% |
| 05 · `part-kit_progress_row` | 24 | 0 | 0 | 24 | 0 | 0 | 0.0% | 0.0% |
| 05 · `part-kit_segmented` | 30 | 16 | 14 | 0 | 0 | 0 | 53.3% | 53.3% |
| 05 · `part-kit_status_mark` | 24 | 0 | 0 | 24 | 0 | 0 | 0.0% | 0.0% |
| 05 · `part-kit_swatch` | 30 | 17 | 13 | 0 | 0 | 0 | 56.7% | 56.7% |
| 05 · `part-kit_term` | 26 | 0 | 0 | 26 | 0 | 0 | 0.0% | 0.0% |
| 06 · `part-kit_chip` | 16 | 16 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 06 · `part-kit_divider` | 22 | 12 | 10 | 0 | 0 | 2 | 54.5% | 54.5% |
| 06 · `part-kit_filter_chip` | 6 | 6 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 06 · `part-kit_filter_chips` | 6 | 6 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 06 · `part-kit_key_value` | 7 | 5 | 2 | 0 | 0 | 2 | 71.4% | 71.4% |
| 06 · `part-kit_level_meter` | 22 | 22 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 06 · `part-kit_needs_you` | 16 | 4 | 12 | 0 | 0 | 0 | 25.0% | 25.0% |
| 06 · `part-kit_qr` | 10 | 2 | 8 | 0 | 0 | 6 | 20.0% | 20.0% |
| 06 · `part-kit_search_field` | 20 | 2 | 18 | 0 | 0 | 7 | 10.0% | 10.0% |
| 06 · `part-kit_section_label` | 6 | 0 | 6 | 0 | 0 | 0 | 0.0% | 0.0% |
| 07 · `part-kit_buttons` | 32 | 30 | 2 | 0 | 0 | 0 | 93.8% | 93.8% |
| 07 · `part-kit_status_tag` | 6 | 6 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 08 · `part-kit_action_stack` | 24 | 20 | 4 | 0 | 0 | 0 | 83.3% | 83.3% |
| 08 · `part-kit_checklist` | 24 | 11 | 13 | 0 | 0 | 0 | 45.8% | 45.8% |
| 08 · `part-kit_code_block` | 44 | 0 | 0 | 44 | 0 | 0 | 0.0% | 0.0% |
| 08 · `part-kit_log_panel` | 26 | 1 | 23 | 2 | 0 | 0 | 3.8% | 4.2% |
| 08 · `part-kit_notice` | 52 | 23 | 29 | 0 | 0 | 0 | 44.2% | 44.2% |
| 08 · `part-kit_receipt` | 26 | 26 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 08 · `part-kit_row` | 26 | 15 | 5 | 6 | 0 | 0 | 57.7% | 75.0% |
| 08 · `part-kit_state_view` | 30 | 14 | 16 | 0 | 0 | 0 | 46.7% | 46.7% |
| 08 · `part-kit_status_line` | 30 | 22 | 8 | 0 | 0 | 0 | 73.3% | 73.3% |
| 08 · `part-kit_task_card` | 24 | 11 | 13 | 0 | 0 | 0 | 45.8% | 45.8% |
| 09 · `part-kit_consequences` | 8 | 6 | 2 | 0 | 0 | 0 | 75.0% | 75.0% |
| 09 · `part-kit_details_fold` | 20 | 5 | 15 | 0 | 0 | 0 | 25.0% | 25.0% |
| 09 · `part-kit_feed_item` | 10 | 10 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 09 · `part-kit_group_note` | 16 | 1 | 15 | 0 | 0 | 0 | 6.2% | 6.2% |
| 09 · `part-kit_last_known` | 8 | 8 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 09 · `part-kit_mini_table` | 7 | 2 | 5 | 0 | 0 | 0 | 28.6% | 28.6% |
| 09 · `part-kit_sliver_row_group` | 6 | 4 | 2 | 0 | 0 | 0 | 66.7% | 66.7% |
| 09 · `part-kit_swipe_action` | 6 | 0 | 6 | 0 | 0 | 0 | 0.0% | 0.0% |
| 10 · `part-kit_row_parts` | 20 | 18 | 2 | 0 | 0 | 0 | 90.0% | 90.0% |
| 11 · `part-kit_capability_explainer` | 20 | 0 | 0 | 20 | 0 | 0 | 0.0% | 0.0% |
| 11 · `part-kit_context_sheet` | 12 | 0 | 0 | 12 | 0 | 0 | 0.0% | 0.0% |
| 11 · `part-kit_date_time_picker` | 24 | 0 | 0 | 24 | 0 | 0 | 0.0% | 0.0% |
| 11 · `part-kit_dialog` | 24 | 0 | 2 | 22 | 0 | 0 | 0.0% | 0.0% |
| 11 · `part-kit_jump_pill` | 16 | 0 | 0 | 16 | 0 | 0 | 0.0% | 0.0% |
| 11 · `part-kit_menu` | 30 | 0 | 0 | 30 | 0 | 0 | 0.0% | 0.0% |
| 11 · `part-kit_request_card` | 30 | 0 | 2 | 28 | 0 | 0 | 0.0% | 0.0% |
| 11 · `part-kit_request_sheet` | 24 | 0 | 0 | 24 | 0 | 0 | 0.0% | 0.0% |
| 11 · `part-kit_sheet` | 36 | 4 | 32 | 0 | 0 | 0 | 11.1% | 11.1% |
| 11 · `part-kit_undo` | 26 | 0 | 0 | 26 | 0 | 0 | 0.0% | 0.0% |
| 12 · `part-kit_confirm_sheet` | 32 | 0 | 32 | 0 | 0 | 0 | 0.0% | 0.0% |
| 12 · `part-kit_floating_action` | 10 | 10 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 13 · `part-kit_arrival` | 8 | 0 | 8 | 0 | 0 | 8 | 0.0% | 0.0% |
| 13 · `part-kit_breadcrumb` | 16 | 13 | 3 | 0 | 0 | 0 | 81.2% | 81.2% |
| 13 · `part-kit_nav` | 10 | 5 | 5 | 0 | 0 | 0 | 50.0% | 50.0% |
| 13 · `part-kit_page_route` | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 13 · `part-kit_screen` | 18 | 0 | 18 | 0 | 0 | 0 | 0.0% | 0.0% |
| 13 · `part-kit_top_bar` | 26 | 4 | 22 | 0 | 0 | 0 | 15.4% | 15.4% |
| 14 · `part-kit_agent_strip` | 24 | 0 | 24 | 0 | 0 | 0 | 0.0% | 0.0% |
| 14 · `part-kit_ask_line` | 30 | 12 | 18 | 0 | 0 | 0 | 40.0% | 40.0% |
| 14 · `part-kit_composer` | 32 | 4 | 6 | 22 | 0 | 0 | 12.5% | 40.0% |
| 14 · `part-kit_composer_chips` | 24 | 8 | 4 | 12 | 0 | 0 | 33.3% | 66.7% |
| 14 · `part-kit_diff_view` | 36 | 0 | 10 | 26 | 0 | 0 | 0.0% | 0.0% |
| 14 · `part-kit_markdown` | 30 | 0 | 14 | 16 | 0 | 0 | 0.0% | 0.0% |
| 14 · `part-kit_message` | 28 | 2 | 22 | 4 | 0 | 0 | 7.1% | 8.3% |
| 14 · `part-kit_queued_message` | 28 | 2 | 10 | 16 | 0 | 0 | 7.1% | 16.7% |
| 14 · `part-kit_tool_row` | 26 | 0 | 10 | 16 | 0 | 0 | 0.0% | 0.0% |
| 14 · `part-kit_turn` | 28 | 0 | 24 | 4 | 0 | 0 | 0.0% | 0.0% |
| 15 · `part-kit_background_work_line` | 10 | 0 | 0 | 10 | 0 | 0 | 0.0% | 0.0% |
| 15 · `part-kit_composer_glow` | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 15 · `part-kit_context_chart` | 10 | 0 | 0 | 10 | 0 | 0 | 0.0% | 0.0% |
| 15 · `part-kit_context_ring` | 18 | 0 | 0 | 18 | 0 | 0 | 0.0% | 0.0% |
| 15 · `part-kit_phrase_chips` | 8 | 8 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 15 · `part-kit_project_file_list` | 10 | 0 | 0 | 10 | 0 | 0 | 0.0% | 0.0% |
| 15 · `part-kit_sense_ask` | 11 | 1 | 3 | 7 | 0 | 0 | 9.1% | 25.0% |
| 15 · `part-kit_step_timeline` | 16 | 1 | 9 | 6 | 0 | 0 | 6.2% | 10.0% |
| 15 · `part-kit_stream_caret` | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 15 · `part-kit_work_line` | 18 | 6 | 8 | 4 | 0 | 0 | 33.3% | 42.9% |
| 16 · `part-kit_composer_status_strip` | 6 | 0 | 1 | 5 | 0 | 0 | 0.0% | 0.0% |
| 16 · `part-kit_markdown_table` | 4 | 0 | 4 | 0 | 0 | 0 | 0.0% | 0.0% |
| 16 · `part-kit_transcript_excerpt` | 6 | 0 | 6 | 0 | 0 | 0 | 0.0% | 0.0% |
| 17 · `part-kit_step_light` | 40 | 2 | 10 | 28 | 0 | 0 | 5.0% | 16.7% |
| 18 · `part-kit_board_lane` | 16 | 13 | 3 | 0 | 0 | 0 | 81.2% | 81.2% |
| 18 · `part-kit_chart` | 15 | 9 | 6 | 0 | 0 | 0 | 60.0% | 60.0% |
| 18 · `part-kit_image_gallery` | 20 | 0 | 20 | 0 | 0 | 0 | 0.0% | 0.0% |
| 18 · `part-kit_image_markup` | 16 | 0 | 16 | 0 | 0 | 0 | 0.0% | 0.0% |
| 18 · `part-kit_scanner` | 13 | 3 | 10 | 0 | 0 | 0 | 23.1% | 23.1% |
| 18 · `part-kit_terminal_view` | 34 | 7 | 27 | 0 | 0 | 0 | 20.6% | 20.6% |
| 18 · `part-kit_viewer` | 48 | 1 | 47 | 0 | 0 | 0 | 2.1% | 2.1% |
| 18 · `part-kit_work_graph` | 16 | 16 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 20 · `part-kit_digest` | 22 | 15 | 7 | 0 | 0 | 0 | 68.2% | 68.2% |
| 20 · `part-kit_findings_card` | 22 | 18 | 4 | 0 | 0 | 0 | 81.8% | 81.8% |
| 20 · `part-kit_merge_queue` | 22 | 19 | 3 | 0 | 0 | 0 | 86.4% | 86.4% |
| 20 · `part-kit_milestone_row` | 22 | 17 | 5 | 0 | 0 | 0 | 77.3% | 77.3% |
| 20 · `part-kit_phase_card` | 22 | 15 | 7 | 0 | 0 | 0 | 68.2% | 68.2% |
| 20 · `part-kit_plan_card` | 22 | 15 | 7 | 0 | 0 | 0 | 68.2% | 68.2% |
| 20 · `part-kit_project_row` | 22 | 17 | 5 | 0 | 0 | 0 | 77.3% | 77.3% |
| 20 · `part-kit_promote_card` | 22 | 15 | 7 | 0 | 0 | 0 | 68.2% | 68.2% |
| 20 · `part-kit_server_trust` | 24 | 12 | 12 | 0 | 0 | 0 | 50.0% | 50.0% |
| 20 · `part-kit_steer_box` | 30 | 0 | 30 | 0 | 0 | 0 | 0.0% | 0.0% |
| 21 · `part-kit_agent_card` | 15 | 6 | 9 | 0 | 0 | 0 | 40.0% | 40.0% |
| 21 · `part-kit_ask_field` | 16 | 0 | 16 | 0 | 0 | 0 | 0.0% | 0.0% |
| 21 · `part-kit_continued_line` | 16 | 16 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 21 · `part-kit_fact_row` | 16 | 7 | 9 | 0 | 0 | 0 | 43.8% | 43.8% |
| 21 · `part-kit_frost` | 20 | 0 | 18 | 2 | 0 | 0 | 0.0% | 0.0% |
| 21 · `part-kit_handoff_record` | 16 | 8 | 8 | 0 | 0 | 0 | 50.0% | 50.0% |
| 21 · `part-kit_one_time_code` | 14 | 3 | 11 | 0 | 0 | 0 | 21.4% | 21.4% |
| 21 · `part-kit_server_lane` | 22 | 0 | 0 | 22 | 0 | 0 | 0.0% | 0.0% |
| 21 · `part-kit_timeline_day` | 22 | 0 | 0 | 22 | 0 | 0 | 0.0% | 0.0% |
| 21 · `part-kit_word_text` | 16 | 10 | 6 | 0 | 0 | 0 | 62.5% | 62.5% |
| 22 · `part-kit_carousel` | 10 | 3 | 7 | 0 | 0 | 0 | 30.0% | 30.0% |
| 22 · `part-kit_choice_cards` | 12 | 6 | 6 | 0 | 0 | 0 | 50.0% | 50.0% |
| 22 · `part-kit_choice_tiles` | 12 | 8 | 4 | 0 | 0 | 0 | 66.7% | 66.7% |
| 22 · `part-kit_fine_tune_panel` | 10 | 2 | 8 | 0 | 0 | 0 | 20.0% | 20.0% |
| 22 · `part-kit_guide` | 10 | 0 | 10 | 0 | 0 | 0 | 0.0% | 0.0% |
| 22 · `part-kit_live_look_preview` | 10 | 10 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 22 · `part-kit_option_card` | 8 | 2 | 6 | 0 | 0 | 0 | 25.0% | 25.0% |
| 22 · `part-kit_page_heading` | 8 | 6 | 2 | 0 | 0 | 0 | 75.0% | 75.0% |
| 22 · `part-kit_progress_ring` | 12 | 10 | 2 | 0 | 0 | 0 | 83.3% | 83.3% |
| 22 · `part-kit_step_slider` | 10 | 10 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 23 · `part-kit_agent_choice` | 6 | 0 | 6 | 0 | 0 | 0 | 0.0% | 0.0% |
| 23 · `part-kit_agent_grid` | 6 | 0 | 6 | 0 | 0 | 0 | 0.0% | 0.0% |
| 23 · `part-kit_agent_line` | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 23 · `part-kit_presence` | 8 | 4 | 4 | 0 | 0 | 0 | 50.0% | 50.0% |
| 23 · `part-kit_spec_block` | 8 | 0 | 0 | 8 | 0 | 0 | 0.0% | 0.0% |
| 23 · `part-kit_start_box` | 8 | 0 | 8 | 0 | 0 | 0 | 0.0% | 0.0% |
| 23 · `part-kit_status_answer` | 8 | 2 | 6 | 0 | 0 | 0 | 25.0% | 25.0% |
| 23 · `part-kit_web_preview` | 8 | 0 | 0 | 8 | 0 | 0 | 0.0% | 0.0% |
| 23 · `part-kit_wheel` | 8 | 0 | 8 | 0 | 0 | 0 | 0.0% | 0.0% |
| 23 · `part-kit_window_row` | 8 | 0 | 8 | 0 | 0 | 0 | 0.0% | 0.0% |
| 24 · `part-kit_error_fallback` | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 24 · `part-kit_floating_panel` | 6 | 0 | 6 | 0 | 0 | 0 | 0.0% | 0.0% |
| 24 · `part-kit_hero_question` | 6 | 2 | 4 | 0 | 0 | 0 | 33.3% | 33.3% |
| 24 · `part-kit_ink_layer` | 6 | 3 | 3 | 0 | 0 | 0 | 50.0% | 50.0% |
| 24 · `part-kit_live_background` | 6 | 6 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 24 · `part-kit_plan_block` | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 24 · `part-kit_scroll_fade` | 6 | 6 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 24 · `part-kit_session_tabs` | 6 | 6 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 24 · `part-kit_task_chip` | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 24 · `part-kit_tip` | 6 | 2 | 4 | 0 | 0 | 0 | 33.3% | 33.3% |
| 25 · `part-kit_frost_header` | 2 | 0 | 2 | 0 | 0 | 0 | 0.0% | 0.0% |
| 25 · `part-kit_widget_preview` | 6 | 6 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 25 · `part-kit_widget_style_form` | 6 | 0 | 6 | 0 | 0 | 0 | 0.0% | 0.0% |
| 29 · `gallery-o29` | 22 | 16 | 2 | 0 | 4 | 2 | 72.7% | 72.7% |
| 30 · `gallery-o30` | 8 | 8 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 31 · `part-kit_live_blueprint` | 1 | 0 | 0 | 1 | 0 | 0 | 0.0% | 0.0% |
| 31 · `part-kit_live_bokeh` | 1 | 0 | 0 | 1 | 0 | 0 | 0.0% | 0.0% |
| 31 · `part-kit_live_brushed_metal` | 1 | 0 | 0 | 1 | 0 | 0 | 0.0% | 0.0% |
| 31 · `part-kit_live_caustics` | 1 | 0 | 0 | 1 | 0 | 0 | 0.0% | 0.0% |
| 31 · `part-kit_live_circuit` | 2 | 0 | 0 | 2 | 0 | 0 | 0.0% | 0.0% |
| 32 · `part-kit_live_honeycomb` | 2 | 0 | 0 | 2 | 0 | 0 | 0.0% | 0.0% |
| 32 · `part-kit_live_matrix_rain` | 1 | 0 | 0 | 1 | 0 | 0 | 0.0% | 0.0% |
| 33 · `part-kit_live_oscilloscope` | 1 | 0 | 0 | 1 | 0 | 0 | 0.0% | 0.0% |
| 33 · `part-kit_live_radar` | 1 | 0 | 0 | 1 | 0 | 0 | 0.0% | 0.0% |
| 33 · `part-kit_live_silk` | 1 | 0 | 0 | 1 | 0 | 0 | 0.0% | 0.0% |
| 34 · `part-glass-kit_surface_glass` | 32 | 0 | 0 | 32 | 0 | 0 | 0.0% | 0.0% |
| 34 · `part-glass-kit_words_plate` | 14 | 0 | 0 | 14 | 0 | 0 | 0.0% | 0.0% |
| 34 · `part-kit_glass` | 18 | 0 | 0 | 18 | 0 | 0 | 0.0% | 0.0% |
| 36 · `gallery-o36` | 95 | 0 | 0 | 95 | 0 | 0 | 0.0% | 0.0% |
| 37 · `gallery-o37` | 64 | 0 | 0 | 64 | 0 | 0 | 0.0% | 0.0% |
| 40 · `gallery-o40` | 58 | 28 | 8 | 22 | 0 | 0 | 48.3% | 77.8% |
| 41 · `gallery-o41` | 186 | 0 | 20 | 166 | 0 | 0 | 0.0% | 0.0% |
| 42 · `gallery-o42` | 280 | 0 | 8 | 272 | 0 | 0 | 0.0% | 0.0% |
| 43 · `gallery-o43` | 16 | 0 | 10 | 6 | 0 | 0 | 0.0% | 0.0% |
| 44 · `gallery-o44` | 215 | 0 | 25 | 190 | 0 | 0 | 0.0% | 0.0% |
| 45 · `gallery-o45` | 155 | 1 | 13 | 141 | 0 | 0 | 0.6% | 7.1% |
| 46 · `gallery-o46` | 67 | 0 | 0 | 67 | 0 | 0 | 0.0% | 0.0% |
| 47 · `gallery-o47` | 52 | 0 | 0 | 52 | 0 | 0 | 0.0% | 0.0% |
| 48 · `gallery-o48` | 166 | 0 | 4 | 162 | 0 | 0 | 0.0% | 0.0% |
| 49 · `gallery-o49` | 104 | 0 | 6 | 98 | 0 | 0 | 0.0% | 0.0% |
| 50 · `gallery-o50` | 25 | 0 | 2 | 23 | 0 | 0 | 0.0% | 0.0% |
| 51 · `gallery-o51` | 108 | 0 | 0 | 108 | 0 | 0 | 0.0% | 0.0% |
| 52 · `gallery-o52` | 12 | 1 | 11 | 0 | 0 | 0 | 8.3% | 8.3% |
| 53 · `gallery-o53` | 50 | 22 | 0 | 28 | 0 | 0 | 44.0% | 100.0% |

### Worst measured diffs, ranked by daily-path visibility

Rendered failing shots only. Order: visibility weight of the surface (home, chat, composer, sheets, settings first), then box %. One line per golden stem (the worse mode), at most three per part or gallery.

| rank | surface | order | golden (worse mode) | tier | box % | blob px | ink ratio | line moved px |
|---:|---|---|---|---|---:|---:|---:|---:|
| 1 | composer | 43 | `chat_approval_plus_light` | T5 | 45.28 | 156360 | 6.01 | 173.8 |
| 2 | chat | 42 | `chat_loading_dark` | T5 | 10.23 | 6052 | n/a | 64.5 |
| 3 | chat | 42 | `chat_load_error_dark` | T5 | 9.65 | 4958 | 0.00 | 14.2 |
| 4 | chat | 42 | `chat_transcript_turn_dark` | T5 | 8.74 | 2874 | 0.00 | 148.7 |
| 5 | home | 41 | `chats_home_mixed_dark` | T5 | 7.02 | 689 | 0.00 | 158.7 |
| 6 | composer | 43 | `chat_approval_confirm_light` | T5 | 4.91 | 4174 | 0.01 | 147.7 |
| 7 | home | 41 | `chats_home_sheet_1280x800_light` | T5 | 4.90 | 5888 | n/a | 252.4 |
| 8 | home | 41 | `chats_home_sheet_light` | T5 | 4.64 | 3288 |  | 4.0 |
| 9 | composer | 43 | `chat_approval_ask_dark` | T5 | 4.39 | 263 | n/a | 148.7 |
| 10 | chat kit | 14 | `kit_diff_view_too_big_dark` | T3 | 36.49 | 100304 | 1.38 | 20.2 |
| 11 | chat kit | 14 | `kit_turn_waiting_for_you_dark` | T3 | 33.71 | 45479 | 0.00 | 53.7 |
| 12 | chat kit | 14 | `kit_diff_view_unified_dark` | T3 | 31.52 | 15642 | 9.89 | 162.3 |
| 13 | chat kit | 14 | `kit_diff_view_selecting_light` | T3 | 31.02 | 49359 | 0.00 | 173.1 |
| 14 | chat kit | 14 | `kit_agent_strip_needs_you_1280x800_dark` | T2 | 26.06 | 2947 | 0.67 | 27.3 |
| 15 | chat kit | 14 | `kit_agent_strip_mixed_1280x800_dark` | T2 | 25.33 | 2437 | 0.64 | 27.5 |
| 16 | chat kit | 14 | `kit_agent_strip_done_1280x800_dark` | T2 | 25.07 | 2437 | 0.64 | 26.5 |
| 17 | chat kit | 14 | `kit_markdown_images_inline_dark` | T3 | 23.34 | 1101 | 0.00 | 62.8 |
| 18 | chat kit | 15 | `kit_sense_ask_default_1280x800_dark` | T2 | 21.98 | 7462 |  | 561.2 |
| 19 | chat kit | 14 | `kit_markdown_secondary_1280x800_dark` | T3 | 20.70 | 345 | 2.67 | 26.2 |
| 20 | chat kit | 14 | `kit_markdown_secondary_dark` | T3 | 19.82 | 347 | n/a | 47.6 |
| 21 | chat kit | 14 | `kit_message_thought_open_dark` | T3 | 17.75 | 219 | 0.00 | 154.6 |
| 22 | chat kit | 15 | `kit_step_timeline_expanded_ar_dark` | T3 | 16.79 | 22413 | 0.00 | 50.9 |
| 23 | chat kit | 15 | `kit_step_timeline_running_open_dark` | T3 | 15.49 | 22541 | 0.00 | 44.1 |
| 24 | chat kit | 15 | `kit_step_timeline_expanded_dark` | T3 | 15.04 | 22235 | 0.00 | 36.1 |
| 25 | chat kit | 14 | `kit_message_prompt_attachments_dark` | T3 | 14.32 | 4939 | 0.00 | 161.2 |
| 26 | chat kit | 14 | `kit_composer_chips_attachments_light` | T2 | 13.27 | 2775 | 0.63 | 37.4 |
| 27 | chat kit | 14 | `kit_composer_chips_default_light` | T2 | 12.22 | 2775 | 0.63 | 37.4 |
| 28 | chat kit | 14 | `kit_tool_row_agent_dark` | T3 | 10.85 | 353 | 0.00 | 37.8 |
| 29 | chat kit | 14 | `kit_message_notice_dark` | T3 | 10.65 | 232 | 0.00 | 124.3 |
| 30 | chat kit | 14 | `kit_ask_line_ask_inline_text2_dark` | T2 | 10.49 | 275 |  | 15.2 |
| 31 | chat kit | 14 | `kit_ask_line_ask_inline_text2_1280x800_dark` | T2 | 10.42 | 275 |  | 15.2 |
| 32 | chat kit | 14 | `kit_ask_line_ask_inline_1280x800_dark` | T2 | 9.92 | 95 |  | 4.0 |
| 33 | chat kit | 14 | `kit_tool_row_failed_dark` | T3 | 8.83 | 221 |  | 30.4 |
| 34 | chat kit | 14 | `kit_tool_row_running_dark` | T3 | 8.30 | 314 |  | 31.5 |
| 35 | chat kit | 14 | `kit_turn_default_text2_dark` | T3 | 6.77 | 273 |  | 22.5 |
| 36 | chat kit | 14 | `kit_turn_default_text2_1280x800_dark` | T3 | 6.16 | 210 |  | 42.8 |
| 37 | chat kit | 15 | `kit_work_line_default_text2_dark` | T3 | 4.51 |  |  | 2.0 |
| 38 | chat kit | 15 | `kit_work_line_default_text2_1280x800_dark` | T3 | 3.98 |  |  | 2.0 |
| 39 | chat kit | 14 | `kit_composer_voice_listening_light` | T2 | 3.64 | 4435 | 2.03 | 50.7 |
| 40 | chat kit | 14 | `kit_composer_idle_text_dark` | T2 | 0.00 | 70 | 0.83 | 6.9 |
| 41 | chat kit | 14 | `kit_composer_model_above_dark` | T2 | 0.00 | 535 |  | 4.6 |
| 42 | chat kit | 14 | `kit_queued_message_default_dark` | T3 | 0.00 | 123 |  | 7.1 |
| 43 | chat kit | 14 | `kit_queued_message_offline_dark` | T3 | 0.00 | 53 |  | 2.0 |
| 44 | chat kit | 14 | `kit_queued_receipt_checking_dark` | T3 | 0.00 | 74 |  | 2.2 |
| 45 | chat kit | 15 | `kit_work_line_done_expanded_dark` | T3 | 0.00 | 134 |  | 2.0 |
| 46 | chat kit | 15 | `kit_sense_ask_default_dark` | T2 | 0.00 |  |  | 2.8 |
| 47 | chat kit | 16 | `kit_composerstatusstrip_wrapped_text2_360x800_dark` | T2 | 50.56 | 8846 | 2.45 | 51.8 |
| 48 | sheets | 11 | `kit_sheet_with_icon_1280x800_light` | T2 | 40.79 | 62118 | 1.33 | 5.7 |
| 49 | sheets | 11 | `kit_sheet_text250_1280x800_light` | T2 | 32.08 | 46568 | 1.16 | 5.8 |
| 50 | sheets | 11 | `kit_sheet_with_icon_text2_1280x800_light` | T2 | 28.90 | 29616 |  | 5.8 |
| 51 | sheets | 12 | `kit_confirm_destructive_800x1280_light` | T2 | 23.69 | 33827 | 1.14 | 3.8 |
| 52 | sheets | 12 | `kit_confirm_destructive_text2_1280x800_light` | T2 | 21.04 | 18587 |  | 1.9 |
| 53 | chat kit | 16 | `kit_transcript_excerpt_loading_text2_dark` | T3 | 20.56 | 440 | 25.02 | 84.3 |
| 54 | sheets | 12 | `kit_confirm_destructive_text2_light` | T2 | 17.61 | 37368 | 0.00 | 105.0 |
| 55 | sheets | 11 | `kit_dialog_alert_light` | T2 | 13.76 | 6534 |  |  |
| 56 | chat kit | 16 | `kit_transcript_excerpt_loading_text2_1280x800_dark` | T3 | 13.37 | 399 | n/a | 14.0 |
| 57 | chat kit | 16 | `kit_transcript_excerpt_loading_dark` | T3 | 13.35 | 288 | n/a | 123.6 |
| 58 | chat kit | 17 | `kit_step_glow_running_t40_ar_text2_dark` | T4 | 6.86 | 360 |  | 3.9 |
| 59 | sheets | 11 | `kit_request_card_answered_dark` | T2 | 0.00 | 76 |  | 6.3 |
| 60 | chat kit | 16 | `kit_markdown_table_1280x800_dark` | T3 | 0.00 |  | 2.83 |  |
| 61 | chat kit | 16 | `kit_markdown_table_dark` | T3 | 0.00 |  | 2.83 |  |
| 62 | chat kit | 17 | `kit_step_glow_finish_tick_light` | T4 | 0.00 |  |  | 4.6 |
| 63 | chat kit | 17 | `kit_step_glow_first_t40_dark` | T4 | 0.00 |  |  | 3.7 |
| 64 | foundation | 01 | `kit_tappable_focused_1280x800_dark` | T1 | 38.20 | 29551 | 3.24 | 287.7 |
| 65 | settings | 44 | `settings_appearance_guided_solid_returning_dark` | T5 | 22.11 | 19489 | 0.00 | 141.5 |
| 66 | frame/nav | 13 | `kit_top_bar_default_text2_dark` | T2 | 18.28 | 460 | n/a | 101.7 |
| 67 | foundation | 01 | `kit_text_type_ar_dark` | T3 | 16.51 | 4451 |  | 95.7 |
| 68 | frame/nav | 13 | `kit_screen_keyboard_dark` | T2 | 14.89 | 19514 | 0.00 | 53.1 |
| 69 | frame/nav | 13 | `kit_screen_status_dark` | T2 | 14.64 | 209 | 0.00 | 106.0 |
| 70 | foundation | 01 | `kit_signal_chat_folded_light` | T2 | 14.40 | 9305 | 0.00 | 121.5 |

### RC1 gate G2 (daily path ≥ 90 % rendered and passing; all ≥ 75 %)

| surface | goldens | rendered | pass | pass / total | G2 |
|---|---:|---:|---:|---:|---|
| home (41) | 186 | 20 | 0 | 0.0% | FAIL |
| chat (42) | 280 | 8 | 0 | 0.0% | FAIL |
| composer (43) | 16 | 10 | 0 | 0.0% | FAIL |
| sheets (11, 12) | 284 | 82 | 14 | 4.9% | FAIL |
| settings hub (44 `settings_hub_*`) | 8 | 6 | 0 | 0.0% | FAIL |
| **daily path** | 774 | 126 | 14 | 1.8% | FAIL (≥ 90 %) |
| **all goldens** | 4525 | 2219 | 1040 | 23.0% | FAIL (≥ 75 %) |

## Next orders (short; one group per module set, no module in two groups)

The exact failing ids are in [goldens.tsv](scoreboard/2026-11-08/goldens.tsv). The Flutter spec lines from the
11-07 orders still apply where an item repeats.

- **Lead / `:ui:gate`.**
  - Land the KitCodeBlock superset, then rerun `kit_code_block_*` (44).
  - Add a "compile every desktopTest source set" step to each merge. Two merges in a row broke test compilation.
  - Write gate cases for orders 34/36/37 (223 goldens, none rendered; the school proof).
- **`:feature:chat` + `:feature:chat-composer`** (one group: the composer host lives in `:feature:chat`). RC1-critical;
  daily path 0 of 296.
  1. Gate cases for the other 272 chat goldens and 6 composer goldens.
  2. Turn grouping: work line, changed-file card, "All files", "Sonnet 4" footer, paragraph gap [chat spec C-001/C-003/C-011/C-025].
  3. Header subtitle Git chip ("shopfront · Git", `lib/ui/screens/chat/chat_top_bar.dart:180-190`). It also fixes the
     13-16 px shift on loading and load error and the `gap-step` 5 dp finding.
  4. Chip row "Ask first" + "Server default" over a composer whose idle trailing control is the mic.
  5. The "Prompt tools" sheet needs all eight rows (`chat_approval_plus`).
  6. The guard's `edge-tap "More for this reply"` (x 365-412, needs 4 dp).
- **`:feature:chats`** (home). Rows: the title first, then one meta line "status · project · age". Drop the avatar
  line and the extra "Show all / Projects / Activity / Usage" rows that the golden doesn't have (`chats_home_screen.dart:694-703`).
  Gate cases for the other 166 goldens.
- **`:feature:settings`.**
  - Rows 65 dp apart, not 56 (`kit_row.dart:121` roomy 64). If the screen already asks for roomy, this moves to `:ui:kit`.
  - The title edge: the title starts at x 56 where the rows start at 18.
  - Appearance (`settings_appearance_guided_*`): 6 themes plus "See all 31 themes", and the labelled 4×2 accent grid
    with Match. Waive the Glass card position (owner decision, standard §5.1).
  - Gate cases for the other 190 goldens.
- **`:ui:kit`** (837 of 1,948 pass).
  - Sheets, which every daily screen uses: `kit_sheet` 32 fail (the wide variant should be a centred dialog with its
    actions in one end-aligned row); `kit_confirm_sheet` 32 fail (560 dp cap, centred, drag handle, body-face hint,
    centred buttons).
  - `kit_top_bar` 22 fail and `kit_screen` 18 fail: the frame of every page.
  - `kit_icon_button` 29, `kit_notice` 29, `kit_surface` 18 (Arabic and 2.0 text), `kit_search_field` 18, `kit_frost` 18.
  - Gate cases for `kit_choice_list`, `kit_progress`, `kit_menu`, `kit_field`, `kit_term`, `kit_undo`,
    `kit_progress_row`, `kit_status_mark`, `kit_date_time_picker`, `kit_capability_explainer`, `kit_server_lane` and
    `kit_timeline_day` (about 300).
  - The 55 icon-label alignment rows: the icon centre sits 10 dp below the first line.
- **`:ui:kit-chat`** (95 of 643).
  - Fails: `kit_steer_box` 30, `kit_turn` 24, `kit_agent_strip` 24 (wide 25-26 %), `kit_message` 22, `kit_markdown` 14,
    `kit_diff_view` 10 (box 31-36 %), `kit_tool_row` 10, `kit_step_light` 10.
  - Gate cases for 333 more, `kit_request_card`/`_sheet` and the context ring and chart among them.
- **`:ui:kit-content`.** `kit_viewer` 47 of 48 fail, `kit_terminal_view` 27, `kit_log_panel` 23, `kit_image_gallery` 20,
  `kit_image_markup` 16.
- **Other screen modules** (each in one group): `:feature:servers` (1 of 155), `:feature:phone`, `:feature:agents`,
  `:feature:diagnostics`, `:feature:files`, `:feature:projects`, `:feature:usage`, `:feature:widget-panels`, plus
  `:ui:shell` (22 of 22 rendered pass; 28 goldens without a case) and `:feature:onboarding`. Gate cases first.

To reach RC1 G2, the daily path needs at least 697 of its 774 goldens passing. 648 of them have no gate case
today, so writing those cases is the first step on the critical path.
