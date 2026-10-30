// The live look of a step's node on a timeline (owner picks 2026-10-11: "From
// above, both sides", and a look for every state). Part of
// kit_step_timeline.dart so it reads the rail's measures and the geometry
// book. docs/ux-system/kit-api/KitStepTimeline.md "Node states".
part of 'kit_step_timeline.dart';

/// Whether a light may travel here; false draws the still looks.
bool _lightMoves(BuildContext context) =>
    KitMotion.loops &&
    !KitMotion.reduced(context) &&
    KitEffects.of(context).motion != KitMotionLevel.off;

/// The node's tile radius: the one number the tile and every outline share.
double _tileRadius(double size) => size * .3;

class _StepLight extends StatefulWidget {
  const _StepLight({
    required this.icon,
    required this.size,
    required this.state,
    required this.mark,
    required this.registry,
    required this.index,
    required this.last,
    required this.stalled,
    this.lone = false,
  });

  /// Off any rail (a row outside a timeline): the light goes round the node
  /// only, with no rail above or below it.
  final bool lone;

  final IconData icon;
  final double size;
  final KitStepNodeState state;
  final Widget? mark;
  final _RailRegistry registry;
  final int index;
  final bool last;
  final bool stalled;

  @override
  State<_StepLight> createState() => _StepLightState();
}

class _StepLightState extends State<_StepLight> with TickerProviderStateMixin {
  final List<AnimationController> _made = [];
  AnimationController _make(Duration duration) {
    final controller = AnimationController(vsync: this, duration: duration);
    _made.add(controller);
    return controller;
  }

  // Made on first use: most nodes never move.
  late final AnimationController _lap = _make(KitMotion.stepLap);
  late final AnimationController _breathe = _make(KitMotion.stepBreathe);
  late final AnimationController _finish = _make(
    KitMotion.stepTick + KitMotion.stepSpring,
  );
  late final AnimationController _flash = _make(KitMotion.stepFlash);
  late final AnimationController _fade = _make(KitMotion.stepFade);
  final GlobalKey _tile = GlobalKey();

  bool _moving = false;

  KitStepNodeState? _ended;

