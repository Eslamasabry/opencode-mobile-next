import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import '../theme_roles.dart';
import 'kit_effects.dart';
import 'kit_motion.dart';
import 'kit_tokens.dart';

/// The app's drawings (design standard §10): line art in the brand's
/// stroke — rounded caps and joins, the "open portal" mark's weight — drawn
/// in code, so it follows the theme (dark and light), scales without
/// assets, and can move.
///
/// A drawing is a [KitScene]: it paints one frame from a [KitSceneFrame]
/// (how far its entrance is, where its ambient loop is, the palette).
/// [KitIllustration] owns the time: it plays the entrance once, then, when
/// asked ([KitIllustration.ambient]) and allowed ([KitMotion.loopsIn]),
/// repeats the loop. With reduced motion it shows the finished drawing.
///
/// Scenes draw in their own design box ([KitScene.box], 120 × 120 by
/// default); the widget scales it uniformly to its width and centres it.
abstract class KitScene {
  const KitScene();

  /// The design box the scene draws in, in scene units.
  Size get box => const Size(120, 120);

  /// Paints one frame into [box]-sized scene units.
  void paint(Canvas canvas, KitSceneFrame frame);

  /// Whether this scene draws differently from [old] at the same frame
  /// (a scene with data, such as a progress, compares its fields).
  bool differs(covariant KitScene old) => old.runtimeType != runtimeType;

  /// True for a drawing that shows progress along a line (steps, a relay, a
  /// link from one device to another): [KitIllustration] flips it
  /// horizontally under RTL (LAY-8: progress direction mirrors). Default
  /// false: devices, folders and marks are pictures and never mirror.
  bool get mirrorsInRtl => false;
}

/// The colours a scene draws with: always from the theme, never literals.
@immutable
class KitPalette {
  const KitPalette({
    required this.accent,
    required this.accentSoft,
    required this.ink,
    required this.muted,
    required this.line,
    required this.surface,
    required this.success,
    required this.warning,
    required this.failure,
    required this.progress,
  });

  /// The palette of [roles] (design standard §10; KitScenes.md's one
  /// mapping): every colour a scene paints comes from a [ThemeRoles] role,
  /// so a drawing follows every theme pack with the same meanings as the
  /// rest of the app — the accent for what matters, no red for failures
  /// (B2 interim: [failure] is [ThemeRoles.text1], said in words and a
  /// neutral mark, LOOK-5), amber only for "needs you" ([warning] is
  /// [ThemeRoles.attention]; no scene uses it after v2), flat fills at no
  /// more than [KitTokens.sceneWashAlpha] ([accentSoft], LOOK-36).
  factory KitPalette.fromRoles(ThemeRoles roles) => KitPalette(
    accent: roles.accent,
    accentSoft: roles.accent.withValues(alpha: KitTokens.sceneWashAlpha),
    ink: roles.text1,
    muted: roles.text2,
    line: roles.text3,
    surface: roles.surface3,
    success: roles.success,
    warning: roles.attention,
    failure: roles.text1,
    progress: roles.accent,
  );

  factory KitPalette.of(ThemeData theme) =>
      KitPalette.fromRoles(ThemeRoles.resolve(theme));

  /// The brand colour: the one thing in a scene that matters most.
  final Color accent;

  /// The brand colour as a wash behind shapes.
  final Color accentSoft;

  /// Text colour, for the few strong outlines.
  final Color ink;

  /// Secondary outlines.
  final Color muted;

  /// Hairlines and the quietest detail.
  final Color line;

  /// Filled shapes (a phone's screen, a card).
  final Color surface;

  final Color success;
  final Color warning;
  final Color failure;
  final Color progress;

  @override
  bool operator ==(Object other) =>
      other is KitPalette &&
      other.accent == accent &&
      other.accentSoft == accentSoft &&
      other.ink == ink &&
      other.muted == muted &&
      other.line == line &&
      other.surface == surface &&
      other.success == success &&
      other.warning == warning &&
      other.failure == failure &&
      other.progress == progress;

  @override
  int get hashCode => Object.hash(
    accent,
    accentSoft,
    ink,
    muted,
    line,
    surface,
    success,
    warning,
    failure,
    progress,
  );
}

