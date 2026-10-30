import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import '../theme_roles.dart';
import 'kit_effects.dart';
import 'kit_motion.dart';
import 'kit_tokens.dart';
import 'style/kit_style.dart';
import 'style/kit_style_soft.dart';

export 'kit_effects.dart' show KitSurfaceStyle;

part 'kit_surface_paint.dart';

/// Where a surface sits, which tunes its depth (Settings › Appearance ›
/// Style).
enum KitSurfaceRole {
  /// Cards, grouped rows, panels and the answer cards: content in a
  /// scrolling page.
  content,

  /// A small resting control: a chip, a secondary button, a segment, an
  /// icon tile. A selected or pressed one sits pressed in under Soft.
  control,

  /// The bed a row of controls sits in: a segmented track, a tab strip.
  track,

  /// A bounded piece that floats over the page: the dock, the rail, the top
  /// bar's pills, the message box, the jump pill.
  floating,

  /// A sheet, a dialog or a menu.
  sheet,

  /// The page itself: the ground a whole screen is hosted on
  /// (`KitSurface(level: KitSurfaceLevel.ground)`, which `kitSurfaceMaterial`
  /// maps here). Fill only in every style: no edge, no shadow, and the
  /// surfaces on it are not nested in it, so they keep their own shadows
  /// (hook H9) and their own faces (the nesting rule).
  ground,

  /// A text field, a search field, the ask field, a one-time code. Pass
  /// `selected: true` while it has focus. Its boundary reads at 3:1 or more
  /// at rest in every style but Solid, whatever Edges says (contract F6).
  field,

  /// A code block, log, terminal, diff or command box. Its fill stays
  /// opaque in every style (technical text never sits on translucency);
  /// only its edge and depth follow the style.
  code,

  /// A divider line. Ask with [KitSurfaceLook.divider], which gives the
  /// line's colour (transparent: no line in this style) and its width.
  divider,

  /// The hover and pressed highlight laid over a row or a tappable surface.
  /// Pass `pressed: true` while it is held; otherwise it is the hover (or
  /// focus) highlight. A part paints no state layer at rest.
  stateLayer,

  /// A switch's track; pass `selected: true` when it is on, with the on
  /// fill as `color`. The thumb is a `control` with `circle: true`. Like a
  /// field, an off track's boundary reads at 3:1 or more at rest.
  toggle;

  /// The roles a pane, panel or piece takes, which the styles' fill and
  /// depth models are tuned for. The other roles are a part's own
  /// surface (the page, a field, code, a line, a highlight, a switch).
  static const panes = [content, control, track, floating, sheet];
}

/// The one resolver for how a kit surface is made: [KitSurfaceStyle] (the
/// person's Style) in the theme's brightness, for a [KitSurfaceRole], under
/// the Fine-tune knobs in force ([KitStyles.knobsOf]).
///
/// Each style is one [KitStyleSpec] (lib/ui/kit/style/), found by
/// [kitStyleSpec]; this class only reads the context and asks it. Solid at
/// its defaults is always the part's own decoration, so a solid screen is
/// exactly what it was before the choice existed. The platform's
/// high-contrast request makes Soft solid (Brutal and Paper keep
/// themselves), and Saver is already applied to the effects.
abstract final class KitSurfaceLook {
  /// How long a change of style cross-fades; a state change (pressed,
  /// selected) inside one style is [stateFade].
  static const styleFade = Duration(milliseconds: 200);
  static const stateFade = Duration(milliseconds: 120);

  /// [effects] with [style] chosen ([KitStyles.choose]): what the Style
  /// picker writes. A style that starts without the live background
  /// ([KitLivePolicy.offWhenChosen]: Paper) turns it off and remembers that
  /// it did ([KitEffects.liveHeldByStyle]); choosing another style then
  /// gives the person their live background back. One the person turned off
  /// themselves stays off.
  static KitEffects choose(KitEffects effects, KitSurfaceStyle style) {
    if (style == effects.surfaceStyle) return KitStyles.choose(effects, style);
    final offWhenChosen = kitStyleSpec(style).tokens.live.offWhenChosen;
    final held = effects.liveHeldByStyle && !effects.liveBackground;
    final next = effects.withSurfaceStyle(style);
    if (offWhenChosen) {
      return next.copyWith(
        liveBackground: false,
        liveHeldByStyle: held || effects.liveBackground,
      );
    }
    return held
        ? next.copyWith(liveBackground: true, liveHeldByStyle: false)
        : next.copyWith(liveHeldByStyle: false);
  }

