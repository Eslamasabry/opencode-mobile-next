// Signal's instruments (owner, 2026-10-30; concept CD in
// docs/design/design-schools/schools/signal.md): the track, the signal
// heads, the segment timer, the raised keys and the start tick. Only the
// Signal style draws them; every part asks [KitTrack.on] first and keeps its
// own look elsewhere.
//
// The lamps are the one place the app shows red: the owner approved the
// railway signal (red = needs you, the Stop key) for this style only, so the
// colours live here and nowhere else (test/no_red_scan_test.dart allows this
// file and no other).
import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../theme_packs_signal.dart';
import '../kit_effects.dart';
import '../kit_motion.dart';
import '../kit_surface_look.dart';
import '../kit_text.dart';
import '../kit_tokens.dart';
import 'kit_style.dart';

/// Whether the style in force runs lists and the transcript on a track
/// ([KitStyleTokens.track]: Signal).
abstract final class KitTrack {
  static bool on(BuildContext context) =>
      kitStyleSpec(KitSurfaceLook.styleOf(context)).tokens.track;

  /// The track's distance from a list row's start edge: the middle of the
  /// lead slot (the gutter and half an icon tile), so a row's words start
  /// on the list's text line (56 dp on a page).
  static double rowCentre(KitTokens tokens) =>
      tokens.gutter + tokens.iconTileSize / 2;

  /// Where a row's words start on a track.
  static double rowText(KitTokens tokens) => tokens.gutter + tokens.leadColumn;

  /// The live track's stroke, and the dashed siding's.
  static const double stroke = 3;
  static const double sidingStroke = 2;
  static const double dash = 6;
  static const double gap = 4;

  /// A sleeper's length across the track.
  static const double sleeper = 12;

  /// The segment timer's window corners.
  static const double lcdRadius = 3;

  /// Where a request under a list row starts: on the row's words, less the
  /// ring's place a request card keeps (its printer slot reaches into it).
  static double slipStart(KitTokens tokens) =>
      rowText(tokens) - 2 * KitTokens.needsYouRingWidth;
}

/// What a signal head shows.
enum KitSignalAspect {
  /// Red: the work waits for the person.
  stop,

  /// Green: the work runs.
  go,

  /// Dark: nothing lit (a step that has not started).
  off,
}

/// Signal's fixed colours (lib/ui/theme_packs_signal.dart).
typedef KitSignalColors = SignalPalette;

/// The lamp's colour for an aspect.
extension KitSignalLamp on SignalPalette {
  Color lamp(KitSignalAspect aspect) => switch (aspect) {
    KitSignalAspect.stop => SignalPalette.lampRed,
    KitSignalAspect.go => SignalPalette.lampGreen,
    KitSignalAspect.off => lampOff,
  };
}

/// The palette for the roles in force.
SignalPalette kitSignalColors(BuildContext context) =>
    SignalPalette.forRoles(KitTokens.of(context).roles);

/// A raised hardware key's face (hook for every part that is an action
/// under Signal): [fill] with a 1 dp [edge] and a 3 dp lip under it in the
/// edge's colour, no blur; held, the lip is 1 dp (the key goes down 2 dp).
/// Straight down in both reading directions: a key is lit from above.
KitSurfaceDecoration kitSignalKey({
  required Color fill,
  required Color edge,
  required BorderRadius radius,
  bool circle = false,
  bool pressed = false,
}) => KitSurfaceDecoration(
  borderRadius: radius,
  circle: circle,
  fill: fill,
  edgeStart: edge,
  edgeEnd: edge,
  edgeWidth: 1,
  shadows: [BoxShadow(color: edge, offset: Offset(0, pressed ? 1 : 3))],
);

/// The colour a key's edge and lip take under [fill]: the fill gone
/// most of the way to black.
Color kitSignalKeyEdge(Color fill) {
  final hsl = HSLColor.fromColor(fill.withValues(alpha: 1));
  return hsl.withLightness(hsl.lightness * .5).toColor();
}

