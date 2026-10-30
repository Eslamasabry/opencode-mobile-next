# KitIconButton v2: API freeze (wave 0, 2026-09-26)

> **Copy (SEC-13, coordinator 2026-09-27):** `.copy` takes `redact` (default true) and passes it to `KitCopy.copy`, so message and diff callers can copy verbatim (KitCodeBlock keeps the redacting default).

Unit: `kit-KitIconButton-v2` (wave 1, tier 1a, kit-change). Spec: kit-v2.md §1.10, §8.2 (KitIconButton row), §8.3, §4.8. Cut review: C04 and its verdict correction. Rules: KIT-22, KIT-23, KIT-39, KIT-43, LAY-9, LAY-11, STATE-7, STATE-8, A11Y-1, LOOK-5, LOOK-6, LOOK-33, R23, and Appendix A #18, #19, #82.

## Purpose

It is the app's only icon-only control. Its label is required and is both its tooltip and its semantic name. It has a 48 dp target, can take a destructive tint, a working state and a toggle state, and on a PC it shows its keyboard shortcut on hover. `KitIconButton.copy` is the one copy button: it shows a check in place and announces "Copied" once, and it never shows a snackbar.

## Replaces

- **Map (kit-v2.json `assignment`, 22 elements on 21 pages):** `about#about-report-bug-icon`, `attention-overview#attention-overview-monitor-settings`, `capabilities#capabilities-run`, `chat#chat-appbar-stop-reading`, `embedded-composer#embedded-composer-open-editor`, `embedded-composer#embedded-composer-tools-button`, `embedded-message-view#embedded-message-view-actions-button`, `embedded-pending-sends-strip#pending-actions`, `embedded-team-cycle-strip#team-cycle-action-refresh`, `files-changes-sheet#files-changes-sheet-stage`, `integrations#add-mcp`, `legacy-drafts-review-sheet#legacy-copy`, `managed-workspaces#managed-workspaces-sync`, `project-health#project-health-refresh`, `saved-permissions#saved-permissions-refresh`, `team-agent#team-agent-refresh`, `team-home#team-home-board`, `team-run#team-run-refresh`, `terminal-surface#terminal-surface-header`, `termux-processes#stop-orphan`, `timeline-sheet#timeline-sheet-row-fork`, `usage-hub#usage-refresh`.
- **Code (`test/kit_ratchet_baseline.json`, G16, outside the kit):**
  - `IconButton` 157 uses in 77 files, including 5 `IconButton.filled` uses in 4 files;
  - `Tooltip` 26 uses in 10 files. After R23, a tooltip is reached only through `KitIconButton`, `KitTerm` or `KitTappable.tooltip`.
- **Copy path:**
  - `Clipboard.setData(` 47 uses in 37 files (G2);
  - about 40 "Copied" snackbars inside the 127 `showSnackBar(` uses in 68 files (G1).

  The copy button calls the one copy service, `KitCopy.copy` (KIT-23), which the text action `KitAction.copy` and the menu item `KitMenuItem.copy` also use.
- **Retires:** the `label:` parameter of today's `KitIconButton`, which is kept and forwards to `tooltip` (KIT-43). It is called in `lib/ui/kit/kit_secret_field.dart:64`, which kit-KitField owns, and in `lib/ui/screens/mcp_setup_screen.dart:686`.

## File

`lib/ui/kit/kit_icon_button.dart` (existing file, rewritten additively). Tests go in `test/kit/kit_icon_button_test.dart` (new), and the gallery in `test/goldens/kit/kit_icon_button_golden_test.dart` (new).

## Public API