  /// The style in force here.
  static KitSurfaceStyle styleOf(BuildContext context) =>
      KitStyles.styleOf(context);

  /// Whether this is Solid at its defaults here: every part paints exactly
  /// its own look ([decorate] returns `solid` itself). A part that paints
  /// through a `Material` keeps that path while this holds and asks
  /// [decorate] otherwise, so Fine-tune reaches it under Solid too.
  static bool isPlain(BuildContext context) =>
      styleOf(context) == KitSurfaceStyle.solid &&
      KitStyles.knobsOf(context) == _solidDefaults;

  /// Whether lists sit straight on the page here
  /// ([KitStyleTokens.flatLists]: Solid): a row group, a choice list and
  /// the big choices paint no panel of their own, and a row's icon no tile.
  static bool flatLists(BuildContext context) =>
      kitStyleSpec(styleOf(context)).tokens.flatLists;

  /// A panel with nothing of its own (no fill, edge or shadow): what a
  /// list's panel is where [flatLists] holds. [KitSurfaceDecoration.isBare].
  static const bare = KitSurfaceDecoration(fill: Colors.transparent);

  /// The decoration for a surface here. [solid] is the part's own look,
  /// returned unchanged for Solid at its defaults; [color] keeps a part's
  /// own fill (an accent button, a selected chip) instead of the surface
  /// step's. [hovered], [disabled] and [withinGroup] tell the style more of
  /// the state ([KitSurfaceRequest]); a style that ignores them answers as
  /// before. A `stateLayer` asked with [color] is the highlight on a
  /// control's own fill ([KitStyleSpec.resolveFilledLayer]).
  ///
  /// Two rules apply on top of the style's answer:
  /// - **Nesting.** A `content` surface inside another `content` surface
  ///   paints nothing of its own at rest: it sits on the outer one's face
  ///   (no second fill, edge or shadow). Chosen or pressed, it keeps its
  ///   face and casts nothing. Every style but Solid
  ///   ([KitStyleTokens.keepsNestedSurfaces]).
  /// - **One shadow per stack** (hook H9): inside a surface that casts, a
  ///   style that flattens nested surfaces keeps only the outer shadow.
  static Decoration decorate(
    BuildContext context, {
    required Decoration solid,
    KitSurfaceLevel level = KitSurfaceLevel.surface1,
    KitSurfaceRole role = KitSurfaceRole.content,
    BorderRadius? borderRadius,
    bool circle = false,
    Color? color,
    bool selected = false,
    bool pressed = false,
    bool hovered = false,
    bool disabled = false,
    bool withinGroup = false,
    bool action = false,
  }) {
    final style = styleOf(context);
    final knobs = KitStyles.knobsOf(context);
    if (style == KitSurfaceStyle.solid && knobs == _solidDefaults) {
      return solid;
    }
    // Hook H4: a stadium follows Corners › Sharp like every other box.
    final tokens = KitTokens.of(context);
    final given = borderRadius ?? KitSurfaceDecoration.radiusOf(solid);
    final pill = tokens.pillRadius;
    if (pill.isFinite && given != null && given.topLeft.x >= _stadiumRadius) {
      borderRadius = BorderRadius.circular(pill);
    }
    final look = resolve(
      roles: tokens.roles,
      style: style,
      role: role,
      solid: solid,
      level: level,
      borderRadius: borderRadius,
      circle: circle,
      color: color,
      selected: selected,
      pressed: pressed,
      hovered: hovered,
      disabled: disabled,
      withinGroup: withinGroup,
      action: action,
      knobs: knobs,
    );
    if (look is! KitSurfaceDecoration) return look;
    final scope = context
        .dependOnInheritedWidgetOfExactType<_KitSurfaceScope>();
    if (scope == null) return look;
    final spec = kitStyleSpec(style).tokens;
    if (scope.hosts &&
        role == KitSurfaceRole.content &&
        !spec.keepsNestedSurfaces) {
      return selected || pressed ? look.withoutCast : look.nested;
    }
    if (scope.casts && spec.flattensNested) return look.withoutCast;
    return look;
  }

