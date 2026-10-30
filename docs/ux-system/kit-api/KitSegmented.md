# KitSegmented — API freeze (wave 0, 2026-09-26)

Unit: `kit-KitSegmented` (wave 1, tier 1d, kind `kit-part`). Spec: kit-v2.md §1.6, §8.2, G6; STANDARDS KIT-24, LOOK-6, LOOK-21, STATE-8, STATE-9, MOT-2, Appendix A #78 (it stacks into `KitChoiceRow`s, not wrapping chips); cut review C09 (ChoiceChip stays with KitSegmented), C25; README.md decision D7 (C14's RowParts → Segmented edge becomes RowParts → ChoiceList). STANDARDS wins where it differs from kit-v2.md.

## Purpose

One choice among 2–4 short, always-visible options: a scope, a mode, a range, a filter. It is full width, start-aligned, and marks the selected segment with a check. When the labels do not fit on one line (long words, text at 2.0), it becomes a vertical stack of full-width `KitChoiceRow`s with the same meaning.

## Replaces

- **Map elements (kit-v2.json `assignment` → `KitSegmented`): 25 elements on 22 pages:**
  - active-context-chips;
  - appearance-settings effects-animations;
  - builtin-server-setup runtime-choice;
  - catalog tabs;
  - embedded-composer delivery toggle;
  - embedded-file-preview-body mode;
  - embedded-mobile-task-list filter;
  - global-sessions folder chip;
  - mcp-setup kind-segmented and scope-segmented;
  - model-picker-sheet model-filters;
  - model-picker-sheet-options-dialog options-variant;
  - provider-quota chips;
  - review-workspace mode and scope-picker;
  - skill-activation-sheet view-toggle;
  - skills-preview-sheet view-toggle;
  - team-host-sheet host-kind;
  - team-run-timeline-tab filter;
  - terminal-source;
  - theme-pack-preview-sheet theme-mode chips;
  - usage-range;
  - usage-hub range and scope;
  - workspace-new-mode.
- **Files and their G16 baseline counts:**

  | File | SegmentedButton | ChoiceChip | FilterChip |
  |---|---|---|---|
  | `lib/ui/screens/mcp_setup_screen.dart` | 2 | – | – |
  | `lib/ui/screens/builtin_server_screen.dart`, `chat/composer.dart`, `review_workspace.dart`, `settings/personal_settings_screens.dart`, `terminal_screen.dart`, `workspace_screen.dart` | 1 each | – | – |
  | `lib/ui/screens/team/run_screen.dart` | 1 | 1 | – |
  | `lib/ui/widgets/pickers.dart` | – | 3 | – |
  | `lib/ui/screens/active_context_screen.dart` | – | 2 | – |
  | `lib/ui/screens/global_sessions_screen.dart` | – | 2 | 1 |
  | `lib/ui/widgets/appearance_picker.dart` | – | 1 | 1 |
  | `provider_quota_screen.dart`, `usage_screen.dart`, `widgets/team_host_form.dart` | – | 1 each | – |
  | `library/catalog_screen.dart`, `library/skill_activation.dart`, `library/skills_screen.dart`, `usage_hub_screen.dart`, `widgets/file_preview.dart`, `widgets/mobile_task_view.dart` | 0 in G16 (hand-built pills or `TabBar`) | | |

- **Whole app:** `SegmentedButton` 9 in 8 files, and `ChoiceChip` 12 in 8 files, which C09 leaves with KitSegmented. Of the 3 `FilterChip`s, the ones posing as a single choice come here. A multi-select `FilterChip` row becomes `KitChoiceList.multi` or a `KitChip`, per the map.

## File

`lib/ui/kit/kit_segmented.dart`. It holds `KitSegment<T>` and `KitSegmented<T>`. Tests go in `test/kit/kit_segmented_test.dart` and galleries in `test/goldens/kit/kit_segmented_golden_test.dart`.

## Public API

```dart
@immutable
class KitSegment<T> {
  const KitSegment({
    required this.value,
    required this.label,        // words; an icon alone is never enough
    this.icon,                  // optional leading glyph beside the words
    this.count,                 // "Needs you 2": tabular figures, part of the label's semantics
    this.enabled = true,
    this.disabledReason,        // required when !enabled: "Chosen at install · reinstall to change"
    this.key,
  }) : assert(enabled || disabledReason != null);

  final T value;
  final String label;
  final IconData? icon;
  final int? count;
  final bool enabled;
  final String? disabledReason;
  final Key? key;
}

class KitSegmented<T> extends StatelessWidget {
  const KitSegmented({
    super.key,
    required this.segments,        // 2..4 (G37 assert)
    required this.selected,        // one of segments' values (assert)
    required this.onChanged,       // null: the whole control is disabled (then [disabledReason] is required)
    required this.semanticsLabel,  // the group's name: "Time range" (announced; a visible SectionLabel above says it too)
    this.disabledReason,           // the whole control's reason when onChanged is null
  }) : assert(segments.length >= 2 && segments.length <= 4),
       assert(onChanged != null || disabledReason != null);

  final List<KitSegment<T>> segments;
  final T selected;
  final ValueChanged<T>? onChanged;
  final String semanticsLabel;
  final String? disabledReason;
}
```

