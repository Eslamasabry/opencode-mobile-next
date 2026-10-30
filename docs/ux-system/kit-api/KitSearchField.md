# KitSearchField — API freeze (wave 0, 2026-09-26)

Unit: `kit-KitSearchField` (wave 1, tier 1c, kind `kit-part`). Spec: kit-v2.md §1.5, §2.12, §8.2, §8.3; STANDARDS KIT-20, STATE-11, A11Y-3, AUTO-20, §0.2 (P2 deferred); cut review C06 (the `KitScreen.search` slot), C09 (KitChip), C25, C45 (slice-P9.4 reads this file only). STANDARDS wins where it differs from kit-v2.md.

## Purpose

The one search field for lists, sheets and viewers. It pins under the top bar through `KitScreen.search`, announces the result count once typing settles, and says when results are partial. It is also the field Settings search and the P9.4 search index run through. It draws the field and its no-match state; finding things is the host's job.

## Replaces

- **Map elements (kit-v2.json `assignment` → `KitSearchField`): 15 elements on 15 pages.** They are active-context-search, capabilities-search, command-launcher-sheet-search, command-palette-dialog-query, commands search, embedded-transcript-find-bar, files-search-field, global-sessions-search, legacy-drafts legacy-search, model-picker-sheet model-search, projects-search, session-destination-sheet-filter, settings library-search, team-home-search and web-sources web-query.
- **Files and their G16 `TextField` baseline counts:**

  | File | TextField |
  |---|---|
  | `lib/ui/screens/web_sources_screen.dart` | 4 (one is web-query; the rest go to KitField) |
  | `lib/ui/screens/projects_screen.dart` | 2 |
  | `lib/ui/desktop/shortcuts.dart` (command palette), `screens/active_context_screen.dart`, `screens/chat/command_launcher.dart`, `screens/chat/transcript_find.dart`, `screens/files_screen.dart`, `screens/global_sessions_screen.dart`, `screens/legacy_drafts_screen.dart`, `screens/library/commands_screen.dart`, `screens/session_destination_sheet.dart`, `screens/settings_screen.dart`, `screens/team/team_home_screen.dart`, `widgets/pickers.dart` (model picker) | 1 each |
  | `lib/ui/screens/capabilities_screen.dart` | 0 in G16 (built another way) |

- **Each site also carries a hand-built clear `IconButton` and a magnifier `Icon`.** They go too.
- **Not replaced here:** `lib/ui/search/search_index.dart`. The index, typo tolerance, aliases and "arrival with a highlight" belong to screen-shell-2 and slice-P9.4 (C34, C45).

## File

`lib/ui/kit/kit_search_field.dart`. It holds `KitSearchField` and `KitSearchNoMatch`. Tests go in `test/kit/kit_search_field_test.dart` and galleries in `test/goldens/kit/kit_search_field_golden_test.dart`.

## Public API

```dart
class KitSearchField extends StatefulWidget {
  const KitSearchField({
    super.key,
    required this.label,                // "Search settings": visible as the hint and the semantic label
    required this.onChanged,            // called once typing settles (debounced), and at once on clear
    this.controller,
    this.onSubmitted,                   // Enter / IME search: e.g. open the first result
    this.resultCount,                   // announced politely once typing settles: "12 results"
    this.partial = false,               // results are still coming: "12 loaded · searching the server…"
    this.filters = const [],            // List<KitMenuItem>: a labelled filter menu at the end
    this.activeFilter,                  // shown as words in a removable KitChip: "Symbols ×"
    this.onClearFilter,                 // the chip's remove; required when activeFilter != null
    this.enabled = true,
    this.disabledReason,                // required when !enabled
    this.autofocus = false,
    this.focusNode,
    this.fieldKey,
    this.clearKey,
    this.filterKey,
  });

  /// A search earns its place when the list can pass about 8 items
  /// (kit-v2.md §1.5); hosts ask this instead of comparing to a literal.
  static bool worthShowing(int itemCount) => itemCount > showAbove;
  static const int showAbove = 8;
}

/// The inline "nothing matches" state under a search: a KitStateView
/// (inline) with "Clear search" and at most one further way forward.
class KitSearchNoMatch extends StatelessWidget {
  const KitSearchNoMatch({
    super.key,
    required this.query,               // shown isolated: Nothing matches "opus"
    required this.onClear,             // "Clear search"
    this.what,                         // "models": Nothing in models matches "opus"
    this.action,                       // one more way forward, e.g. "Search all projects"
    this.titleKey,
    this.calm = false,                 // KitStateView.calm inline: centred words, Clear search as text
  });
}
```