  /// Marks [child] as inside [decoration] for a surface painted with a
  /// plain [DecoratedBox] instead of [kitSurfaceBox] (the request card):
  /// the surfaces in it follow the nesting rule and hook H9 ([decorate]).
  /// [child] itself under a style that keeps nested surfaces as they are
  /// (Solid).
  static Widget castScope(
    BuildContext context, {
    required Decoration decoration,
    required Widget child,
  }) {
    final tokens = kitStyleSpec(styleOf(context)).tokens;
    final flattens = tokens.flattensNested;
    final nests = !tokens.keepsNestedSurfaces;
    if (!flattens && !nests) return child;
    final outer = context
        .dependOnInheritedWidgetOfExactType<_KitSurfaceScope>();
    final surface = decoration is KitSurfaceDecoration ? decoration : null;
    return _KitSurfaceScope(
      casts: flattens && ((outer?.casts ?? false) || (surface?.casts ?? false)),
      hosts: nests && (surface?.hosts ?? false),
      child: child,
    );
  }

  /// A divider line here (the `divider` role): the line's [color] and
  /// [width] in this style. A transparent colour means the style draws no
  /// line (Soft: space only); keep the space the line took. Solid at its
  /// defaults returns both unchanged. [withinGroup]: a line between the
  /// rows of one group (inset to the words), which a style may part by
  /// space alone while it rules between groups (Brutal).
  static ({Color color, double width}) divider(
    BuildContext context, {
    required Color color,
    required double width,
    bool withinGroup = false,
  }) {
    final look = decorate(
      context,
      solid: BoxDecoration(color: color),
      role: KitSurfaceRole.divider,
      withinGroup: withinGroup,
    );
    final styled = kitStyleSpec(styleOf(context)).tokens.dividerWidth;
    return switch (look) {
      KitSurfaceDecoration(:final fill) => (
        color: fill,
        width: fill.a > 0 ? styled ?? width : width,
      ),
      BoxDecoration(color: final line?) => (color: line, width: width),
      _ => (color: color, width: width),
    };
  }

  /// The rule above a section's label here (the `divider` role asked with
  /// `selected: true`), or null when the style draws none: only a style
  /// that answers it apart from a row's divider draws one (Paper). Its
  /// width is that answer's edge width, or [hairline].
  static ({Color color, double width})? sectionRule(
    BuildContext context, {
    required double hairline,
  }) {
    const none = KitSurfaceDecoration(fill: Colors.transparent);
    final row = decorate(context, solid: none, role: KitSurfaceRole.divider);
    final section = decorate(
      context,
      solid: none,
      role: KitSurfaceRole.divider,
      selected: true,
    );
    if (section is! KitSurfaceDecoration || section == row) return null;
    if (section.fill.a == 0) return null;
    return (
      color: section.fill,
      width: section.edgeWidth > 0 ? section.edgeWidth : hairline,
    );
  }

  /// The underline under a tertiary action's words here, as a multiple of
  /// the words' own stroke, or null for none
  /// ([KitStyleTokens.linkUnderline]; by default as the `divider` role
  /// draws a line). Solid at its defaults draws none.
  static double? linkUnderline(BuildContext context) {
    final own = kitStyleSpec(styleOf(context)).tokens.linkUnderline;
    if (own != null) return own > 0 ? own : null;
    final line = divider(context, color: Colors.transparent, width: 1);
    return line.color.a > 0 ? line.width : null;
  }

  static final KitKnobs _solidDefaults = kitStyleSpec(
    KitSurfaceStyle.solid,
  ).tokens.defaults;

  /// [decorate] without a context, for tests and painters. [knobs] null
  /// takes [style]'s defaults.
  static Decoration resolve({
    required ThemeRoles roles,
    required KitSurfaceStyle style,
    required KitSurfaceRole role,
    required Decoration solid,
    KitSurfaceLevel level = KitSurfaceLevel.surface1,
    BorderRadius? borderRadius,
    bool circle = false,
    Color? color,
    bool selected = false,
    bool pressed = false,
    bool hovered = false,
    bool disabled = false,
    bool withinGroup = false,
    double edgeWidth = 1,
    KitKnobs? knobs,
    bool action = false,
  }) {
    final radius =
        borderRadius ??
        KitSurfaceDecoration.radiusOf(solid) ??
        BorderRadius.zero;
    final isCircle = circle || KitSurfaceDecoration.isCircle(solid);
    final spec = kitStyleSpec(style);
    final request = KitSurfaceRequest(
      roles: roles,
      role: role,
      solid: solid,
      knobs: knobs ?? spec.tokens.defaults,
      level: level,
      radius: radius,
      circle: isCircle,
      color: color,
      selected: selected,
      pressed: pressed,
      hovered: hovered,
      disabled: disabled,
      withinGroup: withinGroup,
      edgeWidth: edgeWidth,
      action: action,
    );
    // A highlight on a control's own fill (the accent) is the part's own
    // unless the style answers it apart from its face's highlight.
    if (role == KitSurfaceRole.stateLayer && color != null) {
      return spec.resolveFilledLayer(request) ?? solid;
    }
    final look = spec.resolve(request);
    if (look is! KitSurfaceDecoration) return look;
    // What every style keeps, whatever its spec says: the page is its fill
    // alone and never casts; code stays opaque; content hosts the surfaces
    // nested in it (the nesting rule, [decorate]).
    return switch (role) {
      KitSurfaceRole.ground => look.fillOnly,
      KitSurfaceRole.code => look.opaqueOver(roles.ground),
      KitSurfaceRole.content => look.hosting,
      _ => look,
    };
  }

