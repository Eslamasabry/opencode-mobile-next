import 'package:flutter/material.dart';

import '../theme_roles.dart';
import 'kit_effects.dart';
import 'kit_surface_look.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// How much inside space a [KitSurface] gives its child
/// (docs/ux-system/kit-api/KitSurface.md): none (0), compact
/// ([KitTokens.space3], 12) or panel ([KitTokens.space4], 16).
enum KitSurfacePadding { none, compact, panel }

/// The one solid box (KitSurface.md; visual language §4): a fill from the
/// surface steps ([KitSurfaceLevel]), a token [shape] and token [padding],
/// with an optional hairline [outlined] edge (LOOK-21). Depth comes from the
/// surface steps, never from a shadow — a surface never casts one.
///
/// Contract resolution (STANDARDS §0.2, KIT-42, Appendix A #24 and #41):
/// there is no "raised", "tonal" or "glass" level. "Raised" is `surface1`
/// (the panel look); the attention look belongs only to `KitNeedsYou`,
/// `KitRequestCard` and `KitNotice.card`; glass is not a level at all —
/// `KitGlass` stays the one glass part and this widget never wraps it.
///
/// Not interactive: a surface that opens something wraps in `KitTappable`
/// (`KitTappable(shape: KitShape.panel, child: KitSurface.panel(...))`), so
/// the hover, pressed and focus looks follow the same shape. KitSurface
/// itself paints through a `Material` of type canvas at elevation 0, so a
/// descendant that needs a Material ancestor (a field, a menu anchor) keeps
/// working.
///
/// States: none — a surface only draws its own fill and shape.
class KitSurface extends StatelessWidget {
  const KitSurface({
    super.key,
    required this.child,
    this.level = KitSurfaceLevel.surface1,
    this.shape = KitShape.panel,
    this.padding = KitSurfacePadding.panel,
    this.outlined = false,
    this.clip = true,
    this.flatInLists = false,
  }) : _panelTitle = null,
       _panelIcon = null,
       _panelTitleKey = null,
       _inset = false,
       _tileIcon = null,
       _tileTone = null,
       _tileSemanticsLabel = null;

  /// A grouped panel (VL §5; `KitPanel`'s look): `surface1`, the panel
  /// shape, panel padding, and an optional one-line header — a
  /// [KitTokens.smallIconSize] [icon] in `text2` and a headline [title] in
  /// `text1` — above [child].
  const KitSurface.panel({
    super.key,
    required this.child,
    String? title,
    IconData? icon,
    this.padding = KitSurfacePadding.panel,
    Key? titleKey,
  }) : level = KitSurfaceLevel.surface1,
       shape = KitShape.panel,
       outlined = false,
       clip = true,
       flatInLists = false,
       _panelTitle = title,
       _panelIcon = icon,
       _panelTitleKey = titleKey,
       _inset = false,
       _tileIcon = null,
       _tileTone = null,
       _tileSemanticsLabel = null;

  /// A panel set into a sheet or a dialog, such as a confirmation's
  /// consequences: [KitTokens.insetSurface] (`surface1` in dark, `ground` in
  /// light).
  const KitSurface.inset({
    super.key,
    required this.child,
    this.padding = KitSurfacePadding.panel,
  }) : level = KitSurfaceLevel.surface1,
       shape = KitShape.panel,
       outlined = false,
       clip = true,
       flatInLists = false,
       _panelTitle = null,
       _panelIcon = null,
       _panelTitleKey = null,
       _inset = true,
       _tileIcon = null,
       _tileTone = null,
       _tileSemanticsLabel = null;

  /// A leading icon in its tile (VL §4, LOOK-34): [KitTokens.iconTileSize]
  /// of `surface3` with [KitTokens.iconTileRadius] corners, a
  /// [KitTokens.smallIconSize] glyph in [tone] (`text1` by default). It
  /// replaces tonal circles (LOOK-23).
  const KitSurface.tile(
    IconData icon, {
    super.key,
    KitTextTone? tone,
    String? semanticsLabel,
  }) : child = const SizedBox.shrink(),
       level = KitSurfaceLevel.surface3,
       shape = KitShape.tile,
       padding = KitSurfacePadding.none,
       outlined = false,
       clip = true,
       flatInLists = false,
       _panelTitle = null,
       _panelIcon = null,
       _panelTitleKey = null,
       _inset = false,
       _tileIcon = icon,
       _tileTone = tone,
       _tileSemanticsLabel = semanticsLabel;