- **`semanticsLabel` is required.** kit-v2.md §1.6 has it optional; the freeze makes it required so every group is named (A11Y-1).
- **Disabled reasons are per segment,** plus one for the whole control. kit-v2.md §1.6 had only a group `disabledReason`, and a disabled segment's reason is shown under the control.
- **Calls.** `onChanged` is called once per activation of a *different* enabled segment. Activating the selected segment does nothing.
- **No styling parameters.** There is no `showCheck`, no density and no style.
- **Kit copy:** none of its own. The check is a glyph, and the selected state is announced by semantics.

## States

The doc comment declares: `default` (one selected), `with-counts`, `segment-disabled` (reason under), `disabled` (the whole control, with its reason), `stacked` (labels do not fit, so the `KitChoiceRow` stack).

- **Loading, empty and error.** Not applicable. The choices are local and known. A choice that depends on data is not a KitSegmented; use `KitChoiceList` with its loading state.
- **Disabled.** A disabled segment is dimmed (`text3`) and not activatable, and its `disabledReason` is shown as one muted line under the control (STATE-8). With several disabled segments, each reason is shown once. A disabled control shows its reason under it.
- **Working and answered.** Not applicable. A segment that writes to a server shows the result through the host's `KitReceipt` or `KitStatusLine`, never on the segment.

## Tokens

All of the following exist on `feat/visual-language-v1` unless flagged.

- **ThemeRoles:**
  - `surface1`: the track;
  - `hairline`: the track outline and the selected segment's outline, 1 physical px;
  - `surface3`: the selected segment's fill;
  - `accent`: the check glyph and the focus ring. LOOK-6 lists the "segment check" as a current-selection mark;
  - `text1`: the selected label;
  - `text2`: unselected labels;
  - `text3`: disabled labels.
- **KitText roles:**
  - `label` (13/18, 600): segment labels, in sentence case (LOOK-15);
  - `caption`: counts, with tabular figures;
  - `secondary`: the disabled-reason line.
  - In the stacked form, the `KitChoiceRow` roles apply.
- **KitTokens:**
  - `minTarget` (48): the control's height and each segment's minimum width;
  - `buttonRadius` (14): the track corner; the indicator corner is `buttonRadius − space1`, computed with no literal;
  - `space1`–`space3`;
  - `smallIconSize` (20): the check and icons;
  - `maxIconScale`.
- **KitMotion:** `quick` with `enter` for the indicator slide.
- **New tokens:** `KitTokens.hairlineWidth(context)` and `focusRingWidth(context)`, the pre-wave seam (LOOK-21, STANDARDS §0.5 step 2). None of its own.

## Adaptive

