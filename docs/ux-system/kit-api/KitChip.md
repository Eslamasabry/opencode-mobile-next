# KitChip: API freeze (wave 0, 2026-09-26)

Unit: `kit-KitChip` (wave 1, tier 1a, kit-part). Spec: cut review C09 (a new part; K2 has no section for it), visual language §4 (chips and pills: radius 999), §5 (the work chip "Read 3 files · edited 1", the composer's model chip). Rules: KIT-3, KIT-8, KIT-24 (ChoiceChip stays with KitSegmented), LAY-9, LOOK-6, LOOK-19, LOOK-23, STATE-8, STATE-9, A11Y-8, COPY-30.

## Purpose

A small rounded label, in five kinds:

- **plain:** a fact;
- **action:** a tap target, or an on/off filter;
- **removable:** a chosen thing that can be taken out;
- **count:** a label with a number;
- **summary:** folded work that opens.

Every kind carries a word, never colour alone, and has a 48 dp hit area around a smaller visual pill.

## Replaces

- **Code (`test/kit_ratchet_baseline.json`, G16, outside the kit), 19 uses in 17 files:**
  - `Chip`, 5 uses: `screens/chat/message_view.dart`, `screens/chat_screen.dart`, `screens/files_screen.dart`, `screens/library/integration_tiles.dart`, `screens/team/policy_block.dart`;
  - `ActionChip`, 10 uses in 9 files: `screens/chat/approvals_sheet.dart`, `screens/chat/attention_card.dart`, `screens/chat/empty_chat.dart`, `screens/chat/permission_sheet.dart`, `screens/chat_screen.dart`, `screens/files_screen.dart` (2), `screens/team/gate_sheet.dart`, `screens/team/work_sheet.dart`, `widgets/team_receipt.dart`;
  - `InputChip`, 1 use: `widgets/form_renderer.dart`;
  - `FilterChip`, 3 uses: `screens/global_sessions_screen.dart`, `widgets/appearance_picker.dart`, `widgets/form_renderer.dart`.
- **Not replaced:**
  - `ChoiceChip` (12 uses in 8 files) is one choice among several, which is `KitSegmented` (KIT-24).
  - `Badge` (4) is `KitNeedsYou`'s badge or `KitChip.count`, decided by what it means.
- **Map:** kit-v2.json assigns no element to KitChip, because the part was added by the cut review. The composer chips reach it through kit-KitComposerChips (`embedded-composer#embedded-composer-model-chip`, `#embedded-composer-attachment-chip-preview`), and the transcript's work chip through kit-KitWorkLine. `gate-sheet#gate-work-chip` and `work-sheet#work-dependency-chip` stay assigned to `KitRow`.

## File

`lib/ui/kit/kit_chip.dart` (new). Tests go in `test/kit/kit_chip_test.dart`, and galleries in `test/goldens/kit/kit_chip_golden_test.dart` (covering `KitChipWrap` too).

## Public API

```dart
enum KitChipKind { plain, action, removable, count, summary }

/// A small rounded label (visual language §4–§5). States: disabled is not
/// a chip state (a chip that cannot act now is not shown, STATE-8);
/// declared states: selected (action), expanded (summary) (KIT-12).
class KitChip extends StatelessWidget {
  /// A fact that does nothing when tapped: "main", "3 agents".
  const KitChip({
    super.key,
    required this.label,
    this.icon,
  }) : kind = KitChipKind.plain,
       onPressed = null,
       onRemove = null,
       count = null,
       selected = null,
       expanded = null;

  /// A tap target ("Open in terminal"). With [selected] non-null it is an
  /// on/off filter (replaces FilterChip): a check at the start when on,
  /// toggled semantics. One choice among several is KitSegmented, not this.
  const KitChip.action({
    super.key,
    required this.label,
    required VoidCallback this.onPressed,
    this.icon,
    this.selected,
  }) : kind = KitChipKind.action,
       onRemove = null,
       count = null,
       expanded = null;

  /// Something the person chose that can be taken out: an attachment, a
  /// form value (replaces InputChip). The × at the end is its own 48 dp
  /// target labelled l10n.kitChipRemove("Remove {label}").
  const KitChip.removable({
    super.key,
    required this.label,
    required VoidCallback this.onRemove,
    this.icon,
    this.onPressed,              // optional: open the thing (preview)
  }) : kind = KitChipKind.removable,
       count = null,
       selected = null,
       expanded = null;

  /// A label with a number: "Tasks · 3". The number uses tabular figures
  /// and intl formatting for the locale; semantics "Tasks, 3".
  const KitChip.count({
    super.key,
    required this.label,
    required int this.count,
    this.onPressed,
    this.icon,
  }) : kind = KitChipKind.count,
       onRemove = null,
       selected = null,
       expanded = null;

  /// Folded work that opens: "Read 3 files · edited 1" (the transcript's
  /// work chip, visual language §5). [expanded] non-null adds a chevron
  /// (down folded, up open) and expanded semantics.
  const KitChip.summary({
    super.key,
    required this.label,
    required VoidCallback this.onPressed,
    this.icon,
    this.expanded,
  }) : kind = KitChipKind.summary,
       onRemove = null,
       count = null,
       selected = null;

  final KitChipKind kind;

  /// The chip's words, from the caller's ARB (COPY-1). A name the person or
  /// the server chose is wrapped by the caller with KitBidi.auto (COPY-30).
  final String label;
  final IconData? icon;
  final VoidCallback? onPressed;
  final VoidCallback? onRemove;
  final int? count;
  final bool? selected;
  final bool? expanded;
}

/// Lays chips out in a wrapping row with the kit's spacing: space2 (8)
/// between chips, and a run spacing that keeps every chip's 48 dp hit area
/// clear of the next run's (LAY-9). Start-aligned in both directions.
class KitChipWrap extends StatelessWidget {
  const KitChipWrap({super.key, required this.children});

  final List<Widget> children;
}
```

- **Why there is no `disabled`:** a chip is either there and usable, or not shown. That is STATE-8's "otherwise it is hidden": a chip has no room for a visible reason. `KitChip.action`, `.summary` and `.removable` take non-null callbacks.
- **Internal keys (TEST-5):** `kit-chip-remove` (the × target) and `kit-chip-check` (the selected check).

## States

| Kind / state | Look |
|---|---|
| plain | `surface3` pill; label in `text2`; optional 20 dp glyph in `text2` at the start; not focusable, not a button |
| action | `surface3` pill; label in `text1`; glyph in `text1`; the hover and pressed fill is `surface2` |
| action, selected: true | a check glyph in `accent` at the start (LOOK-6 current-selection mark), label `text1` |
| action, selected: false | the action look, with no check |
| removable | `surface3` pill; label `text1`; a 20 dp close glyph in `text2` at the end inside its own 48 dp target |
| count | `surface3` pill; label in `text2`, then " · ", then the number in `text1` with tabular figures |
| summary | `surface3` pill; label `text1`; with `expanded` non-null, a 20 dp chevron at the end (down folded, up open) |
| focused (keyboard) | a 2-physical-pixel `accent` focus ring around the pill |

There is no loading, empty or error state: a chip shows a value its host already has (KIT-12). A failure in folded work is said in the summary's words, with a neutral error glyph passed as `icon`. It is never red (LOOK-5, B2).

## Tokens

- **ThemeRoles:** `surface3` (the pill), `surface2` (the hover and pressed fill: one surface step away from the resting pill, so the change reads without an overlay colour or alpha), `text1`, `text2`, `accent` (the check and the focus ring).
- **KitTokens:** `minTarget` (48, the hit height), `space1` (4, the gap between glyph and label), `space2` (8, between chips), `space3` (12, the pill's inner horizontal padding), `smallIconSize` (20).
- **KitText:** `secondary`, 14/20, for the label, with the number in `secondary` with tabular figures (LOOK-18).
- **Not yet on the VL branch:** `KitTokens.focusRingWidth(context)` (STANDARDS §0.5 step 2).
- **Shape:** `KitShape.pill` (a stadium: VL §4 "chips and pills: 999", LOOK-19), from the pre-wave `KitShape` enum. There is no `pillRadius` number (README.md, decision D13).
- **New token (pre-wave, `_new-tokens.md`):** `KitTokens.chipHeight` = 32, the visual pill height. The 48 dp hit area is transparent padding around it. `KitChipWrap`'s run spacing is `minTarget - chipHeight` (16), so vertical hit areas never overlap.

## Adaptive

| Window | Behaviour |
|---|---|
| compact | Visual height `chipHeight`, hit height 48. A label longer than the available width ellipsises on one line (A11Y-8: a chip may truncate), with the full label in semantics and a long-press tooltip. |
| medium | the same |
| expanded / large | the same size (§8.3: never smaller). A fine pointer adds a hover fill and a tooltip with the full label only when it is truncated. |

- **Keyboard:** action, removable, count (with `onPressed`) and summary chips are focusable in reading order, and Enter or Space activates them.
  - On a removable chip, Tab first reaches the chip (when `onPressed` is set), then its ×.
  - Delete or Backspace on a focused removable chip calls `onRemove`.
  - A plain chip is not focusable.
- **Short windows:** no change. Chips flow inside `KitChipWrap`.

## Accessibility

| Kind | Semantics |
|---|---|
| plain | text only (`label`), not a button |
| action | `button: true`, `label`; `toggled` when `selected != null` |
| removable | the chip is a `button` (when `onPressed` is set) named `label`; the × is a separate `button` named l10n.kitChipRemove(label), "Remove {label}" |
| count | label "{label}, {count}", with the count formatted by `intl` |
| summary | `button: true`, `label`; `expanded` when `expanded != null` |

- **Target:** 48 dp high on every window. A chip narrower than 48 dp gets a 48 dp-wide hit area. `KitChipWrap` keeps neighbouring hit areas from overlapping (LAY-9).
- **Words, not colour:** selected is shown by the check glyph *and* toggled semantics, not by colour alone (STATE-9).
- **200 % text:**
  - the pill grows in height with the text (`chipHeight` is a minimum);
  - the label stays on one line and truncates with the full value in semantics (A11Y-8);
  - glyphs do not scale.
  - `KitChipWrap` wraps to more runs, with no overflow at 320 dp.
- **Announcements:** none. A filter change is announced by the list it filters, if at all.

## RTL

- Glyph and check at the start, × and chevron at the end, all mirrored with directional padding.
- `KitChipWrap` flows from the start edge.
- Chevrons are not mirrored: they point down and up.
- The count's " · " separator and the number follow the locale's `intl` digits (COPY-30, B17).

## Motion and haptics

- **selected:** the check cross-fades in on `KitMotion.quick`.
- **expanded:** the chevron turns on `KitMotion.standard` with `emphasized`: the same turn as `KitSpin.chevron` (KitMotionParts.md). KitMotionParts builds in the same tier, so the chip draws that turn itself and kit-hygiene (2d) swaps it to `KitSpin.chevron`.
- **Reduced motion:** both are instant.
- **No other motion:** no size animation (removing a chip is the host list's change, never `AnimatedSize` in a list, MOT-5) and no ripple spread beyond the pill.
- **Haptics:** none (MOT-11).

## Data safety and honest state

- **Removal:** `KitChip.removable`'s × removes at once. The host decides the DATA-11 treatment: removing a chosen attachment before sending is "neither" (it is re-addable). Removing something stored offers Undo through `showKitUndo`. The chip never confirms.
- **Counts:** a count chip shows the host's real count. It never shows a stale number without the host saying so (STATE-18).
- **Summary chip words:** they come from the one status source (ARCH-8). The chip does not build status text.
- **No attention colour:** attention roles are never used on a chip (LOOK-4). A needs-you count is `KitNeedsYou`'s badge.

## Depends on

- **Units:** none (tier 1a, C25).
- **Shared seams (STANDARDS §0.5 step 2):** `KitTokens.focusRingWidth`, `KitBidi` (used by callers).
- **Depend on it (C25):** kit-KitSearchField, kit-KitWorkLine, kit-KitComposerChips.

## Tests required

These go in `test/kit/kit_chip_test.dart`:

1. A plain chip is not a button (no tap action in semantics) and is not focusable.
2. Tapping an action chip calls `onPressed` once. With `selected: true`, it shows `kit-chip-check` and exposes `toggled: true`. With `selected: null`, there is no `toggled` flag.
3. Removable:
   - the × is a separate 48×48 target labelled "Remove {label}";
   - tapping it calls `onRemove` only, not `onPressed`;
   - Delete on the focused chip calls `onRemove`.
4. Count: the semantics label is "{label}, {count}"; the number is formatted with `intl` for `en` and `ar`.
5. Summary: a tap calls `onPressed`; `expanded: true`/`false` exposes `expanded` semantics, and the chevron points up or down.
6. Every interactive chip's hit area is at least 48 dp high at text 1.0 and 2.0. In a `KitChipWrap` of 10 chips at 320 dp wide, no two chips' 48 dp hit areas overlap (LAY-9).
7. At text 2.0, a long label ellipsises on one line, the semantics label is the full text, and there is no overflow exception at 320, 360 and 412 dp, LTR and RTL (G6).
8. No text is painted with alpha below 255 (LOOK-14), and no attention role is used (LOOK-4). This is a scan of the painted colours in the gallery scene.
9. Reduced motion (G8): selecting and expanding settle after one `pump()`.
10. Keyboard (G14): Tab order is chip, then its ×; Enter and Space activate.

## Galleries required

These go in `test/goldens/kit/kit_chip_golden_test.dart`. The scene is a `KitChipWrap` holding one chip of each kind on `ground` and on `surface1`, rendered at DPR 3.0 (TEST-9, TEST-20):

- **Each state at 412×915, dark and light:** `default` (all five kinds), `selected` (action on and off), `expanded` (summary open and folded), `focused` (keyboard focus on a removable ×) and `truncated` (a long label), which is 10 PNGs.
- **`default` at 360×800, 915×412, 800×1280, 1280×800 and 1600×1000, dark and light:** 10 PNGs.
- **`default` at text 2.0 and Arabic RTL, at 412×915 and 1280×800, dark and light:** 8 PNGs.
- **Total:** 28 PNGs.

## Non-goals

- No single-choice chip row: that is `KitSegmented`, and `ChoiceChip` goes there.
- No needs-you badge: that is `KitNeedsYou`.
- No avatar chip: that is `KitAvatar` in kit-KitImage.
- No model chip logic (the model picker, "Sign in to a model"): that is kit-KitComposerChips, built on this.
- No disabled chip.
- No screen migration (wave 2).

## Open questions

None. `chipHeight` and the `KitShape` enum are pre-wave (`_new-tokens.md`). If they are missing when the unit starts, it records the gap and uses a `StadiumBorder` and `minTarget - 2 * space2` (32) for the height.

## AI Team severity (2026-09-29)

All constructors accept `tone: KitChipTone.neutral` by default. `attention`
uses the attention text role for a worded Major finding; `danger` uses danger
for Critical. Minor remains neutral. Text and optional icon carry the tone;
the existing neutral pill fill and 48 dp target remain. Severity is always
written in the label so color is never the only signal.

## Accent tone (3 Oct 2026)

`KitChipTone.active` is a worded "this is on" state: the pill is the accent
at 22 % over `surface3`, and the words and glyph are made readable on that
tint. Used by the composer's Auto-approve chip, which pairs it with
`AppIconography.shieldFilled`; the off state stays neutral with the outline
shield. The label always carries the state, so colour is never the only signal.
