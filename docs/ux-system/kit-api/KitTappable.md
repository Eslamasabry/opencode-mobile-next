# KitTappable: API freeze (wave 0)

Unit: `kit-KitTappable` (kind `kit-part`, tier 1b, `after: kit-KitMenu`; cut review C25 "Tappable=[Menu]", C27/R23). Spec for `revamp.workflow.js`.

## Purpose

KitTappable makes a region act on a tap, the same way everywhere:
- the target is at least 48 dp;
- it has a keyboard focus ring;
- a fine pointer gets a hover fill and a click cursor;
- a press draws the native Material ripple on Android (2026-10-26, kit-v2 top note); elsewhere, and under reduced motion, a flat pressed fill answers it;
- it has an optional tooltip;
- right-click and long-press open the same `KitRowMenu` items through `showKitMenu`, and those items are also semantic custom actions.

It is the only interactive primitive outside the named controls. `KitRow`, `KitSurface`-based cards, `KitBreadcrumb` and the chat parts build on it.

## Replaces

- **G16 baseline:** 84 in 34 files:
  - `InkWell` 47 in 24 files;
  - `Tooltip` 26 in 10. R23 allows a tooltip only through `KitIconButton`, `KitTerm` or `KitTappable.tooltip`;
  - `GestureDetector` 8 in 8. Tap-only uses come here; drags belong to their parts: sheet handle → `KitSheet`, swipe → `KitSwipeAction`;
  - `MouseRegion` 2;
  - `FocusableActionDetector` 1.

  The largest are `chat/message_view.dart` 16, `review_workspace.dart` 9, `chat/composer.dart` 7, `chat_screen.dart` 5, `workspace_screen.dart` 5, `files_screen.dart` 3, `home_screen.dart` 3 and `tool_card.dart` 3.
