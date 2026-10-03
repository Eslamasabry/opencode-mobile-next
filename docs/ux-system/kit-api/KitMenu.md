# KitMenu: API freeze (wave 0, 2026-09-26)

> **Copy (SEC-13, coordinator 2026-09-27):** copies of technical values, logs and details use `KitCopy.copy(context, text)` (redacted). A part that also copies the person's own content passes `redact: false` for that content.

Unit: `kit-KitMenu` (wave 1, tier 1a, kit-part). Spec: cut review C07 (a new part; K2 has no section for it), kit-v2.md §4.2 (destructive last), §8.2 (KitRow row), §8.3 (right-click and long-press open the same menu), visual language §1 and §5 (no per-row ⋮). Rules: KIT-2, KIT-8, KIT-11, KIT-23, KIT-28, LAY-9, LAY-10, LAY-11, LOOK-5, LOOK-19, LOOK-20, MOT-2, A11Y-5, and Appendix A #82.

## Purpose

It is the one popup menu in the app, used by the row menu (`KitRowMenu`, and `KitRow.menu` on long-press and right-click), the top bar's overflow, the action block's "More" and the composer's chips. It opens at a point or anchored to a widget, lists `KitMenuItem`s with an optional icon, a check, groups and a disabled reason, and always puts destructive items last, after a divider.

## Replaces

- **Code (`test/kit_ratchet_baseline.json`, G16, outside the kit), 84 uses in 21 files:**
  - `PopupMenuButton`, 22 uses in 19 files:
    - `screens/chat/team_conversation_view.dart`, `screens/files_screen.dart` (2), `screens/global_sessions_screen.dart`;
    - `screens/library/integration_tiles.dart`, `screens/local_terminal_screen.dart`, `screens/managed_workspaces_screen.dart`;
    - `screens/review_workspace.dart`, `screens/session_relations_screen.dart`;
    - `screens/team/agent_screen.dart`, `screens/team/run_screen.dart`, `screens/team/team_board_screen.dart`, `screens/team/team_home_screen.dart`;
    - `screens/terminal_screen.dart`, `screens/workspace_screen.dart` (3), `screens/worktrees_screen.dart`;
    - `widgets/local_agent_onboarding.dart`, `widgets/markdown.dart`, `widgets/model_shortcuts.dart`, `widgets/phone_server_card.dart`.
  - `PopupMenuItem`, 53 uses in 19 files.
  - `CheckedPopupMenuItem`, 7 uses in 5 files: `files_screen.dart` (3), `local_terminal_screen.dart`, `team_board_screen.dart`, `team_home_screen.dart`, `widgets/markdown.dart`.
  - `PopupMenuDivider`, 2 uses: `local_terminal_screen.dart`, `workspace_screen.dart`.
- **The desktop right-click menu:** `showContextMenu`/`ContextMenuAction` in `lib/ui/desktop/context_menu.dart` (a raw `showMenu` with `PopupMenuItem`s). `ContextMenuRegion` uses it in 7 files: `terminal_screen`, `worktrees_screen`, `workspace_screen`, `global_sessions_screen`, `files_screen`, `managed_workspaces_screen` and `chat/message_view`. Those move to `KitTappable`/`KitRow.menu` in wave 2, and `context_menu.dart` becomes a forwarding wrapper, then is deleted by the unit that empties it.
- **Kit-internal menus, rebuilt on it later:**
  - `KitRowMenu` (kit_row_parts.dart:74-113, `PopupMenuButton`), by kit-KitRowParts-v2;
  - `KitActionBlock`'s "More" (kit_buttons.dart, `PopupMenuButton`), by kit-KitAction-v2 (see Open questions).
- **Map:** the 11 `KitRowMenu` elements in kit-v2.json reach it through KitRowMenu v2:
  - `chat#chat-appbar-session-menu`, `embedded-context-menu-region#embedded-context-menu-region-item`, `embedded-local-agent-onboarding-block#more-menu`;
  - `files#files-order-menu`, `global-sessions#global-sessions-row-menu`, `managed-workspaces#managed-workspaces-tile-menu`;
  - `review-workspace#review-workspace-file-actions`, `team-run#team-run-more`, `workspace#workspace-session-menu`;
  - `workspace-archived-sheet#workspace-archived-sheet-menu`, `worktrees#worktrees-tile-menu`.

