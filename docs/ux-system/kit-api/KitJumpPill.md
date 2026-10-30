# KitJumpPill — frozen API (wave 0, 2026-09-26)

Group: screen. Unit `kit-KitJumpPill` (wave 1, tier 1b; after kit-KitUndo, C25). Spec: kit-v2.md §1.19, §8.2; STANDARDS Appendix A #24 (the pill is **solid**, not glass), KIT-31, LOOK-20, LOOK-27, MOT-2.

## Purpose

The floating "3 new · Jump to latest" pill on transcripts, team output and logs (and its "Earlier messages" twin at the top), with kit timings instead of the literal 200 ms `easeOutBack` pills it replaces. It is a button with words; an arrow alone is never enough.

## Replaces

- Map (kit-v2.json `assignment`, 5 elements on 4 pages): `chat#chat-earlier-messages-pill`, `chat#chat-jump-to-latest`, `embedded-message-view#embedded-message-view-jump-to-latest`, `team-agent-output#team-agent-output-jump`, `team-run-timeline-tab#team-run-timeline-jump`.
- Code: `_JumpToLatestButton` (`lib/ui/screens/chat/message_view.dart:5`, used at `chat_screen.dart:7607`), `_JumpPill` (`lib/ui/screens/team/run_screen.dart:1911`), the jump control in `lib/ui/screens/team/agent_output_screen.dart`. KitLogPanel's "new lines" pill (K2 §1.11, KIT-31) uses it too.
- Ratchet: no own G16 pattern; the replaced private classes carry `Material`/`InkWell`/`Container`/`Text`/`Icon` counts in those files, which drop in the chat chain (chat-1, chat-8) and team units.

## File

`lib/ui/kit/kit_jump_pill.dart` (new).

## Public API

```dart
/// Which edge of its scroll area a pill belongs to.
enum KitJumpEdge {
  /// Newer content below: "3 new · Jump to latest". Arrow down.
  bottom,

  /// Older content above: "Earlier messages". Arrow up.
  top,
}

class KitJumpPill extends StatelessWidget {
  const KitJumpPill({
    super.key,
    required this.label,              // the words; see latestLabel
    required this.onPressed,
    required this.visible,            // keep mounted; toggle this so it can animate out
    this.icon = AppIconography.down,  // K2 names arrowDown, which does not exist
    this.edge = KitJumpEdge.bottom,
    this.pillKey,
  });

  /// The top pill: [edge] top, [icon] AppIconography.chevronUp.
  const KitJumpPill.older({
    super.key,
    required this.label,              // e.g. "Earlier messages"
    required this.onPressed,
    required this.visible,
    this.pillKey,
  });

  final String label;
  final VoidCallback onPressed;
  final bool visible;
  final IconData icon;
  final KitJumpEdge edge;
  final Key? pillKey;

  /// Kit copy: "Jump to latest", or "{count} new · Jump to latest"
  /// (ICU plural; ARB keys kitJumpLatest, kitJumpNewLatest).
  static String latestLabel(BuildContext context, {int newCount = 0});
}

/// Lays [pill] over [child] (the scroll area it belongs to): centred on the
/// pill's edge, space3 inside it. A bottom pill also clears the published
/// bottom inset (the composer, the pinned primary, the dock) unless
/// [clearBottomInset] is false (a pill inside a panel mid-page, e.g.
/// KitLogPanel).
class KitJumpPillLayer extends StatelessWidget {
  const KitJumpPillLayer({
    super.key,
    required this.child,
    required this.pill,
    this.clearBottomInset = true,
  });

  final Widget child;
  final KitJumpPill pill;
  final bool clearBottomInset;
}
```

Rules: a host shows at most one pill per edge per scroll area. The host decides `visible` (not at the newest end; for logs, the person scrolled up: KIT-31). Tapping it scrolls and is the host's `onPressed`; the pill does not scroll anything itself.

## States

Declared (KIT-12): default (visible), hidden (`visible: false`: kept mounted, invisible, not hittable, out of semantics and focus). No loading, empty, error or disabled (a pill that cannot act is hidden). No working (a jump is instant).

## Tokens

