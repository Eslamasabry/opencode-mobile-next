# Native UI rebuild scoreboard, 2026-11-07

How close native OpenCode Mobile is to the Flutter app, measured by the rebuild's own gate
([GOLDEN-STANDARD.md](GOLDEN-STANDARD.md) §8-§9) against Flutter's goldens at FLUTTER_REF `8302de537` (Glass:
`1bbdc3f9c`). The candidate is the integration head `native/screens-merge` at **`89ec49e55`** ("merge: s9-polish-kit").
This page reports what the gate found. It does not fix anything.

## Headline

| | goldens | pass | fail (rendered, measured) | fail (no gate case rendered it) | waived | pass rate |
|---|---:|---:|---:|---:|---:|---:|
| Strict: every order counted as merged | 4,525 | **339** | 445 | 3,739 | 2 | **7.5 %** |
| As gated: DEPENDS_ON waivers honoured per `~/wt/ui/state` | 4,525 | 339 | 299 (+146 deferred) | 3,739 | 2 | 7.5 % |
| Only goldens that rendered | 786 | 339 | 445 | – | 2 | **43.1 %** |

- **83 % of the goldens are not measured at all.** 3,739 of 4,525 have no gate case on the head, so nothing renders
  them. Measuring them is work in its own right; most next orders below start with it.
- **Every measured daily-path screen fails**: home (41), chat (42), settings (44) and servers (45) are at 0 passing,
  and the composer (43) and shell (53) have no gate cases at all. Only first-run setup (40: 28 of 36 rendered) and the
  foundation (01: 155 of 224 rendered) pass in quantity.
- The strict count is the honest one. All 146 deferred shots wait for orders (07, 08, 09, 12, 15) that are already merged
  into this head, and none of them passes when re-measured. Only `~/wt/ui/state/<NN>.done` is missing, so as gated they
  still show as DEFER. Matrix, cap and parity failures (X1, X2, P1; 1 to 76 per order) are not counted in the table.
- Chat, the owner's main worry: **1 of 751 chat goldens passes** (orders 14-17, 42, 43). 679 are not measured.

### How it was run (reproducible)

- PC worktree `/home/eslam/Storage2/wt/rb-score` on `native/rb-score` (from `native/screens-merge` 89ec49e55). VPS
  worktree `~/wt/rb-score`, detached at the same commit. Reference goldens came from `fetch_all_goldens.sh`
  (`FLUTTER_REPO=/home/eslam/Storage/Code/oc_app`; on the VPS `~/wt/flutter-ref` and `~/Storage/Code/oc_app` for
  Glass): 51 orders, 0 failed, 4,525 images. Manifests list 4,527 rows, but two order-44 rows share a file name.