  /// The words' colour on a surface here (hook H3): the style's
  /// [KitStyleSpec.onColor], or [fallback] (the part's own colour). Solid
  /// always keeps [fallback].
  static Color wordsOn(
    BuildContext context, {
    required Color fallback,
    KitSurfaceRole role = KitSurfaceRole.control,
    KitSurfaceLevel level = KitSurfaceLevel.surface3,
    Color? color,
    bool selected = false,
    bool pressed = false,
    bool action = false,
  }) {
    final spec = kitStyleSpec(styleOf(context));
    return spec.onColor(
          KitSurfaceRequest(
            roles: KitTokens.of(context).roles,
            role: role,
            solid: const BoxDecoration(),
            knobs: KitStyles.knobsOf(context),
            level: level,
            color: color,
            selected: selected,
            pressed: pressed,
            action: action,
          ),
        ) ??
        fallback;
  }

  /// Corners that make any box a stadium.
  static const stadium = BorderRadius.all(Radius.circular(_stadiumRadius));
  static const double _stadiumRadius = 9999;

  /// Soft's depth per role ([KitStyleSoft.depthOf]).
  static ({double distance, double blur}) softDepth(KitSurfaceRole role) =>
      KitStyleSoft.depthOf(role);
}

/// The decoration non-solid surfaces paint: a fill with a lit
/// top-start corner, paired soft shadows raised out of or pressed into the
/// ground, drop shadows, and an inner edge that fades from the top start
/// to the bottom end. The light comes from the top start, so it mirrors in
/// right-to-left. Lerps with itself and with a plain [BoxDecoration] or
/// [ShapeDecoration], so a change of style cross-fades.
@immutable
class KitSurfaceDecoration extends Decoration {
  const KitSurfaceDecoration({
    this.borderRadius = BorderRadius.zero,
    this.circle = false,
    required this.fill,
    Color? fillStart,
    this.edgeStart = Colors.transparent,
    this.edgeEnd = Colors.transparent,
    this.edgeWidth = 0,
    this.lightShadow = Colors.transparent,
    this.darkShadow = Colors.transparent,
    this.distance = 0,
    this.blur = 0,
    this.inset = 0,
    this.shadows = const [],
    this.hardShadow = Colors.transparent,
    this.hardOffset = 0,
    this.faceShift = 0,
    this.hosts = false,
  }) : fillStart = fillStart ?? fill;

  final BorderRadius borderRadius;
  final bool circle;
  final Color fill;

  /// The fill at the top-start corner; it fades to [fill] at the bottom end.
  final Color fillStart;

  /// The inner edge, at the top start and at the bottom end.
  final Color edgeStart;
  final Color edgeEnd;
  final double edgeWidth;

  /// The paired soft shadows: [lightShadow] towards the top start,
  /// [darkShadow] towards the bottom end, [distance] apart and [blur] soft.
  final Color lightShadow;
  final Color darkShadow;
  final double distance;
  final double blur;

  /// 0 raised out of the ground, 1 pressed into it.
  final double inset;

  /// Plain drop shadows under the surface.
  final List<BoxShadow> shadows;

  /// A hard shadow with no blur (Brutal): the surface's shape in
  /// [hardShadow], [hardOffset] towards the bottom end (mirrored in
  /// right-to-left, like the soft pair).
  final Color hardShadow;
  final double hardOffset;

  /// How far the face (fill, edge, pressed-in shadows) and the content sit
  /// moved towards the bottom end (hook H2): a pressed Brutal control sinks
  /// into its hard shadow. The hit box never moves.
  final double faceShift;

