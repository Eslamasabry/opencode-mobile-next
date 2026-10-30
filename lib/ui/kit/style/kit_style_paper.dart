import 'package:flutter/material.dart';

import '../../theme_roles.dart';
import '../kit_effects.dart';
import '../kit_shape.dart' show KitSurfaceLevel;
import '../kit_surface_look.dart';
import 'kit_style.dart';

/// Paper (flat print, Swiss style, docs/design/design-schools/schools/
/// paper.md): ink on newsprint. No shadows, no blur, square corners; rows
/// are set on the page and parted by fine ink rules, and only what you act
/// on (a button, a chip, a field, the message box, your own prompt) is a
/// box, ruled in ink on the paper.
class KitStylePaper extends KitStyleSpec {
  const KitStylePaper();

  /// The ink box around what you act on: 0.9 on light paper, 0.6 on dark
  /// (6.3:1 there; full ink glares on near-black).
  static double boxAlpha(ThemeRoles roles) => roles.isDark ? .6 : .9;

  /// The row divider (the theme's hairline under Paper): one physical
  /// pixel of ink.
  static const double ruleAlpha = .2;

  /// A chosen control's rule, in logical pixels: twice the box.
  static const double chosenRule = 2;

  /// The section rule above a section's label: ink at the box's weight,
  /// one logical pixel (paper.md §3).
  static const double sectionAlpha = .9;

  /// The highlight under a fine pointer, in ink over the face.
  static const double hoverAlpha = .03;

  /// How far toward the ink a held row or control goes: past the tile step
  /// (`surface3`) so the press reads apart from the hover, short of where
  /// the quietest words would lose AA (4.5:1).
  static double pressStep(ThemeRoles roles) => roles.isDark ? .10 : .07;

  /// The ink a chosen control is filled with (inverted selection).
  static Color invertedInk(ThemeRoles roles) =>
      roles.text1.withValues(alpha: 1);

  /// Whether [r] is a chosen control, set in reverse: ink with paper words.
  static bool _inverted(KitSurfaceRequest r) =>
      r.role == KitSurfaceRole.control && r.selected && !r.circle;

  /// A round control Corners › Sharp squares: a switch's thumb (the one
  /// round control asked at the sheet step, kit_row_parts.dart), like its
  /// track; and the main action in its accent (the composer's mic or send,
  /// paper.md F5 "primary: accent fill, square"), which keeps the mint.
  /// Stop (ink, in its turning ring) and the quiet discs stay round.
  static bool _squared(KitSurfaceRequest r) =>
      r.role == KitSurfaceRole.control &&
      r.circle &&
      r.knobs.corners == KitCorners.sharp &&
      (r.level == KitSurfaceLevel.surface2 || r.color == r.roles.accent);

  @override
  KitStyleTokens get tokens => const KitStyleTokens(
    defaults: KitKnobs(corners: KitCorners.sharp),
    sharpRadius: 0,
    keepsUnderHighContrast: true,
    // Owner Q4 and contract §3.10: print does not move (the live look is
    // off when Paper is chosen, still the person's switch) and the running
    // glow is a thin rule.
    live: KitLivePolicy(offWhenChosen: true),
    glow: KitStyleGlow(halo: false, lineWidth: 1),
    // Needs you is set like a notice in the margin: the 4 dp amber rule at
    // the start and the amber caption, on the paper; no wash, no ring. The
    // default look decides exactly that from a glow without a halo
    // (KitNeedsYouLook.decide), so it is left at its default.
    type: KitStyleType(
      displayWeight: 750,
      displayTracking: -.03,
      largeTitleSize: 34,
      // The concept's page title: a big bold name over the rule, the
      // question's size (24), not a card's headline.
      titleSize: 24,
      monoLabels: true,
      firmWeights: true,
    ),
  );

  /// Newsprint: light paper a shade under white and a touch warm, dark
  /// paper a step off the theme's black. Panels are not filled (surface1 is
  /// the paper), the pressed step sits one tone down, and the hairline is
  /// the ink rule between rows.
  @override
  ThemeRoles adjustRoles(ThemeRoles roles) {
    final ink = roles.text1.withValues(alpha: 1);
    final ground = roles.isDark
        ? Color.lerp(roles.ground, ink, .02)!
        : Color.alphaBlend(
            roles.attentionFill.withValues(alpha: .04),
            Color.lerp(roles.surface2, ink, .04)!,
          );
    return roles.copyWith(
      ground: ground,
      surface1: ground,
      surface3: Color.lerp(ground, ink, roles.isDark ? .08 : .05),
      hairline: ink.withValues(alpha: ruleAlpha),
    );
  }

