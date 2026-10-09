# KitStepTimeline — API (2026-10-09)

Group: chat. File `lib/ui/kit/chat/kit_step_timeline.dart`. Rules: STATE-16, KIT-41, AUTO-15, LOOK-5, MOT-5, PERF-2, A11Y-8, LAY-8. Built on request of the owner (2026-10-09): "do something similar to the Claude app's grouping sheet, but without bottom sheets". Visual reference: the Claude app's "Summary" sheet, drawn inline.

## Purpose

A turn's work as one quiet line that opens **in place** to a timeline. Closed it is a glyph, plain words and a chevron: no pill, no frame, no fill. Open, the steps hang off a thin rail under it in the order they happened. No bottom sheet.

`KitWorkLine` (the host-facing part: counts, state, steps) draws itself with this part; a screen builds `KitWorkLine`, not `KitStepTimeline`.

## Public API

```dart
class KitStepTimeline extends StatelessWidget {
  const KitStepTimeline({
    required String label,            // the line's words
    required IconData icon,           // the group's glyph, text2
    required bool expanded,
    required VoidCallback onPressed,  // the line was tapped
    required List<Widget> steps,      // KitToolRow, KitMessage.thought, any widget
    String? spoken,                   // screen-reader words; null reads label
    Widget? mark,                     // in the glyph's place: the live or failed mark
    Widget? head,                     // a row above the first step ("Show N earlier steps")
    Animation<double>? fade,          // paints the opened steps in
    Key? lineKey, Key? stepsKey,
  });

  static bool inside(BuildContext context);
  static Widget node(BuildContext context, {required Widget child,
      double lineHeight = 22, double top = 0, IconData? icon, Widget? mark});
  static Widget sentence(BuildContext context, {required Widget child});
  static Widget previewCard(BuildContext context, {required KitStepPreview preview,
      required VoidCallback onOpen, required String label, Key? key});
  static IconData iconFor({int read, searched, listed, edited, ran, fetched, delegated, other});
}

class KitStepPreview {            // data, not a widget
  KitStepPreview(List<KitDiffLine> lines, {bool more});
  KitStepPreview.fromPatch(String patch, {int maxLines = 6});
  KitStepPreview.fromText(String text, {int maxLines = 6});            // a new file
  KitStepPreview.fromTexts({String? before, String? after, int maxLines = 6});
}
```

`KitToolRow` gains `preview: KitStepPreview?`.

## The line

- Glyph (20 dp, grows with text to the kit's icon cap) in `text2`, the words in `secondary`/`text2`, the kit's one disclosure chevron (D10). Hugs its words; the whole line is the 48 dp tap target. Two lines of words, then the end is cut; from 1.3x text the words wrap in full.
- `mark` replaces the glyph: the working mark while the work runs (collapsed only: opened, the running step on the rail carries the one mark), the failed mark when it ended on a failure.
- When open and there are steps, the rail leaves the glyph and runs into the first step.

## The rail

A 2-physical-pixel stroke in `text3` at half strength, down the middle of the glyph column (glyph width + `space2`; the words of the line and of every step start where the column ends). The stroke mirrors under right-to-left. Every step sits in a slot that paints its stretch of the rail; the last slot stops at its node.

A step draws its own node through `KitStepTimeline.node`; outside a timeline `node` returns its child, so rows keep their plain look anywhere else. A step with no node (plain text) still sits beside the rail.

| Node | Used for |
|---|---|
| Small grey dot (6 dp, `text3`) | a sentence: `KitMessage.thought`, prose between steps (`sentence`) |
| Tile (glyph width, `surface3`, glyph in `text2`) | a tool step by its kind: read, search, shell, edit, web, agent… |
| The state's mark in the node's place | running/background (the live mark), waiting, failed, stopped |

One live mark: the running step on the rail (or a thought that is still thinking) carries it; the closed line carries it only while folded.

## Steps on the rail

- `KitToolRow`: title in `rowTitle` (the step's words), the file or detail muted under it (`KitText.mono` for a path), counts and the state's words at the end, a chevron when it opens. Rows are top-aligned in a box at least 48 dp tall, so the first line (and so the node) sits at a fixed place.
- `KitToolRow.agent`: the same, the state's mark or the agent glyph as the node.
- `KitMessage.thought` / `.notice`: a dot (or the notice's glyph as a tile) and the words; opens in place.

## Preview card (file write or edit)

`previewCard`: at most 6 lines of what the step changed, in `mono`: a gutter of `+n` / `−n` / `n` (the diff roles; sign only when the call carries no numbers), the line clipped (no wrap) and a tint from `codeAddedSurface` / `codeRemovedSurface`. On `surface1` in the code shape. When more lines follow, the last lines fade (`ShaderMask`, paint only). The whole card is one tap target: it opens the step, whose `body` is the full view (`KitCodeBlock` / `KitDiffView`, "Open full output" included). The card shows only for a finished step (`done`), and not while the step is open. Code is laid out left to right under Arabic.

## States

None of its own (`States: none`): it lays out the line and the steps it is given. Running, waiting, done, failed and stopped are the host's (`KitWorkLine`) and each step's.

## Accessibility

- The line is one button node with the expanded state and `spoken`; its glyph and chevron are excluded.
- Nodes are decoration: no semantics, no taps.
- A row's semantics are its words (title, file, counts, state, time); the preview card is a button named by the file with the hint "Open its details".
- 48 dp targets everywhere; 2.0 text wraps, nothing overflows at 320 dp.

## Motion

None of its own. The host may pass `fade` (the opened steps' paint-only fade, MOT-5). The live mark is `KitStatusMark`'s (a still dot under reduced motion). No shimmer: the turn's live line keeps the chat's one moving light.

## RTL

The glyph column, the rail and the nodes sit at the start edge (the right under Arabic); the preview card's code is left to right; paths stay isolated left to right.

## Long work

The timeline builds the steps it is given, in a column. The host keeps long work cheap: `KitWorkLine` builds at most `stepCap` steps at a time and reveals earlier ones in chunks (PERF-2).
