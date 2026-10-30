import 'package:flutter/widgets.dart';

import '../app_iconography.dart';
import 'kit_buttons.dart';
import 'kit_effects.dart';
import 'kit_max_width.dart';
import 'kit_segmented.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_reveal.dart';
import 'style/kit_style.dart';

/// One knob of a [KitFineTunePanel]: its name, at most one short line on
/// what it does, its choices as [KitSegment]s, the one in force and the
/// one the style starts with.
@immutable
class KitFineTuneKnob<T> {
  const KitFineTuneKnob({
    required this.title,
    required this.segments,
    required this.selected,
    required this.defaultNote,
    required this.onChanged,
    this.hint,
    this.disabledReason,
    this.key,
  }) : assert(
         onChanged != null || disabledReason != null,
         'KitFineTuneKnob.disabledReason is required when onChanged is null.',
       );

  /// "Corners": the knob's name, also the control's semantics label.
  final String title;

  /// One short line, only where the name alone does not say it.
  final String? hint;

  /// 2 to 4 choices (KitSegmented's rule).
  final List<KitSegment<T>> segments;

  /// The choice in force.
  final T selected;

  /// Which choice is the style's own, in words: "Default: Round".
  final String defaultNote;

  /// Null when a rule holds the knob ([disabledReason] says which).
  final ValueChanged<T>? onChanged;

  /// Why the knob cannot change right now ("High contrast keeps edges on").
  final String? disabledReason;
  final Key? key;

  Widget _control() => KitSegmented<T>(
    semanticsLabel: title,
    selected: selected,
    segments: segments,
    onChanged: onChanged,
    disabledReason: disabledReason,
  );
}

/// Settings › Appearance › Fine-tune: a few knobs over the chosen style
/// (corners, depth, density, edges), each a [KitSegmented] under its name
/// with the style's own choice said in words at the end of the name's line.
/// "Reset to <style> defaults" unfolds under the knobs while any of them
/// differs from the style, and folds away once they match again
/// ([KitReveal], 250 ms, instant under reduced motion).
///
/// While [disabledReason] is set (Saver keeps surfaces solid), the knobs
/// give way to that one line: nothing on the page can change them.
///
/// States: disabled.
class KitFineTunePanel extends StatelessWidget {
  const KitFineTunePanel({
    super.key,
    required this.knobs,
    required this.resetLabel,
    required this.onReset,
    this.disabledReason,
  });

  /// The knobs, top to bottom.
  final List<KitFineTuneKnob<Object?>> knobs;

  /// "Reset to Soft defaults".
  final String resetLabel;

  /// Null while every knob is at the style's default: the action is hidden.
  final VoidCallback? onReset;

  /// Set when no knob can change right now; shown instead of the knobs.
  final String? disabledReason;

  /// What Fine-tune shows for the [stored] choices: the style in force
  /// (high contrast may turn it to Solid), that style's own knobs, the
  /// knobs in force under the rules of [KitStyles.knobsFor], which knobs a
  /// rule holds, and whether any choice differs from the style's own.
  static ({
    KitSurfaceStyle style,
    KitKnobs defaults,
    KitKnobs knobs,
    bool heldByContrast,
    bool edgesHeldByDepth,
    bool differs,
  })
  read(KitEffects stored, {bool highContrast = false}) {
    final style = KitStyles.effectiveStyle(
      stored.surfaceStyle,
      highContrast: highContrast,
    );
    final tokens = kitStyleSpec(style).tokens;
    final knobs = KitStyles.knobsFor(
      style: stored.surfaceStyle,
      fineTune: stored.fineTune,
      highContrast: highContrast,
    );
    return (
      style: style,
      defaults: tokens.defaults,
      knobs: knobs,
      heldByContrast: highContrast,
      edgesHeldByDepth: tokens.dependsOnDepth && knobs.depth == KitDepth.flat,
      differs: stored.fineTune.over(tokens.defaults) != tokens.defaults,
    );
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    if (disabledReason case final reason?) {
      return KitText(
        reason,
        key: const ValueKey('kit-fine-tune-disabled'),
        role: KitTextRole.secondary,
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final (i, knob) in knobs.indexed) ...[
          if (i > 0) SizedBox(height: tokens.space4),
          KeyedSubtree(
            key: knob.key,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: KitText(knob.title, role: KitTextRole.rowTitle),
                    ),
                    SizedBox(width: tokens.space2),
                    // The style's own choice, at the end of the line.
                    Flexible(
                      child: Align(
                        alignment: AlignmentDirectional.topEnd,
                        child: KitText(
                          knob.defaultNote,
                          role: KitTextRole.secondary,
                          textAlign: TextAlign.end,
                        ),
                      ),
                    ),
                  ],
                ),
                if (knob.hint case final hint?)
                  KitText(hint, role: KitTextRole.secondary),
                SizedBox(height: tokens.space2),
                knob._control(),
              ],
            ),
          ),
        ],
        KitReveal(
          child: onReset == null
              ? null
              : Padding(
                  padding: EdgeInsetsDirectional.only(top: tokens.space3),
                  child: Align(
                    alignment: AlignmentDirectional.centerStart,
                    child: KitButton.tertiary(
                      key: const ValueKey('kit-fine-tune-reset'),
                      label: resetLabel,
                      icon: AppIconography.restart,
                      onPressed: onReset,
                    ),
                  ),
                ),
        ),
      ],
    );
  }
}
