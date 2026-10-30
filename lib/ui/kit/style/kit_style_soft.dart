import 'package:flutter/painting.dart';

import '../../theme_roles.dart';
import '../kit_effects.dart';
import '../kit_shape.dart' show KitSurfaceLevel;
import '../kit_surface_look.dart';
import 'kit_style.dart';

/// Soft (neumorphism, docs/design/design-schools/schools/soft.md): the
/// surface takes the ground's colour and is shaped by paired soft shadows,
/// raised at rest and pressed in when chosen.
class KitStyleSoft extends KitStyleSpec {
  const KitStyleSoft();

  @override
  KitStyleTokens get tokens => const KitStyleTokens(
    defaults: KitKnobs(depth: KitDepth.raised, edges: KitEdges.none),
    dependsOnDepth: true,
    // Owner Q4: ground-matched surfaces want a calm ground; Subtle at most.
    live: KitLivePolicy(maxStrength: .7),
    // Contract F2: a raised card with the 4 dp attention edge at its start
    // and the amber caption; no amber wash (it would break ground-matching)
    // and no ring.
    needsYou: KitNeedsYouLook(tint: false, ring: false, startEdge: true),
    // The ask field glows like the start box, around its well.
    fieldHalo: true,
    // Soft keeps the firmer weights it was drawn with (Solid is lighter).
    type: KitStyleType(firmWeights: true),
  );

  /// How far a soft surface stands out (the shadow offset) and how soft its
  /// shadows are, per role.
  static ({double distance, double blur}) depthOf(KitSurfaceRole role) =>
      switch (role) {
        KitSurfaceRole.content => (distance: 6, blur: 16),
        KitSurfaceRole.control => (distance: 3, blur: 8),
        KitSurfaceRole.track => (distance: 2.5, blur: 6),
        KitSurfaceRole.floating => (distance: 6, blur: 16),
        KitSurfaceRole.sheet => (distance: 0, blur: 0),
        // Wells: a field, a switch's track, a pressed row, code.
        KitSurfaceRole.field ||
        KitSurfaceRole.toggle ||
        KitSurfaceRole.stateLayer ||
        KitSurfaceRole.code => (distance: 2.5, blur: 6),
        KitSurfaceRole.ground ||
        KitSurfaceRole.divider => (distance: 0, blur: 0),
      };

  /// The ground moved off the theme's black or near-white, so both shadows
  /// of the pair can show (guide §1 and §3, contract §3.3): dark lifted
  /// about +6 L* (`#0B0C0E` to about `#1C1E23` in Graphite), light one step
  /// down (about −5 L*), hue kept. Content and sheets are the ground; tiles
  /// and wells one step off it. Light keeps `surface2` (the near-white the
  /// highlight is made of), dark lifts it with the ground.
  ///
  /// The lift stops short where the theme's quieter words would drop under
  /// AA on the ground, a raised face or the tile step.
  @override
  ThemeRoles adjustRoles(ThemeRoles roles) {
    final ink = roles.text1.withValues(alpha: 1);
    final dark = roles.isDark;
    final step = dark ? .035 : .03;
    final words = [roles.text2, roles.text3];
    for (var t = dark ? .07 : .045; t > 0; t -= .005) {
      final ground = Color.lerp(roles.ground, ink, t)!;
      final faces = [
        ground,
        Color.alphaBlend(
          kitStyleLight(roles).withValues(alpha: dark ? _lift : 0),
          ground,
        ),
        Color.lerp(ground, ink, step)!,
      ];
      if (!words.every((w) => faces.every((f) => contrastRatio(w, f) >= 4.5))) {
        continue;
      }
      return roles.copyWith(
        ground: ground,
        surface1: ground,
        surface2: dark ? Color.lerp(roles.surface2, ink, t) : roles.surface2,
        surface3: faces.last,
      );
    }
    return roles;
  }

  /// The near-white washed over a raised face in dark (its lit top).
  static const double _lift = .05;

  /// The shade pressed into a dark well (a track, a field, code).
  static const double _well = .18;

  /// Words on a chosen control that has no fill of its own (a session tab,
  /// a chip, the dock's lens): pressed in, it says "chosen" in the accent
  /// (schools/soft.md §4 F5, Malewicz: an inset alone is too quiet), moved
  /// until it reads at 4.5:1 on the well.
  @override
  Color? onColor(KitSurfaceRequest r) {
    if (!r.selected || r.role != KitSurfaceRole.control) return null;
    if (r.color != null) return null;
    final roles = r.roles;
    final under = roles.ground.withValues(alpha: 1);
    final well = Color.alphaBlend(_shaped(r).fill, under);
    return readableOn(
      roles.accent,
      [well, under],
      4.6,
      toward: roles.text1.withValues(alpha: 1),
    );
  }

  @override
  Decoration resolve(KitSurfaceRequest r) {
    if (_part(r) case final part?) return part;
    final shaped = _shaped(r);
    // Depth › Flat or Edges › On: a visible edge, so no surface loses its
    // boundary without its shadows (contract §2.2).
    if (r.knobs.edges != KitEdges.hairline) return shaped;
    final edge = r.roles.text3.withValues(alpha: .45);
    return _withEdge(shaped, edge, r.edgeWidth);
  }