  @override
  Decoration resolve(KitSurfaceRequest r) {
    if (_part(r) case final part?) return part;
    final roles = r.roles;
    final ink = roles.text1.withValues(alpha: boxAlpha(roles));
    final down = r.selected || r.pressed;
    final edges = r.knobs.edges == KitEdges.hairline;
    final Color fill;
    final bool boxed;
    switch (r.role) {
      case KitSurfaceRole.content:
        // A panel is set on the page: no fill, no box; its rows are parted
        // by the hairline. A raised step (your prompt, a card on its own)
        // is a box on the paper, or under Edges › Off a block in the
        // pressed tone, so it still stands apart from the reply. A part's
        // own fill (the needs-you tint) stays.
        final step =
            r.level == KitSurfaceLevel.surface2 ||
            r.level == KitSurfaceLevel.surface3;
        fill =
            r.color ??
            (down || (step && !edges)
                ? roles.surface3
                : step
                ? roles.ground
                : roles.ground.withValues(alpha: 0));
        boxed = edges && (step || r.selected);
      case KitSurfaceRole.control:
      case KitSurfaceRole.track:
        // A box in ink on the paper; the accent (primary) keeps its own
        // fill and needs no box. Fields and segments keep their rule under
        // Edges › Off: they must stay identifiable. A chosen control (a
        // segment, a tab, a chip, the nav lens) is set in reverse: ink,
        // with the paper as its words ([onColor]).
        fill = _inverted(r)
            ? invertedInk(roles)
            : r.color ?? (down ? roles.surface3 : roles.ground);
        boxed = r.color == null || r.selected;
      case KitSurfaceRole.floating:
        // The message box, the dock, the jump pill: opaque paper over the
        // page, ruled.
        fill = r.color ?? roles.ground;
        boxed = true;
      case KitSurfaceRole.sheet:
        // A sheet, dialog or menu: the white page over the paper, ruled.
        fill = r.color ?? r.levelFill;
        boxed = edges;
      case KitSurfaceRole.ground ||
          KitSurfaceRole.field ||
          KitSurfaceRole.code ||
          KitSurfaceRole.divider ||
          KitSurfaceRole.stateLayer ||
          KitSurfaceRole.toggle:
        // A part's own surface: answered by [_part] above.
        return _part(r)!;
    }
    // Depth › Raised gives the floating layer the one tight drop; print
    // never shadows content.
    final shadows =
        r.knobs.depth == KitDepth.raised && r.role == KitSurfaceRole.floating
        ? [
            BoxShadow(
              color: roles.elevationShadow,
              blurRadius: 16,
              spreadRadius: -6,
              offset: const Offset(0, 6),
            ),
          ]
        : const <BoxShadow>[];
    final chosen = r.selected && r.role != KitSurfaceRole.floating;
    // An inverted control is one block of ink: its rule is the fill.
    final edge = _inverted(r) ? fill : ink;
    return KitSurfaceDecoration(
      // Corners › Sharp squares every box, including the ones a part
      // rounds by itself (the search field); circles stay circles, but
      // for the switch's thumb and the main action ([_squared]).
      borderRadius: r.knobs.corners == KitCorners.sharp
          ? BorderRadius.zero
          : r.radius,
      circle: r.circle && !_squared(r),
      fill: fill,
      edgeStart: boxed ? edge : Colors.transparent,
      edgeEnd: boxed ? edge : Colors.transparent,
      edgeWidth: boxed ? (chosen && !_inverted(r) ? chosenRule : 1) : 0,
      shadows: shadows,
    );
  }

  /// The words on a chosen control are the paper (hook H3), on its ink.
  /// Every other surface keeps the part's own words.
  @override
  Color? onColor(KitSurfaceRequest r) =>
      _inverted(r) ? r.roles.ground.withValues(alpha: 1) : null;

  /// A part's own surface (schools/paper.md §5), or null for a pane. No
  /// shadows anywhere; Corners › Sharp squares them like the panes.
  /// - the page: its fill alone;
  /// - a field, a switch's track: an ink box ([boxAlpha]; [chosenRule]
  ///   once focused or on), kept under Edges › Off; a field has no face
  ///   of its own (the page under it shows);
  /// - code (a listing, the command a card asks to run): its opaque fill
  ///   in the ink box ([boxAlpha]), like the concept's `npm test`; none
  ///   under Edges › Off;
  /// - a divider: the ink rule at the part's hairline; asked `selected`
  ///   (the section rule above a label), one dp of ink at [sectionAlpha];
  ///   none under Edges › Off;
  /// - a highlight: a step past the pressed tone while held
  ///   ([pressStep]), a faint ink on hover ([hoverAlpha]).
  Decoration? _part(KitSurfaceRequest r) {
    final roles = r.roles;
    final ink = roles.text1;
    final edged = r.knobs.edges == KitEdges.hairline;
    final radius = r.knobs.corners == KitCorners.sharp
        ? BorderRadius.zero
        : r.radius;
    final rule = edged ? ink.withValues(alpha: ruleAlpha) : Colors.transparent;
    final box = ink.withValues(alpha: boxAlpha(roles));
    return switch (r.role) {
      KitSurfaceRole.ground => kitStyleGround(r),
      KitSurfaceRole.field || KitSurfaceRole.toggle => KitSurfaceDecoration(
        borderRadius: radius,
        circle: r.circle,
        // A field is a ruled box on whatever page holds it (the paper, a
        // white sheet), never a slab of the paper's tone; a track is
        // filled, and the on fill once on.
        fill: r.role == KitSurfaceRole.field
            ? roles.ground.withValues(alpha: 0)
            : (r.selected ? r.color : null) ?? r.levelFill,
        edgeStart: box,
        edgeEnd: box,
        edgeWidth: r.selected ? chosenRule : 1,
      ),
      KitSurfaceRole.code => KitSurfaceDecoration(
        borderRadius: radius,
        circle: r.circle,
        fill: r.color ?? r.levelFill,
        edgeStart: edged ? box : Colors.transparent,
        edgeEnd: edged ? box : Colors.transparent,
        edgeWidth: edged ? 1 : 0,
      ),
      KitSurfaceRole.divider =>
        r.selected && edged
            ? KitSurfaceDecoration(
                fill: ink.withValues(alpha: sectionAlpha),
                edgeWidth: 1,
              )
            : KitSurfaceDecoration(fill: rule),
      KitSurfaceRole.stateLayer => KitSurfaceDecoration(
        borderRadius: radius,
        circle: r.circle,
        fill: r.pressed
            ? Color.lerp(roles.ground, ink, pressStep(roles))!
            : ink.withValues(alpha: hoverAlpha),
      ),
      _ => null,
    };
  }
}
