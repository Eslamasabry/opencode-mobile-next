# KitTopBar — frozen API (wave 0, 2026-09-26)

Group: screen. Unit `kit-KitTopBar` (wave 1, tier 1c; C25: after kit-KitIcon, kit-KitIconButton-v2, kit-KitNeedsYou, kit-KitMenu, kit-KitStatusMark-v2). Spec: kit-v2.md §1.18, §8.2; design-standard §1; VL §2 (headline), §5, §6 (glass top controls and PC toolbar); cut review C26 (`KitTopBar.shell`), K25; STANDARDS KIT-36, LAY-8, LOOK-17, LOOK-27, A11Y-8, STATE-9, Appendix A #22, #77.

## Purpose

The screen's header: where you are in the person's words, one line of state, a switcher when the title switches something, and the page's few actions — one icon plus overflow on a phone, labelled actions on a PC. `KitTopBar.shell` is the shell's glass top controls (server pill with its status word, search). `KitShellControls` is that control group, reused as the PC sidebar header.

## Replaces

- Map (kit-v2.json `assignment`, 20 elements on 19 pages): `about#about-title`, `activity#activity-header`, `builtin-server-setup#title-and-duplicate-heading`, `chat#chat-appbar-title`, `demo#demo-header`, `diff-view#diff-view-header`, `files#files-back-bar`, `home-shell#home-shell-server-switcher`, `project-hub#project-hub-files-back`, `prompt-editor#prompt-editor`, `prompt-editor#prompt-editor-done`, `servers#brand-header`, `servers-welcome#brand-header`, `settings#settings-header`, `shell-output#shell-output-title`, `team-board#team-board-project`, `team-conversation#team-conversation-title`, `team-run#team-run-appbar`, `team-run-overview-tab#overview-objective`, `workspace#workspace-project-header`. Merged gap names: `KitBrandHeader`, `KitProjectHeader`, `KitShellHeader`, `KitTopBar`.
- G16 (`test/kit_ratchet_baseline.json`): `AppBar` 95 in 77 files (with `Scaffold` 98 in 80 files, retired through `KitScreen(topBar:)`), `PreferredSize` 5 (`local_terminal_screen.dart` 1, `session_destination_sheet.dart` 2, `terminal_screen.dart` 2), `AppBarTheme` 1 (`app_theme.dart`), `Badge` on the chat app bar (`chat_screen.dart` 2, via `KitNeedsYou.badge`).
- `_WorkspaceAppBarTitle` in `home_screen.dart` (server name + tab title + status) and its G15 `compact: width < 600` literal.

## File

`lib/ui/kit/kit_top_bar.dart` (new): `KitTopBar`, `KitTopBarExit`, `KitShellControls`, `KitShellControlsLayout`.

## Public API