  /// Whether a `content` surface inside this one sits on this one's face
  /// (the nesting rule, [KitSurfaceLook.decorate]). The resolver sets it on
  /// every `content` surface; a style's spec never needs to.
  final bool hosts;

  /// The corners of a plain decoration, or null when it has none it can say.
  static BorderRadius? radiusOf(Decoration decoration) {
    if (decoration is KitSurfaceDecoration) return decoration.borderRadius;
    if (decoration is BoxDecoration) {
      final r = decoration.borderRadius;
      if (r is BorderRadius) return r;
      if (r != null) return r.resolve(TextDirection.ltr);
      return decoration.shape == BoxShape.rectangle ? BorderRadius.zero : null;
    }
    if (decoration is ShapeDecoration) {
      final shape = decoration.shape;
      if (shape is RoundedRectangleBorder) {
        return shape.borderRadius.resolve(TextDirection.ltr);
      }
      if (shape is StadiumBorder) return KitSurfaceLook.stadium;
    }
    return null;
  }

  /// Whether [decoration] paints nothing around what it holds: no fill,
  /// edge or shadow (a panel the style sets straight on the page, Paper's
  /// row groups). A plain decoration always paints something.
  static bool isBare(Decoration decoration) => switch (decoration) {
    KitSurfaceDecoration(
      :final fill,
      :final fillStart,
      :final edgeStart,
      :final edgeEnd,
      :final edgeWidth,
      :final shadows,
      :final hardShadow,
      :final distance,
    ) =>
      fill.a == 0 &&
          fillStart.a == 0 &&
          (edgeWidth == 0 || (edgeStart.a == 0 && edgeEnd.a == 0)) &&
          shadows.isEmpty &&
          hardShadow.a == 0 &&
          distance == 0,
    _ => false,
  };

  static bool isCircle(Decoration decoration) => switch (decoration) {
    KitSurfaceDecoration(:final circle) => circle,
    BoxDecoration(:final shape) => shape == BoxShape.circle,
    ShapeDecoration(:final shape) => shape is CircleBorder,
    _ => false,
  };

  /// The same look as a plain [decoration], so the two can lerp; null for
  /// one that has an image, a gradient or an uneven border.
  static KitSurfaceDecoration? from(Decoration? decoration) {
    switch (decoration) {
      case null:
        return null;
      case KitSurfaceDecoration():
        return decoration;
      case BoxDecoration(
        :final color,
        :final border,
        :final boxShadow,
        gradient: null,
        image: null,
      ):
        Color edge = Colors.transparent;
        var width = 0.0;
        if (border != null) {
          if (border is! Border || !border.isUniform) return null;
          edge = border.top.color;
          width = border.top.width;
        }
        return KitSurfaceDecoration(
          borderRadius: radiusOf(decoration) ?? BorderRadius.zero,
          circle: decoration.shape == BoxShape.circle,
          fill: color ?? Colors.transparent,
          edgeStart: edge,
          edgeEnd: edge,
          edgeWidth: width,
          shadows: boxShadow ?? const [],
        );
      case ShapeDecoration(
        :final color,
        :final shape,
        :final shadows,
        gradient: null,
        image: null,
      ):
        final side = shape is OutlinedBorder ? shape.side : BorderSide.none;
        final radius = radiusOf(decoration);
        final circle = shape is CircleBorder;
        if (radius == null && !circle) return null;
        final edge = side.style == BorderStyle.none
            ? Colors.transparent
            : side.color;
        return KitSurfaceDecoration(
          borderRadius: radius ?? BorderRadius.zero,
          circle: circle,
          fill: color ?? Colors.transparent,
          edgeStart: edge,
          edgeEnd: edge,
          edgeWidth: side.style == BorderStyle.none ? 0 : side.width,
          shadows: shadows ?? const [],
        );
      default:
        return null;
    }
  }

