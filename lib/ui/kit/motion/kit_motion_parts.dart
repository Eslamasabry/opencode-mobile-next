// KitMotionParts (docs/ux-system/kit-api/KitMotionParts.md, API freeze wave
// 0): the small motion primitives a screen may use, all on KitMotion timings
// and curves. Each one shows its final state on the first build, is instant
// under KitMotion.reduced, moves only paint and transforms (MOT-5), and
// never blurs, scales or fade-scales (VL §7, MOT-2).
//
// They replace the framework's Animated* widgets, transitions, Opacity,
// Transform and TweenAnimationBuilder OUTSIDE the kit; this file itself may
// use those primitives to build the parts, the way kit_reveal.dart already
// does. Arrivals (fade plus rise) are the existing KitEntrance
// (kit_reveal.dart) and are not redefined here.
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';

import '../../app_iconography.dart';
import '../kit_motion.dart';
import '../kit_tokens.dart';

/// The two paces a part may move at (MOT-1).
enum KitPace {
  /// [KitMotion.quick] (150 ms): a control answering a touch.
  quick,

  /// [KitMotion.standard] (250 ms): a part appearing or changing.
  standard,
}

Duration _paceLength(KitPace pace) => switch (pace) {
  KitPace.quick => KitMotion.quick,
  KitPace.standard => KitMotion.standard,
};

/// Zero under reduced motion (MOT-7), so every implicit animation below
/// jumps to its target within the frame instead of easing to it.
Duration _durationOf(BuildContext context, KitPace pace) =>
    KitMotion.reduced(context) ? Duration.zero : _paceLength(pace);

/// Cross-fades between children: [AnimatedSwitcher]'s job, without scale or
/// size animation. The [child]'s key says when the content is new (falling
/// back to its widget type, so two different widget classes still cross-fade
/// without an explicit key). The old child ignores touches and is excluded
/// from semantics while it leaves.
///
/// Built on [AnimatedSwitcher], so each child keeps its element and state
/// from the moment it arrives until it has faded out, and a change during a
/// swap fades every leaving child on from its current opacity.
///
/// States: none — motion only; its child carries any state.
class KitSwap extends StatelessWidget {
  const KitSwap({
    super.key,
    required this.child,
    this.pace = KitPace.quick,
    this.alignment = AlignmentDirectional.center,
  }) : count = null;

  /// A count that changes in place (a badge's number, a tab's or a
  /// segment's count): the new number rolls in from below as the old one
  /// rolls out above, both fading, on [KitMotion.quick]; a number that goes
  /// down rolls the other way. Under Animations: Calm it only cross-fades,
  /// and under Off or the system's remove animations it changes at once
  /// ([KitMotion.travels], [KitMotion.reduced]). [child] draws [count]; it
  /// needs no key of its own. The roll is clipped to the number's own box
  /// while it runs, and the first build shows [count] at once.
  const KitSwap.count({
    super.key,
    required int this.count,
    required this.child,
    this.alignment = AlignmentDirectional.center,
  }) : pace = KitPace.quick;

  final Widget child;
  final KitPace pace;
  final AlignmentDirectional alignment;

  /// The number [child] draws, for [KitSwap.count]; null for a cross-fade.
  final int? count;

  // A static tear-off is one identical function on every build, so
  // AnimatedSwitcher never rebuilds the leaving children's transitions.
  static Widget _layer(Widget child, Animation<double> animation) =>
      _KitSwapLayer(animation: animation, child: child);

  /// Whether [element] is the child a swap is fading out: it takes no taps
  /// and is hidden from assistive technology, so page checks that count
  /// what is on screen (KitScreen's one primary) leave it out.
  static bool isLeaving(Element element) {
    final widget = element.widget;
    return widget is _KitSwapLayer &&
        _KitSwapLayerState._isLeaving(widget.animation.status);
  }

  @override
  Widget build(BuildContext context) {
    final count = this.count;
    if (count != null) {
      return _KitCountRoll(count: count, alignment: alignment, child: child);
    }
    return _crossFade(context);
  }

