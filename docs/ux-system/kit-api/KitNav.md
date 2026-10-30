# KitNav — frozen API (wave 0, 2026-09-26)

> **Amended 2026-10-20 (owner: no bottom bar on phones, concepts 1 + 9).**
> Compact windows draw no navigation at all (`KitNavLayout.none`, was
> `dock`): Conversations is the phone's only home, the shell's top bar
> carries Search and a Settings gear, and a project's Files open as a page
> from its conversations. `KitNavBar` and the dock metrics are removed; the
> rail (medium) and the sidebar (expanded, large) are unchanged. The dock
> sections below are history. Record: `docs/qa/no-bottom-nav-2026-10-20/`.

Group: screen. Unit `kit-KitNav` (wave 1, tier 1c; replaces the planned kit-KitNavRail, cut review C08). Write set: `lib/ui/kit/kit_nav.dart`, `test/kit/kit_nav_test.dart`, and `lib/ui/widgets/glass_surface.dart` (becomes a forwarding wrapper). Spec: kit-v2.md §8.1 (rail from medium, extended from expanded), §8.2 (badge on the rail destination), §9.2 (`KitNavRail`); VL §4 (floating 60 dp bar, radius 22, surface2 at 82 %), §5 (PC panes), §6 (glass tab bar with lens; PC sidebar header); Desktop canvas render (`docs/design/visual-language-2026-09-26/Desktop.png`); STANDARDS LAY-1, LAY-5, LAY-15, LOOK-27, LOOK-33, LOOK-34, A11Y-8, KIT-43.

## Purpose