  static KitSurfaceDecoration lerpSurface(
    KitSurfaceDecoration a,
    KitSurfaceDecoration b,
    double t,
  ) {
    Color c(Color x, Color y) => Color.lerp(x, y, t)!;
    double d(double x, double y) => ui.lerpDouble(x, y, t)!;
    return KitSurfaceDecoration(
      borderRadius: BorderRadius.lerp(a.borderRadius, b.borderRadius, t)!,
      circle: t < .5 ? a.circle : b.circle,
      fill: c(a.fill, b.fill),
      fillStart: c(a.fillStart, b.fillStart),
      edgeStart: c(a.edgeStart, b.edgeStart),
      edgeEnd: c(a.edgeEnd, b.edgeEnd),
      edgeWidth: d(a.edgeWidth, b.edgeWidth),
      // A shadow fades rather than slides in from nothing.
      lightShadow: c(a.lightShadow, b.lightShadow),
      darkShadow: c(a.darkShadow, b.darkShadow),
      distance: a.distance == 0
          ? b.distance
          : b.distance == 0
          ? a.distance
          : d(a.distance, b.distance),
      blur: a.blur == 0
          ? b.blur
          : b.blur == 0
          ? a.blur
          : d(a.blur, b.blur),
      inset: d(a.inset, b.inset),
      shadows: BoxShadow.lerpList(a.shadows, b.shadows, t) ?? const [],
      hardShadow: c(a.hardShadow, b.hardShadow),
      hardOffset: a.hardOffset == 0
          ? b.hardOffset
          : b.hardOffset == 0
          ? a.hardOffset
          : d(a.hardOffset, b.hardOffset),
      faceShift: d(a.faceShift, b.faceShift),
      hosts: t < .5 ? a.hosts : b.hosts,
    );
  }

  @override
  Decoration? lerpFrom(Decoration? a, double t) {
    final from = a == null ? copyTransparent() : KitSurfaceDecoration.from(a);
    if (from == null) return super.lerpFrom(a, t);
    return lerpSurface(from, this, t);
  }

  @override
  Decoration? lerpTo(Decoration? b, double t) {
    final to = b == null ? copyTransparent() : KitSurfaceDecoration.from(b);
    if (to == null) return super.lerpTo(b, t);
    return lerpSurface(this, to, t);
  }

  /// The same with nothing falling outside the surface (hook H9: a surface
  /// nested in one that casts).
  KitSurfaceDecoration get withoutCast => KitSurfaceDecoration(
    borderRadius: borderRadius,
    circle: circle,
    fill: fill,
    fillStart: fillStart,
    edgeStart: edgeStart,
    edgeEnd: edgeEnd,
    edgeWidth: edgeWidth,
    lightShadow: inset > 0 ? lightShadow : Colors.transparent,
    darkShadow: inset > 0 ? darkShadow : Colors.transparent,
    distance: inset > 0 ? distance : 0,
    blur: inset > 0 ? blur : 0,
    inset: inset,
    faceShift: faceShift,
    hosts: hosts,
  );

  /// A surface nested in one that hosts it (the nesting rule): nothing of
  /// its own, so what it holds sits on the outer surface's face. It keeps
  /// its shape (a style change still fades in place) and goes on hosting.
  KitSurfaceDecoration get nested => KitSurfaceDecoration(
    borderRadius: borderRadius,
    circle: circle,
    fill: fill.withValues(alpha: 0),
    hosts: hosts,
  );

  /// The fill alone: no edge, no shadow (the page, which never casts and
  /// hosts nothing).
  KitSurfaceDecoration get fillOnly => KitSurfaceDecoration(
    borderRadius: borderRadius,
    circle: circle,
    fill: fill,
    fillStart: fillStart,
  );

  /// The same with its fill made opaque over [ground] (code surfaces).
  KitSurfaceDecoration opaqueOver(Color ground) {
    Color solid(Color c) =>
        c.a >= 1 ? c : Color.alphaBlend(c, ground.withValues(alpha: 1));
    if (fill.a >= 1 && fillStart.a >= 1) return this;
    return KitSurfaceDecoration(
      borderRadius: borderRadius,
      circle: circle,
      fill: solid(fill),
      fillStart: solid(fillStart),
      edgeStart: edgeStart,
      edgeEnd: edgeEnd,
      edgeWidth: edgeWidth,
      lightShadow: lightShadow,
      darkShadow: darkShadow,
      distance: distance,
      blur: blur,
      inset: inset,
      shadows: shadows,
      hardShadow: hardShadow,
      hardOffset: hardOffset,
      faceShift: faceShift,
      hosts: hosts,
    );
  }

  /// The same, hosting the surfaces nested in it ([hosts]).
  KitSurfaceDecoration get hosting => hosts
      ? this
      : KitSurfaceDecoration(
          borderRadius: borderRadius,
          circle: circle,
          fill: fill,
          fillStart: fillStart,
          edgeStart: edgeStart,
          edgeEnd: edgeEnd,
          edgeWidth: edgeWidth,
          lightShadow: lightShadow,
          darkShadow: darkShadow,
          distance: distance,
          blur: blur,
          inset: inset,
          shadows: shadows,
          hardShadow: hardShadow,
          hardOffset: hardOffset,
          faceShift: faceShift,
          hosts: true,
        );

