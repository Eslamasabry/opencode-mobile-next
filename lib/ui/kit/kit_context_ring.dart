import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:intl/intl.dart' hide TextDirection;

import '../../l10n/app_localizations.dart';
import '../theme_roles.dart';
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// How full the model's context window is, in words the ring and the sheet
/// share: calm under 70 %, a warning from 70 %, nearly full from 95 %.
enum KitContextLevel {
  normal,
  high,
  full;

  /// The share where the ring turns to a warning.
  static const highFrom = 0.7;

  /// The share where it reads as almost full.
  static const fullFrom = 0.95;

  static KitContextLevel of(double fraction) => fraction >= fullFrom
      ? full
      : fraction >= highFrom
      ? high
      : normal;

  /// The arc and number colour: accent, then the attention role, then the strong neutral (never red).
  Color colorIn(ThemeRoles roles) => switch (this) {
    normal => roles.accent,
    high => roles.attention,
    full => roles.text1,
  };

  /// A whole percent, floored so "95 %" never shows below the "almost full"
  /// line; the epsilon absorbs binary fractions (0.7 * 100 = 70.000…01).
  /// Clamped to 0–100.
  static int percentOf(double fraction) =>
      (fraction.clamp(0.0, 1.0) * 100 + 1e-9).floor().clamp(0, 100);
}

/// Where a [KitContextRing] sits.
enum KitContextRingSize {
  /// The chat's top bar: a 24 dp ring with its percent beside it, one tap
  /// target that opens the details.
  bar,

  /// The details sheet: a 64 dp ring with the percent inside it.
  large,

  /// A row in the chats list: an 18 dp ring, the number only from 70 %.
  row,
}

/// The conversation's context window as a ring: an arc from 12 o'clock,
/// clockwise, in the level's colour over a muted track, with the percent
/// beside it (docs/ux-system/kit-api/KitContextRing.md). Unknown use draws
/// nothing at all.
///
/// A new value moves the arc and counts the number together; the colour
/// crosses to the next level; crossing 70 % or 95 % upward sends out one
/// halo. While the conversation is compacting a short arc turns and the
/// word reads "Compacting"; when it ends the arc drains to the new value.
/// Under reduced motion it is a still arc and a still number. No scaling,
/// only rotation, opacity, colour and the arc's own length.
///
/// States: none — unknown draws nothing, and normal, filling, almost full,
/// compacting and still are the data this part is given, not KIT-12 states.
class KitContextRing extends StatefulWidget {
  const KitContextRing({
    super.key,
    required this.used,
    this.compacting = false,
    this.openDetails,
    this.size = KitContextRingSize.bar,
    this.ringKey,
  });

  /// Share of the window in use, 0..1 (more is clamped). Null: unknown.
  final double? used;

  /// The conversation is being compacted right now.
  final bool compacting;

  /// Opens the details when tapped. Only the [KitContextRingSize.bar] ring is a button.
  final VoidCallback? openDetails;

  final KitContextRingSize size;

  /// A test hook on the ring's own painted surface.
  final Key? ringKey;

  /// The halo, present only while it is out (a test hook).
  static const haloKey = ValueKey('kit-context-ring-halo');

  /// The short arc's share of the circle while compacting.
  static const compactingArc = 0.16;

  @override
  State<KitContextRing> createState() => _KitContextRingState();
}

class _Dims {
  const _Dims(this.box, this.stroke, this.radius);

  final double box;
  final double stroke;
  final double radius;

  static const bar = _Dims(24, 2.5, 9);
  static const large = _Dims(64, 6, 24);
  static const row = _Dims(18, 2, 6.75);
}