  static bool _isLive(KitStepNodeState s) =>
      s == KitStepNodeState.running || s == KitStepNodeState.background;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _sync();
  }

  @override
  void didUpdateWidget(covariant _StepLight old) {
    super.didUpdateWidget(old);
    final was = old.state;
    final now = widget.state;
    if (was != now) {
      final reduced = KitMotion.reduced(context);
      final active = _isLive(was) || was == KitStepNodeState.waiting;
      _ended = null;
      if (active && !reduced) {
        switch (now) {
          case KitStepNodeState.done when _isLive(was):
            _ended = now;
            _finish.forward(from: 0);
          case KitStepNodeState.failed:
            _ended = now;
            _flash.forward(from: 0);
          case KitStepNodeState.stopped:
            _ended = now;
            _fade.forward(from: 0);
          default:
            break;
        }
      }
    }
    _sync();
  }

  void _sync() {
    final state = widget.state;
    final moves = _lightMoves(context);
    _moving = _isLive(state) && moves;
    if (_moving && !widget.stalled) {
      _lap.duration = state == KitStepNodeState.background
          ? KitMotion.stepLapCalm
          : (KitEffects.of(context).motion == KitMotionLevel.calm
                ? KitMotion.stepLap * 2
                : KitMotion.stepLap);
      if (!_lap.isAnimating) _lap.repeat();
    } else if (_made.contains(_lap)) {
      _lap.stop(); // held where it is when the connection is down
    }
    if (state == KitStepNodeState.waiting && moves) {
      if (!_breathe.isAnimating) _breathe.repeat(reverse: true);
    } else if (_made.contains(_breathe)) {
      _breathe.stop();
    }
  }

  @override
  void dispose() {
    for (final controller in _made) {
      controller.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // Signal: the node is a signal on the track (kit_step_signal.dart).
    if (KitTrack.on(context)) {
      return _signalNode(context, widget.state, widget.size, widget.mark);
    }
    final roles = KitTokens.of(context).roles;
    final state = widget.state;
    final size = widget.size;
    final radius = _tileRadius(size);
    final live = _isLive(state);
    final glyphSize = size * .6;

    Color glyphColor() => live
        ? roles.accent
        : (state == KitStepNodeState.queued ? roles.text3 : roles.text2);

    // The node's tile is made for the person's Style like every other
    // tile (KitSurface.tile's control surface); the light keeps its shape.
    // A tiny tile on the rail throws no shadow of its own (owner,
    // 2026-10-22: one under every step read as busy): it is set as inside
    // a cast, so a style that keeps one shadow per stack (Brutal, hook H9)
    // keeps its edge and drops the shadow. Other styles are unchanged.
    Widget tile(Widget glyph) => SizedBox.square(
      key: _tile,
      dimension: size,
      child: KitSurfaceLook.castScope(
        context,
        // What the tiles sit in for hook H9: a surface that casts. Never
        // painted.
        decoration: KitSurfaceDecoration(
          fill: Colors.transparent,
          hardShadow: roles.text1,
          hardOffset: 1,
        ),
        child: kitSurfaceMaterial(
          context,
          level: KitSurfaceLevel.surface3,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(radius),
          ),
          fill: roles.surface3,
          role: KitSurfaceRole.control,
          clip: false,
          child: Center(child: glyph),
        ),
      ),
    );

    // The light on a layer of its own: its lap repaints the light and
    // nothing else, never the steps' names around it (scroll-perf,
    // 2026-10-23).
    Widget outline(_Look look) => Positioned.fill(
      child: IgnorePointer(
        child: RepaintBoundary(
          child: CustomPaint(
            key: const ValueKey('kit-step-light'),
            painter: _LightPainter(
              look: look,
              tile: _tile,
              registry: widget.registry,
              index: widget.index,
              last: widget.last,
              lone: widget.lone,
              size: size,
              radius: radius,
              dpr: MediaQuery.devicePixelRatioOf(context),
              railColor: roles.text3,
              repaint: look.lap,
            ),
          ),
        ),
      ),
    );

    Widget stack(Widget glyph, [_Look? look]) => SizedBox.square(
      dimension: size,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          Positioned.fill(child: tile(glyph)),
          if (look != null) outline(look),
        ],
      ),
    );

    // Finished, failed, stopped: the short flash first, then the resting look.
    if (_ended == KitStepNodeState.done && state == KitStepNodeState.done) {
      return AnimatedBuilder(
        animation: _finish,
        builder: (context, _) {
          final spent = _finish.value * _finish.duration!.inMilliseconds;
          final tickMs = KitMotion.stepTick.inMilliseconds;
          if (_finish.isAnimating && spent < tickMs) {
            return stack(
              Icon(
                AppIconography.check,
                key: const ValueKey('kit-step-tick'),
                size: glyphSize,
                color: roles.success,
              ),
            );
          }
          final t = _finish.isAnimating
              ? (spent - tickMs) / KitMotion.stepSpring.inMilliseconds
              : 1.0;
          final scale = .4 + .6 * KitMotion.land.transform(t.clamp(0.0, 1.0));
          // The glyph grows back by its own size, not by a transform.
          return stack(
            Icon(widget.icon, size: glyphSize * scale, color: roles.text2),
          );
        },
      );
    }
    if (state == KitStepNodeState.failed) {
      if (_ended == KitStepNodeState.failed) {
        return AnimatedBuilder(
          animation: _flash,
          builder: (context, _) => _flash.isCompleted
              ? (widget.mark ??
                    tile(
                      Icon(widget.icon, size: glyphSize, color: roles.text2),
                    ))
              : stack(
                  Icon(widget.icon, size: glyphSize, color: roles.text2),
                  _Look.still(roles.text2, 1 - _flash.value),
                ),
        );
      }
      if (widget.mark != null) return widget.mark!;
    }
    if (state == KitStepNodeState.stopped &&
        _ended == KitStepNodeState.stopped) {
      return AnimatedBuilder(
        animation: _fade,
        builder: (context, _) {
          final glyph = Icon(widget.icon, size: glyphSize, color: roles.text2);
          return _fade.isCompleted
              ? tile(glyph)
              : stack(glyph, _Look.still(roles.accent, 1 - _fade.value));
        },
      );
    }

    final icon = Icon(widget.icon, size: glyphSize, color: glyphColor());
    switch (state) {
      case KitStepNodeState.running:
      case KitStepNodeState.background:
        final calm = state == KitStepNodeState.background;
        final held = widget.stalled;
        final intensity = (calm ? .6 : 1.0) * (held ? .5 : 1.0);
        return stack(
          icon,
          _moving || (held && _lightMoves(context))
              ? _Look.lap(roles.accent, _lap, intensity)
              : _Look.still(roles.accent, intensity),
        );
      case KitStepNodeState.waiting:
        if (_lightMoves(context)) {
          return AnimatedBuilder(
            animation: _breathe,
            builder: (context, _) => stack(
              icon,
              _Look.still(roles.attention, .5 + .5 * _breathe.value),
            ),
          );
        }
        return stack(icon, _Look.still(roles.attention, 1));
      case KitStepNodeState.queued:
        return stack(icon, _Look.still(roles.text3, .45, dim: true));
      case KitStepNodeState.none:
      case KitStepNodeState.done:
      case KitStepNodeState.failed:
      case KitStepNodeState.stopped:
        return tile(icon);
    }
  }
}

