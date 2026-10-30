import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../kit_surface_look.dart';
import '../kit_tokens.dart';

/// A tiny mock chat (a message, some lines, the box) used by the picker's
/// tiles and the full-screen preview, so a look is judged under real-looking
/// content. The message and the box are drawn as the style in force draws
/// them (a raised control, and a floating box), so a preview
/// under Brutal or Paper shows their ink and corners; the look shows
/// between and behind them. [scale] is 1 for the preview and smaller in a
/// tile.
Widget liveMockChat(BuildContext context, {double scale = 1}) {
  final tokens = KitTokens.of(context);
  final roles = tokens.roles;
  final gap = tokens.space2 * scale;
  // A line of words: a pill, the style's sharp box under Corners › Sharp.
  Widget bar(double w, double h, Color c) => Container(
    width: w * scale,
    height: h * scale,
    decoration: BoxDecoration(
      color: c,
      borderRadius: BorderRadius.circular(
        math.min(h * scale / 2, tokens.pillRadius),
      ),
    ),
  );
  Widget surface({
    required KitSurfaceRole role,
    required double radius,
    required Widget child,
    EdgeInsetsGeometry? padding,
    double? height,
    AlignmentGeometry? alignment,
  }) {
    final corners = BorderRadius.circular(radius * scale);
    return SizedBox(
      height: height,
      child: kitSurfaceBox(
        decoration: KitSurfaceLook.decorate(
          context,
          solid: BoxDecoration(color: roles.surface2, borderRadius: corners),
          role: role,
          level: KitSurfaceLevel.surface2,
        ),
        child: Container(padding: padding, alignment: alignment, child: child),
      ),
    );
  }

  return Padding(
    padding: EdgeInsets.all(tokens.space3 * scale),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Align(
          alignment: AlignmentDirectional.centerEnd,
          // A control, not content: the tile is itself a content surface,
          // and a content surface nested in one paints no face of its own.
          child: surface(
            role: KitSurfaceRole.control,
            radius: tokens.buttonRadius,
            padding: EdgeInsets.all(tokens.space2 * scale),
            child: bar(70, 6, roles.text2),
          ),
        ),
        SizedBox(height: gap * 1.5),
        bar(90, 5, roles.text3),
        SizedBox(height: gap),
        bar(130, 6, roles.text2),
        SizedBox(height: gap),
        bar(110, 6, roles.text2),
        SizedBox(height: gap),
        bar(80, 6, roles.text2),
        const Spacer(),
        surface(
          role: KitSurfaceRole.floating,
          radius: tokens.composerRadius,
          height: tokens.space6 * scale,
          padding: EdgeInsets.symmetric(horizontal: tokens.space3 * scale),
          alignment: AlignmentDirectional.centerStart,
          child: bar(60, 5, roles.text3),
        ),
      ],
    ),
  );
}
