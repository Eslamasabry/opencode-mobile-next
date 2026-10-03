# KitRow v2: API freeze (wave 0, 2026-09-26)

Unit: `kit-KitRow-v2` (wave 1, tier 1e, kit-change), whose write set is `lib/ui/kit/kit_row.dart` plus the new `lib/ui/kit/kit_swipe_action.dart`, frozen in KitSwipeAction.md (cut review C14).

- **Spec:** kit-v2.md §2.5 (unavailable, server label, swipe, destructive rows last, no ListTile look-alike), §4.1, §4.2, §8.2 (KitRow row: hover, focus ring, right-click and long-press open KitRowMenu, Enter activates), §8.3; design-standard.md §6; visual language §4–§5 (rows, grouped panels, no per-row ⋮).
- **Cut review:** C14, C20 (the capability registry), C24 correction (`GatedRow`/`GatedRowTile` become KitRow wrappers in shared-system-1).
- **Rules:** KIT-26, KIT-27, KIT-28, KIT-29, STATE-8, STATE-9, STATE-12, STATE-13, LAY-9, LAY-10, LAY-11, LOOK-5, LOOK-14, A11Y-5, A11Y-8, COPY-30, and Appendix A #20.

## Purpose

This is the one list row: a leading icon tile, a title, a muted supporting line and a trailing value, chevron or one icon action, on a grouped `surface1` panel. v2 adds five things:

- **Unavailable:** an honest unavailable row that says why and offers the enable flow.
- **Server label:** a small server label for rows from another server.
- **Pointer and keyboard:** hover, a focus ring, and right-click, long-press or the context key opening the row's menu, which is also exposed as semantic actions.
- **Swipe:** a swipe that always offers Undo.
- **Destructive order:** destructive rows placed last in their group, after a divider.

## Replaces

- **Map (kit-v2.json `existingAdoption` KitRow, 90 elements on 57 pages):**
  - `about-open-source-tab` (open-source-summary); `active-context` (active-context-tile); `activity` (background-hint, digests-toggle, running-row); `app-diagnostics` (diagnostic-entry, perf-slowest-steps); `appearance-settings` (language-row); `capabilities` (command-row);
  - `chat-message-actions-sheet` (copy, delete, fork, read-aloud, revert); `command-launcher-sheet` (agent-row, command-row, search-result); `command-palette-dialog` (row); `commands` (command-row); `external-agents` (row);
  - `files` (changes-card, row); `files-changes-sheet` (file); `files-row-actions-sheet` (item); `gate-sheet` (gate-work-chip); `global-sessions` (row); `guide` (advanced, connection-help); `host-management` (facts);
  - `integrations` (mcp-row, provider-row); `integrations-connect-method-sheet` (method-rows); `legacy-drafts` (legacy-draft-row); `manage-project` (header, rows); `managed-workspaces` (adapters, tile); `model-picker-sheet` (model-row);
  - `phone-setup-start` (other-ways); `profile-monitor` (server); `project-health` (rows); `project-hub` (context, tool-row); `projects` (open-folder, project-tile); `prompt-history-sheet` (prompt-row); `prompt-stash-sheet` (row); `prompt-tools-sheet` (advanced, clear, prompts, rows);
  - `references` (reference-row); `run-result` (rows, summary); `running-work-sheet` (agent, shell); `saved-permissions` (saved-permission-row); `server-capabilities` (capability-row); `server-switcher-sheet` (add, manage); `session-approvals-sheet` (log); `session-import` (destination);
  - `session-menu-sheet` (actions, actions-expander, display-expander, go-to-chips); `session-relations` (row); `skills` (skill-row); `staged-revert` (file-row, prompt); `start-run-sheet` (start-planner); `tailscale-setup` (help);
  - `terminal` (process-row); `termux-processes` (process-row); `termux-storage` (category-rows); `timeline-sheet` (row); `todos-sheet` (todo-row); `tools` (model-header, tool-row); `web-sources` (web-paste, web-result); `work-sheet` (work-dependency-chip);
  - `workspace-archived-sheet` (row); `workspace-context-sheet` (header, local, manage-project, switch-project, workspace); `worktrees` (primary-tile, tile).
