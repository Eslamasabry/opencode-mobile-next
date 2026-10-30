# KitStatusAnswer (2026-10-20)

Unit: vision slice "This phone: one answer" (`docs/qa/vision-this-phone-2026-10-20/README.md`).
File: `lib/ui/kit/kit_status_answer.dart`. Test: `test/kit/kit_status_answer_test.dart`.
Gallery: `test/goldens/kit/kit_status_answer_golden_test.dart`.

## Purpose

A status page's one big plain answer to "Is it OK?", centred in the first view:
a mark, the answer in display type, one small line, at most one quiet action.
The page's main action, when something is wrong, is its pinned bottom button,
not part of this widget.

## Public API

```dart
enum KitAnswerTone { ok, paused, working, needsYou, off }

enum KitAnswerScale { large, calm, compact } // 40/46, 28/34, 24/30

class KitStatusAnswer extends StatefulWidget {
  const KitStatusAnswer({
    Key? key,
    KitAnswerTone? tone,             // null draws no mark (KitStateView.calm)
    required String answer,
    KitAnswerScale scale = KitAnswerScale.large,
    String? line,
    DateTime? countdownTo,
    String Function(Duration left)? countdownLine, // required with countdownTo
    KitAction? action,                             // drawn as a tertiary button
    Key? answerKey,
    Key? lineKey,
  });
}
```

## Look

- Mark in a 32 dp slot: ok = 14 dp `success` dot with a soft halo of its own
  colour; needsYou = the same in `attention` (the only amber); paused = two
  4 x 14 dp `text2` bars; working = `KitStatusMark` working; off = 14 dp ring
  in `text3` at the focus-ring width.
- Answer: the large-title face at 40/46, tracking -1.1, centred. Grows with the
  person's text up to 1.5x (1.2x under 300 dp of width); steps its size down
  so the longest word never breaks; lines balanced (the narrowest width that
  keeps the same line count, never narrower than the longest word: a broken
  word keeps the count too, so the count alone let it break at 320 dp and
  2x text). `calm` and `compact` set the same face at 28/34 (-0.6) and
  24/30 (-0.4).
- Line: body, `text2`, centred, balanced the same way. With `countdownTo` the
  line is asked for again once a second until the time passes.
- Horizontal margin: two gutters, one under 360 dp.

## Motion

A new tone or answer cross-fades with a 4 % rise over `KitMotion.standard`
(enter/exit curves); instant under reduced motion. No loop.

## Semantics

One live region; the answer is its own header node; the mark is excluded.

## States

None of its own: the tones and the countdown are variants of one answer.