## File

`lib/ui/kit/kit_menu.dart` (new). Tests go in `test/kit/kit_menu_test.dart`, and the gallery in `test/goldens/kit/kit_menu_golden_test.dart`.

`KitMenuItem` moves here from `kit_row_parts.dart`. This needs two lines in kit_row_parts.dart: delete the class, and add `export 'kit_menu.dart' show KitMenuItem;`. See Open questions.

## Public API

```dart
/// One entry of a KitMenu, a KitRowMenu, a KitRow's menu or an action
/// block's "More". A superset of today's KitMenuItem (label, onSelected,
/// key, destructive, enabled keep their meaning and defaults, KIT-43).
@immutable
class KitMenuItem {
  const KitMenuItem({
    required this.label,
    required this.onSelected,
    this.key,
    this.destructive = false,
    this.enabled = true,
    this.icon,
    this.checked,
    this.group,
    this.disabledReason,
    this.shortcut,
  }) : copyText = null;

  /// A copy entry (KIT-23): selecting it runs KitCopy.copy(context, text())
  /// after the menu closes; "Copied" is announced once; no SnackBar.
  KitMenuItem.copy({
    required this.label,              // "Copy message", "Copy path"
    required String Function() text,  // read at selection time
    this.key,
    this.icon = AppIconography.copy,
    this.group,
    this.shortcut,
    this.redact = true,               // false: the person's own content, verbatim (SEC-13)
  }) : copyText = text,
       onSelected = _noop,
       destructive = false,
       enabled = true,
       checked = null,
       disabledReason = null;

  /// A verb that names what happens: "Archive conversation".
  final String label;

  /// Runs after the menu has closed (so a confirmation it opens never
  /// stacks on the menu).
  final VoidCallback onSelected;
  final Key? key;

  /// An act that loses data or ends running work (LOOK-5): label and icon in
  /// `danger`. Always shown last, after a divider (§4.2). What it opens
  /// confirms or offers Undo per DATA-11; the item itself never decides.
  final bool destructive;

  /// False: shown in `text3`, not selectable, with [disabledReason] as a
  /// second line (STATE-8). A disabled item with no reason should be left
  /// out; `KitAsserts.strict` asserts on it (KitAction.md, Open question 1).
  final bool enabled;
  final String? disabledReason;

  /// A 20 dp glyph at the start (one glyph per verb, COPY-18).
  final IconData? icon;

  /// Null: not checkable. True/false: a checkable item; the check sits in
  /// the start slot in `accent` (LOOK-6 current-selection mark), with
  /// `checked` semantics. Replaces CheckedPopupMenuItem.
  final bool? checked;

  /// Items with the same [group] sit together; a hairline divider separates
  /// consecutive groups. Null is its own group. Replaces PopupMenuDivider.
  /// A KitMenuGroup also names its group: a muted heading above its first
  /// item ("Go to", "Do").
  final Object? group;
}

/// A named group (slice-P10.1-2): equal by label; its heading is a header,
/// never focusable or selectable.
@immutable
class KitMenuGroup {
  const KitMenuGroup(this.label);
  final String label;

  /// "Ctrl+Shift+C", shown at the end on a fine pointer (display only).
  final String? shortcut;

  /// Set only by [KitMenuItem.copy].
  final String Function()? copyText;
}

/// Opens the one popup menu (KIT-11: modal parts return a Future).
///
/// [position]: a global point (right-click, long-press). Null: anchored to
/// [context]'s render box, opening below it (above when there is no room)
/// and aligned to its end edge.
///
/// Returns the chosen item after its [KitMenuItem.onSelected] (or copy) has
/// run, or null when dismissed. Callers never act on the result a second
/// time; it exists for tests and focus return.
///
/// An empty [items] returns null at once without opening anything.
Future<KitMenuItem?> showKitMenu(
  BuildContext context, {
  required List<KitMenuItem> items,
  Offset? position,
  String? semanticsLabel,   // the menu's name: "Conversation actions"
  Key? menuKey,
});

/// The menu's panel on its own, without a route: for galleries and for a
/// part that shows a menu inline (a KitTopBar overflow on a PC). The same
/// ordering, grouping and item look as [showKitMenu].
class KitMenuPanel extends StatelessWidget {
  const KitMenuPanel({
    super.key,
    required this.items,
    required this.onSelected,  // ValueChanged<KitMenuItem>
    this.semanticsLabel,
  });
}
```

