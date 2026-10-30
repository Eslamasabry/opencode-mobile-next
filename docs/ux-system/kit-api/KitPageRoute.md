# KitPageRoute — frozen API (wave 0, 2026-09-26)

Group: screen. Unit `kit-KitPageRoute` (wave 1, tier 1a leaf). Its write set also takes `lib/ui/kit/motion/kit_page_transitions.dart` (cut review C23). Spec: kit-v2.md §9.1 (routes row), §9.2 (KitPageRoute row); design-standard §10 (KitPageTransitions); STANDARDS KIT-1, KIT-7, KIT-44, MOT-2, MOT-3, MOT-7.

## Purpose

The one way to push a page. It always plays the kit's one page transition (`KitPageTransitionsBuilder`), whatever the ambient theme, so screens never pick a transition and `MaterialPageRoute`/`PageRouteBuilder` can leave the G16 allowlist.

## Replaces

- No map element (kit-v2.json has no `assignment` entry; it is a §9.2 foundation).
- Code: `MaterialPageRoute` (and `MaterialPageRoute<T>`) — 122 uses in 49 files under `lib/` (count 2026-09-26, `grep -rn "MaterialPageRoute\|PageRouteBuilder" lib`), including `lib/main.dart` (5+, coord-main) and `lib/voice/notices.dart`; four `fullscreenDialog: true` pages (`chat/permission_sheet.dart:290`, `widgets/diff_view.dart:47`, `chat_screen.dart:3689`, `widgets/form_renderer.dart:43`). `PageRouteBuilder` has no use under `lib/ui` today.
- Allowlist: the §9.1 "Routes" row (`MaterialPageRoute` and `PageRouteBuilder` "until KitPageRoute exists"). KIT-7: the commit that adds KitPageRoute removes both from the G16 allowlist, baselining every current use per file in the same commit with `ratchet-tighten: G16 MaterialPageRoute` and `ratchet-tighten: G16 PageRouteBuilder` in its body (KIT-44). This unit is the one KIT-7 names, so it may stage those baseline entries (the single exception to R05 for this pattern).

## File

`lib/ui/kit/kit_page_route.dart` (new). `lib/ui/kit/motion/kit_page_transitions.dart` stays where it is, API unchanged; this unit may edit it (write set) only as noted under Tokens.

## Public API

```dart
/// A pushed page with the kit's one transition (design standard §10).
class KitPageRoute<T> extends PageRoute<T> {
  KitPageRoute({
    required this.builder,
    super.settings,
    this.maintainState = true,
    super.fullscreenDialog = false,   // a page with Close at the end instead of Back
    super.allowSnapshotting = true,
  });

  final WidgetBuilder builder;

  @override
  final bool maintainState;

  // Fixed by the kit, not parameters:
  // transitionDuration / reverseTransitionDuration = KitMotion.standard
  // (a page grown from a marked row closes in at most KitMotion.rowReturn)
  // opaque = true, barrierColor = null, barrierDismissible = false
  // buildTransitions → const KitPageTransitionsBuilder().buildTransitions(...)
  // delegatedTransition → KitPageTransitionsBuilder's covered-page transition
}

/// Pushes [builder] as a KitPageRoute on the nearest Navigator.
Future<T?> pushKitPage<T>(
  BuildContext context,
  WidgetBuilder builder, {
  RouteSettings? settings,
  bool fullscreenDialog = false,
  bool rootNavigator = false,
});

/// Replaces the current route with [builder] (the one-way hand-offs:
/// setup finished → Work).
Future<T?> replaceWithKitPage<T, TO>(
  BuildContext context,
  WidgetBuilder builder, {
  RouteSettings? settings,
  TO? result,
});
```

Kept unchanged (in `kit_page_transitions.dart`): `KitPageTransitionsBuilder` (`travel` 30, `fadeOutShare` .3, `transitionDuration` = `KitMotion.standard`), still installed in `AppTheme`'s `pageTransitionsTheme` for every platform but iOS (MOT-3), so a stock `MaterialPageRoute` behaves the same until it is migrated.

`pushNamed` routes: `onGenerateRoute`/`routes` in `main.dart` return `KitPageRoute`s (coord-main); screens keep calling `Navigator.pushNamed`.

## States