/// One half of the node's outline, from the top centre round to the bottom
/// centre, the stroke inside the tile's own edge: [left] or its mirror. Both
/// halves have the same length and meet exactly at the bottom centre.
Path _halfPath(
  double size,
  double radius,
  double stroke, {
  required bool left,
}) {
  final inset = stroke / 2;
  final r = math.max(0.0, radius - inset);
  final l = inset;
  final t = inset;
  final rr = size - inset;
  final b = size - inset;
  final c = size / 2;
  final d = 2 * r;
  final path = Path()..moveTo(c, t);
  if (left) {
    path
      ..lineTo(l + r, t)
      ..arcTo(Rect.fromLTWH(l, t, d, d), -math.pi / 2, -math.pi / 2, false)
      ..lineTo(l, b - r)
      ..arcTo(Rect.fromLTWH(l, b - d, d, d), math.pi, -math.pi / 2, false)
      ..lineTo(c, b);
  } else {
    path
      ..lineTo(rr - r, t)
      ..arcTo(Rect.fromLTWH(rr - d, t, d, d), -math.pi / 2, math.pi / 2, false)
      ..lineTo(rr, b - r)
      ..arcTo(Rect.fromLTWH(rr - d, b - d, d, d), 0, math.pi / 2, false)
      ..lineTo(c, b);
  }
  return path;
}

/// [_halfPath] for tests.
@visibleForTesting
Path debugStepOutlineHalf(double size, double stroke, {required bool left}) =>
    _halfPath(size, _tileRadius(size), stroke, left: left);

/// What the painter draws: a still outline, or the light at the lap's phase.
@immutable
class _Look {
  const _Look.still(this.color, this.intensity, {this.dim = false})
    : lap = null;
  const _Look.lap(this.color, AnimationController this.lap, this.intensity)
    : dim = false;

  final Color color;
  final double intensity;
  final bool dim;
  final AnimationController? lap;
}

double _seg(double p, double a, double b) =>
    ((p - a) / (b - a)).clamp(0.0, 1.0);

class _LightPainter extends CustomPainter {
  _LightPainter({
    required this.look,
    required this.tile,
    required this.registry,
    required this.index,
    required this.last,
    required this.lone,
    required this.size,
    required this.radius,
    required this.dpr,
    required this.railColor,
    super.repaint,
  });

  final _Look look;
  final GlobalKey tile;
  final _RailRegistry registry;
  final int index;
  final bool last;
  final bool lone;
  final double size;
  final double radius;
  final double dpr;
  final Color railColor;

  static const double _fillStroke = 1.8;
  static const double _headStroke = 2.6;
  static const double _headLen = 8;
  static const double _lightLen = 14;
  static const double _tail = 14;

  /// The light on the rail is 2 dp wide, centred on the 1 dp rail.
  static const double _lightW = 2;

  Paint _paint(Color color, double width, {bool round = false}) => Paint()
    ..style = PaintingStyle.stroke
    ..strokeWidth = width
    ..strokeCap = round ? StrokeCap.round : StrokeCap.butt
    ..strokeJoin = StrokeJoin.round
    ..isAntiAlias = true
    ..color = color;

  void _outline(Canvas canvas, Color color, double fraction, {double? width}) {
    final stroke = width ?? _fillStroke;
    for (final left in [true, false]) {
      final metric = _halfPath(
        size,
        radius,
        stroke,
        left: left,
      ).computeMetrics().first;
      final end = metric.length * fraction;
      if (end <= 0) continue;
      canvas.drawPath(metric.extractPath(0, end), _paint(color, stroke));
    }
  }

  /// The y of [dy] inside [key]'s box, in the tile's own coordinates.
  double? _yIn(RenderBox tileBox, GlobalKey key, double dy) {
    final box = registry.box(key);
    if (box == null) return null;
    return tileBox.globalToLocal(box.localToGlobal(Offset(0, dy))).dy;
  }