/// The red Stop key's face under Signal (for the part that draws Stop):
/// the signal's red with its dark edge and lip, white words.
KitSurfaceDecoration kitSignalStopKey(
  BuildContext context, {
  required BorderRadius radius,
  bool circle = false,
  bool pressed = false,
}) {
  final colors = kitSignalColors(context);
  return kitSignalKey(
    fill: colors.stopKey,
    edge: colors.stopEdge,
    radius: radius,
    circle: circle,
    pressed: pressed,
  );
}

/// A signal head: a dark rounded body with one lamp, [height] tall (the
/// body is 16 by 26 at the default). A lit lamp has a halo; under the
/// night palette it glows, and while [breathing] (the live lamp) the glow
/// breathes, still under reduced motion. Decoration: no semantics.
Widget kitSignalHead({
  Key? key,
  required KitSignalAspect aspect,
  double height = 26,
  bool breathing = false,
}) => _SignalHead(
  key: key ?? ValueKey('kit-signal-${aspect.name}'),
  aspect: aspect,
  height: height,
  breathing: breathing,
);

/// A signal head's width for [height].
double kitSignalHeadWidth(double height) => height * 16 / 26;

class _SignalHead extends StatefulWidget {
  const _SignalHead({
    super.key,
    required this.aspect,
    this.height = 26,
    this.breathing = false,
  });

  final KitSignalAspect aspect;
  final double height;
  final bool breathing;

  @override
  State<_SignalHead> createState() => _SignalHeadState();
}

class _SignalHeadState extends State<_SignalHead>
    with SingleTickerProviderStateMixin {
  AnimationController? _breath;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _sync();
  }

  @override
  void didUpdateWidget(_SignalHead old) {
    super.didUpdateWidget(old);
    _sync();
  }

  void _sync() {
    final moves =
        widget.breathing &&
        widget.aspect != KitSignalAspect.off &&
        KitMotion.loopsIn(context);
    if (moves) {
      final breath = _breath ??= AnimationController(
        vsync: this,
        duration: KitMotion.stepBreathe,
      );
      if (!breath.isAnimating) breath.repeat(reverse: true);
    } else {
      _breath?.stop();
    }
  }

  @override
  void dispose() {
    _breath?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = kitSignalColors(context);
    final height = widget.height;
    final width = kitSignalHeadWidth(height);
    final breath = _breath;
    final painter = breath == null
        ? _HeadPainter(colors: colors, aspect: widget.aspect)
        : _HeadPainter(colors: colors, aspect: widget.aspect, breath: breath);
    return ExcludeSemantics(
      child: SizedBox(
        width: width,
        height: height,
        child: RepaintBoundary(child: CustomPaint(painter: painter)),
      ),
    );
  }
}

class _HeadPainter extends CustomPainter {
  _HeadPainter({required this.colors, required this.aspect, this.breath})
    : super(repaint: breath);

  final KitSignalColors colors;
  final KitSignalAspect aspect;
  final Animation<double>? breath;

  @override
  void paint(Canvas canvas, Size size) {
    final body = RRect.fromRectAndRadius(
      Offset.zero & size,
      Radius.circular(size.width / 2),
    );
    canvas.drawRRect(body, Paint()..color = colors.head);
    final centre = size.center(Offset.zero);
    final r = size.width * 9 / 32;
    final lamp = colors.lamp(aspect);
    if (aspect != KitSignalAspect.off) {
      final strength = 0.75 + 0.25 * (breath?.value ?? 1);
      if (colors.dark) {
        canvas.drawCircle(
          centre,
          r + 2,
          Paint()
            ..color = lamp.withValues(alpha: .55 * strength)
            ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 4),
        );
      } else {
        canvas.drawCircle(
          centre,
          r + 2,
          Paint()..color = lamp.withValues(alpha: .25 * strength),
        );
      }
    }
    canvas.drawCircle(centre, r, Paint()..color = lamp);
  }

  @override
  bool shouldRepaint(_HeadPainter old) =>
      old.colors != colors || old.aspect != aspect || old.breath != breath;
}

/// A hollow ring on the track: a step or a conversation that is done.
Widget kitSignalRing({Key? key, double size = 12}) =>
    _SignalRing(key: key ?? const ValueKey('kit-signal-ring'), size: size);

