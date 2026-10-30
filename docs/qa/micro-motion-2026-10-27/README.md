# Micro-interactions and micro-animations audit (2026-10-27)

Owner request (2026-10-27): "what about micro interactions and micro
animations". Every press, value change, arrival and departure should get a
short, quiet answer. Standing rules: 150–250 ms, native Android feel (ripple,
predictive back, stretch, haptics), nothing bouncy, nothing gimmicky, and no
glass, blur or glow.

Branch `feat/micro-motion-2026-10-27` (based on `ee059e7c3`). Scope:
`lib/ui/kit/**`, except `lib/ui/kit/chat/**`,
`lib/ui/kit/motion/kit_page_transitions.dart`, `lib/ui/kit/kit_diff_view.dart`
and `lib/ui/kit/diff/**` (owned by other agents).

## Rules every change follows

- **Timings and curves** come from `KitMotion`: `quick` (150 ms) for a
  control answering, `standard` (250 ms) for a part arriving; curves `enter`
  and `exit` (MOT-1).
- **Paint and transforms only.** No layout animation in scrolling list items
  (MOT-5), and no scale, blur or fade-scale (MOT-2).
- **Three motion levels.** The new `KitMotion.travels(context)` is true only
  under Animations: Full.
  - **Full:** a change may move as well as fade (a number rolling, a menu
    dropping in).
  - **Calm:** the same change only cross-fades.
  - **Off, or the system's remove animations** (`KitMotion.reduced`): the
    change is instant and settles after one `pump()` (MOT-7).
- **First build is final.** A screen that opens with a value already set does
  not animate it.
- **Haptics** only where MOT-11 lists them (send, done, commit, long-press,
  select). Nothing fires on plain taps, scrolling, copy, sheets or failures.

## What was already there

The kit already answered most interactions, and this audit confirmed each of
these parts:

- **KitTappable:** press tracker, Android ripple (`KitInkLayer`) and the
  `pressHold` minimum.
- **Switches:** the styled thumb glides; the platform switch is used in
  Solid. `KitSwitchRow` gives a select haptic.
- **KitSegmented:** the indicator slides and choosing gives a select haptic.
- **Styled surfaces:** every surface drawn through `kitSurfaceBox` (chips,
  fields, styled buttons, rows) cross-fades its state over `stateFade`.
- **Status and progress parts:** a status tag or status line change
  (`KitSwap`), a notice arriving (`KitEntrance`, `KitReveal`), loading to
  content to error (`KitStateView`), progress bars (tween to the new value),
  and the progress ring's figure.
- **Chevrons:** `KitSpin.chevron`, the chip and fold chevrons.
- **Copy and working states:** copy turns to a check (`KitIconButton`,
  `KitButton`), and a working button's spinner cross-fades.
- **Undo bar:** slides up and fades in, then fades out.
- **Lists:** `KitAnimatedRows` handles rows that come and go (inbox,
  servers, terminal list, server switcher, board lane, checklist).
- **Refresh and paging:** pull to refresh (`KitRefresh`), the tab switcher's
  lens springs, the wheel and the jump pill.
- **Badges:** a needs-you badge fades in and out.

## Audit table

Priority: **H** high, **M** medium, **L** low (noted, not built).
"Done" means it is implemented on this branch.

| Part | Today | Change | Priority |
|---|---|---|---|
| Counts: `KitNeedsYou.badge` number | The whole pill cross-fades on any change; the number jumps inside it | The pill stays; its number rolls (`KitSwap.count`): up when it rises, down when it falls, both fading. Calm: cross-fade. Off: instant. "99+" rolls only when what it says changes | H, done |
| Counts: `KitTabSwitcher` tab counts (board lanes, list tabs) | Cross-fade (`KitSwap`) | `KitSwap.count` roll | H, done |
| Counts: `KitSegmented` segment counts | Number jumps | `KitSwap.count` roll | H, done |
| New motion part: `KitSwap.count` | (none) | Rolls a count in place: 60 % of its height of travel, a clip only while it rolls, the leaving number excluded from semantics and touches. Uses `KitMotion.travels` | H, done |
| `KitIconButton` glyph toggles (show/hide secret, play/pause, any icon that swaps on the same button) and colour changes (selected, enabled) | Same switcher key, so the glyph and colour jump | Keyed by glyph and colour, so a toggle cross-fades on `quick`; instant under reduced motion. Benefits every `KitField` secret reveal | H, done |
| `KitButton` primary, enabled to disabled (Solid) | Material does not ease its fill, so Save jumps from grey to mint; the words ease over Material's own 200 ms, even under Animations: Off | The fill eases on `quick` through a colour tween that is always in the tree. The words' ease is set to `KitMotion.quick` (zero under reduced motion). Other styles already cross-fade through their surface box | H, done |
| `KitMenu` opening | Cross-fade only | Under Full the panel also drops `space2` (8 dp) from its anchor, or rises when it opens above. Only its position moves, never its layout. Closing fades only. Calm: fade. Off: instant | M, done |
| `KitStepSlider` (text size and similar) | No haptic | A select haptic for each step reached, as a wheel's next value gives (MOT-11). None for the same step, none with Vibration off. Spec updated (`KitStepSlider.md`) | M, done |
| Radio-like choices: `KitChoiceList` (local single and multi), `KitChoiceCards`, `KitChoiceTiles`, `KitAccentRow` swatches | No haptic. The specs say so explicitly (`KitChoiceList.md` §Motion and haptics, `KitOptionCard.md`), and a test pins it | Not changed. MOT-11 lists switch, segment and wheel, not radios or checkboxes. Android's own radios and checkboxes do not vibrate either | Owner decision |
| `KitCopy` | Light tick (`KitHaptics.send`) on every copy | Conflicts with MOT-11 ("nothing on … copy"); `KitCopy`'s own doc and test choose the tick. Not changed | Owner decision |
| `KitSwipeAction` | The row snaps back after the act and the list owner removes it; no tick at the threshold | A threshold tick is not in MOT-11. Snapping back before removal is the spec ("the row always returns"); removal animates where the list uses `KitAnimatedRows` | L |
| `KitRowGroup` rows coming and going | Instant | Not made automatic. It would also animate filters and search results (many rows folding on every keystroke), which the board lane deliberately avoids. Lists that change one item at a time already use `KitAnimatedRows` | L |
| `KitSliverRowGroup` (lazy lists) | Instant | Would need `SliverAnimatedList` and keyed diffing; not worth the risk for long lists | L |
| `KitChip.count` | The number sits inside one truncating text line | Rolling the number means splitting the chip's truncation measurement; there is one use in the app | L |
| `KitButton` secondary or tertiary icon colour when disabled | Material's `IconTheme` jumps | Small; the words already ease | L |
| `KitFloatingAction` | Appears with its screen; never toggles in place | None needed | L |
| Text fields | Focus edge cross-fades (`kitSurfaceBox`); the error line unfolds (`KitReveal`) | None needed | Done before |
| Sheets and dialogs | Native bottom sheet; dialog cross-fade on `standard` | None needed | Done before |
| Haptics: switch, segment, wheel, long-press, confirm, send, done | All fire per MOT-11 | Step slider added (above) | Done |