- **Ordering (structural, not an assert).**
  1. Items keep their order within their group.
  2. Groups keep the order of their first item.
  3. All `destructive` items are then moved, in a stable order, after a divider at the end.

  This makes §4.2 and the G37 "KitRowMenu destructive items last" hold by construction. The test checks the rendered order.
- **Internal keys (TEST-5):** `kit-menu` (the panel), `kit-menu-divider`, and each item's own `key` on its row.

## States

| State | Look / behaviour |
|---|---|
| default | the panel with items |
| with icons | a 20 dp glyph slot at the start; items without an icon keep the slot empty so labels align, when any item in the menu has an icon or a check |
| checked | a check glyph in `accent` in the start slot; an unchecked checkable item leaves the slot empty |
| groups | a 1-physical-pixel `hairline` divider between groups |
| named groups | a `KitMenuGroup` group also draws its label (`label` role, secondary) above its first item, key `kit-menu-heading-<label>`, semantics header; the conversation menu's "Go to" and "Do" (slice-P10.1-2) |
| destructive | the label and glyph in `danger`, last, after a divider |
| with supporting (slice-P3.3) | `supporting` is one `text2` (`secondary`) line under the label that says what choosing the item means ("Edits files and runs commands"), for a menu of choices whose names alone do not say it |
| disabled | the label in `text3`, the `disabledReason` line in `text2` (`secondary`), not focusable for selection, but still read by a screen reader |
| empty | not opened; the Future resolves to null |

The menu shows no server data, so it has no loading or error state. A caller that must fetch items first shows a working `KitIconButton` until they arrive (KIT-12).

## Tokens

