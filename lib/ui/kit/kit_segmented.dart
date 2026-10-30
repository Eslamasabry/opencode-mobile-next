import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart' show NumberFormat;

import '../app_iconography.dart';
import 'kit_choice_list.dart';
import 'kit_divider.dart';
import 'kit_ink_layer.dart';
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_surface.dart';
import 'kit_surface_look.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_haptics.dart';
import 'motion/kit_motion_parts.dart' show KitSwap;

/// One choice of a [KitSegmented] group
/// (docs/ux-system/kit-api/KitSegmented.md): a value, its visible label and
/// optional leading icon and count, and whether it can be chosen right now.
/// An icon alone is never enough — [label] always carries the words.
@immutable
class KitSegment<T> {
  const KitSegment({
    required this.value,
    required this.label,
    this.icon,
    this.count,
    this.enabled = true,
    this.disabledReason,
    this.key,
  }) : assert(
         enabled || disabledReason != null,
         'KitSegment.disabledReason is required when enabled is false: a '
         'disabled choice always shows why (STATE-8).',
       );

  final T value;
  final String label;
  final IconData? icon;

  /// "Needs you 2": tabular figures, part of the label's semantics.
  final int? count;
  final bool enabled;

  /// Shown as text under the control when this segment is disabled:
  /// "Chosen at install · reinstall to change".
  final String? disabledReason;
  final Key? key;
}

/// One choice among 2–4 short, always-visible options (design standard §2,
/// §5; kit v2 §1.6, §8.2; docs/ux-system/kit-api/KitSegmented.md): a scope, a
/// mode, a range or a filter. Full width, start-aligned, never a centred
/// pill, and marks the chosen segment with a check rather than colour alone.
///
/// Its scenes (KitSegmented.md "States"): default (one selected),
/// with-counts, segment-disabled (the reason under the control), disabled
/// (the whole control, with its reason) and stacked.
///
/// Stacked (KIT-24, Appendix A #78): the part measures, the way KitAskLine
/// does, whether every segment's check, icon, label and count fit on one
/// line in its equal share of the width. When one does not, or the text
/// scale is 2.0 or more, the track gives way to a vertical stack of
/// full-width [KitChoiceRow]s with radio marks: the same order, values and
/// keys, a disabled segment's reason on its own row, and still one
/// mutually exclusive group with one Tab stop (Arrow Up and Down, Home and
/// End, and Left and Right in reading order move focus there). Nothing
/// truncates in either form. The switch is a layout change with no
/// animation.
///
/// States: disabled.
class KitSegmented<T> extends StatelessWidget {
  const KitSegmented({
    super.key,
    required this.segments,
    required this.selected,
    required this.onChanged,
    required this.semanticsLabel,
    this.disabledReason,
    this.iconOnly = false,
    this.fitLabels = false,
    this.tall = false,
  }) : assert(
         segments.length >= 2 && segments.length <= 4,
         'KitSegmented needs 2 to 4 segments (KitSegmented.md); use '
         'KitPickerRow for more.',
       ),
       assert(
         onChanged != null || disabledReason != null,
         'KitSegmented.disabledReason is required when onChanged is null.',
       );

  /// 2 to 4 choices (G37).
  final List<KitSegment<T>> segments;

  /// One of [segments]' values (G37, checked when the control builds).
  final T selected;

  /// Null disables the whole control (then [disabledReason] is required).
  /// Called once per activation of a *different* enabled segment; activating
  /// the selected segment does nothing.
  final ValueChanged<T>? onChanged;

  /// The group's name ("Time range"): announced, and named by a visible
  /// `SectionLabel` above the control (the caller's job, not this part's).
  final String semanticsLabel;

  /// Shown as text under the control when [onChanged] is null.
  final String? disabledReason;

  /// A compact pill of icons, each [KitSegment.label] kept as its tooltip and
  /// its semantics (an icon alone is never the only name). Every segment
  /// needs an icon. It is as wide as its icons, never stacks, and sits at the
  /// start of the space it is given.
  final bool iconOnly;