```dart
/// How the bar leaves the page. Back sits at the start, Close at the end
/// (LAY-8, Appendix A #77).
enum KitTopBarExit {
  /// Close at the end for a fullscreenDialog route; Back at the start when
  /// the route can pop; nothing at a root or inside a KitScreen pane.
  auto,
  back,
  close,
  none,
}

class KitTopBar extends StatelessWidget {
  const KitTopBar({
    super.key,
    required this.title,             // the place, in the person's words (COPY-10)
    this.subtitle,                   // one line of state: "Working · 2 min", the server
    this.subtitleTone = AppStatusTone.neutral,
    this.needsYou = 0,               // > 0: subtitle starts with KitNeedsYou.span; badge on a switcher
    this.onTitleTap,                 // the title is a switcher (project, conversation): chevron + button semantics
    this.titleTapLabel,              // what the switcher does: "Switch project" (required when onTitleTap != null)
    this.actions = const [],         // List<KitAction>, most important first; each has an icon
    this.menu = const [],            // List<KitMenuItem>, the overflow's own entries
    this.brand = false,              // the product mark in place of the title (root pages only)
    this.exit = KitTopBarExit.auto,
    this.onExit,                     // overrides the pop (the shell's nested Files back)
    this.titleKey,
    this.exitKey,
    this.menuKey,
  }) : controls = null;

  /// The shell's top controls (compact and medium): KitShellControls in its
  /// bar layout, plus the page actions. No title: the dock names the tab and
  /// the tab's body shows its own largeTitle. The other fields take their
  /// neutral values (title '', exit none, brand false, needsYou 0).
  const KitTopBar.shell({
    super.key,
    required KitShellControls this.controls,
    this.actions = const [],
    this.menu = const [],
    this.menuKey,
  }) : title = '',
       subtitle = null,
       subtitleTone = AppStatusTone.neutral,
       needsYou = 0,
       onTitleTap = null,
       titleTapLabel = null,
       brand = false,
       exit = KitTopBarExit.none,
       onExit = null,
       titleKey = null,
       exitKey = null;

  /// Non-null only for [KitTopBar.shell].
  final KitShellControls? controls;
  final String title;
  final String? subtitle;
  final AppStatusTone subtitleTone;
  final int needsYou;
  final VoidCallback? onTitleTap;
  final String? titleTapLabel;
  final List<KitAction> actions;
  final List<KitMenuItem> menu;
  final bool brand;
  final KitTopBarExit exit;
  final VoidCallback? onExit;
  final Key? titleKey;
  final Key? exitKey;
  final Key? menuKey;
  // slice-P10.1-2: when the overflow is one thing's menu, its name is the
  // button's tooltip and the menu's semantic name ("Conversation menu");
  // default "More".
  final String? menuLabel;
}

enum KitShellControlsLayout {
  /// One row: server pill at the start, search button at the end.
  bar,

  /// The PC sidebar header: server pill, then the project switcher, then a
  /// full-width search button that looks like a field ("Search").
  sidebar,
}

/// The glass top controls (VL §6): the server pill and search. Each control
/// is its own KitGlass piece (dim: true, it holds words); they share one
/// BackdropGroup provided by the shell.
class KitShellControls extends StatelessWidget {
  const KitShellControls({
    super.key,
    required this.server,             // the server's display name
    required this.serverStatus,       // the status word, always visible: "Connected", "Reconnecting"
    this.serverTone = AppStatusTone.neutral,
    required this.onServer,           // opens the server switcher (a KitSheet)
    this.needsYou = 0,                // requests on other servers: KitNeedsYou.badge on the pill
    this.project,                     // sidebar layout only: the project switcher's title
    this.onProject,
    this.onSearch,                    // null hides search
    this.layout = KitShellControlsLayout.bar,
    this.serverKey,
    this.projectKey,
    this.searchKey,
  });

  final String server;
  final String serverStatus;
  final AppStatusTone serverTone;
  final VoidCallback onServer;
  final int needsYou;
  final String? project;
  final VoidCallback? onProject;
  final VoidCallback? onSearch;
  final KitShellControlsLayout layout;
  final Key? serverKey;
  final Key? projectKey;
  final Key? searchKey;
}
```

Structural rules (debug asserts, G37):

