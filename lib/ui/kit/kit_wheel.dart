import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_surface_look.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_haptics.dart';

/// One column of a [KitWheel]: the values it rolls through, the one chosen
/// now and how each reads ("9:00 AM", "Tomorrow", "Every 3 hours").
@immutable
class KitWheelColumn<T> {
  const KitWheelColumn({
    required this.values,
    required this.selected,
    required this.label,
    required this.onChanged,
    required this.semanticsLabel,
    this.flex = 1,
    this.key,
  });

  /// In order, first at the top; at least one.
  final List<T> values;

  /// One of [values].
  final T selected;
  final String Function(T value) label;

  /// Called once the column rests on a different value.
  final ValueChanged<T> onChanged;

  /// What the column chooses ("Time", "Day"), read before its value.
  final String semanticsLabel;

  /// Its share of the wheel's width beside the other columns.
  final int flex;

  /// A test handle on the column.
  final Key? key;

  /// How many values the column rolls through.
  int get length => values.length;

  /// Where [selected] sits (the first row when it is not among [values]).
  int get selectedIndex {
    final i = values.indexOf(selected);
    return i < 0 ? 0 : i;
  }

  /// The words of the value at [index].
  String labelAt(int index) => label(values[index]);

  /// Reports the value at [index] as chosen.
  void chooseAt(int index) => onChanged(values[index]);
}

/// A value chosen by rolling it under a band, the way a phone's alarm sets
/// its time (the schedule sheet's time, its day and its interval): one to
/// three columns side by side, three rows tall, the chosen row in the title
/// size in `text1` inside a band with an accent line, its neighbours in the
/// headline size in `text3` (two of the screen's three text sizes). A tap on a neighbour rolls to it.
///
/// Keyboard: each column is one focus stop; the up and down arrows move one
/// step. Screen readers hear "Time, 9:00 AM" and increase or decrease it.
/// Rolling to a tapped row takes [KitMotion.quick], at once under reduced
/// motion; the chosen row's size eases over the same time.
///
/// Text grows with the person's text size up to 1.5×, so three rows stay in
/// one view at 2× text; the values stay whole (never cut).
///
/// States: none — a disabled wheel is not offered; hide it instead.
class KitWheel extends StatelessWidget {
  const KitWheel({super.key, required this.columns})
    : assert(columns.length >= 1 && columns.length <= 3);

  final List<KitWheelColumn<dynamic>> columns;

  /// The text scale the wheel grows to at most.
  static const maxTextScale = 1.5;

