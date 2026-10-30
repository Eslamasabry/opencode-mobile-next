import 'package:flutter/material.dart';

import '../../theme_packs_signal.dart';
import '../../theme_roles.dart';
import '../kit_effects.dart';
import '../kit_shape.dart' show KitSurfaceLevel;
import '../kit_surface_look.dart';
import 'kit_signal.dart';
import 'kit_style.dart';

/// Signal (owner, 2026-10-30; concept CD, docs/design/design-schools/
/// schools/signal.md): a railway signal box crossed with a hardware
/// console. Cream enamel and ink in light, a night signal box in dark (warm
/// near-black, not inverted). Lists and the transcript run on one track
/// ([KitStyleTokens.track]); signal lights are the only status colour.
///
/// Surfaces are flat print on the enamel: no cards per row, no gradients,
/// no glass on content. What you act on is either a flat ink outline (a
/// filter, a segment, a field) or, when it is an action with a fill of its
/// own (the primary, Allow, Send), a raised hardware key with a lip under
/// it ([kitSignalKey]). A chosen control is set in reverse, ink with
/// enamel words.
class KitStyleSignal extends KitStyleSpec {
  const KitStyleSignal();

  /// A flat control's ink outline (a filter, a field), in logical pixels.
  static const double outline = 1.5;

  /// The ink a chosen control is filled with.
  static Color invertedInk(ThemeRoles roles) =>
      roles.text1.withValues(alpha: 1);

  static bool _inverted(KitSurfaceRequest r) =>
      r.role == KitSurfaceRole.control &&
      r.selected &&
      r.color == null &&
      !r.circle;

  @override
  KitStyleTokens get tokens => const KitStyleTokens(
    defaults: KitKnobs(corners: KitCorners.sharp),
    // Keys and fields keep a small machined radius; the slip is square.
    sharpRadius: 4,
    // Ink on enamel is already high contrast.
    keepsUnderHighContrast: true,
    // A signal box does not drift: the live look is off when Signal is
    // chosen, still the person's switch. The running glow stays (owner:
    // the green glow around the message box is liked).
    live: KitLivePolicy(offWhenChosen: true),
    // A request is a printed slip ([kitRequestFrame]): no wash, no ring,
    // no start edge; the slip itself and the red lamp say it.
    needsYou: KitNeedsYouLook(tint: false, ring: false, startEdge: false),
    flatLists: true,
    track: true,
    type: KitStyleType(
      displayWeight: 700,
      // The page title as the concept sets it: 32, a step heavier.
      largeTitleSize: 32,
      largeTitleWeight: 760,
      displayTracking: -.02,
      titleSize: 22,
      engravedLabels: true,
      firmWeights: true,
    ),
  );

  /// The enamel: cream by day, warm near-black by night; the slip a step
  /// lighter, the pressed tone a step toward the ink, and the rule between
  /// rows. Each word role keeps 4.5:1 on every step, or the theme's own
  /// steps stay.
  ///
  /// The quietest words (`text3`) and the attention words are taken just
  /// far enough toward the ink to keep 4.5:1 on the enamel (a cream ground
  /// is a shade darker than most themes' white); the main words and the
  /// accent are the theme's own.
  @override
  ThemeRoles adjustRoles(ThemeRoles roles) {
    final palette = SignalPalette.forRoles(roles);
    final ink = roles.text1.withValues(alpha: 1);
    for (final share in const [1.0, .7, .4]) {
      final ground = Color.lerp(roles.ground, palette.ground, share)!;
      final slip = Color.lerp(roles.surface2, palette.slip, share)!;
      final pressed = Color.lerp(ground, ink, roles.isDark ? .09 : .07)!;
      final steps = [ground, slip, pressed];
      final next = roles.copyWith(
        ground: ground,
        surface1: ground,
        surface2: slip,
        surface3: pressed,
        hairline: Color.lerp(roles.hairline, palette.rule, share),
        text3: readableOn(roles.text3, steps, 4.5, toward: ink),
        attention: readableOn(roles.attention, steps, 4.5, toward: ink),
      );
      if (_readable(next)) return next;
    }
    return roles;
  }

  static bool _readable(ThemeRoles r) {
    for (final fill in [r.ground, r.surface1, r.surface2, r.surface3]) {
      for (final ink in [r.text1, r.text2, r.text3, r.attention]) {
        if (contrastRatio(ink, fill) < 4.5) return false;
      }
    }
    return true;
  }

  BorderRadius _radius(KitSurfaceRequest r) {
    if (r.knobs.corners != KitCorners.sharp) return r.radius;
    Radius cap(Radius c) =>
        c.x > tokens.sharpRadius ? Radius.circular(tokens.sharpRadius) : c;
    final b = r.radius;
    return BorderRadius.only(
      topLeft: cap(b.topLeft),
      topRight: cap(b.topRight),
      bottomLeft: cap(b.bottomLeft),
      bottomRight: cap(b.bottomRight),
    );
  }

  static bool _uniform(BorderRadius b) =>
      b.topLeft == b.topRight &&
      b.topLeft == b.bottomLeft &&
      b.topLeft == b.bottomRight;

