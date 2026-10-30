import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../l10n/app_localizations.dart';
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_surface_look.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_haptics.dart';

/// One step of a [KitStepSlider]: its value, its visible name and an
/// optional key for its 48 dp target.
@immutable
class KitSliderStep<T> {
  const KitSliderStep({required this.value, required this.label, this.key});

  final T value;
  final String label;
  final Key? key;
}

/// One choice among a few ordered steps, as a slider that rests only on the
/// steps (docs/ux-system/kit-api/KitStepSlider.md): a size, a pace, a
/// strength. A small and a large "A" at its ends say which way is more, the
/// chosen step's name sits under the track, and each step is also a target
/// of its own, so a tap goes straight to it. Use [KitSegmented] where the
/// options are words that must all show; use this where they are one scale.
///
/// Drag or tap to choose; the left and right arrow keys (reading direction
/// aware) and a screen reader's increase and decrease move one step. The
/// thumb glides to the step over [KitMotion.quick], at once under reduced
/// motion.
///
/// States: disabled.
class KitStepSlider<T> extends StatefulWidget {
  const KitStepSlider({
    super.key,
    required this.semanticsLabel,
    required this.steps,
    required this.selected,
    required this.onChanged,
  }) : assert(steps.length >= 2);

  /// What is being chosen: "Text size".
  final String semanticsLabel;

  /// The steps, least to most.
  final List<KitSliderStep<T>> steps;

  final T selected;

  /// Null disables it.
  final ValueChanged<T>? onChanged;

  @override
  State<KitStepSlider<T>> createState() => _KitStepSliderState<T>();
}

class _KitStepSliderState<T> extends State<KitStepSlider<T>> {
  final _focus = FocusNode(debugLabel: 'KitStepSlider');
  bool _focused = false;

  int get _index {
    final i = widget.steps.indexWhere((s) => s.value == widget.selected);
    return i < 0 ? 0 : i;
  }

  @override
  void initState() {
    super.initState();
    _focus.addListener(() {
      if (_focused != _focus.hasFocus) {
        setState(() => _focused = _focus.hasFocus);
      }
    });
  }

  @override
  void dispose() {
    _focus.dispose();
    super.dispose();
  }

  void _choose(int index) {
    final onChanged = widget.onChanged;
    if (onChanged == null) return;
    final next = index.clamp(0, widget.steps.length - 1);
    if (next == _index) return;
    // Each step reached clicks, as a wheel's next value does (MOT-11).
    KitHaptics.select(context);
    onChanged(widget.steps[next].value);
  }