  Widget _crossFade(BuildContext context) => AnimatedSwitcher(
    duration: _durationOf(context, pace),
    switchInCurve: KitMotion.enter,
    // The switcher runs a leaving child's animation in reverse (1 to 0);
    // KitMotion.exit in that slot fades it out at once and settles.
    switchOutCurve: KitMotion.exit,
    transitionBuilder: _layer,
    layoutBuilder: (current, previous) =>
        Stack(alignment: alignment, children: [...previous, ?current]),
    child: child,
  );
}

/// [KitSwap.count]'s roll. It remembers which way the count went, so a
/// rising number comes up from below and a falling one comes down from
/// above.
class _KitCountRoll extends StatefulWidget {
  const _KitCountRoll({
    required this.count,
    required this.alignment,
    required this.child,
  });

  final int count;
  final AlignmentDirectional alignment;
  final Widget child;

  @override
  State<_KitCountRoll> createState() => _KitCountRollState();
}

class _KitCountRollState extends State<_KitCountRoll> {
  /// How far a number travels, as a share of its own height.
  static const double _travel = .6;

  bool _rising = true;

  @override
  void didUpdateWidget(covariant _KitCountRoll old) {
    super.didUpdateWidget(old);
    if (widget.count != old.count) _rising = widget.count > old.count;
  }

  @override
  Widget build(BuildContext context) {
    final travels = KitMotion.travels(context);
    final current = ValueKey<int>(widget.count);
    // The arriving number comes from the side the count moved towards; the
    // leaving one goes out the other side. Calm: no travel, a cross-fade.
    final double from = travels ? (_rising ? _travel : -_travel) : 0;
    return AnimatedSwitcher(
      duration: _durationOf(context, KitPace.quick),
      switchInCurve: KitMotion.enter,
      switchOutCurve: KitMotion.exit,
      transitionBuilder: (child, animation) => _KitSwapLayer(
        animation: animation,
        child: SlideTransition(
          position: animation.drive(
            Tween<Offset>(
              begin: Offset(0, child.key == current ? from : -from),
              end: Offset.zero,
            ),
          ),
          child: child,
        ),
      ),
      layoutBuilder: (shown, leaving) => ClipRect(
        // Clipped only while a number leaves, so a settled count never
        // loses a glyph's overhang.
        clipBehavior: leaving.isEmpty ? Clip.none : Clip.hardEdge,
        child: Stack(
          alignment: widget.alignment,
          children: [...leaving, ?shown],
        ),
      ),
      child: KeyedSubtree(key: current, child: widget.child),
    );
  }
}

/// One child of [KitSwap]. Its structure never changes when the child starts
/// to leave: only the [IgnorePointer] and [ExcludeSemantics] flags turn on,
/// so the child's element and state survive the swap.
class _KitSwapLayer extends StatefulWidget {
  const _KitSwapLayer({required this.animation, required this.child});

  final Animation<double> animation;
  final Widget child;

  @override
  State<_KitSwapLayer> createState() => _KitSwapLayerState();
}

class _KitSwapLayerState extends State<_KitSwapLayer> {
  late bool _leaving = _isLeaving(widget.animation.status);

  static bool _isLeaving(AnimationStatus status) =>
      status == AnimationStatus.reverse || status == AnimationStatus.dismissed;

  @override
  void initState() {
    super.initState();
    widget.animation.addStatusListener(_onStatus);
  }

  @override
  void didUpdateWidget(covariant _KitSwapLayer old) {
    super.didUpdateWidget(old);
    if (widget.animation != old.animation) {
      old.animation.removeStatusListener(_onStatus);
      widget.animation.addStatusListener(_onStatus);
      _leaving = _isLeaving(widget.animation.status);
    }
  }

  void _onStatus(AnimationStatus status) {
    final leaving = _isLeaving(status);
    if (leaving != _leaving && mounted) setState(() => _leaving = leaving);
  }

  @override
  void dispose() {
    widget.animation.removeStatusListener(_onStatus);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => IgnorePointer(
    ignoring: _leaving,
    child: ExcludeSemantics(
      excluding: _leaving,
      child: FadeTransition(opacity: widget.animation, child: widget.child),
    ),
  );
}

/// Rotates its child. The rotation is paint only and does not change layout.
///
/// States: none — motion only; its child carries any state.
class KitSpin extends StatelessWidget {
  /// Animated to [turns] (0.5 = half a turn) on [pace].
  const KitSpin({
    super.key,
    required double turns,
    required Widget child,
    this.pace = KitPace.quick,
  }) : _kind = _KitSpinKind.turns,
       _turns = turns,
       _child = child,
       _quarterTurns = null,
       _expanded = null;

