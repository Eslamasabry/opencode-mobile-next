# Scoreboard group K — screen modules (orders 40, 46-52)

Branch `native/s17-screens`, from `native/screens-merge22` (e6a10900e). Scoreboard: `SCOREBOARD-2026-11-07.md` (taken
at 89ec49e55) "Next orders → Group K".

## How this was measured

- Not the VPS. This container (4 cores, JDK 21, no Android SDK) ran Gradle `desktopTest` with the orders' gate-case
  filters (`--tests '*.rb.oNN.*'`), then `RB_DEV=1 python3 -I native/tool/design/rb_gate.py evaluate --order NN`.
  The Gradle dependency cache came through a Google mirror of Maven Central (a user-level init script, not committed),
  because Maven Central answered 429 to Gradle's parallel downloads.
- Flutter goldens: FLUTTER_REF `8302de537` is not in this clone, so `rb_gate.py fetch` cannot run. The PNGs were copied
  from this checkout's `test/goldens` and `test/revamp/goldens` (the Flutter tree on the branch) into
  `looks-gate/flutter/8302de537/` (592 files, every golden of orders 40 and 46-52). If they differ from 8302de537 the
  VPS numbers will differ; the native side is the same.
- The layout guard and the alignment gate ran on every shot (no `OC_LAYOUT_BASELINE=update`, no `OC_ALIGN=report`).
- I looked at every native render next to its golden for the shots named below (side-by-side PNGs were produced
  from `looks-out/gate/<shot>-{flutter,native}.png`; not committed).

## Before / after

Counts are `rb_gate.py evaluate` on this container's renders. "Before" is the scoreboard's strict column at 89ec49e55.

| Order | Goldens | Pass before → after | Rendered and failing before → after | No gate case before → after |
|---|---:|---:|---:|---:|
| 40 | 58 | 28 → 28 | 8 → 8 | 22 → 22 |
| 46 | 67 | 0 → 0 | 0 → 0 | 67 → 67 (not run here) |
| 47 | 52 | 0 → 0 | 0 → 0 | 52 → 52 (not run here) |
| 48 | 166 | 0 → 0 | 4 → 4 | 162 → 162 |
| 49 | 104 | 0 → 0 | 6 → 6 | 98 → 98 |
| 50 | 25 | 0 → 0 | 0 → 2 | 25 → 23 |
| 51 | 108 | 0 → 0 | 0 → 0 | 108 → 108 (not run here) |
| 52 | 12 | 0 → 0 | 12 → 12 | 0 → 0 |

No shot crossed its threshold. Per shot (ink-box % / largest blob px, evaluator metrics; T5 limits 4 % / 100 px):

| Shot | Before (scoreboard) | After |
|---|---|---|
| `setup_start_dark` | 21.24 % / 10,954 | 21.41 % / 11,961 |
| `setup_start_light` | 20.17 % / 10,508 | 20.12 % / 11,376 |
| `setup_customize_dark` | 11.79 % / 14,719 | 7.74 % / 4,043 |
| `setup_customize_light` | 11.62 % / 14,674 | 7.51 % / 4,654 |
| `setup_progress_failed_dark` | 11.33 % / 11,164 ¹ | 7.79 % / 4,697 |
| `setup_progress_failed_light` | 11.05 % / 11,003 ¹ | 7.51 % / 4,649 |
| `setup_progress_running_dark` | 10.74 % / 23,204 | 10.91 % / 24,037 |
| `setup_progress_running_light` | 9.84 % / 17,145 | 9.96 % / 17,460 |
| `phone_this_phone_running_dark` | 10.25 % / 431 | 10.80 % / 433 |
| `phone_this_phone_running_light` | 9.05 % / 259 | 9.48 % / 261 |
| `usage_stats_unsupported_dark` | not rendered | 6.83 % / 1,842 |
| `usage_stats_unsupported_light` | not rendered | 4.47 % / 194 |

¹ Measured on the dead `:feature:setup` screen; after, on the live journey screen.

