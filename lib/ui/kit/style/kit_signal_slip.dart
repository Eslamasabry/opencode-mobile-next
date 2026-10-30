// Signal's printed slip (a request the agent asks: "Run git push origin
// main?") and the Style picker's sample of the look. Part of the Signal
// style's instruments (kit_signal.dart).
import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../theme_packs_signal.dart';
import '../../theme_roles.dart';
import '../kit_surface_look.dart';
import '../kit_text.dart';
import '../kit_tokens.dart';
import 'kit_signal.dart';
import 'kit_style_signal.dart';
import 'kit_style_theme.dart';

/// Marks what sits on a printed slip: its answers are raised keys, the
/// quiet one (Reject) as well as the main one (Allow). Anywhere else under
/// Signal only an action with a fill of its own is a key.
bool kitSignalInSlip(BuildContext context) =>
    context.dependOnInheritedWidgetOfExactType<_SlipScope>() != null;

class _SlipScope extends InheritedWidget {
  const _SlipScope({required super.child});

  @override
  bool updateShouldNotify(_SlipScope oldWidget) => false;
}

/// A request as a printed slip coming out of a printer slot: the slot's
/// dark bar across the top, the slip in its paper with a fine edge down
/// the sides, and a perforated bottom where it tears off. No ring, no
/// tint: the slip and the red lamp say it needs you. [focused] draws the
/// keyboard focus border round the slip. [key] names the slip's face.
/// [endsWithTap]: the slip ends in a quiet act, whose target already
/// leaves air under its words.
Widget kitSignalSlip(
  BuildContext context, {
  required Widget child,
  Key? key,
  bool focused = false,
  bool endsWithTap = false,
}) {
  final tokens = KitTokens.of(context);
  final colors = kitSignalColors(context);
  // A quiet act last ("Always allow sleep") brings its own target's air:
  // the perforation follows right under it.
  final bottom = (endsWithTap ? 0.0 : tokens.space4) + _SlipEdge.depth;
  final focus = focused
      ? Border.all(
          color: tokens.roles.accent,
          width: KitTokens.focusRingWidth(context),
        )
      : null;
  final face = DecoratedBox(
    key: key,
    decoration: BoxDecoration(color: colors.slip),
    position: DecorationPosition.background,
    child: CustomPaint(
      // The paper's fine edge down the sides and across the top.
      foregroundPainter: _SlipSides(colors.slipEdge),
      child: DecoratedBox(
        position: DecorationPosition.foreground,
        decoration: BoxDecoration(border: focus),
        child: Padding(
          padding: EdgeInsetsDirectional.fromSTEB(
            tokens.space4,
            tokens.space3,
            tokens.space4,
            bottom,
          ),
          child: _SlipScope(child: child),
        ),
      ),
    ),
  );
  return Column(
    mainAxisSize: MainAxisSize.min,
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      // The printer's slot, a little wider than the slip it feeds.
      ExcludeSemantics(
        child: LayoutBuilder(
          builder: (context, constraints) => SizedBox(
            height: _slotHeight,
            child: OverflowBox(
              minWidth: constraints.maxWidth + 8,
              maxWidth: constraints.maxWidth + 8,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: colors.head,
                  borderRadius: BorderRadius.circular(_slotHeight),
                  boxShadow: colors.dark
                      ? [
                          BoxShadow(
                            color: colors.slipEdge,
                            offset: const Offset(0, 1),
                          ),
                        ]
                      : null,
                ),
                child: const SizedBox.expand(),
              ),
            ),
          ),
        ),
      ),
      ClipPath(clipper: const _SlipEdge(), child: face),
    ],
  );
}

/// The printer slot's bar: its thickness, and its overhang past the slip.
const double _slotHeight = 5;

/// The slip's fine edge: one logical pixel down both sides and across the
/// top (the bottom is torn off).
class _SlipSides extends CustomPainter {
  const _SlipSides(this.color);

  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()..color = color;
    const w = 1.0;
    canvas
      ..drawRect(Rect.fromLTWH(0, 0, size.width, w), paint)
      ..drawRect(Rect.fromLTWH(0, 0, w, size.height), paint)
      ..drawRect(Rect.fromLTWH(size.width - w, 0, w, size.height), paint);
  }

  @override
  bool shouldRepaint(_SlipSides old) => old.color != color;
}

/// The torn-off bottom: half-round bites every 10 dp along the bottom
/// edge.
class _SlipEdge extends CustomClipper<Path> {
  const _SlipEdge();

  static const double depth = 6;
  static const double bite = 4;
  static const double pitch = 10;

  @override
  Path getClip(Size size) {
    final body = Path()..addRect(Offset.zero & size);
    final bites = Path();
    final count = (size.width / pitch).ceil();
    final start = (size.width - count * pitch) / 2;
    for (var i = 0; i < count; i++) {
      bites.addOval(
        Rect.fromCircle(
          center: Offset(start + pitch * (i + .5), size.height),
          radius: bite,
        ),
      );
    }
    return Path.combine(PathOperation.difference, body, bites);
  }

  @override
  bool shouldReclip(_SlipEdge oldClipper) => false;
}

/// The Style picker's sample of Signal, drawn whatever style is in force:
/// the enamel, a stretch of track with a red lamp (needs you), a green one
/// (running) and a hollow ring on the dashed siding (done), each beside a
/// line of words, and a raised key.
Widget kitSignalSample({required double height, double radius = 4}) =>
    _SignalSample(height: height, radius: radius);

