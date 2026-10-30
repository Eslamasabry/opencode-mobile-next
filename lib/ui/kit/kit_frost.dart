import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/widgets.dart';

import '../../state/effects.dart';
import '../../state/glass_choice.dart';
import 'kit_motion.dart';
import 'kit_tokens.dart';
import 'style/kit_style.dart' show KitStyles;

export '../../state/glass_choice.dart' show KitGlassChoice, GlassChoiceStore;

/// Frosted glass the ColorOS / HarmonyOS way (owner, 2026-10-30): the
/// content behind is blurred and a little more saturated, a surface-colour
/// tint lies over it, a faint noise keeps the tint from looking like plastic,
/// and a hairline catches the light along the top. No refraction, no rim
/// lighting, no liquid look (the old shader was "very artificial").
///
/// Exactly two places use it, and only while the conversation is scrolled
/// under them: the chat's floating composer (`KitComposer.layer`, its chip
/// row and field as one pane) and the chat's header (`KitScreen`'s bar).
/// At rest both are solid. Study: docs in
/// concepts-2026-10-30/glass/NOTES.md, the "balanced" recipe.
///
/// [KitFrost.strengthOf] says whether glass shows here at all, and how
/// strong; null means solid.
enum KitFrostStrength {
  /// CSS blur 12 px, saturation 1.2, tint 62% (header 72%).
  weak(sigma: 6, saturation: 1.2, tint: .62, headerTint: .72),

  /// CSS blur 20 px, saturation 1.4, tint 70% (header 80%). The default.
  balanced(sigma: 10, saturation: 1.4, tint: .70, headerTint: .80),

  /// CSS blur 32 px, saturation 1.6, tint 76% (header 85%, its cap).
  strong(sigma: 16, saturation: 1.6, tint: .76, headerTint: .85);

  const KitFrostStrength({
    required this.sigma,
    required this.saturation,
    required this.tint,
    required this.headerTint,
  });

  /// The blur's sigma in logical pixels (a CSS blur radius is about twice
  /// this).
  final double sigma;

  /// How much more colourful the blurred content gets (1 keeps it).
  final double saturation;

  /// How much of the surface colour lies over the composer's blur.
  final double tint;

  /// The header's: a little more than the composer's, since the title sits
  /// on it and dark code blocks otherwise smoke through as a grey band.
  final double headerTint;
}

/// Why glass is held solid whatever the person chose ([KitFrost.heldBy]).
enum KitFrostHold { saver, motion, contrast, style }

/// Which of the two glass places a [KitFrost] is.
enum KitFrostRole {
  /// The composer pane: rounded, a white hairline along its top and a faint
  /// ink edge all round. Clear at rest.
  composer,

  /// The header band: square, a faint ink line along its bottom. The page
  /// ground at rest.
  header,
}

/// The composer pane's glass, handed to the parts inside it ([KitFrost.wellOf]).
class _KitFrostZone extends InheritedWidget {
  const _KitFrostZone({required this.amount, required super.child});

  final Animation<double> amount;

  @override
  bool updateShouldNotify(_KitFrostZone oldWidget) =>
      oldWidget.amount != amount;
}

/// A surface that is glass while [shown] and [strength] allow it, and
/// solid otherwise, fading between the two over [KitFrost.fade]. The
/// glass is painted behind [child] and never clips it (the composer's
/// glow runs outside its pane); [child] keeps its place and state through
/// every change.
///
/// [shown] is the caller's "content is under me right now": the blur runs
/// only then, never over empty ground. [paneInsets] places the glass
/// relative to [child]'s box (negative values run it out past the box): the
/// header's glass runs up through the status bar; the composer's pane
/// stands one space2 around the composer's width, its chip row on it.
///
/// Solid means no blur layer in the tree at all.
///
/// States: none — a surface around its child, it holds no data.
class KitFrost extends StatefulWidget {
  const KitFrost({
    super.key,
    required this.role,
    required this.strength,
    required this.shown,
    required this.tint,
    required this.child,
    this.borderRadius = BorderRadius.zero,
    this.paneInsets = EdgeInsets.zero,
    this.edges = true,
  });

  final KitFrostRole role;

  /// Null: solid, whatever [shown] says.
  final KitFrostStrength? strength;
  final bool shown;