`setup_start` gains the Other ways rows Flutter has, so its box does not drop while the top bar still shifts the page.


## What changed, per order

### 40 first-run setup (`:feature:phonesetup`, `:feature:setup`)

- `setup_start`: Flutter folds the less common ways under a divider and an "Other ways" row
  (`phone_setup_start_screen.dart:686-760`); native stopped at "Choose what to install". Added, with the one way native
  can take ("Connect to a computer by address" → the existing `connectComputer`; Termux setup is dropped by D7). The row
  sits on the 16 dp column and the hairline takes no height (align gate start-edge and gap-step), so the title is at
  x 16 where Flutter's is at x 32.
- `setup_customize`: Done is the full-width action block (`phone_setup_customize_sheet.dart:465`).
  Box 11.8 % → 7.7 %, blob 14,719 → 4,043 px (dark).
- `setup_progress_failed` / running: Continue and Open fill the footer as Pause does (`phone_setup_progress_screen.dart:351`).
  Box 11.3 % → 7.8 %, blob 11,164 → 4,697 px (dark).
- Two modules rendered the same two goldens: `:feature:setup` (`SetupRoute.Phone`, which no route opens) and
  `:feature:phonesetup` (`PhoneSetupRoute.Setup`, the live screen). Whichever ran last wrote the row. The failed shot now
  renders on the live screen with Flutter's scene; the dead screen's two checks are gone. Its one align-baseline line
  (`# of #`, the kit progress ring's centred numeral) moved with the shot; the baselines shrank by one line overall.
- Tests: other ways unfolds and calls `connectComputer`; customize Done spans the sheet; a stopped job's Continue fills
  the bottom.

### 48 this phone (`:feature:phone`)

- `phone_this_phone_running`: the version row (Update) now leads the tools list a section below Restart/Stop, as in
  `this_phone_screen.dart:1116-1135` (it sat inside the server group). The Installed line reads like `joinSetupNames`
  ("Linux base, Git and SSH, Node.js and OpenCode"). The gate case now feeds Flutter's scene (700 MiB, saved in-app
  server not connected → "Use this phone's server", 2.1.1 offered).
- The box number does not move yet (10.25 % → 10.80 %): the kit top bar shifts every row by about 28 px, so the rows that
  now line up with Flutter still land 28 px low.
- Tests: installed names read as one sentence; Update sits a section below Stop.

### 49 agents (`:feature:agents`)

- `agent_account_*`: looked at side by side. Layout, rows, values, section labels and the Details fold already match
  Flutter; the measured difference is the kit top bar (large title on its own line under Back) plus the kit row rhythm
  (56 vs 57 dp). Nothing screen-owned to fix.

### 50 usage (`:feature:usage`)

- `usage_stats_unsupported`: Flutter makes the whole page the missing capability (`usage_screen.dart:253, 266-275`:
  `KitCapabilityExplainer.state` for `server.oc2`, no Refresh, no description, filters or budgets). Native drew the
  description, a centred headline and the budget rows. `UsageScreen` now uses the kit's
  `KitCapabilityExplainer(variant = State)`. New gate case `rb/o50/UsageGateTest`: 6.83 % / 1,842 px dark,
  4.47 % / 194 px light (before the screen change, the same case measured 11.45 % / 1,205 px on the gate's own row file).
- The module's tests did not compile on this head (`LimitsUiTest` used the old `left` argument; `LimitsScreenTest`
  used a custom-action call the current Compose test API rejects). Both fixed. Six older tests in the module still fail
  on this head (B-NAV-021 clear budgets, B-159 no-limits provider, two B-160 cost tests, and the two Limits/Cost
  goldens that have no recorded PNG); untouched, they were red before this branch's changes.

### 52 widget panels (`:feature:widget-panels`)

- Rendered all 12 again. Everything left is inside kit parts: `KitSteerBox`'s field (Flutter: about 100 dp tall, 16 dp
  text inset, 1 dp border; native: 70 dp, 12 dp, 2 dp focused border) and `KitChoiceList` (Flutter 64 dp rows with
  dividers; native 56 dp, none). No screen-owned change.