- **ThemeRoles:** `surface2` (the panel), `hairline` (the panel's border and the dividers), `surface3` (hover and focus highlight of an item), `text1`, `text2`, `text3`, `accent` (check and focus ring), `danger`.
- **KitTokens:**
  - `minTarget` (48, each item's minimum height);
  - `space2`, `space3`, `space4` (item padding: 16 at the sides, 12 between glyph and label);
  - `smallIconSize` (20, the glyph);
  - `rowTitle` (the item label; KitText `rowTitle`);
  - `note` (the disabled reason; KitText `secondary`).
- **KitText:** `rowTitle`, `secondary`, `mono` (the shortcut).
- **KitMotion:** `quick`, `enter`, `exit`, `reduced(context)`.
- **Not yet on the VL branch** (STANDARDS §0.5 step 2):
  - `KitTokens.hairlineWidth(context)` for the border and dividers;
  - `KitTokens.focusRingWidth(context)`;
  - `KitCopy.copy`;
  - `KitBidi.ltr` for the shortcut.
- **New tokens (pre-wave, `_new-tokens.md`):**
  - `KitTokens.popoverRadius` = 14, the panel's corners, shared with the KitIconButton tooltip and the KitTerm bubble.
  - `KitLayout.popoverMinWidth` = 200 and `KitLayout.popoverMaxWidth` = 320: the popover widths the KitMenu panel and the KitTerm bubble share (README.md decision D2). They are named layout widths (LAY-2), and the part never uses a literal.
- **Depth:** the panel has no elevation and no shadow (LOOK-20: popups 0). It is separated by the `surface2` step and the hairline border.

## Adaptive

| Window | Behaviour |
|---|---|
| compact | Opens at the long-press point, or anchored to the invoker. Width `popoverMinWidth`–`popoverMaxWidth`, clamped to the window minus the 16 dp gutter. Items are 48 dp or taller. |
| medium | the same |
| expanded / large | The same popup, never a sheet or dialog. With a fine pointer, hovering highlights an item, and the shortcut column shows. It opens at the right-click point. |

- **A short window (`KitLayout.isShort`):** the panel scrolls inside the window height, with pinned edges.
- **Keyboard (LAY-10, G14x):**
  - A menu opened from the keyboard (Shift+F10, the context-menu key, or Enter on a `KitRowMenu`) focuses the first enabled item. One opened by pointer focuses the panel.
  - Up and Down move between enabled items and wrap; Home and End jump; Enter and Space select; Esc and Back close.
  - Focus returns to the invoker on close.
- **Right-click and long-press (§8.3, LAY-11):** the same list opens from either. The part that owns the gesture (`KitTappable`, `KitRow`) calls `showKitMenu` with the gesture's position.

## Accessibility

- **The panel:**
  - `Semantics(scopesRoute: true, namesRoute: true, explicitChildNodes: true)`, named by `semanticsLabel` or, when it is absent, by l10n.kitMenu ("Menu");
  - `scopesRoute` and `namesRoute` are set so a screen reader announces the menu when it opens.
- **Each item:**
  - a `button` with its `label`;
  - `enabled` follows `enabled`;
  - `checked` when checkable;
  - the hint is `disabledReason`, or the shortcut.
- **48 dp items** at every window (LAY-9). There is no dense desktop variant (§8.3).
- **Twins:** the same items are exposed as semantic custom actions on the host row by `KitRow.menu` (KIT-28), so a screen reader never needs the long-press (A11Y-5).
- **Announcements:** none on open, beyond the route name; none on select. The act's own result is announced by its host.
- **200 % text:** labels wrap to two lines and are never cut (A11Y-8); the panel grows up to `popoverMaxWidth` and then wraps. The disabled reason wraps. The panel scrolls when it is taller than the window.

## RTL

- Anchored to the end edge of the invoker (mirrored); the glyph and check are at the start, the shortcut at the end.
- All padding is `EdgeInsetsDirectional`.
- The shortcut is isolated with `KitBidi.ltr`.
- A label from the server (an agent's name) is wrapped by the caller with `KitBidi.auto` (COPY-30).

## Motion and haptics

- **Open and close:** a cross-fade of the panel on `KitMotion.quick` (`enter` in, `exit` out). There is no scale, size or fade-scale animation: Material's popup size tween is replaced (MOT-2).
- **Reduced motion:** it appears and disappears at once and settles after one `pump()` (G8).
- **Item highlight:** it follows focus and hover instantly.
- **Haptics:** none (MOT-11: menus, rows and copy are silent).

## Data safety and honest state

- **Destructive items:** never decide their own treatment. The item runs `onSelected` after the menu closes, and the act confirms or offers Undo per DATA-11, the same as from every other door. A menu entry and a swipe for the same act behave alike (KIT-29).
- **Copy items:** go through `KitCopy.copy`, which redacts (G12) and never receives a secret; only `redact: false`, for the person's own content, copies verbatim (SEC-13, R5).
- **Disabled items:** show their reason as visible text (STATE-8), never only in a tooltip.
- **No stacking:** `onSelected` runs after the popup route has popped, so an item that opens `showKitConfirm` from inside a `KitSheet` replaces the sheet's content (KIT-16), never stacking on the menu.

## Depends on

- **Units:** none (tier 1a, C25).
- **Shared seams (STANDARDS §0.5 step 2):** `KitCopy`, `KitBidi`, `KitTokens.hairlineWidth`, `KitTokens.focusRingWidth`.
- **Depend on it (C25):**
  - kit-KitTappable, kit-KitTopBar, kit-KitViewer, kit-KitRowParts-v2, kit-KitComposerChips;
  - through RowParts: kit-KitRow-v2.
  - Not kit-KitAction-v2 in wave 1 (README.md, decision D3): kit-hygiene moves its "More" onto `showKitMenu`.

## Tests required

These go in `test/kit/kit_menu_test.dart` (G9, G14x, G37):

1. `showKitMenu` returns the tapped item after its `onSelected` ran exactly once. Dismissing it (tap outside, Esc, Back) returns null and runs nothing.
2. Destructive items render last, after a `kit-menu-divider`, even when passed first. Order within groups is kept, and groups are separated by one divider each.
3. With `checked: true`, the item exposes `checked` semantics and paints the check. With `checked: false`, the slot is empty and the item is still `checked: false` in semantics.
4. A disabled item shows its `disabledReason` text, cannot be selected by tap or Enter, and exposes the reason as its hint.
5. `KitMenuItem.copy`:
   - the clipboard is set after the menu closes;
   - "Copied" is announced once;
   - no `SnackBar`;
   - a fake provider key is redacted (G12).
6. With an empty `items`, the Future resolves to null and no route is pushed.
7. Keyboard, with desktop capabilities:
   - opening from the keyboard focuses the first enabled item;
   - Down and Up skip disabled items and wrap;
   - Enter selects; Esc closes;
   - focus returns to the invoker.
8. Anchoring: with `position`, the panel's top-start corner is at the point, clamped inside the window. Anchored in RTL, the panel aligns to the invoker's end edge.
9. Each item is 48 dp or taller at text 1.0 and 2.0. At 2.0 no label is ellipsised, and there is no overflow at 320 dp wide.
10. `onSelected` of an item that calls `showKitConfirm` runs after the menu route is gone: the navigator's history does not contain the menu route.
11. Reduced motion (G8): it opens and closes within one `pump()` with no ticker running.
12. The retired `KitMenuItem(label:, onSelected:, key:, destructive:, enabled:)` constructor still compiles and behaves the same (KIT-43).

## Galleries required

These go in `test/goldens/kit/kit_menu_golden_test.dart`, rendering `KitMenuPanel` over a `ground` screen with a `surface1` row as the invoker, at DPR 3.0 (TEST-9, TEST-20):

- **Each state at 412×915, dark and light:** `default`, `icons`, `checked`, `groups`, `destructive` and `disabled`, which is 12 PNGs. The `destructive` scene also includes an icon and a group.
- **`default` at 360×800, 915×412, 800×1280, 1280×800 and 1600×1000, dark and light:** 10 PNGs. The 1280 and 1600 shots show the shortcut column and one hovered item.
- **`default` at text 2.0 and Arabic RTL, at 412×915 and 1280×800, dark and light:** 8 PNGs.
- **Total:** 30 PNGs.

Used by the composer's approval chip (screens/chat/approval_mode_menu.dart): three checkable `supporting` items for the modes and a last "Approval settings…" item in its own group.

## Non-goals

- No screen migration: the 84 popup-menu uses move in wave 2.
- No submenus, no search inside the menu, no multi-select menu (filters are `KitChip.action(selected:)` or `KitSegmented`).
- No bottom-sheet variant on phones: a list of many actions on one thing is a `KitSheet` of `KitRow`s (§1.1 "a list of actions on one thing").
- It does not edit `kit_row_parts.dart` beyond moving `KitMenuItem` (KitRowMenu is rebuilt by kit-KitRowParts-v2).

## Open questions

Both are settled in the cross-check (README.md); they are recorded here for the builder.

1. **Where `KitMenuItem` lives (decision D6, a build_units change).**
   - **The problem:** C07 gives kit-KitMenu (tier 1a) "KitMenuItem v2" and says it does not edit `kit_row_parts.dart`. But `KitMenuItem` is declared at `kit_row_parts.dart:53`, which is also exported by `kit.dart`. Two public classes named `KitMenuItem` would break `kit.dart`'s exports, an ambiguous export, as soon as the integrator adds `kit_menu.dart` (R06).
   - **Proposed:** add a two-line edit of `kit_row_parts.dart` to kit-KitMenu's write set: remove the class, and add `export 'kit_menu.dart' show KitMenuItem;`. kit-KitRowParts-v2 runs in tier 1d, after 1a is integrated, so the two edits never overlap.
   - **Adopted.** `build_units.py`'s write-set assertion is per concurrency set, and kit-KitRowParts-v2 (tier 1d) is never concurrent with this unit (tier 1a). If the change is not made, kit-KitMenu must name its item class differently, which the idioms rule out, so the unit would be blocked (PROC-32).
2. **KitActionBlock's "More" (decision D3).** Not moved in wave 1: adding Menu and IconButton-v2 to kit-KitAction-v2 would move it to tier 1b and push the five parts that take Action-v2 as a tier-1a edge (Field, DetailsFold, Dialog, Nav, TopBar) one tier later. kit-hygiene (2d) moves it.
