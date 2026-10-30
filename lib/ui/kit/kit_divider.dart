import 'package:flutter/material.dart';

import 'kit_surface_look.dart';
import 'kit_tokens.dart';

/// Where a horizontal [KitDivider] starts (visual language §4: "separators
/// are inset to the text start").
enum KitDividerInset {
  /// Edge to edge: between sections, under a header.
  none,

  /// From [KitTokens.space4]: rows with no leading icon.
  gutter,

  /// From `space4 + leadColumn` (56 on a page): rows with an icon tile.
  text,
}

/// The one separator (docs/ux-system/kit-api/KitDivider.md): a hairline
/// exactly one physical pixel thick, snapped to the pixel grid so it is
/// never smeared over two device-pixel rows, and optionally inset to where
/// a row's words start.
///
/// Replaces `Divider` and `VerticalDivider` everywhere in the app, so no
/// separator is ever doubled, soft, or a different grey. Decorative only:
/// it carries no state, is excluded from semantics, and is never
/// interactive or focusable.
///
/// States: none — a static hairline between things.
class KitDivider extends StatelessWidget {
  /// A horizontal hairline. Its layout extent is exactly one physical
  /// pixel; the spacing around it is the host's, from tokens.
  const KitDivider({
    super.key,
    this.inset = KitDividerInset.none,
    this.endInset = 0,
  }) : assert(endInset >= 0),
       _axis = Axis.horizontal,
       _section = false;

  /// A vertical hairline between items in a row, such as a toolbar's
  /// groups. It is as tall as its parent allows.
  const KitDivider.vertical({super.key})
    : inset = KitDividerInset.none,
      endInset = 0,
      _axis = Axis.vertical,
      _section = false;

  /// The rule above a section's label, edge to edge: the `divider` role
  /// asked with `selected: true`. A style draws it only when it answers
  /// that apart from a row's divider (Paper's section rule); otherwise it
  /// takes no space at all, so every other style lays out as before.
  const KitDivider.section({super.key})
    : inset = KitDividerInset.none,
      endInset = 0,
      _axis = Axis.horizontal,
      _section = true;

  /// Where the line starts. Ignored on [KitDivider.vertical], which has no
  /// direction.
  final KitDividerInset inset;

  /// How far the line stops short of its end edge. A row group whose rows
  /// reach out into the page margin (Paper) passes that reach, so its lines
  /// still end at the margin, where the section rule ends.
  final double endInset;

  final Axis _axis;
  final bool _section;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    // The thickness is the kit's one hairline token (LOOK-21); the device
    // pixel ratio is kept only to snap the line's position to the grid.
    // The line follows the style (the `divider` role): Brutal's ink is
    // thicker, Soft draws none and keeps the space.
    final hairline = KitTokens.hairlineWidth(context);
    // An inset line parts the rows of one group; an edge-to-edge one parts
    // groups and sections (a style may rule only those: Brutal).
    final rule = _section
        ? KitSurfaceLook.sectionRule(context, hairline: hairline)
        : KitSurfaceLook.divider(
            context,
            color: tokens.roles.hairline,
            width: hairline,
            withinGroup:
                _axis == Axis.horizontal && inset != KitDividerInset.none,
          );
    if (rule == null) return const SizedBox.shrink();
    final thickness = rule.width;
    final dpr = MediaQuery.devicePixelRatioOf(context);
    // TEST-5: an internal key so the part's own tests can find the painted
    // line itself, unambiguously, regardless of the inset padding around
    // it (and never confused with an unrelated ExcludeSemantics, which
    // Text and Icon also wrap themselves in).
    final line = ExcludeSemantics(
      key: const ValueKey('kit-divider-line'),
      child: _KitHairline(
        axis: _axis,
        thickness: thickness,
        devicePixelRatio: dpr,
        color: rule.color,
      ),
    );
    if (_axis == Axis.vertical) return line;

    final double start = switch (inset) {
      KitDividerInset.none => 0.0,
      KitDividerInset.gutter => tokens.space4,
      KitDividerInset.text => tokens.space4 + tokens.leadColumn,
    };
    if (start == 0.0 && endInset == 0.0) return line;
    return Padding(
      padding: EdgeInsetsDirectional.only(start: start, end: endInset),
      child: line,
    );
  }
}

