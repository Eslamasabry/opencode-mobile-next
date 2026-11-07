# Scoreboard group H: `:feature:settings` (order 44), 2026-11-07

Branch `native/s17-settings`, from `native/screens-merge22` (e6a10900e). Scoreboard:
[SCOREBOARD-2026-11-07.md](../SCOREBOARD-2026-11-07.md) on `native/rb-score`, "Group H".

## How it was measured

- Flutter goldens: `FLUTTER_REPO=<this repo> RB_DEV=1 python3 -I native/tool/design/rb_gate.py fetch --order 44`
  (FLUTTER_REF 8302de537 is in this repository's history; all 217 order-44 goldens are `product`).
- Gate cases: `cd native && ./gradlew :feature:settings:desktopTest --tests '*.rb.o44.*' --no-configuration-cache`.
  The configuration cache keeps the environment of the run that stored it, so `OC_ALIGN=report` (used once to render
  the kit-blocked shots) only reaches the test JVM with `--no-configuration-cache`.
- Evaluation: `RB_DEV=1 RB_STATE=<scratch> OC_UI_URL=http://127.0.0.1:9 python3 -I native/tool/design/rb_gate.py
  evaluate --order 44` (tracker posts pointed at a dead port, as the scoreboard run did).
- Run in a cloud container (JDK 21, no Android SDK, Maven Central reached through Google's mirror by a local Gradle
  init script that is not committed). The full `gate_order.sh 44` was not run: its static step compares against
  `native/looks-pass-2026-11-04` and its Gradle step expects the VPS paths.

## Before and after

| | before (scoreboard, 89ec49e55) | after (this branch) |
|---|---:|---:|
| order-44 goldens rendered by a live gate case | 7 | **25** |
| rendered when the kit-blocked cases run (`OC_ALIGN=report`) | 7 | **48** |
| pass | 0 | 0 |
| `settings_hub_loaded_dark`: line moved | 149.0 px | **23.1 px** |
| `settings_hub_loaded_dark`: box / blob | n/a / 203 | 4.08 % / 203 |
| `settings_notifications_loaded_dark`: box | 14.38 % | 14.08 % |
| "Turn on notifications in Android" button | 16..384 dp | **16..396 dp** (Flutter 16..396) |

No order-44 golden passes yet. In every rendered shot the largest difference is kit-owned (below); the settings-side
causes found were fixed.

## What changed in `:feature:settings`

1. **H2, hub rows 64 dp** (0ae2eccd5). Flutter's hub rows are `roomy: true` (`settings_screen.dart:324`,
   `KitRow.roomyHeight = 64`, `kit_row.dart:121`). Native had 56 dp (P-028). The hub asks for the 64 dp minimum through
   the row's modifier because `KitRow` has no roomy flag. Measured in Flutter's golden: rows 64 dp + a 1 px hairline,
   so the rows are 65 px apart; hairline from x 56 to 396. **The scoreboard's "inset 68" is not what the golden shows:**
   native's inset is already 56, as Flutter's.
2. **H3, notifications button** (4f4c08470). The gutter was passed as the button's padding and counted against
   KitButton's 400 dp control cap: the button ended 12 dp short. The gutter now sits around the button.
3. **Language sheet** (4f4c08470). Flutter's picker opens with "Choose the language used throughout the app…" and a
   12 dp gap (`language_picker.dart:158-159`); native went straight to the list. Existing `e7LocaleUiDescription`
   words, English and Arabic.
4. **Appearance guide home**. Flutter's intro line is secondary tone and "Skip the guide" is an accent action row,
   8 dp under it, offered only before the first question (`appearance_screen.dart:652-670`). Native showed a plain
   Skip row always.
5. **Gate cases (H4)**: `SettingsScreensGateTest` (hub, wide hub, Help, notifications/automation, privacy and its
   delete sheet, about, appearance and guided home/returning, language sheet) and `LibraryGateTest` (commands,
   references, skills), with the inputs of `test/support/settings_scenes.dart`,
   `test/revamp/screen_settings_1_golden_test.dart`, `test/goldens/settings_appearance_guided_golden_test.dart` and
   `test/revamp/screen_library_4_golden_test.dart`.