  /// The surface colour of the tint (the composer's surface, the page
  /// ground for the header); the strength's alpha is applied to it.
  final Color tint;
  final BorderRadius borderRadius;
  final EdgeInsets paneInsets;

  /// The glass's own hairlines (the light along the top, the ink edge).
  /// False when the child draws its own outline on the same shape (the
  /// composer's field standing alone), so there is never a second ring.
  final bool edges;
  final Widget child;

  @override
  State<KitFrost> createState() => _KitFrostState();

  // ── The rules: which strength shows where, and the recipe's numbers ──

  /// The style hook (owned with this file; the Signal style's slice adds
  /// itself here): whether a Style lets the two places turn to glass. Solid
  /// and Signal do; Soft, Brutal and Paper keep their own floating look.
  /// By name, so a style added to [KitSurfaceStyle] later needs only its
  /// name in this set.
  static bool styleAllows(KitSurfaceStyle style) =>
      _frostStyles.contains(style.name);

  static const _frostStyles = {'solid', 'signal'};

  /// The noise over the tint.
  static const double noiseOpacity = .03;

  /// The composer's top hairline: white at this alpha.
  static const double highlightAlpha = .20;

  /// The composer's edge all round, ink at this alpha.
  static const double edgeAlpha = .10;

  /// The header's bottom line, ink at this alpha.
  static const double headerEdgeAlpha = .12;

  /// The message field inside the composer pane stays denser than the pane
  /// (an inner well): its surface at this alpha.
  static const double wellAlpha = .80;

  /// How much less tint dark mode lays over the blur than light.
  static const double darkTintRelief = .10;

  /// Glass fading in or out as content moves under or away.
  static const Duration fade = Duration(milliseconds: 180);

  /// The strength in force for these conditions; null for solid. Off when
  /// the person chose Off, under Saver, with reduced motion (the system's
  /// or the app's Animations Off), under high contrast, and under a style
  /// that keeps its own look ([styleAllows]).
  static KitFrostStrength? strengthFor({
    required KitGlassChoice choice,
    required KitSurfaceStyle style,
    bool saver = false,
    bool reducedMotion = false,
    bool highContrast = false,
  }) {
    if (saver || reducedMotion || highContrast) return null;
    if (!styleAllows(style)) return null;
    return switch (choice) {
      KitGlassChoice.off => null,
      KitGlassChoice.weak => KitFrostStrength.weak,
      KitGlassChoice.balanced => KitFrostStrength.balanced,
      KitGlassChoice.strong => KitFrostStrength.strong,
    };
  }

  /// [strengthFor] here: the stored choice ([GlassChoiceStore.choice]; the
  /// caller rebuilds when it changes, e.g. under a ValueListenableBuilder)
  /// and the context's effects, motion, contrast and style.
  static KitFrostStrength? strengthOf(BuildContext context) {
    final effects = KitEffects.of(context);
    return strengthFor(
      choice: GlassChoiceStore.choice.value,
      style: KitStyles.styleOf(context),
      saver: effects.saver,
      reducedMotion: KitMotion.reduced(context),
      highContrast: MediaQuery.maybeHighContrastOf(context) ?? false,
    );
  }

  /// What holds glass solid here regardless of the choice (Settings says
  /// why), or null when the choice applies.
  static KitFrostHold? heldBy(BuildContext context) {
    if (KitEffects.of(context).saver) return KitFrostHold.saver;
    if (KitMotion.reduced(context)) return KitFrostHold.motion;
    if (MediaQuery.maybeHighContrastOf(context) ?? false) {
      return KitFrostHold.contrast;
    }
    if (!styleAllows(KitStyles.styleOf(context))) return KitFrostHold.style;
    return null;
  }

  /// Starts the one read of the stored choice (it lands on
  /// [GlassChoiceStore.choice]). A chat's composer layer and the Settings
  /// choice start it; other pages never touch the store.
  static void unawaitedLoad() {
    GlassChoiceStore.ensureLoaded().ignore();
  }

  /// The colour matrix that multiplies saturation by [s] (Rec. 709 luma).
  static List<double> saturationMatrix(double s) {
    const r = .2126, g = .7152, b = .0722;
    final i = 1 - s;
    return [
      r * i + s, g * i, b * i, 0, 0, //
      r * i, g * i + s, b * i, 0, 0, //
      r * i, g * i, b * i + s, 0, 0, //
      0, 0, 0, 1, 0, //
    ];
  }