/// One frame of a scene.
@immutable
class KitSceneFrame {
  const KitSceneFrame({
    required this.entrance,
    required this.loop,
    required this.looping,
    required this.palette,
  });

  /// 0 → 1 over [KitMotion.entrance], linear; 1 when finished or reduced.
  /// Scenes stage their parts with [KitDraw.interval].
  final double entrance;

  /// 0 → 1, repeating once per loop period while [looping]; 0 otherwise.
  final double loop;

  /// Whether the ambient loop runs. A scene drawn with `looping: false` must
  /// look complete and still at `loop: 0`.
  final bool looping;

  final KitPalette palette;
}

/// Drawing helpers every scene shares, so the line weight and the way
/// things draw themselves in are the same everywhere.
abstract final class KitDraw {
  /// The standard stroke in scene units (a 120-unit box).
  static const stroke = 5.0;

  /// A thin stroke for detail.
  static const hairline = 2.5;

  static Paint pen(Color color, [double width = stroke]) => Paint()
    ..style = PaintingStyle.stroke
    ..strokeCap = StrokeCap.round
    ..strokeJoin = StrokeJoin.round
    ..strokeWidth = width
    ..color = color
    ..isAntiAlias = true;

  static Paint fill(Color color) => Paint()
    ..style = PaintingStyle.fill
    ..color = color
    ..isAntiAlias = true;

  /// [t] of [begin]..[end], clamped and eased: stages a part of an entrance
  /// ("the second bracket draws from 25 % to 85 %").
  static double interval(
    double t,
    double begin,
    double end, [
    Curve curve = KitMotion.enter,
  ]) {
    if (t <= begin) return 0;
    if (t >= end) return 1;
    return curve.transform((t - begin) / (end - begin));
  }

  /// A smooth 0 → 1 → 0 wave over one loop, shifted by [phase] (0..1).
  static double wave(double loop, [double phase = 0]) =>
      (1 - math.cos((loop + phase) * 2 * math.pi)) / 2;

  /// Draws the first [t] (0..1) of [path]'s length: a line drawing itself.
  static void partialPath(Canvas canvas, Path path, double t, Paint paint) {
    if (t <= 0) return;
    if (t >= 1) {
      canvas.drawPath(path, paint);
      return;
    }
    final metrics = path.computeMetrics().toList();
    final total = metrics.fold<double>(0, (sum, m) => sum + m.length);
    var remaining = total * t;
    for (final metric in metrics) {
      if (remaining <= 0) break;
      final length = math.min(remaining, metric.length);
      canvas.drawPath(metric.extractPath(0, length), paint);
      remaining -= length;
    }
  }

  /// [color] at [opacity] of its own alpha.
  static Color fade(Color color, double opacity) =>
      color.withValues(alpha: color.a * opacity.clamp(0, 1));

  /// A flat accent fill (LOOK-36): [palette].accentSoft, optionally faded
  /// further by [opacity]; never above [KitTokens.sceneWashAlpha].
  static Paint wash(KitPalette palette, [double opacity = 1]) =>
      fill(fade(palette.accentSoft, opacity));
}

/// Shows a [KitScene] at [width], playing its entrance once and, when
/// [ambient] and allowed, its loop (design standard §10).
///
/// States: entrance, finished, ambient, reduced (decorative; not
/// interactive).
///
/// Decorative by default (screen readers skip it); give [semanticLabel]
/// only when the drawing says something the text around it does not.
class KitIllustration extends StatefulWidget {
  const KitIllustration({
    super.key,
    required this.scene,
    this.width = KitTokens.illustrationPage,
    this.ambient = false,
    this.loopPeriod = KitMotion.breath,
    this.animateEntrance = true,
    this.entranceDuration = KitMotion.entrance,
    this.semanticLabel,
  });

  final KitScene scene;
  final double width;

  /// Keep moving after the entrance: only on a screen where the person
  /// waits (connecting, installing, a team at work), never on a resting
  /// screen.
  final bool ambient;

  final Duration loopPeriod;

  /// False draws the finished drawing at once (a list rebuilt often).
  final bool animateEntrance;

  /// How long the entrance takes: [KitMotion.entrance] for a drawing,
  /// [KitMotion.celebration] for a finished moment worth marking.
  final Duration entranceDuration;

  final String? semanticLabel;