Behaviour tests (each fails without its fix): `SettingsHubGateTest` "B-NAV-068 hub group rows are roomy, 64 dp…",
`ScoreGroupHTest` (button 16..396, language description before the list, Skip only before the first question).
`S8PolishGoldensTest` P-028 now asserts 64 dp; its `s8-hub-*` snapshots were re-recorded (only row heights changed).

## Gate cases waiting on kit fixes (`@Ignore`, with the finding as the reason)

The alignment gate fails these shots on kit parts, and the module's align baseline may only shrink, so they are
written and ignored until the kit fix lands. Remove the `@Ignore` then.

| shots | finding | owner |
|---|---|---|
| `settings_hub`, `library_commands_loaded{,_1280x800}` | start-edge: `KitSearchField` placeholder at 64 dp (Flutter draws it at 64 too: the rule or the field needs an exception) | kit / gate |
| `settings_notifications`, `settings_notifications_loaded_1280x800`, `settings_automation_loaded{,_1280x800}` | row-height: `KitRow` with a chevron and a supporting line is 72 dp, its list's rows 64 | kit A6 |
| `settings_language_sheet_loaded{,_1280x800}` | start-edge: `KitChoiceList` words at 54 dp in a sheet (Flutter 68, with hairlines); gap-step: `KitSheet` title-to-body 14 dp | kit A3/A7 |
| `library_references_empty` | start-edge: `KitStateView` words at 32 dp (Flutter also 32) | kit / gate |
| `settings_appearance`, `settings_appearance_loaded{,_1280x800}`, `settings_appearance_guided_solid_home` | gap-step 1 dp: `KitGuideNextBar` draws its top `KitDivider` outside its semantics | kit |

## Kit-owned differences seen in these shots (not changed here)

- **Page title (A8)**: every settings page title is the 28 sp large title (`TopBarTitle.role`); Flutter's pushed page
  uses the 17 sp headline (`kit_top_bar.dart:591`). This is most of each shot's line shift (hub 23 px, the others
  120-175 px together with the rows below).
- **Row hairline (A6)**: `KitRowGroup` draws its hairline inside the row; Flutter adds 1 px between rows (pitch 65, not
  64; 57, not 56). Every list drifts 1 px a row.
- **Switch rows (A6)**: `KitIconSwitchRow` with a supporting line is 56 dp; Flutter's two-line `KitSwitchRow` is 64.
- **"On but blocked" switch (A3)**: `KitSwitchTrack` paints an on switch that cannot be used in faded accent (green);
  Flutter's `_KitSwitch` (`kit_row_parts.dart:604-640`, `Switch.adaptive` with `onChanged: null`) shows a grey track
  with a large grey thumb. Flutter's thumb is 24 dp on and 16 dp off; native's is 20 both ways.
- CJK language names render as boxes in the desktop gate (font fallback in `:ui:gate`).

## Not covered

- No gate case: `aisetup_*`, `settings_aisetup_*` (no AI-setup page in this module), `library2_*` (external agents
  are dropped by order 44; MCP setup lives elsewhere), `library_integrations_*`, `library_tools_*`,
  `library_credential_*`, `library_command_auth_sheet`, `library_*_sheet*`, `settings_saved_permissions_*`,
  `settings_console_*`, `settings_team_page_off`, `settings_plugins_loaded*`, `settings_theme_pack_preview_sheet_*`,
  `settings_this_server`, `settings_diagnostics*`, the other guided Appearance steps, `gallery_*` and `languages_*`.
- Library shots: native's Library is one tabbed page ("Built-in", tabs); Flutter's goldens are each tab's own page.
  The shots render but cannot pass until the page structure follows Flutter's.
- `settings_appearance_guided_solid_returning` uses a returning record without Flutter's `lastIn`/`last`.

## Side by side (Flutter left, native right)

- [settings_hub_loaded_dark](score-H/settings_hub_loaded_dark.png)
- [settings_notifications_loaded_dark](score-H/settings_notifications_loaded_dark.png)
- [settings_hub_report_problem_dark](score-H/settings_hub_report_problem_dark.png)
- [settings_privacy_clear_drafts_sheet_confirming_dark](score-H/settings_privacy_clear_drafts_sheet_confirming_dark.png)