class _KitContextRingState extends State<KitContextRing>
    with TickerProviderStateMixin {
  late final AnimationController _value = AnimationController(
    vsync: this,
    value: 1,
  );
  late final AnimationController _color = AnimationController(
    vsync: this,
    duration: KitMotion.contextColor,
    value: 1,
  );
  late final AnimationController _halo = AnimationController(
    vsync: this,
    duration: KitMotion.contextHalo,
  );
  late final AnimationController _spin = AnimationController(
    vsync: this,
    duration: KitMotion.contextSpin,
  );

  double _from = 0;
  double _to = 0;
  Color? _colorFrom;
  Color? _colorTo;
  Color? _haloColor;

  /// What the arc showed when compacting began: where the drain starts.
  double? _beforeCompaction;

  double _clamp(double? v) =>
      v == null || v.isNaN ? 0 : v.clamp(0.0, 1.0).toDouble();

  double get _arc =>
      _from + (_to - _from) * KitMotion.enter.transform(_value.value);

  Color _levelColor(double fraction) => KitContextLevel.of(
    fraction,
  ).colorIn(ThemeRoles.resolve(Theme.of(context)));

  Color get _shownColor => Color.lerp(
    _colorFrom,
    _colorTo,
    KitMotion.pulse.transform(_color.value),
  )!;

  @override
  void initState() {
    super.initState();
    _from = _to = _clamp(widget.used);
    if (widget.compacting) _beforeCompaction = _to;
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _colorFrom ??= _levelColor(_to);
    _colorTo ??= _colorFrom;
    _syncSpin();
  }

  /// The short arc turns only where loops are allowed (not under
  /// `flutter test`, not under reduced motion).
  void _syncSpin() {
    final want = widget.compacting && KitMotion.loopsIn(context);
    if (want && !_spin.isAnimating) {
      _spin.repeat();
    } else if (!want && _spin.isAnimating) {
      _spin.stop();
    }
  }

  @override
  void didUpdateWidget(KitContextRing old) {
    super.didUpdateWidget(old);
    final reduced = KitMotion.reduced(context);
    final target = _clamp(widget.used);
    final before = old.used == null ? null : _clamp(old.used);
    final current = _arc;
    final colorNow = _shownColor;

    void moveArc(double from, double to, Duration duration) {
      _from = from;
      _to = to;
      if (reduced || duration == Duration.zero || from == to) {
        _from = _to = to;
        _value.value = 1;
        return;
      }
      _value.duration = duration;
      _value.forward(from: 0);
    }

    if (before == null) {
      // Appearing: no journey from nothing.
      _from = _to = target;
      _value.value = 1;
      _colorFrom = _colorTo = _levelColor(target);
      _color.value = 1;
    } else if (old.compacting && !widget.compacting) {
      // Compaction ended: the short arc drains to the new value.
      moveArc(
        _beforeCompaction ?? KitContextRing.compactingArc,
        target,
        KitMotion.contextDrain,
      );
      _beforeCompaction = null;
    } else if (widget.compacting) {
      // Compacting: the number is not shown. The value before it began is
      // where the drain will start from; later values land quietly.
      if (!old.compacting) _beforeCompaction = current;
      _from = _to = target;
      _value.value = 1;
    } else if (target != before) {
      moveArc(
        current,
        target,
        target < before ? KitMotion.contextDrain : KitMotion.contextValue,
      );
      final crossed =
          (before < KitContextLevel.highFrom &&
              target >= KitContextLevel.highFrom) ||
          (before < KitContextLevel.fullFrom &&
              target >= KitContextLevel.fullFrom);
      if (crossed && !reduced) {
        _haloColor = _levelColor(target);
        _halo.forward(from: 0);
      }
    }

    final next = _levelColor(target);
    if (next != _colorTo) {
      _colorFrom = reduced ? next : colorNow;
      _colorTo = next;
      if (reduced) {
        _color.value = 1;
      } else {
        _color.forward(from: 0);
      }
    }
    _syncSpin();
  }

  @override
  void dispose() {
    _value.dispose();
    _color.dispose();
    _halo.dispose();
    _spin.dispose();
    super.dispose();
  }

  String _percent(BuildContext context, int whole) =>
      NumberFormat.decimalPattern(
        Localizations.localeOf(context).toString(),
      ).format(whole);

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final used = widget.used;
    if (used == null) return const SizedBox.shrink();
    final size = widget.size;
    if (size == KitContextRingSize.row) return _RowRing(used: used);
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final dims = size == KitContextRingSize.large ? _Dims.large : _Dims.bar;
    final scale = dims.box / _Dims.bar.box;
    final compacting = widget.compacting;

    final body = AnimatedBuilder(
      animation: Listenable.merge([_value, _color, _halo, _spin]),
      builder: (context, _) {
        final shownColor = _shownColor;
        final arc = _arc;
        final whole = KitContextLevel.percentOf(arc);
        final level = KitContextLevel.of(arc);
        final muted = level == KitContextLevel.normal;
        final label = compacting
            ? l10n.kitContextRingCompacting
            : l10n.kitModelContext(_percent(context, whole));
        final style =
            KitText.styleOf(
              context,
              size == KitContextRingSize.large
                  ? KitTextRole.label
                  : KitTextRole.caption,
            ).copyWith(
              color: compacting || muted ? roles.text2 : shownColor,
              fontFeatures: const [FontFeature.tabularFigures()],
            );
        final haloOut =
            _haloColor != null && _halo.value > 0 && _halo.value < 1;
        final ring = SizedBox.square(
          dimension: dims.box,
          child: Stack(
            clipBehavior: Clip.none,
            alignment: Alignment.center,
            children: [
              CustomPaint(
                key: widget.ringKey,
                size: Size.square(dims.box),
                painter: KitContextArcPainter(
                  arc: compacting ? KitContextRing.compactingArc : arc,
                  turns: compacting ? _spin.value : 0,
                  color: compacting ? roles.accent : shownColor,
                  track: roles.text3.withValues(alpha: 0.35),
                  stroke: dims.stroke,
                  radius: dims.radius,
                ),
              ),
              if (haloOut)
                CustomPaint(
                  key: KitContextRing.haloKey,
                  size: Size.square(dims.box),
                  painter: KitContextHaloPainter(
                    t: _halo.value,
                    color: _haloColor!,
                    radius: 11 * scale,
                    strokeFrom: 2 * scale,
                    strokeTo: 9 * scale,
                  ),
                ),
              if (size == KitContextRingSize.large)
                Center(child: Text(label, style: style, maxLines: 1)),
            ],
          ),
        );
        if (size == KitContextRingSize.large) return ring;
        return Row(
          mainAxisSize: MainAxisSize.min,
          spacing: tokens.space1,
          children: [
            ring,
            Text(label, style: style, maxLines: 1, softWrap: false),
          ],
        );
      },
    );

    final spoken = compacting
        ? l10n.kitContextRingCompactingSemantics
        : l10n.kitContextRingSemantics(
            _percent(context, KitContextLevel.percentOf(_clamp(used))),
          );
    if (size == KitContextRingSize.large || widget.openDetails == null) {
      return Semantics(
        label: spoken,
        container: true,
        excludeSemantics: true,
        child: body,
      );
    }
    return KitTappable(
      onTap: widget.openDetails,
      label: spoken,
      tooltip: l10n.kitContextRingTapHint,
      shape: KitShape.pill,
      child: Padding(
        padding: EdgeInsets.symmetric(horizontal: tokens.space2),
        child: body,
      ),
    );
  }
}