## Performance

- **Idle screens stay still.** Every new animation is triggered by a change
  and ends within `quick` (150 ms). The count roll and the icon cross-fade
  use `AnimatedSwitcher`, idle at rest. The button's colour tween is an
  `ImplicitlyAnimatedWidget`, idle at rest. The menu drop runs while the
  route's own entrance animation runs.
- **Small rebuilds.** The roll rebuilds one `Text`, and the button tween
  rebuilds one button for 150 ms. The menu drop repositions the panel
  without laying it out again (same constraints).
- **Goldens.** Settled frames are unchanged: a settled roll is one child in a
  `Stack` with no clip, and the tween ends at the same colour.

## Edits outside `lib/ui/kit`

None in `lib/`. Docs:

- `docs/ux-system/kit-api/KitStepSlider.md`: the haptic line.
- `docs/ux-system/kit-api/KitMotionParts.md`: `KitSwap.count`.
- `docs/ux-system/kit-api/KitMenu.md`: the opening drop.
- `docs/ux-system/kit-api/KitIconButton.md`: glyph and colour cross-fade.

## Tests

New behaviour tests:

- **`test/kit/kit_motion_parts_test.dart`, `KitSwap.count`:**
  - the first build is final;
  - a rising count rolls up and a falling one rolls down;
  - the leaving number is excluded from semantics;
  - Calm cross-fades with no travel;
  - Off is instant;
  - an unchanged count does not roll.
- **`test/kit/kit_icon_button_test.dart`:** a toggled glyph cross-fades, and
  is instant under reduced motion.
- **`test/kit/kit_action_test.dart`:** a primary that wakes up passes through
  the colours in between, and is instant under reduced motion.
- **`test/kit/kit_menu_test.dart`:** the menu drops from its anchor under
  Full, and only fades under Calm.
- **`test/kit/kit_step_slider_test.dart`:** one select haptic per new step,
  none for the same step.

Results are in the run log section below.

## Run log

The set:

- `test/kit_*_test.dart`;
- everything under `test/kit/` and `test/goldens/kit/`;
- `test/file_size_ratchet_test.dart`, `test/ui_glossary_test.dart` and
  `test/ui_ledger_coverage_test.dart`.

It ran on the build VPS (`OC_VPS_JOBS=4 tool/qa/vps_test.sh … -- -r
failures-only`) against the final working tree: **+7744 ~2, all other tests
passed** (2 skipped, both pre-existing skips).

### Golden changes (10), and why

`kit_confirm_stop_working_{dark,light}` and the eight
`kit_image_markup_working_*` shots changed.

1. **Where the change shows.** Each shot is taken one frame after a tap that
   starts work, and in each a secondary button turns disabled while the work
   runs ("Keep running", "Done").
2. **Why the old images were stale.** The gallery runs with the system's
   remove animations on. Material's own 200 ms word fade ignored that, so the
   old goldens caught the disabled button still drawn in its enabled colour.
3. **What the new images show.** The words now follow `KitMotion` (zero under
   reduced motion), so the frame shows the button's real disabled state.

No settled look changed anywhere else.

### Fixed during the run

- **Icon button colour.** Keying the glyph by colour chained cross-fades
  while a theme lerps, because every frame made a new key. The glyph is now
  keyed by icon only, and its colour eases with a tween.
- **Button fill slots.** Material's enabled and disabled slots each keep
  their own meaning (`kit_confirm_sheet_look_test`).
