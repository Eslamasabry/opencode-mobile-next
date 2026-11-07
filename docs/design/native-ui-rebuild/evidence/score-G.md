# Evidence, scoreboard group G (`:feature:chats`, order 41), 2026-11-07

Branch `native/s17-chats`, from `native/screens-merge22` (`e6a10900e`). Flutter ref `8302de537`. Gate cases:
`native/feature/chats/src/desktopTest/kotlin/**/rb/o41/ChatsGateTest.kt`. Runs below are local desktopTest runs in a
cloud container (JDK 21, no Android SDK; desktop target only), with the order's 186 goldens fetched by
`rb_gate.py fetch --order 41` and judged by `RB_DEV=1 rb_gate.py evaluate --order 41`. The full `gate_order.sh 41` was
not run (it needs the VPS); static checks were not run as a gate either (see "Write set").

## Before / after

Before = the old `ChatsGateTest` (5 cases) on `e6a10900e`. After = the new `ChatsGateTest` (10 cases) on this branch.
Strict pass counts, order 41's `chats_home_*` goldens (24):

| | rendered | pass | notes |
|---|---:|---:|---|
| Scoreboard (89ec49e55) | 10 | 0 | |
| Before (e6a10900e) | 4 | 0 | `mixed`, `mixedWide`, `projectSheet` threw on the alignment gate before drawing (see "Blocked") |
| After (this branch) | 8 | 0 | empty ×4 and nomatch ×4 render; mixed, running, sheet (12 shots) still blocked |

Raw gate rows (whole-frame %, ink-box %, largest blob px):

| shot | before | after |
|---|---|---|
| `chats_home_empty_dark` | 11.17 / 12.02 / 17,883 | 2.03 / 2.27 / 62 |
| `chats_home_empty_light` | 10.14 / 10.65 / 17,417 | 1.95 / 2.18 / 72 |
| `chats_home_empty_1280x800_dark` | 4.13 / 5.50 / 18,883 | 0.76 / 2.52 / 42 |
| `chats_home_empty_1280x800_light` | 3.77 / 4.95 / 18,417 | 0.73 / 2.41 / 43 |
| `chats_home_nomatch_dark` | not rendered | 3.94 / 4.42 / 1,290 |
| `chats_home_nomatch_light` | not rendered | 3.06 / 3.43 / 1,071 |
| `chats_home_nomatch_1280x800_dark` | not rendered | 1.45 / 4.79 / 1,290 |
| `chats_home_nomatch_1280x800_light` | not rendered | 1.12 / 3.72 / 1,071 |

Strict evaluation after: empty fails only narrowly (blob 109-134 px against 100; the calm words sit 6-7 px low). Nomatch
fails on blob 1,387-1,695 px and a 16 px line move (the "need you" line and chips). Both remaining causes are in kit parts
(below), not in the screen.

Looked at next to Flutter (left Flutter, right native): `score-G/chats_home_empty_dark-flutter-vs-native.png`,
`score-G/chats_home_empty_1280x800_dark-flutter-vs-native.png`, `score-G/chats_home_nomatch_dark-flutter-vs-native.png`.

## What changed

- G1: `ChatsScreen(shellBar = false)` is the page alone, as Flutter's home draws no bar (`chats_home_screen.dart:32`); the
  app keeps the bar (default true). The gate cases render without it. The unused in-page `SearchField` is deleted.
- E20-S27: the screen is always a framed page (`page = true`), one ground behind heading and list with or without the
  bar; the heading stays in the Reading column, aligned with the rows on wide windows.
- G2: no 4 dp `spacedBy` between list items: rows are one hairline apart.
- G3: empty and no-match are Flutter's calm pages (words above the middle, full-width button at the bottom, quiet link
  under it; `KitStatusAnswer` at Calm in a room of at most `Layout.STATE_MAX_WIDTH` from the column's start). No FAB over
  them. 8 dp above the title (and under it without a "need you" line).
- G4: project rows show their kind glyph (`projectKindGlyph`, Flutter `phoneProjectKindIcon`) and a "Git" pill; the facts
  come from the new `ChatsHomeExtras.projectFacts` port (default empty; the connection owner fills it). "· needs you"
  counts were already supported; the gate case now passes `projectNeedsYou`, `allCount = 8` and `otherFoldersCount = 2`
  as Flutter's scene does. A chosen project tints its chip instead of checking it.

## Blocked (not this module)

- **`KitConversationRow` alignment (kit, B-161).** Every list shot (`mixed`, `running`, `sheet`; 12 goldens) throws on
  the alignment gate before it is drawn: the project tile's letters start at x 19 (columns are 16/20/48…), and the time
  ends at 395.8-395.9 where the list's rows end at 395.0. This was already so at `e6a10900e`. Also, B-161's row (tile,
  project line above the title) is not Flutter's two-line feed row, so these shots cannot pass even once aligned unless
  the owner decides which row look wins.
- **`KitPageHeading` spacing (kit, scoreboard A8).** Native adds `bottom = S3` and `spacedBy(S1)`; Flutter has no bottom
  padding and lifts the 48 dp line target by `space2` (`kit_page_heading.dart:96-121`). This puts the calm words 6 px low
  and the chips 16 px low.
- **`KitStatusAnswer` balanced wrap (kit).** Flutter narrows a wrapped answer to the narrowest width that keeps its line
  count (`kit_status_answer.dart:350-380`); native wraps at the full width ("Nothing running" body).
- **Sheet pill vs. alignment rule.** The Git pill's label ink ends 13 dp before the check column, which the end-edge rule
  flags (`chats_home_sheet*`). Flutter's own label ends there too (the pill's face reaches the edge); the rule, not the
  screen, would need to count a chip's face.
- **`chats_home_leftover_*`** (4): the "still busy" line comes from the shell's runaway watcher; `ChatsUi` has no field
  for it. No gate case.

## Write set

Order 41's `WRITES` covers `ui/**`, `*Screen.kt`, `*Sheet*.kt` and `rb/o41/**`. Outside it: `presentation/ChatsUi.kt`,
`HomeRows.kt`, `ChatsHomeExtras.kt`, `ChatsViewModel.kt` (the `projectFacts` field and port), the module's own
`commonTest` and S10 goldens, and two kit files (`KitRowTrailing.Chip`, `KitIcons.packageBox`, one commit, `54f00bc4e`).
