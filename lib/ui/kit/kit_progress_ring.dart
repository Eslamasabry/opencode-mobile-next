// KitProgressRing: one long job as one big calm ring, the sentence of what
// it does now and a slim trail of its steps
// (docs/ux-system/kit-api/KitProgressRing.md; vision pick "Phone setup
// progress" B, 2026-10-20).
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:intl/intl.dart' hide TextDirection;

import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// One step's place on a [KitProgressRing]'s trail.
enum KitTrailMark { done, current, waiting }

/// What the ring's figure says.
enum _Figure { measured, steps, finished }

/// One long job (phone setup) as one big ring with its figure inside, one
/// sentence under it of what is happening now, and a slim trail of the
/// job's steps.
///
/// The figure is always a real one, chosen by the constructor:
/// [KitProgressRing.measured] is the current step's measured share ("64%",
/// floored, so it never claims more than arrived), with an optional
/// [caption] under it ("18 of 30 MB"); [KitProgressRing.steps] is steps done
/// of all ("2 of 6") for a step that reports no amount, never a guessed
/// percent; [KitProgressRing.finished] is a full ring with a check.
///
/// [halted] (paused, stopped or failed) turns the arc and the current step's
/// mark neutral: never red, and not amber, which is for "needs you".
///
/// The arc eases forward over [KitMotion.standard]; a smaller value (a new
/// stage measuring from zero) is a new arc and lands at once, so the ring
/// never runs backwards. Under reduced motion every change is instant. The
/// ring is one semantics node (its label, the figure and the caption); the
/// [headline] is a live region, so a screen reader hears each new step once
/// and never a byte tick.
///
/// States: none — the caller supplies every value.
class KitProgressRing extends StatefulWidget {
  const KitProgressRing.measured({
    super.key,
    required double this.value,
    required this.headline,
    required this.semanticsLabel,
    this.caption,
    this.trail = const [],
    this.halted = false,
    this.ringKey,
    this.headlineKey,
  }) : _figure = _Figure.measured,
       done = null,
       of = null;

  const KitProgressRing.steps({
    super.key,
    required int this.done,
    required int this.of,
    required this.headline,
    required this.semanticsLabel,
    this.trail = const [],
    this.halted = false,
    this.ringKey,
    this.headlineKey,
  }) : _figure = _Figure.steps,
       value = null,
       caption = null;

  const KitProgressRing.finished({
    super.key,
    required this.headline,
    required this.semanticsLabel,
    this.trail = const [],
    this.ringKey,
    this.headlineKey,
  }) : _figure = _Figure.finished,
       value = null,
       done = null,
       of = null,
       caption = null,
       halted = false;

  final _Figure _figure;

  /// [KitProgressRing.measured]: 0..1 of the current step, measured.
  final double? value;

  /// [KitProgressRing.steps]: steps finished, and all of them.
  final int? done;
  final int? of;

  /// Under the figure, inside the ring: the measured amount ("18 of 30 MB").
  final String? caption;

  /// What is happening now, in one plain sentence ("Installing OpenCode").
  final String headline;

  /// One mark per step of the job, in order; empty draws no trail.
  final List<KitTrailMark> trail;

  /// Paused, stopped or failed: the arc and the current mark go neutral.
  final bool halted;

  /// What the ring measures, for a screen reader ("Setup progress").
  final String semanticsLabel;

  /// Test hooks: the ring's semantics node and the headline.
  final Key? ringKey;
  final Key? headlineKey;

  /// The arc's share of the circle, 0..1.
  double get fraction => switch (_figure) {
    _Figure.measured => (value ?? 0).isNaN ? 0 : value!.clamp(0.0, 1.0),
    _Figure.steps => of! <= 0 ? 0 : (done! / of!).clamp(0.0, 1.0),
    _Figure.finished => 1,
  };

  @override
  State<KitProgressRing> createState() => _KitProgressRingState();
}