  @override
  void paint(Canvas canvas, Size canvasSize) {
    final lap = look.lap;
    if (lap == null) {
      if (look.dim) {
        canvas.drawRRect(
          RRect.fromRectAndRadius(
            Rect.fromLTWH(0, 0, size, size).deflate(_fillStroke / 2),
            Radius.circular(radius - _fillStroke / 2),
          ),
          _paint(look.color.withValues(alpha: look.intensity), _fillStroke),
        );
        return;
      }
      _outline(canvas, look.color.withValues(alpha: look.intensity), 1);
      return;
    }
    final tileObject = tile.currentContext?.findRenderObject();
    if (tileObject is! RenderBox || !tileObject.attached) return;

    // The real distance to the neighbours, read from the render boxes now.
    double above = 0;
    final slot = registry.keyFor(index);
    double? from;
    if (index == 0) {
      from = _yIn(tileObject, registry.headerKey, registry.headerStart);
    } else {
      final prev = registry.nodes[index - 1];
      from = _yIn(tileObject, registry.keyFor(index - 1), prev?.bottom ?? 0);
      if (prev == null) from = _yIn(tileObject, slot, 0);
    }
    if (from != null && !lone) above = math.max(0, -from);
    double below = lone ? 0 : _tail;
    if (!lone && !last && index + 1 < registry.count) {
      final next = registry.nodes[index + 1];
      final to = _yIn(tileObject, registry.keyFor(index + 1), next?.top ?? 0);
      if (to != null) below = math.max(0, to - size);
    }

    final p = lap.value;
    final k = look.intensity;
    final color = look.color;
    final railW = dpr > 0 ? 2 / dpr : 1.0;
    final railX = (size / 2 - railW / 2) * (dpr > 0 ? dpr : 1);
    final x = (dpr > 0 ? railX.roundToDouble() / dpr : railX) + railW / 2;

    final bounds = Rect.fromLTRB(-8, -above - 4, size + 8, size + below + 4);
    canvas.saveLayer(bounds, Paint());
    // The last step has no rail of its own below its node.
    if (last && below > 0) {
      canvas.drawLine(
        Offset(x, size),
        Offset(x, size + below),
        _paint(railColor.withValues(alpha: .5), railW),
      );
    }

    // 1) From above, down the rail to the node's top edge.
    if (above > 0 && p < .3) {
      final head =
          KitMotion.stepEase.transform(_seg(p, 0, .28)) * (above + _lightLen);
      final a = (head - _lightLen).clamp(0.0, above);
      final b = head.clamp(0.0, above);
      if (b > a) {
        canvas.drawLine(
          Offset(x, -above + a),
          Offset(x, -above + b),
          _paint(color.withValues(alpha: k), _lightW, round: true),
        );
      }
    }

    // 2) Split at the top centre, fill both sides, meet at the bottom centre.
    final f = KitMotion.stepEase.transform(_seg(p, .26, .66));
    final dim = .75 * (1 - _seg(p, .7, 1));
    if (f > 0) {
      _outline(canvas, color.withValues(alpha: dim * k), f);
      if (p > .26 && p < .68) {
        for (final left in [true, false]) {
          final metric = _halfPath(
            size,
            radius,
            _headStroke,
            left: left,
          ).computeMetrics().first;
          final end = metric.length * f;
          final start = math.max(0.0, end - _headLen);
          if (end > start) {
            canvas.drawPath(
              metric.extractPath(start, end),
              _paint(color.withValues(alpha: k), _headStroke, round: true),
            );
          }
        }
      }
    }

    // 3) One light on down the rail to the next node, fading.
    if (below > 0 && p > .64 && p < .97) {
      final head =
          KitMotion.stepOut.transform(_seg(p, .64, .95)) * (below + _lightLen);
      final a = (head - _lightLen).clamp(0.0, below);
      final b = head.clamp(0.0, below);
      if (b > a) {
        canvas.drawLine(
          Offset(x, size + a),
          Offset(x, size + b),
          _paint(color.withValues(alpha: k), _lightW, round: true),
        );
      }
    }

    // The rail parts fade: in above, out below; the node itself is whole.
    if (above > 0) {
      canvas.drawRect(
        Rect.fromLTRB(-8, -above, size + 8, 0),
        Paint()
          ..blendMode = BlendMode.dstIn
          ..shader = LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [color.withValues(alpha: 0), color, color],
            stops: const [0, .4, 1],
          ).createShader(Rect.fromLTRB(0, -above, size, 0)),
      );
    }
    if (below > 0) {
      canvas.drawRect(
        Rect.fromLTRB(-8, size, size + 8, size + below + 4),
        Paint()
          ..blendMode = BlendMode.dstIn
          ..shader = LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [color, color, color.withValues(alpha: 0)],
            stops: const [0, .5, 1],
          ).createShader(Rect.fromLTRB(0, size, size, size + below)),
      );
    }
    canvas.restore();
  }

  @override
  bool shouldRepaint(_LightPainter old) =>
      old.look.color != look.color ||
      old.look.intensity != look.intensity ||
      old.look.dim != look.dim ||
      old.look.lap != look.lap ||
      old.index != index ||
      old.last != last ||
      old.lone != lone ||
      old.size != size ||
      old.dpr != dpr;
}

