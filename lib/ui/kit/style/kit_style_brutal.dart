import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../theme_roles.dart';
import '../kit_effects.dart';
import '../kit_shape.dart' show KitSurfaceLevel;
import '../kit_surface_look.dart';
import 'kit_style.dart';

/// Brutal (neo-brutalism, docs/design/design-schools/schools/brutal.md):
/// ink and paper. A bold 2 dp ink edge on everything that holds content or
/// takes input, a hard offset shadow with no blur towards the bottom end,
/// flat opaque fills on a cream paper ground (light), heavy display type.
/// A pressed control sinks into its shadow; a chosen one already sits down.
/// Only the outer surface casts (hook H9).
class KitStyleBrutal extends KitStyleSpec {
  const KitStyleBrutal();

  /// The ink edge, in logical pixels.
  static const double edge = 2;

  /// The accent ring around a focused field, outside its ink edge (F6).
  static const double focusRing = 3;

  @override
  KitStyleTokens get tokens => const KitStyleTokens(
    defaults: KitKnobs(corners: KitCorners.soft, depth: KitDepth.raised),
    keepsUnderHighContrast: true,
    // Owner Q4 and contract §3.10: the live look at Subtle at most, a hard
    // ring for the running glow, and only the outer surface casts.
    live: KitLivePolicy(maxStrength: .7),
    glow: KitStyleGlow(halo: false, lineWidth: 2),
    flattensNested: true,
    // The rule between groups is ink at the kit's hairline weight (owner,
    // 2026-10-22: a 2 dp bar across the page read as a heavy black rule);
    // a link keeps its 2x ink underline.
    linkUnderline: edge,
    // F2: "Needs you" is an amber pill in the ink box (the one amber fill),
    // on a paper card with no wash, ring or start edge.
    needsYou: KitNeedsYouLook(
      tag: KitNeedsYouTag.pill,
      tint: false,
      ring: false,
      startEdge: false,
    ),
    type: KitStyleType(
      displayWeight: 800,
      displayTracking: -.03,
      bodyWeight: 500,
      monoLabels: true,
      firmWeights: true,
    ),
  );

  /// How far the hard shadow falls at Comfortable, per role (0: none).
  /// A sheet's shadow belongs to its dialog and menu shapes only.
  static double offsetOf(KitSurfaceRole role) => switch (role) {
    KitSurfaceRole.content => 4,
    KitSurfaceRole.control => 3,
    KitSurfaceRole.track => 0,
    KitSurfaceRole.floating => 6,
    KitSurfaceRole.sheet => 4,
    KitSurfaceRole.ground ||
    KitSurfaceRole.field ||
    KitSurfaceRole.code ||
    KitSurfaceRole.divider ||
    KitSurfaceRole.stateLayer ||
    KitSurfaceRole.toggle => 0,
  };

  /// [offsetOf] under Density › Compact: a step tighter (guide §6).
  static double compactOffsetOf(KitSurfaceRole role) => switch (role) {
    KitSurfaceRole.content => 3,
    KitSurfaceRole.control => 2,
    KitSurfaceRole.track => 0,
    KitSurfaceRole.floating => 4,
    KitSurfaceRole.sheet => 3,
    KitSurfaceRole.ground ||
    KitSurfaceRole.field ||
    KitSurfaceRole.code ||
    KitSurfaceRole.divider ||
    KitSurfaceRole.stateLayer ||
    KitSurfaceRole.toggle => 0,
  };

  /// How warm the light paper is: the share of the theme's own amber
  /// stirred into the ground (cream, about `#F4F0E8` on Graphite), the
  /// paper surfaces (`#FFFDF8`) and the tile step. Tried in order; the
  /// first that keeps every word role at AA wins.
  static const _warmth = [.12, .09, .06, .03];
  static const _paperShare = .35;

  /// The shadow colour: ink in light; in dark, black on near-black would
  /// vanish, so an ink-grey (`text3` taken 40 % back towards the ground,
  /// about `#585B61` on Graphite) stands in and the ink edge carries the
  /// boundary. Plain `text3` read as a second, louder edge.
  static Color shadowInk(ThemeRoles roles) => roles.isDark
      ? Color.lerp(roles.text3, roles.ground, .4)!.withValues(alpha: 1)
      : roles.text1;