- **Code (G16, outside the kit):**
  - `ListTile`, 142 uses in 54 files (the spec counts 147 plain). The switch, radio and checkbox kinds go to `KitSwitchRow` (KitRowParts.md) and `KitChoiceRow`.
  - `ListTileTheme`, 1 use.
  - The row share of `InkWell` (47 uses in 24 files) and `GestureDetector` (8), with the rest going to `KitTappable`.
  - `ContextMenuRegion` wrapping rows in 7 files (the right-click half, KitMenu.md).
- **Absorbed widgets:**
  - `GatedRow`/`GatedRowTile` (`lib/ui/widgets/product_states.dart`) become forwarding wrappers over `KitRow`/`KitRow.unavailable`, in shared-system-1 (C24 correction);
  - the proposed `KitCapabilityRow` is `KitRow.unavailable`;
  - `SwipeDeleteBackground` goes to `KitSwipeAction`.
- **Behaviour it retires:** `Opacity(opacity: .5)` on a disabled row (kit_row.dart today; LOOK-14).

## File

`lib/ui/kit/kit_row.dart` (existing: `KitRow`, `KitRowValue` and `KitRowGroup` on the VL branch).

- **Tests:** `test/kit/kit_row_test.dart`.
- **Galleries:** `test/goldens/kit/kit_row_golden_test.dart`. It renders `KitRow` inside `KitRowGroup` with `KitRowValue`; see Open question 3.

## Public API

```dart
/// The one list row (design standard §6, visual language §5).
///
/// States: default, two-line, disabled (with reason), unavailable,
/// selected, hover, focused, destructive (KIT-12). Loading, empty and error
/// belong to the list (KitSkeletonRows, KitStateView), not the row.
class KitRow extends StatelessWidget {
  const KitRow({
    super.key,
    required this.title,
    this.leading,
    this.supporting,
    this.trailing,
    this.onTap,
    this.onLongPress,          // kept (KIT-43). Retired for menus by kit-KitRow-v2: use [menu].
    this.titleMaxLines = 1,
    this.titleIsFileName = false,
    this.supportingMaxLines = 1,
    this.below,
    this.titleKey,
    this.supportingKey,
    this.padding,
    this.enabled = true,
    this.destructive = false,
    // v2, all optional:
    this.menu = const [],      // List<KitMenuItem>
    this.menuLabel,            // String?: the opened menu's name ("Conversation actions")
    this.swipe,                // KitSwipeAction?
    this.server,               // String?: another server's name ("laptop")
    this.disabledReason,       // String?: why it cannot run now; shown as the supporting line
    this.selected = false,     // the row shown in the detail pane (twoPane)
    this.action,               // KitAction?: the row's own one action ("Turn on"); takes the trailing slot (slice-team-g17)
  }) : capability = null,
       enable = null;
  // build() asserts: onLongPress == null || menu.isEmpty ("long-press opens
  // the menu; pass one or the other"). A List's isEmpty cannot be checked in
  // a const constructor.

  /// A capability this server lacks (kit-v2.md §2.5; STATE-12): a dimmed
  /// row that says why and, when the capability can be turned on, offers
  /// the flow. Never a dead row; `whenMissing: hidden` only where no enable
  /// flow exists (built by KitCapabilityExplainer.row from the registry,
  /// kit-CapabilityExplainer, tier 1e).
  const KitRow.unavailable({
    super.key,
    required this.title,
    required String reason,    // one sentence: "Voice needs a model on this phone."
    this.enable,               // KitAction?: "Download voice model"
    this.capability,           // String?: the capabilities.json id ("voice.model")
    this.leading,
    this.server,
    this.titleKey,
    this.supportingKey,
    this.padding,
  }) : supporting = null,
       disabledReason = reason,
       enabled = false,
       trailing = null,
       onTap = null,
       onLongPress = null,
       titleMaxLines = 2,
       supportingMaxLines = 2,
       below = null,
       destructive = false,
       menu = const [],
       menuLabel = null,
       swipe = null,
       selected = false;

  final Widget? leading;
  final String title;
  final InlineSpan? supporting;
  final Widget? trailing;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;
  final int titleMaxLines;
  final int supportingMaxLines;

  /// The title is a file name: from 1.3x text it wraps only after `_`, `-`
  /// or before the extension's dot (never mid-word) and is shown whole;
  /// screen readers read the plain name (slice-polish 2026-09-28).
  final bool titleIsFileName;
  final Widget? below;
  final Key? titleKey;
  final Key? supportingKey;
  final EdgeInsetsGeometry? padding;
  final bool enabled;

  /// Deletes or ends something (LOOK-5): title in `danger`. The act
  /// confirms first or offers Undo per DATA-11 (K2 §4.1); this flag decides
  /// neither. (Corrects today's "always confirms", Appendix A #20.) In a
  /// KitRowGroup it sits last, after a divider.
  final bool destructive;

  /// The row's rarer actions (KIT-28): opened by long-press, right-click,
  /// Shift+F10 or the context-menu key via KitRowMenu.show, and exposed as
  /// semantic custom actions. Destructive items last (KitMenu ordering).
  /// A [swipe] adds its twin here automatically.
  final List<KitMenuItem> menu;
  final String? menuLabel;

  /// The row's one swipe (always Undo, never confirm; KitSwipeAction.md).
  final KitSwipeAction? swipe;

  /// Shown first in the supporting line, "laptop · …", on rows that come
  /// from another server (multi-server vertical). Wrapped with KitBidi.auto.
  final String? server;

  /// With [enabled] false: the visible reason (STATE-8), shown as the
  /// supporting line (replacing [supporting]) and as the semantic hint.
  final String? disabledReason;

  /// The row currently open in the detail pane (KitScreen.twoPane).
  final bool selected;

  /// Only on [KitRow.unavailable].
  final KitAction? enable;
  final String? capability;

  /// Unchanged: a leading icon in its 30 dp surface3 tile.
  static Widget icon(BuildContext context, IconData icon, {Color? color});
}

/// Unchanged API (VL branch). Trailing value in text3, optionally with the
/// chevron.
class KitRowValue extends StatelessWidget {
  const KitRowValue(this.value, {super.key, this.chevron = true});

  /// slice-P8.2: a count badge instead of the value (Settings' Report a
  /// problem, "errors kept"). The number sits in a small surface2 pill with
  /// danger digits ("99+" above 99), badgeHeight/badgeMinWidth, text scale
  /// clamped to badgeTextScaleMax; [value] is its semantics in words, so the
  /// number is never colour alone (STATE-9). A count of 0 or less shows only
  /// the chevron.
  const KitRowValue.count(int count, String value, {Key? key, bool chevron = true});
}

/// Rows grouped on one surface1 panel (VL branch), gaining the destructive
/// order (§2.5, §4.2): rows whose `destructive` is true sit after every
/// other row, separated by a full-width hairline (not inset). Same
/// constructor as the VL branch.
class KitRowGroup extends StatelessWidget {
  const KitRowGroup({
    super.key,
    required this.children,
    this.label,
    this.labelTrailing,
    this.leadingIcons = true,
    this.margin,
  });
}
```