```dart
/// The one icon-only control (kit-v2.md §1.10).
///
/// States: disabled, working, selected, copied (KIT-12).
class KitIconButton extends StatefulWidget {
  const KitIconButton({
    super.key,
    required this.icon,
    String? tooltip,
    /// Retired by kit-KitIconButton-v2: use [tooltip]. Forwards to it.
    String? label,
    required this.onPressed,
    this.size = 24,
    this.destructive = false,
    this.working = false,
    this.selected,
    this.shortcut,
    this.disabledReason,
  }) : assert((tooltip ?? label ?? '').length > 0,
           'KitIconButton needs a non-empty tooltip (KIT-22)'),
       assert(size == 20 || size == 22 || size == 24, 'LOOK-33 sizes only'),
       tooltip = tooltip ?? label,
       copyText = null;

  /// Copies what [text] returns, read at tap time, through `KitCopy.copy`.
  /// The glyph turns into a check for `KitMotion.copiedHold`, and "Copied"
  /// is announced once per tap. It never shows a SnackBar.
  const KitIconButton.copy({
    super.key,
    required String Function() text,
    this.tooltip,      // null: l10n.kitCopy ("Copy"); prefer a noun: "Copy command"
    this.size = 24,
    this.shortcut,
    this.redact = true, // false: the person's own content, copied verbatim (SEC-13)
  }) : copyText = text,
       icon = AppIconography.copy,
       onPressed = null,
       destructive = false,
       working = false,
       selected = null,
       disabledReason = null;

  final IconData icon;

  /// What pressing it does, in the person's words: "Remove header". It is
  /// both the tooltip text and the semantic label. It is null only on
  /// [KitIconButton.copy], where it resolves to l10n.kitCopy.
  final String? tooltip;

  /// Null means disabled (KitButton-style dimming, never partial opacity).
  /// On [KitIconButton.copy] it is null, and the button is still enabled.
  final VoidCallback? onPressed;

  /// The glyph size: 20, 22 or 24 (LOOK-33). It keeps today's default of 24.
  final double size;

  /// Tints the glyph `danger`. Use it only for an act that loses data or
  /// ends running work (LOOK-5); the act itself confirms or offers Undo
  /// (DATA-11).
  final bool destructive;

  /// This tap is in flight (at most a second or two). The glyph becomes the
  /// small spinner and taps are ignored. It is never lasting status
  /// (STATE-7).
  final bool working;

  /// A toggle (follow output, wrap lines). Null means it is not a toggle;
  /// true or false maps to toggled semantics.
  final bool? selected;

  /// The key combination that does the same thing, as the shortcuts help
  /// sheet writes it ("Ctrl+R"). It is shown after the label in the hover
  /// tooltip on a fine pointer. The button does not bind it; the shortcut
  /// layer does.
  final String? shortcut;

  /// Why it is disabled. It goes into the semantic hint and the tooltip.
  /// STATE-8 still requires the host to show it as visible text nearby, or
  /// to hide the button (Appendix A #19).
  final String? disabledReason;

  /// Set only by [KitIconButton.copy].
  final String Function()? copyText;
}
```

- **Compatibility (KIT-43).** Existing calls `KitIconButton(icon:, label:, onPressed:)` and `size:` compile and behave as before. `label:` gets a `/// Retired by kit-KitIconButton-v2: use tooltip` note and a G2 ratchet pattern (a `label:` argument inside a `KitIconButton(` call), and it is deleted by the unit that brings that count to zero. No `@Deprecated`.
- **Internal keys (TEST-5):** `kit-icon-button-copied` (the check glyph during the hold) and `kit-icon-button-working` (the spinner). Callers pass `key:` only when a test needs it (KIT-10).

## States

| State | Look | Behaviour |
|---|---|---|
| default | glyph in `text1`, no fill | tap runs `onPressed` |
| hover / pressed (fine pointer) | a `surface3` circle behind the glyph | — |
| focused (keyboard) | a 2-physical-pixel `accent` focus ring around the circle | Enter or Space activates |
| disabled | glyph in `text3`, never partial opacity (LOOK-14) | ignores taps; semantics `enabled: false`; hint = `disabledReason` |
| working | the kit spinner in `accent` in the glyph's box | ignores taps; hint = l10n.kitWorking ("Working") |
| selected: true | an `accent` glyph on a `surface3` circle (a current-selection mark, LOOK-6) | toggled semantics `toggled: true` |
| selected: false | the default look | `toggled: false` |
| destructive | glyph in `danger` | — |
| copied (`.copy` only) | a check glyph in `text1` for `KitMotion.copiedHold` | a repeat tap copies again and restarts the hold |

There is no loading, empty or error state: the button shows no data (KIT-12).

## Tokens

Only names that exist on `feat/visual-language-v1`, except where flagged:

