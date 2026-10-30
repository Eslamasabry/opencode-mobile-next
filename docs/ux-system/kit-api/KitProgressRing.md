# KitProgressRing — API (2026-10-20)

Unit: phone setup progress (vision pick B, `docs/qa/vision-phone-setup-2026-10-20/`). Rules: KIT-1, KIT-12, LOOK-5, MOT-1, MOT-2 (no scale transforms), A11Y-3, honest state (no figure the job did not measure).

## Purpose

One long job as one big calm ring with a real figure inside, the one sentence of what it does now under it, and a slim trail of the job's steps. Used by the phone setup progress screen; any long local job with steps and measured amounts may use it.

## File

`lib/ui/kit/kit_progress_ring.dart`: `KitProgressRing`, `KitTrailMark`. Tests: `test/kit/kit_progress_ring_test.dart`. Gallery: `test/goldens/kit/kit_progress_ring_golden_test.dart`.

## API

```dart
const KitProgressRing.measured({
  required double value,          // 0..1 of the current step, measured (bytes, percent)
  required String headline,       // "Downloading OpenCode"
  required String semanticsLabel, // "Setup progress"
  String? caption,                // inside, under the figure: "41.2 of 64 MB"
  List<KitTrailMark> trail = const [],
  bool halted = false,
  Key? ringKey, Key? headlineKey,
});
const KitProgressRing.steps({
  required int done, required int of, // "2 of 6" when the step reports no amount
  required String headline, required String semanticsLabel,
  List<KitTrailMark> trail = const [], bool halted = false,
  Key? ringKey, Key? headlineKey,
});
const KitProgressRing.finished({
  required String headline, required String semanticsLabel,
  List<KitTrailMark> trail = const [], Key? ringKey, Key? headlineKey,
});
enum KitTrailMark { done, current, waiting }
```

There is no way to pass a free percent: a figure is a measured share, steps done of all, or finished.

## Look

- Ring: diameter 64 % of the width, at most 264 dp, at least 150; stroke 5.2 % of it (9–14 dp), round caps, from 12 o'clock clockwise (mirrored right to left), over a `text3` track (22 % dark, 18 % light). The arc is `accent` with a soft accent glow under it (a blurred stroke, 32 % dark, 18 % light); halted it is `text3` with no glow. Never red, never amber.
- Figure: the measured whole percent (floored) at 25 % of the diameter, weight 600, tabular, with the percent sign at half size; or "2 of 6" at 16 %; or a check in `accent`. The caption is the secondary role in `text2`. The inside scales down to fit at any text size.
- Headline: the title role, centred, up to three lines, 32 dp under the ring.
- Trail: 16 dp under the headline. 8 dp dots, the current one a 24 dp bar, joined by 14 dp hairlines; done and current in `accent` (`text2` when halted), waiting an outlined `text3` dot.

## Motion

The arc eases forward over `KitMotion.standard` (250 ms, `enter`). A smaller value is a new arc (a new stage measuring from zero) and lands at once, so the ring never runs backwards. A new headline cross-fades over 250 ms; the trail's marks change width and colour over 250 ms. Reduced motion: every change is instant.

## Semantics

The ring is one node: its label, then the figure and the caption as its value ("Setup progress, 64%, 41.2 of 64 MB"). The headline is a live region, so a new step is announced once and a byte tick never is. The trail is excluded (the headline and the figure say it).

## States

None of its own (KIT-12): measured, steps, finished and halted are the data it is given.