- **Interaction (through `KitTappable`, kit-KitTappable).** The row's tap surface is a `KitTappable`, which replaces the `InkWell`. It provides:
  - the 48 dp minimum;
  - hover (fine pointer) and the focus ring;
  - Enter and Space activating `onTap`;
  - long-press, right-click, Shift+F10 and the context-menu key opening `KitRowMenu.show(context, menu, position: …)` when `menu` is non-empty.

  With `onLongPress` set, a long-press calls it instead (KIT-43).
- **Semantic twins (KIT-28, A11Y-5).** Each enabled `menu` item becomes a `CustomSemanticsAction(label: item.label)` on the row. A swipe's twin is added to `menu` first, so it is both a menu item and a semantic action.
- **Destructive order (structural, KitRowGroup).** `KitRowGroup` stably moves `KitRow` children with `destructive: true` to the end, behind a full-width hairline. G37's rendered-order test checks it, and there is no assert to trip on existing callers. Rows outside a `KitRowGroup` (a sheet's action list) are ordered by their caller, and the reviewer checks them (§4.2).
- **Unavailable look:**
  - the title and leading glyph are in `text3`;
  - the reason is the supporting line in `text2`, so it stays readable;
  - `enable`, when given, is a trailing tertiary `KitButton` (48 dp);
  - a tap on the row also runs `enable`, since the whole row is the target;
  - with no `enable`, the reason says where the capability is available ("Available on OpenCode 2 servers", STATE-12), and the row is not focusable for activation.