  final Widget child;
  final KitSurfaceLevel level;
  final KitShape shape;
  final KitSurfacePadding padding;

  /// A hairline edge, exactly one physical pixel (LOOK-21). Only the default
  /// constructor exposes this; every named form is opinionated.
  final bool outlined;

  /// Clips [child] to [shape] (`ClipRRect`'s job). Only the default
  /// constructor exposes this; every named form clips.
  final bool clip;

  /// A box that holds a page's leading group (Report a problem's summary):
  /// where the style sets its lists flat on the page (Solid), it does too,
  /// with no fill, edge or padding of its own, so its words start on the
  /// page's gutter with everything under it. Every other style draws it as
  /// given. Only the default constructor exposes this.
  final bool flatInLists;

  final String? _panelTitle;
  final IconData? _panelIcon;
  final Key? _panelTitleKey;
  final bool _inset;
  final IconData? _tileIcon;
  final KitTextTone? _tileTone;
  final String? _tileSemanticsLabel;

  /// The decoration of a [level] surface here, for the person's Surfaces
  /// choice ([KitSurfaceLook.decorate]): [solid] unchanged under Solid.
  static Decoration decorate(
    BuildContext context,
    KitSurfaceLevel level, {
    required Decoration solid,
    KitSurfaceRole role = KitSurfaceRole.content,
    BorderRadius? borderRadius,
    Color? color,
    bool selected = false,
    bool pressed = false,
  }) => KitSurfaceLook.decorate(
    context,
    solid: solid,
    level: level,
    role: role,
    borderRadius: borderRadius,
    color: color,
    selected: selected,
    pressed: pressed,
  );

  static EdgeInsetsGeometry _paddingOf(
    KitTokens tokens,
    KitSurfacePadding padding,
  ) => switch (padding) {
    KitSurfacePadding.none => EdgeInsets.zero,
    KitSurfacePadding.compact => EdgeInsetsDirectional.all(tokens.space3),
    KitSurfacePadding.panel => EdgeInsetsDirectional.all(tokens.space4),
  };

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    if (_tileIcon case final tileIcon?) {
      return _buildTile(context, tokens, roles, tileIcon);
    }
    if (flatInLists && KitSurfaceLook.flatLists(context)) {
      // A clear Material, so a field or an ink inside still has one.
      return Material(
        type: MaterialType.transparency,
        child: kitPanelBody(
          tokens,
          title: _panelTitle,
          icon: _panelIcon,
          titleKey: _panelTitleKey,
          child: child,
        ),
      );
    }
    final fill = _inset ? tokens.insetSurface : tokens.fillOf(level);
    final base = tokens.shapeOf(shape);
    final resolvedShape = outlined && base is OutlinedBorder
        ? base.copyWith(
            side: BorderSide(
              color: roles.hairline,
              width: KitTokens.hairlineWidth(context),
            ),
          )
        : base;
    // An inset panel is a well: pressed into the sheet under Soft.
    return kitSurfaceMaterial(
      context,
      level: level,
      shape: resolvedShape,
      fill: fill,
      inset: _inset,
      clip: clip,
      child: _content(tokens),
    );
  }

  Widget _content(KitTokens tokens) => Padding(
    padding: _paddingOf(tokens, padding),
    child: kitPanelBody(
      tokens,
      title: _panelTitle,
      icon: _panelIcon,
      titleKey: _panelTitleKey,
      child: child,
    ),
  );

  Widget _buildTile(
    BuildContext context,
    KitTokens tokens,
    ThemeRoles roles,
    IconData icon,
  ) {
    final color = KitText.toneColor(roles, _tileTone ?? KitTextTone.primary);
    final glyph = Icon(icon, size: tokens.smallIconSize, color: color);
    final label = _tileSemanticsLabel;
    final content = label == null
        ? ExcludeSemantics(child: glyph)
        : Semantics(
            image: true,
            label: label,
            excludeSemantics: true,
            child: glyph,
          );
    return kitSurfaceMaterial(
      context,
      level: level,
      shape: tokens.shapeOf(shape),
      fill: tokens.fillOf(level),
      role: KitSurfaceRole.control,
      child: SizedBox(
        width: tokens.iconTileSize,
        height: tokens.iconTileSize,
        child: Center(child: content),
      ),
    );
  }
}