  /// Each segment as wide as its own words (plus an even share of the
  /// room left over) instead of an equal share, so four choices of
  /// different lengths ("Once", "Every day", "Weekdays", "Custom") stay on
  /// one line where equal shares would stack. It still stacks when the
  /// words together do not fit, or at 2× text.
  final bool fitLabels;

  /// Each segment as its icon over its word, in a taller track (Settings ›
  /// Appearance's Auto / Light / Dark / Saver). Every segment needs an icon.
  /// The chosen one is the filled block, its words on the accent; there is
  /// no check (the fill is a shape, not colour alone). It stacks like the
  /// row form when a word does not fit its share, or at 2× text.
  final bool tall;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    // Not in the constructor: `Iterable.any` is not a constant expression,
    // and the constructor stays `const` as the frozen API declares it.
    assert(
      segments.any((s) => s.value == selected),
      'KitSegmented.selected must be the value of one of its segments.',
    );
    assert(
      !iconOnly || segments.every((s) => s.icon != null),
      'KitSegmented.iconOnly needs an icon on every segment.',
    );
    assert(
      !tall || segments.every((s) => s.icon != null),
      'KitSegmented.tall needs an icon on every segment.',
    );
    return _SegmentedGroup<T>(part: this);
  }
}

/// The focus, hover and press bookkeeping behind [KitSegmented]. The
/// selection itself is never kept here: value is truth (DATA-11).
class _SegmentedGroup<T> extends StatefulWidget {
  const _SegmentedGroup({required this.part});

  final KitSegmented<T> part;

  @override
  State<_SegmentedGroup<T>> createState() => _SegmentedGroupState<T>();
}

class _SegmentedGroupState<T> extends State<_SegmentedGroup<T>> {
  late List<FocusNode> _nodes;

  /// The stacked form's rows. Their own nodes: a [KitChoiceRow]'s
  /// KitTappable installs its own key handler on the node it is given.
  late List<FocusNode> _stackNodes;

  /// The segment holding keyboard focus; null while focus is outside the
  /// group, so Tab enters on the selected segment again.
  int? _focused;

  /// The segment showing the keyboard focus ring (focus came from the
  /// keyboard: `FocusHighlightMode.traditional`).
  int? _ring;
  int? _hovered;

  /// Each segment's press, shown on the next frame of a touch
  /// (KitPressTracker) rather than after the tap-or-scroll wait.
  final _presses = <int, KitPressTracker>{};

  KitPressTracker _press(int index) => _presses[index] ??= KitPressTracker(() {
    if (mounted) setState(() {});
  });

  KitSegmented<T> get _part => widget.part;
  List<KitSegment<T>> get _segments => _part.segments;
  bool get _controlEnabled => _part.onChanged != null;
  bool _canChoose(int index) => _controlEnabled && _segments[index].enabled;

  @override
  void initState() {
    super.initState();
    _nodes = _buildNodes();
    _stackNodes = _buildStackNodes();
  }

  List<FocusNode> _buildNodes() => [
    for (var i = 0; i < _segments.length; i++)
      FocusNode(
        debugLabel: 'kit-segmented-$i',
        onKeyEvent: (node, event) => _handleKey(i, event),
      ),
  ];

  List<FocusNode> _buildStackNodes() => [
    for (var i = 0; i < _segments.length; i++)
      FocusNode(debugLabel: 'kit-segmented-row-$i'),
  ];

