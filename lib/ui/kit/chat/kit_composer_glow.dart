part of 'kit_composer.dart';

/// How the activity glow is drawn.
enum _GlowMode {
  /// Full motion: the soft ring sweeps the box, one lap in 8 s (never faster
  /// than [KitMotion.edgeLightMaxLapsPerSecond]).
  live,

  /// Calm: a slow, still glow at half strength; nothing travels.
  calm,

  /// One frame of the live sweep (tests and screenshots: loops are off).
  frame,
}

/// A part of KitComposer. The owner-chosen "Glowing border while replying" (Settings › Appearance ›
/// Effects), drawn only when the person has turned it on: a soft ring sweep around
/// the whole message box in the theme pack's primary and tertiary hues.
/// Hues fade into each other and into nothing; there is no head, no seam and
/// no sharp line. One painter on one repaint boundary, no backdrop filter.
///
/// The ticker runs only while a reply runs (and while the glow fades out),
/// and stops with the route (TickerMode).
class _ActivityGlow extends StatefulWidget {
  const _ActivityGlow({
    required this.active,
    required this.mode,
    required this.radius,
    required this.primary,
    required this.partner,
  });

  final bool active;
  final _GlowMode mode;
  final double radius;
  final Color primary;
  final Color partner;

  /// The line's width, the wash's width and blur, and their strongest alpha
  /// (from the appearance spike's ring sweep).
  static const double lineWidth = 2.2;
  static const double washWidth = 12;
  static const double washBlur = 7;
  static const double linePeak = .75;
  static const double washPeak = .22;

  /// Where a still frame starts the sweep, as a share of a lap.
  static const double framePhase = .08;

  @override
  State<_ActivityGlow> createState() => _ActivityGlowState();
}

class _ActivityGlowState extends State<_ActivityGlow>
    with SingleTickerProviderStateMixin {
  late final Ticker _ticker = createTicker(_step);
  final _repaint = ValueNotifier<int>(0);
  double _strength = 0;
  double _phase = _ActivityGlow.framePhase;
  Duration _last = Duration.zero;

  double get _target => !widget.active
      ? 0
      : widget.mode == _GlowMode.calm
      ? .5
      : 1;

  bool get _travels => widget.mode == _GlowMode.live;

  @override
  void initState() {
    super.initState();
    _settle();
  }

  @override
  void didUpdateWidget(_ActivityGlow old) {
    super.didUpdateWidget(old);
    _settle();
  }

  void _settle() {
    if (widget.mode == _GlowMode.live) {
      if (!_ticker.isActive && (widget.active || _strength > 0.005)) {
        _last = Duration.zero;
        unawaited(_ticker.start());
      }
    } else {
      // Still modes change at once: nothing animates.
      _ticker.stop();
      _strength = _target;
    }
    _repaint.value++;
  }

  void _step(Duration elapsed) {
    final dt = _last == Duration.zero
        ? 0.016
        : ((elapsed - _last).inMicroseconds / 1e6).clamp(0.0, 0.05);
    _last = elapsed;
    final k =
        1 - math.exp(-dt / (KitMotion.edgeLightHueFade.inMilliseconds / 1500));
    _strength += (_target - _strength) * k;
    if (_travels) {
      _phase =
          (_phase +
              math.min(
                    KitMotion.activityGlowLapsPerSecond,
                    KitMotion.edgeLightMaxLapsPerSecond,
                  ) *
                  dt) %
          1;
    }
    if (!widget.active && _strength < 0.005) {
      _strength = 0;
      _ticker.stop();
    }
    _repaint.value++;
    // The widget tree drops the paint once it is gone.
    if (_strength == 0 && mounted) setState(() {});
  }

  @override
  void dispose() {
    _ticker.dispose();
    _repaint.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_strength < 0.005 && !widget.active) return const SizedBox.shrink();
    return Positioned.fill(
      child: IgnorePointer(
        child: ExcludeSemantics(
          child: RepaintBoundary(
            child: CustomPaint(
              key: ValueKey('kit-activity-glow-${widget.mode.name}'),
              painter: _GlowPainter(
                strength: () => _strength,
                phase: () => _phase,
                radius: widget.radius,
                primary: widget.primary,
                partner: widget.partner,
                repaint: _repaint,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// The ring sweep: one soft wash and one thin gradient line on the box's own
/// outline, a [SweepGradient] turned by
/// the phase.
class _GlowPainter extends CustomPainter {
  _GlowPainter({
    required this.strength,
    required this.phase,
    required this.radius,
    required this.primary,
    required this.partner,
    required super.repaint,
  });

  final double Function() strength;
  final double Function() phase;
  final double radius;
  final Color primary;
  final Color partner;

  @override
  void paint(Canvas canvas, Size size) {
    final level = strength();
    if (level <= 0.01) return;
    final outline = _composerOutline(size, radius);
    const peak = _ActivityGlow.linePeak;
    final colors = [
      primary.withValues(alpha: peak),
      partner.withValues(alpha: peak),
      partner.withValues(alpha: 0.12),
      primary.withValues(alpha: 0.12),
      primary.withValues(alpha: peak),
    ];
    Shader shader(double share) => SweepGradient(
      colors: [
        for (final c in colors) c.withValues(alpha: c.a * level * share),
      ],
      stops: const [0, .3, .55, .8, 1],
      transform: GradientRotation(2 * math.pi * (phase() - .25)),
    ).createShader(Offset.zero & size);
    canvas
      ..drawPath(
        outline,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = _ActivityGlow.washWidth
          ..maskFilter = const MaskFilter.blur(
            BlurStyle.normal,
            _ActivityGlow.washBlur,
          )
          ..shader = shader(_ActivityGlow.washPeak / peak),
      )
      ..drawPath(
        outline,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = _ActivityGlow.lineWidth
          ..strokeCap = StrokeCap.round
          ..shader = shader(1),
      );
  }

  @override
  bool shouldRepaint(_GlowPainter old) =>
      old.radius != radius || old.primary != primary || old.partner != partner;
}