- Every order ran in turn on the VPS (JDK 21 at `~/jdk21`, one Gradle slot under `~/.oc-rb-gradle.*.lock`) through
  `gate_order.sh --recheck <NN>` with `RB_DEV=1`. That runs fetch, the order's Gradle `desktopTest --tests
  '*.rb.o<NN>.*'` and `rb_gate.py evaluate`, without the static write-set check, which can't hold on a merged branch.
  When Gradle failed, the evaluation still ran on what rendered. Tracker posts were pointed at a dead port so the run did
  not overwrite the swarm's tracker. The driver is [scoreboard/tools/score.sh](scoreboard/tools/score.sh).
- Strict pass: each order's gate output was evaluated again on the PC with every order marked done
  ([scoreboard/tools/strict.sh](scoreboard/tools/strict.sh)).
- Three measurement-only changes were made in the VPS worktree and are **not committed**:
  1. `OC_LAYOUT_BASELINE=update`. Without it the new `LayoutGuard` (E02-S13) throws before the picture comparison, and
     all chat screen shots were lost. The findings it would have raised are recorded in
     [scoreboard/layout-guard-findings.diff](scoreboard/layout-guard-findings.diff) and listed under Next orders.
  2. `:ui:kit-chat` test sources did not compile on 89ec49e55. `PolishScenes.kt` and `polish/S8PolishTest.kt` use
     `KitTrayAction` and `KitTrayKind`, which don't exist. The two files were moved aside so kit-chat gates could run.
     The coordinator has since fixed this on `native/screens-merge20` (431fc8f32).
  3. Order 14 has no gate cases, so a probe rendered six of its scenes (`kit_markdown_default*`,
     `kit_message_default*`) with today's native parts. Source: [scoreboard/probe-o14-ScoreProbeGateTest.kt.txt](scoreboard/probe-o14-ScoreProbeGateTest.kt.txt).
     Treat these as indicative: native has no `KitMessage` part, so the probe stands in with `KitPromptBubble` +
     `KitMarkdown`.
- Per-golden results (strict and as gated, with box %, blob, ink ratio and line shift):
  [scoreboard/goldens-2026-11-07.tsv](scoreboard/goldens-2026-11-07.tsv). Use it for the exact failing ids.

Thresholds are `native/tool/design/gate_thresholds.tsv`: T1 1 %/16 px, T2 2 %/36, T3 3 %/49, T4 and T5 4 %/100, T6 8 %/400,
plus the ink band and the line shift. "ink n/a" in the ranking below means a line exists in one picture and not the
other, so the ratio is meaningless.

¹ "deferred as gated": the shot was DEFER (DEPENDS_ON) in the VPS run. In the strict columns it is counted as pass or
fail on its measurement.

## The 12 worst daily-path screens, side by side

Each image shows the Flutter golden on the left, the native render in the middle and a diff heat map on the right
(native in grey, differing pixels from yellow (small) to red (large)), with a label strip on top. I looked at each one.

| # | Screen (golden) | Image | Measured | What differs, as seen |
|---:|---|---|---|---|
| 1 | Chat, short turn (`chat_transcript_turn_dark`) | [chat_transcript_turn_dark.png](scoreboard/chat_transcript_turn_dark.png) | box 8.9 % (light 10.2 %), line moved 253 px | Native hangs the turn from the top; Flutter sits it on the composer (≈225 px). The work line, changed-file card, "All files" and the "Sonnet 4 · copy · ⋯" footer are missing. There is no gap between paragraphs. Inline code is full-size mono in a filled box, where Flutter has small mono with no fill. The composer has two rows (field, then + · Model · Send); Flutter has one row (+ · words · mic) under an "Ask first / Server default" chip row. The header adds a back arrow and a title chevron and drops "· Git". |
| 2 | Home, list (`chats_home_mixed_dark`) | [chats_home_mixed_dark.png](scoreboard/chats_home_mixed_dark.png) | box 10.8 %, line moved 263 px | Native adds a 56 dp row with a search button above the heading, so everything is 56 px lower. Rows are 73 px apart where Flutter's are 69 (+4 px a row). Native adds "Show all conversations", "Projects" and "Activity" rows that this golden doesn't have. "Sep 24" shows as "9d ago". |
| 3 | Chat, wide (`chat_transcript_turn_1280x800_light`) | [chat_transcript_turn_1280x800_light.png](scoreboard/chat_transcript_turn_1280x800_light.png) | blob 2,842, line moved 522 px | The conversation runs the full 1280 width. Flutter holds it to the 700 dp pane, centred. Plus every difference in #1. |
| 4 | Chat, find (`chat_find_open_dark`) | [chat_find_open_dark.png](scoreboard/chat_find_open_dark.png) | box 8.3-8.9 %, blob 7,497 | Matches are not highlighted in the reply, and the reply isn't framed as the current match. The find bar replaces the composer at the bottom; Flutter puts it above the chips and composer. The turn footer is missing. |
| 5 | Chat, loading (`chat_loading_dark`) | [chat_loading_dark.png](scoreboard/chat_loading_dark.png) | box 13.9 %, blob 13,496 | Native shows six list-row skeletons at the top. Flutter shows a two-bubble transcript skeleton at the bottom. Native keeps the composer, which Flutter hides while loading. The progress bar sits at a different place under the header. |
| 6 | Chat, load error (`chat_load_error_dark`) | [chat_load_error_dark.png](scoreboard/chat_load_error_dark.png) | box 18.0 %, blob 19,874 | Native shows a small state at the top: a 40 dp warning tile, the headline and a button. Flutter centres the page with the unplugged illustration, "Report a problem" and a "Details" fold, and shows no composer. |
| 7 | Home, project sheet (`chats_home_sheet_dark`) | [chats_home_sheet_dark.png](scoreboard/chats_home_sheet_dark.png) | box 12.8 % (light 23.4 %), blob 27,980 | The sheet sits 68 px lower. The selected row is a square band, not Flutter's inset rounded tile. Rows show generic folders where Flutter has per-project glyphs. The "Git" pill and the "· needs you" counts are missing, and so is the "Other folders" row. |
| 8 | Home, empty (`chats_home_empty_light`) | [chats_home_empty_light.png](scoreboard/chats_home_empty_light.png) | box 11.0 %, blob 19,154 | Native shows a top-aligned state with three option rows and a FAB. Flutter shows a centred "No conversations yet" over a full-width "New conversation" button and a "Connect a computer" link at the bottom. |
| 9 | Sheet (`kit_sheet_default_dark`) | [kit_sheet_default_dark.png](scoreboard/kit_sheet_default_dark.png) | box 12.6 %, blob 20,296 | The sheet is 48 px taller. The selected check is grey, not accent. The Arabic title is pushed to the end on a line of its own. "More languages" is accent and centred; Flutter has it text1 at the start. |
| 10 | Confirm sheet (`kit_confirm_destructive_light`) | [kit_confirm_destructive_light.png](scoreboard/kit_confirm_destructive_light.png) | box 39-87 %, blob up to 1.38 M | **Native draws nothing**: the gate case composes empty (the matrix says "empty composition"), so neither the scrim nor the sheet appears. |
| 11 | Settings hub (`settings_hub_loaded_dark`) | [settings_hub_loaded_dark.png](scoreboard/settings_hub_loaded_dark.png) | blob 203, line moved 149 px | The page title uses the large-title role; Flutter uses a 17 sp headline. Rows are 56 dp apart where Flutter's are 64-65 dp (−9 px a row, −34 px by "Help"). The divider inset is 56 where Flutter's is 68. |
| 12 | Settings › Notifications (`settings_notifications_loaded_dark`) | [settings_notifications_loaded_dark.png](scoreboard/settings_notifications_loaded_dark.png) | box 14.4 %, blob 2,085 | The same large title, and rows 56-60 dp where Flutter's are 65. Switches that are on but blocked by Android show green; Flutter shows them grey. Thumb sizes differ (on 24 / off 16 in Flutter, 20 native). The button is 12 px narrower. |

Also looked at: servers ([servers_servers_loaded_dark.png](scoreboard/servers_servers_loaded_dark.png)). Native shows a
back arrow and a large "Servers" title where Flutter has the OpenCode brand bar. Leading glyphs sit in 28 dp tiles where
Flutter draws them bare. The rows are 24 px lower.

Chat kit parts (orders 14, 15, 17), for the chat builders:
[kit_markdown_default_dark](scoreboard/kit_markdown_default_dark.png) (inline code, lost soft break, white quote, green link, compact code block without copy),
[kit_message_default_light](scoreboard/kit_message_default_light.png) (white rounder bubble wrapped to its words instead of the grey 85 % one; time missing),
[kit_work_line_default_text2_dark](scoreboard/kit_work_line_default_text2_dark.png) and [kit_step_timeline_collapsed_dark](scoreboard/kit_step_timeline_collapsed_dark.png)
(the gate cases use `Frame.Screen` where Flutter uses `kitGalleryPart`. The whole 14-16 px shift is the gate case's
fault; the parts themselves also put 9 px less space before and 7 px less after the line, and cut the label off sooner),
[kit_phrase_chips_default_dark](scoreboard/kit_phrase_chips_default_dark.png) (chips at the start, Flutter's centred: the row fills the width instead of wrapping its chips),
[kit_step_glow_done_dark](scoreboard/kit_step_glow_done_dark.png) (no "Ran 3 commands" header, rail or node tiles; flat bold rows).


## Scoreboard tables (strict)

### Per order

| order | goldens | pass | fail (rendered) | fail (no gate case rendered it) | waived | deferred as gated¹ | pass / total | pass / rendered |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 01 | 226 | 155 | 67 | 2 | 2 | 8 | 68.6% | 69.2% |
| 02 | 10 | 0 | 10 | 0 | 0 | 8 | 0.0% | 0.0% |
| 05 | 300 | 53 | 85 | 162 | 0 | 0 | 17.7% | 38.4% |
| 06 | 131 | 68 | 63 | 0 | 0 | 56 | 51.9% | 51.9% |
| 07 | 38 | 4 | 0 | 34 | 0 | 0 | 10.5% | 100.0% |
| 08 | 306 | 0 | 16 | 290 | 0 | 0 | 0.0% | 0.0% |
| 09 | 81 | 1 | 1 | 79 | 0 | 0 | 1.2% | 50.0% |
| 10 | 20 | 0 | 0 | 20 | 0 | 0 | 0.0% | 0.0% |
| 11 | 242 | 2 | 38 | 202 | 0 | 34 | 0.8% | 5.0% |
| 12 | 42 | 0 | 18 | 24 | 0 | 0 | 0.0% | 0.0% |
| 13 | 84 | 2 | 4 | 78 | 0 | 0 | 2.4% | 33.3% |
| 14 | 286 | 0 | 12 | 274 | 0 | 0 | 0.0% | 0.0% |
| 15 | 113 | 1 | 9 | 103 | 0 | 0 | 0.9% | 10.0% |
| 16 | 16 | 0 | 0 | 16 | 0 | 0 | 0.0% | 0.0% |
| 17 | 40 | 0 | 40 | 0 | 0 | 40 | 0.0% | 0.0% |
| 18 | 178 | 0 | 0 | 178 | 0 | 0 | 0.0% | 0.0% |
| 20 | 230 | 0 | 0 | 230 | 0 | 0 | 0.0% | 0.0% |
| 21 | 173 | 4 | 0 | 169 | 0 | 0 | 2.3% | 100.0% |
| 22 | 102 | 4 | 2 | 96 | 0 | 0 | 3.9% | 66.7% |
| 23 | 74 | 0 | 0 | 74 | 0 | 0 | 0.0% | 0.0% |
| 24 | 60 | 17 | 9 | 34 | 0 | 0 | 28.3% | 65.4% |
| 25 | 14 | 0 | 0 | 14 | 0 | 0 | 0.0% | 0.0% |
| 29 | 22 | 0 | 0 | 22 | 0 | 0 | 0.0% | 0.0% |
| 30 | 8 | 0 | 0 | 8 | 0 | 0 | 0.0% | 0.0% |
| 31 | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 32 | 3 | 0 | 0 | 3 | 0 | 0 | 0.0% | 0.0% |
| 33 | 3 | 0 | 0 | 3 | 0 | 0 | 0.0% | 0.0% |
| 34 | 64 | 0 | 0 | 64 | 0 | 0 | 0.0% | 0.0% |
| 36 | 95 | 0 | 0 | 95 | 0 | 0 | 0.0% | 0.0% |
| 37 | 64 | 0 | 0 | 64 | 0 | 0 | 0.0% | 0.0% |
| 40 | 58 | 28 | 8 | 22 | 0 | 0 | 48.3% | 77.8% |
| 41 | 186 | 0 | 10 | 176 | 0 | 0 | 0.0% | 0.0% |
| 42 | 280 | 0 | 10 | 270 | 0 | 0 | 0.0% | 0.0% |
| 43 | 16 | 0 | 0 | 16 | 0 | 0 | 0.0% | 0.0% |
| 44 | 215 | 0 | 7 | 208 | 0 | 0 | 0.0% | 0.0% |
| 45 | 155 | 0 | 14 | 141 | 0 | 0 | 0.0% | 0.0% |
| 46 | 67 | 0 | 0 | 67 | 0 | 0 | 0.0% | 0.0% |
| 47 | 52 | 0 | 0 | 52 | 0 | 0 | 0.0% | 0.0% |
| 48 | 166 | 0 | 4 | 162 | 0 | 0 | 0.0% | 0.0% |
| 49 | 104 | 0 | 6 | 98 | 0 | 0 | 0.0% | 0.0% |
| 50 | 25 | 0 | 0 | 25 | 0 | 0 | 0.0% | 0.0% |
| 51 | 108 | 0 | 0 | 108 | 0 | 0 | 0.0% | 0.0% |
| 52 | 12 | 0 | 12 | 0 | 0 | 0 | 0.0% | 0.0% |
| 53 | 50 | 0 | 0 | 50 | 0 | 0 | 0.0% | 0.0% |
| **all orders** | 4525 | 339 | 445 | 3739 | 2 | 146 | 7.5% | 43.1% |

### Per Gradle module

| module | goldens | pass | fail (rendered) | fail (no gate case rendered it) | waived | deferred as gated¹ | pass / total | pass / rendered |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| `:ui:kit` | 1948 | 223 | 304 | 1419 | 2 | 100 | 11.4% | 42.2% |
| `:ui:kit-chat` | 643 | 44 | 62 | 537 | 0 | 40 | 6.8% | 41.5% |
| `:feature:chat` | 280 | 0 | 10 | 270 | 0 | 0 | 0.0% | 0.0% |
| `:ui:gate` | 223 | 0 | 0 | 223 | 0 | 0 | 0.0% | 0.0% |
| `:ui:kit-content` | 217 | 44 | 8 | 165 | 0 | 6 | 20.3% | 84.6% |
| `:feature:settings` | 215 | 0 | 7 | 208 | 0 | 0 | 0.0% | 0.0% |
| `:feature:chats` | 186 | 0 | 10 | 176 | 0 | 0 | 0.0% | 0.0% |
| `:feature:phone` | 166 | 0 | 4 | 162 | 0 | 0 | 0.0% | 0.0% |
| `:feature:servers` | 155 | 0 | 14 | 141 | 0 | 0 | 0.0% | 0.0% |
| `:feature:diagnostics` | 108 | 0 | 0 | 108 | 0 | 0 | 0.0% | 0.0% |
| `:feature:agents` | 104 | 0 | 6 | 98 | 0 | 0 | 0.0% | 0.0% |
| `:feature:files` | 67 | 0 | 0 | 67 | 0 | 0 | 0.0% | 0.0% |
| `:feature:onboarding` | 58 | 28 | 8 | 22 | 0 | 0 | 48.3% | 77.8% |
| `:feature:projects` | 52 | 0 | 0 | 52 | 0 | 0 | 0.0% | 0.0% |
| `:ui:shell` | 50 | 0 | 0 | 50 | 0 | 0 | 0.0% | 0.0% |
| `:feature:usage` | 25 | 0 | 0 | 25 | 0 | 0 | 0.0% | 0.0% |
| `:feature:chat-composer` | 16 | 0 | 0 | 16 | 0 | 0 | 0.0% | 0.0% |
| `:feature:widget-panels` | 12 | 0 | 12 | 0 | 0 | 0 | 0.0% | 0.0% |

### Per part / screen gallery

| order · tracker id | goldens | pass | fail (rendered) | fail (no gate case rendered it) | waived | deferred as gated¹ | pass / total | pass / rendered |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 01 · `part-kit_icon` | 32 | 16 | 16 | 0 | 0 | 0 | 50.0% | 50.0% |
| 01 · `part-kit_motion` | 54 | 54 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 01 · `part-kit_signal` | 8 | 0 | 8 | 0 | 0 | 8 | 0.0% | 0.0% |
| 01 · `part-kit_since` | 22 | 18 | 4 | 0 | 0 | 0 | 81.8% | 81.8% |
| 01 · `part-kit_surface` | 34 | 15 | 19 | 0 | 0 | 0 | 44.1% | 44.1% |
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
| 05 · `part-kit_segmented` | 30 | 4 | 26 | 0 | 0 | 0 | 13.3% | 13.3% |
| 05 · `part-kit_status_mark` | 24 | 0 | 0 | 24 | 0 | 0 | 0.0% | 0.0% |
| 05 · `part-kit_swatch` | 30 | 0 | 30 | 0 | 0 | 0 | 0.0% | 0.0% |
| 05 · `part-kit_term` | 26 | 0 | 0 | 26 | 0 | 0 | 0.0% | 0.0% |
| 06 · `part-kit_chip` | 16 | 16 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 06 · `part-kit_divider` | 22 | 5 | 17 | 0 | 0 | 17 | 22.7% | 22.7% |
| 06 · `part-kit_filter_chip` | 6 | 6 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 06 · `part-kit_filter_chips` | 6 | 6 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 06 · `part-kit_key_value` | 7 | 5 | 2 | 0 | 0 | 2 | 71.4% | 71.4% |
| 06 · `part-kit_level_meter` | 22 | 22 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 06 · `part-kit_needs_you` | 16 | 4 | 12 | 0 | 0 | 12 | 25.0% | 25.0% |
| 06 · `part-kit_qr` | 10 | 2 | 8 | 0 | 0 | 6 | 20.0% | 20.0% |
| 06 · `part-kit_search_field` | 20 | 2 | 18 | 0 | 0 | 13 | 10.0% | 10.0% |
| 06 · `part-kit_section_label` | 6 | 0 | 6 | 0 | 0 | 6 | 0.0% | 0.0% |
| 07 · `part-kit_buttons` | 32 | 2 | 0 | 30 | 0 | 0 | 6.2% | 100.0% |
| 07 · `part-kit_status_tag` | 6 | 2 | 0 | 4 | 0 | 0 | 33.3% | 100.0% |
| 08 · `part-kit_action_stack` | 24 | 0 | 0 | 24 | 0 | 0 | 0.0% | 0.0% |
| 08 · `part-kit_checklist` | 24 | 0 | 0 | 24 | 0 | 0 | 0.0% | 0.0% |
| 08 · `part-kit_code_block` | 44 | 0 | 0 | 44 | 0 | 0 | 0.0% | 0.0% |
| 08 · `part-kit_log_panel` | 26 | 0 | 0 | 26 | 0 | 0 | 0.0% | 0.0% |
| 08 · `part-kit_notice` | 52 | 0 | 12 | 40 | 0 | 0 | 0.0% | 0.0% |
| 08 · `part-kit_receipt` | 26 | 0 | 0 | 26 | 0 | 0 | 0.0% | 0.0% |
| 08 · `part-kit_row` | 26 | 0 | 4 | 22 | 0 | 0 | 0.0% | 0.0% |
| 08 · `part-kit_state_view` | 30 | 0 | 0 | 30 | 0 | 0 | 0.0% | 0.0% |
| 08 · `part-kit_status_line` | 30 | 0 | 0 | 30 | 0 | 0 | 0.0% | 0.0% |
| 08 · `part-kit_task_card` | 24 | 0 | 0 | 24 | 0 | 0 | 0.0% | 0.0% |
| 09 · `part-kit_consequences` | 8 | 0 | 0 | 8 | 0 | 0 | 0.0% | 0.0% |
| 09 · `part-kit_details_fold` | 20 | 1 | 1 | 18 | 0 | 0 | 5.0% | 50.0% |
| 09 · `part-kit_feed_item` | 10 | 0 | 0 | 10 | 0 | 0 | 0.0% | 0.0% |
| 09 · `part-kit_group_note` | 16 | 0 | 0 | 16 | 0 | 0 | 0.0% | 0.0% |
| 09 · `part-kit_last_known` | 8 | 0 | 0 | 8 | 0 | 0 | 0.0% | 0.0% |
| 09 · `part-kit_mini_table` | 7 | 0 | 0 | 7 | 0 | 0 | 0.0% | 0.0% |
| 09 · `part-kit_sliver_row_group` | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 09 · `part-kit_swipe_action` | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 10 · `part-kit_row_parts` | 20 | 0 | 0 | 20 | 0 | 0 | 0.0% | 0.0% |
| 11 · `part-kit_capability_explainer` | 20 | 0 | 0 | 20 | 0 | 0 | 0.0% | 0.0% |
| 11 · `part-kit_context_sheet` | 12 | 0 | 0 | 12 | 0 | 0 | 0.0% | 0.0% |
| 11 · `part-kit_date_time_picker` | 24 | 0 | 0 | 24 | 0 | 0 | 0.0% | 0.0% |
| 11 · `part-kit_dialog` | 24 | 0 | 2 | 22 | 0 | 0 | 0.0% | 0.0% |
| 11 · `part-kit_jump_pill` | 16 | 0 | 0 | 16 | 0 | 0 | 0.0% | 0.0% |
| 11 · `part-kit_menu` | 30 | 0 | 0 | 30 | 0 | 0 | 0.0% | 0.0% |
| 11 · `part-kit_request_card` | 30 | 0 | 2 | 28 | 0 | 0 | 0.0% | 0.0% |
| 11 · `part-kit_request_sheet` | 24 | 0 | 0 | 24 | 0 | 0 | 0.0% | 0.0% |
| 11 · `part-kit_sheet` | 36 | 2 | 34 | 0 | 0 | 34 | 5.6% | 5.6% |
| 11 · `part-kit_undo` | 26 | 0 | 0 | 26 | 0 | 0 | 0.0% | 0.0% |
| 12 · `part-kit_confirm_sheet` | 32 | 0 | 18 | 14 | 0 | 0 | 0.0% | 0.0% |
| 12 · `part-kit_floating_action` | 10 | 0 | 0 | 10 | 0 | 0 | 0.0% | 0.0% |
| 13 · `part-kit_arrival` | 8 | 0 | 0 | 8 | 0 | 0 | 0.0% | 0.0% |
| 13 · `part-kit_breadcrumb` | 16 | 0 | 4 | 12 | 0 | 0 | 0.0% | 0.0% |
| 13 · `part-kit_nav` | 10 | 2 | 0 | 8 | 0 | 0 | 20.0% | 100.0% |
| 13 · `part-kit_page_route` | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 13 · `part-kit_screen` | 18 | 0 | 0 | 18 | 0 | 0 | 0.0% | 0.0% |
| 13 · `part-kit_top_bar` | 26 | 0 | 0 | 26 | 0 | 0 | 0.0% | 0.0% |
| 14 · `part-kit_agent_strip` | 24 | 0 | 0 | 24 | 0 | 0 | 0.0% | 0.0% |
| 14 · `part-kit_ask_line` | 30 | 0 | 0 | 30 | 0 | 0 | 0.0% | 0.0% |
| 14 · `part-kit_composer` | 32 | 0 | 0 | 32 | 0 | 0 | 0.0% | 0.0% |
| 14 · `part-kit_composer_chips` | 24 | 0 | 0 | 24 | 0 | 0 | 0.0% | 0.0% |
| 14 · `part-kit_diff_view` | 36 | 0 | 0 | 36 | 0 | 0 | 0.0% | 0.0% |
| 14 · `part-kit_markdown` | 30 | 0 | 6 | 24 | 0 | 0 | 0.0% | 0.0% |
| 14 · `part-kit_message` | 28 | 0 | 6 | 22 | 0 | 0 | 0.0% | 0.0% |
| 14 · `part-kit_queued_message` | 28 | 0 | 0 | 28 | 0 | 0 | 0.0% | 0.0% |
| 14 · `part-kit_tool_row` | 26 | 0 | 0 | 26 | 0 | 0 | 0.0% | 0.0% |
| 14 · `part-kit_turn` | 28 | 0 | 0 | 28 | 0 | 0 | 0.0% | 0.0% |
| 15 · `part-kit_background_work_line` | 10 | 0 | 0 | 10 | 0 | 0 | 0.0% | 0.0% |
| 15 · `part-kit_composer_glow` | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 15 · `part-kit_context_chart` | 10 | 0 | 0 | 10 | 0 | 0 | 0.0% | 0.0% |
| 15 · `part-kit_context_ring` | 18 | 0 | 0 | 18 | 0 | 0 | 0.0% | 0.0% |
| 15 · `part-kit_phrase_chips` | 8 | 0 | 2 | 6 | 0 | 0 | 0.0% | 0.0% |
| 15 · `part-kit_project_file_list` | 10 | 0 | 0 | 10 | 0 | 0 | 0.0% | 0.0% |
| 15 · `part-kit_sense_ask` | 11 | 1 | 3 | 7 | 0 | 0 | 9.1% | 25.0% |
| 15 · `part-kit_step_timeline` | 16 | 0 | 2 | 14 | 0 | 0 | 0.0% | 0.0% |
| 15 · `part-kit_stream_caret` | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 15 · `part-kit_work_line` | 18 | 0 | 2 | 16 | 0 | 0 | 0.0% | 0.0% |
| 16 · `part-kit_composer_status_strip` | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 16 · `part-kit_markdown_table` | 4 | 0 | 0 | 4 | 0 | 0 | 0.0% | 0.0% |
| 16 · `part-kit_transcript_excerpt` | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 17 · `part-kit_step_light` | 40 | 0 | 40 | 0 | 0 | 40 | 0.0% | 0.0% |
| 18 · `part-kit_board_lane` | 16 | 0 | 0 | 16 | 0 | 0 | 0.0% | 0.0% |
| 18 · `part-kit_chart` | 15 | 0 | 0 | 15 | 0 | 0 | 0.0% | 0.0% |
| 18 · `part-kit_image_gallery` | 20 | 0 | 0 | 20 | 0 | 0 | 0.0% | 0.0% |
| 18 · `part-kit_image_markup` | 16 | 0 | 0 | 16 | 0 | 0 | 0.0% | 0.0% |
| 18 · `part-kit_scanner` | 13 | 0 | 0 | 13 | 0 | 0 | 0.0% | 0.0% |
| 18 · `part-kit_terminal_view` | 34 | 0 | 0 | 34 | 0 | 0 | 0.0% | 0.0% |
| 18 · `part-kit_viewer` | 48 | 0 | 0 | 48 | 0 | 0 | 0.0% | 0.0% |
| 18 · `part-kit_work_graph` | 16 | 0 | 0 | 16 | 0 | 0 | 0.0% | 0.0% |
| 20 · `part-kit_digest` | 22 | 0 | 0 | 22 | 0 | 0 | 0.0% | 0.0% |
| 20 · `part-kit_findings_card` | 22 | 0 | 0 | 22 | 0 | 0 | 0.0% | 0.0% |
| 20 · `part-kit_merge_queue` | 22 | 0 | 0 | 22 | 0 | 0 | 0.0% | 0.0% |
| 20 · `part-kit_milestone_row` | 22 | 0 | 0 | 22 | 0 | 0 | 0.0% | 0.0% |
| 20 · `part-kit_phase_card` | 22 | 0 | 0 | 22 | 0 | 0 | 0.0% | 0.0% |
| 20 · `part-kit_plan_card` | 22 | 0 | 0 | 22 | 0 | 0 | 0.0% | 0.0% |
| 20 · `part-kit_project_row` | 22 | 0 | 0 | 22 | 0 | 0 | 0.0% | 0.0% |
| 20 · `part-kit_promote_card` | 22 | 0 | 0 | 22 | 0 | 0 | 0.0% | 0.0% |
| 20 · `part-kit_server_trust` | 24 | 0 | 0 | 24 | 0 | 0 | 0.0% | 0.0% |
| 20 · `part-kit_steer_box` | 30 | 0 | 0 | 30 | 0 | 0 | 0.0% | 0.0% |
| 21 · `part-kit_agent_card` | 15 | 0 | 0 | 15 | 0 | 0 | 0.0% | 0.0% |
| 21 · `part-kit_ask_field` | 16 | 0 | 0 | 16 | 0 | 0 | 0.0% | 0.0% |
| 21 · `part-kit_continued_line` | 16 | 4 | 0 | 12 | 0 | 0 | 25.0% | 100.0% |
| 21 · `part-kit_fact_row` | 16 | 0 | 0 | 16 | 0 | 0 | 0.0% | 0.0% |
| 21 · `part-kit_frost` | 20 | 0 | 0 | 20 | 0 | 0 | 0.0% | 0.0% |
| 21 · `part-kit_handoff_record` | 16 | 0 | 0 | 16 | 0 | 0 | 0.0% | 0.0% |
| 21 · `part-kit_one_time_code` | 14 | 0 | 0 | 14 | 0 | 0 | 0.0% | 0.0% |
| 21 · `part-kit_server_lane` | 22 | 0 | 0 | 22 | 0 | 0 | 0.0% | 0.0% |
| 21 · `part-kit_timeline_day` | 22 | 0 | 0 | 22 | 0 | 0 | 0.0% | 0.0% |
| 21 · `part-kit_word_text` | 16 | 0 | 0 | 16 | 0 | 0 | 0.0% | 0.0% |
| 22 · `part-kit_carousel` | 10 | 0 | 2 | 8 | 0 | 0 | 0.0% | 0.0% |
| 22 · `part-kit_choice_cards` | 12 | 0 | 0 | 12 | 0 | 0 | 0.0% | 0.0% |
| 22 · `part-kit_choice_tiles` | 12 | 0 | 0 | 12 | 0 | 0 | 0.0% | 0.0% |
| 22 · `part-kit_fine_tune_panel` | 10 | 0 | 0 | 10 | 0 | 0 | 0.0% | 0.0% |
| 22 · `part-kit_guide` | 10 | 0 | 0 | 10 | 0 | 0 | 0.0% | 0.0% |
| 22 · `part-kit_live_look_preview` | 10 | 4 | 0 | 6 | 0 | 0 | 40.0% | 100.0% |
| 22 · `part-kit_option_card` | 8 | 0 | 0 | 8 | 0 | 0 | 0.0% | 0.0% |
| 22 · `part-kit_page_heading` | 8 | 0 | 0 | 8 | 0 | 0 | 0.0% | 0.0% |
| 22 · `part-kit_progress_ring` | 12 | 0 | 0 | 12 | 0 | 0 | 0.0% | 0.0% |
| 22 · `part-kit_step_slider` | 10 | 0 | 0 | 10 | 0 | 0 | 0.0% | 0.0% |
| 23 · `part-kit_agent_choice` | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 23 · `part-kit_agent_grid` | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 23 · `part-kit_agent_line` | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 23 · `part-kit_presence` | 8 | 0 | 0 | 8 | 0 | 0 | 0.0% | 0.0% |
| 23 · `part-kit_spec_block` | 8 | 0 | 0 | 8 | 0 | 0 | 0.0% | 0.0% |
| 23 · `part-kit_start_box` | 8 | 0 | 0 | 8 | 0 | 0 | 0.0% | 0.0% |
| 23 · `part-kit_status_answer` | 8 | 0 | 0 | 8 | 0 | 0 | 0.0% | 0.0% |
| 23 · `part-kit_web_preview` | 8 | 0 | 0 | 8 | 0 | 0 | 0.0% | 0.0% |
| 23 · `part-kit_wheel` | 8 | 0 | 0 | 8 | 0 | 0 | 0.0% | 0.0% |
| 23 · `part-kit_window_row` | 8 | 0 | 0 | 8 | 0 | 0 | 0.0% | 0.0% |
| 24 · `part-kit_error_fallback` | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 24 · `part-kit_floating_panel` | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 24 · `part-kit_hero_question` | 6 | 2 | 2 | 2 | 0 | 0 | 33.3% | 50.0% |
| 24 · `part-kit_ink_layer` | 6 | 1 | 5 | 0 | 0 | 0 | 16.7% | 16.7% |
| 24 · `part-kit_live_background` | 6 | 6 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 24 · `part-kit_plan_block` | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 24 · `part-kit_scroll_fade` | 6 | 6 | 0 | 0 | 0 | 0 | 100.0% | 100.0% |
| 24 · `part-kit_session_tabs` | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 24 · `part-kit_task_chip` | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 24 · `part-kit_tip` | 6 | 2 | 2 | 2 | 0 | 0 | 33.3% | 50.0% |
| 25 · `part-kit_frost_header` | 2 | 0 | 0 | 2 | 0 | 0 | 0.0% | 0.0% |
| 25 · `part-kit_widget_preview` | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 25 · `part-kit_widget_style_form` | 6 | 0 | 0 | 6 | 0 | 0 | 0.0% | 0.0% |
| 29 · `gallery-o29` | 22 | 0 | 0 | 22 | 0 | 0 | 0.0% | 0.0% |
| 30 · `gallery-o30` | 8 | 0 | 0 | 8 | 0 | 0 | 0.0% | 0.0% |
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
| 41 · `gallery-o41` | 186 | 0 | 10 | 176 | 0 | 0 | 0.0% | 0.0% |
| 42 · `gallery-o42` | 280 | 0 | 10 | 270 | 0 | 0 | 0.0% | 0.0% |
| 43 · `gallery-o43` | 16 | 0 | 0 | 16 | 0 | 0 | 0.0% | 0.0% |
| 44 · `gallery-o44` | 215 | 0 | 7 | 208 | 0 | 0 | 0.0% | 0.0% |
| 45 · `gallery-o45` | 155 | 0 | 14 | 141 | 0 | 0 | 0.0% | 0.0% |
| 46 · `gallery-o46` | 67 | 0 | 0 | 67 | 0 | 0 | 0.0% | 0.0% |
| 47 · `gallery-o47` | 52 | 0 | 0 | 52 | 0 | 0 | 0.0% | 0.0% |
| 48 · `gallery-o48` | 166 | 0 | 4 | 162 | 0 | 0 | 0.0% | 0.0% |
| 49 · `gallery-o49` | 104 | 0 | 6 | 98 | 0 | 0 | 0.0% | 0.0% |
| 50 · `gallery-o50` | 25 | 0 | 0 | 25 | 0 | 0 | 0.0% | 0.0% |
| 51 · `gallery-o51` | 108 | 0 | 0 | 108 | 0 | 0 | 0.0% | 0.0% |
| 52 · `gallery-o52` | 12 | 0 | 12 | 0 | 0 | 0 | 0.0% | 0.0% |
| 53 · `gallery-o53` | 50 | 0 | 0 | 50 | 0 | 0 | 0.0% | 0.0% |

### Worst measured diffs, ranked by daily-path visibility

Rendered failing shots only. Order: visibility weight of the surface (home, chat, composer, sheets, settings first), then box %. One line per golden stem (the worse mode), at most three per part or gallery.

| rank | surface | order | golden (worse mode) | tier | box % | blob px | ink ratio | line moved px |
|---:|---|---|---|---|---:|---:|---:|---:|
| 1 | home | 41 | `chats_home_sheet_light` | T5 | 23.39 | 27980 | 3.43 | 26.5 |
| 2 | chat | 42 | `chat_load_error_dark` | T5 | 17.98 | 19874 | n/a | 147.0 |
| 3 | chat | 42 | `chat_loading_dark` | T5 | 13.88 | 13496 | 0.00 | 132.0 |
| 4 | home | 41 | `chats_home_empty_dark` | T5 | 11.94 | 19432 | 0.00 | 263.3 |
| 5 | home | 41 | `chats_home_mixed_dark` | T5 | 10.83 | 3445 | n/a | 263.3 |
| 6 | chat | 42 | `chat_transcript_turn_light` | T5 | 10.22 | 4820 | n/a | 253.4 |
| 7 | chat kit | 15 | `kit_phrase_chips_default_dark` | T2 | 42.56 | 1890 |  | 59.0 |
| 8 | chat kit | 14 | `kit_message_default_text2_light` | T3 | 38.21 | 54570 | n/a | 59.2 |
| 9 | chat kit | 14 | `kit_message_default_light` | T3 | 35.89 | 15528 | 0.00 | 131.2 |
| 10 | chat kit | 14 | `kit_message_default_1280x800_light` | T3 | 31.29 | 15428 | 0.00 | 19.8 |
| 11 | chat kit | 15 | `kit_work_line_default_text2_dark` | T3 | 18.85 | 226 | 0.84 | 14.0 |
| 12 | chat kit | 15 | `kit_step_timeline_collapsed_dark` | T3 | 18.03 | 261 | 0.00 | 87.0 |
| 13 | chat kit | 15 | `kit_sense_ask_default_1280x800_dark` | T2 | 16.84 | 7462 | 1.24 | 144.0 |
| 14 | chat kit | 14 | `kit_markdown_default_text2_dark` | T3 | 13.32 | 1339 | 3.65 | 134.8 |
| 15 | chat kit | 14 | `kit_markdown_default_dark` | T3 | 12.59 | 1059 | 0.00 | 119.6 |
| 16 | chat kit | 14 | `kit_markdown_default_1280x800_dark` | T3 | 10.37 | 993 | 0.00 | 96.5 |
| 17 | chat kit | 15 | `kit_sense_ask_default_dark` | T2 | 0.00 |  |  | 2.8 |
| 18 | sheets | 12 | `kit_confirm_destructive_1600x1000_light` | T2 | 86.88 | 1381609 | 7.05 |  |
| 19 | sheets | 12 | `kit_confirm_destructive_1280x800_light` | T2 | 79.51 | 805609 | 4.51 |  |
| 20 | sheets | 12 | `kit_confirm_destructive_ar_1280x800_light` | T2 | 72.46 | 730729 | 3.39 | 2.5 |
| 21 | sheets | 11 | `kit_sheet_with_icon_1280x800_light` | T2 | 42.76 | 83531 | 1.38 | 8.5 |
| 22 | sheets | 11 | `kit_sheet_oneclose_1280x800_light` | T2 | 29.01 | 32056 |  | 9.7 |
| 23 | sheets | 11 | `kit_sheet_with_icon_text2_1280x800_light` | T2 | 28.51 | 28866 |  | 8.9 |
| 24 | chat kit | 17 | `kit_step_glow_done_dark` | T4 | 17.32 | 1156 |  | 15.5 |
| 25 | chat kit | 17 | `kit_step_glow_finish_spring_dark` | T4 | 17.32 | 1156 |  | 15.5 |
| 26 | chat kit | 17 | `kit_step_glow_finish_tick_dark` | T4 | 17.32 | 1156 |  | 15.5 |
| 27 | sheets | 11 | `kit_dialog_alert_light` | T2 | 13.76 | 6534 |  |  |
| 28 | sheets | 11 | `kit_request_card_answered_dark` | T2 | 0.00 | 81 | 0.84 | 15.6 |
| 29 | foundation | 01 | `kit_tappable_focused_1280x800_dark` | T1 | 38.20 | 29551 | 3.24 | 287.7 |
| 30 | foundation | 01 | `kit_text_type_ar_dark` | T3 | 16.51 | 4451 |  | 95.7 |
| 31 | foundation | 01 | `kit_signal_chat_folded_light` | T2 | 14.40 | 9305 | 0.00 | 121.5 |
| 32 | settings | 44 | `settings_notifications_loaded_dark` | T5 | 14.38 | 2085 | 0.00 | 174.6 |
| 33 | foundation | 01 | `kit_signal_chat_light` | T2 | 13.47 | 9305 | 0.00 | 131.5 |
| 34 | foundation | 01 | `kit_signal_home_light` | T2 | 12.88 | 11258 | 0.00 | 130.7 |
| 35 | frame/nav | 13 | `kit_breadcrumb_default_1280x800_dark` | T2 | 12.36 | 87 |  | 15.3 |
| 36 | frame/nav | 13 | `kit_breadcrumb_default_dark` | T2 | 12.36 | 87 |  | 15.3 |
| 37 | foundation | 01 | `kit_text_type_ar_1280x800_dark` | T3 | 11.30 | 7478 |  | 199.2 |
| 38 | settings | 44 | `settings_privacy_loaded_dark` | T5 | 10.91 | 312 | 0.18 | 126.2 |
| 39 | foundation | 01 | `kit_since_minutes_ar_1280x800_light` | T2 | 6.82 |  |  | 2.0 |
| 40 | foundation | 01 | `kit_since_minutes_ar_light` | T2 | 6.82 |  |  | 2.0 |
| 41 | foundation | 01 | `kit_surface_panel_ar_1280x800_dark` | T1 | 5.91 | 130 |  | 4.3 |
| 42 | foundation | 01 | `kit_surface_panel_ar_dark` | T1 | 5.91 | 130 |  | 4.3 |
| 43 | foundation | 01 | `kit_icon_sizes_ar_1280x800_dark` | T1 | 4.78 | 90 | 0.89 | 4.9 |
| 44 | foundation | 01 | `kit_icon_sizes_ar_dark` | T1 | 4.78 | 90 | 0.89 | 4.9 |
| 45 | settings | 44 | `settings_privacy_loaded_1280x800_dark` | T5 | 4.57 | 324 | 0.00 | 46.5 |
| 46 | foundation | 01 | `kit_surface_panel_text2_dark` | T1 | 4.23 | 70 |  |  |
| 47 | foundation | 01 | `kit_text_type_text2_1280x800_dark` | T3 | 4.02 | 271 |  | 3.4 |
| 48 | foundation | 01 | `kit_tappable_focused_dark` | T1 | 3.48 | 816 | 1.73 | 10.9 |
| 49 | foundation | 01 | `kit_icon_status_ar_1280x800_dark` | T1 | 2.62 | 55 |  | 4.8 |
| 50 | foundation | 01 | `kit_tappable_enabled_text2_light` | T1 | 0.00 |  |  | 3.6 |
| 51 | controls | 05 | `kit_swatch_preview_graphite_text2_dark` | T2 | 32.55 | 11556 | 0.00 | 54.3 |
| 52 | controls | 06 | `kit_search_field_nomatch_dark` | T2 | 31.22 | 14658 | 2.11 | 160.1 |
| 53 | controls | 05 | `kit_swatch_preview_graphite_text2_1280x800_dark` | T2 | 30.02 | 21258 | 0.00 | 108.9 |
| 54 | controls | 05 | `kit_swatch_grid_text2_dark` | T2 | 27.54 | 10172 | 0.00 | 75.4 |
| 55 | controls | 06 | `kit_needs_you_row_dark` | T2 | 19.10 | 255 | 0.00 | 71.8 |
| 56 | controls | 05 | `kit_segmented_default_text2_1280x800_dark` | T2 | 18.40 | 163 |  | 8.0 |
| 57 | controls | 05 | `kit_segmented_default_text2_dark` | T2 | 18.40 | 163 |  | 8.0 |
| 58 | controls | 05 | `kit_segmented_stacked_dark` | T2 | 15.22 | 164 |  | 8.7 |
| 59 | rows | 08 | `kit_notice_default_360x800_dark` | T2 | 14.20 | 299 | 0.81 | 15.1 |
| 60 | rows | 08 | `kit_notice_default_dark` | T2 | 12.95 | 299 | 1.11 | 8.5 |
| 61 | controls | 06 | `kit_needs_you_row_1280x800_dark` | T2 | 10.47 | 239 | 0.02 | 94.3 |
| 62 | controls | 06 | `kit_section_label_page_text2_dark` | T2 | 9.02 | 367 | 0.00 | 63.6 |
| 63 | rows | 08 | `kit_notice_default_1280x800_light` | T2 | 8.68 | 198 | 1.94 | 80.0 |
| 64 | controls | 06 | `kit_needs_you_marks_1280x800_dark` | T2 | 8.59 | 376 |  | 3.9 |
| 65 | controls | 06 | `kit_divider_insets_text2_1280x800_dark` | T1 | 7.97 | 153 |  | 4.8 |
| 66 | controls | 06 | `kit_divider_insets_text2_dark` | T1 | 7.97 | 153 |  | 4.8 |
| 67 | controls | 05 | `kit_icon_button_default_text2_dark` | T2 | 6.96 | 1594 | 1.43 | 27.1 |
| 68 | controls | 06 | `kit_divider_insets_ar_1280x800_dark` | T1 | 6.45 | 70 |  | 4.8 |
| 69 | controls | 06 | `kit_key_value_default_text2_dark` | T2 | 2.38 |  |  | 2.0 |
| 70 | controls | 06 | `kit_section_label_page_dark` | T2 | 2.32 | 236 | 0.00 | 42.4 |

## Next orders

Grouping rules:
- Each group owns one or more Gradle modules, and no module appears in two groups, so the groups can run side by side.
- Within a group, the steps follow the dependency order of [batches.md](batches.md): foundation, then controls, rows,
  sheets, frame and screens.
- Kit groups come before the screen groups that use them.
- Each item names the order, the failing golden ids (each stem's `_light`/`_dark` and the size and locale variants
  that failed; the full list is in [goldens-2026-11-07.tsv](scoreboard/goldens-2026-11-07.tsv)), what was measured,
  and the Flutter line that is the spec (FLUTTER_REF `8302de537`).
- **"Gate cases first"** means the order has goldens no test renders. Write the `FlutterGoldenGate.check` cases with
  the Dart scene's exact inputs (standard §6 step 6) before you change any look, or the change can't be measured.
- Chat items already covered by the five chat builders (`/home/eslam/Storage2/wt/polish-audit/chat-parity/SPEC.md`)
  are marked **[covered C-0xx]** and are not ordered again.

### Before any group: the gate has to measure (lead, `:ui:gate` + tools)

0.1 **The layout guard hides the pictures.** `FlutterGoldenGate.check` calls `LayoutGuard.check` first
    (`native/ui/gate/.../FlutterGoldenGate.kt:156`), and a new finding throws before the native render. Every chat
    screen shot was lost until I set `OC_LAYOUT_BASELINE=update`. Record guard findings as their own failing row and
    still render and compare, so the gate never trades a picture for a rule.
0.2 **Stale DEPENDS_ON waivers.** 146 shots in orders 01, 02, 06, 11 and 17 wait for orders 07, 08, 09, 12 and 15, which
    are all merged into this head, but `~/wt/ui/state/{07,08,09,12,15}.done` doesn't exist. Write the `.done` files, or
    run `rb_gate.py recheck --order NN`. Strictly measured, all 146 fail: for example
    `kit_step_glow_done_dark` has box 17.3 % against a 4 % max.
0.3 **Test compile.** `:ui:kit-chat` test sources must compile on the integration head. This is fixed on
    `native/screens-merge20` 431fc8f32. Add a "compile all desktopTest sources" step to the merge checklist.

### Group A: `:ui:kit` (orders 01, 02, 05, 06, 07, 08, 09, 10, 11, 12, 13, 15 kit parts, 22, 24, 29-33)

Pass rate: 223 of 1,948 (11.4 %). 1,419 goldens have no gate case. Run the steps in this order:

A1. **Order 01: Arabic and text 2.0 in the foundation** (`part-kit_text`, `part-kit_icon`, `part-kit_surface`,
    `part-kit_since`). The plain LTR 1.0 shots pass. The 55 failing shots are almost all `_ar` and `_text2` variants.
    - `kit_text_type_ar{,_1280x800}`: box 10.7-16.5 %, line moved 96-214 px. A line that starts with a Latin role label
      ("largeTitle · …") is laid out left to right with the label at the start. Flutter keeps the RTL paragraph
      direction, so the label sits at the end. The Arabic face differs as well. Spec:
      `lib/ui/kit/kit_bidi.dart`, `lib/ui/kit/kit_text.dart:377-385` (Arabic face, `letterSpacing: 0`).
      Native: `kit/text/KitBidi.kt`, `kit/KitText.kt`.
    - `kit_text_mono_ar*`: ink 0.82-0.85×, mono lines too light under Arabic. `kit_text_type_360x800`: ink 0.27×, line
      moved 25 px.
    - `kit_icon_sizes_ar*`, `kit_icon_status_ar*`: box 2.5-4.8 %, moved 4.5-5.2 px. The RTL glyph row is off by about
      5 px. `kit_icon_sizes_text2*`, `kit_icon_status_text2*`: blob 38 px (max 16): a glyph doesn't follow 2.0 text.
      Spec: `lib/ui/kit/kit_icon.dart`.
    - `kit_surface_panel_ar*`, `kit_surface_panel_text2*`: box 3.3-5.9 %, blob 70-130. `kit_surface_tile_*`: ink 0.89×
      dark / 1.14× light, a tile text weight or colour mismatch. `kit_surface_levels_light`: ink 1.19×, moved 5.2 px.
      Spec: `lib/ui/kit/kit_surface.dart`.
    - `kit_since_minutes_ar*`: box 6.8 %, moved 2.0 px. `kit_tappable_focused{,_1280x800}`: box 3.5-38 %, blob 816-29,551,
      a focus ring that differs. `kit_tappable_pressed_dark`: ink 0.52×. `kit_tappable_menu_open`: gate case first.
      Spec: `lib/ui/kit/kit_tappable.dart`.
A2. **Order 02** `kit_foundation_work_*` (10 goldens): all fail, worst `kit_foundation_work_text2_light` box 42.9 %. It
    is a composed gallery, so re-measure after A3-A6 before porting anything here.
A3. **Order 05: controls.**
    - `part-kit_segmented`, 26 of 30 fail. Native draws a 1 px hairline outline around the track, which Flutter doesn't
      have. The track is 3 px taller and the labels 2 px lower. `kit_segmented_default_text2*` box 18.4 %. Spec:
      `lib/ui/kit/kit_segmented.dart:566-567` (track shape) and `:653-662` (indicator; its border only when `quiet`).
      Native: `kit/choice/KitSegmented.kt`.
    - `part-kit_swatch`, 30 of 30 fail. `kit_swatch_preview_graphite_text2_dark` box 32.6 %, `kit_swatch_grid_text2`
      27.5 %. Spec: `lib/ui/kit/kit_swatch.dart`.
    - `part-kit_icon_button`, 29 of 36 fail. Mostly blob 0 with moved 2 px, which is glyph raster. `_text2` box 7.0 %.
      Spec: `lib/ui/kit/kit_icon_button.dart`.
    - Gate cases first for `kit_field`, `kit_choice_list`, `kit_progress`, `kit_term`, `kit_progress_row` and
      `kit_status_mark`: 162 goldens.
A4. **Order 06.**
    - `part-kit_search_field` 18 of 20 (`kit_search_field_nomatch_dark` box 31.2 %). The no-match state is centred, with
      a headline-size title and the icon in the middle. Flutter's inline state starts at the reading edge, with a small
      title. Spec: `lib/ui/kit/kit_state_view.dart:257`, `:543`. Native: `kit/state/KitStateView.kt`.
    - `part-kit_divider` 17 of 22: insets under `_ar` and `_text2` moved 4.8 px.
    - `part-kit_needs_you` 12 of 16: `kit_needs_you_row` box 19.1 %, moved 13.7 px.
    - `part-kit_section_label` 6 of 6: `_text2` moved 62 px.
    - `part-kit_qr` 8 of 10; `part-kit_key_value` `_text2`.
A5. **Order 07**: gate cases first, `kit_buttons` (30 goldens) and `kit_status_tag` (4). The 4 that render pass.
A6. **Orders 08-10.**
    - `part-kit_notice`: all 12 rendered shots fail (box 8.6-14.2 %). The card sits 16 dp lower. The body wraps
      narrower. The action button has 24 dp side padding where Flutter has 16. The cap is measured as `list`; the
      contract says `reading`. Spec: `lib/ui/kit/kit_notice.dart:682` (`EdgeInsets.all(space4)`) and `:352`.
    - `part-kit_row` `kit_row_default*`: blob 39 px (max 36), moved 3.7-5 px. It is close.
    - Gate cases first for `kit_code_block`, `kit_state_view`, `kit_status_line`, `kit_log_panel`, `kit_receipt`,
      `kit_action_stack`, `kit_checklist`, `kit_task_card` (order 08, 290 goldens), all of order 09 except the details
      fold (79), and all of order 10 (20).
A7. **Orders 11-12: sheets.**
    - `part-kit_confirm_sheet`: the gate case renders **an empty frame** (`KitConfirm` at `kit/modal/KitConfirm.kt:149`
      draws nothing in the desktop gate; matrix "empty composition"), so 18 shots fail at box 8-87 %. Render the confirm
      in the window, the way `KitSheet` does, then port `lib/ui/kit/kit_confirm_sheet.dart`. Gate cases for 14 more
      goldens.
    - `part-kit_sheet`, 34 of 36 fail. `kit_sheet_default_dark`: box 12.6 %, blob 20,296; the sheet is 48 px taller.
      The selected check is not accent. The footer link is accent and centred; Flutter has it text1 at the start. An
      Arabic row title sits at the end on its own line. The wide variants are worse
      (`kit_sheet_with_icon_1280x800_light` 42.8 %). Spec: `lib/ui/kit/kit_sheet.dart`, `kit_sheet_frame.dart`.
      Native: `kit/modal/KitSheet.kt`.
    - `kit_dialog_alert` box 13.8 % (`lib/ui/kit/kit_dialog.dart`). `kit_request_card_answered` moved 15.6 px, ink
      0.84×. Gate cases first for `kit_menu`, `kit_undo`, `kit_date_time_picker`, `kit_request_sheet`,
      `kit_capability_explainer`, `kit_jump_pill`, `kit_context_sheet` (202 goldens).
A8. **Order 13: frame.** The pushed-page title uses `KitTextRole.headline` (`lib/ui/kit/kit_top_bar.dart:591`, `pageTitle:
    true`). Native draws a large title on settings, notifications and servers (screens #11 and #12 above). Native:
    `kit/frame/KitTopBar.kt`. `kit_breadcrumb_default*`: box 11.3-12.4 %, moved 15.3 px (`lib/ui/kit/kit_breadcrumb.dart`).
    Gate cases for `kit_top_bar`, `kit_screen`, `kit_arrival`: 78 goldens.
A9. **Order 15 parts in `:ui:kit`.**
    - `kit_phrase_chips_default`: box 18.4-42.6 %. The chips start at x 0 where Flutter's are centred about 35 px in.
      Flutter's chip row wraps its chips, and the gallery centres it; native fills the width. Spec:
      `lib/ui/kit/chat/kit_phrase_chips.dart`. Native: `kit/chip/KitPhraseChips.kt`.
    - `kit_sense_ask_default_1280x800`: box 16.8 %, ink 1.24×, moved 144 px. The phone dark shot fails by 2.8 px.
A10. **Orders 22, 24.** `kit_ink_layer` 5 of 6 fail (`_rest_1280x800_dark` box 20.2 %). `kit_hero_question_default_1280x800`
    box 25.8 %. `kit_carousel_five` box 3.5 %. `kit_tip`. Gate cases first for the other 130 goldens of 22 and 24.
A11. **Orders 29-33** (scenes, live backgrounds; 42 goldens): no gate cases at all.

### Group B: `:ui:kit-chat` (orders 14-17 and the kit-chat galleries of 01, 06, 08, 11)

Pass rate: 44 of 643 (6.8 %). 537 goldens have no gate case. The five chat builders own this module now. Mapped to their spec:

- Covered: transcript anchoring [C-002]; turn grouping and paragraph gaps [C-001, C-025]; the turn footer and
  changed-files list [C-003, C-011]; inline code [C-016]; link colour [C-017]; quote [C-018]; code block [C-006,
  C-019]; prompt bubble and timestamp [C-012, C-027]; work line and step timeline / step light rail [C-004, C-013].
- **Not covered, add to their batches:**
  - B1. Order 14: gate cases for all 286 goldens (message, turn, tool row, markdown, composer, diff view, ask line,
    agent strip, composer chips, queued message). Only the six probe shots exist. Without these gate cases the chat
    builders can't show a measured pass.
  - B2. Order 16: gate cases for all 16 goldens.
  - B3. `KitWorkLineGateTest` and `KitStepTimelineGateTest` use `Frame.Screen`. Flutter frames these scenes with
    `kitGalleryPart`: `test/goldens/kit/kit_work_line_golden_test.dart:71`, and the same in the step-timeline test.
    Fix the frame first; that alone removes the 14-16 px shift. What then remains for the parts: 9 px less space before
    the work line and 7 px less after it, and the label cut off sooner.
  - B4. The 40 `kit_step_glow_*` shots: box 12.1-17.3 %. Their DEPENDS_ON 15 waivers are stale (0.2); include them in the
    C-004 acceptance.

### Group C: `:ui:kit-content` (parts of orders 05, 06, 08, 18, 23)

Pass rate: 44 of 217 (20.3 %); 84.6 % of what renders passes. Gate cases first for order 18 (`kit_viewer`,
`kit_terminal_view`, `kit_image_gallery`, `kit_board_lane`, `kit_image_markup`, `kit_work_graph`, `kit_chart`,
`kit_scanner`: 131 goldens in this module) and the 8 of order 23.

### Group D: `:ui:gate` galleries (orders 34, 36, 37)

0 of 223 measured. No gate case exists for the Glass gallery (64) or the school galleries (95 + 64). This is the only
proof that the six Styles look right. Do it after A3-A8.

### Group E: `:feature:chat` (order 42; after A and B)

0 of 280, and only 10 rendered. Every item below is outside the chat builders' spec:

- E1. **700 dp conversation pane.** `chat_transcript_turn_1280x800`: the prose runs the full 1280 px. Spec:
  `lib/ui/screens/chat/chat_transcript.dart:146` (`maxWidth: KitLayout.paneDetailMaxWidth + 2 * tokens.gutter`) and
  `lib/ui/kit/kit_layout.dart:48` (700). Native: `feature/chat/.../ui/ChatTranscript.kt`.
- E2. **Find.** `chat_find_open`: box 8.3-8.9 %, blob 7,497. Native doesn't highlight matches and doesn't frame the
  current reply. The find bar replaces the composer; Flutter puts it above the chips row and the composer. Spec:
  `lib/ui/screens/chat/transcript_find.dart:7-111`, `lib/ui/kit/chat/kit_find_mark.dart`. Native: `ui/TranscriptFindBar.kt`.
- E3. **Loading.** `chat_loading`: box 9.2-13.9 %. Native shows six row skeletons at the top. Flutter shows
  `KitSkeletonTranscript` (two bubbles at the bottom) and no composer. Spec: `lib/ui/screens/chat/chat_states.dart:22`.
- E4. **Load error.** `chat_load_error`: box 16.0-18.0 %, blob 19,874. Spec: `lib/ui/screens/chat/chat_states.dart:69-95`.
  That is a page `KitStateView` with `StatesUnpluggedScene`, the report button (`product-error-report-bug`) and the
  `details` fold, centred and with no composer. Native shows a 40 dp warning tile, the headline and a button, at the
  top, above the composer.
- E5. **Header.** The title chevron and back arrow are extra, and the subtitle lacks the Git chip ("shopfront · Git"),
  so title and words are 44 px apart. Spec: `lib/ui/screens/chat/chat_top_bar.dart:180-190` (Git chip, branch or "Git")
  and `:273-298`. Native: `feature/chat/.../header/ui/ChatHeaderBar.kt`. Also the **layout guard**:
  `chat_transcript_turn`, `chat_find_open`, `chat_loading` and `chat_load_error` raise "content-edge: bar-title starts
  at x 60, the page's words at 16".
- E6. **Layout guard "edge-tap Copy"**: the reply's Copy target spans x 2-50, and the rule needs 4 dp from the edge
  (`chat_transcript_turn{,_1280x800}`, `chat_find_open`, phone and tablet). The Copy control belongs in the turn footer
  [C-003]; check it after C-003 lands.
- E7. Gate cases first for the other 270 order-42 goldens: sheets C03-C08, C11-C14, C17, C24-C30; pages C09, C31-C39;
  agent and connector cards C40.

### Group F: `:feature:chat-composer` (order 43; after B)

0 of 16; the gate cases test behaviour only.
- F1. **One-row composer, chips above.** Flutter's idle box is one row: plus, words, mic (`lib/ui/kit/chat/kit_composer.dart:796-808`:
  `Row(leading, field, trailing)` unless `stacked`, which only growing text sets). The approval and model chips ("Ask
  first", "Server default") sit in a row **above** the box. Native draws two rows (field, then + · Model · Send) and the
  chips inside. Native `KitComposerBox(stacked = true)` is the default (`ui/kit-chat/.../composer/KitComposerBox.kt:89`);
  the composer host must pass `stacked = false` while the text is short and supply the chips row through `above`. This
  needs the host in `:feature:chat`. Either fold F1 into E, or land a one-line seam first (lead).
- F2. Gate cases for the 16 goldens (`chat_approval_*` and the composer states). The layout guard flags
  `kit_composer_idle_empty` "small-target: Ask OpenCode…" (phone and tablet).

### Group G: `:feature:chats` (order 41; after A8)

0 of 186, 10 rendered.
- G1. **Header.** Native adds a 56 dp row with an in-page search button above "Conversations", which pushes the whole
  page down 56 px (`chats_home_mixed`: line moved 263 px). Flutter has no search on the page: "the server pill and
  search belong to the shell's …" (`lib/ui/screens/chats/chats_home_screen.dart:32`). The heading is `KitPageHeading` (`:393`).
- G2. **Row rhythm.** Rows are 73 px apart where Flutter's are 69 (+4 px a row). The "Show all conversations",
  "Projects" and "Activity" rows show where the golden has none (`chats_home_screen.dart:694-703`: Show all is
  conditional).
- G3. **Empty state.** `chats_home_empty`: box 11.0-11.9 %, blob 19,432. Flutter: a centred "No conversations yet"
  with a full-width "New conversation" button and a "Connect a computer" link at the bottom
  (`chats_home_screen.dart:744-748`). Native: a top-aligned state with three option rows and a FAB.
- G4. **Project sheet.** `chats_home_sheet`: box 12.8-23.4 %. The sheet is 68 px lower. Flutter has per-project glyphs,
  a "Git" pill, "· needs you" counts, an "Other folders" row and an inset rounded selected tile. Spec:
  `lib/ui/screens/chats/chats_project_sheet.dart:57` onward.
- G5. Gate cases for the other 176 goldens (search, inbox, filters, wide).

### Group H: `:feature:settings` (order 44; after A1, A3 and A8)

0 of 215, 7 rendered.
- H1. Page titles: from A8 (headline, not large title).
- H2. **Row height.** Hub and notifications rows are 56-60 dp; Flutter's are 64-65 dp (`lib/ui/kit/kit_row.dart:121`
  `roomyHeight = 64`). The divider inset is 56 where Flutter's is 68. Check whether the settings rows ask for roomy, or
  whether `KitRow` ignores it (if so, it moves to A6).
- H3. **Notifications.** A switch that is on but blocked by Android shows green-on; Flutter shows it grey (on, not
  available). The thumb is 20 dp where Flutter's is on 24 / off 16. The "Turn on notifications in Android" button is
  12 px narrower. `settings_privacy_loaded*`: box 4.6-10.9 %, moved 126 px.
- H4. Gate cases for the other 208 goldens, Appearance first (`settings_*`, `kit_option_card_*`).

### Group I: `:ui:shell` (order 53)

0 of 50 measured, and no gate case exists. Write them; this is the app frame on every screen.

### Group J: `:feature:servers` (order 45; after A8)

0 of 155, 14 rendered.
- J1. `servers_servers_loaded*`: blob 199-5,499, line moved 53-577 px. Flutter shows the OpenCode brand bar with ⋯
  (the root page); native shows a back arrow and a large "Servers" title. Leading glyphs are in 28 dp tiles where
  Flutter draws bare glyphs for roomy index rows (`lib/ui/kit/kit_row.dart:308`). The rows are 24 px lower.
- J2. `servers_pairing_scanner_*` (blocked, denied, no_camera, starting): blob 174-203, ink 2.5-3.0×, moved 26-30 px.
- J3. Layout guard: "tight: Servers" on `servers_servers_loaded*`.
- J4. Gate cases for the other 141 goldens.

### Group K: the other screen modules (orders 40, 46-52; each module once)

- `:feature:onboarding` (40): 28 of 36 rendered pass. The 8 failures are `setup_start` (box 20.2-21.2 %, moved 96 px), `setup_customize` (11.8 %, 142 px),
  `setup_progress_failed` (11.3 %) and `setup_progress_running` (10.7 %). The layout guard raises "tight: On this phone" on `setup_start`, `setup_customize` and
  `setup_progress_*`. Gate cases for 22 more.
- `:feature:phone` (48): 4 rendered fail (`phone_this_phone_running_dark` box 10.3 %, moved 139 px; guard "tight: This
  phone"); 162 gate cases.
- `:feature:agents` (49): 6 rendered fail (`agent_account_signed_in_dark` box 10.3 %, moved 141 px); 98 gate cases.
- `:feature:widget-panels` (52): 12 of 12 rendered fail (`widget_overlay_failed_dark` box 10.0 %).
- `:feature:files` (46, 67), `:feature:projects` (47, 52), `:feature:usage` (50, 25) and `:feature:diagnostics`
  (51, 108): no shot rendered; gate cases first.

### Suggested dispatch

1. Lead: 0.1-0.3 (an hour).
2. In parallel: A (one builder, steps in order; it is the long pole), B (the existing chat builders plus B1-B4),
   C, and the gate cases for I and K, which need no kit fixes to be measured.
3. When A8 lands: E, F (with its seam), G, H and J in parallel. Each owns its module.
4. Last: D (school galleries), then a fresh run of this scoreboard (`scoreboard/tools/score.sh`, about 3.5 h on the
   loaded VPS).