  /// The main action's face (F5, contract §5 F5): in light, the theme's
  /// accent lifted to its bright hue (about `#3DDC8A` from Graphite's
  /// `#087F43`), so it carries ink words like the dark accent does; the
  /// dark accent is bright already. The hue stays (away from amber), the
  /// lightness climbs until ink reads on it at [_mintWords]; the accent
  /// itself where no step gets there.
  static Color mintOf(ThemeRoles roles) {
    if (roles.isDark) return roles.accent;
    final hsl = HSLColor.fromColor(roles.accent);
    final tone = hsl.withSaturation(math.min(hsl.saturation, .7));
    for (var l = math.max(hsl.lightness, .55); l <= .85; l += .03) {
      final face = tone.withLightness(l).toColor();
      if (contrastRatio(roles.text1, face) >= _mintWords) return face;
    }
    return roles.accent;
  }

  /// Ink on the bright face: well past AA, as the guide's 10.5:1.
  static const double _mintWords = 7;

  /// Whether [r] is an action on the accent (a primary button, send, the
  /// floating "+"), which takes the bright face. A chosen segment or tab
  /// keeps the theme's accent and its own words: one bright mint per
  /// screen is the main action's (the owner's colour rule).
  static bool _acts(KitSurfaceRequest r) =>
      r.color == r.roles.accent && !r.selected;

  /// A part's own fill, with an action's accent taken to the bright face.
  static Color? _own(KitSurfaceRequest r) =>
      _acts(r) ? mintOf(r.roles) : r.color;

  /// Ink words on the bright face (hook H3); elsewhere the part's own.
  @override
  Color? onColor(KitSurfaceRequest r) {
    final roles = r.roles;
    if (!_acts(r)) return null;
    return mintOf(roles) == roles.accent ? null : roles.text1;
  }

  /// A held control with a fill of its own (the primary, hook S9-9) keeps
  /// its colour: raised, the sink into the shadow is the answer, so no
  /// layer; flat, a faint ink.
  @override
  Decoration resolveFilledLayer(KitSurfaceRequest r) => KitSurfaceDecoration(
    borderRadius: r.radius,
    circle: r.circle,
    fill: r.knobs.depth == KitDepth.raised
        ? Colors.transparent
        : r.roles.text1.withValues(alpha: .12),
  );

  @override
  ThemeRoles adjustRoles(ThemeRoles roles) {
    if (roles.isDark) {
      // Dark keeps its near-black ground; cards and fields take the second
      // step so the paper reads apart from the ground under its ink edge.
      return roles.copyWith(surface1: roles.surface2);
    }
    for (final warmth in _warmth) {
      final warm = roles.attentionFill.withValues(alpha: 1);
      Color stir(Color c, double share) =>
          Color.alphaBlend(warm.withValues(alpha: share), c);
      final paper = stir(roles.surface2, warmth * _paperShare);
      final next = roles.copyWith(
        ground: stir(roles.ground, warmth),
        surface1: paper,
        surface2: paper,
        surface3: stir(roles.surface3, warmth),
      );
      if (_readable(next)) return next;
    }
    return roles;
  }

  /// Every word role keeps 4.5:1 on every step it may sit on.
  static bool _readable(ThemeRoles r) {
    for (final fill in [r.ground, r.surface1, r.surface2, r.surface3]) {
      for (final ink in [r.text1, r.text2, r.text3, r.attention]) {
        if (contrastRatio(ink, fill) < 4.5) return false;
      }
    }
    return true;
  }

