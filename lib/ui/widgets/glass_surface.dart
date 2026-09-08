import 'dart:ui' as ui;

import 'package:flutter/material.dart';

/// Bounded frosted navigation, rendered by Flutter's Android GPU pipeline.
///
/// The shell extends scrolling content beneath this clipped backdrop filter.
/// This is in-app Flutter glass, not Android OS cross-window or Compose blur.
/// Accessibility settings remove the filter and use a fully opaque material.
class GlassSurface extends StatelessWidget {
  const GlassSurface({super.key, required this.child});

  final Widget child;

  /// Neutral ink stays readable even when contrasting content crosses behind
  /// the translucent material; muted palette roles are not sufficient here.
  static Color foregroundColor(ThemeData theme) =>
      theme.brightness == Brightness.dark ? Colors.white : Colors.black;

  static bool reduceEffects(BuildContext context) {
    final media = MediaQuery.of(context);
    return media.highContrast ||
        media.accessibleNavigation ||
        media.disableAnimations;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final opaque = reduceEffects(context);
    final dark = theme.brightness == Brightness.dark;
    const radius = BorderRadius.all(Radius.circular(24));
    final tint = Color.alphaBlend(
      scheme.primary.withValues(alpha: dark ? .035 : .018),
      scheme.surfaceContainerLow,
    );

    final material = DecoratedBox(
      decoration: BoxDecoration(
        color: opaque
            ? scheme.surfaceContainerHigh
            : tint.withValues(alpha: dark ? .78 : .72),
        borderRadius: radius,
        border: Border.all(
          color: opaque
              ? scheme.outline
              : scheme.onSurface.withValues(alpha: dark ? .16 : .12),
        ),
      ),
      child: child,
    );

    return DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: radius,
        boxShadow: opaque
            ? const []
            : [
                BoxShadow(
                  color: Colors.black.withValues(alpha: dark ? .18 : .06),
                  blurRadius: 8,
                  offset: const Offset(0, 2),
                ),
              ],
      ),
      child: ClipRRect(
        borderRadius: radius,
        child: opaque
            ? material
            : BackdropFilter(
                filter: ui.ImageFilter.blur(sigmaX: 12, sigmaY: 12),
                child: material,
              ),
      ),
    );
  }
}