  @override
  void didUpdateWidget(covariant _SegmentedGroup<T> oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.part.segments.length != _segments.length) {
      for (final node in [..._nodes, ..._stackNodes]) {
        node.dispose();
      }
      _nodes = _buildNodes();
      _stackNodes = _buildStackNodes();
      _focused = _ring = _hovered = null;
      for (final press in _presses.values) {
        press.dispose();
      }
      _presses.clear();
    }
  }

  @override
  void dispose() {
    for (final node in [..._nodes, ..._stackNodes]) {
      node.dispose();
    }
    for (final press in _presses.values) {
      press.dispose();
    }
    super.dispose();
  }

  int _selectedIndex() {
    final i = _segments.indexWhere((s) => s.value == _part.selected);
    return i < 0 ? 0 : i;
  }

  /// Where Tab enters the group: the selected segment, or the first one
  /// that can be chosen when the selected one cannot.
  int _entryIndex() {
    final selected = _selectedIndex();
    if (_canChoose(selected)) return selected;
    for (var i = 0; i < _segments.length; i++) {
      if (_canChoose(i)) return i;
    }
    return selected;
  }

  /// The group is one Tab stop (§8.2, G14): only the focused segment, or
  /// the entry segment when nothing here is focused, takes part in Tab.
  int _stopIndex() {
    final focused = _focused;
    return focused != null && focused < _nodes.length ? focused : _entryIndex();
  }

  /// [_OneStopPolicy]'s view of the group: which nodes are segments (both
  /// forms; only one is mounted) and which of them Tab may reach.
  bool _isSegmentNode(FocusNode node) =>
      _nodes.contains(node) || _stackNodes.contains(node);

  bool _isStopNode(FocusNode node) {
    final stop = _stopIndex();
    return node == _nodes[stop] || node == _stackNodes[stop];
  }

  late final _policy = _OneStopPolicy(
    isSegment: _isSegmentNode,
    isStop: _isStopNode,
  );

  void _groupFocusChanged(bool hasFocus) {
    if (hasFocus || _focused == null) return;
    setState(() => _focused = null);
  }

  void _segmentFocusChanged(int index, bool hasFocus) {
    if (!hasFocus) return;
    setState(() => _focused = index);
  }

  int? _nextChoosable(int from, {required bool forward}) {
    final n = _segments.length;
    for (var step = 1; step <= n; step++) {
      final i = forward ? (from + step) % n : (from - step + n) % n;
      if (_canChoose(i)) return i;
    }
    return null;
  }

  KeyEventResult _handleKey(int index, KeyEvent event) {
    if (event is! KeyDownEvent && event is! KeyRepeatEvent) {
      return KeyEventResult.ignored;
    }
    final key = event.logicalKey;
    final right = key == LogicalKeyboardKey.arrowRight;
    if (!right && key != LogicalKeyboardKey.arrowLeft) {
      return KeyEventResult.ignored;
    }
    // Arrows follow the reading direction and move focus only; they never
    // select (the same rule as KitChoiceList). They stay inside the group,
    // even when no other segment can take focus.
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final target = _nextChoosable(index, forward: right != rtl);
    if (target != null && target != index) _nodes[target].requestFocus();
    return KeyEventResult.handled;
  }

  /// The stacked form's arrows: Up and Down (Home and End jump; Left and
  /// Right follow the reading direction), focus only, never a selection;
  /// they stay inside the group.
  KeyEventResult _handleStackKey(FocusNode _, KeyEvent event) {
    if (event is! KeyDownEvent && event is! KeyRepeatEvent) {
      return KeyEventResult.ignored;
    }
    final at = _stackNodes.indexWhere((node) => node.hasPrimaryFocus);
    if (at < 0) return KeyEventResult.ignored;
    final key = event.logicalKey;
    final choosable = [
      for (var i = 0; i < _segments.length; i++)
        if (_canChoose(i)) i,
    ];
    // Left and Right keep working as in the row form (reading direction),
    // so a person who learned them is not stranded when the part stacks.
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final next =
        key == LogicalKeyboardKey.arrowDown ||
        key ==
            (rtl
                ? LogicalKeyboardKey.arrowLeft
                : LogicalKeyboardKey.arrowRight);
    final previous =
        key == LogicalKeyboardKey.arrowUp ||
        key ==
            (rtl
                ? LogicalKeyboardKey.arrowRight
                : LogicalKeyboardKey.arrowLeft);
    final int? target;
    if (next || previous) {
      target = _nextChoosable(at, forward: next);
    } else if (key == LogicalKeyboardKey.home) {
      target = choosable.firstOrNull;
    } else if (key == LogicalKeyboardKey.end) {
      target = choosable.lastOrNull;
    } else {
      return KeyEventResult.ignored;
    }
    if (target != null && target != at) _stackNodes[target].requestFocus();
    return KeyEventResult.handled;
  }

  /// A tap, Space/Enter or the semantics tap. It never moves focus: a touch
  /// does not leave a keyboard ring behind.
  void _choose(int index) {
    if (!_canChoose(index)) return;
    final value = _segments[index].value;
    if (value == _part.selected) return;
    KitHaptics.select(context);
    _part.onChanged!(value);
  }

  void _setHovered(int index, bool hovered) {
    final next = hovered ? index : (_hovered == index ? null : _hovered);
    if (next != _hovered) setState(() => _hovered = next);
  }

  String _formatCount(BuildContext context, int count) =>
      NumberFormat.decimalPattern(
        Localizations.localeOf(context).toString(),
      ).format(count);

  /// Distinct reasons to show under the control, in first-seen order.
  List<String> _reasons() {
    if (!_controlEnabled) return [_part.disabledReason!];
    final seen = <String>{};
    return [
      for (final segment in _segments)
        if (!segment.enabled && seen.add(segment.disabledReason!))
          segment.disabledReason!,
    ];
  }

  @override
  Widget build(BuildContext context) {
    return Semantics(
      container: true,
      label: _part.semanticsLabel,
      child: FocusTraversalGroup(
        policy: _policy,
        child: Focus(
          canRequestFocus: false,
          skipTraversal: true,
          includeSemantics: false,
          onFocusChange: _groupFocusChanged,
          child: LayoutBuilder(
            builder: (context, constraints) {
              if (_part.iconOnly) {
                final tokens = KitTokens.of(context);
                return _withReasons(
                  context,
                  Align(
                    alignment: AlignmentDirectional.centerStart,
                    widthFactor: 1,
                    child: SizedBox(
                      width: _segments.length * tokens.minTarget * 1.15,
                      child: _track(context),
                    ),
                  ),
                  _reasons(),
                );
              }
              final stacked = _stacks(context, constraints.maxWidth);
              return _withReasons(
                context,
                stacked
                    ? _stack(context)
                    : _track(context, width: constraints.maxWidth),
                // In the stack a disabled segment's reason is on its row;
                // only the whole control's reason stays under it.
                stacked
                    ? [if (!_controlEnabled) _part.disabledReason!]
                    : _reasons(),
              );
            },
          ),
        ),
      ),
    );
  }

  /// KIT-24: whether any segment's one line (check, icon, label, count and
  /// insets) is wider than its equal share of [width], or the text is at
  /// 2.0 or more. Measured like KitAskLine, with the styles the row draws.
  bool _stacks(BuildContext context, double width) {
    final scaler = MediaQuery.textScalerOf(context);
    final labelStyle = KitText.styleOf(context, KitTextRole.label);
    if (labelStyle.fontSize case final size?
        when scaler.scale(size) / size >= 2) {
      return true;
    }
    if (!width.isFinite) return false;
    final needed = _neededWidths(context);
    final room = width - 2 * KitTokens.hairlineWidth(context);
    if (_part.fitLabels) {
      return needed.fold<double>(0, (a, b) => a + b) > room;
    }
    final share = room / _segments.length;
    return needed.any((w) => w > share);
  }

  /// Each segment's one line: check, icon, label, count and insets.
  List<double> _neededWidths(BuildContext context) {
    final tokens = KitTokens.of(context);
    final scaler = MediaQuery.textScalerOf(context);
    final labelStyle = KitText.styleOf(context, KitTextRole.label);
    final direction = Directionality.of(context);
    double measure(String text, TextStyle style) {
      final painter = TextPainter(
        text: TextSpan(text: text, style: style),
        textDirection: direction,
        textScaler: scaler,
        maxLines: 1,
      )..layout();
      final result = painter.width.ceilToDouble();
      painter.dispose();
      return result;
    }

    final countStyle = KitText.styleOf(
      context,
      KitTextRole.caption,
    ).copyWith(fontFeatures: const [FontFeature.tabularFigures()]);
    final iconSize = tokens.iconSize(context, tokens.smallIconSize);
    if (_part.tall) {
      final small = KitText.styleOf(context, KitTextRole.caption);
      return [
        for (final segment in _segments)
          2 * tokens.space1 +
              (measure(segment.label, small) > iconSize
                  ? measure(segment.label, small)
                  : iconSize),
      ];
    }
    return [
      for (final segment in _segments)
        2 * tokens.space2 +
            iconSize +
            tokens.space1 +
            measure(segment.label, labelStyle) +
            (segment.icon != null ? iconSize + tokens.space1 : 0) +
            (segment.count == null
                ? 0
                : tokens.space1 +
                      measure(
                        _formatCount(context, segment.count!),
                        countStyle,
                      )),
    ];
  }

  Widget _withReasons(
    BuildContext context,
    Widget control,
    List<String> reasons,
  ) {
    if (reasons.isEmpty) return control;
    final tokens = KitTokens.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        control,
        Padding(
          padding: EdgeInsetsDirectional.only(
            start: tokens.space2,
            top: tokens.space1,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              for (final reason in reasons)
                KitText(reason, role: KitTextRole.secondary),
            ],
          ),
        ),
      ],
    );
  }

  /// The stacked form (KIT-24): one full-width [KitChoiceRow] per segment
  /// in a `surface1` panel, as KitChoiceList draws its rows.
  Widget _stack(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final iconSize = tokens.iconSize(context, tokens.smallIconSize);
    final rows = <Widget>[
      for (var i = 0; i < _segments.length; i++)
        Focus(
          canRequestFocus: false,
          skipTraversal: true,
          includeSemantics: false,
          onFocusChange: (focused) => _segmentFocusChanged(i, focused),
          child: _row(context, i, iconSize: iconSize),
        ),
    ];
    return Focus(
      canRequestFocus: false,
      skipTraversal: true,
      includeSemantics: false,
      onKeyEvent: _handleStackKey,
      child: kitSurfaceMaterial(
        context,
        level: KitSurfaceLevel.surface1,
        fill: roles.surface1,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(tokens.panelCornerRadius),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            for (var i = 0; i < rows.length; i++) ...[
              if (i > 0)
                Padding(
                  padding: EdgeInsetsDirectional.only(
                    start:
                        tokens.gutter +
                        tokens
                            .iconSize(context, tokens.smallIconSize)
                            .roundToDouble() +
                        tokens.space3,
                  ),
                  child: const KitDivider(),
                ),
              rows[i],
            ],
          ],
        ),
      ),
    );
  }

  Widget _row(BuildContext context, int index, {required double iconSize}) {
    final roles = KitTokens.of(context).roles;
    final segment = _segments[index];
    final enabled = _canChoose(index);
    final count = segment.count;
    return KitChoiceRow<T>(
      key: segment.key,
      choice: KitChoice<T>(
        value: segment.value,
        title: count == null
            ? segment.label
            : '${segment.label} · ${_formatCount(context, count)}',
        leading: segment.icon == null
            ? null
            : Icon(
                segment.icon,
                size: iconSize,
                color: enabled ? roles.text2 : roles.text3,
              ),
        enabled: segment.enabled,
        disabledReason: segment.disabledReason,
      ),
      selected: segment.value == _part.selected,
      focusNode: _stackNodes[index],
      onTap: enabled ? () => _choose(index) : null,
    );
  }

  Widget _track(BuildContext context, {double width = double.infinity}) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final reduced = KitMotion.reduced(context);
    final hairline = KitTokens.hairlineWidth(context);
    final n = _segments.length;
    // Half a space1 (2 dp): the selected indicator's inset from the track.
    final inset = tokens.space1 / 2;
    final selectedIndex = _selectedIndex();
    final indicatorAlign = n > 1
        ? AlignmentDirectional(-1 + selectedIndex * (2 / (n - 1)), 0)
        : AlignmentDirectional.center;
    // [KitSegmented.fitLabels]: each segment its own words' width plus an
    // even share of what is left.
    List<double>? widths;
    if (_part.fitLabels && width.isFinite) {
      final needed = _neededWidths(context);
      final room = width - 2 * hairline;
      final spare = room - needed.fold<double>(0, (a, b) => a + b);
      widths = [for (final w in needed) w + spare / n];
    }
    final fitted = widths;

    final style = KitSurfaceLook.styleOf(context);
    // Solid: the chosen segment is a neutral step up from the track
    // (surface2 in light, surface3 in dark, with the hairline), as the tab
    // strip's is; its words in text1 and the check in the accent. Every
    // other style draws a chosen control in mint its own way (Soft's well,
    // Paper's inverted ink).
    final quiet = style == KitSurfaceStyle.solid;
    final step = roles.isDark ? roles.surface3 : roles.surface2;
    final indicatorRadius = BorderRadius.circular(tokens.buttonRadius - inset);
    final indicator = kitSurfaceBox(
      style: style,
      decoration: KitSurfaceLook.decorate(
        context,
        solid: BoxDecoration(
          color: quiet ? step : roles.accent,
          borderRadius: indicatorRadius,
          border: quiet
              ? Border.all(
                  color: roles.hairline,
                  width: KitTokens.hairlineWidth(context),
                )
              : null,
        ),
        role: KitSurfaceRole.control,
        color: quiet ? step : roles.accent,
        selected: true,
      ),
      child: const SizedBox.expand(),
    );
    final trackRadius = BorderRadius.circular(tokens.buttonRadius);
    return SizedBox(
      // The track is taller by its hairline on each side so a segment never
      // shrinks under [KitTokens.minTarget] (A11Y-3); the segments sit inside
      // that hairline in every style.
      height:
          (_part.tall ? _tallHeight(context) : tokens.minTarget) + 2 * hairline,
      child: kitSurfaceBox(
        style: style,
        decoration: KitSurfaceLook.decorate(
          context,
          solid: BoxDecoration(
            color: roles.surface1,
            borderRadius: trackRadius,
            border: Border.all(
              color: roles.hairline,
              width: KitTokens.hairlineWidth(context),
            ),
          ),
          role: KitSurfaceRole.track,
          borderRadius: trackRadius,
        ),
        child: Padding(
          padding: EdgeInsets.all(hairline),
          child: Stack(
            alignment: AlignmentDirectional.center,
            children: [
              if (fitted != null)
                AnimatedPositionedDirectional(
                  duration: reduced ? Duration.zero : KitMotion.quick,
                  curve: KitMotion.enter,
                  start: fitted
                      .take(selectedIndex)
                      .fold<double>(0, (a, b) => a + b),
                  width: fitted[selectedIndex],
                  top: 0,
                  bottom: 0,
                  child: Padding(
                    padding: EdgeInsets.all(inset),
                    child: indicator,
                  ),
                )
              else
                AnimatedAlign(
                  duration: reduced ? Duration.zero : KitMotion.quick,
                  curve: KitMotion.enter,
                  alignment: indicatorAlign,
                  child: FractionallySizedBox(
                    widthFactor: 1 / n,
                    heightFactor: 1,
                    // Inset from the track's own hairline and concentric with its
                    // corners, with no border of its own: two hairlines side by
                    // side would read as one doubled line (LOOK-21).
                    child: Padding(
                      padding: EdgeInsets.all(inset),
                      // The chosen segment is filled in the accent, as the
                      // studio's tab pill is.
                      child: indicator,
                    ),
                  ),
                ),
              Row(
                children: [
                  for (var i = 0; i < n; i++)
                    if (fitted != null)
                      SizedBox(
                        width: fitted[i],
                        child: _segment(context, i, reduced: reduced),
                      )
                    else
                      Expanded(
                        child: _part.tall && i < n - 1
                            // The tall track parts its resting segments with a
                            // hairline; none beside the chosen block.
                            ? DecoratedBox(
                                position: DecorationPosition.foreground,
                                decoration: BoxDecoration(
                                  border: BorderDirectional(
                                    end: BorderSide(
                                      color:
                                          i == selectedIndex ||
                                              i + 1 == selectedIndex
                                          ? roles.hairline.withAlpha(0)
                                          : roles.hairline,
                                      width: KitTokens.hairlineWidth(context),
                                    ),
                                  ),
                                ),
                                child: _segment(context, i, reduced: reduced),
                              )
                            : _segment(context, i, reduced: reduced),
                      ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// The tall track's height: the icon, a small gap and one line of the
  /// small words, inside the same insets as a row segment.
  double _tallHeight(BuildContext context) {
    final tokens = KitTokens.of(context);
    final scaler = MediaQuery.textScalerOf(context);
    final small = KitText.styleOf(context, KitTextRole.caption);
    final line = scaler.scale(small.fontSize ?? 12) * (small.height ?? 1.3);
    final content =
        tokens.iconSize(context, tokens.smallIconSize) * 1.2 +
        tokens.space1 +
        line +
        2 * tokens.space2;
    return content < tokens.minTarget ? tokens.minTarget : content;
  }

  Widget _segment(BuildContext context, int index, {required bool reduced}) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final segment = _segments[index];
    final isSelected = segment.value == _part.selected;
    final enabled = _canChoose(index);
    final radius = BorderRadius.circular(tokens.buttonRadius - tokens.space1);

    // Hover and pressed are surface steps above the surface1 track, never
    // overlays (KitTappable's rule, README.md decision D11): hover is the
    // next step whose colour differs (surface2 in dark, surface3 in light),
    // pressed the step after that, at most surface3. The selected segment
    // already sits on the surface3 indicator.
    // On Android a press draws the native ripple instead (KitInk).
    final pressedFill =
        (_presses[index]?.shown ?? false) && !KitInk.on(context);
    final KitSurfaceLevel? step = !enabled || isSelected
        ? null
        : pressedFill
        ? KitSurfaceLevel.surface3
        : _hovered == index
        ? (roles.isDark ? KitSurfaceLevel.surface2 : KitSurfaceLevel.surface3)
        : null;
    final own = BoxDecoration(
      color: step == null ? null : tokens.fillOf(step),
      borderRadius: radius,
    );
    final iconSize = tokens.iconSize(context, tokens.smallIconSize);
    // Solid's chosen segment is a neutral step: text1 words, accent check.
    final quiet = KitSurfaceLook.styleOf(context) == KitSurfaceStyle.solid;
    final tone = !enabled
        ? KitTextTone.tertiary
        : isSelected
        ? (quiet ? KitTextTone.primary : KitTextTone.onAccent)
        : KitTextTone.secondary;
    // Hook H3: a style may say the chosen segment's words colour.
    final chosenInk = KitSurfaceLook.wordsOn(
      context,
      fallback: quiet ? roles.text1 : roles.onAccent,
      role: KitSurfaceRole.control,
      color: roles.accent,
      selected: true,
    );
    final Color color = !enabled
        ? roles.text3
        : isSelected
        ? chosenInk
        : roles.text2;

    final count = segment.count;
    final label = count == null
        ? segment.label
        : '${segment.label}, ${_formatCount(context, count)}';

    // Solid's tall and icon-only segments have no check: the chosen one's
    // glyph takes the accent instead, its one accent mark.
    final glyph = quiet && isSelected && enabled ? roles.accent : color;
    final Widget content = _part.tall
        ? Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(segment.icon, size: iconSize * 1.2, color: glyph),
              SizedBox(height: tokens.space1),
              Text(
                segment.label,
                maxLines: 1,
                softWrap: false,
                style: KitText.styleOf(
                  context,
                  KitTextRole.caption,
                  tone: tone,
                ).copyWith(color: color),
              ),
            ],
          )
        : _part.iconOnly
        ? Tooltip(
            message: segment.label,
            child: Icon(segment.icon, size: iconSize * 1.2, color: glyph),
          )
        : Row(
            mainAxisAlignment: MainAxisAlignment.center,
            mainAxisSize: MainAxisSize.min,
            children: [
              // fitLabels: the words stay centred; only the chosen one
              // shows its check (each width already has room for it).
              if (!_part.fitLabels || isSelected)
                AnimatedOpacity(
                  opacity: isSelected ? 1 : 0,
                  duration: reduced ? Duration.zero : KitMotion.quick,
                  curve: KitMotion.enter,
                  child: Padding(
                    padding: EdgeInsetsDirectional.only(end: tokens.space1),
                    child: Icon(
                      AppIconography.check,
                      size: iconSize,
                      color: !enabled
                          ? roles.text3
                          : quiet
                          ? roles.accent
                          : chosenInk,
                    ),
                  ),
                ),
              if (segment.icon case final icon?) ...[
                Icon(icon, size: iconSize, color: color),
                SizedBox(width: tokens.space1),
              ],
              Flexible(
                // One line that always fits: when it would not, the part stacks
                // instead (KIT-24, [_stacks]); nothing truncates.
                child: Text(
                  segment.label,
                  maxLines: 1,
                  softWrap: false,
                  style: KitText.styleOf(
                    context,
                    KitTextRole.label,
                    tone: tone,
                  ),
                ),
              ),
              if (count != null) ...[
                SizedBox(width: tokens.space1),
                KitSwap.count(
                  count: count,
                  child: Text(
                    _formatCount(context, count),
                    style:
                        KitText.styleOf(
                          context,
                          KitTextRole.caption,
                          tone: tone,
                        ).copyWith(
                          fontFeatures: const [FontFeature.tabularFigures()],
                        ),
                  ),
                ),
              ],
            ],
          );

    return Semantics(
      key: segment.key,
      container: true,
      excludeSemantics: true,
      button: true,
      selected: isSelected,
      inMutuallyExclusiveGroup: true,
      enabled: enabled,
      label: label,
      onTap: enabled ? () => _choose(index) : null,
      child: FocusableActionDetector(
        focusNode: _nodes[index],
        enabled: enabled,
        includeFocusSemantics: false,
        mouseCursor: enabled ? SystemMouseCursors.click : MouseCursor.defer,
        actions: {
          ActivateIntent: CallbackAction<ActivateIntent>(
            onInvoke: (_) {
              _choose(index);
              return null;
            },
          ),
        },
        onFocusChange: (focused) => _segmentFocusChanged(index, focused),
        onShowFocusHighlight: (show) => setState(
          () => _ring = show ? index : (_ring == index ? null : _ring),
        ),
        // Hover comes from the pointer itself, not the focus highlight mode:
        // a mouse on a phone or tablet leaves that mode on touch.
        child: MouseRegion(
          onEnter: enabled ? (_) => _setHovered(index, true) : null,
          onExit: (_) => _setHovered(index, false),
          child: _press(index).listen(
            context: context,
            enabled: enabled,
            child: GestureDetector(
              behavior: HitTestBehavior.opaque,
              excludeFromSemantics: true,
              onTapCancel: enabled ? _press(index).cancel : null,
              onTap: enabled
                  ? () {
                      _press(index).confirm();
                      _choose(index);
                    }
                  : null,
              child: KitInkLayer(
                key: _press(index).inkKey,
                shape: RoundedRectangleBorder(borderRadius: radius),
                child: Container(
                  constraints: BoxConstraints(minHeight: tokens.minTarget),
                  alignment: Alignment.center,
                  padding: EdgeInsetsDirectional.symmetric(
                    horizontal: tokens.space2,
                  ),
                  // Hover and pressed: the style's state layer.
                  decoration: step == null
                      ? own
                      : KitSurfaceLook.decorate(
                          context,
                          solid: own,
                          role: KitSurfaceRole.stateLayer,
                          level: step,
                          pressed: pressedFill,
                        ),
                  // The keyboard ring is painted over the segment, inside its
                  // bounds, so it never moves the content (LOOK-21: 2 physical px
                  // in accent).
                  foregroundDecoration: _ring == index
                      ? BoxDecoration(
                          borderRadius: radius,
                          border: Border.all(
                            color: roles.accent,
                            width: KitTokens.focusRingWidth(context),
                          ),
                        )
                      : null,
                  child: content,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// The group's one Tab stop (§8.2, G14), decided when Tab is pressed
/// rather than by switching `skipTraversal` on the segments' nodes: a node
/// property changed by a new selection is announced after the frame, and
/// inside the part's LayoutBuilder that announcement would leave a frame
/// callback behind, so one pump() would no longer settle (G8).
class _OneStopPolicy extends WidgetOrderTraversalPolicy {
  _OneStopPolicy({required this.isSegment, required this.isStop});

  final bool Function(FocusNode node) isSegment;
  final bool Function(FocusNode node) isStop;

  @override
  Iterable<FocusNode> sortDescendants(
    Iterable<FocusNode> descendants,
    FocusNode currentNode,
  ) => super
      .sortDescendants(descendants, currentNode)
      .where((node) => node == currentNode || !isSegment(node) || isStop(node));
}