  @override
  Decoration resolve(KitSurfaceRequest r) {
    final roles = r.roles;
    final radius = _radius(r);
    final edges = r.knobs.edges == KitEdges.hairline;
    final ink = roles.text2.withValues(alpha: 1);
    final down = r.selected || r.pressed;
    final clear = roles.ground.withValues(alpha: 0);
    KitSurfaceDecoration box({
      required Color fill,
      Color? edge,
      double width = 1,
      List<BoxShadow> shadows = const [],
    }) => KitSurfaceDecoration(
      borderRadius: radius,
      circle: r.circle,
      fill: fill,
      edgeStart: edge ?? Colors.transparent,
      edgeEnd: edge ?? Colors.transparent,
      edgeWidth: edge == null ? 0 : width,
      shadows: shadows,
    );
    final lift = r.knobs.depth == KitDepth.raised
        ? [
            BoxShadow(
              color: roles.elevationShadow,
              blurRadius: 16,
              spreadRadius: -6,
              offset: const Offset(0, 6),
            ),
          ]
        : const <BoxShadow>[];
    switch (r.role) {
      case KitSurfaceRole.ground:
        return kitStyleGround(r);
      case KitSurfaceRole.field:
        // A machined window in ink: the page shows through it.
        return box(fill: clear, edge: ink, width: r.selected ? 2 : outline);
      case KitSurfaceRole.toggle:
        return box(
          fill: (r.selected ? r.color : null) ?? r.levelFill,
          edge: ink,
          width: outline,
        );
      case KitSurfaceRole.code:
        return box(
          fill: r.color ?? r.levelFill,
          edge: edges ? roles.hairline : null,
        );
      case KitSurfaceRole.divider:
        // The rule between rows; no rule above a section (the track and the
        // engraved label part it).
        return KitSurfaceDecoration(
          fill: edges && !r.selected ? roles.hairline : Colors.transparent,
        );
      case KitSurfaceRole.stateLayer:
        final face = roles.text1.withValues(alpha: 1);
        return box(
          fill: r.pressed
              ? Color.lerp(roles.ground, face, roles.isDark ? .12 : .08)!
              : face.withValues(alpha: .04),
        );
      case KitSurfaceRole.content:
        // Rows sit on the enamel; a raised step (a card on its own, your
        // prompt) is a slip on it, ruled.
        final step =
            r.level == KitSurfaceLevel.surface2 ||
            r.level == KitSurfaceLevel.surface3;
        return box(
          fill:
              r.color ??
              (down ? roles.surface3 : (step ? roles.surface2 : clear)),
          edge: edges && (step || r.selected) ? roles.hairline : null,
          shadows: _uniform(radius) ? lift : const [],
        );
      case KitSurfaceRole.control:
        final own = r.color;
        final opaque = own != null && own.a >= 1;
        if (r.action &&
            opaque &&
            !r.selected &&
            !r.disabled &&
            _uniform(radius)) {
          // An action with a face of its own: a raised key.
          final palette = SignalPalette.forRoles(roles);
          final quiet = own == palette.key;
          final face = keyFace(roles, own);
          return kitSignalKey(
            fill: face,
            edge: quiet ? palette.keyEdge : kitSignalKeyEdge(face),
            radius: radius,
            circle: r.circle,
            pressed: r.pressed,
          );
        }
        if (_inverted(r)) return box(fill: invertedInk(roles));
        // A chosen control with its own fill keeps it, flat; a tinted
        // mark (a status tag) is its words alone: the lamps are the only
        // status colour.
        if (own != null) return box(fill: opaque ? own : clear);
        return box(
          fill: down ? roles.surface3 : clear,
          edge: ink,
          width: outline,
        );
      case KitSurfaceRole.track:
        return box(fill: clear, edge: ink, width: outline);
      case KitSurfaceRole.floating
          when r.action &&
              r.color != null &&
              r.color!.a >= 1 &&
              !r.disabled &&
              _uniform(radius):
        // A floating action (the + button): a raised key too.
        final face = keyFace(roles, r.color!);
        return kitSignalKey(
          fill: face,
          edge: kitSignalKeyEdge(face),
          radius: radius,
          circle: r.circle,
          pressed: r.pressed,
        );
      case KitSurfaceRole.floating:
        // The message box, the jump pill: enamel over the page in an ink
        // outline.
        return box(
          fill: r.color ?? roles.ground,
          edge: ink,
          width: outline,
          shadows: _uniform(radius) ? lift : const [],
        );
      case KitSurfaceRole.sheet:
        return box(
          fill: r.color ?? r.levelFill,
          edge: edges ? roles.hairline : null,
          shadows: _uniform(radius) ? lift : const [],
        );
    }
  }

  /// A key's face for an action's own [fill]: as it is by day; by night
  /// a dim key with no glare (eased toward the ground, as far as its
  /// words keep 4.5:1).
  static Color keyFace(ThemeRoles roles, Color fill) {
    if (!roles.isDark || fill == SignalPalette.night.key) return fill;
    return Color.lerp(fill, roles.ground.withValues(alpha: 1), .3)!;
  }

  /// The words on a chosen control are the enamel; on a dimmed night key,
  /// the ink or the ground, whichever reads better.
  @override
  Color? onColor(KitSurfaceRequest r) {
    if (_inverted(r)) return r.roles.ground.withValues(alpha: 1);
    final own = r.color;
    if ((r.role != KitSurfaceRole.control &&
            r.role != KitSurfaceRole.floating) ||
        !r.action ||
        own == null ||
        own.a < 1 ||
        r.selected ||
        r.disabled ||
        !r.roles.isDark ||
        own == SignalPalette.night.key) {
      return null;
    }
    final face = keyFace(r.roles, own);
    final light = r.roles.text1.withValues(alpha: 1);
    final dark = r.roles.ground.withValues(alpha: 1);
    return contrastRatio(light, face) >= contrastRatio(dark, face)
        ? light
        : dark;
  }
}