- Every `KitAction` in `actions` has an `icon`; none is `destructive` (a destructive act goes in `menu`, last, and confirms: KIT-28, §4.2).
- `onTitleTap != null` requires a non-empty `titleTapLabel`.
- `brand: true` only with `exit: none` or `auto` at a root route.
- The overflow button appears only when something is in it.
- A screen inside a tab never builds a second bar: `KitScreen(topBar:)` inside the shell's KitScreen asserts (KIT-36, K2 §1.18 rule).
- `scope` (a `KitTopBarScope`: chip label, semantics label, at least one menu item) puts one small `KitChip.action` under the title and subtitle. It names what the page belongs to (a conversation's project) and opens a compact `KitMenu` of what can be done there. The chip is its own semantics node, outside the title's header label. Chats-first shell, 2026-10-03.
- `KitTopBar` is not a `PreferredSizeWidget` (a deliberate change from K2 §1.18): it has no fixed height and is hosted only by `KitScreen(topBar:)`, which lets it grow at 200 % text. G16 removes `Scaffold`/`AppBar` outside the kit, so nothing else can host it.

## States

Declared (KIT-12): default (title), with subtitle state (neutral, working, needs-you, not answering), switcher (title with chevron and badge), brand, disabled actions (an action with `onPressed == null` is not shown in the bar; it appears in the overflow as a disabled `KitMenuItem` whose `disabledReason` line is the action's `disabledReason`: STATE-8). Shell: connected, reconnecting (word + working mark), not answering, needs-you badge. No loading (the screen's one loading bar sits under the bar, KitScreen), no empty, no error of its own (errors are the screen's KitStateView or status line).

## Tokens

- ThemeRoles: `ground` (the bar on compact/medium, solid), `hairline` (bottom border, 1 physical px, shown when content is scrolled under the bar's edge), `text1` (title, icons), `text2` (subtitle), `attention` (only through `KitNeedsYou`), `accent` (focus ring, working mark via KitStatusMark).
- KitText: `headline` (title; LOOK-17: never the old 20/26 AppBar title), `secondary` (subtitle), `button` (labelled actions via KitButton tertiary), `rowTitle` (the shell pill's server name), `caption` (the pill's status word).
- KitTokens: `minTarget` (48; minimum bar row = `minTarget` + 2 × `space1`, no fixed height), `gutter`, `space1`–`space4`, `smallIconSize` (20, chevron), `navRadius` (22, the glass pill and search button corners — the navigation layer's radius), `hairlineWidth(context)` and `focusRingWidth(context)` (§0.5 step 2 seam, `_new-tokens.md`).
- Glass: `KitGlass(dim: true)` with the VL branch's rim and single shadow (roles `glassRimLight`, `glassRimDark`, `glassShadow`, already on the VL branch — read inside KitGlass, not here).

## Adaptive

(K2 §8.2 row KitTopBar; KIT-36; Appendix A #22.)

- compact: exit, title (+ subtitle under it), then at most **two icons**: the first action as a `KitIconButton` and the overflow (the rest of `actions`, then `menu`). With one action and an empty menu: one icon.
- medium: at most **three icons**: up to two actions (three when the menu is empty and there are exactly three) plus the overflow.
- expanded / large: actions become labelled tertiary buttons (`KitButton.fromAction`, icon + words, plus the action's `shortcut` hint on a fine pointer, e.g. "Stop task", "Ctrl N") in reading order, as many as fit beside a title that keeps at least half the bar; the rest move into the overflow. On PC (large, or expanded with a fine pointer: STANDARDS §6) the bar is the pane's toolbar and is a `KitGlass` surface (VL §6 "on PC, the … toolbar"); content does not scroll beneath it.
- `KitTopBar.shell`: compact and medium only. From expanded the shell passes `KitShellControls(layout: sidebar)` to `KitNav`'s sidebar header instead, and each pane has its own `KitTopBar`.
- Short windows (< 480 dp tall): compact rules.
- Fine pointer: every icon button shows its tooltip (label + shortcut when one exists; KitIconButton v2); hover state on the switcher and pill.
- Keyboard: Tab order exit → title switcher → actions → overflow; Enter/Space activate; the overflow opens `showKitMenu` anchored to its button, arrow keys move in it, Esc closes it.

## Accessibility

- The title is a header and names the route (`Semantics(header: true, namesRoute: true)`).
- The subtitle's state is part of the title's label: "Fix login, working, 2 minutes"; a needs-you count reads "1 needs you" (KitNeedsYou words).
- A switcher title is a button: "shopfront, Switch project"; the chevron is excluded.
- The server pill reads "Laptop, Connected, Switch server" (+ ", 1 needs you"); the dot is never the only carrier: the status word is visible (STATE-9).
- All targets ≥ 48 dp with ≥ 8 dp between their 48 dp areas.
- 200 % text: the bar grows; the title wraps (up to two lines) instead of being cut; the subtitle drops under the title; labelled actions fall back to icons, then to the overflow, before the title shrinks. The title is never clamped below 2.0 (A11Y-8).

## RTL

Back at the start (right in RTL, glyph mirrored via `matchTextDirection`), Close and the actions at the end, the overflow last. The pill's chevron does not mirror (it points down). A server or project name from the person is bidi-isolated (`KitBidi.auto`, §0.5 seam).

## Motion and haptics

- Subtitle changes cross-fade over `KitMotion.quick`; the hairline appears over `KitMotion.quick` when content scrolls under the bar. No layout animation, no scale (MOT-2, MOT-5).
- The shell pill's status word and mark change with KitStatusMark's own motion.
- Fluid glass (kit-fluid-glass, 2026-09-28, KitGlass.md): in the bar layout the server pill and search are one `KitGlass.pair`. While the page under KitNav is scrolled past one `minTarget` (`KitGlass.scrolledOf`), search slides next to the pill, becomes the smaller drop and the two melt together (`KitMotion.glassJoin`); back at the top, or on another destination, they pull apart. Only the drawn glass and search's painted place move; search keeps its label, size and reading order. Pill and search (and the sidebar's search field-button) give under a finger (`KitGlass(respond: true)`).
- Reduced motion: all changes at once (the pair joins and parts instantly); one `pump()` settles.
- Haptics: none.

## Data safety and honest state

- The subtitle and the pill's status word come from the one status source per entity (the screen passes words produced by that source's mapping, e.g. `KitStatusLine.of(status)`'s wording or the connection status mapper), never a hand-built string that could contradict the status line (K2 §1.18 honest state; G11 contradiction pairs).
- "Working" and "Not answering" never show together; a needs-you subtitle comes only from the attention source (AUTO-11).
- A disabled action is never a silent dead button (STATE-8).

## Depends on

kit-KitIcon, kit-KitIconButton-v2 (`tooltip`, `shortcut`), kit-KitNeedsYou (`span`, `badge`), kit-KitMenu (`KitMenuItem`, `showKitMenu`, `KitMenuPanel`), kit-KitStatusMark-v2 (C25), plus **kit-KitAction-v2** (`disabledReason`, `shortcut`; edge added, README.md; tier 1a, so KitTopBar stays in tier 1c). Existing: `KitButton`, `KitGlass`, `KitMotion`, VL `KitText`/`KitTokens`/`ThemeRoles`.

## Tests required

`test/kit/kit_top_bar_test.dart` (G9, G14x, G37):

1. Title is a header and names the route; subtitle is part of the title's semantics label.
2. Exit `auto`: root → none; pushed route → Back at the start; `fullscreenDialog` → Close at the end; inside a `KitScreen.twoPane` detail pane → none. `onExit` overrides the pop.
3. Compact (412): 3 actions + 1 menu item → exactly two icon buttons (first action + overflow); the overflow lists actions 2–3 then the menu item.
4. Medium (700): same input → three icons (two actions + overflow).
5. Expanded (1280): actions render as labelled buttons; when they don't fit, the last move into the overflow; the title keeps ≥ half the width.
6. Asserts: an action without an icon, a destructive action, `onTitleTap` without `titleTapLabel` → `AssertionError`.
7. Switcher: tap calls `onTitleTap`; semantics "…, Switch project"; `needsYou: 2` shows a badge whose count is in the label.
8. `KitTopBar.shell`: pill shows name + status word; tapping calls `onServer`; search button labelled; both are one `KitGlass.pair` with `dim: true` (kit-fluid-glass); under Effects › Glass off they are solid.
9. `KitShellControls(layout: sidebar)`: pill, project switcher, search field-button stacked; `onProject` null hides the project row.
10. 200 % text at 320 dp: no overflow; title wraps, not cut; actions collapse to icons then overflow.
11. RTL: Back is at the right and mirrored; actions at the left.
12. Desktop capabilities: Tab reaches exit, switcher, each action, overflow in that order; tooltips equal labels; Esc closes the overflow menu.
13. Reduced motion: a subtitle change settles in one `pump()`.

## Galleries required

`test/goldens/kit/kit_top_bar_golden_test.dart`, DPR 3, Android (TEST-9, TEST-20):

- States at 412×915, dark and light: `kit_top_bar_default` (Back + title + one action + overflow), `kit_top_bar_subtitle_working`, `kit_top_bar_needs_you`, `kit_top_bar_switcher`, `kit_top_bar_brand`, `kit_top_bar_close`, `kit_top_bar_shell_connected`, `kit_top_bar_shell_reconnecting`, `kit_top_bar_shell_needs_you`.
- Default at 360×800, 915×412, 800×1280 (three icons), 1280×800 (labelled actions, glass toolbar) and 1600×1000, dark and light; `kit_top_bar_shell_sidebar` (KitShellControls sidebar layout) at 1280×800 dark and light.
- Default at text 2.0 and Arabic RTL at 412×915 and 1280×800.
- G5/G6 for the rest.

## Non-goals

- Content scrolling beneath the top controls or the PC toolbar (the canvas shows them at rest; a top inset is not in this freeze).
- Large collapsing titles: the screen's `largeTitle` lives in the body (a `KitText` role), not in the bar.
- Tabs inside the bar (that is `KitTabSwitcher.tabs`), search fields inside the bar (that is `KitScreen(search:)`), a loading bar (KitScreen).
- The server switcher sheet itself (screen-shell-1 via `showKitSheet`).

## Open questions

None. The canvas shows the connected server pill as dot + name with no word; STATE-9 (owner-approved: no state by colour alone) requires a visible word, so the word always shows ("Laptop · Connected"). A dot alone when connected would carry the state by colour, which STATE-9 forbids, so this is not left open.