- **Disabled look (all rows):**
  - `enabled: false` paints the title in `text3` and the leading glyph in `text3`, never through `Opacity` (LOOK-14);
  - `disabledReason` replaces the supporting line;
  - there is no hover and no tap.

  A disabled row with no reason renders as today, apart from the colour change. In strict mode it asserts (KitAction.md, Open question 1).
- **`KitRow.badge` and `titleAccent` (2026-10-03, open-project polish):** `KitRow.badge(context, icon, accent: false)` is a round icon badge (same size as the tile): quiet (`surface3`) by default, or accent-tinted (accent at 18 % behind an accent glyph) for the row that starts something. `titleAccent: true` paints the title in the accent. New project (accent) and Search this phone (quiet) are siblings in one `KitRowGroup`.
- **Internal keys (TEST-5):** `kit-row-menu-action-<index>` is for tests of the semantic actions only. Existing keys (`titleKey`, `supportingKey`, callers' `ValueKey`s such as `session-subtitle-<id>`) are unchanged.

## States

| State | Look |
|---|---|
| default | title `rowTitle`/`text1`, supporting `secondary`/`text2`, 54 dp (one line) or 60 dp (two lines) |
| two-line | supporting wraps to `supportingMaxLines` |
| hover (fine pointer) | one surface step up from the `surface1` panel: `surface2` in dark, `surface3` in light, where surface1 and surface2 are both white (KitTappable's rule, B5; README.md decision D11) |
| pressed | the next step, at most `surface3` |
| focused (keyboard) | a 2-physical-pixel `accent` focus ring inside the row's bounds |
| selected | a `surface3` fill; `selected` semantics; the supporting line starts with the state word when the caller gives one (STATE-9) |
| disabled | title and leading in `text3`; reason line in `text2` |
| unavailable | as disabled, plus the trailing `enable` tertiary, or none; the reason wraps in full (never cut), and from 1.3× text `enable` sits under it (A11Y-8) |
| with action | an enabled row whose own one action (a team found on the server: "Turn on") sits where `enable` does: a trailing tertiary at 1.0×, under the supporting line from 1.3× text (A11Y-8); a tap elsewhere on the row still runs `onTap`. It takes the trailing slot (asserted), so the row shows no chevron |
| destructive | title (and a tinted leading glyph, by the caller's `KitRow.icon(color:)`) in `danger`; last in its group |
| with server | the supporting line begins "{server} · ", with the server name in `text2` |
| swipe revealing | see KitSwipeAction.md |

## Tokens

- **ThemeRoles:**
  - `surface1` (the panel, via KitRowGroup), the hover and pressed steps through `KitTokens.fillOf` (KitTappable), `surface3` (selected, and the icon tile);
  - `hairline` (inset separators and the destructive divider);
  - `text1`, `text2`, `text3`, `danger`;
  - `accent` (the focus ring; the current mark via `KitRowIcon`, unchanged).
- **KitTokens:**
  - `rowHeight` (54), `rowHeightTwoLine` (60), `minTarget`;
  - `space1`–`space4` (the row's 16/8 padding moves onto these, from today's literals; G21 kit ratchet), `gutter`, `sectionGap`, `labelGap`;
  - `iconTileSize`, `iconTileRadius`, `panelCornerRadius`;
  - `rowTitle`, `rowSupporting`, `rowValue`, `sectionLabel`, `smallIconSize`.
- **KitText:** `rowTitle`, `secondary`, `label`.
- **KitMotion:** `quick` (the hover and pressed fill cross-fade) and `reduced(context)`.
- **Not yet on the VL branch** (STANDARDS §0.5 step 2):
  - `KitTokens.hairlineWidth(context)` (separators; replaces `Divider(thickness: 0)`);
  - `KitTokens.focusRingWidth(context)`;
  - `KitBidi.auto` for `server`.
- **New tokens:** none.

## Adaptive

| Window (§8.2) | Behaviour |
|---|---|
| compact | 54 or 60 dp rows (48 dp minimum target); tap; long-press opens the menu |
| medium | the same |
| expanded / large | Hover highlight and focus ring. Right-click or long-press opens the same menu (§8.3), and Enter activates. In `KitScreen.twoPane`, tapping a row fills the detail pane instead of pushing a route (the screen's choice), and the row shows `selected`. |

- **Keyboard (LAY-10, G14x):**
  - Tab reaches each enabled row in reading order;
  - Enter and Space activate;
  - Shift+F10 and the context-menu key open the menu;
  - an unavailable row with `enable` takes focus on its button.
- **Short windows:** no change.

## Accessibility

- **The row:** one merged node: a `button` when `onTap` is set, named by the title, with the value from the supporting line.
  - `selected` sets `selected: true`;
  - disabled and unavailable rows are `enabled: false`, with the reason as the hint;
  - the menu items are custom actions (A11Y-5).
- **Words, not colour (STATE-9):** a state is never shown by colour alone. The destructive title's words name the act ("Delete conversation", COPY-8), and a selected row gets its `selected` flag plus the fill.
- **Targets:** 48 dp minimum; a trailing icon action is a `KitIconButton` with its own 48 dp area (LAY-9).
- **Live regions:** a row is not a live region (A11Y-3).
- **Traversal:** reading order is top to bottom, then start to end (A11Y-4).
- **200 % text (A11Y-8):**
  - a row title is one line below 1.3× text and two lines from 1.3×, with the full value in semantics;
  - supporting lines wrap to their max, and at large text the row raises that max: at least two lines from 1.3×, three from 2.0×, before the ellipsis (slice-polish2: "Editing workflow fil…" at 2.0). At 1.0× a list keeps its one line (design standard §6);
  - the row grows with the text, since its heights are minimums;
  - there is no overflow at 320 dp (G6).

## RTL

- The leading tile is at the start and the trailing items at the end. Padding is `EdgeInsetsDirectional` (it already is).
- The destructive divider is full width.
- The "{server} · " prefix is built from `KitBidi.auto(server)` and the separator, so a Latin server name inside Arabic keeps its order (COPY-30).
- Chevrons in `KitRowValue` and `KitChevron` mirror through `AppIcons`/`matchTextDirection` (LAY-8).

## Motion and haptics

- **Hover and pressed:** the fill cross-fades on `KitMotion.quick`, and is instant under reduced motion.
- **No layout animation:** the row never animates its own size (MOT-5). Swipe motion is in KitSwipeAction.md, and the menu's in KitMenu.md.
- **Haptics:** none (MOT-11: rows are silent).

## Data safety and honest state

- **Honest unavailability (STATE-12, STATE-13):** a missing capability is a row that says why, and offers the enable flow when one exists. It is never a dead or disabled row without words, and never silently hidden when an enable flow exists (G13).
- **Destructive:** last in the group, after a divider, never between frequent rows (the `session-menu-sheet` Revert case). The act follows DATA-11 from every door: tap, menu and swipe.
- **Swipe:** always Undo, never confirm, and always mirrored in the menu (KitSwipeAction.md, KIT-29).
- **Server label:** a row from another server says so, so an answer or an act never lands on the wrong server unnoticed.
- **Disabled:** it shows its reason as visible text (STATE-8).

## Depends on

- **kit-KitUndo:** through KitSwipeAction.
- **kit-KitTappable:** the tap surface, hover, focus and menu gestures.
- **kit-KitDivider:** the hairline separators and the destructive divider.
- **kit-KitRowParts-v2** (tier 1d): `KitRowMenu.show` and `KitMenuItem`, through kit-KitMenu (C14, C25). It is why this unit is tier 1e (README.md).
- **Shared seams (STANDARDS §0.5 step 2):** `KitBidi`, `KitTokens.hairlineWidth`/`focusRingWidth`.
- **Depend on it:** kit-CapabilityExplainer (tier 1f: `KitCapabilityExplainer.row(context, capabilityId)` builds `KitRow.unavailable` from the registry), and every wave-2 screen with lists.

## Tests required

These go in `test/kit/kit_row_test.dart` (G9, G14x, G37):

1. A tap calls `onTap`, and Enter and Space call it with desktop capabilities. A disabled row ignores all three.
2. A disabled row with `disabledReason`:
   - the reason text is visible and replaces `supporting`;
   - the semantics hint equals the reason;
   - no `Opacity` widget is in the row's subtree;
   - the title colour is `text3`.
3. `KitRow.unavailable`:
   - the reason is visible;
   - `enable`'s button is a 48 dp tertiary and calls the flow;
   - a tap on the row also calls it;
   - without `enable`, there is no button and the row is not activatable;
   - `capability` is readable by tests (a `Semantics` identifier or a public field).
4. Menu:
   - long-press opens `showKitMenu` with the items;
   - right-click (desktop capabilities) and Shift+F10 open the same items;
   - each item is a `CustomSemanticsAction` on the row;
   - destructive items render last after a divider.
5. `onLongPress` together with a non-empty `menu` asserts. `onLongPress` alone still fires (KIT-43).
6. Swipe: the twin item appears in the menu and as a semantic action, and the swipe runs the KitSwipeAction flow (the full contract is in KitSwipeAction.md's tests).
7. `server: 'laptop'` renders "laptop · {supporting}". In Arabic with a Latin server name, the name is isolated: the painted text contains FSI/PDI around it.
8. `selected: true` exposes `selected` semantics and paints `surface3`.
9. `KitRowGroup` with a destructive row passed first renders it last, behind a full-width divider. The inset separators still start at the text.
10. Hover (desktop capabilities) paints the next surface step (`surface2` in dark, `surface3` in light), and focus paints the ring. Both settle after one `pump()` under reduced motion (G8).
11. The target is at least 48 dp at text 1.0 and 2.0. At 2.0 and 320 dp, with a long title, a server label and a trailing value, there is no overflow, LTR and RTL (G6).
12. KIT-43: every existing constructor shape in the 34 calling files compiles, with the same `titleKey` and `supportingKey` behaviour. `kit-row-current-mark` still resolves (KitRowParts).

## Galleries required

These go in `test/goldens/kit/kit_row_golden_test.dart`, with rows inside a `KitRowGroup` with a label, on `ground`, rendered at DPR 3.0 (TEST-9, TEST-20):

- **Each state at 412×915, dark and light:** `default` (one-line and two-line rows, a `KitRowValue`, a chevron), `disabled`, `unavailable` (with and without `enable`), `server`, `selected`, `hover`, `focused`, `destructive` (last, after the divider) and `menu_open`, which is 18 PNGs.
- **`default` at 360×800, 915×412, 800×1280, 1280×800 and 1600×1000, dark and light:** 10 PNGs.
- **`default` at text 2.0 and Arabic RTL, at 412×915 and 1280×800, dark and light:** 8 PNGs.
- **KitRow total:** 36 PNGs. With KitSwipeAction's 22, the unit total is **58**, under the 60 cap.

## Non-goals

- No `ListTile` look-alike parameters (`dense`, `visualDensity`, `contentPadding`, `tileColor`, `shape`): the kit owns the look (§2.5).
- No choice rows (`KitChoiceRow`) and no switch or fold rows (KitRowParts).
- No capability registry here: that is kit-CapabilityExplainer, built on `KitRow.unavailable`.
- No per-row ⋮ assert: existing trailing `KitRowMenu`s keep working and move to `menu` in wave 2.
- No screen migration: the 142 `ListTile` sites move in wave 2.

## Open questions

These are notes for the builder and the coordinator; none needs the owner.

1. **`GatedRow` and `GatedRowTile`.** They live in `lib/ui/widgets/product_states.dart`, owned by shared-system-1 (C24 correction). This freeze requires only that `KitRow.unavailable(title:, reason:, enable:)` covers what they draw. If shared-system-1 finds a case it does not cover (a gated row with a trailing value), it reports a contract problem (PROC-20) and does not add a local variant.
2. **Strict asserts (coordinator).** The disabled-without-reason assert uses the `KitAsserts.strict` seam (KitAction.md, Open question 1; `_new-tokens.md`). Until that seam exists, the row ships the reason rendering without the assert.
3. **G4 galleries for `KitRowGroup`, `KitRowValue` and `KitSwipeAction`: settled.** README.md's convention is one gallery file per unit with a group per public widget, and the G4 manifest (kit-gates-manifest) maps each export to its unit's file. `kit_row_golden_test.dart` covers KitRow, KitRowGroup, KitRowValue and KitSwipeAction (58 PNGs).