  /// Saturation over blur, as the study's `blur() saturate()`.
  static ui.ImageFilter filterFor(KitFrostStrength strength) =>
      ui.ImageFilter.compose(
        outer: ui.ColorFilter.matrix(saturationMatrix(strength.saturation)),
        inner: ui.ImageFilter.blur(
          sigmaX: strength.sigma,
          sigmaY: strength.sigma,
          tileMode: TileMode.clamp,
        ),
      );

  /// A 64 px tile of black specks of random strength, made once (seeded,
  /// so goldens are stable) and repeated under the tint at [noiseOpacity].
  static ui.Image get noiseTile => _noise ??= _makeNoise();
  static ui.Image? _noise;

  static ui.Image _makeNoise() {
    const size = 64;
    final random = math.Random(7);
    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);
    final paint = Paint();
    for (var y = 0; y < size; y++) {
      for (var x = 0; x < size; x++) {
        // A mask: only the alpha counts; the specks take their colour
        // from a role when painted (_TintPainter).
        paint.color = Color.from(
          alpha: random.nextDouble(),
          red: 1,
          green: 1,
          blue: 1,
        );
        canvas.drawRect(Rect.fromLTWH(x.toDouble(), y.toDouble(), 1, 1), paint);
      }
    }
    return recorder.endRecording().toImageSync(size, size);
  }

  /// How far the composer pane around this context has turned to glass (0
  /// solid → 1 glass), or null outside one. [KitGlass] reads it to become
  /// the pane's denser inner well.
  static Animation<double>? wellOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<_KitFrostZone>()?.amount;

  /// The composer pane's inner edge, as an inset from each side of the
  /// parts over the composer: a line of chips that scrolls sideways ends
  /// (and fades) there, never across the pane's edge. 0 outside the pane.
  static double lineInsetOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<_KitFrostLineInset>()?.value ??
      0;

  /// Publishes [value] as [lineInsetOf] to [child] (the composer layer).
  static Widget lineInset({required double value, required Widget child}) =>
      _KitFrostLineInset(value: value, child: child);
}

class _KitFrostLineInset extends InheritedWidget {
  const _KitFrostLineInset({required this.value, required super.child});

  final double value;

  @override
  bool updateShouldNotify(_KitFrostLineInset oldWidget) =>
      oldWidget.value != value;
}