/// The running step's name with a soft light sweeping over it (2.2 s a pass);
/// still when the light does not move.
class _StepSweep extends StatefulWidget {
  const _StepSweep({required this.active, required this.child});

  final bool active;
  final Widget child;

  @override
  State<_StepSweep> createState() => _StepSweepState();
}

class _StepSweepState extends State<_StepSweep>
    with SingleTickerProviderStateMixin {
  late final AnimationController _sweep = AnimationController(
    vsync: this,
    duration: KitMotion.stepSweep,
  );
  bool _moving = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _sync();
  }

  @override
  void didUpdateWidget(covariant _StepSweep old) {
    super.didUpdateWidget(old);
    _sync();
  }

  void _sync() {
    _moving = widget.active && _lightMoves(context);
    if (!_moving) {
      _sweep.stop();
    } else if (!_sweep.isAnimating) {
      _sweep.repeat();
    }
  }

  @override
  void dispose() {
    _sweep.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!_moving) return widget.child;
    final roles = KitTokens.of(context).roles;
    final rtl = Directionality.of(context) == TextDirection.rtl;
    // Paint only: the light moves on [_sweep] without a rebuild, and the
    // name keeps its picture under it. Rebuilt on every frame, as it was,
    // the sweep relaid out the step row's LayoutBuilder and recorded the
    // whole page around it again each frame (scroll-perf, 2026-10-23).
    return RepaintBoundary(
      child: _SweepMask(
        key: const ValueKey('kit-step-sweep'),
        sweep: _sweep,
        shaderCallback: (bounds) {
          final t = _sweep.value;
          final centre = -0.3 + 1.6 * (rtl ? 1 - t : t);
          return LinearGradient(
            colors: [roles.text1, roles.accent, roles.text1],
            stops: [
              (centre - 0.18).clamp(0.0, 1.0),
              centre.clamp(0.0, 1.0),
              (centre + 0.18).clamp(0.0, 1.0),
            ],
          ).createShader(bounds);
        },
        child: RepaintBoundary(child: widget.child),
      ),
    );
  }
}

/// A [ShaderMask] (source-in) that paints again on each tick of [sweep],
/// with no rebuild and no layout.
///
/// It stays a [ShaderMask] so the parts that look for a recolouring mask
/// above their words still find it: `KitTextHalo` keeps the halo and the
/// words' plate out from under it (a plate under a source-in mask is
/// painted in the sweep's colours too, a pale block with the words lost in
/// it; seen on the phone, 2026-10-24).
class _SweepMask extends ShaderMask {
  const _SweepMask({
    super.key,
    required this.sweep,
    required super.shaderCallback,
    required super.child,
  }) : super(blendMode: BlendMode.srcIn);

  final Animation<double> sweep;

  @override
  _RenderSweepMask createRenderObject(BuildContext context) =>
      _RenderSweepMask(sweep: sweep, shaderCallback: shaderCallback);

  @override
  void updateRenderObject(
    BuildContext context,
    covariant _RenderSweepMask renderObject,
  ) {
    renderObject
      ..sweep = sweep
      ..shaderCallback = shaderCallback;
  }
}

class _RenderSweepMask extends RenderShaderMask {
  _RenderSweepMask({
    required Animation<double> sweep,
    required super.shaderCallback,
  }) : _sweep = sweep,
       super(blendMode: BlendMode.srcIn);

  Animation<double> _sweep;
  set sweep(Animation<double> value) {
    if (identical(value, _sweep)) return;
    if (attached) _sweep.removeListener(markNeedsPaint);
    _sweep = value;
    if (attached) _sweep.addListener(markNeedsPaint);
    markNeedsPaint();
  }

  @override
  void attach(PipelineOwner owner) {
    super.attach(owner);
    _sweep.addListener(markNeedsPaint);
  }

  @override
  void detach() {
    _sweep.removeListener(markNeedsPaint);
    super.detach();
  }
}