  @override
  State<KitIllustration> createState() => _KitIllustrationState();
}

class _KitIllustrationState extends State<KitIllustration>
    with TickerProviderStateMixin {
  late final AnimationController _entrance = AnimationController(
    vsync: this,
    duration: widget.entranceDuration,
  )..addStatusListener(_entranceStatus);
  late final AnimationController _loop = AnimationController(
    vsync: this,
    duration: widget.loopPeriod,
  );
  bool _started = false;

  /// A celebration (its entrance at least [KitMotion.celebration]) the
  /// person turned off in Settings › Appearance shows finished at once.
  bool get _skipCelebration =>
      widget.entranceDuration >= KitMotion.celebration &&
      !KitEffects.of(context).celebrations;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (KitMotion.reduced(context) || _skipCelebration) {
      _entrance.value = 1;
      _loop
        ..stop()
        ..value = 0;
      _started = true;
      return;
    }
    if (!_started) {
      _started = true;
      if (widget.animateEntrance) {
        _entrance.forward();
      } else {
        _entrance.value = 1;
        _syncLoop();
      }
    } else {
      _syncLoop();
    }
  }

  @override
  void didUpdateWidget(KitIllustration old) {
    super.didUpdateWidget(old);
    if (old.loopPeriod != widget.loopPeriod) {
      _loop.duration = widget.loopPeriod;
    }
    if (old.ambient != widget.ambient) _syncLoop();
  }

  void _entranceStatus(AnimationStatus status) {
    if (status == AnimationStatus.completed) _syncLoop();
  }

  void _syncLoop() {
    final run =
        widget.ambient &&
        _entrance.isCompleted &&
        mounted &&
        KitMotion.loopsIn(context);
    if (run && !_loop.isAnimating) {
      _loop.repeat();
    } else if (!run && _loop.isAnimating) {
      _loop
        ..stop()
        ..value = 0;
    }
  }

  @override
  void dispose() {
    _entrance.dispose();
    _loop.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final box = widget.scene.box;
    final height = widget.width * box.height / box.width;
    final mirror =
        widget.scene.mirrorsInRtl &&
        Directionality.of(context) == TextDirection.rtl;
    final painter = CustomPaint(
      size: Size(widget.width, height),
      painter: _ScenePainter(
        scene: widget.scene,
        entrance: _entrance,
        loop: _loop,
        palette: KitPalette.of(Theme.of(context)),
        mirror: mirror,
      ),
    );
    final drawing = RepaintBoundary(child: painter);
    final label = widget.semanticLabel;
    return label == null
        ? ExcludeSemantics(child: drawing)
        : Semantics(label: label, image: true, child: drawing);
  }
}

class _ScenePainter extends CustomPainter {
  _ScenePainter({
    required this.scene,
    required this.entrance,
    required this.loop,
    required this.palette,
    required this.mirror,
  }) : super(repaint: Listenable.merge([entrance, loop]));

  final KitScene scene;
  final Animation<double> entrance;
  final AnimationController loop;
  final KitPalette palette;

  /// True flips the scene horizontally about its own box (LAY-8): set only
  /// for [KitScene.mirrorsInRtl] scenes under RTL.
  final bool mirror;

  @override
  void paint(Canvas canvas, Size size) {
    final box = scene.box;
    final scale = math.min(size.width / box.width, size.height / box.height);
    canvas.save();
    canvas.translate(
      (size.width - box.width * scale) / 2,
      (size.height - box.height * scale) / 2,
    );
    canvas.scale(scale);
    if (mirror) {
      canvas.translate(box.width, 0);
      canvas.scale(-1, 1);
    }
    canvas.clipRect(ui.Offset.zero & box);
    scene.paint(
      canvas,
      KitSceneFrame(
        entrance: entrance.value,
        loop: loop.isAnimating ? loop.value : 0,
        looping: loop.isAnimating,
        palette: palette,
      ),
    );
    canvas.restore();
  }

  @override
  bool shouldRepaint(_ScenePainter old) =>
      // A slot can swap one kind of scene for another (the connecting card's
      // phone for the portal); a scene's differs() compares only its own kind.
      scene.runtimeType != old.scene.runtimeType ||
      scene.differs(old.scene) ||
      palette != old.palette ||
      mirror != old.mirror;
}
