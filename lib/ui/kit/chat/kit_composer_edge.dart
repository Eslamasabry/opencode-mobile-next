part of 'kit_composer.dart';

/// A 40 dp circle in a 48 dp target: Send, the mic, Stop.
/// What the edge says now: a running reply or a failed send.
@immutable
class _EdgeData {
  const _EdgeData({this.live, this.failure, this.note});

  final KitTurnLive? live;
  final KitComposerFailure? failure;
  final String? note;

  bool get isEmpty => live == null && failure == null;
}

/// The light that runs on the outline: mutable, painted by [_EdgePainter],
/// stepped by the state's ticker.
class _EdgeLight {
  /// Head position along the outline, 0..1 of one lap.
  double phase = 0;

  /// Laps per second.
  double speed = 0;

  /// 0..1.
  double bright = 0;

  /// 0..1: how far the heartbeat at the gap's edges has taken over.
  double stall = 0;

  /// 0..1: the faint accent tint (only while text streams).
  double tint = 0;

  /// Tail length as a share of the outline.
  double tail = 0.3;

  /// Seconds, for the breathing and the heartbeat.
  double time = 0;

  /// Calm: a still glow instead of a travelling light (0..1).
  double still = 0;
}

/// "The edge is the status": the composer's top border eases down into one
/// wide, shallow dip that cradles the phase words (with a small red stop),
/// and a soft light travels the outline at the pace of the model.
///
/// - Zero height change: the caption straddles the border line.
/// - Full motion: the dip deepens from nothing along one bell; a soft glow
///   runs the outline (slow and breathing while thinking, following
///   [KitTurnLive.pace] while writing, settling to a breathing hue at the
///   dip's edges when the server is quiet, never faster than
///   [KitMotion.edgeLightMaxLapsPerSecond]); at the end it fades into the
///   border as the dip straightens. A failure washes the outline once in
///   the neutral text colour, never red (LOOK-5).
/// - Calm: the caption, a half-depth dip and a slow breathing hue.
/// - Off or reduced motion: the caption and the dip, still.
///
/// The light is decorative and left out of semantics; the caption is the
/// live region. The ticker only runs while there is something to show, and
/// stops with the route (TickerMode) and the app in the background.
class _LivingEdge extends StatefulWidget {
  const _LivingEdge({
    required this.surface,
    required this.radius,
    this.activityGlow,
    this.live,
    this.failure,
    this.note,
  });

  /// Null follows [KitEffects.activityGlow].
  final bool? activityGlow;

  final Widget surface;
  final double radius;
  final KitTurnLive? live;
  final KitComposerFailure? failure;
  final String? note;

  @override
  State<_LivingEdge> createState() => _LivingEdgeState();
}