class _SignalRing extends StatelessWidget {
  const _SignalRing({super.key, this.size = 12});

  final double size;

  @override
  Widget build(BuildContext context) {
    final roles = KitTokens.of(context).roles;
    return ExcludeSemantics(
      child: SizedBox.square(
        dimension: size,
        child: DecoratedBox(
          decoration: KitSurfaceDecoration(
            circle: true,
            fill: roles.ground,
            edgeStart: roles.text2,
            edgeEnd: roles.text2,
            edgeWidth: KitTrack.sidingStroke,
          ),
        ),
      ),
    );
  }
}

/// Paints the track down [centre] (from the start edge, mirrored in RTL),
/// from [top] to the bottom (or [bottom]): solid ink while [live], else the
/// dashed siding.
class KitTrackPainter extends CustomPainter {
  const KitTrackPainter({
    required this.colors,
    required this.centre,
    required this.rtl,
    this.live = true,
    this.top = 0,
    this.bottom,
  });

  final KitSignalColors colors;
  final double centre;
  final bool rtl;
  final bool live;
  final double top;
  final double? bottom;

  @override
  void paint(Canvas canvas, Size size) => kitPaintTrack(
    canvas,
    colors: colors,
    x: rtl ? size.width - centre : centre,
    top: top,
    bottom: math.min(bottom ?? size.height, size.height),
    live: live,
  );

  @override
  bool shouldRepaint(KitTrackPainter old) =>
      old.colors != colors ||
      old.centre != centre ||
      old.rtl != rtl ||
      old.live != live ||
      old.top != top ||
      old.bottom != bottom;
}

/// The track as one stroke at [x] from [top] to [bottom].
void kitPaintTrack(
  Canvas canvas, {
  required KitSignalColors colors,
  required double x,
  required double top,
  required double bottom,
  required bool live,
}) {
  if (bottom <= top) return;
  if (live) {
    const w = KitTrack.stroke;
    canvas.drawRect(
      Rect.fromLTRB(x - w / 2, top, x + w / 2, bottom),
      Paint()..color = colors.track,
    );
    return;
  }
  const w = KitTrack.sidingStroke;
  final paint = Paint()..color = colors.siding;
  for (var y = top; y < bottom; y += KitTrack.dash + KitTrack.gap) {
    canvas.drawRect(
      Rect.fromLTRB(
        x - w / 2,
        y,
        x + w / 2,
        math.min(y + KitTrack.dash, bottom),
      ),
      paint,
    );
  }
}

/// The dashed siding down a whole list group, as a sliver decoration
/// (`DecoratedSliver`): [centre] from the start edge.
class KitTrackDecoration extends Decoration {
  const KitTrackDecoration({required this.colors, required this.centre});

  final KitSignalColors colors;
  final double centre;

  @override
  BoxPainter createBoxPainter([VoidCallback? onChanged]) =>
      _TrackBoxPainter(this);

  @override
  bool operator ==(Object other) =>
      other is KitTrackDecoration &&
      other.colors == colors &&
      other.centre == centre;

  @override
  int get hashCode => Object.hash(colors, centre);
}

class _TrackBoxPainter extends BoxPainter {
  _TrackBoxPainter(this.decoration);

  final KitTrackDecoration decoration;

  @override
  void paint(Canvas canvas, Offset offset, ImageConfiguration configuration) {
    final size = configuration.size;
    if (size == null) return;
    final rtl = configuration.textDirection == TextDirection.rtl;
    final x = rtl ? size.width - decoration.centre : decoration.centre;
    kitPaintTrack(
      canvas,
      colors: decoration.colors,
      x: offset.dx + x,
      top: offset.dy,
      bottom: offset.dy + size.height,
      live: false,
    );
  }
}

/// The running time as a segment display: green digits in a dark window,
/// the unlit segments ("8") ghosted behind them, the same length as the
/// words so the window never jumps. Under the night palette the window
/// has a bezel line. [text] is what a screen reader hears; [size] is the
/// digits' size at 1.0 text.
Widget kitSegmentTimer(String text, {Key? key, double size = 15}) =>
    _SegmentTimer(text, key: key, size: size);