  /// Whether anything falls outside the surface.
  bool get casts =>
      (inset < 1 && distance > 0 && (lightShadow.a > 0 || darkShadow.a > 0)) ||
      shadows.any((s) => s.color.a > 0) ||
      (hardOffset > 0 && hardShadow.a > 0);

  /// This shape with nothing painted.
  KitSurfaceDecoration copyTransparent() => KitSurfaceDecoration(
    borderRadius: borderRadius,
    circle: circle,
    fill: fill.withValues(alpha: 0),
    distance: distance,
    blur: blur,
    inset: inset,
    hardOffset: hardOffset,
  );

  @override
  bool get isComplex => blur > 0 || shadows.isNotEmpty;

  @override
  Path getClipPath(Rect rect, TextDirection textDirection) =>
      Path()..addRRect(_rrect(rect));

  RRect _rrect(Rect rect) {
    if (circle) {
      return RRect.fromRectAndRadius(
        Rect.fromCircle(center: rect.center, radius: rect.shortestSide / 2),
        Radius.circular(rect.shortestSide / 2),
      );
    }
    return borderRadius.toRRect(rect).scaleRadii();
  }

  @override
  bool hitTest(Size size, Offset position, {TextDirection? textDirection}) =>
      _rrect(Offset.zero & size).contains(position);

  @override
  BoxPainter createBoxPainter([VoidCallback? onChanged]) =>
      _KitSurfacePainter(this, onChanged);

  @override
  bool operator ==(Object other) =>
      other is KitSurfaceDecoration &&
      other.borderRadius == borderRadius &&
      other.circle == circle &&
      other.fill == fill &&
      other.fillStart == fillStart &&
      other.edgeStart == edgeStart &&
      other.edgeEnd == edgeEnd &&
      other.edgeWidth == edgeWidth &&
      other.lightShadow == lightShadow &&
      other.darkShadow == darkShadow &&
      other.distance == distance &&
      other.blur == blur &&
      other.inset == inset &&
      other.hardShadow == hardShadow &&
      other.hardOffset == hardOffset &&
      other.faceShift == faceShift &&
      other.hosts == hosts &&
      _sameShadows(other.shadows, shadows);

  static bool _sameShadows(List<BoxShadow> a, List<BoxShadow> b) {
    if (a.length != b.length) return false;
    for (var i = 0; i < a.length; i++) {
      if (a[i] != b[i]) return false;
    }
    return true;
  }

  @override
  int get hashCode => Object.hash(
    borderRadius,
    circle,
    fill,
    fillStart,
    edgeStart,
    edgeEnd,
    edgeWidth,
    lightShadow,
    darkShadow,
    distance,
    blur,
    inset,
    Object.hashAll(shadows),
    hardShadow,
    hardOffset,
    faceShift,
    hosts,
  );
}

/// Kit-internal (screens use the kit parts): paints [decoration] behind [child] and cross-fades to a new one: a
/// change of [style] over [KitSurfaceLook.styleFade], any other change over
/// [KitSurfaceLook.stateFade]; at once when motion is reduced. At rest it
/// paints [decoration] itself, so a solid part looks exactly as before.
Widget kitSurfaceBox({
  Key? key,
  required Decoration decoration,
  required Widget child,
  KitSurfaceStyle? style,
  DecorationPosition position = DecorationPosition.background,
}) => _KitSurfaceBox(
  key: key,
  decoration: decoration,
  style: style,
  position: position,
  child: child,
);

/// [kitSurfaceBox] for a sliver, as [DecoratedSliver] (a grouped list's
/// panel).
Widget kitSurfaceSliver({
  Key? key,
  required Decoration decoration,
  required Widget sliver,
  KitSurfaceStyle? style,
}) => _KitSurfaceBox.sliver(
  key: key,
  decoration: decoration,
  style: style,
  sliver: sliver,
);

class _KitSurfaceBox extends StatefulWidget {
  const _KitSurfaceBox({
    super.key,
    required this.decoration,
    required this.child,
    this.style,
    this.position = DecorationPosition.background,
  }) : _sliver = false;