  @override
  Decoration resolve(KitSurfaceRequest r) {
    if (_part(r) case final part?) return part;
    // The page itself (a shell drawn at the ground step) is the paper the
    // surfaces sit on: no edge and no shadow, or it would be the one outer
    // surface and flatten everything on it (hook H9).
    if (r.level == KitSurfaceLevel.ground && r.color == null) return r.solid;
    final roles = r.roles;
    final ink = roles.text1;
    final raised = r.knobs.depth == KitDepth.raised;
    final compact = r.knobs.density == KitDensity.compact;
    final fall = compact ? compactOffsetOf(r.role) : offsetOf(r.role);
    final radius = r.radius;
    // A bottom sheet (rounded on top only) sits on the scrim without a
    // shadow; a dialog or a menu (even corners) casts. Elsewhere a tailed
    // shape (the prompt bubble) casts too: a blur-0 shadow is one plain
    // shape, with no blur to fall off the fast path.
    final even =
        r.role != KitSurfaceRole.sheet ||
        (radius.topLeft == radius.topRight &&
            radius.topLeft == radius.bottomLeft &&
            radius.topLeft == radius.bottomRight);
    final down = r.selected || r.pressed;
    // Hook H2: a pressed surface sinks into its shadow, so the shadow is
    // covered by the face rather than taken away.
    final sink = r.pressed && raised && even ? fall : 0.0;
    // Controls rest on paper (chips, secondary buttons, tiles); pressed or
    // chosen they take the tile step, which is also all Flat has to show.
    final rest = r.role == KitSurfaceRole.control
        ? roles.surface2
        : r.levelFill;
    // A control that cannot be used lies flat in a muted edge, on the tile
    // step (or the quiet fill its part keeps for it, the waiting hero):
    // never a secondary with a shadow (hook S9-12); its words say it is
    // off (`text3`, AA on `surface3`).
    if (r.disabled && r.role == KitSurfaceRole.control) {
      return KitSurfaceDecoration(
        borderRadius: radius,
        circle: r.circle,
        fill: r.color ?? roles.surface3,
        edgeStart: roles.text3,
        edgeEnd: roles.text3,
        edgeWidth: edge,
      );
    }
    final fill = _own(r) ?? (down ? roles.surface3 : rest);
    // Edges › Off keeps the border on controls and tracks (fields and
    // segments must stay identifiable, WCAG 1.4.11).
    final keepsEdge =
        r.knobs.edges == KitEdges.hairline ||
        r.role == KitSurfaceRole.control ||
        r.role == KitSurfaceRole.track;
    final offset = raised && !down && even ? fall : 0.0;
    // In dark the shadow a chosen surface gives up is ink-grey and the tile
    // step under it is one shade off, so a chosen card or tile trades its
    // ink edge for the accent (kept under Edges › Off: it is the state). A
    // part's own fill (a chosen segment) shows the choice itself; in light
    // the ink shadow it gives up says it already.
    final ringed =
        roles.isDark &&
        r.selected &&
        r.color == null &&
        (r.role == KitSurfaceRole.content || r.role == KitSurfaceRole.control);
    final line = ringed ? roles.accent : ink;
    final edged = keepsEdge || ringed;
    return KitSurfaceDecoration(
      borderRadius: radius,
      circle: r.circle,
      fill: fill,
      edgeStart: edged ? line : Colors.transparent,
      edgeEnd: edged ? line : Colors.transparent,
      edgeWidth: edged ? edge : 0,
      hardShadow: offset > 0 ? shadowInk(roles) : Colors.transparent,
      hardOffset: offset,
      faceShift: sink,
    );
  }

  /// A part's own surface (schools/brutal.md §5), or null for a pane. None
  /// casts: only panes throw the hard shadow.
  /// - the page: its fill alone;
  /// - a field, a switch's track: paper (the on fill when on) in a 2 dp
  ///   ink box, kept under Edges › Off (the 3:1 rule);
  /// - code: paper in the ink box, opaque;
  /// - a focused field: the same box in a 3 dp accent ring ([focusRing]);
  /// - a divider: ink at α 0.9 and the kit's hairline weight between
  ///   groups; space alone between the rows of one group, and none under
  ///   Edges › Off;
  /// - a highlight: `surface3` while pressed, a faint ink on hover.
  Decoration? _part(KitSurfaceRequest r) {
    final roles = r.roles;
    final ink = roles.text1;
    final edged = r.knobs.edges == KitEdges.hairline;
    KitSurfaceDecoration box(
      Color fill, {
      bool keep = true,
      List<BoxShadow> ring = const [],
    }) => KitSurfaceDecoration(
      borderRadius: r.radius,
      circle: r.circle,
      fill: fill,
      edgeStart: keep ? ink : Colors.transparent,
      edgeEnd: keep ? ink : Colors.transparent,
      edgeWidth: keep ? edge : 0,
      shadows: ring,
    );
    return switch (r.role) {
      KitSurfaceRole.ground => kitStyleGround(r),
      KitSurfaceRole.field => box(
        r.color ?? r.levelFill,
        ring: [
          if (r.selected)
            BoxShadow(color: roles.accent, spreadRadius: focusRing),
        ],
      ),
      KitSurfaceRole.toggle => box(
        (r.selected ? r.color : null) ?? r.levelFill,
      ),
      KitSurfaceRole.code => box(r.color ?? r.levelFill, keep: edged),
      KitSurfaceRole.divider => KitSurfaceDecoration(
        fill: edged && !r.withinGroup
            ? ink.withValues(alpha: .9)
            : Colors.transparent,
      ),
      KitSurfaceRole.stateLayer => KitSurfaceDecoration(
        borderRadius: r.radius,
        circle: r.circle,
        fill: r.pressed ? roles.surface3 : ink.withValues(alpha: .04),
      ),
      _ => null,
    };
  }
}