- **Assistant hook (P2 deferred).** The only hooks are `onSubmitted` and `KitSearchNoMatch.action`, which is a generic `KitAction`. When the owner approves P2, "Ask the setup assistant" will plug into `action`. Until then no caller passes an assistant action, and no row leads to one (AUTO-20, STANDARDS §0.2). The kit ships no assistant-specific parameter.
- **Filters.** A menu of `KitMenuItem`s: the type exists today in `kit_row_parts.dart`, and kit-KitMenu v2 adds checked and group. It opens with `showKitMenu` from a labelled button "Filter" at the end of the field, never an unlabelled caret. The active filter is shown as words in a removable `KitChip`.
- **Where it sits.** The host puts it in `KitScreen.search` (pinned under the bar; kit-KitScreen-v2, C06) or at the top of a sheet body. It is on the 16 dp rails (`KitTokens.gutter`). It never floats in glass: glass is only for the top controls' search button (LOOK-27), which opens a screen with this field.
- **Kit copy** (ARB, `kit` prefix):
  - `kitSearchClear` "Clear search";
  - `kitSearchFilter` "Filter";
  - `kitSearchFilterActive` "Filter: {name}";
  - `kitSearchRemoveFilter` "Remove filter {name}";
  - `kitSearchResults`, an ICU plural: "{count, plural, =0{No results} =1{1 result} other{{count} results}}";
  - `kitSearchPartial` "{count} loaded · searching the server…";
  - `kitSearchNoMatch` "Nothing matches {query}";
  - `kitSearchNoMatchIn` "Nothing in {what} matches {query}".

## States

The doc comment declares: `empty` (no query), `typing` (query with the clear button), `results` (count known), `partial` (still searching), `no-match` (`KitSearchNoMatch`), `filtered` (active filter chip), `disabled`.

- **Loading.** The field does not draw a spinner. Remote results that are loading use the screen's one `KitLoadingBar` (STATE-4), and `partial: true` changes the count line to "12 loaded · searching the server…" (STATE-11).
- **Empty.** With no query there is no clear button, no count and no announcement.
- **No match.** The host renders `KitSearchNoMatch` in place of the list: "Nothing matches 'opus'", **Clear search**, plus at most one `action`.
- **Error.** The host's `KitNotice` on the list, not the field. The field keeps the query.
- **Disabled.** Dimmed, with its reason in a line under it (STATE-8).
- **Working / answered.** Not applicable.

## Tokens

All of the following exist on `feat/visual-language-v1` unless flagged.

- **ThemeRoles:**
  - `surface1`: the fill;
  - `hairline`: the border at rest;
  - `accent`: the focus ring, cursor and selection;
  - `text1`: the query;
  - `text3`: the hint and the magnifier;
  - `text2`: the count line, clear icon and filter label.
- **KitText roles:** `body` for the query, `secondary` for the count line and filter label, `rowTitle` and `secondary` for the no-match title and body (through `KitStateView`).
- **KitTokens:**
  - `buttonHeight` (50): the field height;
  - `minTarget` (48);
  - `gutter` (16);
  - `space2`–`space4`;
  - `smallIconSize` (20): the magnifier and clear icon;
  - `maxIconScale`.