### 46 files, 47 projects/terminal, 51 diagnostics

- Not started: no gate cases added. See "What remains".

## Gate cases added or changed

| Module | Case | Golden |
|---|---|---|
| `:feature:phonesetup` | `SetupJourneyGateTest.failed` | `setup_progress_failed` (moved from `:feature:setup`) |
| `:feature:phone` | `PhoneGateTest.running` (Flutter's scene inputs) | `phone_this_phone_running` |
| `:feature:usage` | `UsageGateTest.usage_stats_unsupported` (new) | `usage_stats_unsupported` |

Written and dropped, with the reason:

- `setup_progress_log` (Details sheet with the live log): the align gate rejects the kit's `KitChecklistStep` columns
  (icon at x 26, words at x 64 — which is where Flutter draws them) and `KitDetailsFold`'s text at x 32. Kit-owned;
  a native rule that disagrees with Flutter. Not baselined.
- `usage_stats_empty`: the align gate rejects the total card's inner column (x 32, Flutter's too). Kit/`KitSurfacePanel`
  question for the lead, not baselined.
- `setup_welcome_entry`: native's welcome is the owner's pick A (2026-11-05, comment in `WelcomeScreen.kt`), which
  differs from Flutter's synthetic scene on purpose; it also trips the guard on the kit choice cards.

## Not fixable in group K (for the owners named)

- Kit top bar (group A8, `:ui:kit` `KitTopBar`): a pushed page's title is the 28 sp large title on its own line under
  Back; Flutter's `pageTitle` is the 17 sp headline beside Back (`kit_top_bar.dart:591`). It is the largest remaining
  difference on `setup_start`, `setup_customize`, `setup_progress_*`, `phone_this_phone_*`, `agent_account_*` and
  `usage_stats_*`.
- Flutter's start-screen goldens are root pages (no Back); native's setup and This phone pages always show Back,
  which is right in the app (they are pushed) but differs from the golden.
- `KitProgressRing` arc gradient (`setup_progress_running`): Flutter's arc deepens towards its start; native's is flat.
- `KitSteerBox`, `KitChoiceList` (order 52, above); `KitLockedRow` row height and lock glyph (`setup_customize`).
- Missing state, not inventable in Compose: the progress note's "We'll let you know when it's ready" needs the
  notification-permission answer (`SetupJourneyUi`); the start hero's progress / ready / Termux variants
  (`setup_start_progress|stopped|ready|termux`) need an in-app probe and Termux discovery; `setup_ready` needs the
  project journey (F04); This phone's "Agents on this phone" row and a Storage value row have no field or action in
  `ThisPhoneUi`/`ThisPhoneActions`.
- `first_run_connect` is the add-server screen (`:feature:servers`, group J). `kit_setup_termux` needs a Termux mood in
  `KitSetupPhone` (kit).

## What remains in group K

- Orders 46 (67 goldens), 47 (52), 51 (108): no gate cases yet. 48 has 162 and 49 has 98 goldens without gate cases;
  50 has 23.
- The align gate rejects several Flutter layouts (cards and checklists whose words start at x 32 or 64). Until the lead
  decides whether those columns join the allowed set, gate cases for such screens cannot be added without baselining.

## Emulator checks for the coordinator

1. First run → On this phone (fresh): under "Choose what to install" a divider and "Other ways"; tap it, the chevron
   turns and "Connect to a computer by address" unfolds; tap it → the add-server screen. With reduced motion the row
   opens at once.
2. On this phone → Choose what to install: "Done" is a full-width button.
3. Start setup, turn on airplane mode until it fails: "Continue setup" is full width at the foot; after it finishes,
   "Open" is full width.
4. Settings → This phone (set up, running, update offered): Restart and Stop in one group; a section gap; then Update
   OpenCode leading the next list; Installed reads "…, Node.js and OpenCode".
5. Usage on a server that is not OpenCode 2: the page shows only "OpenCode 2 server / Needs a server running OpenCode 2."
   with no Refresh in the bar and no budget rows.