/// Kit-internal: a canvas [Material] of [fill] in [shape] at elevation 0,
/// as every solid kit surface is painted, made for the person's Style.
/// Under Solid at its defaults ([KitSurfaceLook.isPlain]) it is exactly
/// that Material; otherwise (another style, or Solid with Fine-tune moved)
/// the Material turns clear and a [kitSurfaceBox] paints the resolved
/// surface beneath, both cross-fading, so the child (a field, a menu
/// anchor) keeps its Material ancestor and its state.
///
/// A `ground` [level] surface with the default role hosts a whole page: it
/// resolves as [KitSurfaceRole.ground] (its fill alone, never a shadow), so
/// the surfaces on the page keep theirs.
Widget kitSurfaceMaterial(
  BuildContext context, {
  required KitSurfaceLevel level,
  required ShapeBorder shape,
  required Color fill,
  required Widget child,
  KitSurfaceRole role = KitSurfaceRole.content,
  Color? color,
  bool inset = false,
  bool selected = false,
  bool clip = true,
}) {
  final style = KitSurfaceLook.styleOf(context);
  final solid = KitSurfaceLook.isPlain(context);
  final surfaceRole =
      level == KitSurfaceLevel.ground && role == KitSurfaceRole.content
      ? KitSurfaceRole.ground
      : role;
  // The shape alone, so a change of style fades the corners in place.
  final circle = shape is CircleBorder;
  final outline = BoxDecoration(
    shape: circle ? BoxShape.circle : BoxShape.rectangle,
    borderRadius: circle
        ? null
        : KitSurfaceDecoration.radiusOf(ShapeDecoration(shape: shape)),
  );
  final decoration = solid
      ? outline
      : KitSurfaceLook.decorate(
          context,
          solid: ShapeDecoration(color: fill, shape: shape),
          level: level,
          role: surfaceRole,
          color: color,
          selected: selected,
          pressed: inset,
        );
  final reduced =
      (MediaQuery.maybeDisableAnimationsOf(context) ?? false) ||
      KitEffects.of(context).motion == KitMotionLevel.off;
  return kitSurfaceBox(
    style: style,
    decoration: decoration,
    child: TweenAnimationBuilder<Color?>(
      tween: ColorTween(end: solid ? fill : fill.withValues(alpha: 0)),
      duration: reduced ? Duration.zero : KitSurfaceLook.styleFade,
      builder: (context, color, child) => Material(
        type: MaterialType.canvas,
        color: color,
        elevation: 0,
        shape: solid ? shape : _clearEdge(shape),
        clipBehavior: clip ? Clip.antiAlias : Clip.none,
        child: child,
      ),
      child: child,
    ),
  );
}

ShapeBorder _clearEdge(ShapeBorder shape) =>
    shape is OutlinedBorder ? shape.copyWith(side: BorderSide.none) : shape;

/// Kit-internal, not part of `KitSurface`'s frozen API: the panel header — a
/// [KitTokens.smallIconSize] [icon] in `text2` at the start and a headline
/// [title] — a [KitTokens.labelGap] above [child]; just [child] when there is
/// neither. It is the one source of truth for that header, shared by
/// `KitSurface.panel` and `KitPanel`'s custom-padding / `onTap` path, so a
/// change to the header (such as the planned `Icon` → `KitIcon` swap)
/// reaches both. Screens name `KitSurface.panel`, never this.
Widget kitPanelBody(
  KitTokens tokens, {
  required Widget child,
  String? title,
  IconData? icon,
  Key? titleKey,
}) {
  if (title == null && icon == null) return child;
  return Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    mainAxisSize: MainAxisSize.min,
    children: [
      Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (icon != null) ...[
            Icon(icon, size: tokens.smallIconSize, color: tokens.roles.text2),
            SizedBox(width: tokens.labelGap),
          ],
          if (title != null)
            Expanded(
              child: KitText(title, key: titleKey, role: KitTextRole.headline),
            ),
        ],
      ),
      SizedBox(height: tokens.labelGap),
      child,
    ],
  );
}