- **All windows.** The control is full width on the rails and start-aligned, never centred; the host's column caps it (reading width 720 in `KitScreen`). Each segment takes an equal share. The part measures whether every label (plus icon, count and check) fits on one line in its share, the same way `KitAskLine` measures.
- **Stacked form (KIT-24, Appendix A #78).** When any label does not fit, or the text scale is at least 2.0, it renders a vertical stack of full-width `KitChoiceRow`s (radio mark, the same order, the same `value`s and keys, and the disabled reason on the row's supporting line). The semantics stay those of one mutually exclusive group. This happens in every window class; it is not a wrapping chip row.
- **Short windows (< 480 dp tall).** No difference: the stack scrolls with its host.
- **Keyboard (§8.2, §8.3, G14):**
  - The group is one Tab stop, and Tab enters on the selected segment.
  - Arrow Left and Right (in the stacked form, Up and Down) move focus within the group, following the reading direction.
  - Space or Enter selects the focused segment.
  - Arrows move focus only; they do not select (the same rule as `KitChoiceList`).
  - The focus ring is `accent`, 2 physical px.
- **Fine pointer.** A hovered unselected segment fills with the next surface step above the `surface1` track (`surface2` in dark, `surface3` in light; KitTappable's rule, README.md decision D11). A tooltip repeats the full label when a count or icon shortens it. Targets stay 48 dp.

## Accessibility

- **Group and segment semantics.** The group carries `semanticsLabel` and is a mutually exclusive group. Each segment is a `button` with `selected`, `inMutuallyExclusiveGroup` and `enabled`, and its label includes the count ("Needs you, 2").
- **Selection is not colour only.** The selected segment shows a check glyph, a `surface3` fill and a 1 px outline (STATE-9).
- **48 dp targets.** The control is 48 dp tall and each segment at least 48 dp wide, with no overlapping target areas. Disabled segments keep their size.
- **Announcements.** A change of selection is announced by the framework's `selected` state change. The part adds no extra live region.
- **200 % text.** It reaches the stacked form (G6 checks this at 320 and 412 dp). Nothing truncates: a label that would need an ellipsis triggers the stack instead.

## RTL

- The segment order follows the text direction: the first segment sits at the start.
- The check sits before the label (at the start).
- Arrow keys follow the visual direction.
- The indicator slides in the mirrored direction.
- Counts use `intl` digits.

## Motion and haptics

- The selection indicator slides to the new segment on `KitMotion.quick` with the `enter` curve (a transform of paint, MOT-5). The check cross-fades on `quick`.
- The switch between the row and stacked forms is a layout change with no animation.
- Under `KitMotion.reduced` it is instant, and one `pump()` settles (G8). There is no fade-scale (MOT-2).
- Haptics: none, so feedback keeps its meaning (MOT-11).

## Data safety and honest state

- **Local and instantly reversible.** The treatment is "neither" (DATA-11): the choice itself is the undo. The part never confirms.
- **Honest disabled state (STATE-8).** A disabled segment always shows why, as visible text under the control and on the row in the stacked form; a tooltip alone is never enough. This fixes the map's runtime choice that greys out with no reason.
- **No inverted accents.** Only the selected segment carries the accent check. An unselected segment never uses the accent (the map found "Raw" highlighted while unselected).
- **Value is truth.** The segment shown as selected is always `selected`. The part keeps no internal selection state that could drift from its host.

## Depends on

- **KitChoiceList → KitChoiceRow** (kit-KitChoiceList, tier 1c): the stacked form (KIT-24). Edge added (README.md, decision D7): Field → Receipt → ChoiceList → Segmented, no cycle, so this unit is tier 1d. To keep the wave at seven tiers, kit-KitRowParts-v2's `until` choice uses `KitChoiceList` directly instead of this part. The alternative, Segmented drawing its own stacked rows, would give `KitChoiceRow`'s job to two parts.
- **Existing parts:** `KitMotion`, `KitAskLine`'s fit measurement (read, not edited; the measurement is re-implemented or lifted into a shared private helper inside this file), and `KitText`/`ThemeRoles`/`KitTokens` (VL).
- **Used by:** kit-KitDateTimePicker (AM/PM), kit-KitComposer (the delivery choice), and the screens in "Replaces".

## Tests required

In `test/kit/kit_segmented_test.dart` (G9, G14, G37, TEST-15):

1. Fewer than 2 or more than 4 segments throws an `AssertionError`. A `selected` value not in the segments asserts. A disabled segment without `disabledReason` asserts, and so does `onChanged == null` without a group `disabledReason` (G37).
2. Tapping another segment calls `onChanged` exactly once with its value. Tapping the selected one does not call it. Tapping a disabled one does not call it.
3. The selected segment shows a check glyph (found by semantics or icon, not colour). Semantics: `selected`, `inMutuallyExclusiveGroup`, `button` and the group label.
4. A disabled segment's reason is visible text under the control.
5. Stacking:
   - at text 2.0, or at 320 dp with long labels, the part renders `KitChoiceRow`s (found by semantics and labels) with the same values;
   - tapping a row calls `onChanged`;
   - at text 1.0 and 412 dp with short labels it stays one row (G6).
6. Keyboard:
   - Tab lands on the selected segment;
   - Arrow Right moves focus without calling `onChanged`;
   - Space calls `onChanged`;
   - in RTL, Arrow Left moves to the next segment.
7. The count is included in the segment's semantic label.
8. Under reduced motion one `pump()` settles and no ticker is running (G8).
9. Every segment and the whole control are at least 48 dp. There is no overflow at 320–1600 dp and 915×412, text 1.0, 1.3 and 2.0, LTR and RTL (G6).

## Galleries required

`test/goldens/kit/kit_segmented_golden_test.dart`, at DPR 3 (TEST-9, 2 × 5 + 18 = 28 PNGs):

- **Every declared state, dark and light, at 412×915.** The states are default (3 segments), with-counts, segment-disabled with its reason, disabled, and stacked. The stacked scene uses long labels at text 1.0, so the stacking is shown without scaling.
- **The default state, dark and light, at the other sizes:** 360×800, 915×412, 800×1280, 1280×800 and 1600×1000.
- **The default state at text 2.0 and in Arabic RTL, dark and light, at 412×915 and 1280×800.** The text 2.0 scenes show the stacked form.

## Non-goals

- No more than 4 options (use `KitPickerRow`).
- No switching between views that keep scroll and state (use `KitTabSwitcher.tabs`, §2.10).
- No on/off (use `KitSwitchRow`) and no multi-select (use `KitChoiceList.multi`).
- No split button, no icon-only segments, no centred pill toggle, and no wrapping chip row (Appendix A #78).
- No migration of call sites (wave 2).

## Open questions

None. The Segmented → ChoiceList dependency for the stacked form is in README.md's build_units list.

## Appearance revamp additions (2026-10-20)

- **The chosen segment is filled in the accent** (`roles.accent`), its words, check and icon in `onAccent`, as the studio's tab pill is. This replaces the neutral `surface3` indicator everywhere the control is used. The keyboard focus ring is still a 2 physical px accent stroke inside the segment (on a chosen segment the ring and the fill share the colour; the ring is drawn as an outline over it).
- **`iconOnly: true`** draws a compact pill of icons, as wide as its icons (`minTarget * 1.15` each), at the start edge. Every segment needs an icon; its `label` stays as the tooltip and the semantics label, so an icon is never the only name. It never stacks. Used for light, dark and system on Appearance.
