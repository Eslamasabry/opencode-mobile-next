# KitStepSlider — API (appearance revamp, 2026-10-20)

One choice among a few ordered steps, as a slider that rests only on the steps: a size, a pace, a strength.

## Replaces

- The four radio-style rows (then a four-segment bar) for Text size on Settings › Appearance. Use `KitSegmented` where the options are words that must all show; use this where they are one scale.

## File

- `lib/ui/kit/kit_step_slider.dart`, exported from `kit.dart`.
- Tests: `test/kit/kit_step_slider_test.dart`. Gallery: `test/goldens/kit/kit_step_slider_golden_test.dart`.

## Public API

```dart
class KitStepSlider<T> extends StatefulWidget {
  const KitStepSlider({
    super.key,
    required this.semanticsLabel, // "Text size"
    required this.steps,          // the steps, least to most (2 or more)
    required this.selected,
    required this.onChanged,      // null disables it
  });
}

/// One step: its value, its visible name and a key for its 48 dp target.
class KitSliderStep<T> {
  const KitSliderStep({required this.value, required this.label, this.key});
}
```

## Look

- A small "A" and a large "A" at the ends say which way is more; between them a rail (`surface3`) filled to the thumb in `accent`, a dot on each step, and a thumb (`accent` with a `surface1` ring; a `text1` ring while focused).
- The chosen step's name sits under the track in the secondary role.
- Each step is a 48 dp target; the whole track is 48 dp high.

## Behaviour

- Tap a step, or drag: the thumb snaps to the nearest step and the choice is made as it passes. Arrow keys (reading-direction aware), Home and End move it; a screen reader's increase and decrease move one step.
- The thumb and the filled part glide over `KitMotion.quick`, at once under reduced motion. The name cross-fades.
- Disabled (`onChanged` null): the rail, dots and thumb in `text3`, no response.

States: disabled.

## Accessibility

One slider node: label, the step's name as its value, the neighbouring steps' names as the increased and decreased values. At 200 % text the letters and name scale and nothing overflows; the track keeps its 48 dp height.

## RTL

The least step is at the start edge (right in Arabic) and arrow keys follow the reading direction.

## Motion and haptics

150 ms glide and cross-fade. Each step reached gives `KitHaptics.select`, as a wheel's next value does (MOT-11, owner rule 2026-10-26: native Android feel); nothing with Vibration off.

## Depends on

`KitText`, `KitTokens`, `KitMotion`.