- **ThemeRoles:** `text1`, `text3`, `surface3`, `accent`, `danger`.
- **KitTokens:** `minTarget` (48), `space2` (the 8 dp gap to neighbours), `smallIconSize` (the default for dense rows is 20 when a caller passes it), `gutter` (the tooltip's side margin, so a long label at 2.0 text wraps inside the screen instead of running edge to edge; R5), `spinnerStroke` (the working spinner's 2 dp arc; R5). The tooltip's words use `KitText` roles below.
- **KitText:** `secondary` for the tooltip label; `mono` for the shortcut (a key combination is a technical token, COPY-24).
- **KitMotion:** `quick`, `enter`, `exit`, `reduced(context)`.
- **Not yet on the VL branch** (arriving in STANDARDS §0.5 step 2, before wave 1):
  - `KitMotion.copiedHold`;
  - `KitTokens.focusRingWidth(context)`;
  - `KitCopy.copy(context, text)`;
  - `KitBidi.ltr(value)` for the shortcut.
- **New token (pre-wave, `_new-tokens.md`):** `KitTokens.popoverRadius` = 14, the tooltip's corner radius, shared with the KitMenu panel and the KitTerm bubble; Appendix A #40 drops the off-table 8. If it is missing, the unit reads `buttonRadius` and records the gap.
- **The circle:** `KitShape.circle` (the pre-wave `KitShape` enum; no radius number, README.md decision D13).

## Adaptive

| Window (§8.1) | Behaviour |
|---|---|
| compact | 48×48 target; the tooltip shows on long-press; no hover |
| medium | the same |
| expanded / large | the same 48 dp target (§8.3: never smaller). With a fine pointer (`KitLayout.finePointer`), hovering shows the tooltip "label" plus a second span with the `shortcut`, if there is one. |

- **Keyboard (§8.3, LAY-10):** focusable in reading order, with the focus ring always visible when focused from the keyboard. Enter or Space activates it.
- **No pointer-only information (LAY-11):** the tooltip repeats the semantic label; the shortcut is also in the shortcuts help sheet.

## Accessibility