  /// A fixed rotation that also rotates the layout ([RotatedBox]'s job), for
  /// example a glyph drawn sideways. Not animated.
  const KitSpin.fixed({
    super.key,
    required int quarterTurns,
    required Widget child,
  }) : _kind = _KitSpinKind.fixed,
       _quarterTurns = quarterTurns,
       _child = child,
       _turns = null,
       _expanded = null,
       pace = KitPace.quick;

  /// The one disclosure chevron of the kit (README.md decision D10):
  /// [AppIconography.chevronDown] at 20 dp in `text2`, turning on
  /// [KitMotion.standard] with [KitMotion.emphasized], pointing down when
  /// closed and up when [expanded]. It uses the vertical glyph so it never
  /// depends on the reading direction.
  const KitSpin.chevron({super.key, required bool expanded})
    : _kind = _KitSpinKind.chevron,
      _expanded = expanded,
      _child = null,
      _turns = null,
      _quarterTurns = null,
      pace = KitPace.standard;

  final _KitSpinKind _kind;
  final double? _turns;
  final int? _quarterTurns;
  final bool? _expanded;
  final Widget? _child;
  final KitPace pace;

  @override
  Widget build(BuildContext context) {
    switch (_kind) {
      case _KitSpinKind.fixed:
        return RotatedBox(quarterTurns: _quarterTurns!, child: _child!);
      case _KitSpinKind.chevron:
        final tokens = KitTokens.of(context);
        return AnimatedRotation(
          turns: _expanded! ? .5 : 0,
          duration: _durationOf(context, KitPace.standard),
          curve: KitMotion.emphasized,
          child: Icon(
            AppIconography.chevronDown,
            size: tokens.smallIconSize,
            color: tokens.roles.text2,
          ),
        );
      case _KitSpinKind.turns:
        return AnimatedRotation(
          turns: _turns!,
          duration: _durationOf(context, pace),
          curve: KitMotion.enter,
          child: _child!,
        );
    }
  }
}

enum _KitSpinKind { turns, fixed, chevron }

/// A box whose paint changes between surface steps: the fill level and the
/// hairline edge animate on [pace]. A change of size snaps; layout animation
/// is [KitReveal]'s alone (MOT-5). [level] null is no fill.
///
/// States: none — motion only; its child carries any state.
class KitAnimatedBox extends StatelessWidget {
  const KitAnimatedBox({
    super.key,
    required this.child,
    this.level,
    this.shape = KitShape.panel,
    this.outlined = false,
    this.pace = KitPace.quick,
  });

  final Widget child;
  final KitSurfaceLevel? level;
  final KitShape shape;

  /// Exactly one physical pixel (LOOK-21).
  final bool outlined;
  final KitPace pace;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final base = tokens.shapeOf(shape);
    final fillLevel = level;
    final hairline = tokens.roles.hairline;
    // The fill is a side-less shape and the edge is a FOREGROUND decoration,
    // and Container pads its child only by the background decoration's
    // insets. So neither the fill nor the edge ever takes layout space, and
    // turning [outlined] on or off changes paint only (MOT-5): the edge
    // keeps its one-physical-pixel width and animates its colour from
    // transparent to the hairline role.
    return AnimatedContainer(
      duration: _durationOf(context, pace),
      curve: KitMotion.enter,
      decoration: ShapeDecoration(
        color: fillLevel == null ? null : tokens.fillOf(fillLevel),
        shape: base,
      ),
      foregroundDecoration: ShapeDecoration(
        shape: _withEdge(
          base,
          BorderSide(
            color: outlined ? hairline : hairline.withAlpha(0),
            width: KitTokens.hairlineWidth(context),
          ),
        ),
      ),
      child: child,
    );
  }
}

/// [shape] with [side] as its edge, so an outlined box's edge can fade in
/// and out with the rest of its paint (`ShapeBorder.lerp` interpolates the
/// side's colour like any other property).
ShapeBorder _withEdge(ShapeBorder shape, BorderSide side) => switch (shape) {
  RoundedRectangleBorder(:final borderRadius) => RoundedRectangleBorder(
    borderRadius: borderRadius,
    side: side,
  ),
  StadiumBorder() => StadiumBorder(side: side),
  CircleBorder() => CircleBorder(side: side),
  _ => shape,
};