- **KitMotion:** `quick` for the clear button's fade.
- **New tokens (pre-wave, `_new-tokens.md`):**
  - `KitTokens.fieldRadius` (14): shared with KitField; see KitField.md.
  - `KitMotion.typingSettle`, about 300 ms: the debounce, and the delay before the result count is announced. It is a named kit wait under MOT-1, so it is not a literal in this file. Owner: the pre-wave `kit_motion.dart` seam (STANDARDS §0.5 step 2) or this unit, whichever the coordinator names.

## Adaptive

- **Compact.** Full width on the 16 dp rails, pinned under the top bar (`KitScreen.search`) or at the top of a sheet body. On compact, the filter button is a `KitIconButton` labelled "Filter", with the label as both its tooltip and its semantic name. From medium up it shows the word "Filter". An active filter always shows as a chip with words, on a second line if needed.
- **Medium.** The same, in the host's column. `KitScreen` caps a list at `KitLayout.listWidth`.
- **Expanded and large.**
  - In two panes (`KitScreen.twoPane`), the field sits at the top of the list pane (`paneListWidth`).
  - The filter button shows its word.
  - Ctrl+F or Cmd+F focusing the field is a shortcut registered by the shell's shortcuts layer (it draws nothing, so it stays outside the kit); the field exposes `focusNode` for it.
- **Keyboard (§8.3, G14):**
  - Esc clears the query first. With an empty query, Esc is not consumed, so the screen or modal leaves.
  - Enter settles the query at once (a pending `onChanged` runs now, once), then calls `onSubmitted`.
  - Arrow Down moves focus to the first result (the next focus in traversal).
  - Tab goes field → filter → clear, in reading order.
  - The back gesture does the same as Esc (it clears first, then leaves) through a `PopScope` inside the part.
- **Fine pointer.** The clear and filter buttons show tooltips that repeat their labels. Targets stay 48 dp.

## Accessibility

- The field's semantic label is `label` ("Search settings"), with `textField: true`. The magnifier is decorative (excluded).
- Clear is a 48 dp `KitIconButton` labelled "Clear search". Filter is a labelled button that says its state ("Filter: Symbols").
- **Announcements.** The result count is a polite live region, announced once after `KitMotion.typingSettle` with no further keystroke, never on every keystroke (A11Y-3). A partial count says so. `KitSearchNoMatch` is announced once when it replaces the list.
- **200 % text.** The field grows in height. The count line and the filter chip wrap below the field and are never clipped. The hint is never the only label: the hint equals the semantic label, so nothing is lost when it disappears.

## RTL

- The magnifier sits at the start and clear at the end, both directional; the magnifier glyph does not mirror (LAY-8).
- The query follows the typed text's direction.
- In `KitSearchNoMatch` the query is isolated with `KitBidi.auto` (a word the person typed), and the filter name with `KitBidi.auto`.
- The count uses `intl` plurals and digits (COPY-30).

## Motion and haptics

- The clear button fades on `KitMotion.quick`.
- The filter chip and the count line appear with `KitReveal`.
- The list the host swaps (results and no-match) is the host's `KitAnimatedRows` or a plain swap, never `AnimatedSize` (MOT-5).
- Everything is instant under `KitMotion.reduced`.
- Haptics: none.

## Data safety and honest state

- **The query survives.** It survives rebuilds, and filter changes keep it. Clearing is one tap and is visible. The query is not persisted across screens: search text is not work (DATA-1 does not cover it).
- **No secret, token or path is ever searched through a log in this part.** The field treats its query as plain text and never sends it anywhere itself. The host decides.
- **Honest counts (STATE-11).** "12 results" only when the search is complete. `partial: true` says "12 loaded · searching the server…". The no-match state never appears while `partial` is true: the host shows the loading bar instead.
- **Honest filters.** An active filter is always visible as words, so an empty result is never caused by a hidden filter.

## Depends on

