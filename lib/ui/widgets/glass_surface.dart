import 'package:flutter/material.dart';

/// A quiet tonal material for navigation chrome.
///
/// This surface lives outside scrolling content, so a backdrop blur would only
/// blur a solid scaffold. A solid tint, light-catching edge and shallow depth provide
/// separation without pretending to sample the content behind it. Accessibility
/// settings remove decorative depth and strengthen the surface boundary.
class GlassSurface extends StatelessWidget {
  const GlassSurface({super.key, required this.child});

  final Widget child;

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

    return DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: radius,
        boxShadow: opaque
            ? const []
            : [
                BoxShadow(
                  color: Colors.black.withValues(alpha: dark ? .18 : .05),
                  blurRadius: 8,
                  offset: const Offset(0, 2),
                ),
              ],
      ),
      child: ClipRRect(
        borderRadius: radius,
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: opaque ? scheme.surfaceContainerHigh : tint,
            borderRadius: radius,
            border: Border.all(
              color: opaque
                  ? scheme.outline
                  : scheme.onSurface.withValues(alpha: dark ? .14 : .10),
            ),
          ),
          child: child,
        ),
      ),
    );
  }
}