  /// One row's height at 1× text.
  static const rowExtent = 48.0;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.control(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final scale = math.min(
      MediaQuery.textScalerOf(context).scale(1),
      maxTextScale,
    );
    final extent = rowExtent * scale;
    return KitText.capScale(
      max: maxTextScale,
      child: SizedBox(
        height: extent * 3,
        child: Stack(
          children: [
            // The band the chosen row rests in.
            PositionedDirectional(
              start: 0,
              end: 0,
              top: extent,
              height: extent,
              // A focused field in the style: the band holds the value
              // being set.
              child: IgnorePointer(
                child: kitSurfaceBox(
                  style: KitSurfaceLook.styleOf(context),
                  decoration: KitSurfaceLook.decorate(
                    context,
                    solid: BoxDecoration(
                      color: tokens.roles.surface2,
                      borderRadius: BorderRadius.circular(tokens.buttonRadius),
                      border: Border.fromBorderSide(
                        BorderSide(
                          color: tokens.roles.accent,
                          // Two device pixels: the band reads as chosen at a
                          // glance, where a hairline disappears.
                          width: KitTokens.focusRingWidth(context),
                        ),
                      ),
                    ),
                    role: KitSurfaceRole.field,
                    level: KitSurfaceLevel.surface2,
                    selected: true,
                  ),
                  child: const SizedBox.expand(),
                ),
              ),
            ),
            Row(
              children: [
                for (final column in columns)
                  Expanded(
                    flex: column.flex,
                    child: _KitWheelColumnView(
                      key: column.key,
                      column: column,
                      extent: extent,
                    ),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _KitWheelColumnView extends StatefulWidget {
  const _KitWheelColumnView({
    super.key,
    required this.column,
    required this.extent,
  });

  final KitWheelColumn<dynamic> column;
  final double extent;

  @override
  State<_KitWheelColumnView> createState() => _KitWheelColumnViewState();
}

class _KitWheelColumnViewState extends State<_KitWheelColumnView> {
  late FixedExtentScrollController _controller;
  late int _index;
  final _focus = FocusNode(debugLabel: 'KitWheel column');
  bool _focused = false;

  KitWheelColumn<dynamic> get _column => widget.column;

  @override
  void initState() {
    super.initState();
    _index = _column.selectedIndex;
    _controller = FixedExtentScrollController(initialItem: _index);
  }

  @override
  void didUpdateWidget(_KitWheelColumnView oldWidget) {
    super.didUpdateWidget(oldWidget);
    final wanted = _column.selectedIndex;
    final listChanged = oldWidget.column.length != _column.length;
    if (wanted != _index || listChanged) {
      _index = wanted;
      // The host chose another value (a different rule): rest on it now.
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && _controller.hasClients) _controller.jumpToItem(_index);
      });
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    _focus.dispose();
    super.dispose();
  }

  void _settle(int index) {
    if (index == _index || index < 0 || index >= _column.length) return;
    // A light tick as each value passes the centre line, like a native
    // picker wheel.
    KitHaptics.select(context);
    setState(() => _index = index);
    _column.chooseAt(index);
  }

  void _go(int index) {
    final target = index.clamp(0, _column.length - 1);
    if (KitMotion.reduced(context)) {
      _controller.jumpToItem(target);
    } else {
      _controller.animateToItem(
        target,
        duration: KitMotion.quick,
        curve: KitMotion.enter,
      );
    }
  }

  KeyEventResult _key(FocusNode node, KeyEvent event) {
    if (event is! KeyDownEvent && event is! KeyRepeatEvent) {
      return KeyEventResult.ignored;
    }
    if (event.logicalKey == LogicalKeyboardKey.arrowDown) {
      _go(_index + 1);
      return KeyEventResult.handled;
    }
    if (event.logicalKey == LogicalKeyboardKey.arrowUp) {
      _go(_index - 1);
      return KeyEventResult.handled;
    }
    return KeyEventResult.ignored;
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final count = _column.length;
    final own = KitText.styleOf(
      context,
      KitTextRole.title,
      tone: KitTextTone.primary,
    );
    // Hook H3: the chosen value asks the style what reads on the band's
    // face (a focused field), so a style that fills it keeps it legible.
    final chosen = own.copyWith(
      color: KitSurfaceLook.wordsOn(
        context,
        fallback: own.color!,
        role: KitSurfaceRole.field,
        level: KitSurfaceLevel.surface2,
        selected: true,
      ),
      fontFeatures: const [FontFeature.tabularFigures()],
    );
    final other =
        KitText.styleOf(
          context,
          KitTextRole.headline,
          tone: KitTextTone.tertiary,
        ).copyWith(
          fontWeight: FontWeight.w500,
          fontFeatures: const [FontFeature.tabularFigures()],
        );
    final label = _column.labelAt(_index);
    String? neighbour(int i) => i >= 0 && i < count ? _column.labelAt(i) : null;
    return Semantics(
      container: true,
      label: _column.semanticsLabel,
      value: label,
      increasedValue: neighbour(_index + 1),
      decreasedValue: neighbour(_index - 1),
      onIncrease: _index + 1 < count ? () => _go(_index + 1) : null,
      onDecrease: _index > 0 ? () => _go(_index - 1) : null,
      child: Focus(
        focusNode: _focus,
        onKeyEvent: _key,
        onFocusChange: (v) => setState(() => _focused = v),
        child: DecoratedBox(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(tokens.buttonRadius),
            border: _focused
                ? Border.fromBorderSide(
                    BorderSide(
                      color: tokens.roles.text2,
                      width: KitTokens.focusRingWidth(context),
                    ),
                  )
                : null,
          ),
          child: ExcludeSemantics(
            child: ListWheelScrollView.useDelegate(
              controller: _controller,
              itemExtent: widget.extent,
              physics: const FixedExtentScrollPhysics(),
              // Nearly flat: the rows roll, they do not bend away.
              diameterRatio: 3,
              perspective: 0.001,
              overAndUnderCenterOpacity: 0.9,
              onSelectedItemChanged: _settle,
              childDelegate: ListWheelChildBuilderDelegate(
                childCount: count,
                builder: (context, i) => GestureDetector(
                  behavior: HitTestBehavior.opaque,
                  onTap: () => _go(i),
                  child: Center(
                    child: AnimatedDefaultTextStyle(
                      duration: KitMotion.reduced(context)
                          ? Duration.zero
                          : KitMotion.quick,
                      curve: KitMotion.enter,
                      style: i == _index ? chosen : other,
                      textAlign: TextAlign.center,
                      maxLines: 1,
                      child: FittedBox(
                        fit: BoxFit.scaleDown,
                        child: Padding(
                          padding: EdgeInsets.symmetric(
                            horizontal: tokens.space2,
                          ),
                          child: Text(_column.labelAt(i)),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