/// How far [KitDim] dims.
enum KitDimLevel {
  /// [KitTokens.staleAlpha]: last-known content while a refresh is pending.
  stale,

  /// [KitTokens.disabledAlpha]: an image or drawing for an unavailable
  /// choice.
  disabled,
}

/// Dims an image, a drawing or a mark ([Opacity]'s job) for non-text content
/// only. A debug assert fails when [child]'s render subtree contains a
/// paragraph (LOOK-14: text at rest is opaque; dim text with a
/// `KitTextTone` instead).
///
/// States: none — motion only; its child carries any state.
class KitDim extends StatelessWidget {
  const KitDim({
    super.key,
    required this.child,
    this.dimmed = true,
    this.level = KitDimLevel.disabled,
    this.pace = KitPace.quick,
  });

  final Widget child;
  final bool dimmed;
  final KitDimLevel level;
  final KitPace pace;

  double _alpha() => switch (level) {
    KitDimLevel.stale => KitTokens.staleAlpha,
    KitDimLevel.disabled => KitTokens.disabledAlpha,
  };

  @override
  Widget build(BuildContext context) => AnimatedOpacity(
    opacity: dimmed ? _alpha() : 1,
    duration: _durationOf(context, pace),
    curve: KitMotion.enter,
    child: _KitDimGuard(child: child),
  );
}

/// Fails a debug assert when its child's render subtree paints a paragraph
/// (LOOK-14): [KitDim] dims images, drawings and marks, never text.
class _KitDimGuard extends SingleChildRenderObjectWidget {
  const _KitDimGuard({required Widget super.child});

  @override
  RenderObject createRenderObject(BuildContext context) => _RenderKitDimGuard();
}

class _RenderKitDimGuard extends RenderProxyBox {
  @override
  void paint(PaintingContext context, Offset offset) {
    assert(
      _paintsNoParagraph(this),
      'KitDim dims images, drawings and marks, never text (LOOK-14): dim '
      'text with a KitTextTone instead of wrapping it in KitDim.',
    );
    super.paint(context, offset);
  }
}

bool _paintsNoParagraph(RenderObject node) {
  var clean = true;
  void visit(RenderObject child) {
    if (child is RenderParagraph) {
      clean = false;
      return;
    }
    child.visitChildren(visit);
  }

  node.visitChildren(visit);
  return clean;
}

/// Eases a number toward [value] ([TweenAnimationBuilder]'s job). The first
/// build shows [value] at once, and later changes animate on [pace]. [jump]
/// shows the new value at once (a new job, a reset). [builder] should
/// change paint or a transform: a bar's fill, a count's position. It must
/// not change layout.
///
/// States: none — motion only; its child carries any state.
class KitAnimatedValue extends StatefulWidget {
  const KitAnimatedValue({
    super.key,
    required this.value,
    required this.builder,
    this.pace = KitPace.standard,
    this.jump = false,
  });

  final double value;
  final Widget Function(BuildContext context, double value) builder;
  final KitPace pace;
  final bool jump;

  @override
  State<KitAnimatedValue> createState() => _KitAnimatedValueState();
}

class _KitAnimatedValueState extends State<KitAnimatedValue>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: _paceLength(widget.pace),
    value: 1,
  );
  late final Animation<double> _eased = CurvedAnimation(
    parent: _controller,
    curve: KitMotion.enter,
  );
  late Tween<double> _tween = Tween(begin: widget.value, end: widget.value);

  @override
  void didUpdateWidget(covariant KitAnimatedValue old) {
    super.didUpdateWidget(old);
    if (widget.value != old.value) {
      if (widget.jump || KitMotion.reduced(context)) {
        _tween = Tween(begin: widget.value, end: widget.value);
        _controller.value = 1;
      } else {
        _tween = Tween(
          begin: _tween.transform(_eased.value),
          end: widget.value,
        );
        _controller.duration = _paceLength(widget.pace);
        _controller.forward(from: 0);
      }
    } else if (widget.pace != old.pace) {
      _controller.duration = _paceLength(widget.pace);
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: _controller,
    builder: (context, _) =>
        widget.builder(context, _tween.transform(_eased.value)),
  );
}