- **Semantic label:** `Semantics(container: true, button: true, label: tooltip)`. The glyph is excluded from semantics. It is always its own node: inside a tappable parent (a `KitRow`'s trailing action) it is never merged into the row, whose own tap and label stay on the row's node (2026-09-28, slice-bugfix-nonchat2).
- **Hint:** `disabledReason` when disabled, otherwise `shortcut`. G14x compares the tooltip's label span with the semantic label, and they must be equal.
- **Target:** 48×48 dp, with 8 dp from any neighbouring target's 48 dp area when `destructive` is set (LAY-9, G37).
- **Toggle:** `toggled` semantics when `selected != null`.
- **Copy:** `KitCopy.copy` announces "Copied" once through `SemanticsService.announce` and takes no focus. The button itself does not become a live region.
- **200 % text:** the glyph does not scale; an action glyph is not a leading or state icon, LOOK-33. The tooltip text wraps up to two lines and is never cut. The 48 dp target never shrinks.

## RTL

- The button has no inherent direction.
- A caller that passes a directional glyph (back, forward, undo, send) gets it mirrored through `AppIcons`/`matchTextDirection` (LAY-8). Copy, check, close and refresh do not mirror.
- The tooltip's shortcut span is isolated LTR with `KitBidi.ltr`.

## Motion and haptics

- The glyph and the check (copy), and the glyph and the spinner (working), cross-fade on `KitMotion.quick` with `enter`/`exit`, and so does a change of glyph or of its colour (show and hide, play and pause, selected, enabled; micro-motion audit 2026-10-27). The swap is instant under `KitMotion.reduced(context)`.
- The copied hold is a state timer (`KitMotion.copiedHold`), not an animation, so under reduced motion the check still shows for the hold.
- There is no scale or fade-scale (MOT-2).
- **Haptics:** none. MOT-11 names copy and icon taps as silent.

## Data safety and honest state

- **Copy:**
  - It copies only through `KitCopy.copy`, which applies the redactor (G12, SEC-2) unless the caller passes `redact: false` for the person's own content (SEC-13).
  - It is never given a secret: KitField.secret has no copy (SEC-3).
  - The text is read at tap time, so it copies what is on screen now.
- **Destructive:** the tint marks an act that loses data or ends work. That act still follows DATA-11 (confirm or Undo), and the button never performs it silently.
- **Working:** only this tap's own flight. A lasting job is shown by a state part (STATE-7).
- **Disabled:** it has a visible reason nearby or it is hidden (STATE-8). A disabled icon button with only a tooltip reason fails review.
- **Refresh:** a screen shows at most one refresh control, pull or icon (COPY-16). It is a reviewer check, plus the G37 per-screen count once KitScreen-v2 lands.

## Depends on

- **Units:** none (tier 1a, C25).
- **Shared seams from STANDARDS §0.5 step 2, which are not units:** `KitCopy`, `KitBidi`, `KitMotion.copiedHold` and `KitTokens.focusRingWidth`.
- **Parts that depend on it (C04 correction, ten units):**
  - kit-KitField, kit-KitDetailsFold, kit-KitCodeBlock, kit-KitLogPanel, kit-KitDiffView, kit-KitViewer;
  - kit-KitStateView-v2, kit-KitTopBar, kit-KitQueuedMessage, kit-KitComposer;
  - also KitRowMenu v2, through KitRowParts-v2.

## Tests required

These go in `test/kit/kit_icon_button_test.dart` (G9, G37):

1. The semantic label equals the tooltip. Hovering with desktop capabilities (`debugPlatformCapabilities`) shows a tooltip whose first span equals the semantic label and whose second span is the shortcut (G14x).
2. With an empty tooltip and no label, the constructor asserts (`AssertionError`, G37).
3. The target is 48×48 at text scale 1.0 and 2.0, on compact and large windows (`androidTapTargetGuideline`).
4. `label:` alone still renders, and the semantic label equals `label` (KIT-43 forwarding).
5. With `onPressed: null`, a tap does nothing, semantics say `enabled: false`, the hint equals `disabledReason`, and the glyph colour is `text3`, never with alpha below 255.
6. With `working: true`, the spinner shows (`kit-icon-button-working`), taps do not call `onPressed`, and it settles after one `pump()` under reduced motion.
7. `selected: true` or `false` exposes `toggled` semantics; `selected: null` exposes none.
8. `destructive: true` paints the glyph `danger`, never `dangerFill`.
9. **Copy contract (G9):**
   - `.copy` puts the text into the (mocked) clipboard channel;
   - "Copied" is announced exactly once per tap;
   - no `SnackBar` is in the tree;
   - the check shows, then returns after `KitMotion.copiedHold` under fake async;
   - the text is read at tap time (change the source between build and tap).
10. **Redaction (G12):** `.copy` with a fake provider key produces clipboard text without the key. This is `KitCopy`'s contract, asserted here through the button.
11. **Reduced motion (G8):** under `disableAnimations` and Effects Off, the button settles after one `pump()` in every state.
12. **Keyboard (G14):** Tab reaches it, the focus ring is painted, and Enter and Space activate it.

## Galleries required

These go in `test/goldens/kit/kit_icon_button_golden_test.dart`, at DPR 3.0, with names from TEST-20 (`kit_icon_button_<state>[_ar][_text2][_<W>x<H>]_<dark|light>.png`):

- **Each state at 412×915, dark and light:**
  - `default`, `hover`, `focused`, `disabled`, `working`;
  - `selected_on`, `selected_off`, `destructive`, `copied`.

  That is 9 states × 2 = 18 PNGs. The hover and focused shots render with desktop capabilities.
- **The `default` state at the other LAY-4 gallery sizes, dark and light:** 360×800, 915×412, 800×1280, 1280×800 and 1600×1000, which is 10 PNGs.
- **`default` at text 2.0 and in Arabic RTL, at 412×915 and 1280×800, dark and light:** 8 PNGs. The 1280×800 shots show the hover tooltip with its shortcut.
- **Total:** 36 PNGs, under the 60-PNG cap (TEST-20).
- **The scene:** a row of the button in each state on `ground` and on a `surface1` panel, so hover and selected read on both surfaces.

## Non-goals

- **No screen migration.** The 157 `IconButton` sites move in wave 2.
- **No shortcut binding:** the button only shows the shortcut.
- **No filled or outlined variants** (`IconButton.filled` becomes a primary `KitAction`, or `selected: true`).
- **No text label next to the icon:** that is a `KitAction` in a slot.
- **No badge:** a count or needs-you dot is KitNeedsYou's.
- **No dependency on KitIcon:** it is in the same tier; the glyph stays `Icon` inside the kit until KitIcon lands, then switches in kit-hygiene.

## Open questions

None. The `label` vs `tooltip` conflict (C04 correction, Appendix A #18) is settled by KIT-39 and KIT-43. `tooltip` is the name, as in the spec, and `label` forwards to it without `@Deprecated`.
