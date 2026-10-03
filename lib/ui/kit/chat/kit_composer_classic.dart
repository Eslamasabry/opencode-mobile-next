part of 'kit_composer.dart';

/// The classic glowing border: the composer's original "alive" treatment,
/// restored as it was drawn in 1.0.43 and 1.0.44 (`_ComposerActivity` and
/// `_ActivityRingPainter` in `lib/ui/screens/chat/composer.dart`). A bright
/// highlight travels the box's own rounded border over a dim base ring while
/// a soft halo breathes twice per lap underneath. Geometry, gradient stops,
/// stroke width, breath and halo are unchanged; only the hues come from the
/// person's colour choice and the lap time from their speed choice.
abstract final class _Classic {
  /// One lap at Normal (the original 3600 ms cycle).
  static const cycle = Duration(milliseconds: 3600);
  static const glowFloor = .15;
  static const glowCeiling = .35;

  /// Where one still frame (tests, screenshots) puts the highlight: a share
  /// of a lap that centres it on the top edge.
  static const framePhase = .57;

  /// The halo's strength: floor to ceiling and back twice per lap. A still
  /// glow holds the midpoint so it reads as lit, not as a frozen frame.
  static double halo(double? t) => t == null
      ? (glowFloor + glowCeiling) / 2
      : (glowFloor + glowCeiling) / 2 -
            (glowCeiling - glowFloor) / 2 * math.cos(4 * math.pi * t);

  /// Laps per second for a speed choice, never above the kit's cap.
  static double laps(KitGlowSpeed speed) => math.min(
    KitMotion.glowMaxLapsPerSecond,
    KitMotion.classicGlowLapsPerSecond *
        switch (speed) {
          KitGlowSpeed.slow => .5,
          KitGlowSpeed.normal => 1.0,
          KitGlowSpeed.fast => 2.0,
        },
  );
}

/// The two hues of the glowing border, both styles: the theme's main colour
/// and, for One colour, a lighter shade of it (deeper on a light surface so
/// the highlight still shows), for Two colours the pack's partner hue (never
/// amber: amber means "needs you").
(Color, Color) _glowHues(BuildContext context, KitGlowColours colours) {
  final roles = KitTokens.of(context).roles;
  final primary = roles.accent;
  if (colours == KitGlowColours.two) return (primary, roles.codeKeyword);
  final dark = Theme.of(context).brightness == Brightness.dark;
  return (primary, Color.lerp(primary, roles.text1, dark ? .55 : .3)!);
}

/// The halo under the box (a [DecoratedBox] shadow: the box's own solid
/// surface covers its middle, so only the halo outside shows).
class _ClassicHalo extends StatelessWidget {
  const _ClassicHalo({
    required this.lap,
    required this.breathing,
    required this.radius,
    required this.color,
  });

  final Animation<double> lap;
  final bool breathing;
  final double radius;
  final Color color;

  @override
  Widget build(BuildContext context) => IgnorePointer(
    child: ExcludeSemantics(
      child: AnimatedBuilder(
        animation: lap,
        builder: (context, _) => DecoratedBox(
          key: const ValueKey('kit-activity-glow-halo'),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(radius),
            boxShadow: [
              BoxShadow(
                color: color.withValues(
                  alpha: _Classic.halo(breathing ? lap.value : null),
                ),
                blurRadius: 18,
                spreadRadius: 1,
              ),
            ],
          ),
        ),
      ),
    ),
  );
}

/// The ring over the box. [sweep] is the highlight's place in the lap, or
/// null for a still primary ring.
class _ClassicRing extends StatelessWidget {
  const _ClassicRing({
    required this.lap,
    required this.travels,
    required this.radius,
    required this.primary,
    required this.partner,
    required this.mode,
  });

  final Animation<double> lap;
  final bool travels;
  final double radius;
  final Color primary;
  final Color partner;
  final _GlowMode mode;

  @override
  Widget build(BuildContext context) => IgnorePointer(
    child: ExcludeSemantics(
      child: RepaintBoundary(
        child: CustomPaint(
          key: ValueKey('kit-activity-glow-${mode.name}'),
          painter: _ActivityRingPainter(
            radius: BorderRadius.circular(radius),
            primary: primary,
            tertiary: partner,
            sweep: switch (mode) {
              _GlowMode.live => null,
              _GlowMode.frame => _Classic.framePhase,
              _GlowMode.calm => null,
            },
            repaint: travels ? lap : null,
            lap: lap,
            travels: travels,
          ),
        ),
      ),
    ),
  );
}

/// Strokes the composer's rounded border with a gradient that carries one
/// bright primary-to-tertiary highlight around the ring; the sweep is the
/// highlight's position around the loop, or none for a still primary ring.
/// (Restored verbatim; it now repaints from the lap itself.)
class _ActivityRingPainter extends CustomPainter {
  _ActivityRingPainter({
    required this.radius,
    required this.primary,
    required this.tertiary,
    required double? sweep,
    required this.lap,
    required this.travels,
    super.repaint,
  }) : _still = sweep;

  final BorderRadius radius;
  final Color primary;
  final Color tertiary;
  final Animation<double> lap;
  final bool travels;
  final double? _still;

  double? get sweep => travels ? lap.value : _still;

  static const _strokeWidth = 1.6;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;
    final ring = radius.toRRect(rect).deflate(_strokeWidth / 2);
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = _strokeWidth;
    final sweep = this.sweep;
    if (sweep == null) {
      paint.color = primary;
    } else {
      final base = primary.withValues(alpha: .28);
      // A dim primary ring with one soft highlight: primary brightening
      // into tertiary and fading back out over about a third of the loop.
      paint.shader = SweepGradient(
        colors: [base, primary, tertiary, primary, base, base],
        stops: const [0, .1, .18, .26, .38, 1],
        transform: GradientRotation(2 * math.pi * sweep),
      ).createShader(rect);
    }
    canvas.drawRRect(ring, paint);
  }

  @override
  bool shouldRepaint(_ActivityRingPainter old) =>
      old.sweep != sweep ||
      old.primary != primary ||
      old.tertiary != tertiary ||
      old.radius != radius;
}