class _SignalSample extends StatelessWidget {
  const _SignalSample({required this.height, this.radius = 4});

  final double height;
  final double radius;

  @override
  Widget build(BuildContext context) {
    // The theme as Signal makes it, whatever style is in force.
    final restyled = ThemeRoles.resolve(
      kitStyleRestyled(Theme.of(context), KitSurfaceStyle.signal),
    );
    final palette = SignalPalette.forRoles(restyled);
    final rtl = Directionality.of(context) == TextDirection.rtl;
    // The main action's key, from Signal's own resolver.
    final key = KitSurfaceLook.resolve(
      roles: restyled,
      style: KitSurfaceStyle.signal,
      role: KitSurfaceRole.control,
      color: restyled.accent,
      knobs: const KitStyleSignal().tokens.defaults,
      borderRadius: BorderRadius.circular(KitTrack.lcdRadius),
      solid: BoxDecoration(
        color: restyled.accent,
        borderRadius: BorderRadius.circular(KitTrack.lcdRadius),
      ),
    );
    return SizedBox(
      key: const ValueKey('kit-style-sample-signal'),
      height: height,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(radius),
        child: Stack(
          fit: StackFit.expand,
          children: [
            CustomPaint(
              painter: _SamplePainter(
                palette: palette,
                ink: restyled.text1.withValues(alpha: 1),
                quiet: restyled.text2.withValues(alpha: 1),
                ground: restyled.ground,
                rtl: rtl,
              ),
            ),
            PositionedDirectional(
              end: 8,
              top: height * .22 - 8,
              width: 34,
              height: 14,
              child: DecoratedBox(decoration: key),
            ),
          ],
        ),
      ),
    );
  }
}

class _SamplePainter extends CustomPainter {
  const _SamplePainter({
    required this.palette,
    required this.ink,
    required this.quiet,
    required this.ground,
    required this.rtl,
  });

  final SignalPalette palette;
  final Color ink;
  final Color quiet;
  final Color ground;
  final bool rtl;

  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawRect(Offset.zero & size, Paint()..color = ground);
    double x(double v) => rtl ? size.width - v : v;
    final track = 18.0;
    final rows = [size.height * .22, size.height * .5, size.height * .78];
    // Live track down to the running lamp, then the dashed siding.
    kitPaintTrack(
      canvas,
      colors: palette,
      x: x(track),
      top: 0,
      bottom: rows[1],
      live: true,
    );
    kitPaintTrack(
      canvas,
      colors: palette,
      x: x(track),
      top: rows[1],
      bottom: size.height,
      live: false,
    );
    void head(double y, Color lamp) {
      const w = 10.0, h = 16.0;
      canvas.drawRRect(
        RRect.fromRectAndRadius(
          Rect.fromCenter(center: Offset(x(track), y), width: w, height: h),
          const Radius.circular(w / 2),
        ),
        Paint()..color = palette.head,
      );
      canvas.drawCircle(
        Offset(x(track), y),
        4.5,
        Paint()..color = lamp.withValues(alpha: .3),
      );
      canvas.drawCircle(Offset(x(track), y), 3, Paint()..color = lamp);
    }

    head(rows[0], SignalPalette.lampRed);
    head(rows[1], SignalPalette.lampGreen);
    canvas.drawCircle(Offset(x(track), rows[2]), 4.5, Paint()..color = ground);
    canvas.drawCircle(
      Offset(x(track), rows[2]),
      4,
      Paint()
        ..color = quiet
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.6,
    );
    // A line of words beside each signal, shorter as the list goes on.
    final words = Paint()..color = ink.withValues(alpha: .8);
    final start = track + 14;
    const keyWidth = 34.0;
    for (var i = 0; i < rows.length; i++) {
      final end = size.width - keyWidth - 16 - i * 10;
      final left = math.min(start, end);
      final rect = Rect.fromLTRB(
        x(rtl ? end : left),
        rows[i] - 2,
        x(rtl ? left : end),
        rows[i] + 2,
      );
      canvas.drawRRect(
        RRect.fromRectAndRadius(
          Rect.fromLTRB(
            math.min(rect.left, rect.right),
            rect.top,
            math.max(rect.left, rect.right),
            rect.bottom,
          ),
          const Radius.circular(KitTrack.sidingStroke),
        ),
        words,
      );
    }
  }

  @override
  bool shouldRepaint(_SamplePainter old) =>
      old.palette != palette ||
      old.ink != ink ||
      old.quiet != quiet ||
      old.ground != ground ||
      old.rtl != rtl;
}

/// "Needs you" as Signal engraves it: small capitals in the signal's red
/// ink, widely tracked ([text] as written for a screen reader).
Widget kitSignalNeedsYou(
  BuildContext context,
  String text, {
  bool waiting = true,
}) {
  final colors = kitSignalColors(context);
  final style = KitText.styleOf(context, KitTextRole.caption).copyWith(
    fontWeight: FontWeight.w600,
    letterSpacing: 12 * .12,
    color: waiting ? colors.alertInk : KitTokens.of(context).roles.text2,
    shadows: const [],
  );
  return Semantics(
    label: text,
    excludeSemantics: true,
    child: Text(text.toUpperCase(), style: style),
  );
}