/// The list's quiet ring: no halo, no tap, the number only from 70 %.
class _RowRing extends StatelessWidget {
  const _RowRing({required this.used});

  final double used;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final reduced = KitMotion.reduced(context);
    final value = used.isNaN ? 0.0 : used.clamp(0.0, 1.0).toDouble();
    return TweenAnimationBuilder<double>(
      tween: Tween<double>(end: value),
      duration: reduced ? Duration.zero : KitMotion.contextValue,
      curve: KitMotion.enter,
      builder: (context, arc, _) {
        final level = KitContextLevel.of(arc);
        final color = level.colorIn(roles);
        final whole = KitContextLevel.percentOf(arc);
        final l10n = lookupAppLocalizations(Localizations.localeOf(context));
        return Row(
          mainAxisSize: MainAxisSize.min,
          spacing: tokens.space1,
          children: [
            SizedBox.square(
              dimension: _Dims.row.box,
              child: CustomPaint(
                painter: KitContextArcPainter(
                  arc: arc,
                  turns: 0,
                  color: color,
                  track: roles.text3.withValues(alpha: 0.35),
                  stroke: _Dims.row.stroke,
                  radius: _Dims.row.radius,
                ),
              ),
            ),
            if (level != KitContextLevel.normal)
              Text(
                l10n.kitModelContext(
                  NumberFormat.decimalPattern(
                    Localizations.localeOf(context).toString(),
                  ).format(whole),
                ),
                maxLines: 1,
                softWrap: false,
                style: KitText.styleOf(context, KitTextRole.caption).copyWith(
                  color: color,
                  fontFeatures: const [FontFeature.tabularFigures()],
                ),
              ),
          ],
        );
      },
    );
  }
}

/// Paints the track and the arc: from 12 o'clock, clockwise, round caps.
/// [arc] is the share of the circle; [turns] rotates it (the compacting
/// spin). Public so a test can read what was drawn.
class KitContextArcPainter extends CustomPainter {
  const KitContextArcPainter({
    required this.arc,
    required this.turns,
    required this.color,
    required this.track,
    required this.stroke,
    required this.radius,
  });

  final double arc;
  final double turns;
  final Color color;
  final Color track;
  final double stroke;
  final double radius;

  @override
  void paint(Canvas canvas, Size size) {
    final center = size.center(Offset.zero);
    final rect = Rect.fromCircle(center: center, radius: radius);
    final base = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = stroke
      ..strokeCap = StrokeCap.round
      ..isAntiAlias = true;
    canvas.drawCircle(center, radius, base..color = track);
    if (arc <= 0.003) return;
    canvas.drawArc(
      rect,
      -math.pi / 2 + turns * 2 * math.pi,
      arc.clamp(0.0, 1.0) * 2 * math.pi,
      false,
      base..color = color,
    );
  }

  @override
  bool shouldRepaint(KitContextArcPainter old) =>
      old.arc != arc ||
      old.turns != turns ||
      old.color != color ||
      old.track != track ||
      old.stroke != stroke ||
      old.radius != radius;
}

/// The one halo sent out when 70 % or 95 % is crossed: a circle whose
/// stroke grows while it fades (opacity 0.6 to 0). No scale transform.
class KitContextHaloPainter extends CustomPainter {
  const KitContextHaloPainter({
    required this.t,
    required this.color,
    required this.radius,
    required this.strokeFrom,
    required this.strokeTo,
  });

  final double t;
  final Color color;
  final double radius;
  final double strokeFrom;
  final double strokeTo;

  @override
  void paint(Canvas canvas, Size size) {
    final eased = KitMotion.enter.transform(t.clamp(0.0, 1.0));
    canvas.drawCircle(
      size.center(Offset.zero),
      radius,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = strokeFrom + (strokeTo - strokeFrom) * eased
        ..color = color.withValues(alpha: 0.6 * (1 - eased)),
    );
  }

  @override
  bool shouldRepaint(KitContextHaloPainter old) =>
      old.t != t || old.color != color;
}
