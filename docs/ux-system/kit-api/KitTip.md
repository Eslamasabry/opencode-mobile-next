# KitTip: API (teaching system, 2026-10-21)

Owner rule "Teach what is hidden" (AGENTS.md); the full spec is
[docs/ux-system/teaching.md](../teaching.md). The rules (max count, one at a
time, cooldown, never over a question card or the keyboard, stored as
`oc.tips`) live in `TipController` (`lib/state/tips.dart`); the registry of
every tip and tutorial is `lib/ui/teaching/teaching.dart`. These parts only
draw.

## File

`lib/ui/kit/kit_tip.dart`, exported from `kit.dart`. Tests:
`test/kit/kit_tip_test.dart`; gallery: `test/goldens/kit/kit_tip_golden_test.dart`.

## Public API

```dart
/// One sentence anchored to a control, shown while [visible]. Takes no
/// touch; any touch anywhere fades it, and it fades by itself after [hold].
class KitTip extends StatefulWidget {
  const KitTip({Key? key, required Widget child, required String message,
      required bool visible, required VoidCallback onDismissed,
      Duration hold = KitTip.defaultHold, Key? tipKey});
  static const Duration defaultHold; // 4 s
  static const Duration fade;        // 200 ms
  static const double maxWidth;      // 280
}

/// The tip's look: the sentence on the panel surface, with optional
/// tertiary actions (the tour offer, the tour card).
class KitTipBubble extends StatelessWidget {
  const KitTipBubble({Key? key, required String message, List<KitAction>? actions});
}

/// One tour step: the control's keys (first found, last route wins) and a
/// sentence; [around] highlights an enclosing widget type instead.
class KitTourStep {
  const KitTourStep({required List<Key> anchors, required String message, Type? around});
}

/// The 2–4 step mini tutorial, and its once-only offer.
abstract final class KitTour {
  static Future<bool> show(BuildContext context, {required List<KitTourStep> steps});
  static Future<bool> offer(BuildContext context, {required String message,
      required String startLabel, required String notNowLabel, Key? offerKey});
}
```

## Behaviour

- Placement: above the anchor when it sits below the top quarter of the
  screen, else below; aligned with the anchor's nearer edge so it never
  leaves the screen; at most 280 wide, wrapping at 2× text.
- Motion: a 200 ms fade with a 4-point rise; under reduced motion it appears and leaves at once, with no movement (MOT-7, as every kit part).
- Screen reader: the sentence is announced politely when it appears.
- The tour: a dim scrim with the control cut out and an accent ring, a card
  with "Step n of m", the sentence, Next (Done on the last) and Skip; Back or
  a tap outside the card leaves. The card is a live region.
- Never amber, never red.