  KeyEventResult _onKey(FocusNode node, KeyEvent event) {
    if (event is KeyUpEvent || widget.onChanged == null) {
      return KeyEventResult.ignored;
    }
    final ltr = Directionality.of(context) == TextDirection.ltr;
    final key = event.logicalKey;
    if (key == LogicalKeyboardKey.arrowRight ||
        key == LogicalKeyboardKey.arrowUp) {
      _choose(_index + (ltr || key == LogicalKeyboardKey.arrowUp ? 1 : -1));
      return KeyEventResult.handled;
    }
    if (key == LogicalKeyboardKey.arrowLeft ||
        key == LogicalKeyboardKey.arrowDown) {
      _choose(_index + (ltr || key == LogicalKeyboardKey.arrowDown ? -1 : 1));
      return KeyEventResult.handled;
    }
    if (key == LogicalKeyboardKey.home) {
      _choose(0);
      return KeyEventResult.handled;
    }
    if (key == LogicalKeyboardKey.end) {
      _choose(widget.steps.length - 1);
      return KeyEventResult.handled;
    }
    return KeyEventResult.ignored;
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final enabled = widget.onChanged != null;
    final count = widget.steps.length;
    final index = _index;
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final glide = KitMotion.reduced(context) ? Duration.zero : KitMotion.quick;
    final thumb = tokens.minTarget * .6;
    final target = tokens.minTarget;

    Widget end(String glyph, KitTextRole role) => SizedBox(
      width: target * .7,
      child: Center(
        child: KitText(glyph, role: role, tone: KitTextTone.secondary),
      ),
    );

    final track = LayoutBuilder(
      builder: (context, box) {
        final usable = box.maxWidth - target;
        double x(int i) => target / 2 + usable * i / (count - 1);
        int nearest(double dx) {
          final from = rtl ? box.maxWidth - dx : dx;
          final t = ((from - target / 2) / usable).clamp(0.0, 1.0);
          return (t * (count - 1)).round();
        }

        final line = tokens.space1;
        final style = KitSurfaceLook.styleOf(context);
        // The rail is the style's track; the thumb a round control in its
        // own colour. The passed part and the step marks stay the part's.
        final rail = KitSurfaceLook.decorate(
          context,
          solid: BoxDecoration(
            color: roles.surface3,
            borderRadius: BorderRadius.circular(line),
          ),
          role: KitSurfaceRole.track,
          level: KitSurfaceLevel.surface3,
        );
        final thumbFill = enabled ? roles.accent : roles.text3;
        final thumbOwn = BoxDecoration(
          shape: BoxShape.circle,
          color: thumbFill,
          border: Border.all(
            color: _focused ? roles.text1 : roles.surface1,
            width: _focused
                ? KitTokens.focusRingWidth(context)
                : tokens.space1 * .75,
          ),
        );
        final thumbLook = KitSurfaceLook.decorate(
          context,
          solid: thumbOwn,
          role: KitSurfaceRole.control,
          level: KitSurfaceLevel.surface2,
          circle: true,
          color: thumbFill,
        );
        // A style's own thumb drops the part's ring; focus still shows.
        final focusRing = _focused && !identical(thumbLook, thumbOwn)
            ? KitSurfaceDecoration(
                circle: true,
                fill: Colors.transparent,
                edgeStart: roles.text1,
                edgeEnd: roles.text1,
                edgeWidth: KitTokens.focusRingWidth(context),
              )
            : null;
        return GestureDetector(
          behavior: HitTestBehavior.opaque,
          onHorizontalDragStart: enabled
              ? (d) {
                  _focus.requestFocus();
                  _choose(nearest(d.localPosition.dx));
                }
              : null,
          onHorizontalDragUpdate: enabled
              ? (d) => _choose(nearest(d.localPosition.dx))
              : null,
          child: SizedBox(
            height: target,
            child: Stack(
              clipBehavior: Clip.none,
              children: [
                // The rail, and the part of it already passed.
                PositionedDirectional(
                  start: target / 2,
                  end: target / 2,
                  top: (target - line) / 2,
                  height: line,
                  child: kitSurfaceBox(
                    style: style,
                    decoration: rail,
                    child: const SizedBox.expand(),
                  ),
                ),
                AnimatedPositionedDirectional(
                  duration: glide,
                  curve: KitMotion.enter,
                  start: target / 2,
                  width: usable * index / (count - 1),
                  top: (target - line) / 2,
                  height: line,
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      color: enabled ? roles.accent : roles.text3,
                      borderRadius: BorderRadius.circular(line),
                    ),
                  ),
                ),
                for (var i = 0; i < count; i++)
                  PositionedDirectional(
                    start: x(i) - target / 2,
                    top: 0,
                    width: target,
                    height: target,
                    child: GestureDetector(
                      key: widget.steps[i].key,
                      behavior: HitTestBehavior.opaque,
                      onTap: enabled
                          ? () {
                              _focus.requestFocus();
                              _choose(i);
                            }
                          : null,
                      child: Center(
                        child: DecoratedBox(
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: i <= index && enabled
                                ? roles.accent
                                : roles.text3,
                          ),
                          child: SizedBox.square(dimension: tokens.space2),
                        ),
                      ),
                    ),
                  ),
                AnimatedPositionedDirectional(
                  duration: glide,
                  curve: KitMotion.enter,
                  start: x(index) - thumb / 2,
                  top: (target - thumb) / 2,
                  width: thumb,
                  height: thumb,
                  child: IgnorePointer(
                    child: kitSurfaceBox(
                      style: style,
                      decoration: thumbLook,
                      child: focusRing == null
                          ? const SizedBox.expand()
                          : DecoratedBox(
                              position: DecorationPosition.foreground,
                              decoration: focusRing,
                              child: const SizedBox.expand(),
                            ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );

    return Semantics(
      container: true,
      slider: true,
      enabled: enabled,
      label: widget.semanticsLabel,
      value: widget.steps[index].label,
      increasedValue: index < count - 1 ? widget.steps[index + 1].label : null,
      decreasedValue: index > 0 ? widget.steps[index - 1].label : null,
      onIncrease: enabled && index < count - 1
          ? () => _choose(index + 1)
          : null,
      onDecrease: enabled && index > 0 ? () => _choose(index - 1) : null,
      child: Focus(
        focusNode: _focus,
        canRequestFocus: enabled,
        onKeyEvent: _onKey,
        child: ExcludeSemantics(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  end(l10n.kitStepSliderLess, KitTextRole.secondary),
                  Expanded(child: track),
                  end(l10n.kitStepSliderMore, KitTextRole.title),
                ],
              ),
              AnimatedSwitcher(
                duration: glide,
                switchInCurve: KitMotion.enter,
                switchOutCurve: KitMotion.exit,
                child: KitText(
                  widget.steps[index].label,
                  key: ValueKey(index),
                  role: KitTextRole.secondary,
                  textAlign: TextAlign.center,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