/// Paints one hairline [thickness] thick ([KitTokens.hairlineWidth]), its
/// position rounded to the physical pixel grid at paint time (LOOK-21: "a
/// doubled hairline or a half-pixel offset is a bug"). A filled rectangle,
/// not a stroke, so its edges never antialias across two device-pixel rows.
class _KitHairline extends LeafRenderObjectWidget {
  const _KitHairline({
    required this.axis,
    required this.thickness,
    required this.devicePixelRatio,
    required this.color,
  });

  final Axis axis;

  /// The line's layout extent across its axis, in logical pixels.
  final double thickness;

  /// Used only to snap the paint offset to the device-pixel grid.
  final double devicePixelRatio;
  final Color color;

  @override
  RenderObject createRenderObject(BuildContext context) => _RenderKitHairline(
    axis: axis,
    thickness: thickness,
    devicePixelRatio: devicePixelRatio,
    color: color,
  );

  @override
  void updateRenderObject(
    BuildContext context,
    covariant _RenderKitHairline renderObject,
  ) {
    renderObject
      ..axis = axis
      ..thickness = thickness
      ..devicePixelRatio = devicePixelRatio
      ..color = color;
  }
}

class _RenderKitHairline extends RenderBox {
  _RenderKitHairline({
    required Axis axis,
    required double thickness,
    required double devicePixelRatio,
    required Color color,
  }) : _axis = axis,
       _thickness = thickness,
       _devicePixelRatio = devicePixelRatio,
       _color = color;

  Axis _axis;
  set axis(Axis value) {
    if (_axis == value) return;
    _axis = value;
    markNeedsLayout();
  }

  /// One physical pixel in logical pixels, from [KitTokens.hairlineWidth].
  double _thickness;
  set thickness(double value) {
    if (_thickness == value) return;
    _thickness = value;
    markNeedsLayout();
  }

  /// Only the grid snap reads it, at paint time.
  double _devicePixelRatio;
  set devicePixelRatio(double value) {
    if (_devicePixelRatio == value) return;
    _devicePixelRatio = value;
    markNeedsPaint();
  }

  Color _color;
  set color(Color value) {
    if (_color == value) return;
    _color = value;
    markNeedsPaint();
  }

  @override
  double computeMinIntrinsicWidth(double height) =>
      _axis == Axis.vertical ? _thickness : 0;

  @override
  double computeMaxIntrinsicWidth(double height) =>
      _axis == Axis.vertical ? _thickness : 0;

  @override
  double computeMinIntrinsicHeight(double width) =>
      _axis == Axis.horizontal ? _thickness : 0;

  @override
  double computeMaxIntrinsicHeight(double width) =>
      _axis == Axis.horizontal ? _thickness : 0;

  @override
  Size computeDryLayout(BoxConstraints constraints) => _size(constraints);

  Size _size(BoxConstraints constraints) {
    if (_axis == Axis.horizontal) {
      final width = constraints.hasBoundedWidth
          ? constraints.maxWidth
          : constraints.minWidth;
      return constraints.constrain(Size(width, _thickness));
    }
    final height = constraints.hasBoundedHeight
        ? constraints.maxHeight
        : constraints.minHeight;
    return constraints.constrain(Size(_thickness, height));
  }

  @override
  void performLayout() {
    size = _size(constraints);
  }

  /// Decorative only: never hit-tested.
  @override
  bool hitTestSelf(Offset position) => false;

  @override
  void paint(PaintingContext context, Offset offset) {
    final paint = Paint()
      ..color = _color
      ..style = PaintingStyle.fill;
    final Rect rect;
    if (_axis == Axis.horizontal) {
      rect = Rect.fromLTWH(offset.dx, _snap(offset.dy), size.width, _thickness);
    } else {
      rect = Rect.fromLTWH(
        _snap(offset.dx),
        offset.dy,
        _thickness,
        size.height,
      );
    }
    context.canvas.drawRect(rect, paint);
  }

  /// Rounds a logical-pixel coordinate to the nearest device-pixel
  /// boundary, so the hairline's start (and, since [_thickness] is exactly
  /// one device pixel, its end) lands on the grid.
  double _snap(double logical) {
    if (_devicePixelRatio <= 0) return logical;
    return (logical * _devicePixelRatio).round() / _devicePixelRatio;
  }
}