class _KitProgressRingState extends State<KitProgressRing>
    with SingleTickerProviderStateMixin {
  late final AnimationController _move = AnimationController(
    vsync: this,
    duration: KitMotion.standard,
    value: 1,
  );
  late double _from = widget.fraction;
  late double _to = widget.fraction;

  double get _arc =>
      _from + (_to - _from) * KitMotion.enter.transform(_move.value);

  @override
  void didUpdateWidget(KitProgressRing old) {
    super.didUpdateWidget(old);
    final target = widget.fraction;
    if (target == _to) return;
    final now = _arc;
    // Backwards is a new arc (a new stage, a new job): it lands at once.
    if (target < now || KitMotion.reduced(context)) {
      _from = _to = target;
      _move.value = 1;
      return;
    }
    _from = now;
    _to = target;
    _move.forward(from: 0);
  }

  @override
  void dispose() {
    _move.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final locale = Localizations.localeOf(context);
    final l10n = lookupAppLocalizations(locale);
    final dark = Theme.of(context).brightness == Brightness.dark;
    final halted = widget.halted;
    final figure = widget._figure;

    final String spokenFigure;
    final String? whole;
    String? total;
    switch (figure) {
      case _Figure.measured:
        final percent = (widget.fraction * 100 + 1e-9).floor().clamp(0, 100);
        whole = NumberFormat.decimalPattern(locale.toString()).format(percent);
        spokenFigure = l10n.kitProgressRingPercent(whole);
      case _Figure.steps:
        final format = NumberFormat.decimalPattern(locale.toString());
        whole = format.format(widget.done);
        total = format.format(widget.of);
        spokenFigure = l10n.kitProgressRingSteps(whole, total);
      case _Figure.finished:
        whole = null;
        spokenFigure = l10n.kitProgressRingFinished;
    }

    final ring = LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.maxWidth.isFinite
            ? constraints.maxWidth
            : 264.0;
        final diameter = math.min(width * 0.64, 264.0).clamp(150.0, 264.0);
        final stroke = (diameter * 0.052).clamp(9.0, 14.0);
        final inner = (diameter - stroke * 2) * 0.74;
        final ink = halted ? roles.text2 : roles.text1;
        final big = KitText.styleOf(context, KitTextRole.largeTitle).copyWith(
          color: ink,
          fontSize: diameter * 0.25,
          height: 1,
          fontWeight: FontWeight.w600,
          letterSpacing: -diameter * 0.006,
          fontFeatures: const [FontFeature.tabularFigures()],
        );
        final small = KitText.styleOf(
          context,
          KitTextRole.secondary,
        ).copyWith(color: roles.text2);
        final Widget center = switch (figure) {
          _Figure.measured => Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Directionality(
                textDirection: TextDirection.ltr,
                child: Text.rich(
                  TextSpan(
                    children: [
                      TextSpan(text: whole),
                      TextSpan(
                        text: l10n.kitProgressRingPercentSign,
                        style: big.copyWith(
                          fontSize: diameter * 0.13,
                          letterSpacing: 0,
                        ),
                      ),
                    ],
                  ),
                  style: big,
                  maxLines: 1,
                ),
              ),
              if (widget.caption case final caption?) ...[
                SizedBox(height: tokens.space2),
                Text(
                  caption,
                  style: small,
                  maxLines: 1,
                  textAlign: TextAlign.center,
                ),
              ],
            ],
          ),
          _Figure.steps => Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text.rich(
                TextSpan(
                  children: [
                    TextSpan(text: whole),
                    TextSpan(
                      text: ' ${l10n.kitProgressRingOfTotal(total!)}',
                      style: big.copyWith(
                        fontSize: diameter * 0.1,
                        letterSpacing: 0,
                        fontWeight: FontWeight.w500,
                        color: roles.text2,
                      ),
                    ),
                  ],
                ),
                style: big,
                maxLines: 1,
              ),
              SizedBox(height: tokens.space2),
              Text(
                l10n.kitProgressRingStepsDone,
                style: small,
                maxLines: 1,
                textAlign: TextAlign.center,
              ),
            ],
          ),
          _Figure.finished => Icon(
            AppIconography.check,
            size: diameter * 0.3,
            color: roles.accent,
          ),
        };
        return Center(
          child: SizedBox.square(
            dimension: diameter,
            child: AnimatedBuilder(
              animation: _move,
              builder: (context, child) => CustomPaint(
                painter: _RingPainter(
                  arc: _arc,
                  stroke: stroke,
                  track: roles.text3.withValues(alpha: dark ? 0.22 : 0.18),
                  color: halted
                      ? roles.text3.withValues(alpha: 0.7)
                      : roles.accent,
                  glow: halted
                      ? null
                      : roles.accent.withValues(alpha: dark ? 0.32 : 0.18),
                  tail: roles.ground,
                  textDirection: Directionality.of(context),
                ),
                child: child,
              ),
              child: Center(
                child: SizedBox.square(
                  dimension: inner,
                  child: FittedBox(fit: BoxFit.scaleDown, child: center),
                ),
              ),
            ),
          ),
        );
      },
    );

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Semantics(
          key: widget.ringKey,
          container: true,
          excludeSemantics: true,
          label: widget.semanticsLabel,
          value: [spokenFigure, ?widget.caption].join(', '),
          child: ring,
        ),
        SizedBox(height: tokens.space6 + tokens.space2),
        Semantics(
          liveRegion: true,
          child: AnimatedSwitcher(
            duration: KitMotion.reduced(context)
                ? Duration.zero
                : KitMotion.standard,
            switchInCurve: KitMotion.enter,
            switchOutCurve: KitMotion.exit,
            child: KitText(
              widget.headline,
              key: widget.headlineKey ?? ValueKey(widget.headline),
              role: KitTextRole.title,
              textAlign: TextAlign.center,
              maxLines: 3,
            ),
          ),
        ),
        if (widget.trail.isNotEmpty) ...[
          SizedBox(height: tokens.space4),
          ExcludeSemantics(
            child: _Trail(marks: widget.trail, halted: halted),
          ),
        ],
      ],
    );
  }
}