- **Classes it makes redundant** (their file is not in this write set, so they stay until their last caller's unit deletes them, KIT-43): `ClickCursor` (`lib/ui/desktop/desktop_interaction.dart:149`), whose cursor job is built into KitTappable.
- **Inside the kit** (migrated by their own units, not here): `InkWell` in `kit_row.dart` (kit-KitRow-v2 depends on this part), `kit_panel.dart` (`KitPanel.onTap`, kept) and `terminal_key_bar.dart` (kit-KitTerminalView).
- **kit-v2.json `assignment`:** no element is assigned. The per-row ⋮ buttons that VL removes (KIT-28) are assigned to `KitRowMenu`, which this part opens.

## File

`lib/ui/kit/kit_tappable.dart` (new). The write set is that file, `test/kit/kit_tappable_test.dart` and `test/goldens/kit/kit_tappable_golden_test.dart`.

## Public API

```dart
/// What a tappable is to a screen reader.
enum KitTappableRole { button, link }

class KitTappable extends StatefulWidget {
  const KitTappable({
    Key? key,
    required Widget child,
    required VoidCallback? onTap,     // null: disabled
    String? label,                    // semantic name; null when [child]'s own text names it (merged)
    String? disabledReason,           // required when onTap == null (debug assert, STATE-8)
    List<KitMenuItem> menu = const [],// right-click, long-press, Shift+F10 or the Menu key, and semantic custom actions
    VoidCallback? onLongPress,        // compatibility hook only: KitRow.onLongPress (KIT-43, KitRow.md). When set, long-press calls it instead of the menu; asserts menu.isEmpty. New code uses [menu].
    String? tooltip,                  // hover (fine pointer) and keyboard focus; repeats a label the semantics carry (LAY-11)
    String? shortcut,                 // a hint shown in the tooltip, e.g. "Ctrl+Enter"; the binding itself lives in the screen's Shortcuts
    KitShape shape = KitShape.square, // the hover and pressed fill and the focus ring follow it (pre-wave seam enum)
    KitSurfaceLevel surface = KitSurfaceLevel.surface1, // the step it sits on; hover and pressed step up from it (pre-wave seam enum)
    KitTappableRole role = KitTappableRole.button,
    bool? selected,                   // semantics only (a tab, the current item); the look is the child's
    bool autofocus = false,
    FocusNode? focusNode,
    Key? tappableKey,                 // test handle on the gesture and semantics node (KIT-10)
  });
}
```

**Behaviour, frozen:**
- **Target.** The hit area is at least `KitTokens.minTarget` (48) in both axes. A smaller child is centred in a 48×48 box, and a larger child keeps its size (LAY-9). Hit areas never overlap: the host keeps 8 dp between destructive targets (the KitActionBlock rule).
- **Tap.** `onTap` fires once per tap. Enter and Space fire it once per key press, and key repeat does not fire it again.
- **Menu.** With a non-empty `menu`:
  - Right-click and long-press open `showKitMenu(context, items: menu, position: <the gesture's global point>)`, as KitMenu.md §"Right-click and long-press" says. Shift+F10 and the Menu key open it with `position: null`, anchored to this widget.
  - KitTappable calls `showKitMenu` directly. `KitRowMenu.show` (kit-KitRowParts-v2, a later tier) is a thin alias over the same call, so a row and a tappable behave alike.
  - Each item is also a `CustomSemanticsAction` named by its label (KIT-28).
  - Destructive items are sorted last after a divider (that is KitMenu's rule).
  - With an empty `menu`, long-press shows the tooltip if there is one and otherwise does nothing.
- **Hover and pressed come from surface steps, not overlays.** Depth comes from surface steps (VL §1, §4), and this matches KitRow.md, KitChip.md, KitSegmented.md, KitMenu.md and KitNav.md.
  - Hover is the next surface step above `surface` whose colour differs from it: surface1 → surface2 in dark, surface1 → surface3 in light, where surface1 and surface2 are both white (B5).
  - Pressed is the step after that, at most surface3.
  - From surface3, both go one step down, to surface2 (the chip rule).
  - Hover shows only while `KitLayout.finePointer` is true, with `SystemMouseCursors.click`. Disabled has no hover and a basic cursor.
  - Both fills are flat and fill `shape`. **Since 2026-10-26 (owner: "Yes, max Android native animation and support.")** a touch on Android draws the native ripple (`InkSparkle`, `text1` at 12 %) over the part through `KitInkLayer`, clipped to `shape`, and the flat pressed fill is not painted; the flat pressed fill stays on other platforms and under `KitMotion.reduced`. The menu-open hold keeps its flat fill everywhere.
- **Focus ring.**
  - It is shown only when focus came from the keyboard (`FocusHighlightMode.traditional`).
  - It is drawn inside the bounds, so a parent's clip cannot cut it: exactly `focusRingWidth(context)` (2 physical px, LOOK-21) in `accent` (LOOK-6), following `shape`.
  - Disabled tappables are skipped by Tab but stay in the semantics tree.
- **Tooltip.** It shows on hover after the platform delay, and on keyboard focus. Its text is `tooltip`, plus `  shortcut` when given. It never carries information the semantics lack (§8.3).

## States

- **Enabled, hovered, pressed, focused.**
- **Disabled:** `onTap == null`. There is no fill, no cursor and no Tab stop. Semantics say `enabled: false`, with `disabledReason` as the hint. The reason must also be visible in the host (a row's supporting line, `KitAction.disabledReason`), because KitTappable does not draw it.
- **Selected:** semantics only.
- **Menu open:** the pressed fill holds while the menu is open.
- **Pressed timing (slice-tap-feedback, 2026-09-28):** the pressed fill comes from `KitPressTracker`, the shared press layer (also behind `KitIconButton`, `KitButton`, `KitChip`, `KitSegmented`, `KitJumpPill`, `KitReceipt`'s tap target and the terminal keys). With no scrollable that could take the drag (or under a mouse) it shows on the first frame after pointer-down; inside a scrollable it waits `kPressTimeout` like the platform, and a quick tap shows it on release. A tap keeps it visible for at least `KitMotion.pressHold` (100 ms). Moving past the touch slop, a pointer cancel or losing the tap to another gesture (a control inside, a long-press) clears it at once. Keyboard and screen-reader activation paint no press.
- Loading, empty, error and working belong to the host: a working action is `KitButton` or `KitIconButton.working`, never a tappable with a spinner.

## Tokens

- **ThemeRoles:** `accent` (the focus ring); `ground` and `surface1`–`surface3` (hover and pressed steps, through `KitTokens.fillOf`).
- **KitTokens:** `minTarget` (48); `shapeOf(shape)` and `fillOf(level)` (pre-wave seam); `focusRingWidth(context)` (pre-wave §0.5 step 2, 2 physical px).
- **KitMotion:** `quick` (the hover fill cross-fade and the fill clearing); `pressHold` (the least time a tap's press stays visible).
- **New tokens:** none beyond the shared pre-wave seam enums. There are no translucent overlay colours.
- **Tooltip look:** the theme's `tooltipTheme`, which is `surface3` and `text1`. Its radius literal 8 (`app_theme.dart:512`) is Appendix A #40's to fix, not this unit's.

## Adaptive

- **compact:** 48 dp targets, touch, long-press → menu. No hover and no cursor.
- **medium:** the same. A tablet with a mouse gets the fine-pointer behaviour whenever `KitLayout.finePointer` is true.
- **expanded and large:** the hover fill, click cursor, right-click menu at the pointer, tooltips and the keyboard focus ring. Targets stay at 48 dp (§8.3: density never shrinks them).
- **Keyboard (§8.3, G14):**
  - Tab follows reading order;
  - Enter and Space activate;
  - Shift+F10 or the Menu key opens the menu;
  - Esc closes the menu (KitMenu).

## Accessibility

- **Semantics:**
  - `button: true` (or `link: true`), `enabled`, `selected`;
  - the label is `label`, or the child's merged text;
  - `onTap`;
  - the `onLongPress` semantic action is labelled "Show actions" (`kitTappableShowActions`) when `menu` is not empty;
  - each menu item is a custom action.
- **Every gesture has a twin (A11Y-5):** long-press and right-click have the custom actions and the item's own page (B1 interim, KIT-28).
- **Target and contrast:** 48×48 minimum (A11Y-6 `androidTapTargetGuideline`, `labeledTapTargetGuideline`). The focus ring is `accent`, which meets 3:1 on ground and surface1 (LOOK-8).
- **200 % text:** KitTappable does not constrain its child's height; it only sets a minimum.
- **Announcements:** none. Tapping is not a status change.

## RTL

- A keyboard-opened (anchored) menu aligns to this widget's end edge, as KitMenu's anchoring rule sets. From a pointer or a long-press, it opens at the gesture's point.
- The focus ring and fills are symmetric.
- No left or right literals (G7).

## Motion and haptics

- The hover fill cross-fades in on `KitMotion.quick` with `KitMotion.enter`; the pressed fill appears at once (the next frame), and a fill that clears fades on `KitMotion.enter` too (it starts fading at once). Under `KitMotion.reduced` every change is instant.
- The focus ring appears at once.
- No `AnimationController` runs at rest.
- **Haptics:** a long-press that opens the menu (or runs the `onLongPress` hook) plays `KitHaptics.longPress`, the platform's long-press tick (2026-10-26, MOT-11). A tap is silent.

## Data safety and honest state

- A disabled tappable must say why (the debug assert, STATE-8). It never looks clickable: no hover, no cursor.
- One tap is one callback, and key repeat does not fire twice. This guards acts that send or write against double submission.
- Destructive acts in `menu` follow the DATA-11 treatment of the act. The menu only orders and colours them; confirming or offering Undo is the item's callback's job.
- A tooltip never holds the only copy of a value (§8.3). A truncated value's full text is in semantics (A11Y-8).

## Depends on

- **kit-KitMenu:** `showKitMenu` and `KitMenuItem` v2, exactly as `KitMenu.md` freezes them. KitMenu.md wins on any signature detail.
- The pre-wave seams: `KitShape`, `KitSurfaceLevel`, `shapeOf`, `fillOf` and `focusRingWidth`.
- `KitLayout.finePointer` (existing), `KitMotion` (existing).

## Tests required

`test/kit/kit_tappable_test.dart`:

1. A 20×20 child gets a 48×48 hit area; a tap at its corner (inside 48, outside 20) fires `onTap` once.
2. Enter fires once. A held Enter (key repeat) does not fire again. Space fires once.
3. `onTap: null` without `disabledReason` asserts in debug. With a reason:
   - semantics `enabled: false` and hint = the reason;
   - not focusable by Tab;
   - no hover fill;
   - basic cursor.
4. Right-click with `debugPlatformCapabilities` desktop opens the menu at the click position. Long-press on touch opens the same items at the press point. Shift+F10 opens it anchored (`position: null`). With `onLongPress` set, a long-press calls it and opens no menu. `onLongPress` together with a non-empty `menu` asserts.
5. Menu items appear as `customSemanticsActions`, and invoking one calls its `onSelected`.
6. With an empty `menu`, long-press shows the tooltip and opens no menu.
7. A fine pointer hover on `surface: surface1` paints `fillOf(surface2)` in dark and `fillOf(surface3)` in light, in the shape (pixel probe), and sets the click cursor. Pressed paints surface3. On `surface: surface3`, hover paints surface2. On touch there is no hover paint.
8. Focus by Tab shows a 2-physical-px `accent` ring. Focus by tap shows none.
9. The tooltip shows `tooltip` plus the shortcut on hover and on keyboard focus.
10. No `HapticFeedback` is called on tap or long-press (mock the platform channel and assert no calls).
11. Under reduced motion the fills settle after one `pump()` with no ticker (G8).
12. `selected: true` exposes `isSelected` in semantics.

## Galleries required

`test/goldens/kit/kit_tappable_golden_test.dart`, at DPR 3, Android:

- **States:** `enabled`, `hovered`, `pressed`, `focused` (keyboard ring) and `disabled`, each on a `KitSurface.panel` row in dark and light at 412×915. `menu_open` with a KitMenu showing, one destructive item last.
- **Default state:** at 360×800, 915×412, 800×1280, 1280×800 and 1600×1000. `focused` also at 1280×800 with the desktop platform override.
- **`_text2` and `_ar`:** `enabled` and `focused` at 412×915 and 1280×800. The Arabic shot shows the keyboard-anchored menu aligned to the widget's end edge (the left).
- **G5:** tap-target and label guidelines in both themes.
- **G6 overflow matrix** without images.

## Non-goals

- No drags, swipes, pinches or double-taps. Those are `KitSwipeAction`, `KitSheet` and `KitZoom`.
- No visible look of its own beyond the hover and pressed fills and the focus ring. The child draws the content.
- No per-row ⋮ button (KIT-28).
- No confirmation logic.
- No global shortcuts: the `Shortcuts` binding stays with the screen, and `shortcut` is only the hint text.
- No edits to `desktop_interaction.dart` (not in the write set).
- No call-site migration.

## Open questions

None. The shared enums `KitShape` and `KitSurfaceLevel` are pre-wave seams (`_new-tokens.md`); if they landed in `kit_surface.dart` instead, this unit would gain `after: kit-KitSurface` (still tier 1b). KitJumpPill.md now uses this part's surface-step hover (README.md, decision D11).