class _LivingEdgeState extends State<_LivingEdge>
    with TickerProviderStateMixin {
  static const _sealSeconds = 1.2;
  double _sealBright = 0;

  /// When [KitTurnLive.pace] last changed, on the light's own clock: the
  /// pace decays from then, so a screen that stops rebuilding as words stop
  /// still lets the light settle.
  double _paceStamp = 0;

  late final AnimationController _open = AnimationController(vsync: this);
  late final AnimationController _flash = AnimationController(
    vsync: this,
    duration: KitMotion.composerFlash,
  );
  late final Ticker _ticker = createTicker(_step);
  final _light = _EdgeLight();
  final _repaint = ValueNotifier<int>(0);

  late _EdgeData _data = _current;
  late _EdgeData _shown = _current;
  Duration _last = Duration.zero;
  double? _seal;
  bool _closing = false;
  bool _ticked = false;
  double _captionWidth = 0;

  _EdgeData get _current =>
      _EdgeData(live: widget.live, failure: widget.failure, note: widget.note);

  @override
  void initState() {
    super.initState();
    _open.value = _data.isEmpty ? 0 : 1;
  }

  /// Full and Calm animate the light (Calm only breathes a hue); Off and
  /// reduced motion keep it still. Tests keep loops off, like every loop.
  bool get _loops =>
      KitMotion.loops &&
      !KitMotion.reduced(context) &&
      KitEffects.of(context).motion != KitMotionLevel.off;

  /// Only Full lets the light travel.
  bool get _travels => _loops && _level == KitMotionLevel.full;
  KitMotionLevel get _level => KitEffects.of(context).motion;

  void _animateOpen(double to) {
    if (KitMotion.reduced(context)) {
      _open.value = to;
      return;
    }
    _open.animateTo(
      to,
      duration: _level == KitMotionLevel.calm
          ? KitMotion.composerOpenCalm
          : KitMotion.composerOpen,
      curve: KitMotion.composerOpenCurve,
    );
  }

  @override
  void didUpdateWidget(_LivingEdge old) {
    super.didUpdateWidget(old);
    final before = _data;
    _data = _current;
    if (before.live?.pace != _data.live?.pace) _paceStamp = _light.time;
    if (!_data.isEmpty) {
      _shown = _data;
      _seal = null;
      _closing = false;
      _animateOpen(1);
      if (_data.failure != null && before.failure == null && _loops) {
        _flash.forward(from: 0);
      }
      final live = _data.live;
      if (live?.activity == KitTurnActivity.writing && !_ticked) {
        _ticked = true;
        // The first word: a light tick, under the person's own vibration
        // setting.
        KitHaptics.send(context);
      }
      if (_loops) {
        _wake();
      } else {
        _stillGlow(live);
      }
    } else if (!before.isEmpty) {
      _ticked = false;
      if (before.live != null && _loops) {
        // Sealed: one quick lap, then the gap closes.
        _seal = 0;
        _sealBright = _light.bright;
        _wake();
      } else {
        _light.still = 0;
        _repaint.value++;
        _animateOpen(0);
      }
    }
  }

  void _wake() {
    if (!_ticker.isActive) {
      _last = Duration.zero;
      unawaited(_ticker.start());
    }
  }

  void _stillGlow(KitTurnLive? live) {
    _light.still = live == null
        ? 0
        : live.activity == KitTurnActivity.writing
        ? 0.3 + 0.7 * live.pace.clamp(0.0, 1.0)
        : 0.3;
    _light.tint = live?.activity == KitTurnActivity.writing ? 1 : 0;
    _repaint.value++;
  }

  void _step(Duration elapsed) {
    final dt = _last == Duration.zero
        ? 0.016
        : ((elapsed - _last).inMicroseconds / 1e6).clamp(0.0, 0.05);
    _last = elapsed;
    final l = _light;
    l.time += dt;
    // Hue changes cross-fade over the hue-fade time; speed changes ease
    // over the speed-ease time, so nothing jerks.
    final k =
        1 - math.exp(-dt / (KitMotion.edgeLightHueFade.inMilliseconds / 1500));
    final kSpeed =
        1 -
        math.exp(-dt / (KitMotion.edgeLightSpeedEase.inMilliseconds / 1500));
    final seal = _seal;
    if (seal != null) {
      // Done: the glow keeps its pace and fades into the border as the gap
      // closes.
      final st = seal + dt;
      _seal = st;
      final u = (st / _sealSeconds).clamp(0.0, 1.0);
      l.phase = (l.phase + l.speed * dt) % 1;
      l.bright = _sealBright * (1 - KitMotion.sealFade.transform(u));
      l.stall = 0;
      if (st >= _sealSeconds * 0.4 && !_closing) {
        _closing = true;
        _animateOpen(0);
      }
      if (st >= _sealSeconds) {
        _seal = null;
        l.bright = 0;
        l.tint = 0;
        _ticker.stop();
      }
      _repaint.value++;
      return;
    }
    final live = _data.live;
    var speedT = 0.0, brightT = 0.0, tintT = 0.0, stallT = 0.0, tail = 0.32;
    if (live != null) {
      final since = live.since;
      final waited = since == null
          ? Duration.zero
          : clock.now().difference(since);
      final quiet =
          (live.activity == KitTurnActivity.waitingForServer ||
              live.activity == KitTurnActivity.waitingForModel) &&
          waited >= KitTurnLive.slowAfter;
      final max = KitMotion.edgeLightMaxLapsPerSecond;
      final rest = KitMotion.edgeLightThinkingLapsPerSecond;
      switch (live.activity) {
        case KitTurnActivity.writing:
          final p =
              live.pace.clamp(0.0, 1.0) *
              math.exp(-(l.time - _paceStamp) / 1.5);
          speedT = rest * 0.6 + (max - rest * 0.6) * p;
          brightT = 0.6 + 0.4 * p;
          tintT = 1;
          tail = 0.24;
        case KitTurnActivity.working:
          speedT = rest * 1.2;
          brightT = 0.6;
        case KitTurnActivity.waitingForYou:
          brightT = 0.3;
        default:
          if (quiet) {
            stallT = 1;
          } else {
            speedT = rest;
            brightT = 0.7 + 0.25 * math.sin(l.time * 2 * math.pi / 4.5);
          }
      }
    }
    if (!_travels) speedT = 0;
    l.speed += (speedT - l.speed) * kSpeed;
    l.bright += (brightT - l.bright) * k;
    l.tint += (tintT - l.tint) * k;
    l.stall += (stallT - l.stall) * k;
    l.tail += (tail - l.tail) * k;
    l.phase = (l.phase + l.speed * dt) % 1;
    _repaint.value++;
    if (live == null && l.bright < 0.01 && l.stall < 0.01) _ticker.stop();
  }

  @override
  void dispose() {
    _ticker.dispose();
    _open.dispose();
    _flash.dispose();
    _repaint.dispose();
    super.dispose();
  }

  void _measured(Size size) {
    if (!mounted || size.width == _captionWidth) return;
    setState(() => _captionWidth = size.width);
  }

  static const _captionSize = 12.0;
  static const _bendDepth = 7.0;
  static const _bendMargin = 28.0;

  /// Where the caption's x-height centre sits, measured from the top of its
  /// text box: the caption is placed so that centre lies on the border line,
  /// not the box's middle.
  (double top, double height) _xCentre(BuildContext context) {
    final style = KitText.styleOf(
      context,
      KitTextRole.caption,
      tone: KitTextTone.secondary,
    ).copyWith(fontSize: _captionSize, fontWeight: FontWeight.w500);
    final painter = TextPainter(
      text: TextSpan(text: 'x', style: style),
      textDirection: Directionality.of(context),
      textScaler: MediaQuery.textScalerOf(context),
    )..layout();
    final baseline = painter.computeDistanceToActualBaseline(
      TextBaseline.alphabetic,
    );
    final xHeight = 0.52 * painter.textScaler.scale(_captionSize);
    final result = (baseline - xHeight / 2, painter.height);
    painter.dispose();
    return result;
  }

  /// The chosen soft ring sweep (see [_ActivityGlow]): only while a reply
  /// runs, only when on; Calm is still, Off and reduced motion draw none.
  Widget _glow(BuildContext context, double bendHalf, double dip) {
    final on = widget.activityGlow ?? KitEffects.of(context).activityGlow;
    final level = _level;
    final allowed =
        on && level != KitMotionLevel.off && !KitMotion.reduced(context);
    if (!allowed) return const SizedBox.shrink();
    final roles = KitTokens.of(context).roles;
    return _ActivityGlow(
      active: widget.live != null,
      mode: level == KitMotionLevel.calm
          ? _GlowMode.calm
          : KitMotion.loops
          ? _GlowMode.live
          : _GlowMode.frame,
      radius: widget.radius,
      primary: roles.accent,
      // Never attention: amber means "needs you". The pack's keyword hue is
      // its own second colour (blue → violet on GitHub, green → teal on
      // Graphite) and carries no meaning.
      partner: roles.codeKeyword,
      bendHalf: bendHalf,
      dip: dip,
    );
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final level = _level;
    final reduced = KitMotion.reduced(context);
    final roles = tokens.roles;
    final (xCentre, textHeight) = _xCentre(context);
    return TweenAnimationBuilder<double>(
      tween: Tween<double>(end: _captionWidth),
      duration: reduced ? Duration.zero : KitMotion.quick,
      curve: KitMotion.enter,
      builder: (context, captionWidth, _) => AnimatedBuilder(
        animation: _open,
        builder: (context, _) {
          final open = _open.value;
          // The dip is one bell: as wide as the caption plus a margin each
          // side, as deep as 7 dp (half that in Calm). It deepens from
          // nothing along the same curve; nothing appears at its ends.
          final bendHalf = open > 0 ? captionWidth / 2 + _bendMargin : 0.0;
          final dip =
              _bendDepth * open * (level == KitMotionLevel.calm ? 0.5 : 1);
          // Once the gap has closed on an empty edge nothing stays behind:
          // a hidden caption would keep Stop in the tree and tappable.
          final idle = open == 0 && _data.isEmpty;
          return Stack(
            clipBehavior: Clip.none,
            children: [
              ClipPath(
                clipper: _BendClipper(bendHalf, dip),
                child: widget.surface,
              ),
              _glow(context, bendHalf, dip),
              if (!idle || _light.bright > 0.01)
                Positioned.fill(
                  child: IgnorePointer(
                    child: ExcludeSemantics(
                      child: RepaintBoundary(
                        child: CustomPaint(
                          painter: _EdgePainter(
                            light: _light,
                            open: open,
                            flash: _flash,
                            radius: widget.radius,
                            bendHalf: bendHalf,
                            dip: dip,
                            comet: _travels,
                            still: !_travels && level != KitMotionLevel.off,
                            neutral: roles.text1,
                            accent: roles.accent,
                            wash: roles.text1,
                            rim: roles.hairline,
                            rimWidth: KitTokens.hairlineWidth(context),
                            repaint: Listenable.merge([_repaint, _flash]),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              if (!idle)
                Positioned(
                  top: 0.5 - 24,
                  left: 0,
                  right: 0,
                  height: 48,
                  child: Center(
                    child: Opacity(
                      opacity: ((open - 0.5) / 0.5).clamp(0.0, 1.0),
                      child: _SizeReporter(
                        onSize: _measured,
                        child: _EdgeCaption(
                          data: _shown,
                          xCentre: xCentre,
                          textHeight: textHeight,
                          size: _captionSize,
                        ),
                      ),
                    ),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }
}

/// The bend: the border eases down into one wide, shallow dip that cradles
/// the caption and eases back up. A raised cosine of a flattened argument,
/// so the bottom is broad and there is no corner anywhere: the slope is zero
/// at both ends and at the middle. [dx] is the distance from the centre.
double _dipDepth(double dx, double half, double depth) {
  if (half <= 0) return 0;
  final u = dx.abs() / half;
  if (u >= 1) return 0;
  return depth * 0.5 * (1 + math.cos(math.pi * math.pow(u, 2.4)));
}

/// Points along the dip from left to right on the line at [top] plus the
/// depth, for the clip, the outline and the rim.
List<Offset> _dipPoints(double centre, double half, double depth, double top) {
  const n = 48;
  return [
    for (var i = 0; i <= n; i++)
      Offset(
        centre - half + 2 * half * i / n,
        top + _dipDepth(-half + 2 * half * i / n, half, depth),
      ),
  ];
}

class _BendClipper extends CustomClipper<Path> {
  const _BendClipper(this.half, this.dip);

  final double half;
  final double dip;

  @override
  Path getClip(Size size) {
    final all = Path()
      ..addRect(Rect.fromLTRB(-80, -80, size.width + 80, size.height + 80));
    if (dip < 0.3 || half <= 0) return all;
    final points = _dipPoints(size.width / 2, half, dip, 0);
    final cut = Path()..moveTo(points.first.dx, -0.5);
    for (final point in points) {
      cut.lineTo(point.dx, point.dy);
    }
    cut
      ..lineTo(points.last.dx, -0.5)
      ..close();
    return Path.combine(PathOperation.difference, all, cut);
  }

  @override
  bool shouldReclip(_BendClipper old) => old.half != half || old.dip != dip;
}

/// Draws the light on the outline (see [_LivingEdge]). Everything is drawn
/// on the composer's own outline: the same rounded rectangle the glass
/// clips to, inset by half the rim so the light sits on the border and not
/// beside it. The path starts at the top centre, where the gap is, and runs
/// clockwise.
class _EdgePainter extends CustomPainter {
  _EdgePainter({
    required this.light,
    required this.open,
    required this.flash,
    required this.radius,
    required this.bendHalf,
    required this.dip,
    required this.comet,
    required this.still,
    required this.neutral,
    required this.accent,
    required this.wash,
    required this.rim,
    required this.rimWidth,
    required super.repaint,
  });

  final _EdgeLight light;
  final double open;
  final Animation<double> flash;
  final double radius;
  final double bendHalf;
  final double dip;
  final bool comet;
  final bool still;
  final Color neutral;
  final Color accent;
  final Color wash;

  /// The glass's own edge colour and width, for the parted border's ends.
  final Color rim;
  final double rimWidth;

  static const _inset = 0.5;

  /// Starts at the top centre (at the bottom of the bend when there is
  /// one) and runs clockwise.
  static Path _outline(Size size, double radius, double bendHalf, double dip) {
    final r = math.max(radius - _inset, 1.0);
    final l = _inset,
        t = _inset,
        rt = size.width - _inset,
        b = size.height - _inset;
    final c = size.width / 2;
    final path = Path();
    final bent = dip >= 0.5;
    if (bent) {
      final points = _dipPoints(c, bendHalf, dip, t);
      path.moveTo(c, t + dip);
      for (final point in points) {
        if (point.dx > c) path.lineTo(point.dx, point.dy);
      }
    } else {
      path.moveTo(c, t);
    }
    path
      ..lineTo(rt - r, t)
      ..arcTo(
        Rect.fromLTWH(rt - 2 * r, t, 2 * r, 2 * r),
        -math.pi / 2,
        math.pi / 2,
        false,
      )
      ..lineTo(rt, b - r)
      ..arcTo(
        Rect.fromLTWH(rt - 2 * r, b - 2 * r, 2 * r, 2 * r),
        0,
        math.pi / 2,
        false,
      )
      ..lineTo(l + r, b)
      ..arcTo(
        Rect.fromLTWH(l, b - 2 * r, 2 * r, 2 * r),
        math.pi / 2,
        math.pi / 2,
        false,
      )
      ..lineTo(l, t + r)
      ..arcTo(Rect.fromLTWH(l, t, 2 * r, 2 * r), math.pi, math.pi / 2, false);
    if (bent) {
      final points = _dipPoints(c, bendHalf, dip, t);
      for (final point in points) {
        if (point.dx < c) path.lineTo(point.dx, point.dy);
      }
    }
    return path..close();
  }

  Paint _line(Color color, double width, {double blur = 0}) => Paint()
    ..style = PaintingStyle.stroke
    ..strokeCap = StrokeCap.round
    ..strokeWidth = width
    ..color = color
    ..maskFilter = blur > 0 ? MaskFilter.blur(BlurStyle.normal, blur) : null;

  /// A soft hue with its head at [head] and a long tail behind it: wide,
  /// blurred, low-alpha pieces that overlap into one smooth glow, faint at
  /// both ends. No core, no head: only a fade.
  void _glow(
    Canvas canvas,
    PathMetric metric,
    double head,
    double tail,
    double direction,
    Color color,
    double strength,
  ) {
    const pieces = 20;
    final length = metric.length;
    for (var i = 0; i < pieces; i++) {
      final u0 = i / pieces, u1 = (i + 1) / pieces, mid = (u0 + u1) / 2;
      final fadeIn = KitMotion.tailFadeIn.transform(
        (mid / 0.14).clamp(0.0, 1.0),
      );
      final profile = math.pow(1 - mid, 1.5) * fadeIn;
      // Neighbouring blurs overlap, so each piece carries about half.
      final alpha = 0.2 * strength * profile;
      if (alpha < 0.004) continue;
      _extract(
        canvas,
        metric,
        length,
        head - direction * u0 * tail,
        head - direction * (u1 + 0.02) * tail,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 16
          ..strokeCap = StrokeCap.butt
          ..color = color.withValues(alpha: alpha.clamp(0.0, 1.0))
          ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 7),
      );
    }
  }

  @override
  void paint(Canvas canvas, Size size) {
    final outline = _outline(size, radius, bendHalf, dip);
    final metric = outline.computeMetrics().first;
    final length = metric.length;
    final half = bendHalf;
    final color = Color.lerp(
      neutral,
      accent,
      light.tint.clamp(0.0, 1.0) * 0.5,
    )!;
    canvas.save();
    final f = flash.value;
    if (f > 0 && f < 1) {
      // A neutral wash along the outline, gone within a moment.
      canvas.drawPath(
        outline,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 6
          ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 5)
          ..color = wash.withValues(alpha: 0.2 * (1 - f)),
      );
    }
    if (comet && light.bright > 0.02) {
      _glow(
        canvas,
        metric,
        light.phase * length,
        light.tail * length,
        1,
        color,
        light.bright,
      );
    }
    if (still && light.still + light.bright > 0.02) {
      // Calm and reduced: no travel, a slow breathing hue on the outline.
      canvas.drawPath(
        outline,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 8
          ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 6)
          ..color = color.withValues(
            alpha: 0.10 * math.max(light.bright, light.still),
          ),
      );
    }
    canvas.restore();
    if (dip >= 0.3 && bendHalf > 0) {
      // The bend's own edge: the glass rim was cut away with the dip, so the
      // hairline follows the curve, a little fainter in the deepest part
      // where the caption reads over it.
      final c = size.width / 2;
      final points = _dipPoints(c, bendHalf, dip, _inset);
      final edge = Path()..moveTo(points.first.dx, points.first.dy);
      for (final point in points.skip(1)) {
        edge.lineTo(point.dx, point.dy);
      }
      final box = Rect.fromLTRB(c - bendHalf, 0, c + bendHalf, dip + 2);
      canvas.drawPath(
        edge,
        _line(rim, rimWidth)
          ..shader = LinearGradient(
            colors: [
              rim,
              rim.withValues(alpha: rim.a * 0.85),
              rim.withValues(alpha: rim.a * 0.35),
              rim.withValues(alpha: rim.a * 0.85),
              rim,
            ],
            stops: const [0, 0.25, 0.5, 0.75, 1],
          ).createShader(box),
      );
    }
    if (light.stall > 0.01 && open > 0.01) {
      // A stall, seen without reading: a slow breathing hue at each edge of
      // the gap, no travel.
      final beat = 0.5 + 0.5 * math.sin(light.time * 2 * math.pi / 3.2);
      final s = light.stall * (0.35 + 0.65 * beat);
      final edge = half;
      for (final dir in [1.0, -1.0]) {
        _glow(canvas, metric, dir * (edge + 6), 90, dir, neutral, s);
      }
    }
  }

  static void _extract(
    Canvas canvas,
    PathMetric metric,
    double length,
    double from,
    double to,
    Paint paint,
  ) {
    var a = math.min(from, to), b = math.max(from, to);
    if (b - a <= 0) return;
    if (a >= 0 && b <= length) {
      canvas.drawPath(metric.extractPath(a, b), paint);
    } else if (b <= 0 || a >= length) {
      final shift = (a / length).floor() * length;
      canvas.drawPath(metric.extractPath(a - shift, b - shift), paint);
    } else if (a < 0) {
      canvas.drawPath(metric.extractPath(0, b), paint);
      canvas.drawPath(metric.extractPath(length + a, length), paint);
    } else {
      canvas.drawPath(metric.extractPath(a, length), paint);
      canvas.drawPath(metric.extractPath(0, b - length), paint);
    }
  }

  @override
  bool shouldRepaint(_EdgePainter old) =>
      old.bendHalf != bendHalf ||
      old.dip != dip ||
      old.open != open ||
      old.radius != radius ||
      old.comet != comet ||
      old.still != still ||
      old.neutral != neutral ||
      old.rim != rim;
}