  /// [shaped] with a uniform [edge] of [width].
  static KitSurfaceDecoration _withEdge(
    KitSurfaceDecoration shaped,
    Color edge,
    double width,
  ) => KitSurfaceDecoration(
    borderRadius: shaped.borderRadius,
    circle: shaped.circle,
    fill: shaped.fill,
    fillStart: shaped.fillStart,
    edgeStart: edge,
    edgeEnd: edge,
    edgeWidth: width,
    lightShadow: shaped.lightShadow,
    darkShadow: shaped.darkShadow,
    distance: shaped.distance,
    blur: shaped.blur,
    inset: shaped.inset,
    shadows: shaped.shadows,
  );

  /// A part's own surface (schools/soft.md §5), or null for a pane:
  /// - the page: its fill alone;
  /// - a field or a switch's track: a well pressed into the ground, with a
  ///   boundary that reads at 3:1 (the accent once focused; an on track
  ///   holds its own fill);
  /// - code: a well of the part's opaque fill;
  /// - a divider: none (rows part by space) unless Edges is on;
  /// - a highlight: a faint shade on hover, pressed in while held.
  Decoration? _part(KitSurfaceRequest r) {
    final roles = r.roles;
    switch (r.role) {
      case KitSurfaceRole.ground:
        return kitStyleGround(r);
      case KitSurfaceRole.divider:
        return r.knobs.edges == KitEdges.hairline
            ? r.solid
            : KitSurfaceDecoration(fill: roles.ground.withValues(alpha: 0));
      case KitSurfaceRole.stateLayer:
        final dark = roles.isDark;
        if (!r.pressed) {
          return KitSurfaceDecoration(
            borderRadius: r.radius,
            circle: r.circle,
            fill: dark
                ? kitStyleLight(roles).withValues(alpha: .04)
                : kitStyleShade(roles).withValues(alpha: .03),
          );
        }
        final well = _shaped(r);
        return KitSurfaceDecoration(
          borderRadius: well.borderRadius,
          circle: well.circle,
          fill: kitStyleShade(roles).withValues(alpha: dark ? .28 : .03),
          lightShadow: well.lightShadow,
          darkShadow: well.darkShadow,
          distance: well.distance,
          blur: well.blur,
          inset: 1,
        );
      case KitSurfaceRole.code:
        final well = _shaped(r);
        return r.knobs.edges == KitEdges.hairline
            ? _withEdge(well, roles.text3.withValues(alpha: .45), r.edgeWidth)
            : well;
      case KitSurfaceRole.field || KitSurfaceRole.toggle:
        final on = r.selected && r.role == KitSurfaceRole.toggle;
        final edge = r.selected
            ? (on ? r.color ?? roles.accent : roles.accent)
            : kitStyleBoundary(roles);
        return _withEdge(_shaped(r), edge, r.edgeWidth);
      case KitSurfaceRole.content ||
          KitSurfaceRole.control ||
          KitSurfaceRole.track ||
          KitSurfaceRole.floating ||
          KitSurfaceRole.sheet:
        return null;
    }
  }