/// [text] with every digit unlit (the segment timer's ghost).
String kitSegmentGhost(String text) => text
    .replaceAll(RegExp(r'[0-9]'), '8')
    .replaceAll(RegExp('[\u0660-\u0669]'), '\u0668');

class _SegmentTimer extends StatelessWidget {
  const _SegmentTimer(this.text, {super.key, this.size = 15});

  final String text;

  /// The digits' size at 1.0 text.
  final double size;

  @override
  Widget build(BuildContext context) {
    final colors = kitSignalColors(context);
    final mono = KitText.styleOf(context, KitTextRole.mono).copyWith(
      fontSize: size,
      height: 1.2,
      fontWeight: FontWeight.w600,
      letterSpacing: size * .02,
      fontFeatures: const [FontFeature.tabularFigures()],
      shadows: const [],
    );
    Text line(String words, Color color) => Text(
      words,
      maxLines: 1,
      softWrap: false,
      textAlign: TextAlign.end,
      style: mono.copyWith(color: color),
    );
    return Semantics(
      label: text,
      excludeSemantics: true,
      child: DecoratedBox(
        decoration: KitSurfaceDecoration(
          fill: colors.lcd,
          borderRadius: BorderRadius.circular(KitTrack.lcdRadius),
          edgeStart: colors.lcdBezel,
          edgeEnd: colors.lcdBezel,
          edgeWidth: colors.lcdBezel.a > 0
              ? KitTokens.hairlineWidth(context)
              : 0,
        ),
        child: Padding(
          padding: EdgeInsetsDirectional.symmetric(
            horizontal: KitTokens.of(context).space2,
          ),
          child: Stack(
            alignment: AlignmentDirectional.centerEnd,
            children: [
              line(kitSegmentGhost(text), colors.lcdGhost),
              line(text, SignalPalette.lcdOn),
            ],
          ),
        ),
      ),
    );
  }
}

/// The sleepers beside the folded work line: a short stretch of track
/// crossed by four ties, [height] tall.
Widget kitSignalTies({Key? key, double width = 14, double height = 24}) =>
    _SignalTies(key: key, width: width, height: height);

class _SignalTies extends StatelessWidget {
  const _SignalTies({super.key, this.width = 14, this.height = 24});

  final double width;
  final double height;

  @override
  Widget build(BuildContext context) => ExcludeSemantics(
    child: SizedBox(
      width: width,
      height: height,
      child: CustomPaint(painter: _TiesPainter(kitSignalColors(context).track)),
    ),
  );
}

class _TiesPainter extends CustomPainter {
  const _TiesPainter(this.color);

  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()..color = color;
    final x = size.width / 2;
    canvas.drawRect(
      Rect.fromLTRB(
        x - KitTrack.stroke / 2,
        0,
        x + KitTrack.stroke / 2,
        size.height,
      ),
      paint,
    );
    const ties = 4;
    final step = size.height / ties;
    for (var i = 0; i < ties; i++) {
      final y = step * (i + .5);
      canvas.drawRect(Rect.fromLTRB(0, y - 1, size.width, y + 1), paint);
    }
  }

  @override
  bool shouldRepaint(_TiesPainter old) => old.color != color;
}

/// The buffer stop where a conversation's track begins: a short bar
/// across the track's top, [width] wide (for the transcript's first row).
Widget kitSignalStartTick({Key? key, double width = 20}) =>
    _SignalStartTick(key: key, width: width);

class _SignalStartTick extends StatelessWidget {
  const _SignalStartTick({super.key, this.width = 20});

  final double width;

  @override
  Widget build(BuildContext context) => ExcludeSemantics(
    child: SizedBox(
      width: width,
      height: KitTrack.stroke,
      child: ColoredBox(color: kitSignalColors(context).track),
    ),
  );
}

/// Whether a running lamp should breathe here: the person's Animations
/// allow a loop.
bool kitSignalBreathes(BuildContext context) =>
    KitMotion.loopsIn(context) &&
    KitEffects.of(context).motion == KitMotionLevel.full;