- ThemeRoles: `surface3` (fill), `hairline` (1 physical px border), `text1` (label and icon), `accent` (focus ring only, LOOK-6).
- KitText: `button` (label, one line; two lines from 2.0 text).
- KitTokens: `minTarget` (48: the pill's minimum height and hit area), `smallIconSize` (20), `space2` (icon gap), `space3` (distance from its edge and above the inset), `space4` (side padding), `gutter` (the pill never comes closer than this to the area's sides), `hairlineWidth(context)` and `focusRingWidth(context)` (§0.5 step 2 seam). Shape `KitShape.pill` (VL §4 "chips and pills: 999"; the pre-wave `KitShape` enum, no radius number; README.md decision D13). All in `_new-tokens.md`.
- No shadow, no glass, no blur (LOOK-20, LOOK-22, Appendix A #24).

## Adaptive

Same pill on every window class (K2 §8.2 has no row; it follows its scroll area):

- compact / medium / expanded / large: centred horizontally in its own scroll area (the conversation pane on PC, not the window); max width = area width − 2 × `gutter`.
- A bottom pill clears `KitBottomInset.of(context).bottom` + `space3` when `clearBottomInset` is true.
- Fine pointer: hover is one surface step (the pill's `surface3` steps down to `surface2`, KitTappable's rule; no overlay colour or opacity, README.md decision D11) and a focus ring; the target stays 48 dp.
- Keyboard: focusable only while visible; Enter and Space activate.

## Accessibility

- A button whose label is its words ("3 new, Jump to latest"); the icon is excluded from semantics.
- Not a live region: new lines are not announced one by one (K2 §1.19); the host's own live region, if any, announces only state changes.
- 48 dp tall minimum; at 200 % text the label wraps to two lines and the pill grows; never truncated to a few letters.
- Hidden pills are excluded from semantics and focus.

## RTL

Centred, so no mirroring of position; icon then label in reading order (the icon at the start). The arrow glyphs are vertical and do not mirror.

## Motion and haptics

- In: fades in and rises `space2` over `KitMotion.standard` on `KitMotion.enter`. Out: fades and sinks over `KitMotion.quick` on `KitMotion.exit` (out on `KitMotion.quick`: leaving is quicker than arriving) (a top pill drops/rises toward its own edge). Never scales (MOT-2; replaces `easeOutBack`).
- Under `KitMotion.reduced(context)`: shows and hides at once; one `pump()` settles (G8x).
- Haptics: none.

## Data safety and honest state

- The count in `latestLabel` is what the host says is new; the pill never invents one. With no count, it says "Jump to latest".
- It never covers the composer, the pinned primary or the dock (it sits above the published inset).

## Depends on

- kit-KitUndo (for KitBottomInset; C25).
- Existing: `KitMotion`, `KitText`/`KitTokens` (VL branch), `AppIconography` (moves under kit-KitIcon; the `IconData` parameter type does not change).

## Tests required

`test/kit/kit_jump_pill_test.dart`:

1. Visible: label and icon shown; tap calls `onPressed` once.
2. `visible: false`: not hittable, not in semantics, not focusable; after the out animation nothing paints.
3. Toggling visible animates in over `KitMotion.standard` and out over `KitMotion.quick`, with no `ScaleTransition`/`Transform.scale` in the tree; under reduced motion one `pump()` settles.
4. `KitJumpPillLayer` with `KitBottomInset(bottom: 140)`: the pill's bottom edge is ≥ 152 dp above the area's bottom; with `clearBottomInset: false` it is `space3` from the area's bottom.
5. `KitJumpPill.older` sits at the top edge with the up glyph.
6. `latestLabel(context, newCount: 3)` = "3 new · Jump to latest"; 0 → "Jump to latest"; Arabic plural forms resolve.
7. Semantics: button, label = words, not a live region; ≥ 48×48.
8. Desktop capabilities: focus ring visible when focused; Enter activates.
9. 200 % text at 320 dp: no overflow, label on ≤ 2 lines.

## Galleries required

`test/goldens/kit/kit_jump_pill_golden_test.dart`, DPR 3, Android (TEST-9, TEST-20):

- States at 412×915, dark and light: `kit_jump_pill_default` (bottom, "3 new · Jump to latest", over a transcript-like list above a composer-height inset), `kit_jump_pill_older` (top), `kit_jump_pill_focused` (desktop capabilities). Hidden is covered by test 2, not an image.
- Default at 360×800, 915×412, 800×1280, 1280×800 (centred in a 700 dp conversation pane) and 1600×1000, dark and light.
- Default at text 2.0 and Arabic RTL at 412×915 and 1280×800.
- G5/G6 for the rest.

## Non-goals

- Glass (Appendix A #24 overrides K2 §1.19's "bounded KitGlass surface").
- Scrolling logic, unread counting, or deciding when to show.
- A pill for anything other than returning to one end of a scroll area.

## Open questions

None.