  KitSurfaceDecoration _shaped(KitSurfaceRequest r) {
    final roles = r.roles;
    final role = r.role;
    final radius = r.radius;
    final circle = r.circle;
    final color = r.color;
    final edgeWidth = r.edgeWidth;
    final dark = roles.isDark;
    final white = kitStyleLight(roles);
    final black = kitStyleShade(roles);
    final ground = color ?? roles.ground;
    // A track is a well: pressed in at rest, its selected segment raised
    // out of it reads as the one chosen. A chosen control filled with the
    // accent (a segment's thumb, a session tab) stands raised out of its
    // track too: pressing an accent face into the ground only darkens it
    // into a muddy well (guide F5, gate G5). It still sinks while held.
    final filledChoice =
        role == KitSurfaceRole.control && color != null && r.selected;
    final pressedIn = switch (role) {
      KitSurfaceRole.track ||
      KitSurfaceRole.field ||
      KitSurfaceRole.toggle ||
      KitSurfaceRole.code ||
      KitSurfaceRole.stateLayer => true,
      _ => r.pressed || (r.selected && !filledChoice),
    };
    // Your prompt (the S9-4 bubble, `content` at surface2): a raised face
    // a lit step above the ground, so it stands apart from the reply. Every
    // raised step at surface2 shares it (the agent cards, the report card),
    // so it is neutral: a hue of your colour there reads as a tinted card.
    final prompt =
        role == KitSurfaceRole.content &&
        r.level == KitSurfaceLevel.surface2 &&
        color == null &&
        !pressedIn;
    // A control that cannot be used is flat: no shadows, never mistaken
    // for a pressed-in (chosen) one (guide §5).
    if (r.disabled &&
        (role == KitSurfaceRole.control || role == KitSurfaceRole.content)) {
      return KitSurfaceDecoration(
        borderRadius: radius,
        circle: circle,
        fill: ground,
      );
    }
    final flat = r.knobs.depth == KitDepth.flat;
    final depth = flat ? (distance: 0.0, blur: 0.0) : depthOf(role);

    if (role == KitSurfaceRole.sheet) {
      // A sheet stands on the scrim, where paired shadows cannot read: the
      // ground's colour, a lit top edge and one soft drop.
      return KitSurfaceDecoration(
        borderRadius: radius,
        circle: circle,
        fill: dark
            ? Color.alphaBlend(white.withValues(alpha: .03), ground)
            : ground,
        edgeStart: white.withValues(alpha: dark ? .10 : .9),
        edgeEnd: white.withValues(alpha: 0),
        edgeWidth: edgeWidth,
        shadows: [
          BoxShadow(
            color: black.withValues(alpha: dark ? .55 : .14),
            blurRadius: 28,
            offset: const Offset(0, -4),
          ),
        ],
      );
    }

    final face = prompt
        // A dark theme with no room for a brighter step (its quieter words
        // already at AA on the raised face) takes a faint wash of your
        // colour instead, which costs less contrast.
        ? _tinted(
                ground,
                white,
                most: dark ? .10 : .6,
                least: dark ? _lift + .01 : 0,
                inks: [roles.text1, roles.text2],
              ) ??
              _tinted(
                ground,
                roles.partner,
                most: .10,
                inks: [roles.text1, roles.text2],
              ) ??
              ground
        : ground;

    if (dark) {
      // On a near-black ground a dark shadow alone disappears, so the depth
      // comes from three things together: a lifted face lit from the top
      // start, a lit rim on that side and a deep shadow on the other.
      final lit = Color.alphaBlend(
        white.withValues(
          alpha: pressedIn || prompt || color != null ? 0 : _lift,
        ),
        face,
      );
      return KitSurfaceDecoration(
        borderRadius: radius,
        circle: circle,
        fill: pressedIn
            // A shallow well: on the lifted ground a deeper one reads
            // as a black hole rather than pressed in.
            ? Color.alphaBlend(black.withValues(alpha: _well), ground)
            : lit,
        edgeStart: pressedIn
            ? white.withValues(alpha: 0)
            : white.withValues(alpha: .16),
        edgeEnd: pressedIn
            ? white.withValues(alpha: .06)
            : white.withValues(alpha: 0),
        edgeWidth: edgeWidth,
        lightShadow: white.withValues(alpha: color != null ? .07 : .10),
        darkShadow: black.withValues(alpha: .85),
        distance: depth.distance,
        blur: depth.blur,
        inset: pressedIn ? 1 : 0,
      );
    }

    return KitSurfaceDecoration(
      borderRadius: radius,
      circle: circle,
      fill: pressedIn
          ? (role == KitSurfaceRole.code
                ? _lightWell(roles, ground)
                : Color.alphaBlend(black.withValues(alpha: .025), ground))
          : face,
      edgeStart: pressedIn
          ? white.withValues(alpha: 0)
          : white.withValues(alpha: filledChoice ? .35 : .7),
      edgeEnd: white.withValues(alpha: 0),
      edgeWidth: edgeWidth,
      lightShadow: white.withValues(alpha: .7),
      // A cool deep grey: the theme's own ink, softly; under an accent face
      // the shade leans toward the accent's dark (guide F5).
      darkShadow: filledChoice
          ? Color.lerp(color, roles.text1, .55)!.withValues(alpha: .34)
          : roles.text1.withValues(alpha: .26),
      distance: depth.distance,
      blur: depth.blur,
      inset: pressedIn ? 1 : 0,
    );
  }

  /// [base] washed with up to [most] of [tint], backed off until every one
  /// of [inks] still reads at AA on it; null when that takes less than
  /// [least]. In dark it stands in for the raised face's own wash.
  static Color? _tinted(
    Color base,
    Color tint, {
    required double most,
    double least = 0,
    required List<Color> inks,
  }) {
    for (var a = most; a > least; a -= .01) {
      final face = Color.alphaBlend(tint.withValues(alpha: a), base);
      if (inks.every((ink) => contrastRatio(ink, face) >= 4.5)) return face;
    }
    return null;
  }

  /// The light code well: the part's [fill], lightened toward the near-white
  /// only as far as every code colour needs to read at AA on it (audit S6:
  /// the keyword at 4.40:1 on the old, darker well). The inset shadows still
  /// press it in.
  static Color _lightWell(ThemeRoles roles, Color fill) {
    final light = kitStyleLight(roles);
    final inks = [
      roles.text1,
      roles.text2,
      roles.codeKeyword,
      roles.codeString,
      roles.codeType,
      roles.codeAdded,
    ];
    for (var t = 0.0; t < 1; t += .1) {
      final well = Color.lerp(fill, light, t)!;
      if (inks.every((ink) => contrastRatio(ink, well) >= 4.6)) return well;
    }
    return light;
  }
}