class _KitFrostState extends State<KitFrost>
    with SingleTickerProviderStateMixin {
  late final AnimationController _amount = AnimationController(
    vsync: this,
    duration: KitFrost.fade,
    value: _on ? 1 : 0,
  );

  /// The last strength that showed, kept while the glass fades out.
  KitFrostStrength? _last;

  bool get _on => widget.strength != null && widget.shown;

  @override
  void initState() {
    super.initState();
    _last = widget.strength;
  }

  @override
  void didUpdateWidget(KitFrost oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.strength != null) _last = widget.strength;
    if (widget.strength == null) {
      // Turned off (Saver, reduced motion, Off): solid at once.
      _amount.value = 0;
    } else if (_on) {
      _amount.forward();
    } else {
      _amount.reverse();
    }
  }

  @override
  void dispose() {
    _amount.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final header = widget.role == KitFrostRole.header;
    final child = header
        ? widget.child
        : _KitFrostZone(amount: _amount, child: widget.child);
    return Stack(
      clipBehavior: Clip.none,
      children: [
        // Three fixed slots, so the child never moves in the tree.
        PositionedDirectional(
          start: widget.paneInsets.left,
          end: widget.paneInsets.right,
          top: widget.paneInsets.top,
          bottom: widget.paneInsets.bottom,
          child: AnimatedBuilder(
            animation: _amount,
            builder: (context, _) => _backdrop(context, _amount.value),
          ),
        ),
        child,
        PositionedDirectional(
          start: widget.paneInsets.left,
          end: widget.paneInsets.right,
          top: widget.paneInsets.top,
          bottom: widget.paneInsets.bottom,
          child: IgnorePointer(
            child: AnimatedBuilder(
              animation: _amount,
              builder: (context, _) => _edges(context, _amount.value),
            ),
          ),
        ),
      ],
    );
  }

  Widget _backdrop(BuildContext context, double t) {
    final strength = _last;
    // Solid: no blur layer at all (fading in from 0 already has one).
    if (_amount.status == AnimationStatus.dismissed || strength == null) {
      return const SizedBox.shrink();
    }
    final header = widget.role == KitFrostRole.header;
    // The header leaves the page's own ground (opaque) for the glass; the
    // composer's pane, clear at rest, takes its tint on.
    // Dark lets a little more through, so the shapes behind read as
    // blurred shapes, not a flat grey box (coordinator review 2026-10-30).
    final dark = KitTokens.of(context).roles.isDark;
    final tint = strength.tint - (dark ? KitFrost.darkTintRelief : 0);
    final headerTint =
        strength.headerTint - (dark ? KitFrost.darkTintRelief : 0);
    final alpha = header ? ui.lerpDouble(1, headerTint, t)! : tint * t;
    return ClipRRect(
      borderRadius: widget.borderRadius,
      child: BackdropFilter.grouped(
        filter: KitFrost.filterFor(strength),
        child: CustomPaint(
          painter: _TintPainter(
            tint: widget.tint.withValues(alpha: alpha),
            // Black in both brightnesses (the elevation shadow role).
            specks: KitTokens.of(context).roles.elevationShadow.withValues(
              alpha: KitFrost.noiseOpacity * t,
            ),
          ),
          child: const SizedBox.expand(),
        ),
      ),
    );
  }

  Widget _edges(BuildContext context, double t) {
    if (_amount.status == AnimationStatus.dismissed ||
        _last == null ||
        !widget.edges) {
      return const SizedBox.shrink();
    }
    final roles = KitTokens.of(context).roles;
    return CustomPaint(
      painter: _EdgePainter(
        role: widget.role,
        radius: widget.borderRadius,
        ink: roles.text1,
        // The light along the top, kept visible in dark too: the light
        // surface in light, the light ink in dark.
        light: roles.isDark ? roles.text1 : roles.surface1,
        t: t,
        width: KitTokens.hairlineWidth(context),
      ),
    );
  }
}

class _TintPainter extends CustomPainter {
  const _TintPainter({required this.tint, required this.specks});

  final Color tint;

  /// The noise's colour, its alpha the noise's strength.
  final Color specks;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;
    canvas.drawRect(rect, Paint()..color = tint);
    if (specks.a <= 0) return;
    canvas.drawRect(
      rect,
      Paint()
        ..colorFilter = ColorFilter.mode(specks, BlendMode.srcIn)
        ..shader = ImageShader(
          KitFrost.noiseTile,
          TileMode.repeated,
          TileMode.repeated,
          Matrix4.identity().storage,
        ),
    );
  }

  @override
  bool shouldRepaint(_TintPainter old) =>
      old.tint != tint || old.specks != specks;
}

class _EdgePainter extends CustomPainter {
  const _EdgePainter({
    required this.role,
    required this.radius,
    required this.ink,
    required this.light,
    required this.t,
    required this.width,
  });

  final KitFrostRole role;
  final BorderRadius radius;
  final Color ink;
  final Color light;
  final double t;
  final double width;

  @override
  void paint(Canvas canvas, Size size) {
    final line = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = width;
    if (role == KitFrostRole.header) {
      line.color = ink.withValues(alpha: KitFrost.headerEdgeAlpha * t);
      final y = size.height - width / 2;
      canvas.drawLine(Offset(0, y), Offset(size.width, y), line);
      return;
    }
    final outline = radius.toRRect(Offset.zero & size).deflate(width / 2);
    line.color = ink.withValues(alpha: KitFrost.edgeAlpha * t);
    canvas.drawRRect(outline, line);
    // The light along the top: the outline's upper part only.
    canvas.save();
    canvas.clipRect(
      Rect.fromLTWH(0, 0, size.width, math.max(radius.topLeft.y, width) + 1),
    );
    canvas.drawRRect(
      outline.deflate(width),
      line..color = light.withValues(alpha: KitFrost.highlightAlpha * t),
    );
    canvas.restore();
  }

  @override
  bool shouldRepaint(_EdgePainter old) =>
      old.t != t ||
      old.ink != ink ||
      old.light != light ||
      old.radius != radius ||
      old.role != role ||
      old.width != width;
}