- **KitField** (kit-KitField): the shared field chrome, the focus ring and the `fieldRadius` token use.
- **KitChip** (kit-KitChip, C09): the removable active-filter chip.
- **KitMenu** (kit-KitMenu, C07): `showKitMenu` for the filter menu. Edge added (README.md); tier 1a, so no tier change.
- **KitIconButton v2** (kit-KitIconButton-v2): clear and filter (transitively through Field).
- **KitStateView** (existing; v2 later): `KitSearchNoMatch` builds on its inline size.
- **KitBidi** (a pre-wave seam).
- **Existing parts:** `KitReveal`, `KitMotion`, `KitMenuItem`, and `KitText`/`ThemeRoles`/`KitTokens` (VL).
- **Used by:** kit-KitScreen-v2 (the `search` slot, C06) and kit-KitViewer (C25).

## Tests required

In `test/kit/kit_search_field_test.dart` (G9, G14, TEST-15):

1. `onChanged` fires once after typing settles (fake async `KitMotion.typingSettle`), not per keystroke. Clear fires `onChanged('')` at once.
2. Clear appears only with a query, is labelled "Clear search", is 48 dp, and empties the field and keeps focus.
3. Esc with a query clears it and is consumed. Esc with an empty query is not consumed (a test route pops). The back gesture behaves the same.
4. `resultCount` is announced once after settling ("12 results") and not on each keystroke. `partial: true` shows and announces "12 loaded · searching the server…".
5. `KitSearchNoMatch`:
   - it shows the isolated query and "Clear search";
   - Clear calls `onClear`;
   - `action` renders at most one extra action;
   - it is announced once.
6. Filters: the button is labelled "Filter", the menu lists the items, and choosing one calls it. `activeFilter` shows a removable chip with its words, and removing calls `onClearFilter`. An active filter without `onClearFilter` asserts.
7. A disabled field without `disabledReason` asserts. Its reason is visible.
8. Enter settles a pending query through `onChanged` at once (once only), then calls `onSubmitted`. Arrow Down moves focus out of the field to the next focusable.
9. RTL: the magnifier is at the start and clear at the end, and the magnifier glyph is not mirrored.
10. `worthShowing(8)` is false and `worthShowing(9)` is true.
11. Under reduced motion one `pump()` settles.
12. Overflow (G6): at 320–1600 dp and 915×412, text 1.0, 1.3 and 2.0, LTR and RTL, there is no exception, and the chip and count wrap at 2.0.

## Galleries required

`test/goldens/kit/kit_search_field_golden_test.dart`, at DPR 3 (TEST-9, 2 × 7 + 18 = 32 PNGs):

- **Every declared state, dark and light, at 412×915.** The states are empty, typing, results (count line), partial, no-match (field plus `KitSearchNoMatch`), filtered (active chip) and disabled. Each is pinned under a top bar in a `KitScreen` fixture.
- **The typing state, dark and light, at the other sizes:** 360×800, 915×412, 800×1280, 1280×800 (the two-pane list pane) and 1600×1000.
- **The typing state at text 2.0 and in Arabic RTL, dark and light, at 412×915 and 1280×800.**

## Non-goals

- No search index, ranking, typo tolerance, aliases or result highlighting on arrival (search_index.dart; screen-shell-2 and slice-P9.4).
- No result list or result rows: the host uses `KitRow`.
- No voice search.
- No assistant action or "Ask to set it up" (P2 deferred, AUTO-20).
- No glass variant.
- No persistence of queries or recent searches.
- No migration of call sites (wave 2).

## Open questions

- **A search that stays pinned in a sheet (coordinator).** kit-v2.md §1.5 places the field "in … the header of a KitSheet", but `showKitSheet` has no slot that stays still while the body scrolls. kit-KitSheet-v2 (tier 1a, C17) runs before this part exists, so it cannot name `KitSearchField`. The coordinator should decide one of these:
  - (a) kit-KitSheet-v2 adds a generic pinned slot, for example `Widget? pinned`, restricted by review to `KitSearchField`;
  - (b) a follow-up unit after this one adds `KitSearchField? search` to `showKitSheet`;
  - (c) sheets with search scroll the field away.

  Until then, sheet hosts put the field at the top of the body, and it scrolls away.
