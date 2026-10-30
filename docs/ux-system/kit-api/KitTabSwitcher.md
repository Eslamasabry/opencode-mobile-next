# KitTabSwitcher v2 (with KitTabStrip) — API freeze (wave 0, 2026-09-26)

Unit: `kit-KitTabSwitcher-v2` (wave 1, tier 1c, kind `kit-change`, model sonnet; after kit-KitNeedsYou, C25, plus kit-KitTappable and kit-KitMotionParts, README.md). Write set: `lib/ui/kit/motion/kit_tab_switcher.dart` and `lib/ui/widgets/team_board_tabs.dart` (C24: it becomes a forwarding wrapper). Spec: kit-v2.md §2.10, §8.2; STANDARDS MOT-2 and Appendix A #23 (the switcher's scale goes: slide or cross-fade only), LOOK-4, LOOK-24 (needs-you only through KitNeedsYou), KIT-43, R12; the board design `docs/design/team-board-2026-09-26.md` §2 (the column strip). Written in the cross-check because the unit had no frozen API block (R16) and KitBoardLane.md depends on the strip's name.

## Purpose

Views that each keep their own state, switched from a strip of labelled tabs: the shell's destinations (today's `KitTabSwitcher`, body only), and the in-page tab sets (usage, about, team run, command launcher, model picker, capabilities, the team board's columns). v2 adds the strip the switcher lacks, as a standalone `KitTabStrip` (the board pages its own lanes under it) and as `KitTabSwitcher.tabs` (strip plus body). It also drops the body's 97 % settle, which is a scale (MOT-2).

## Replaces

- **Code (G16, outside the kit):** `TabBar` 5 and `Tab` 9 (the raw strips K2 §2.10 names), and `TabBarView` where it only switches kept-alive pages.
- **`TeamBoardTabs`** (`lib/ui/widgets/team_board_tabs.dart`, 232 lines: a scrolling row of `Material` + `InkWell` stadium tabs, a count that bumps with a `ScaleTransition`, and a 7 dp `AppTheme.statusColor(attention)` dot). It becomes a forwarding wrapper over `KitTabStrip` with its constructor unchanged (`counts`, `needsYou`, `selected`, `onSelect`) and its keys kept (`team-board-tabs`, `team-board-tab-<column>`, `team-board-tab-count-<column>`, `team-board-tab-needs-you-<column>`; TEST-5, used by `test/team_board_test.dart`). It is marked `/// Retired by kit-KitTabSwitcher-v2: use KitTabStrip` (KIT-43: no `@Deprecated`), and `TeamBoardTabs(` becomes a G2 pattern.
- **The switcher's settle:** `KitTabSwitcher.settleFrom` (0.97 scale) is no longer applied (Appendix A #23). The constant stays public and is marked retired (KIT-43).

## File

- `lib/ui/kit/motion/kit_tab_switcher.dart`: `KitTab`, `KitTabStrip`, `KitTabSwitcher` (existing file).
- `lib/ui/widgets/team_board_tabs.dart`: the forwarding wrapper (it maps `TeamBoardColumn` words and counts; the kit reads no app model).
- Tests: `test/kit/kit_tab_switcher_test.dart`. Gallery: `test/goldens/kit/kit_tab_switcher_golden_test.dart`.

## Public API

```dart
/// One tab of a strip.
@immutable
class KitTab {
  const KitTab({
    required this.label,     // "Working": the host's word, shown as written
    this.count,              // null while the first answer comes: no number, never "0"
    this.needsYou = 0,       // > 0: KitNeedsYou.badge beside the label (LOOK-24)
    this.icon,               // optional 20 dp glyph before the label
    this.key,                // on the tab (TeamBoardTabs passes team-board-tab-<column>)
    this.countKey,           // on the count (team-board-tab-count-<column>)
    this.needsYouKey,        // on the badge (team-board-tab-needs-you-<column>)
  });

  final String label;
  final int? count;
  final int needsYou;
  final IconData? icon;
  final Key? key, countKey, needsYouKey;
}

/// A strip of tabs: one selected, each with its words, count and needs-you
/// badge. Scrolls sideways when the tabs do not fit, and keeps the selected
/// tab in view. States: default, counts loading, needs you, overflowing.
class KitTabStrip extends StatefulWidget {
  const KitTabStrip({
    super.key,
    required this.tabs,            // 2..8 (asserted)
    required this.selected,        // index into tabs
    required this.onSelected,      // ValueChanged<int>, called once per activation of a different tab
    this.semanticsLabel,           // the group's name ("Columns"); null: none
    this.stripKey,                 // TeamBoardTabs passes team-board-tabs
  });

  final List<KitTab> tabs;
  final int selected;
  final ValueChanged<int> onSelected;
  final String? semanticsLabel;
  final Key? stripKey;
}

class KitTabSwitcher extends StatefulWidget {
  /// UNCHANGED: the body only (the shell's destinations, whose strip is KitNav).
  const KitTabSwitcher({
    super.key,
    required this.index,
    required this.children,
    this.reduceMotion = false,
  });

  /// NEW (K2 §2.10): the strip over the kept-alive body. [tabs] and
  /// [children] have the same length (asserted).
  const KitTabSwitcher.tabs({
    super.key,
    required List<KitTab> tabs,
    required this.index,
    required ValueChanged<int> onSelected,
    required this.children,
    this.reduceMotion = false,
    String? semanticsLabel,
    Key? stripKey,
  });

  final int index;
  final List<Widget> children;
  final bool reduceMotion;

  /// UNCHANGED: the share of the switch the old destination takes to fade out.
  static const fadeOutShare = .35;

  /// Retired by kit-KitTabSwitcher-v2: the body no longer scales (MOT-2,
  /// Appendix A #23). Kept so existing references compile (KIT-43).
  static const settleFrom = .97;
}
```

- **The body keeps its contract** (unchanged): every destination keeps its state; the chosen one takes touches, focus and semantics from the first frame; the one being left is picture only; tickers stop off screen. Only the scale is removed: the fade-through stays (`fadeOutShare`, `KitMotion.standard`).
- **Lazy first visit** (slice-speed-ui, 2026-09-28): `lazy: true` builds a destination the first time it is chosen, plus the indexes in `preload` from the start, and keeps each one from then on (same state retention as before). Default `false` keeps the eager contract. The shell uses it with `preload: {Work}`, so Inbox, Project and Settings do no build and no reads (Settings' health check) until opened. A host that signals a destination (a `ValueListenable` it listens to) signals after the frame that first builds it.
- **Kit copy** (ARB, `kit` prefix, en and ar): `kitTabLabel` "{label}, {count}" (a tab's semantics with a count). The needs-you words are KitNeedsYou's; the tab words are the host's.

## States

| State | What shows |
|---|---|
| default | tabs start-aligned; the selected tab is a `surface3` pill (`KitShape.pill`) with its label in `text1` at `label` weight, the others `text2` with no fill |
| counts loading | labels only; no number (never "0") |
| needs you | `KitNeedsYou.badge(count: needsYou)` after the label (the only attention colour in the part, LOOK-24) |
| overflowing | the strip scrolls sideways inside itself; the selected tab is scrolled into view when it changes |
| focused (keyboard) | the 2 physical px `accent` focus ring on the tab (KitTappable) |

Loading, empty and error of the pages are the host's. There is no disabled tab: a tab that cannot open is not shown (LAY-15 for the shell; the host's rule elsewhere). KIT-12 doc comment: "States: default, counts loading, needs you, overflowing".

## Tokens

- **ThemeRoles:** `surface3` (selected pill), `text1` (selected label, counts), `text2` (other labels), `accent` (focus ring only); `attentionFill`/`onAttentionFill` only through `KitNeedsYou.badge`.
- **KitText:** `label` (13/18 w600) for tab words; the count in `label` with tabular figures (LOOK-18).
- **KitTokens:** `minTarget` (48, each tab's height and minimum width), `space1` (label to count), `space2` (between tabs), `space3` (a tab's inner padding), `gutter` (the strip's side inset); `smallIconSize` (20). Shape `KitShape.pill` and `focusRingWidth(context)` (pre-wave, `_new-tokens.md`).
- **New tokens:** none.

## Adaptive

| Window | Behaviour |
|---|---|
| compact | one row on the rails, scrolling sideways when it does not fit; the selected tab scrolls into view on `KitMotion.standard` (jumps under reduced motion) |
| medium | the same; most strips fit without scrolling |
| expanded / large | the same row, start-aligned in its pane (never centred); hover is one surface step (KitTappable); a horizontal strip keeps no permanent scrollbar (the desktop rule for strips) |

- **Short windows:** unchanged (one row).
- **Keyboard (§8.3, LAY-10):** the strip is one Tab stop entered on the selected tab; Left and Right (mirrored in RTL) move focus, Home and End jump, Enter and Space select (arrows do not select, the same group rule as KitSegmented and KitChoiceList).

## Accessibility

- The strip is a group named `semanticsLabel`; each tab is a `button` with `selected`, labelled "{label}, {count}" and, with needs-you, the badge's words ("1 needs you") through the merged label (KitNeedsYou.badge).
- Selection is never colour alone: the selected tab has the fill, the weight and `selected` semantics.
- 48 dp tabs with 8 dp between their areas. At 200 % text the labels grow, the strip scrolls, and nothing truncates or overflows at 320 dp.
- A count change is not announced by the strip; the host's live region says it once if at all (A11Y-3).

## RTL

The first tab sits at the start (right in Arabic); arrow keys and the scroll direction follow the reading direction; the badge sits after the label (mirrored by directional padding). Counts come from `intl` (COPY-30).

## Motion and haptics

- **Body:** the existing fade-through on `KitMotion.standard`, with no scale (MOT-2). Under `KitMotion.reduced` or `reduceMotion` the chosen destination shows in the next frame (unchanged).
- **Strip:** the selected pill cross-fades on `KitMotion.quick`; a changed count swaps with `KitSwap` (no bump, no scale); the scroll into view uses `KitMotion.standard` with `enter`.
- **Haptics:** none (MOT-11).

## Data safety and honest state

- **Counts are the host's.** While they are loading the strip shows none, never "0" (TB).
- **One attention source.** `needsYou` is a count from the attention source (AUTO-11); the part never computes it.
- **State is kept.** Switching tabs never rebuilds or drops another tab's content (drafts, scroll positions).

## Depends on

- **kit-KitNeedsYou** (tier 1b, C25): `KitNeedsYou.badge`.
- **kit-KitTappable** (tier 1b) and **kit-KitMotionParts** (tier 1a, `KitSwap`): edges added (README.md), no tier change.
- **Existing:** `KitMotion`, `KitText`, `KitTokens`, `ThemeRoles`, `KitLayout`.
- **Depended on by:** kit-KitBoardLane (`KitTabStrip`, C25), and the wave-2 units owning the raw `TabBar`s and `team_board_screen.dart`.

## Tests required

In `test/kit/kit_tab_switcher_test.dart`:

1. **Strip:** tapping another tab calls `onSelected` once with its index; tapping the selected tab calls nothing.
2. **Counts:** `count: null` shows no number; `count: 3` shows "3" in tabular figures and the semantics "Working, 3".
3. **Needs you:** `needsYou: 1` shows `KitNeedsYou.badge` (found by `needsYouKey`) and the merged label ends with ", 1 needs you"; no other attention colour is painted (a painted-colour scan).
4. **Overflow:** eight tabs at 320 dp scroll sideways inside the strip with no overflow; changing `selected` to the last tab scrolls it into view.
5. **Keyboard (G14, desktop capabilities):** Tab enters on the selected tab; Right moves focus without calling `onSelected`; Enter selects; in RTL, Left moves forward.
6. **Body without scale:** switching `index` shows no `Transform` with a scale and no `ScaleTransition` in the tree (MOT-2); the destinations keep their `State` across switches (the existing contract).
7. **`.tabs`:** the strip and body stay in step; mismatched lengths assert.
8. **Wrapper:** `TeamBoardTabs(counts:, needsYou:, selected:, onSelect:)` renders a `KitTabStrip` with the keys `team-board-tabs`, `team-board-tab-<column>`, `team-board-tab-count-<column>` and `team-board-tab-needs-you-<column>`; `test/team_board_test.dart` finds them unchanged (it is integrator-owned, R08; a break goes to `sharedTestsBroken`).
9. **Reduced motion (G8):** a switch and a count change settle after one `pump()`.
10. **Overflow (G6):** at 320–1600 dp and 915×412, text 1.0, 1.3 and 2.0, LTR and RTL: no exception.

## Studio additions (Appearance revamp, 2026-10-20)

- `KitTabStrip(accent: true)`: the selected tab is a pill filled in the accent with its words in the on-accent colour, for a studio's one strip of things to customise. Default `false` (the quiet `surface3` pill).
- `KitTabSwitcher(onSwipe:)`: a horizontal swipe across the body calls `onSwipe(1)` (next) or `onSwipe(-1)` (previous) in reading direction, mirrored in right to left, above `KitTabSwitcher.swipeVelocity` (300 dp/s). Taps and inner horizontal scrollers (a carousel) are untouched.
- `KitTabSwitcher(shrinkWrap: true)`: the body takes the chosen destination's height (inside a scrolling page) instead of filling the space.

## Galleries required

`test/goldens/kit/kit_tab_switcher_golden_test.dart`, DPR 3, Android (TEST-9, TEST-20), the strip over a page stub:

- **Declared states × dark and light at 412×915:** `default` (five board columns, Working selected, counts), `counts_loading`, `needs_you`, `overflowing` (eight tabs, the last selected), `focused` (desktop capabilities). That is 10 PNGs.
- **Default × dark and light** at 360×800, 915×412, 800×1280, 1280×800 and 1600×1000: 10 PNGs.
- **Text 2.0 and Arabic** (`default`) at 412×915 and 1280×800, dark: 4 PNGs.
- **Names:** `kit_tab_switcher_<state>[_ar][_text2][_WxH]_<dark|light>.png`. That is 24 PNGs.

## Non-goals

- The shell's navigation (KitNav) and a strip of choices that do not keep separate state (KitSegmented).
- Closable, reorderable or scrollable-by-arrow-button tabs.
- Adopting the raw `TabBar`s (their wave-2 units) or editing `team_board_screen.dart`.

## Open questions

None.