A page pushed right after `KitPageOrigin.mark(rowContext)` (a conversation opened from its row; owner request 2026-10-27) grows out of the row's box into the page (container transform: bounds on `emphasized` over the first 80 %, contents fading in between 20 % and 60 %; the page underneath dims to 32 % black over the first 20 %, the box's ground fills in over the row in the same time, and the page rides the box's top-left corner, clipped to the box, translated and never scaled, MOT-2) and Back shrinks it into the row again over `KitMotion.rowReturn` (300 ms, shorter than the opening) on the mirrored path (`KitMotion.rowReturnPath`): it starts moving at once, its contents leave in the first 40 %, the row shows again in the last 20 %, and the dim fades throughout; a Back during the opening turns back from where the page is. The page under it stays still. `KitPageOrigin.covers(route)` is true from the mark until that page has shrunk back (it is popping, but still counts), and `KitPageOrigin.changes` notifies when it may have changed: a list holds its rows still meanwhile (the Conversations list does), so the page lands in the row it came from and the list re-sorts after. On Android the system back gesture keeps Flutter's predictive back preview for as long as it runs. Reduced motion: the plain transition.

None of the KIT-12 set: a route draws the transition only. Declared frames for galleries: mid-forward (a page opening) and mid-reverse (going back).

## Tokens

- KitMotion: `standard` (duration), `enter`, `exit`, `emphasized` (curves) — as today.
- ThemeRoles: `ground` — the covered page's backdrop while it moves. The current `Theme.of(context).scaffoldBackgroundColor` in `_KitCoveredPage` changes to `ThemeRoles.of(context).ground` (LOOK-2); this is the only edit to `kit_page_transitions.dart`.
- `KitPageTransitionsBuilder.travel` (30 dp) and `fadeOutShare` stay as named constants on the builder (motion geometry, not layout widths).

## Adaptive

The same transition on every window class; the page's own layout adapts (KitScreen). Nothing in the route reads the window class. On expanded and large, a row that opens a detail inside `KitScreen.twoPane` does not push a route at all (`KitScreen.openDetail`, see KitScreen.md).

- Keyboard: the system back (and Esc on PC, via the framework's `DismissIntent` only for fullscreen dialogs) pops; nothing is added here.

## Accessibility

- The route scopes semantics (framework). The page's name comes from its `KitTopBar` title, which sets `namesRoute: true` (see KitTopBar.md), so TalkBack announces the page when it opens.
- During the transition the leaving page ignores pointers (as today).
- Focus moves into the new page (framework default).

## RTL

The slide follows the reading direction (existing `_direction`): a page arrives from the end side (the left in Arabic).

## Motion and haptics

- Shared-axis fade-through along x over `KitMotion.standard`: slide and fade only, no scale, no blur (MOT-2).
- Reduced motion (`KitMotion.reduced`): a new page shows at once; a closing page only fades; the widget structure is identical either way so toggling the setting never rebuilds the page (a draft survives). One `pump()` settles on push (G8x).
- Haptics: none.

## Data safety and honest state

- `maintainState` stays true by default so a page underneath keeps its drafts and scroll.
- Toggling reduced motion while a page is open keeps its state (existing guarantee, now tested).

## Depends on

Nothing (tier-1 leaf). Uses `KitMotion`, `ThemeRoles` (VL branch).

## Tests required

`test/kit/kit_page_route_test.dart`:

1. Pushed under a bare `ThemeData()` (no `pageTransitionsTheme`), the route still builds `KitPageTransitionsBuilder`'s transition (find the kit's axis widget, not `ZoomPageTransition`).
2. `transitionDuration` and `reverseTransitionDuration` equal `KitMotion.standard`.
3. Mid-transition there is no `ScaleTransition`, `Transform` with a scale, or `ImageFiltered`/`BackdropFilter` (MOT-2).
4. Under `disableAnimations`, the new page is fully opaque and in place after one `pump()`.
5. RTL: at t = 0.5 the arriving page's x offset is negative (from the left).
6. `pushKitPage` returns the value passed to `Navigator.pop`; `settings.name` is kept.
7. `fullscreenDialog: true` → `ModalRoute.of(context)!.fullscreenDialog` is true in the page (KitTopBar then shows Close at the end).
8. A `TextField` draft in the page underneath survives push + pop, and survives toggling reduced motion while the page is open.
9. `replaceWithKitPage` removes the previous route.
10. Guard: `test/kit_ratchet_test.dart` fails on a new `MaterialPageRoute(` in a file with no baseline entry (KIT-7).

## Galleries required

`test/goldens/kit/kit_page_route_golden_test.dart`, DPR 3, Android: the transition is size-independent, so only 412×915:

- `kit_page_route_mid_forward` and `kit_page_route_mid_reverse`, dark and light.
- `kit_page_route_mid_forward_ar` (RTL), dark.
- Reduced motion is covered by test 4, not an image.

## Non-goals

- Android predictive-back animation (the back gesture still pops with the reverse transition).
- Modal routes: sheets, dialogs, confirmations and Undo have their own `showKit…` entry points (KIT-11).
- Per-screen transitions, hero choreography, or a second transition style.
- iOS (ARCH-11: Android is the target; the theme keeps Cupertino for iOS as today).

## Open questions

None.
