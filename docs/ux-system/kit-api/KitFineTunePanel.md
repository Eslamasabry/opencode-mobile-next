# KitFineTunePanel — API (Fine-tune, owner approved 2026-10-21)

Settings › Appearance › Fine-tune: a few knobs over the chosen style — Corners (Round / Soft / Sharp), Depth (Flat / Raised), Density (Comfortable / Compact) and Edges (Hairline / None) — each a `KitSegmented` under its name, with the style's own choice said in words at the end of the name's line, and "Reset to <style> defaults" while any knob differs. The same file holds `KitFineTuneKnob`.

## File

- `lib/ui/kit/kit_fine_tune_panel.dart`, exported from `kit.dart`.
- Tests: `test/kit/kit_fine_tune_panel_test.dart`. Gallery: `test/goldens/kit/kit_fine_tune_panel_golden_test.dart`.
- Caller: `lib/ui/screens/settings/personal_settings_screens.dart` (Theme tab, under Surfaces). Screen tests: `test/appearance_fine_tune_test.dart`; screen goldens: `test/goldens/settings_appearance_fine_tune_golden_test.dart`.

## Public API

```dart
class KitFineTuneKnob<T> {
  const KitFineTuneKnob({
    required this.title,          // "Corners": also the control's semantics label
    required this.segments,       // 2–4 KitSegments
    required this.selected,       // the choice in force
    required this.defaultNote,    // "Default: Round"
    required this.onChanged,      // null when a rule holds the knob
    this.hint,                    // at most one short line, only where needed
    this.disabledReason,          // required when onChanged is null
    this.key,
  });
}

class KitFineTunePanel extends StatelessWidget {
  const KitFineTunePanel({
    super.key,
    required this.knobs,
    required this.resetLabel,     // "Reset to Glass defaults"
    required this.onReset,        // null hides the reset
    this.disabledReason,          // set: one line instead of the knobs (Saver)
  });

  /// The style in force, its own knobs, the knobs in force under
  /// KitStyles.knobsFor, which knobs a rule holds, and whether any choice
  /// differs from the style's own.
  static ({KitSurfaceStyle style, KitKnobs defaults, KitKnobs knobs,
      bool heldByContrast, bool edgesHeldByDepth, bool differs})
      read(KitEffects stored, {bool highContrast = false});
}
```

## Behaviour

- The host stores a choice equal to the style's own as null ("the style's default"), so a style change or a reset leaves nothing behind. Changing the style resets every knob (`KitEffects.withSurfaceStyle`, `KitSurfaceLook.choose`).
- Rules (`KitStyles.knobsFor`): high contrast holds Depth at Flat and Edges at Hairline; a style shaped by its shadows (Soft) keeps its edges while Flat. A held knob shows the value in force and its reason under the control.
- Saver keeps surfaces solid: the panel shows that one line instead of the knobs; the stored choices stay for when Saver is off.
- The reset unfolds and folds with `KitReveal` (250 ms, instant under reduced motion). It is a tertiary action: the page keeps its one main action.

## States

- default, disabled (Saver). At 2× text each `KitSegmented` stacks into radio rows and the default note wraps at the line's end.