  const _KitSurfaceBox.sliver({
    super.key,
    required this.decoration,
    required Widget sliver,
    this.style,
  }) : child = sliver,
       position = DecorationPosition.background,
       _sliver = true;

  final Decoration decoration;
  final Widget child;

  /// The style [decoration] was made for; a new one fades slower.
  final KitSurfaceStyle? style;
  final DecorationPosition position;
  final bool _sliver;

  @override
  State<_KitSurfaceBox> createState() => _KitSurfaceBoxState();
}

class _KitSurfaceBoxState extends State<_KitSurfaceBox>
    with SingleTickerProviderStateMixin {
  AnimationController? _controller;
  DecorationTween? _tween;

  @override
  void didUpdateWidget(_KitSurfaceBox old) {
    super.didUpdateWidget(old);
    if (old.decoration == widget.decoration) return;
    final reduced = MediaQuery.maybeDisableAnimationsOf(context) ?? false;
    final motionOff = KitEffects.of(context).motion == KitMotionLevel.off;
    // Solid parts change state at once, as they always have.
    final solidStays =
        old.style == widget.style &&
        (widget.style == null || widget.style == KitSurfaceStyle.solid);
    if (reduced || motionOff || solidStays) {
      _tween = null;
      _controller?.stop();
      return;
    }
    final from = _current(old.decoration);
    final controller = _controller ??= AnimationController(vsync: this)
      ..addListener(() => setState(() {}));
    controller.duration = old.style != widget.style
        ? KitSurfaceLook.styleFade
        : KitSurfaceLook.stateFade;
    _tween = DecorationTween(begin: from, end: widget.decoration);
    controller.forward(from: 0);
  }

  Decoration _current(Decoration fallback) {
    final tween = _tween;
    final controller = _controller;
    if (tween == null || controller == null || !controller.isAnimating) {
      return fallback;
    }
    return tween.evaluate(
      CurvedAnimation(parent: controller, curve: KitMotion.pulse),
    );
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final controller = _controller;
    final tween = _tween;
    var decoration =
        tween != null && controller != null && controller.isAnimating
        ? tween.transform(KitMotion.pulse.transform(controller.value))
        : widget.decoration;
    var child = widget.child;
    final settled = widget.decoration;
    if (decoration is KitSurfaceDecoration) {
      final style = widget.style ?? KitSurfaceLook.styleOf(context);
      final tokens = kitStyleSpec(style).tokens;
      final flattens = tokens.flattensNested;
      final nests = !tokens.keepsNestedSurfaces;
      if (flattens || nests) {
        final outer = context
            .dependOnInheritedWidgetOfExactType<_KitSurfaceScope>();
        // Hook H9: inside a surface that casts, a style that flattens
        // nested surfaces keeps only the outer one's shadow.
        if (flattens && outer != null && outer.casts) {
          decoration = decoration.withoutCast;
        }
        // The nesting rule reads the surface this box settles on, so a
        // state fade never flips what is inside. Always the same wrapper,
        // so a press (which drops the shadow) never rebuilds what is
        // inside.
        child = _KitSurfaceScope(
          casts: flattens && ((outer?.casts ?? false) || decoration.casts),
          hosts: nests && settled is KitSurfaceDecoration && settled.hosts,
          child: child,
        );
      }
      // Hook H2: the content sinks with the face; hit testing stays put.
      // Always present on a styled box (zero at rest), so a press never
      // rebuilds what is inside.
      if (!widget._sliver) {
        final rtl = Directionality.maybeOf(context) == TextDirection.rtl;
        child = Transform.translate(
          offset: Offset(rtl ? -1 : 1, 1) * decoration.faceShift,
          transformHitTests: false,
          child: child,
        );
      }
    }
    if (widget._sliver) {
      return DecoratedSliver(decoration: decoration, sliver: child);
    }
    return DecoratedBox(
      decoration: decoration,
      position: widget.position,
      child: child,
    );
  }
}

/// Marks the inside of a surface: whether it (or one around it) casts a
/// shadow (hook H9), and whether it hosts the content surfaces nested in it
/// (the nesting rule).
class _KitSurfaceScope extends InheritedWidget {
  const _KitSurfaceScope({
    required this.casts,
    required this.hosts,
    required super.child,
  });

  /// Whether this surface (or one around it) throws a shadow.
  final bool casts;

  /// Whether a content surface in it sits on its face.
  final bool hosts;

  @override
  bool updateShouldNotify(_KitSurfaceScope old) =>
      old.casts != casts || old.hosts != hosts;
}
