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
/// the highlight still shows), for Two colours the Accent
/// ([ThemeRoles.partner]: the pack's partner hue or the person's swatch,
/// never amber: amber means "needs you").
(Color, Color) _glowHues(BuildContext context, KitGlowColours colours) {
  final roles = KitTokens.of(context).roles;
  final primary = roles.accent;
  if (colours == KitGlowColours.two) return (primary, roles.partner);
  final dark = Theme.of(context).brightness == Brightness.dark;
  return (primary, Color.lerp(primary, roles.text1, dark ? .55 : .3)!);
}

/// The halo under the box (a [BoxDecoration] shadow: the box's own solid
/// surface covers its middle, so only the halo outside shows).
///
/// Paint only, on a layer of its own: its breath repaints the halo and
/// nothing else. Rebuilt on every frame, as it was, it relaid out the
/// nearest LayoutBuilder's subtree and recorded the page around the composer
/// again each frame (scroll-perf, 2026-10-23: the Appearance preview's whole
/// chat, words, plates and panes, on every frame of a scroll).
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
      child: RepaintBoundary(
        child: CustomPaint(
          key: const ValueKey('kit-activity-glow-halo'),
          painter: _HaloPainter(
            lap: breathing ? lap : null,
            radius: radius,
            color: color,
          ),
        ),
      ),
    ),
  );
}

/// Paints [_ClassicHalo]'s shadow as a [DecoratedBox] would: the same
/// [BoxDecoration], its strength read from [lap] at paint time (the
/// midpoint when it does not breathe).
class _HaloPainter extends CustomPainter {
  _HaloPainter({required this.lap, required this.radius, required this.color})
    : super(repaint: lap);

  final Animation<double>? lap;
  final double radius;
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final decoration = BoxDecoration(
      borderRadius: BorderRadius.circular(radius),
      boxShadow: [
        BoxShadow(
          color: color.withValues(alpha: _Classic.halo(lap?.value)),
          blurRadius: 18,
          spreadRadius: 1,
        ),
      ],
    );
    decoration.createBoxPainter().paint(
      canvas,
      Offset.zero,
      ImageConfiguration(size: size),
    );
  }

  @override
  bool shouldRepaint(_HaloPainter old) =>
      old.lap != lap || old.radius != radius || old.color != color;
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
    this.lineWidth,
  });

  /// The ring's width; null keeps its own (hook H8).
  final double? lineWidth;

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
            strokeWidth: lineWidth ?? _ActivityRingPainter.defaultStrokeWidth,
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
    this.strokeWidth = defaultStrokeWidth,
    super.repaint,
  }) : _still = sweep;

  final BorderRadius radius;
  final Color primary;
  final Color tertiary;
  final Animation<double> lap;
  final bool travels;
  final double? _still;

  double? get sweep => travels ? lap.value : _still;

  static const defaultStrokeWidth = 1.6;

  final double strokeWidth;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;
    final ring = radius.toRRect(rect).deflate(strokeWidth / 2);
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth;
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
      old.radius != radius ||
      old.strokeWidth != strokeWidth;
}