One list of destinations drawn three ways by window class: the floating glass dock with a lens tab (compact), a glass rail (medium), and the PC sidebar (expanded and large: the 296 dp column that holds the top controls, the destinations, the selected destination's list, and its primary). One needs-you badge through `KitNeedsYou`. It retires the shell's raw `NavigationBar`/`NavigationRail` and the 760/1040 width literals.

## Replaces

- Map: `home-shell#home-shell-rail` (kit-v2.json `assignment` → `module:special surface`, moved to the kit by §9.2 `KitNavRail`); the dock element (`home-shell` dock, `KitGlass`); `home-shell#home-shell-badge` is drawn by `KitNeedsYou.badge` inside KitNav.
- G16 `lib/ui/screens/home_screen.dart`: `NavigationBar` 1, `NavigationBarTheme` 1, `NavigationDestination` 4, `NavigationRail` 1 (+ its `NavigationRailDestination`s), `Badge` 1, and `VerticalDivider`; `_ShellNavigation`/`_ShellNavigationState` (label measuring) and `_ActivityIcon` (`home_screen.dart:508-630`).
- G15 `home_screen.dart` width literals at :250, :378, :383 (760, 760, 1040), cut review C37; screen-shell-1 removes them by adopting KitNav.
- `lib/ui/widgets/glass_surface.dart` (`GlassSurface`, 27 lines, in no unit; imported by `home_screen.dart` and `tool/capture`): stays as a forwarding wrapper marked `/// Retired by kit-KitNav: use KitNavBar.` (KIT-43: no `@Deprecated`), and `GlassSurface(` becomes a G2 ratchet pattern so call sites only shrink.
- `AppTheme`'s `navigationBarTheme`/`navigationRailTheme` become unused by the app (the theme owner removes them after screen-shell-1).

## File

`lib/ui/kit/kit_nav.dart` (new): `KitNavDestination`, `KitNav`, `KitNavBar`, `KitNavRail`, `KitNavLayout`.

## Public API

```dart
/// One destination. The same list builds the dock, the rail and the sidebar.
@immutable
class KitNavDestination {
  const KitNavDestination({
    required this.label,             // "Work", "Inbox", "Project", "Settings" (always visible)
    required this.icon,
    this.selectedIcon,               // the fill glyph, dock only (LOOK-33)
    this.needsYou = 0,               // KitNeedsYou.badge count (Inbox)
    this.pane,                       // expanded+: this destination's list, shown in the sidebar
    this.key,                        // the destination's hit area
  });

  final String label;
  final IconData icon;
  final IconData? selectedIcon;
  final int needsYou;
  final WidgetBuilder? pane;
  final Key? key;
}

enum KitNavLayout {
  /// compact: the floating glass dock at the bottom.
  dock,

  /// medium: a floating glass rail at the start, icons with labels.
  rail,

  /// expanded and large: the 296 dp sidebar at the start.
  sidebar,
}

/// The shell's navigation frame. [child] is the content (the shell's
/// KitScreen with KitTopBar.shell on compact/medium; the selected
/// destination's detail pane on expanded+).
class KitNav extends StatelessWidget {
  const KitNav({
    super.key,
    required this.destinations,      // 2–5
    required this.selected,          // index into destinations
    required this.onSelected,        // ValueChanged<int>
    required this.child,
    this.sidebarHeader,              // KitShellControls(layout: sidebar); sidebar only
    this.sidebarPrimary,             // KitAction: the sidebar's pinned primary ("New conversation")
    this.navKey,
  });

  final List<KitNavDestination> destinations;
  final int selected;
  final ValueChanged<int> onSelected;
  final Widget child;
  final Widget? sidebarHeader;
  final KitAction? sidebarPrimary;
  final Key? navKey;

  /// The layout KitNav uses in this window (from KitLayout.windowOf, with the
  /// short-window rule: < 480 dp tall keeps dock/rail).
  static KitNavLayout layoutOf(BuildContext context);

  /// True below a KitNav whose sidebar is showing the selected destination's
  /// [KitNavDestination.pane]. KitScreen.twoPane reads it and leaves its own
  /// list out (the list is already in the sidebar).
  static bool hostsPane(BuildContext context);
  static bool hosts(BuildContext context);   // below any KitNav: the page's ground carries the theme's ambient fields
}

/// The floating dock (compact). Public for galleries and tests; the app
/// uses KitNav.
class KitNavBar extends StatelessWidget {
  const KitNavBar({
    super.key,
    required this.destinations,
    required this.selected,
    required this.onSelected,
  });
}

/// The rail (medium) or, with [extended], the sidebar column (expanded+).
/// Public for galleries and tests; the app uses KitNav.
class KitNavRail extends StatelessWidget {
  const KitNavRail({
    super.key,
    required this.destinations,
    required this.selected,
    required this.onSelected,
    this.extended = false,
    this.header,
    this.primary,
  });
}
```

Behaviour (frozen):

- **Dock (compact):** floats `space2` above the system gesture inset with `gutter` side margins; 60 dp tall (`navHeight`) or taller when labels grow; `KitGlass(dim: true)` with radius `navRadius` (22); the selected tab is a glass lens (a clear pill of the glass behind the glyph) with the fill glyph and `text1` label; others `text2`. Hidden while the keyboard is open (as today). Publishes `KitBottomInset` bottom = system inset + `space2` + dock height, and the same as `MediaQuery.padding.bottom` for its child (the `extendBody` contract of today).
- **Rail (medium):** a floating glass column (`KitGlass(dim: true)`, radius `navRadius`) inset `space2` from the start, top and bottom edges; icon with its label under it for every destination; the selected one on the lens. Publishes `KitBottomInset` start = `KitLayout.railWidth` + `space2`.
- **Sidebar (expanded/large):** a solid column `KitLayout.sidebarWidth(context)` wide (296 at 1.0 text, wider with larger text) on `ground`, a 1 physical px `hairline` at its end edge; from top: `sidebarHeader` (its controls are glass, VL §6), the destinations as 48 dp rows (icon + `rowTitle` label + trailing needs-you badge), the selected destination's `pane` (scrolls on its own, below a `sectionGap`), and `sidebarPrimary` pinned at the bottom as a full-width primary `KitButton`. Publishes `KitBottomInset` start = that width.
- The layout switches at the `KitLayout` classes only: dock < 600, rail 600–839, sidebar ≥ 840 (the shell's 760/1040 go). A window < 480 dp tall keeps the dock (compact) or the rail (medium-or-wider).
- `KitNav.hostsPane` is true only in the sidebar layout and only when the selected destination has a `pane`.
- `KitNav.hosts` is true anywhere below a KitNav; `KitScreen`'s page frame then paints the theme's `ambient` fields on the ground (slice-glass-crisp, KitGlass.md).
- Switching destination never rebuilds the other destinations' content (the shell keeps `KitTabSwitcher`).
- Order of destinations is the caller's; LAY-15 (Work · Inbox · Project · Settings, Project only with project tools) is the shell's rule, checked by `home_navigation_test.dart`.

## States

Declared (KIT-12): default (one selected), needs-you (a badge on a destination), dock hidden (keyboard open), glass solid (Effects › Glass off, high contrast, accessible navigation, remove animations: `KitGlass` falls back, LOOK-29). The sidebar pane's own loading/empty/error states belong to the pane's content (a KitStateView inline). No disabled destination: an unavailable destination is absent (LAY-15), never dimmed.

## Tokens

- ThemeRoles: `ground` (sidebar), `surface3` (the sidebar's selected row, per the Desktop canvas), `hairline` (sidebar end edge), `text1` (selected label and glyph), `text2` (unselected), `accent` (focus ring only), badge colours through `KitNeedsYou` (`attentionFill`/`onAttentionFill`).
- KitText: `label` (dock and rail labels), `rowTitle` (sidebar destination labels).
- KitTokens: `navHeight` (60), `navRadius` (22), `gutter`, `space1`–`space4`, `sectionGap` (22), `minTarget` (48), `buttonRadius` (sidebar selected row corners), glyphs at KitIcon size l (24) in dock and rail and m (22) in the sidebar (kit-KitIcon's s/m/l = 20/22/24, cut review C02; no size literal in the part), `hairlineWidth(context)` and `focusRingWidth(context)` (§0.5 step 2 seam), `navLabelMaxScale` (pre-wave token: the named clamp for dock and rail labels, A11Y-8; today computed per width up to 2.0 in `_ShellNavigationState`; the rule stays "scale up to 2.0 but never beyond what fits one label per destination", and 2.0 is the named constant).
- KitLayout: `paneListWidth` = 296 (**changed from 360**, LAY-5, cut review C02), `railWidth` = 80 (§0.5 step 2 named widths). All pre-wave names are listed in `_new-tokens.md`.
- Glass: `KitGlass` (VL branch: rim, dim, one shadow); `BackdropGroup` wraps the shell so the dock, rail and top controls share one backdrop read (LOOK-28).

## Adaptive

- compact: dock (above).
- medium: rail (above).
- expanded: sidebar with the selected destination's pane; the content (`child`) fills the rest.
- large: the same; the content may be a KitScreen.threePane (conversation up to 700 + changes 340).
- Short window: dock/rail as above.
- Fine pointer: hover state on each destination (a `surface3` step in the sidebar; in the dock and rail the hovered label and glyph turn `text1`, with no lens preview); tooltips are not needed (labels are always visible, A11Y-1).
- Keyboard: the navigation is one `FocusTraversalGroup`; Tab enters at the selected destination; arrow keys move between destinations (Left/Right in the dock, Up/Down in rail and sidebar); Enter/Space selects; the focus ring is always visible. Global shortcuts (switching tabs by key) stay in the app's shortcuts layer (`lib/ui/desktop/shortcuts.dart`), not here.

## Accessibility

- Each destination is a button with `selected` state and label "Inbox, 1 needs you" (count in words from KitNeedsYou; the badge is excluded as a separate node).
- 48 dp minimum per destination in every layout; dock destinations fill the full bar height.
- Labels always visible (no icon-only navigation); at 200 % text the dock grows and labels scale to the named clamp; the sidebar widens with the text (`KitLayout.sidebarWidth`: 296 × the text scale, at most `sidebarMaxWidth` 400 dp and a third of the window, kit-polish 2026-09-27) so its header and pinned primary keep their words whole, and its rows grow and wrap.
- Traversal: navigation before content in the rail/sidebar layouts; after content in the dock layout (reading order: it is at the bottom), A11Y-4.
- A badge count change is announced once by the destination's live region (KitNeedsYou, A11Y-3).

## RTL

Rail and sidebar at the start (right in Arabic); `KitClearance.start` is directional; destination order follows reading order; glyphs do not mirror (none is directional).

## Motion and haptics

- Fluid glass (kit-fluid-glass, 2026-09-28; the owner's approved "Fluid glass" sample, KitGlass.md): the lens's two edges ride separate springs, so it stretches towards a new tab, leading with its front edge (`KitMotion.lensLead`), the back edge following (`lensTrail`), thins a little while stretched, and settles on the tab. A drag along the dock (horizontal) or the rail (vertical) lifts the lens (it grows `space2` and its hairline turns `glassRimLight`, `lensLift`), it follows the finger (`lensDragLead`/`lensDragTrail`, resisting past the first and last destinations), and letting go opens the destination under the finger. Taps, keys and screen readers work as before; the drag is excluded from semantics. The lens is laid out alone and its edges sit on physical pixels; the destinations never rebuild or relayout while it moves. Labels cross-fade colour over `KitMotion.quick`. No scale, no blur on content (MOT-2, LOOK-22).
- The dock and rail glass give under a finger (`KitGlass(respond: true)`): the drawn glass swells a few dp and brightens, then springs back.
- The dock hides and shows with the keyboard at once (no animation: the keyboard itself moves).
- KitNav watches the page's vertical scrolling (`KitGlass.trackScroll`, reset on a new destination) so the top controls can join while scrolled (KitTopBar.md).
- Reduced motion: the lens jumps, follows a drag directly without lifting, and one `pump()` settles; no ticker runs.
- Haptics: none (MOT-11: nothing on navigation).

## Data safety and honest state

- The needs-you count comes only from the one attention source (`conn.unifiedAttentionCount` today, AUTO-11); KitNav does not count.
- A destination is never shown for a capability the server lacks (LAY-15); the explanation of a missing tab is the shell's `KitRow.unavailable` elsewhere (STATE-12), not a dimmed tab.
- Selecting a destination preserves each destination's state and drafts (KitTabSwitcher keeps them mounted).

## Depends on

kit-KitNeedsYou (`badge`), kit-KitStatusMark-v2, kit-KitIcon (C25), plus two tier-1a additions (edges added, README.md) that keep KitNav in tier 1c: **kit-KitUndo** (`KitBottomInset`) and **kit-KitAction-v2** (`sidebarPrimary`'s `shortcut` hint, "Ctrl N" in the Desktop canvas). Existing: `KitGlass`, `KitAction`/`KitButton`, `KitMotion`, `KitLayout`, VL `KitText`/`KitTokens`/`ThemeRoles`. `sidebarHeader` is typed `Widget` so KitNav does not depend on kit-KitTopBar; the shell passes `KitShellControls`.

## Tests required

`test/kit/kit_nav_test.dart` (G9, G14x, G6):

1. 412×915: a `KitNavBar` shows; no rail. 700×1000: a rail; no dock. 1280×800: a 296 dp sidebar. 915×412 (short): dock.
2. Tap a destination → `onSelected(index)` once; the selected one has `selected` semantics.
3. `needsYou: 3` on Inbox → semantics "Inbox, 3 need you"; changing the count announces once.
4. Keyboard open (`viewInsets.bottom` 300) → dock not built; `KitBottomInset` bottom excludes the dock.
5. `KitBottomInset.of` in `child`: compact = system inset + 8 + dock height; rail layout start = railWidth + 8; sidebar start = 296. `MediaQuery.padding.bottom` in `child` equals the published bottom (compact).
6. Sidebar: `sidebarHeader`, destinations, the selected destination's `pane`, and `sidebarPrimary` all present; `KitNav.hostsPane` true for a destination with a pane, false without one, false on compact/medium.
7. Glass: dock and rail are `KitGlass(dim: true)` with radius `navRadius`; under Effects › Glass off, `KitGlass.lookOf` is solid; the sidebar body is not glass (LOOK-27).
8. 200 % text at 320 and 412 dp: no overflow; dock taller than 60; every label visible.
9. RTL: rail/sidebar at the right; `KitClearance.start` measured from the right.
10. Desktop capabilities: Tab lands on the selected destination; arrow keys move; Enter selects; focus ring visible.
11. Reduced motion: switching tabs settles in one `pump()`.
12. `GlassSurface` still builds a `KitGlass` with radius 22 (forwarding wrapper).

## Galleries required

`test/goldens/kit/kit_nav_golden_test.dart`, DPR 3, Android (TEST-9, TEST-20):

- States at 412×915, dark and light: `kit_nav_dock_default` (Work selected), `kit_nav_dock_needs_you` (Inbox badge 1), `kit_nav_dock_solid` (glass off). Each over a scrolled list and the theme's ambient ground so the glass is visible.
- Default at 360×800 and 915×412 (dock), 800×1280 (rail), 1280×800 (sidebar with header, a Work pane, a primary), 1600×1000 (sidebar beside a three-pane content), dark and light; `kit_nav_rail_needs_you` at 800×1280.
- Default at text 2.0 and Arabic RTL at 412×915 (dock) and 1280×800 (sidebar).
- G5/G6 for the rest.

## Non-goals

- The shell's composition (which destinations, the server switcher, the double-back exit): screen-shell-1.
- Global keyboard shortcuts for destinations (the app's shortcuts layer).
- A hamburger/drawer layout, a bottom bar on tablets, or more than five destinations.
- Content scrolling beneath the rail or sidebar.

## Open questions

None. The sidebar's selected destination follows the owner-approved Desktop canvas (solid, `surface3`), and the medium rail follows LOOK-27 (glass with the lens). The canvas is the more specific owner-approved source for the expanded sidebar, so this is a coordinator amendment to LOOK-27's wording ("from medium up" becomes "on the medium rail"), not an owner question (README.md).