/// The slim trail: a dot per step, the current one a short bar, joined by
/// hairlines.
class _Trail extends StatelessWidget {
  const _Trail({required this.marks, required this.halted});

  final List<KitTrailMark> marks;
  final bool halted;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final duration = KitMotion.reduced(context)
        ? Duration.zero
        : KitMotion.standard;
    const dot = 8.0;
    final lit = halted ? roles.text2 : roles.accent;
    final waiting = roles.text3.withValues(alpha: 0.45);
    final children = <Widget>[];
    for (var i = 0; i < marks.length; i++) {
      final mark = marks[i];
      if (i > 0) {
        final reached = mark != KitTrailMark.waiting;
        children.add(
          AnimatedContainer(
            duration: duration,
            curve: KitMotion.enter,
            width: 14,
            height: 1.5,
            margin: EdgeInsets.symmetric(horizontal: tokens.space1),
            color: reached ? lit.withValues(alpha: 0.55) : waiting,
          ),
        );
      }
      children.add(
        AnimatedContainer(
          duration: duration,
          curve: KitMotion.enter,
          width: mark == KitTrailMark.current ? 24 : dot,
          height: dot,
          decoration: BoxDecoration(
            color: mark == KitTrailMark.waiting ? null : lit,
            border: mark == KitTrailMark.waiting
                ? Border.all(
                    color: waiting,
                    width: KitTokens.focusRingWidth(context),
                  )
                : null,
            borderRadius: BorderRadius.circular(dot / 2),
          ),
        ),
      );
    }
    return FittedBox(
      fit: BoxFit.scaleDown,
      child: Row(mainAxisSize: MainAxisSize.min, children: children),
    );
  }
}

class _RingPainter extends CustomPainter {
  const _RingPainter({
    required this.arc,
    required this.stroke,
    required this.track,
    required this.color,
    required this.glow,
    required this.tail,
    required this.textDirection,
  });

  final double arc;
  final double stroke;
  final Color track;
  final Color color;
  final Color? glow;

  /// What the arc's tail leans towards (the ground).
  final Color tail;
  final TextDirection textDirection;

  @override
  void paint(Canvas canvas, Size size) {
    final center = size.center(Offset.zero);
    final radius = (size.shortestSide - stroke) / 2;
    final rect = Rect.fromCircle(center: center, radius: radius);
    canvas.drawCircle(
      center,
      radius,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = stroke
        ..color = track,
    );
    if (arc <= 0) return;
    // Clockwise from twelve o'clock; mirrored for a right-to-left reader.
    if (textDirection == TextDirection.rtl) {
      canvas
        ..save()
        ..translate(size.width, 0)
        ..scale(-1, 1);
    }
    final sweep = 2 * math.pi * arc;
    const start = -math.pi / 2;
    if (glow case final glow?) {
      canvas.drawArc(
        rect,
        start,
        sweep,
        false,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = stroke * 1.6
          ..strokeCap = StrokeCap.round
          ..color = glow
          ..maskFilter = MaskFilter.blur(BlurStyle.normal, stroke * 0.9),
      );
    }
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = stroke
      ..strokeCap = StrokeCap.round
      ..color = color;
    if (glow != null && arc < 1) {
      // The tail is a little deeper than the head, so the arc reads as
      // moving towards its bright end. The gradient starts half a cap
      // before the tail, so the round tail cap keeps the tail's colour.
      final cap = stroke / 2 / radius;
      paint.shader = SweepGradient(
        endAngle: math.min(2 * math.pi, sweep + cap * 2),
        colors: [Color.lerp(color, tail, 0.28)!, color],
        transform: GradientRotation(start - cap),
      ).createShader(rect);
    }
    canvas.drawArc(rect, start, sweep, false, paint);
    if (textDirection == TextDirection.rtl) canvas.restore();
  }

  @override
  bool shouldRepaint(_RingPainter old) =>
      old.arc != arc ||
      old.stroke != stroke ||
      old.track != track ||
      old.color != color ||
      old.glow != glow ||
      old.tail != tail ||
      old.textDirection != textDirection;
}
